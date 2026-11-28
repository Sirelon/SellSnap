#!/usr/bin/env node
/**
 * Store screenshot compositor.
 *
 * Reads the headless renders in `store/captures/<device>/<lang>/<scene>_<theme>.png` (written by
 * the `StoreScenesTest` JVM test), puts each into a device frame with a status bar, and adds the
 * caption from `store/copy/copy.json` on a brand background. The manifest `scenes.json` says which
 * scene goes into which slot, in which theme, with which caption, and why the caption is true.
 *
 * Usage (from the repo root):
 *   node store/tools/generate-store-screenshots.mjs                         # every target, every language
 *   node store/tools/generate-store-screenshots.mjs --target=iphone-6.9 --lang=uk --sheet
 *   node store/tools/generate-store-screenshots.mjs --scene=messages        # one scene everywhere
 *
 * Flags: --target, --lang, --scene (comma lists); --sheet writes a contact sheet per target and
 * language to store/previews/.
 * Needs: node 18+, Google Chrome, ImageMagick (`magick`).
 */

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";

const here = dirname(fileURLToPath(import.meta.url));
const storeRoot = join(here, "..");
const repoRoot = join(storeRoot, "..");
const capturesRoot = join(storeRoot, "captures");
const assetsRoot = join(storeRoot, "assets");
const previewsRoot = join(storeRoot, "previews");
const fontDir = join(repoRoot, "composeApp/src/commonMain/composeResources/font");
const chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

const copy = JSON.parse(readFileSync(join(storeRoot, "copy/copy.json"), "utf8"));
const manifest = JSON.parse(readFileSync(join(here, "scenes.json"), "utf8"));

const APP_STORE_LANGS = ["en", "uk", "pl", "ro", "pt"];
const PLAY_LANGS = ["en", "uk", "pl", "ro", "bg", "pt", "kk"];

/**
 * One output slot in a store console.
 * source: capture device folder. list: scene list in scenes.json. inset: status-bar / home-bar
 * strip heights in source pixels (StoreDevice.topInset/bottomInset x density). frame: bezel style.
 * resizeTo: extra accepted sizes written by downscaling the same image.
 */
const TARGETS = {
  "iphone-6.9": {
    store: "app-store", langs: APP_STORE_LANGS, source: "iphone", list: "phone",
    canvas: [1320, 2868], frame: "iphone", inset: [186, 102], density: 3,
  },
  "iphone-duo-outer": {
    store: "app-store", langs: APP_STORE_LANGS, source: "iphone-duo-outer", list: "compact",
    canvas: [1398, 2034], frame: "plain", inset: [132, 72], density: 3,
  },
  "iphone-duo-inner": {
    store: "app-store", langs: APP_STORE_LANGS, source: "iphone-duo-inner", list: "large",
    canvas: [2007, 2853], frame: "plain", inset: [120, 72], density: 3,
  },
  "ipad-13": {
    store: "app-store", langs: APP_STORE_LANGS, source: "ipad", list: "tablet",
    canvas: [2752, 2064], frame: "tablet", inset: [48, 40], density: 2,
  },
  phone: {
    store: "play-store", langs: PLAY_LANGS, source: "android-phone", list: "phone",
    canvas: [1440, 2560], frame: "android", inset: [95, 63], density: 2.625,
  },
  tablet: {
    store: "play-store", langs: PLAY_LANGS, source: "android-tablet", list: "tablet",
    canvas: [2560, 1440], frame: "tablet", inset: [56, 48], density: 2,
  },
};

const args = Object.fromEntries(process.argv.slice(2).map((a) => {
  const [k, v] = a.replace(/^--/, "").split("=");
  return [k, v ?? true];
}));
const pick = (name) => (typeof args[name] === "string" ? args[name].split(",") : null);
const targetFilter = pick("target");
const langFilter = pick("lang");
const sceneFilter = pick("scene");

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function captionFor(lang, key) {
  const block = copy[lang]?.[key];
  if (!block) throw new Error(`copy.json: no "${key}" for ${lang}`);
  return block;
}

/** Status-bar glyphs, drawn over the strip the renderer left free. */
function statusBar(frame, color, heightPx, widthPx, pxPerDp) {
  const s = pxPerDp; // glyph sizes below are in dp
  const ios = frame === "iphone" || frame === "plain" || frame === "tablet";
  const time = ios ? "9:41" : "12:30";
  const font = Math.round((ios ? 17 : 14) * s);
  const icons = `
    <svg height="${Math.round(12 * s)}" viewBox="0 0 18 12" fill="${color}"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5.5" width="3" height="6.5" rx="1"/><rect x="10" y="3" width="3" height="9" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></svg>
    <svg height="${Math.round(12 * s)}" viewBox="0 0 17 12" fill="${color}"><path d="M8.5 2.3c2.4 0 4.6.9 6.3 2.5l1.3-1.3A10.6 10.6 0 0 0 8.5.4C5.6.4 2.9 1.5.9 3.5l1.3 1.3a8.9 8.9 0 0 1 6.3-2.5Zm0 3.7c1.4 0 2.7.5 3.7 1.4l1.3-1.3a7.3 7.3 0 0 0-10 0l1.3 1.3c1-.9 2.3-1.4 3.7-1.4Zm0 3.6c-.5 0-1 .2-1.4.6L8.5 12l1.4-1.8c-.4-.4-.9-.6-1.4-.6Z"/></svg>
    <svg height="${Math.round(12 * s)}" viewBox="0 0 27 12"><rect x=".5" y=".5" width="23" height="11" rx="3" fill="none" stroke="${color}" opacity=".45"/><rect x="2" y="2" width="20" height="8" rx="1.8" fill="${color}"/><rect x="25" y="4" width="1.6" height="4" rx=".8" fill="${color}" opacity=".45"/></svg>`;
  const pad = Math.round(widthPx * (ios ? 0.085 : 0.06));
  const yShift = frame === "iphone" ? Math.round(heightPx * 0.08) : 0;
  return `<div class="sb" style="height:${heightPx}px;padding:0 ${pad}px;font-size:${font}px;color:${color};transform:translateY(${yShift}px)">
    <span>${time}</span><span class="icons" style="gap:${Math.round(6 * s)}px">${icons}</span></div>`;
}

function page(target, lang, entry, capturePath, dark) {
  const t = TARGETS[target];
  const [W, H] = t.canvas;
  const [srcW, srcH] = execFileSync("magick", ["identify", "-format", "%w %h", capturePath]).toString().trim().split(" ").map(Number);
  const landscape = W > H;
  const caption = captionFor(lang, entry.copy);

  // Layout: caption on top, device below, both centred. Sizes are fractions of the canvas.
  const textTop = Math.round(H * (landscape ? 0.06 : 0.045));
  const headlineSize = Math.round((landscape ? H * 0.058 : W * 0.072));
  const subSize = Math.round(headlineSize * 0.47);
  const textBlock = Math.round(headlineSize * 2.3 + subSize * 1.9 + H * 0.035);
  const bottomMargin = Math.round(H * (landscape ? 0.05 : 0.035));
  const bezel = Math.round((landscape ? H : W) * (t.frame === "plain" ? 0.012 : 0.018));
  const availH = H - textTop - textBlock - bottomMargin;
  const availW = W * (landscape ? 0.8 : 0.84);
  const scale = Math.min((availH - 2 * bezel) / srcH, (availW - 2 * bezel) / srcW);
  const screenW = Math.round(srcW * scale);
  const screenH = Math.round(srcH * scale);
  const radius = Math.round(screenW * (t.frame === "iphone" ? 0.13 : t.frame === "android" ? 0.09 : landscape ? 0.03 : 0.07));
  const topStrip = Math.round(t.inset[0] * scale);
  const bottomStrip = Math.round(t.inset[1] * scale);
  const ink = dark ? "#FFFFFF" : "#10201E";

  const island = t.frame === "iphone"
    ? `<div class="island" style="width:${Math.round(screenW * 0.3)}px;height:${Math.round(screenW * 0.088)}px;top:${Math.round(topStrip * 0.3)}px"></div>`
    : t.frame === "android"
      ? `<div class="punch" style="width:${Math.round(screenW * 0.035)}px;height:${Math.round(screenW * 0.035)}px;top:${Math.round(topStrip * 0.32)}px"></div>`
      : "";
  const homeBar = `<div class="home" style="width:${Math.round(screenW * (landscape ? 0.18 : 0.36))}px;height:${Math.max(4, Math.round(bottomStrip * 0.14))}px;bottom:${Math.round(bottomStrip * 0.32)}px;background:${ink}"></div>`;

  const headline = caption.headline.split("\n").map((line) => `<span>${esc(line)}</span>`).join("");
  return `<!doctype html><html><head><meta charset="utf-8"><style>
@font-face { font-family: Manrope; src: url("file://${fontDir}/manrope_variable.ttf"); font-weight: 200 800; }
@font-face { font-family: Inter; src: url("file://${fontDir}/inter_variable.ttf"); font-weight: 100 900; }
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: ${W}px; height: ${H}px; overflow: hidden; }
body {
  background:
    radial-gradient(ellipse ${Math.round(W * 0.9)}px ${Math.round(H * 0.45)}px at 85% 4%, rgba(94,211,196,.38), transparent 70%),
    radial-gradient(ellipse ${Math.round(W * 0.8)}px ${Math.round(H * 0.4)}px at 0% 100%, rgba(94,211,196,.20), transparent 70%),
    linear-gradient(170deg, #0F766E 0%, #0B4F4A 55%, #082F2C 100%);
  font-family: Manrope, sans-serif; color: #fff; position: relative;
}
.text { position: absolute; top: ${textTop}px; left: 0; right: 0; text-align: center; padding: 0 ${Math.round(W * 0.07)}px; }
h1 { font-size: ${headlineSize}px; line-height: 1.08; font-weight: 800; letter-spacing: -0.02em; }
h1 span { display: block; white-space: nowrap; }
p { margin-top: ${Math.round(subSize * 0.7)}px; font-family: Inter, sans-serif; font-size: ${subSize}px; line-height: 1.3; font-weight: 500; color: #CCE8E4; }
.device { position: absolute; left: 50%; bottom: ${bottomMargin}px; transform: translateX(-50%);
  padding: ${bezel}px; border-radius: ${radius + bezel}px; background: #0A0F0E;
  box-shadow: 0 ${Math.round(H * 0.02)}px ${Math.round(H * 0.05)}px rgba(0,0,0,.45), inset 0 0 0 ${Math.max(2, Math.round(bezel * 0.12))}px #2A3533; }
.screen { position: relative; width: ${screenW}px; height: ${screenH}px; border-radius: ${radius}px; overflow: hidden; }
.screen img { display: block; width: 100%; height: 100%; }
.sb { position: absolute; top: 0; left: 0; right: 0; display: flex; align-items: center; justify-content: space-between;
  font-family: Inter, sans-serif; font-weight: 600; letter-spacing: -0.01em; }
.icons { display: flex; align-items: center; }
.island { position: absolute; left: 50%; transform: translateX(-50%); background: #000; border-radius: 999px; }
.punch { position: absolute; left: 50%; transform: translateX(-50%); background: #000; border-radius: 50%; }
.home { position: absolute; left: 50%; transform: translateX(-50%); border-radius: 999px; opacity: .85; }
</style></head><body>
<div class="text"><h1>${headline}</h1><p>${esc(caption.sub)}</p></div>
<div class="device"><div class="screen">
  <img src="file://${capturePath}">
  ${statusBar(t.frame, ink, topStrip, screenW, scale * t.density)}
  ${island}${homeBar}
</div></div>
<script>
  // Each headline line stays on one line: shrink the font until the longest one fits.
  const h1 = document.querySelector("h1");
  let size = ${headlineSize};
  while (size > ${Math.round(headlineSize * 0.6)} && [...h1.children].some((l) => l.scrollWidth > h1.clientWidth)) {
    size -= 2;
    h1.style.fontSize = size + "px";
  }
</script>
</body></html>`;
}

function renderPng(html, W, H, out) {
  const dir = join(tmpdir(), "sellsnap-store");
  mkdirSync(dir, { recursive: true });
  const htmlPath = join(dir, "page.html");
  const shot = join(dir, "shot.png");
  writeFileSync(htmlPath, html);
  rmSync(shot, { force: true });
  execFileSync(chrome, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
    "--force-device-scale-factor=1", "--allow-file-access-from-files",
    `--window-size=${W},${H}`, `--screenshot=${shot}`, `file://${htmlPath}`,
  ], { stdio: "ignore" });
  if (!existsSync(shot)) throw new Error(`Chrome produced no screenshot for ${out}`);
  mkdirSync(dirname(out), { recursive: true });
  // JPEG, no alpha: both stores reject transparency.
  execFileSync("magick", [shot, "-crop", `${W}x${H}+0+0`, "+repage", "-background", "#0B4F4A", "-alpha", "remove", "-alpha", "off", "-quality", "90", out]);
}

const md5 = (p) => createHash("md5").update(readFileSync(p)).digest("hex");
let written = 0;
const problems = [];

for (const [targetName, t] of Object.entries(TARGETS)) {
  if (targetFilter && !targetFilter.includes(targetName)) continue;
  const entries = manifest[t.list] ?? [];
  for (const lang of t.langs) {
    if (langFilter && !langFilter.includes(lang)) continue;
    const sources = entries.map((e) => join(capturesRoot, t.source, lang, `${e.scene}_${e.theme}.png`));
    const missing = sources.filter((p) => !existsSync(p));
    if (missing.length) {
      problems.push(`${targetName}/${lang}: skipped, missing ${missing.map((p) => p.replace(repoRoot + "/", "")).join(", ")}`);
      continue;
    }
    // Two slots showing the same picture would make one caption a lie.
    const seen = new Map();
    sources.forEach((p, i) => {
      const h = md5(p);
      if (seen.has(h)) problems.push(`${targetName}/${lang}: "${entries[i].id}" has the same capture as "${seen.get(h)}"`);
      seen.set(h, entries[i].id);
    });
    const outDir = join(assetsRoot, t.store, targetName, lang);
    rmSync(outDir, { recursive: true, force: true });
    const outputs = [];
    entries.forEach((entry, i) => {
      if (sceneFilter && !sceneFilter.includes(entry.scene)) return;
      const name = `${String(i + 1).padStart(2, "0")}-${entry.id}.jpg`;
      const out = join(outDir, name);
      renderPng(page(targetName, lang, entry, sources[i], entry.theme === "dark"), t.canvas[0], t.canvas[1], out);
      outputs.push(out);
      written++;
      for (const [alsoName, [w, h]] of Object.entries(t.also ?? {})) {
        const alsoOut = join(assetsRoot, t.store, alsoName, lang, name);
        mkdirSync(dirname(alsoOut), { recursive: true });
        execFileSync("magick", [out, "-filter", "Lanczos", "-resize", `${w}x${h}!`, "-quality", "90", alsoOut]);
      }
    });
    console.log(`${t.store}/${targetName}/${lang}: ${outputs.length} images`);
    if (args.sheet && outputs.length) {
      mkdirSync(previewsRoot, { recursive: true });
      const sheet = join(previewsRoot, `${targetName}-${lang}.jpg`);
      execFileSync("magick", [...outputs, "-resize", t.canvas[0] > t.canvas[1] ? "800x" : "x900", "+append", "-quality", "80", sheet]);
      console.log(`  sheet: ${sheet.replace(repoRoot + "/", "")}`);
    }
  }
}

console.log(`\n${written} images written.`);
if (problems.length) {
  console.log("\nProblems:");
  problems.forEach((p) => console.log(`  ! ${p}`));
  process.exitCode = 1;
}
