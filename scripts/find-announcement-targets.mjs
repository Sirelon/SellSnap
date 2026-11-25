#!/usr/bin/env node
// Lists the installs that generated a listing, so an announcement can target them through
// `rules.userIds` (Firebase installation IDs) or `rules.olxUserIds` (OLX account IDs).
// Source: the generation log, `ad_generations/{installationId}/attempts/{id}`, which keeps
// attempts for a limited time (RETENTION in AdGenerationLogModule.mobile.kt). An install that
// never generated inside that window is not listed. See .claude/skills/announce/SKILL.md.
//
//   node scripts/find-announcement-targets.mjs                    # every install, newest first
//   node scripts/find-announcement-targets.mjs --country ua       # one OLX market
//   node scripts/find-announcement-targets.mjs --never-published  # installs with no publish in the log
//   node scripts/find-announcement-targets.mjs --since 2026-11-25T10:40 --until 2026-11-25T11:00
//   node scripts/find-announcement-targets.mjs --json             # machine-readable output

import { execSync } from "node:child_process";

const PROJECT_ID = "sellsnap-6e85c";
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const PAGE = 300;

const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};
const country = option("--country");
const since = option("--since") ? new Date(option("--since") + (option("--since").endsWith("Z") ? "" : "Z")) : null;
const until = option("--until") ? new Date(option("--until") + (option("--until").endsWith("Z") ? "" : "Z")) : null;
const neverPublished = args.includes("--never-published");
const asJson = args.includes("--json");

const token = execSync("gcloud auth print-access-token", { encoding: "utf8" }).trim();
const headers = {
  Authorization: `Bearer ${token}`,
  "x-goog-user-project": PROJECT_ID,
  "Content-Type": "application/json",
};

// A collection-group query with no `where` or `orderBy` needs no composite index; filtering
// happens here instead (creating the index is the owner's call).
async function fetchAttempts() {
  const attempts = [];
  for (let offset = 0; ; offset += PAGE) {
    const response = await fetch(`${BASE}:runQuery`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        structuredQuery: {
          from: [{ collectionId: "attempts", allDescendants: true }],
          select: { fields: ["countryCode", "createdAt", "didPublish", "olxAccountId"].map((fieldPath) => ({ fieldPath })) },
          offset,
          limit: PAGE,
        },
      }),
    });
    if (!response.ok) throw new Error(`runQuery ${response.status}: ${await response.text()}`);
    const rows = (await response.json()).filter((row) => row.document);
    for (const { document } of rows) {
      const f = document.fields ?? {};
      attempts.push({
        installationId: document.name.split("/ad_generations/")[1]?.split("/")[0],
        countryCode: f.countryCode?.stringValue ?? null,
        createdAt: f.createdAt?.timestampValue ? new Date(f.createdAt.timestampValue) : null,
        didPublish: f.didPublish?.booleanValue === true,
        olxAccountId: f.olxAccountId?.integerValue ? Number(f.olxAccountId.integerValue) : null,
      });
    }
    if (rows.length < PAGE) return attempts;
  }
}

const byInstall = new Map();
for (const attempt of await fetchAttempts()) {
  if (!attempt.installationId) continue;
  const install = byInstall.get(attempt.installationId) ?? {
    installationId: attempt.installationId,
    countries: new Set(),
    attempts: 0,
    published: 0,
    olxAccountIds: new Set(),
    times: [],
  };
  install.attempts += 1;
  if (attempt.countryCode) install.countries.add(attempt.countryCode);
  if (attempt.didPublish) install.published += 1;
  if (attempt.olxAccountId) install.olxAccountIds.add(attempt.olxAccountId);
  if (attempt.createdAt) install.times.push(attempt.createdAt);
  byInstall.set(attempt.installationId, install);
}

const installs = [...byInstall.values()]
  .map((install) => ({
    ...install,
    countries: [...install.countries],
    olxAccountIds: [...install.olxAccountIds],
    lastAttempt: install.times.length ? new Date(Math.max(...install.times)) : null,
    inWindow: install.times.some((t) => (!since || t >= since) && (!until || t <= until)),
  }))
  .filter((install) => !country || install.countries.includes(country))
  .filter((install) => !neverPublished || install.published === 0)
  .filter((install) => (!since && !until) || install.inWindow)
  .sort((a, b) => (b.lastAttempt ?? 0) - (a.lastAttempt ?? 0));

if (asJson) {
  console.log(JSON.stringify(installs.map(({ times, inWindow, ...rest }) => rest), null, 2));
} else {
  console.log("installationId\tcountry\tattempts\tpublished\tlastAttempt(UTC)\tolxAccountIds");
  for (const i of installs) {
    console.log([
      i.installationId,
      i.countries.join(",") || "-",
      i.attempts,
      i.published,
      i.lastAttempt?.toISOString().slice(0, 16) ?? "-",
      i.olxAccountIds.join(",") || "-",
    ].join("\t"));
  }
  console.log(`\n${installs.length} installs`);
}
