#!/usr/bin/env python3
"""Listing-generation model bench (SIR-199). Write-up: research/MODEL-BENCH-listing-generation.md.

Sends real seller photos through the production listing prompt, which it reads from OpenAIClient.kt
on every run, and scores the answers against the answer key in manifest.json.

  bench.py fetch                         download the photos and resize them like the app does
  bench.py run CONFIG [CONFIG ...]       one call per photo, plus the stability repeats
  bench.py score [CONFIG ...]            score table; no API calls
  bench.py show CONFIG                   every answer of one config, for reading by eye

Configs live in configs.json. Photos and raw answers live in .cache/ (gitignored): the photos are
seller pictures and are never committed. OPENAI_KEY comes from local.properties at the repo root.
"""
import base64, concurrent.futures as cf, hashlib, json, os, re, statistics, subprocess, sys, time
import urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
CACHE = os.path.join(HERE, '.cache')
PHOTOS = os.path.join(CACHE, 'photos')
RUNS = os.path.join(CACHE, 'runs.jsonl')
CLIENT = os.path.join(ROOT, 'composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/openai/OpenAIClient.kt')
MANIFEST = json.load(open(os.path.join(HERE, 'manifest.json')))
CONFIGS = json.load(open(os.path.join(HERE, 'configs.json')))
COUNTRY = MANIFEST['country']
# The app uploads 1280 px on the long side at JPEG quality 75 (SIR-174).
UPLOAD_LONG_SIDE, UPLOAD_QUALITY = 1280, 75
# $ per 1M tokens: input, cached input, output. Source: https://developers.openai.com/api/docs/pricing (2026-11-24).
PRICE = {'gpt-4.1': (2.0, 0.5, 8.0), 'gpt-6-luna': (0.10, 0.01, 0.50),
         'gpt-6.1-terra': (1.0, 0.05, 5.0), 'gpt-6.2-sol': (2.0, 0.10, 10.0)}
THREADS = 4


def production_prompt():
    src = open(CLIENT).read()
    raw = re.search(r'adGenerationInstructions\(country: OlxCountry\): String = """\n(.*?)"""', src, re.S).group(1)
    for key, value in COUNTRY.items():
        raw = raw.replace('${country.%s}' % key, value)
    assert '${' not in raw, 'the prompt has a template value this bench does not fill'
    version = re.search(r'AD_GENERATION_PROMPT_VERSION = "(\w+)"', src).group(1)
    return raw.strip('\n'), version


def instructions_for(cfg):
    text, version = production_prompt()
    if cfg.get('promptAppend'):
        text += '\n\n' + cfg['promptAppend']
    return text, version


def fingerprint(cfg, instructions):
    """A record counts for a config only while the config and the prompt are unchanged."""
    return hashlib.sha1((json.dumps(cfg, sort_keys=True) + instructions).encode()).hexdigest()[:10]


def photo_path(item_id):
    return os.path.join(PHOTOS, '%03d.jpg' % item_id)


def fetch():
    from PIL import Image, ImageOps
    os.makedirs(os.path.join(PHOTOS, 'orig'), exist_ok=True)
    for item in MANIFEST['items']:
        orig = os.path.join(PHOTOS, 'orig', '%03d' % item['id'])
        if not os.path.exists(orig):
            subprocess.run(['gsutil', '-q', 'cp', item['gs'], orig], check=True)
        # Same steps as the app: EXIF orientation baked in, long side capped, never upscaled.
        im = ImageOps.exif_transpose(Image.open(orig)).convert('RGB')
        w, h = im.size
        if max(w, h) > UPLOAD_LONG_SIDE:
            s = UPLOAD_LONG_SIDE / max(w, h)
            im = im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.BILINEAR, reducing_gap=2.0)
        im.save(photo_path(item['id']), 'JPEG', quality=UPLOAD_QUALITY)
        print('%03d %dx%d' % (item['id'], *im.size))


def api_key():
    for line in open(os.path.join(ROOT, 'local.properties')):
        if line.startswith('OPENAI_KEY='):
            return line.split('=', 1)[1].strip()
    sys.exit('OPENAI_KEY is missing from local.properties')


def call(name, item_id, key):
    cfg = CONFIGS[name]
    instructions, version = instructions_for(cfg)
    b64 = base64.b64encode(open(photo_path(item_id), 'rb').read()).decode()
    body = {'model': cfg['model'], 'instructions': instructions, 'store': False,
            'max_output_tokens': cfg.get('maxOutputTokens', 1000),
            'input': [{'role': 'user', 'content': [
                {'type': 'input_text', 'text': 'Generate the OLX %s listing for the main item shown in the photos.' % COUNTRY['nameEn']},
                {'type': 'input_image', 'image_url': 'data:image/jpeg;base64,' + b64, 'detail': cfg.get('detail', 'high')}]}]}
    if 'temperature' in cfg:
        body['temperature'] = cfg['temperature']
    if 'effort' in cfg:
        body['reasoning'] = {'effort': cfg['effort']}
    req = urllib.request.Request('https://api.openai.com/v1/responses', json.dumps(body).encode(),
                                 {'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    started = time.time()
    try:
        r = json.load(urllib.request.urlopen(req, timeout=300))
    except urllib.error.HTTPError as e:
        raise RuntimeError(e.read().decode()[:400])
    text = ''.join(c.get('text', '') for o in r['output'] if o['type'] == 'message' for c in o['content'])
    u = r['usage']
    cached = u.get('input_tokens_details', {}).get('cached_tokens', 0)
    pi, pc, po = PRICE[cfg['model']]
    return {'cfg': name, 'fp': fingerprint(cfg, instructions), 'prompt': version, 'id': item_id, 'text': text,
            'status': r.get('status'), 'in': u['input_tokens'], 'cached': cached, 'out': u['output_tokens'],
            'reasoning': u.get('output_tokens_details', {}).get('reasoning_tokens', 0),
            'secs': round(time.time() - started, 1),
            'cost': ((u['input_tokens'] - cached) * pi + cached * pc + u['output_tokens'] * po) / 1e6}


def load_runs(name):
    """Current records of one config, keyed by (photo id, run number)."""
    if name not in CONFIGS or not os.path.exists(RUNS):
        return {}
    fp = fingerprint(CONFIGS[name], instructions_for(CONFIGS[name])[0])
    out = {}
    for line in open(RUNS):
        rec = json.loads(line)
        if rec['cfg'] == name and rec['fp'] == fp:
            out[(rec['id'], rec['run'])] = rec
    return out


def run(names):
    key = api_key()
    missing = [i['id'] for i in MANIFEST['items'] if not os.path.exists(photo_path(i['id']))]
    if missing:
        sys.exit('photos missing, run `bench.py fetch` first: %s' % missing)
    stab = MANIFEST['stability']
    jobs = []
    for name in names:
        done = load_runs(name)
        wanted = [(i['id'], 1) for i in MANIFEST['items']]
        wanted += [(i, r) for i in stab['items'] for r in range(2, stab['runs'] + 1)]
        jobs += [(name, i, r) for i, r in wanted if (i, r) not in done]

    def go(job):
        name, item_id, run_no = job
        for attempt in (1, 2):
            try:
                rec = call(name, item_id, key)
                rec['run'] = run_no
                with open(RUNS, 'a') as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + '\n')
                return '%s #%d r%d %.1fs $%.4f' % (name, item_id, run_no, rec['secs'], rec['cost'])
            except Exception as e:
                if attempt == 2:
                    return 'ERROR %s #%d r%d: %s' % (name, item_id, run_no, e)
                time.sleep(5)

    with cf.ThreadPoolExecutor(THREADS) as ex:
        for line in ex.map(go, jobs):
            print(line, flush=True)
    print('cost of these configs so far: $%.3f' % sum(r['cost'] for n in names for r in load_runs(n).values()))


def parse(text):
    try:
        return json.loads(text)
    except ValueError:
        return None


def judge(item, rec):
    """'ok', or 'FAIL: reason', or 'MISS: reason' (no keyword hit: read the title by eye)."""
    ans = parse(rec['text'])
    if ans is None:
        return 'FAIL: not JSON'
    if 'refuse' in item:
        code = ans.get('unrecognized')
        return 'ok' if code in item['refuse'] else 'FAIL: wrote a listing' if code is None else 'FAIL: refused as %s' % code
    if 'unrecognized' in ans:
        return 'FAIL: refused a usable photo (%s)' % ans['unrecognized']
    text = ('%s %s' % (ans.get('title', ''), ans.get('description', ''))).lower()
    for bad in item.get('wrong', []):
        if re.search(bad, text):
            return 'FAIL: wrong item (%s)' % bad
    for need in item.get('must', []):
        if not re.search(need, text):
            return 'MISS: no match for %s' % need
    return 'ok'


def stats(name):
    runs = load_runs(name)
    items = {i['id']: i for i in MANIFEST['items']}
    first = {i: runs.get((i, 1)) for i in items}
    s = {'name': name, 'calls': len(runs), 'cost': sum(r['cost'] for r in runs.values()),
         'missing': [i for i, r in first.items() if r is None], 'fails': [], 'misses': [], 'bonus': 0,
         'bonusOf': 0, 'priceFlags': [], 'wrongDetails': []}
    for i, rec in first.items():
        if rec is None:
            continue
        item = items[i]
        verdict = judge(item, rec)
        if verdict.startswith('FAIL'):
            s['fails'].append('#%d %s' % (i, verdict[6:]))
        elif verdict.startswith('MISS'):
            s['misses'].append('#%d %s' % (i, (parse(rec['text']) or {}).get('title')))
        ans = parse(rec['text']) or {}
        text = ('%s %s' % (ans.get('title', ''), ans.get('description', ''))).lower()
        if 'bonus' in item:
            s['bonusOf'] += 1
            s['bonus'] += bool(re.search(item['bonus'], text))
        # A wrong model or part number: the item is right, but the seller would list a false fact.
        s['wrongDetails'] += ['#%d %s' % (i, d) for d in item.get('wrongDetail', []) if re.search(d, text)]
        if 'maxPrice' in item:
            prices = [(parse(r['text']) or {}).get('suggestedPrice') or 0 for (j, _), r in runs.items() if j == i]
            s['priceFlags'] += ['#%d %s' % (i, p) for p in prices if p > item['maxPrice']]
    spreads = {}
    for i in MANIFEST['stability']['items']:
        prices = [(parse(r['text']) or {}).get('suggestedPrice') for (j, _), r in runs.items() if j == i]
        prices = [p for p in prices if isinstance(p, (int, float)) and p > 0]
        if len(prices) >= 2:
            # Spread = (max - min) / min, the measure SIR-116 used ("drifted up to 46% apart").
            spreads[i] = (round((max(prices) - min(prices)) / min(prices) * 100), sorted(prices))
    s['spreads'] = spreads
    # SIR-116's bar: every run of an item lands within +-10% of that item's median price.
    # Share of runs on each item's most common price: less brittle than +-10% on a 5-run sample.
    s['onMode'] = sum(max(v[1].count(p) for p in v[1]) for v in spreads.values())
    s['stabRuns'] = sum(len(v[1]) for v in spreads.values())
    s['within10'] = sum(all(abs(p - statistics.median(v[1])) <= 0.1 * statistics.median(v[1]) for p in v[1])
                        for v in spreads.values())
    secs = [r['secs'] for r in runs.values()]
    s['medSecs'] = statistics.median(secs) if secs else 0
    s['perCall'] = s['cost'] / len(runs) if runs else 0
    return s


def score(names):
    names = names or list(CONFIGS)
    print('| Config | items wrong | refusals | wrong details | bonus | price flags | within +-10% | on modal price | max spread | median spread | $/call | median s | calls |')
    print('|---|---|---|---|---|---|---|---|---|---|---|---|---|')
    details = []
    for name in names:
        s = stats(name)
        if not s['calls']:
            continue
        refusal_ids = [i['id'] for i in MANIFEST['items'] if 'refuse' in i]
        ref_fails = [f for f in s['fails'] if int(f[1:].split()[0]) in refusal_ids]
        item_fails = [f for f in s['fails'] if f not in ref_fails]
        sp = [v[0] for v in s['spreads'].values()]
        print('| %s | %d | %d/%d | %d | %d/%d | %d | %d/%d | %d/%d | %s | %s | %.5f | %.1f | %d |' % (
            name, len(item_fails), len(refusal_ids) - len(ref_fails), len(refusal_ids), len(s['wrongDetails']),
            s['bonus'], s['bonusOf'], len(s['priceFlags']), s['within10'], len(s['spreads']), s['onMode'], s['stabRuns'],
            '%d%%' % max(sp) if sp else '-', '%d%%' % statistics.median(sp) if sp else '-',
            s['perCall'], s['medSecs'], s['calls']))
        details.append((name, s))
    for name, s in details:
        notes = s['fails'] + ['wrong detail ' + w for w in s['wrongDetails']] + ['read by eye: ' + m for m in s['misses']] + ['overpriced ' + p for p in s['priceFlags']]
        if s['missing']:
            notes.append('not run yet: %s' % s['missing'])
        notes.append('prices: ' + '; '.join('#%d %s' % (i, v[1]) for i, v in s['spreads'].items()))
        print('\n%s ($%.3f): %s' % (name, s['cost'], '\n  '.join([''] + notes)))


def show(name):
    for (i, r), rec in sorted(load_runs(name).items()):
        ans = parse(rec['text']) or {'raw': rec['text'][:200]}
        print('#%d r%d %s | %s | %s' % (i, r, ans.get('title') or ans.get('unrecognized') or ans.get('raw'),
                                        ans.get('suggestedPrice'), (ans.get('description') or '')[:200]))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cmd, args = sys.argv[1], sys.argv[2:]
    unknown = [a for a in args if a not in CONFIGS]
    if cmd in ('run', 'show') and (not args or unknown):
        sys.exit('unknown or missing config %s; configs.json has: %s' % (unknown, ', '.join(CONFIGS)))
    {'fetch': lambda: fetch(), 'run': lambda: run(args), 'score': lambda: score(args),
     'show': lambda: show(args[0])}[cmd]()
