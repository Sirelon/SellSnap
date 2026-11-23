#!/usr/bin/env node
// Publishes one announcement file (scripts/announcements/<id>.json) to the `announcements`
// Firestore collection, as document <id>. firestore.rules denies client writes there, so this
// authenticates as a project member through gcloud. The file is the whole document: a publish
// overwrites it. See .claude/skills/force-update/SKILL.md.
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
for (const field of ["title", "body"]) {
  for (const lang of LANGUAGE_CODES) {
    if (content[field] && !content[field][lang]) warnings.push(`content.${field}.${lang} is missing (English shows)`);
  }
}
if (content.title?.uk !== content.title?.ru || content.body?.uk !== content.body?.ru) {
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
