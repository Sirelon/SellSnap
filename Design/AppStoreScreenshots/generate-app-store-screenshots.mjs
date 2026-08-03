#!/usr/bin/env node
/**
 * App Store screenshot generator (iPhone 6.9" + iPad 13", all locales).
 *
 * Reads the Maestro-captured per-locale screenshots from `screenshots/<device>/<locale>/`
 * and composites each one into a branded frame with a headline / sub / pills taken from
 * `Design/StoreScreenshots/copy.json`.
 *
 * WHY THIS SCRIPT EXISTS instead of the three older ones:
 *   1. The old scripts pair screenshot -> caption BY SORTED FILENAME INDEX. That is
 *      accidental coupling: re-capture or add one file and every caption shifts by one,
 *      which is how you end up with "your listing is live" over a login screen.
 *      Here the pairing is an explicit semantic manifest: scenes.json.
 *   2. The old iOS/iPad scripts hard-code Ukrainian copy inline, so they could never
 *      render another language. This one reads copy.json for every locale.
 *   3. The old iPad script assumes LANDSCAPE sources. The Maestro captures are PORTRAIT.
 *
 * Usage:
 *   node generate-app-store-screenshots.mjs                      # every device, every locale it can find
 *   node generate-app-store-screenshots.mjs --device=iphone
 *   node generate-app-store-screenshots.mjs --locale=pl,ro
 *   node generate-app-store-screenshots.mjs --device=ipad --locale=pl --sheet
 *
 * Requires: node 18+, headless Google Chrome, ImageMagick (`magick`).
 */

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from "node:fs";
import { basename, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..");

const sourceRoot = join(root, "screenshots");
const copyPath = join(root, "Design/StoreScreenshots/copy.json");
const scenesPath = join(here, "scenes.json");
const fontPath = join(root, "composeApp/src/commonMain/composeResources/font/manrope_variable.ttf");
const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

/** App locale folder -> copy.json language key. Kept explicit: they are not always equal. */
const LOCALE_TO_LANG = { bg: "bg", pl: "pl", pt: "pt", ro: "ro", ua: "uk", en: "en" };

/**
 * copy.json is keyed by the filenames of the *legacy* 2026-05-19 Android screenshots,
 * which no longer exist on disk — the keys are really just positions in the flow wearing
 * filename costumes. These readable aliases are what scenes.json refers to, so the
 * manifest can be reviewed by a human without decoding timestamps. copy.json itself is
 * deliberately left untouched: generate-google-play-screenshots.mjs still reads it by
 * the original keys.
 */
const COPY_BLOCKS = {
  hero: "Screenshot_20260519_231837.png",        // "Sell faster with AI" / "Photo in, listing out"
  new_listing: "Screenshot_20260519_232500.png", // "New listing in a minute" / "Add a photo, AI handles the rest"
  ai_writes: "Screenshot_20260519_232525.png",   // "AI writes text while you wait" / "Title, description and price"
  review: "Screenshot_20260519_232545.png",      // "Review everything before posting" / "Edit text, price and details"
  publish_tap: "Screenshot_20260519_232644.png", // "Publish in one tap" / "A ready listing with zero extra work"
  final_check: "Screenshot_20260519_233853.png", // "Publish with confidence" / "Final check before OLX"
  live: "Screenshot_20260519_233904.png",        // "Your listing is live" — INTENTIONALLY UNUSED, see PROGRESS.md §5
};

/** Resolve a scenes.json `copy` value ("review" or "fallback:0") to a copy block. */
function resolveCopy(langCopy, ref, lang, sceneId) {
  if (ref.startsWith("fallback:")) {
    const block = langCopy.fallback[Number(ref.slice("fallback:".length))];
    if (!block) throw new Error(`${lang}: no ${ref} for scene "${sceneId}"`);
    return block;
  }
  const key = COPY_BLOCKS[ref];
  if (!key) throw new Error(`scenes.json scene "${sceneId}" references unknown copy block "${ref}"`);
  const block = langCopy.screenshots[key];
  if (!block) throw new Error(`${lang}: copy.json has no block "${ref}" (${key}) for scene "${sceneId}"`);
  return block;
}

/* -------------------------------------------------------------------------- */
/* Device profiles                                                            */
/* -------------------------------------------------------------------------- */

/**
 * canvas   - final App Store asset size (must be an accepted App Store Connect size)
 * renderScale - Chrome renders at canvas*scale then we Lanczos-downsample for crisp text
 * screen   - the inner cutout the app screenshot is slotted into (keep its aspect ratio
 *            equal to the source screenshot aspect ratio so nothing is cropped)
 */
const DEVICES = {
  iphone: {
    label: "iPhone 6.9\"",
    outDir: "iphone-6.9",
    sourceDir: "iphone",
    canvas: { w: 1290, h: 2796 },
    renderScale: 2,
    screen: { w: 760, h: 1652 }, // 0.460 — matches 1206x2622 sources
    frame: 34,
    cornerOuter: 92,
    cornerInner: 64,
    device: { y: 792, rotateCycle: [-5, 3, 0, 0, -3, 2, 0, 4] },
    text: { x: 78, headlineY: 168, headlineSizes: [96, 86, 76], lineGap: 12, subY: 470, subSize: 44, headlineRightReserve: 230 },
    pill: { y: 616, h: 86, radius: 43, fontSize: 38, padLeft: 86, padRight: 34, iconCx: 48, iconR: 21, gap: 28 },
    badge: { x: 946, y: 648, r: 86, inner: 58 },
    notch: { w: 164, h: 45 },
    doodleBand: { top: 78, bottom: 742 }, // headline cap top ~= 98; pill row ends at 702, device starts at 792
    decor: {
      bigCircle: { cx: 1140, cy: 214, r: 212 },
      warmCircle: { cx: 138, cy: 2550, r: 260 },
      stars: [{ x: 1104, y: 452, size: 98 }, { x: 86, y: 2250, size: 60, fill: "#FFF8F2" }],
    },
  },
  ipad: {
    label: "iPad 13\"",
    outDir: "ipad-13",
    sourceDir: "ipad",
    // LANDSCAPE. The iPad app runs landscape and the captures are landscape once
    // scripts/normalize-ipad-screenshots.sh has un-rotated them. 2752x2064 is an accepted
    // App Store Connect 13" size and matches the already-shipped Ukrainian iPad set.
    canvas: { w: 2752, h: 2064 },
    renderScale: 1,
    screen: { w: 1776, h: 1332 }, // exactly 4:3 — matches 2752x2064 sources
    frame: 40,
    cornerOuter: 86,
    cornerInner: 54,
    // y=500 with a 1412-tall bezel leaves 152px below. The tablet must NOT sit lower: at
    // ±2° rotation a corner drops ~33px, and the drop shadow needs room under that or it
    // gets truncated against the canvas edge.
    device: { y: 500, rotateCycle: [-2, 1, 0, 0, -1, 2, 0, 1] },
    text: { x: 140, headlineY: 130, headlineSizes: [100, 88, 80], lineGap: 8, subY: 312, subSize: 50, headlineRightReserve: 520 },
    pill: { y: 360, h: 90, radius: 45, fontSize: 42, padLeft: 100, padRight: 40, iconCx: 56, iconR: 24, gap: 34 },
    badge: { x: 2440, y: 290, r: 92, inner: 62 },
    notch: null, // landscape iPad: front camera is a small dot centred on the long top edge
    // No bottom sweep: the tablet has to start at y=500 to stay large enough to read, which
    // leaves only ~50px between the pill row and the bezel — not enough for a curved stroke
    // that would not clip something.
    doodleBand: { top: 40, bottom: null },
    decor: {
      bigCircle: { cx: 2520, cy: 190, r: 300 },
      warmCircle: { cx: 170, cy: 1900, r: 320 },
      stars: [{ x: 2650, y: 740, size: 100 }, { x: 96, y: 1230, size: 72, fill: "#FFF8F2" }],
    },
  },
};

/* -------------------------------------------------------------------------- */
/* CLI                                                                        */
/* -------------------------------------------------------------------------- */

const arg = (name) => process.argv.find((a) => a.startsWith(`--${name}=`))?.split("=")[1];
const flag = (name) => process.argv.includes(`--${name}`);

const deviceArg = arg("device");
const localeArg = arg("locale");
const wantSheet = flag("sheet");
const allowSimilar = flag("allow-similar");

const deviceNames = deviceArg ? deviceArg.split(",").map((d) => d.trim()) : Object.keys(DEVICES);
for (const name of deviceNames) {
  if (!DEVICES[name]) {
    console.error(`Unknown device "${name}". Known: ${Object.keys(DEVICES).join(", ")}`);
    process.exit(1);
  }
}

const allCopy = JSON.parse(readFileSync(copyPath, "utf-8"));
/** scenes.json is keyed by device: the usable screens differ per device (see PROGRESS.md §4). */
const allScenes = JSON.parse(readFileSync(scenesPath, "utf-8"));

/* -------------------------------------------------------------------------- */
/* Text measurement                                                           */
/* -------------------------------------------------------------------------- */

/**
 * Manrope ExtraBold advance width, empirically ~0.56em averaged over Latin+Cyrillic
 * mixed case. Used only to (a) pick a headline size that fits and (b) lay pills out
 * left-to-right without overlapping. Being ~5% pessimistic is fine and safe.
 */
const AVG_ADVANCE = 0.56;
const textWidth = (text, fontSize) => text.length * fontSize * AVG_ADVANCE;

/**
 * Largest headline size from the candidate list whose longest line still fits.
 * `headlineRightReserve` keeps the top-right corner free for the burst marks, the star and
 * the tick badge, so a long Romanian line can never run under them.
 */
function fitHeadlineSize(lines, profile) {
  const maxWidth = profile.canvas.w - profile.text.x - profile.text.headlineRightReserve;
  const sizes = profile.text.headlineSizes;
  for (const size of sizes) {
    if (lines.every((line) => textWidth(line, size) <= maxWidth)) return size;
  }
  return sizes[sizes.length - 1];
}

const pillWidth = (text, profile) =>
  Math.round(profile.pill.padLeft + textWidth(text, profile.pill.fontSize) + profile.pill.padRight);

/* -------------------------------------------------------------------------- */
/* SVG pieces                                                                 */
/* -------------------------------------------------------------------------- */

const esc = (value) =>
  String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

const dataUri = (path) => `data:image/png;base64,${readFileSync(path).toString("base64")}`;

function headlineBlock(lines, profile) {
  const size = fitHeadlineSize(lines, profile);
  const { x, headlineY, lineGap } = profile.text;
  return lines
    .map(
      (line, i) =>
        `<text x="${x}" y="${headlineY + i * (size + lineGap)}" class="headline" font-size="${size}">${esc(line)}</text>`,
    )
    .join("\n  ");
}

/**
 * Pills are laid out left-to-right from the text margin, each sized to its own label.
 * The old scripts hard-coded per-screenshot pill x positions tuned for Ukrainian; a
 * longer Polish or Romanian word then overlapped the next pill. This cannot overlap.
 */
function pillRow(labels, iconColors, profile) {
  let x = profile.text.x + 10;
  const { y, h, radius, fontSize, padLeft, iconCx, iconR, gap } = profile.pill;
  return labels
    .map((label, i) => {
      const w = pillWidth(label, profile);
      const svg = `
  <g transform="translate(${x} ${y})" filter="url(#softShadow)">
    <rect width="${w}" height="${h}" rx="${radius}" fill="#FFF8F2"/>
    <circle cx="${iconCx}" cy="${h / 2}" r="${iconR}" fill="${iconColors[i] ?? "#F08030"}"/>
    <path d="M${iconCx - 10} ${h / 2}l${iconR * 0.45} ${iconR * 0.5} ${iconR * 0.8}-${iconR}" fill="none" stroke="#FFF8F2" stroke-width="${Math.round(iconR * 0.38)}" stroke-linecap="round" stroke-linejoin="round"/>
    <text x="${padLeft}" y="${h / 2 + fontSize * 0.36}" class="pill" font-size="${fontSize}">${esc(label)}</text>
  </g>`;
      x += w + gap;
      return svg;
    })
    .join("");
}

function star(x, y, size, fill = "#FBBF24") {
  return `<path d="M${x} ${y - size}l${size * 0.24} ${size * 0.62} ${size * 0.66} ${size * 0.28}-${size * 0.66} ${size * 0.28}-${size * 0.24} ${size * 0.62}-${size * 0.24}-${size * 0.62}-${size * 0.66}-${size * 0.28} ${size * 0.66}-${size * 0.28}z" fill="${fill}"/>`;
}

/**
 * Hand-drawn accent strokes. X scales with canvas width; Y comes from the profile's
 * `doodleBand`, which names two safe horizontal bands: `top` (above the headline cap
 * height) and `bottom` (the gap between the pill row and the top of the device mock).
 *
 * Do NOT go back to deriving Y from the canvas height or from device.y by ratio. The
 * phone and the landscape tablet have completely different vertical rhythms, and any
 * ratio that looks right on one puts a stroke straight through the headline or the pills
 * on the other. The original `burst` variant had two curves at y=232/370 — dead centre of
 * the headline's second line — which rendered as a strikethrough.
 */
function doodles(kind, profile) {
  const { w } = profile.canvas;
  const sx = w / 1290; // strokes were authored against the 1290-wide phone canvas
  const p = (n) => Math.round(n * sx);
  const { top, bottom } = profile.doodleBand;

  const arcTop = `<path d="M${p(62)} ${top}c${p(162)}-${p(46)} ${p(414)}-${p(52)} ${p(614)}-${p(12)}" class="doodle thin"/>`;
  const sweepTop = `<path d="M${p(72)} ${top}c${p(330)}-${p(34)} ${p(792)}-${p(28)} ${p(1068)} ${p(10)}" class="doodle"/>`;
  const sweepBottom = bottom === null
    ? ""
    : `<path d="M${p(88)} ${bottom}c${p(260)} ${p(34)} ${p(640)} ${p(34)} ${p(940)}-${p(4)}" class="doodle thin"/>`;
  // Radiating marks, deliberately confined to the top-right corner past the headline's
  // reserved width (see headlineRightReserve).
  const marks = `<path d="M${p(1110)} ${top + 8}l${p(36)} ${p(96)}M${p(1218)} ${top + 36}l-${p(80)} ${p(72)}M${p(1242)} ${top + 152}l-${p(112)}-${p(28)}" class="doodle"/>`;

  if (kind === "frame") return `${sweepTop}\n  ${sweepBottom}`;
  if (kind === "burst") return `${marks}\n  ${sweepBottom}`;
  return `${arcTop}\n  ${sweepBottom}`;
}

/** The device mock-up: bezel, screen cutout, the screenshot clipped into it. */
function deviceMock({ image, profile, rotate, id }) {
  const { frame, screen, cornerOuter, cornerInner, canvas, notch } = profile;
  const outerW = screen.w + frame * 2;
  const outerH = screen.h + frame * 2;
  const x = Math.round((canvas.w - outerW) / 2);
  const y = profile.device.y;
  const camera = notch
    ? `<rect x="${outerW / 2 - notch.w / 2}" y="${frame + 16}" width="${notch.w}" height="${notch.h}" rx="${notch.h / 2}" fill="#050201"/>`
    : `<circle cx="${outerW / 2}" cy="${frame / 2 + 6}" r="11" fill="#050201"/>`;
  return `
  <g transform="translate(${x} ${y}) rotate(${rotate} ${outerW / 2} ${outerH / 2})" filter="url(#deviceShadow)">
    <rect width="${outerW}" height="${outerH}" rx="${cornerOuter}" fill="#140500"/>
    <rect x="${frame}" y="${frame}" width="${screen.w}" height="${screen.h}" rx="${cornerInner}" fill="#FFF8F2"/>
    <clipPath id="clip-${id}">
      <rect x="${frame}" y="${frame}" width="${screen.w}" height="${screen.h}" rx="${cornerInner}"/>
    </clipPath>
    <image href="${image}" x="${frame}" y="${frame}" width="${screen.w}" height="${screen.h}" preserveAspectRatio="xMidYMid slice" clip-path="url(#clip-${id})"/>
    ${camera}
    <rect x="${frame + 30}" y="${frame + 30}" width="${screen.w - 60}" height="${screen.h - 60}" rx="${cornerInner - 16}" fill="none" stroke="#FFFFFF" stroke-opacity="0.13" stroke-width="2"/>
  </g>`;
}

function pageSvg({ profile, headline, sub, pills, pillIconColors, doodle, image, rotate, id }) {
  const { w, h } = profile.canvas;
  const scale = profile.renderScale;
  const b = profile.badge;
  const d = profile.decor;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${w * scale}" height="${h * scale}" viewBox="0 0 ${w} ${h}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#B51C00"/>
      <stop offset="0.45" stop-color="#F08030"/>
      <stop offset="1" stop-color="#5C1300"/>
    </linearGradient>
    <radialGradient id="glow" cx="34%" cy="36%" r="74%">
      <stop offset="0" stop-color="#FBBF24" stop-opacity="0.56"/>
      <stop offset="0.42" stop-color="#F08030" stop-opacity="0.18"/>
      <stop offset="1" stop-color="#3A1F00" stop-opacity="0"/>
    </radialGradient>
    <filter id="deviceShadow" x="-30%" y="-20%" width="160%" height="150%">
      <feDropShadow dx="0" dy="42" stdDeviation="34" flood-color="#190600" flood-opacity="0.52"/>
    </filter>
    <filter id="softShadow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="14" stdDeviation="12" flood-color="#190600" flood-opacity="0.24"/>
    </filter>
    <style>
      @font-face {
        font-family: "ManropeLocal";
        src: url("file://${fontPath}") format("truetype");
      }
      .headline, .sub, .pill {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
      }
      .headline { font-weight: 900; fill: #FFF8F2; letter-spacing: -1px; }
      .sub { font-weight: 760; fill: #FFE4CA; font-size: ${profile.text.subSize}px; }
      .pill { font-weight: 850; fill: #3A1F00; }
      .doodle { fill: none; stroke: #FFF8F2; stroke-width: ${Math.round(12 * (w / 1290))}; stroke-linecap: round; stroke-linejoin: round; opacity: 0.92; }
      .thin { stroke-width: ${Math.round(7 * (w / 1290))}; opacity: 0.76; }
    </style>
  </defs>
  <rect width="${w}" height="${h}" fill="url(#bg)"/>
  <rect width="${w}" height="${h}" fill="url(#glow)"/>
  <circle cx="${d.bigCircle.cx}" cy="${d.bigCircle.cy}" r="${d.bigCircle.r}" fill="#FFF8F2" opacity="0.08"/>
  <circle cx="${d.warmCircle.cx}" cy="${d.warmCircle.cy}" r="${d.warmCircle.r}" fill="#FBBF24" opacity="0.12"/>
  ${doodles(doodle, profile)}
  ${d.stars.map((s) => star(s.x, s.y, s.size, s.fill)).join("\n  ")}
  ${headlineBlock(headline, profile)}
  <text x="${profile.text.x + 4}" y="${profile.text.subY}" class="sub">${esc(sub)}</text>
  ${pillRow(pills, pillIconColors, profile)}
  ${deviceMock({ image, profile, rotate, id })}
  <g transform="translate(${b.x} ${b.y})" filter="url(#softShadow)">
    <circle cx="${b.r}" cy="${b.r}" r="${b.r}" fill="#FFF8F2"/>
    <circle cx="${b.r}" cy="${b.r}" r="${b.inner}" fill="#1B8E5A"/>
    <path d="M${b.r * 0.67} ${b.r * 1.02}l${b.r * 0.26} ${b.r * 0.26} ${b.r * 0.51}-${b.r * 0.67}" fill="none" stroke="#FFF8F2" stroke-width="${Math.round(b.r * 0.19)}" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
}

/* -------------------------------------------------------------------------- */
/* Render                                                                     */
/* -------------------------------------------------------------------------- */

function render(svgPath, jpgPath, profile) {
  const { w, h } = profile.canvas;
  const scale = profile.renderScale;
  const rawPath = `${jpgPath}.raw.png`;
  execFileSync(chromePath, [
    "--headless",
    "--disable-gpu",
    "--hide-scrollbars",
    `--window-size=${w * scale},${h * scale}`,
    `--screenshot=${rawPath}`,
    `file://${svgPath}`,
  ]);
  execFileSync("magick", [
    rawPath,
    "-filter", "Lanczos",
    "-resize", `${w}x${h}!`,
    "-strip",
    "-interlace", "Plane",
    "-sampling-factor", "4:4:4",
    "-quality", "96",
    jpgPath,
  ]);
  unlinkSync(rawPath);
}

/** Locales that actually have every source screenshot a scene asks for. */
function availableLocales(profile) {
  const deviceRoot = join(sourceRoot, profile.sourceDir);
  if (!existsSync(deviceRoot)) return [];
  return readdirSync(deviceRoot)
    .filter((entry) => !entry.startsWith("."))
    .filter((locale) => existsSync(join(deviceRoot, locale)))
    .sort();
}

function sceneFile(profile, locale, scene) {
  const name = scene.theme ? `${scene.screen}_${scene.theme}.png` : `${scene.screen}.png`;
  return join(sourceRoot, profile.sourceDir, locale, name);
}

/**
 * Guard against the nastiest failure mode this pipeline has: two scenes in one locale whose
 * source screenshots are the SAME APP SCREEN. You then ship two store images that look
 * identical but carry different captions, so at least one caption is a lie.
 *
 * This is not hypothetical. The Maestro `analysing_progress` capture races the AI: if
 * generation finishes first it screenshots the *result* screen instead. On iPad that
 * happened in bg, pt and ro — byte-identical to result_top_dark in bg and pt, and visually
 * the same screen but not byte-identical in ro. So an md5 check alone is not enough.
 *
 * Mean + standard deviation of the whole image is a cheap perceptual fingerprint that
 * separates these screens easily (the dark analysing screen sits at mean ~0.098, the result
 * screen with its large photo at ~0.269) while still matching two captures of the same
 * screen that differ only by a caret blink or a spinner frame.
 */
function findSimilarScenes(profile, locale, scenes) {
  const sigs = scenes.map((scene) => {
    const out = execFileSync("magick", [
      "identify", "-format", "%[fx:mean] %[fx:standard_deviation]", sceneFile(profile, locale, scene),
    ]).toString().trim().split(/\s+/).map(Number);
    return { scene, mean: out[0], sd: out[1] };
  });
  const clashes = [];
  for (let i = 0; i < sigs.length; i += 1) {
    for (let j = i + 1; j < sigs.length; j += 1) {
      if (Math.abs(sigs[i].mean - sigs[j].mean) < 0.002 && Math.abs(sigs[i].sd - sigs[j].sd) < 0.002) {
        clashes.push([sigs[i].scene, sigs[j].scene]);
      }
    }
  }
  return clashes;
}

let generated = 0;
const report = [];

for (const deviceName of deviceNames) {
  const profile = DEVICES[deviceName];
  const scenes = allScenes[deviceName];
  if (!scenes?.length) {
    console.warn(`  ! ${deviceName}: scenes.json has no scene list — skipped`);
    continue;
  }
  const locales = (localeArg ? localeArg.split(",").map((l) => l.trim()) : availableLocales(profile))
    .filter((locale) => {
      if (!LOCALE_TO_LANG[locale]) {
        console.warn(`  ! ${deviceName}/${locale}: no copy.json language mapped — skipped`);
        return false;
      }
      return true;
    });

  for (const locale of locales) {
    const lang = LOCALE_TO_LANG[locale];
    const langCopy = allCopy[lang];
    if (!langCopy) {
      console.warn(`  ! ${deviceName}/${locale}: copy.json has no "${lang}" — skipped`);
      continue;
    }

    const missing = scenes.filter((scene) => !existsSync(sceneFile(profile, locale, scene)));
    if (missing.length) {
      console.warn(
        `  ! ${deviceName}/${locale}: missing ${missing.length}/${scenes.length} source screenshots ` +
        `(${missing.map((m) => m.screen).join(", ")}) — SKIPPED, would produce a partial set`,
      );
      report.push({ device: deviceName, locale, status: "skipped", missing: missing.map((m) => m.screen) });
      continue;
    }

    const clashes = findSimilarScenes(profile, locale, scenes);
    if (clashes.length && !allowSimilar) {
      for (const [a, b] of clashes) {
        console.error(
          `  ! ${deviceName}/${locale}: scenes "${a.id}" (${a.screen}) and "${b.id}" (${b.screen}) are the ` +
          `SAME app screen. Two captions over one screen means one of them is a lie. Pick a different ` +
          `source screen, or pass --allow-similar if you are sure.`,
        );
      }
      report.push({ device: deviceName, locale, status: "skipped", similar: clashes.map(([a, b]) => `${a.id}=${b.id}`) });
      continue;
    }

    const outDir = join(here, profile.outDir, locale);
    rmSync(outDir, { recursive: true, force: true });
    mkdirSync(outDir, { recursive: true });

    scenes.forEach((scene, index) => {
      const block = resolveCopy(langCopy, scene.copy, lang, scene.id);
      const stem = `${String(index + 1).padStart(2, "0")}-${scene.id}`;
      const svgPath = join(outDir, `${stem}.svg`);
      const jpgPath = join(outDir, `${stem}.jpg`);

      writeFileSync(
        svgPath,
        pageSvg({
          profile,
          headline: block.headline,
          sub: block.sub,
          pills: block.pills,
          pillIconColors: scene.pillIconColors ?? ["#F08030", "#1B8E5A"],
          doodle: scene.doodle ?? "circle",
          image: dataUri(sceneFile(profile, locale, scene)),
          rotate: profile.device.rotateCycle[index % profile.device.rotateCycle.length],
          id: `${deviceName}-${locale}-${index}`,
        }),
      );
      render(svgPath, jpgPath, profile);
      unlinkSync(svgPath); // intermediate; multi-MB because sources are inlined base64
      generated += 1;
      console.log(`  ${profile.outDir}/${locale}/${basename(jpgPath)}  <- ${scene.screen}${scene.theme ? `_${scene.theme}` : ""}`);
    });

    report.push({ device: deviceName, locale, status: "ok", count: scenes.length });

    if (wantSheet) {
      const sheetDir = join(here, "previews");
      mkdirSync(sheetDir, { recursive: true });
      const sheet = join(sheetDir, `${profile.outDir}-${locale}-contact-sheet.jpg`);
      execFileSync("magick", [
        "montage",
        // Both of these are required. ImageMagick's default font configuration is broken on
        // this machine (it resolves the default font name to a bare `'` and dies with
        // "unable to read font"), so montage needs an explicit font file even though
        // `-label ""` means no text is actually drawn.
        "-font", fontPath,
        "-label", "",
        ...scenes.map((s, i) => join(outDir, `${String(i + 1).padStart(2, "0")}-${s.id}.jpg`)),
        "-tile", "4x",
        "-geometry", "+8+8",
        "-background", "#FFF8F2",
        "-resize", "440x",
        sheet,
      ]);
      console.log(`  sheet -> previews/${basename(sheet)}`);
    }
  }
}

console.log(`\n${generated} screenshots written.`);
for (const row of report) {
  if (row.status === "ok") console.log(`  OK      ${row.device}/${row.locale}  (${row.count})`);
  else if (row.missing) console.log(`  SKIPPED ${row.device}/${row.locale}  missing: ${row.missing.join(", ")}`);
  else console.log(`  SKIPPED ${row.device}/${row.locale}  duplicate screens: ${row.similar.join(", ")}`);
}
