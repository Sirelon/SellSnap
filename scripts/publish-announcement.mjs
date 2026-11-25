#!/usr/bin/env node
// Publishes one announcement file (scripts/announcements/<id>.json) to the `announcements`
// Firestore collection, as document <id>. firestore.rules denies client writes there, so this
// authenticates as a project member through gcloud. The file is the whole document: a publish
// overwrites it. See .claude/skills/announce/SKILL.md and .claude/skills/force-update/SKILL.md.
//
//   node scripts/publish-announcement.mjs scripts/announcements/<id>.json --dry-run
//   node scripts/publish-announcement.mjs scripts/announcements/<id>.json

import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";

const PROJECT_ID = "sellsnap-6e85c";
const LANGUAGE_CODES = ["en", "uk", "ru", "bg", "kk", "pl", "pt", "ro"];
const PLATFORMS = new Set(["android", "ios", "web", "desktop"]);
const VERSION = /^\d+(\.\d+)*$/;
// The paths of AppDeepLink.kt: `sellsnap://<path>` opens that screen.
const IN_APP_PATHS = new Set(["account", "my-ads", "messages", "generate"]);
const OLX_CONNECTIONS = new Set(["none", "expired", "connected"]);
// The first app version that knows in-app links and rules.olxConnection (SIR-202). Older apps
// drop the button, and ignore olxConnection so they show the dialog to everyone.
const IN_APP_MIN_VERSION = "4.2";

function compareVersions(a, b) {
  const left = a.split(".").map(Number);
  const right = b.split(".").map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const diff = (left[i] ?? 0) - (right[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

const file = process.argv.slice(2).find((arg) => !arg.startsWith("--"));
const dryRun = process.argv.includes("--dry-run");
if (!file) {
  console.error("Usage: node scripts/publish-announcement.mjs <file.json> [--dry-run]");
  process.exit(2);
}
const id = path.basename(file, ".json");
const doc = JSON.parse(readFileSync(file, "utf8"));

function toFirestoreValue(value) {
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (Number.isInteger(value)) return { integerValue: String(value) };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } };
  if (value && typeof value === "object") {
    return {
      mapValue: {
        fields: Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toFirestoreValue(v)])),
      },
    };
  }
  throw new Error(`Unsupported value: ${JSON.stringify(value)}`);
}

// Errors stop the publish: each one makes the app drop the document or show it to the wrong
// devices. Warnings are printed and the publish goes on.
const errors = [];
const warnings = [];
const rules = doc.rules ?? {};
const content = doc.content ?? {};
if (typeof doc.active !== "boolean") errors.push("`active` must be true or false");
for (const key of ["minVersion", "maxVersion"]) {
  if (key in rules && !(typeof rules[key] === "string" && VERSION.test(rules[key]))) {
    errors.push(`rules.${key} must be a string like "3.10", got ${JSON.stringify(rules[key])}`);
  }
}
for (const platform of rules.platforms ?? []) {
  if (!PLATFORMS.has(platform)) errors.push(`rules.platforms: unknown "${platform}"`);
}
if (!content.title?.en) errors.push("content.title.en is missing");
const inAppLink = typeof content.link === "string" && content.link.startsWith("sellsnap://");
if (content.link !== undefined && content.link !== "store" && !content.link?.startsWith?.("https://") && !inAppLink) {
  errors.push(`content.link must be "store", an https:// URL or sellsnap://<screen>, got ${JSON.stringify(content.link)}`);
}
if (inAppLink && !IN_APP_PATHS.has(content.link.slice("sellsnap://".length))) {
  errors.push(`content.link: unknown screen in "${content.link}"; known: ${[...IN_APP_PATHS].join(", ")}`);
}
if (inAppLink && !content.buttonText?.en) errors.push("an in-app link needs content.buttonText.en");
if ("olxConnection" in rules && !Array.isArray(rules.olxConnection)) {
  errors.push("rules.olxConnection must be a list, for example [\"none\"]");
}
for (const state of Array.isArray(rules.olxConnection) ? rules.olxConnection : []) {
  if (!OLX_CONNECTIONS.has(state)) errors.push(`rules.olxConnection: unknown "${state}"`);
}
const needsNewApp = inAppLink || (Array.isArray(rules.olxConnection) && rules.olxConnection.length > 0);
if (needsNewApp && !(VERSION.test(rules.minVersion ?? "") && compareVersions(rules.minVersion, IN_APP_MIN_VERSION) >= 0)) {
  errors.push(`an in-app link or rules.olxConnection needs rules.minVersion "${IN_APP_MIN_VERSION}" or later`);
}
for (const field of ["title", "body", "buttonText"]) {
  for (const lang of LANGUAGE_CODES) {
    if (content[field] && !content[field][lang]) warnings.push(`content.${field}.${lang} is missing (English shows)`);
  }
}
if (["title", "body", "buttonText"].some((field) => content[field]?.uk !== content[field]?.ru)) {
  errors.push("`ru` must be a copy of `uk` (AGENTS.md: no russian)");
}
const isGate = doc.active && content.link === "store" && rules.dismissible === false;
if (content.link === "store" && rules.dismissible === false && rules.showMode !== "everyLaunch") {
  warnings.push('a gate without showMode "everyLaunch" shows once and then lets the seller through');
}

console.log(`announcements/${id}${isGate ? "  [GATE]" : ""}`);
console.log(`  active: ${doc.active}`);
console.log(`  versions: ${rules.minVersion ?? "any"} .. ${rules.maxVersion ?? "any"}`);
console.log(`  platforms: ${(rules.platforms ?? []).join(", ") || "all"}`);
console.log(`  olxConnection: ${[].concat(rules.olxConnection ?? []).join(", ") || "any"}`);
console.log(`  link: ${content.link ?? "-"}${content.buttonText?.en ? ` (button: ${content.buttonText.en})` : ""}`);
console.log(`  title.en: ${content.title?.en}`);
console.log(`  body.en: ${content.body?.en ?? "-"}`);
for (const w of warnings) console.log(`  ! ${w}`);
if (errors.length) {
  for (const e of errors) console.error(`  ERROR ${e}`);
  process.exit(1);
}
if (dryRun) {
  console.log("\n--dry-run: no network call made.");
  process.exit(0);
}

const token = execSync("gcloud auth print-access-token", { encoding: "utf8" }).trim();
const response = await fetch(
  `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
  {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      writes: [{
        update: {
          name: `projects/${PROJECT_ID}/databases/(default)/documents/announcements/${id}`,
          fields: Object.fromEntries(Object.entries(doc).map(([k, v]) => [k, toFirestoreValue(v)])),
        },
      }],
    }),
  },
);
if (!response.ok) {
  console.error(`\nPublish FAILED: ${response.status} ${response.statusText}\n${await response.text()}`);
  process.exit(1);
}
console.log(`\nPublished announcements/${id} to ${PROJECT_ID}.`);
