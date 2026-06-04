import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";

const root = "/Users/sirelon/Projects/SellSnap";
const screenshotsDir = join(root, "Design/Screenshots/ipad");
const outDir = join(root, "Design/StoreScreenshotsIPad");
const fontPath = join(root, "composeApp/src/commonMain/composeResources/font/manrope_variable.ttf");
const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

mkdirSync(outDir, { recursive: true });

const W = 2752;
const H = 2064;
const RENDER_SCALE = 1;
const frame = 38;
const screenW = 1860;
const screenH = 1395;
const tabletW = screenW + frame * 2;
const tabletH = screenH + frame * 2;
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

function sourceImage(path) {
  return `data:image/png;base64,${readFileSync(path).toString("base64")}`;
}

function esc(value) {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function textLines(items, x, y, size = 118) {
  return items
    .map((line, index) => `<text x="${x}" y="${y + index * (size + 16)}" class="headline" font-size="${size}">${esc(line)}</text>`)
    .join("\n");
}

function pill({ x, y, text, iconColor = "#F08030" }) {
  const width = Math.max(300, 130 + text.length * 30);
  return `
    <g transform="translate(${x} ${y})" filter="url(#softShadow)">
      <rect width="${width}" height="92" rx="46" fill="#FFF8F2"/>
      <circle cx="54" cy="46" r="24" fill="${iconColor}"/>
      <path d="M42 46l10 11 18-25" fill="none" stroke="#FFF8F2" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
      <text x="98" y="60" class="pill">${esc(text)}</text>
    </g>`;
}

function star(x, y, size, fill = "#FBBF24") {
  return `<path d="M${x} ${y - size}l${size * 0.24} ${size * 0.62} ${size * 0.66} ${size * 0.28}-${size * 0.66} ${size * 0.28}-${size * 0.24} ${size * 0.62}-${size * 0.24}-${size * 0.62}-${size * 0.66}-${size * 0.28} ${size * 0.66}-${size * 0.28}z" fill="${fill}"/>`;
}

function doodles(kind) {
  if (kind === "burst") {
    return `
      <path d="M2440 104l54 134M2630 144l-118 104M2688 340l-172-42" class="doodle"/>
      <path d="M72 394c84 20 160 70 216 148M44 602c86-16 172 4 242 64" class="doodle thin"/>`;
  }
  if (kind === "frame") {
    return `
      <path d="M112 88c560-62 1270-44 1884 24" class="doodle"/>
      <path d="M128 598c510 62 1180 58 1640-8" class="doodle thin"/>`;
  }
  return `
    <path d="M104 86c272-78 710-82 1040-18" class="doodle thin"/>
    <path d="M128 622c330 78 740 70 1120-18" class="doodle thin"/>`;
}

function ipad({ image, x = 860, y = 510, rotate = 0, id }) {
  const cx = tabletW / 2;
  const cy = tabletH / 2;
  return `
    <g transform="translate(${x} ${y}) rotate(${rotate} ${cx} ${cy})" filter="url(#tabletShadow)">
      <rect width="${tabletW}" height="${tabletH}" rx="86" fill="#140500"/>
      <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="54" fill="#FFF8F2"/>
      <clipPath id="clip-${id}">
        <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="54"/>
      </clipPath>
      <image href="${image}" x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" preserveAspectRatio="xMidYMid slice" clip-path="url(#clip-${id})"/>
      <circle cx="${frame / 2 + 8}" cy="${tabletH / 2}" r="13" fill="#050201"/>
      <rect x="${frame + 28}" y="${frame + 28}" width="${screenW - 56}" height="${screenH - 56}" rx="40" fill="none" stroke="#FFFFFF" stroke-opacity="0.12" stroke-width="2"/>
    </g>`;
}

const copy = [
  {
    headline: ["Стартуйте", "на iPad"],
    sub: "OLX або гостьовий режим",
    doodle: "circle",
    tablet: { x: 836, y: 510, rotate: -2 },
    pills: [
      { x: 112, y: 710, text: "OLX", iconColor: "#1B8E5A" },
      { x: 462, y: 710, text: "Гість", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Нове оголошення", "на великому екрані"],
    sub: "Фото, AI і готова чернетка",
    doodle: "burst",
    tablet: { x: 806, y: 520, rotate: 1 },
    pills: [
      { x: 112, y: 730, text: "Фото" },
      { x: 462, y: 730, text: "AI", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["AI готує", "оголошення"],
    sub: "Бачите прогрес кожного кроку",
    doodle: "frame",
    tablet: { x: 830, y: 520, rotate: 0 },
    pills: [
      { x: 112, y: 730, text: "Статус", iconColor: "#1B8E5A" },
      { x: 462, y: 730, text: "Опис", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Перевірте все", "перед OLX"],
    sub: "Опис, ціна і фінальний крок",
    doodle: "circle",
    tablet: { x: 806, y: 520, rotate: 0 },
    pills: [
      { x: 112, y: 730, text: "Ціна", iconColor: "#FBBF24" },
      { x: 462, y: 730, text: "Публікація", iconColor: "#1B8E5A" },
    ],
  },
];

const pages = collectImages(screenshotsDir).map((path, index) => ({
  file: `ipad-store-screenshot-${String(index + 1).padStart(2, "0")}`,
  image: sourceImage(path),
  source: basename(path),
  ...(copy[index] ?? copy[copy.length - 1]),
}));

function svg(page, index) {
  const titleSize = page.headline[1].length > 14 ? 104 : 118;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W * RENDER_SCALE}" height="${H * RENDER_SCALE}" viewBox="0 0 ${W} ${H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#B51C00"/>
      <stop offset="0.45" stop-color="#F08030"/>
      <stop offset="1" stop-color="#5C1300"/>
    </linearGradient>
    <radialGradient id="glow" cx="32%" cy="42%" r="78%">
      <stop offset="0" stop-color="#FBBF24" stop-opacity="0.54"/>
      <stop offset="0.42" stop-color="#F08030" stop-opacity="0.18"/>
      <stop offset="1" stop-color="#3A1F00" stop-opacity="0"/>
    </radialGradient>
    <filter id="tabletShadow" x="-20%" y="-20%" width="140%" height="145%">
      <feDropShadow dx="0" dy="44" stdDeviation="38" flood-color="#190600" flood-opacity="0.52"/>
    </filter>
    <filter id="softShadow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="14" stdDeviation="12" flood-color="#190600" flood-opacity="0.24"/>
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
        font-size: 54px;
      }
      .pill {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
        font-weight: 850;
        font-size: 42px;
        fill: #3A1F00;
      }
      .doodle {
        fill: none;
        stroke: #FFF8F2;
        stroke-width: 14;
        stroke-linecap: round;
        stroke-linejoin: round;
        opacity: 0.92;
      }
      .thin {
        stroke-width: 8;
        opacity: 0.76;
      }
    </style>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <rect width="${W}" height="${H}" fill="url(#glow)"/>
  <circle cx="2460" cy="220" r="272" fill="#FFF8F2" opacity="0.08"/>
  <circle cx="146" cy="1820" r="300" fill="#FBBF24" opacity="0.12"/>
  ${doodles(page.doodle)}
  ${star(2408, 500, 120)}
  ${star(90, 1780, 72, "#FFF8F2")}
  ${textLines(page.headline, 112, 186, titleSize)}
  <text x="118" y="552" class="sub">${esc(page.sub)}</text>
  ${page.pills.map(pill).join("\n")}
  ${ipad({ image: page.image, ...page.tablet, id: `screen-${index + 1}` })}
  <g transform="translate(2280 692)" filter="url(#softShadow)">
    <circle cx="94" cy="94" r="94" fill="#FFF8F2"/>
    <circle cx="94" cy="94" r="64" fill="#1B8E5A"/>
    <path d="M64 96l24 24 48-64" fill="none" stroke="#FFF8F2" stroke-width="18" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
}

for (const entry of readdirSync(outDir)) {
  if (entry.startsWith("ipad-store-screenshot-") && (entry.endsWith(".jpg") || entry.endsWith(".png") || entry.endsWith(".svg"))) {
    unlinkSync(join(outDir, entry));
  }
}

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
  console.log(`${basename(jpgPath)} written from ${page.source}`);
}
