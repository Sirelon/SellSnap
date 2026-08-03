import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";

const root = "/Users/sirelon/Projects/SellSnap";
const baseOutDir = join(root, "Design/StoreScreenshots");
const fontPath = join(root, "composeApp/src/commonMain/composeResources/font/manrope_variable.ttf");
const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const screenshotsDir = join(root, "Design/Screenshots");
const copyPath = join(root, "Design/StoreScreenshots/copy.json");

const SUPPORTED_LANGS = ["uk", "en", "pl", "ro", "bg", "pt"];

const langArg = process.argv.find((a) => a.startsWith("--lang="))?.split("=")[1];
const langs = langArg === "all"
  ? SUPPORTED_LANGS
  : langArg
    ? langArg.split(",").map((l) => l.trim())
    : SUPPORTED_LANGS;

for (const lang of langs) {
  if (!SUPPORTED_LANGS.includes(lang)) {
    console.error(`Unknown language: ${lang}. Supported: ${SUPPORTED_LANGS.join(", ")}`);
    process.exit(1);
  }
}

const allCopy = JSON.parse(readFileSync(copyPath, "utf-8"));

const W = 1080;
const H = 1920;
const RENDER_SCALE = 2;
const screenW = 560;
const screenH = 1214;
const frame = 26;
const phoneW = screenW + frame * 2;
const phoneH = screenH + frame * 2;

const imageExtensions = new Set([".png", ".jpg", ".jpeg", ".webp"]);

function collectImages(dir) {
  return readdirSync(dir)
    .flatMap((entry) => {
      const path = join(dir, entry);
      const stat = statSync(path);
      if (stat.isDirectory()) return collectImages(path);
      const lower = entry.toLowerCase();
      return [...imageExtensions].some((ext) => lower.endsWith(ext)) ? [path] : [];
    })
    .sort();
}

const screenshot = (path) => {
  const base64 = readFileSync(path).toString("base64");
  return `data:image/png;base64,${base64}`;
};

const esc = (value) =>
  value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

const layouts = new Map([
  ["Screenshot_20260519_231837.png", {
    phone: { x: 250, y: 526, rotate: -9 },
    doodle: "circle",
    pillPositions: [{ x: 86, y: 382 }, { x: 336, y: 382 }],
    pillIconColors: [undefined, "#FBBF24"],
  }],
  ["Screenshot_20260519_232500.png", {
    phone: { x: 238, y: 512, rotate: 0 },
    doodle: "burst",
    pillPositions: [{ x: 92, y: 384 }, { x: 346, y: 384 }],
    pillIconColors: [undefined, "#FBBF24"],
  }],
  ["Screenshot_20260519_232525.png", {
    phone: { x: 260, y: 548, rotate: 0 },
    doodle: "frame",
    pillPositions: [{ x: 104, y: 404 }, { x: 358, y: 404 }],
    pillIconColors: [undefined, "#1B8E5A"],
  }],
  ["Screenshot_20260519_232545.png", {
    phone: { x: 238, y: 514, rotate: 4 },
    doodle: "circle",
    pillPositions: [{ x: 80, y: 386 }, { x: 330, y: 386 }],
    pillIconColors: ["#FBBF24", "#1B8E5A"],
  }],
  ["Screenshot_20260519_232644.png", {
    phone: { x: 248, y: 516, rotate: 0 },
    doodle: "burst",
    pillPositions: [{ x: 104, y: 384 }, { x: 356, y: 384 }],
    pillIconColors: [undefined, "#1B8E5A"],
  }],
  ["Screenshot_20260519_233853.png", {
    phone: { x: 244, y: 514, rotate: -2 },
    doodle: "frame",
    pillPositions: [{ x: 88, y: 386 }, { x: 390, y: 386 }],
    pillIconColors: ["#FBBF24", "#1B8E5A"],
  }],
  ["Screenshot_20260519_233904.png", {
    phone: { x: 248, y: 520, rotate: 0 },
    doodle: "burst",
    pillPositions: [{ x: 92, y: 386 }, { x: 350, y: 386 }],
    pillIconColors: ["#1B8E5A", "#FBBF24"],
  }],
]);

const fallbackLayouts = [
  {
    phone: { x: 244, y: 520, rotate: -5 },
    doodle: "circle",
    pillPositions: [{ x: 92, y: 386 }, { x: 346, y: 386 }],
    pillIconColors: [undefined, "#FBBF24"],
  },
  {
    phone: { x: 244, y: 520, rotate: 3 },
    doodle: "burst",
    pillPositions: [{ x: 92, y: 386 }, { x: 346, y: 386 }],
    pillIconColors: [undefined, "#1B8E5A"],
  },
];

function lines(items, x, y, size = 86) {
  return items
    .map((line, index) => `<text x="${x}" y="${y + index * (size + 10)}" class="headline" font-size="${size}">${esc(line)}</text>`)
    .join("\n");
}

function phone({ image, x, y, rotate = 0, id }) {
  const cx = phoneW / 2;
  const cy = phoneH / 2;
  return `
    <g transform="translate(${x} ${y}) rotate(${rotate} ${cx} ${cy})" filter="url(#phoneShadow)">
      <rect width="${phoneW}" height="${phoneH}" rx="70" fill="#190600"/>
      <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="48" fill="#FFF8F2"/>
      <clipPath id="clip-${id}">
        <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="48"/>
      </clipPath>
      <image href="${image}" x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" preserveAspectRatio="xMidYMid slice" clip-path="url(#clip-${id})"/>
      <circle cx="${phoneW / 2}" cy="${frame + 22}" r="10" fill="#5C2E00" opacity="0.75"/>
    </g>`;
}

function pill({ x, y, text, color = "#FFFFFF", textColor = "#3A1F00", iconColor = "#F08030" }) {
  const width = Math.max(232, 96 + text.length * 24);
  return `
    <g transform="translate(${x} ${y})" filter="url(#softShadow)">
      <rect width="${width}" height="76" rx="38" fill="${color}"/>
      <circle cx="42" cy="38" r="18" fill="${iconColor}"/>
      <path d="M33 38l7 8 13-17" fill="none" stroke="#FFF8F2" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
      <text x="76" y="48" class="pill" fill="${textColor}">${esc(text)}</text>
    </g>`;
}

function star(x, y, size, fill = "#FBBF24") {
  return `<path d="M${x} ${y - size}l${size * 0.24} ${size * 0.62} ${size * 0.66} ${size * 0.28}-${size * 0.66} ${size * 0.28}-${size * 0.24} ${size * 0.62}-${size * 0.24}-${size * 0.62}-${size * 0.66}-${size * 0.28} ${size * 0.66}-${size * 0.28}z" fill="${fill}"/>`;
}

function doodles(kind) {
  if (kind === "circle") {
    return `
      <path d="M58 42c116-34 312-38 448-10" class="doodle thin"/>
      <path d="M70 372c136 36 328 34 498-8" class="doodle thin"/>`;
  }
  if (kind === "frame") {
    return `
      <path d="M72 72c266-26 612-22 806 4" class="doodle"/>
      <path d="M84 394c210 24 488 24 696-4" class="doodle thin"/>`;
  }
  if (kind === "burst") {
    return `
      <path d="M902 70l28 76M976 92l-64 54M1000 176l-88-22" class="doodle"/>
      <path d="M66 143c40 14 78 38 106 76M31 244c46-8 90 2 128 31" class="doodle thin"/>`;
  }
  return `
    <path d="M84 334c188 35 380 34 578 0" class="doodle"/>
    <path d="M744 72c68-24 126-12 174 36" class="doodle thin"/>`;
}

function buildPages(lang, imagePaths) {
  const langCopy = allCopy[lang];
  return imagePaths.map((path, index) => {
    const name = basename(path);
    const layout = layouts.get(name) ?? fallbackLayouts[index % fallbackLayouts.length];
    const copy = langCopy.screenshots[name] ?? langCopy.fallback[index % langCopy.fallback.length];
    const pills = copy.pills.map((text, i) => ({
      x: layout.pillPositions[i].x,
      y: layout.pillPositions[i].y,
      text,
      iconColor: layout.pillIconColors[i],
    }));
    return {
      file: `google-play-screenshot-${String(index + 1).padStart(2, "0")}`,
      image: screenshot(path),
      source: name,
      headline: copy.headline,
      sub: copy.sub,
      phone: layout.phone,
      doodle: layout.doodle,
      pills,
    };
  });
}

function svg(page, index) {
  const imageId = `screen-${index + 1}`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W * RENDER_SCALE}" height="${H * RENDER_SCALE}" viewBox="0 0 ${W} ${H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#B51C00"/>
      <stop offset="0.42" stop-color="#F08030"/>
      <stop offset="1" stop-color="#5C1300"/>
    </linearGradient>
    <radialGradient id="glow" cx="36%" cy="40%" r="70%">
      <stop offset="0" stop-color="#FBBF24" stop-opacity="0.54"/>
      <stop offset="0.44" stop-color="#F08030" stop-opacity="0.18"/>
      <stop offset="1" stop-color="#3A1F00" stop-opacity="0"/>
    </radialGradient>
    <filter id="phoneShadow" x="-30%" y="-20%" width="160%" height="150%">
      <feDropShadow dx="0" dy="34" stdDeviation="30" flood-color="#190600" flood-opacity="0.48"/>
    </filter>
    <filter id="softShadow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="12" stdDeviation="11" flood-color="#190600" flood-opacity="0.24"/>
    </filter>
    <style>
      @font-face {
        font-family: "ManropeLocal";
        src: url("file://${fontPath}") format("truetype");
      }
      .headline {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
        font-weight: 900;
        fill: #FFF8F2;
        letter-spacing: -1px;
      }
      .sub {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
        font-weight: 760;
        fill: #FFE4CA;
        font-size: 34px;
      }
      .pill {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
        font-weight: 850;
        font-size: 34px;
      }
      .doodle {
        fill: none;
        stroke: #FFF8F2;
        stroke-width: 11;
        stroke-linecap: round;
        stroke-linejoin: round;
        opacity: 0.94;
      }
      .thin {
        stroke-width: 6;
        opacity: 0.78;
      }
    </style>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <rect width="${W}" height="${H}" fill="url(#glow)"/>
  <circle cx="960" cy="152" r="172" fill="#FFF8F2" opacity="0.08"/>
  <circle cx="126" cy="1780" r="210" fill="#FBBF24" opacity="0.12"/>
  ${doodles(page.doodle)}
  ${star(930, 320, 88)}
  ${star(76, 1510, 54, "#FFF8F2")}
  ${lines(page.headline, 68, 142, page.headline[0].length > 14 ? 78 : 86)}
  <text x="72" y="348" class="sub">${esc(page.sub)}</text>
  ${page.pills.map((p) => pill(p)).join("\n")}
  ${phone({ image: page.image, ...page.phone, id: imageId })}
  <g transform="translate(770 442)" filter="url(#softShadow)">
    <circle cx="80" cy="80" r="80" fill="#FFF8F2"/>
    <circle cx="80" cy="80" r="54" fill="#1B8E5A"/>
    <path d="M54 82l20 20 40-52" fill="none" stroke="#FFF8F2" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
}

const imagePaths = collectImages(screenshotsDir);

for (const lang of langs) {
  const outDir = langs.length === 1 && lang === "uk" ? baseOutDir : join(baseOutDir, lang);
  mkdirSync(outDir, { recursive: true });

  for (const entry of readdirSync(outDir)) {
    if (entry.startsWith("google-play-screenshot-") && (entry.endsWith(".png") || entry.endsWith(".jpg") || entry.endsWith(".svg"))) {
      unlinkSync(join(outDir, entry));
    }
  }

  const pages = buildPages(lang, imagePaths);

  for (const [index, page] of pages.entries()) {
    const svgPath = join(outDir, `${page.file}.svg`);
    const rawPath = join(outDir, `${page.file}.raw.png`);
    const jpgPath = join(outDir, `${page.file}.jpg`);
    writeFileSync(svgPath, svg(page, index));
    execFileSync(chromePath, [
      "--headless",
      "--disable-gpu",
      "--hide-scrollbars",
      `--window-size=${W * RENDER_SCALE},${H * RENDER_SCALE}`,
      `--screenshot=${rawPath}`,
      `file://${svgPath}`,
    ]);
    execFileSync("magick", [
      rawPath,
      "-filter",
      "Lanczos",
      "-resize",
      `${W}x${H}!`,
      "-strip",
      "-interlace",
      "Plane",
      "-sampling-factor",
      "4:4:4",
      "-quality",
      "96",
      jpgPath,
    ]);
    unlinkSync(rawPath);
    console.log(`[${lang}] ${basename(jpgPath)} written from ${page.source}`);
  }
}
