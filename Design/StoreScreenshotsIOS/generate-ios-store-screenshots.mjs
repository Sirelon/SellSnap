import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";

const root = "/Users/sirelon/Projects/SellSnap";
const screenshotsDir = join(root, "Design/Screenshots/iphone");
const outDir = join(root, "Design/StoreScreenshotsIOS");
const fontPath = join(root, "composeApp/src/commonMain/composeResources/font/manrope_variable.ttf");
const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

mkdirSync(outDir, { recursive: true });

const W = 1284;
const H = 2778;
const RENDER_SCALE = 2;
const screenW = 760;
const screenH = 1648;
const frame = 34;
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

function sourceImage(path) {
  const base64 = readFileSync(path).toString("base64");
  return `data:image/png;base64,${base64}`;
}

function esc(value) {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function textLines(items, x, y, size = 96) {
  return items
    .map((line, index) => `<text x="${x}" y="${y + index * (size + 12)}" class="headline" font-size="${size}">${esc(line)}</text>`)
    .join("\n");
}

function pill({ x, y, text, iconColor = "#F08030" }) {
  const width = Math.max(260, 112 + text.length * 27);
  return `
    <g transform="translate(${x} ${y})" filter="url(#softShadow)">
      <rect width="${width}" height="86" rx="43" fill="#FFF8F2"/>
      <circle cx="48" cy="43" r="21" fill="${iconColor}"/>
      <path d="M38 43l8 9 16-21" fill="none" stroke="#FFF8F2" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
      <text x="86" y="55" class="pill">${esc(text)}</text>
    </g>`;
}

function star(x, y, size, fill = "#FBBF24") {
  return `<path d="M${x} ${y - size}l${size * 0.24} ${size * 0.62} ${size * 0.66} ${size * 0.28}-${size * 0.66} ${size * 0.28}-${size * 0.24} ${size * 0.62}-${size * 0.24}-${size * 0.62}-${size * 0.66}-${size * 0.28} ${size * 0.66}-${size * 0.28}z" fill="${fill}"/>`;
}

function doodles(kind) {
  if (kind === "frame") {
    return `
      <path d="M72 78c330-34 792-28 1068 10" class="doodle"/>
      <path d="M88 598c260 34 640 34 940-4" class="doodle thin"/>`;
  }
  if (kind === "burst") {
    return `
      <path d="M1110 86l36 96M1218 114l-80 72M1242 230l-112-28" class="doodle"/>
      <path d="M66 232c54 20 104 54 140 104M28 370c58-8 114 6 162 42" class="doodle thin"/>`;
  }
  return `
    <path d="M62 58c162-46 414-52 614-12" class="doodle thin"/>
    <path d="M82 578c190 46 456 44 720-10" class="doodle thin"/>`;
}

function phone({ image, x = 222, y = 760, rotate = 0, id }) {
  const cx = phoneW / 2;
  const cy = phoneH / 2;
  return `
    <g transform="translate(${x} ${y}) rotate(${rotate} ${cx} ${cy})" filter="url(#phoneShadow)">
      <rect width="${phoneW}" height="${phoneH}" rx="92" fill="#140500"/>
      <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="64" fill="#FFF8F2"/>
      <clipPath id="clip-${id}">
        <rect x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" rx="64"/>
      </clipPath>
      <image href="${image}" x="${frame}" y="${frame}" width="${screenW}" height="${screenH}" preserveAspectRatio="xMidYMid slice" clip-path="url(#clip-${id})"/>
      <rect x="${phoneW / 2 - 82}" y="${frame + 16}" width="164" height="45" rx="22.5" fill="#050201"/>
      <rect x="${frame + 34}" y="${frame + 34}" width="${screenW - 68}" height="${screenH - 68}" rx="48" fill="none" stroke="#FFFFFF" stroke-opacity="0.14" stroke-width="2"/>
    </g>`;
}

const copy = [
  {
    headline: ["Стартуйте", "як зручно"],
    sub: "OLX або гостьовий режим",
    doodle: "circle",
    phone: { x: 216, y: 778, rotate: -5 },
    pills: [
      { x: 88, y: 620, text: "OLX", iconColor: "#1B8E5A" },
      { x: 380, y: 620, text: "Гість", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Є що продати?", "Сфотографуйте"],
    sub: "Текст і ціну підкаже AI",
    doodle: "burst",
    phone: { x: 220, y: 760, rotate: 3 },
    pills: [
      { x: 88, y: 620, text: "Фото" },
      { x: 380, y: 620, text: "AI", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Нове оголошення", "за хвилину"],
    sub: "Фото, підказка і готовий чернетка",
    doodle: "circle",
    phone: { x: 220, y: 760, rotate: 0 },
    pills: [
      { x: 88, y: 620, text: "Камера" },
      { x: 400, y: 620, text: "Галерея", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["AI готує", "оголошення"],
    sub: "Пише назву, опис і шукає ціну",
    doodle: "frame",
    phone: { x: 232, y: 804, rotate: 0 },
    pills: [
      { x: 92, y: 640, text: "Назва" },
      { x: 400, y: 640, text: "Опис", iconColor: "#1B8E5A" },
    ],
  },
  {
    headline: ["Бачите прогрес", "кожного кроку"],
    sub: "Без здогадок і прихованих станів",
    doodle: "frame",
    phone: { x: 232, y: 804, rotate: 0 },
    pills: [
      { x: 92, y: 640, text: "Статус", iconColor: "#1B8E5A" },
      { x: 400, y: 640, text: "Контроль", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Перевірте текст", "перед запуском"],
    sub: "Редагуйте назву й опис",
    doodle: "circle",
    phone: { x: 218, y: 770, rotate: -3 },
    pills: [
      { x: 92, y: 620, text: "Назва" },
      { x: 400, y: 620, text: "Опис", iconColor: "#1B8E5A" },
    ],
  },
  {
    headline: ["Ціна, категорія", "і деталі"],
    sub: "Усе готове для публікації",
    doodle: "circle",
    phone: { x: 220, y: 760, rotate: 0 },
    pills: [
      { x: 92, y: 620, text: "Ціна", iconColor: "#FBBF24" },
      { x: 400, y: 620, text: "Деталі", iconColor: "#1B8E5A" },
    ],
  },
  {
    headline: ["Публікуйте", "без сумнівів"],
    sub: "Фінальна перевірка перед OLX",
    doodle: "frame",
    phone: { x: 220, y: 760, rotate: 2 },
    pills: [
      { x: 92, y: 620, text: "Перевірка", iconColor: "#FBBF24" },
      { x: 450, y: 620, text: "OLX", iconColor: "#1B8E5A" },
    ],
  },
  {
    headline: ["Оголошення", "вже онлайн"],
    sub: "Статус і посилання під рукою",
    doodle: "burst",
    phone: { x: 220, y: 760, rotate: 0 },
    pills: [
      { x: 92, y: 620, text: "Статус", iconColor: "#1B8E5A" },
      { x: 400, y: 620, text: "OLX", iconColor: "#FBBF24" },
    ],
  },
  {
    headline: ["Керуйте", "своїми OLX"],
    sub: "Переглядайте оголошення в одному місці",
    doodle: "burst",
    phone: { x: 220, y: 760, rotate: 0 },
    pills: [
      { x: 92, y: 620, text: "Список" },
      { x: 400, y: 620, text: "Статус", iconColor: "#1B8E5A" },
    ],
  },
];

const pages = collectImages(screenshotsDir).map((path, index) => ({
  file: `ios-store-screenshot-${String(index + 1).padStart(2, "0")}`,
  image: sourceImage(path),
  source: basename(path),
  ...(copy[index] ?? copy[copy.length - 1]),
}));

function svg(page, index) {
  const titleSize = page.headline[0].length > 14 ? 86 : 96;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${W * RENDER_SCALE}" height="${H * RENDER_SCALE}" viewBox="0 0 ${W} ${H}">
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
    <filter id="phoneShadow" x="-30%" y="-20%" width="160%" height="150%">
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
        font-size: 44px;
      }
      .pill {
        font-family: "ManropeLocal", "Avenir Next", "Helvetica Neue", Arial, sans-serif;
        font-weight: 850;
        font-size: 38px;
        fill: #3A1F00;
      }
      .doodle {
        fill: none;
        stroke: #FFF8F2;
        stroke-width: 12;
        stroke-linecap: round;
        stroke-linejoin: round;
        opacity: 0.92;
      }
      .thin {
        stroke-width: 7;
        opacity: 0.76;
      }
    </style>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <rect width="${W}" height="${H}" fill="url(#glow)"/>
  <circle cx="1140" cy="214" r="212" fill="#FFF8F2" opacity="0.08"/>
  <circle cx="138" cy="2550" r="260" fill="#FBBF24" opacity="0.12"/>
  ${doodles(page.doodle)}
  ${star(1104, 452, 98)}
  ${star(86, 2250, 60, "#FFF8F2")}
  ${textLines(page.headline, 78, 162, titleSize)}
  <text x="82" y="462" class="sub">${esc(page.sub)}</text>
  ${page.pills.map(pill).join("\n")}
  ${phone({ image: page.image, ...page.phone, id: `screen-${index + 1}` })}
  <g transform="translate(930 642)" filter="url(#softShadow)">
    <circle cx="86" cy="86" r="86" fill="#FFF8F2"/>
    <circle cx="86" cy="86" r="58" fill="#1B8E5A"/>
    <path d="M58 88l22 22 44-58" fill="none" stroke="#FFF8F2" stroke-width="16" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
}

for (const entry of readdirSync(outDir)) {
  if (entry.startsWith("ios-store-screenshot-") && (entry.endsWith(".jpg") || entry.endsWith(".png") || entry.endsWith(".svg"))) {
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
  console.log(`${basename(jpgPath)} written from ${page.source}`);
}
