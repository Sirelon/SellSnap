#!/usr/bin/env node
// Publishes scripts/release-notes.json to the `release-notes` Firestore collection that
// the in-app What's New feature reads from. firestore.rules denies all client writes to
// that collection, so this authenticates as a project member (gcloud) instead of going
// through the app's own Firebase config. See .claude/skills/release-notes/SKILL.md.

import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = "sellsnap-6e85c";
const RELEASE_NOTES_JSON = path.join(__dirname, "release-notes.json");
const KNOWN_ICONS = new Set(["feature", "improvement", "fix", "gift", "upload", "magic"]);
const LANGUAGE_CODES = ["en", "uk", "ru", "bg", "kk", "pl", "pt", "ro"];

const dryRun = process.argv.includes("--dry-run");

function toFirestoreValue(value) {
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(toFirestoreValue) } };
  }
  if (value && typeof value === "object") {
    return {
      mapValue: {
        fields: Object.fromEntries(
          Object.entries(value).map(([k, v]) => [k, toFirestoreValue(v)]),
        ),
      },
    };
  }
  throw new Error(`Unsupported value in release-notes.json: ${JSON.stringify(value)}`);
}

function encodeChange(change) {
  const fields = {
    id: toFirestoreValue(change.id),
    icon: toFirestoreValue(change.icon),
    title: toFirestoreValue(change.title),
    summary: toFirestoreValue(change.summary),
  };
  if (change.detail) fields.detail = toFirestoreValue(change.detail);
  return { mapValue: { fields } };
}

function encodeRelease(release) {
  return {
    version: toFirestoreValue(release.version),
    date: toFirestoreValue(release.date),
    active: toFirestoreValue(release.active !== false),
    changes: { arrayValue: { values: release.changes.map(encodeChange) } },
  };
}

function validate(releases) {
  const warnings = [];
  for (const release of releases) {
    if (!release.version) warnings.push("release missing version");
    if (!release.date) warnings.push(`${release.version}: missing date`);
    if (!Array.isArray(release.changes) || release.changes.length === 0) {
      warnings.push(`${release.version}: no changes`);
    }
    for (const change of release.changes ?? []) {
      if (!KNOWN_ICONS.has(change.icon)) {
        warnings.push(
          `${release.version}/${change.id}: icon "${change.icon}" not in WhatsNewIcons.kt vocabulary (falls back to "feature")`,
        );
      }
      for (const field of ["title", "summary"]) {
        const map = change[field] ?? {};
        for (const lang of LANGUAGE_CODES) {
          if (!map[lang]) warnings.push(`${release.version}/${change.id}: missing ${field}.${lang}`);
        }
      }
    }
  }
  return warnings;
}

const releases = JSON.parse(readFileSync(RELEASE_NOTES_JSON, "utf8"));
const warnings = validate(releases);

console.log(`Loaded ${releases.length} release(s) from ${RELEASE_NOTES_JSON}:`);
for (const release of releases) {
  console.log(`  - ${release.version} (${release.date}): ${release.changes.length} change(s)`);
}
if (warnings.length) {
  console.log(`\nWarnings (${warnings.length}):`);
  for (const w of warnings) console.log(`  ! ${w}`);
}

if (dryRun) {
  console.log("\n--dry-run: no network call made.");
  process.exit(0);
}

const token = execSync("gcloud auth print-access-token", { encoding: "utf8" }).trim();

const writes = releases.map((release) => ({
  update: {
    name: `projects/${PROJECT_ID}/databases/(default)/documents/release-notes/${release.version}`,
    fields: encodeRelease(release),
  },
}));

const response = await fetch(
  `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents:commit`,
  {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ writes }),
  },
);

if (!response.ok) {
  const body = await response.text();
  console.error(`\nPublish FAILED: ${response.status} ${response.statusText}\n${body}`);
  process.exit(1);
}

console.log(`\nPublished ${releases.length} release(s) to release-notes/ in ${PROJECT_ID}.`);
