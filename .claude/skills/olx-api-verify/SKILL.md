---
name: olx-api-verify
description: >-
  Live OLX partner-API probes that need no user account, for a given country
  (pt/ro/pl/ua/bg). Use to mint a client_credentials token, check routing,
  diff the live JSON of any partner endpoint against our Response classes
  and mapper, or verify whether we parse an OLX endpoint correctly.
  Fixes mismatches and adds regression tests from the captured payloads.
---

# OLX API Verification

Goal: prove that SellSnap's OLX partner-API parsing is correct against the **live**
API for one country, and close any gap with a fix + regression test. The worked example
below is the categories API (categories, category suggestion, attributes); the same
steps apply to any partner endpoint a token without a user account can read.

The user names a country (one of the live ones: `pt`, `ro`, `pl`, `ua`, `bg`).
You mint the token yourself from the hardcoded credentials — the user does **not**
provide a token.

## 0. Inputs & where things live

| What | Location |
|---|---|
| Per-country creds (`clientId`, `clientSecret`, `domain`) | `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/domain/OlxCountry.kt` |
| Live countries | PT `olx.pt`, RO `olx.ro`, PL `olx.pl`, UA `olx.ua`, BG `olx.bg` (KZ has no creds — excluded) |
| API client / endpoints | `.../features/seller/auth/data/OlxApiClient.kt` |
| HTTP client + `Json` config + token refresh | `.../features/seller/auth/data/OlxHttpClientFactory.kt` |
| Categories Response classes | `.../features/seller/categories/data/response/{OlxCategoryResponse,OlxCategorySuggestionResponse,OlxAttributesResponse}.kt` |
| Mapper (parse → domain) | `.../features/seller/categories/domain/CategoriesMapper.kt` |
| Repository (filtering/normalize) | `.../features/seller/categories/data/CategoriesRepository.kt` |
| Tests | `composeApp/src/commonTest/.../features/seller/categories/` |

> The Response classes and the mapper's map functions are `internal` — a test in
> `commonTest` (same module) can access them directly.

## 1. Read the credentials for the country

Read `OlxCountry.kt` and pull `clientId`, `clientSecret`, `domain` for the requested
country. **Never** paste secrets into the skill file, the report, or any committed
file — use them only in the in-session curl call. They already live (hardcoded) in
`OlxCountry.kt`, so don't duplicate them.

## 2. Mint a client_credentials token

Send a JSON body and the `Version: 2.0` header. Scope `v2 read` is enough for the read
endpoints. Token TTL is 24h (`expires_in: 86400`).

```bash
D=$(mktemp -d)   # session directory for captured JSON, used in step 3
DOMAIN=olx.ua; CID=<clientId>; SECRET=<clientSecret>   # from OlxCountry.kt
TOKEN=$(curl -sS -X POST "https://www.$DOMAIN/api/open/oauth/token" \
  -H 'Content-Type: application/json' -H 'Accept: application/json' -H 'Version: 2.0' \
  -d "{\"grant_type\":\"client_credentials\",\"client_id\":\"$CID\",\"client_secret\":\"$SECRET\",\"scope\":\"v2 read\"}" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
```

If a country ever rejects `client_credentials` (400 / `invalid_client`), fall back to
asking the user for a user access token (OAuth authorization_code) and skip step 2.

## 3. Fetch the endpoints (all GET, read-only)

Base is `https://www.$DOMAIN/api/partner/`. Every request needs
`Authorization: Bearer $TOKEN`, `Accept: application/json`, `Version: 2.0`.

```bash
H=(-H "Authorization: Bearer $TOKEN" -H 'Accept: application/json' -H 'Version: 2.0')
B="https://www.$DOMAIN/api/partner"
curl -sS "${H[@]}" "$B/categories"                       -o $D/categories.json   # OlxCategoriesRootResponse
curl -sS "${H[@]}" -G "$B/categories/suggestion" --data-urlencode 'q=iPhone 14 Pro'  -o $D/suggestion.json   # OlxCategorySuggestionResponse
# pick a leaf id from suggestion or categories, then:
curl -sS "${H[@]}" "$B/categories/<leafId>/attributes"   -o $D/attributes.json        # OlxAttributesResponse
```

The same token also covers `currencies` and `locations`
(`GET locations?latitude=&longitude=`) if the user wants those verified too.

## 4. Diff real JSON against the Response classes

For each endpoint, compute the **union of keys** across all items (not just item[0]),
each key's JSON **type**, and its **null frequency**. Compare to the Response class. Check:

1. **Missing fields** — keys the API returns that no Response class captures (we silently drop
   them; `ignoreUnknownKeys = true`). Flag if useful.
2. **Type mismatch** — e.g. API returns `id` as a quoted string but the Response class says `Int`.
   `isLenient = true` coerces some of these (`"85"` → `85`) but it's fragile; a
   regression test must prove it, not an assumption.
3. **Nullability** — a Response-class non-null field that is null/absent in real data (crashes),
   or an over-defensive nullable that's always present.
4. **Mapper logic** — does `CategoriesMapper` handle the real shapes? Especially
   `deriveInputType()` (SingleSelect / MultiSelect / NumericInput / TextInput) and
   `parent_id <= 0 → root`.

A quick analyzer:
```bash
python3 - "$D" <<'PY'
import json,sys
from collections import defaultdict
for f in ["$D/categories.json","$D/attributes.json"]:
    d=json.load(open(f)); data=d.get("data") or []
    keys=set().union(*[set(x) for x in data]) if data else set()
    t=defaultdict(set); n=defaultdict(int)
    for it in data:
        for k in keys:
            v=it.get(k,"__M__")
            if v is None:n[k]+=1
            elif v!="__M__":t[k].add(type(v).__name__)
    print(f, "count",len(data)); [print(f"  {k:16} {sorted(t[k])} nulls={n[k]}") for k in sorted(keys)]
PY
```

## 5. Report → fix → regression-test

For every confirmed mismatch: state it, fix the Response class/mapper at the root (don't add
null-safety band-aids), then add/extend a parsing test that feeds the **real captured
payload** (a representative subset) through the app's exact `Json` config
(`ignoreUnknownKeys = true; isLenient = true; explicitNulls = false`) → Response class → mapper,
asserting the mapped domain result. Keep fixtures real (trim long value lists, keep
every shape variation: root vs child, leaf true/false, select/multi/numeric/text,
null vs numeric min/max, string-vs-int ids).

Test file: `composeApp/src/commonTest/kotlin/com/sirelon/aicalories/features/seller/categories/data/CategoriesResponseParsingTest.kt` (extend it).

Compile & run:
```bash
./gradlew :composeApp:compileAndroidMain          # fast compile check
./gradlew :composeApp:jvmTest --tests "*Categories*ParsingTest*"
```

Never commit tokens or captured payloads that contain tokens.

## Reference: Response classes ↔ live JSON (baseline)

Live JSON verified 2026-07-20 — **all 5 live countries (PT/RO/PL/UA/BG) return an identical schema**
for these three endpoints, and `client_credentials` token minting works for each. Category
counts differ (BG ~1.2k → PL ~3.1k) but field shapes match, so the UA regression fixtures
are representative of every country.

- **`GET categories`** → item keys `id`(int), `name`(str), `parent_id`(int, `0`=root),
  `is_leaf`(bool), **`photos_limit`(int)**. All non-null in practice.
  `OlxCategoryResponse` captures id/name/parent_id/is_leaf — **misses `photos_limit`**.
  `CategoriesMapper` maps `parent_id <= 0` to a null `parentId`.
- **`GET categories/suggestion?q=`** → items `{id, name, path:[{id,name}]}` where **`id`
  is a quoted string** (`"85"`). `OlxCategorySuggestionResponse` captures only `id`, as
  **`String?`**, and ignores `name`/`path`. `OlxApiClient.loadCategorySuggestionId` converts
  it with `toIntOrNull()`.
- **`GET categories/{id}/attributes`** → items `{code, label, unit, validation, values[]}`;
  `validation = {type, required, numeric, min, max, allow_multiple_values}` (`type` always
  `"attribute"`; `min`/`max` are JSON ints when set, else null); `values[] = {code, label}`
  (`code` is a numeric **string**). `OlxAttributesResponse` matches fully; `min`/`max` are
  `Double?`.
