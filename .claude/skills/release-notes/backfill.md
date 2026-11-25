# Backfill: more than one version missing

Read this when more than one version is missing from `scripts/release-notes.json`. It adds
to the steps in `SKILL.md`; it does not replace them.

## Scope (step 1)

- **Order by version-bump commit, not by version name string** — names don't sort reliably
  (`1.10` vs `1.9`).
- If `version.properties` is younger than the repo, earlier versions live in whatever
  tracked the version before it. Check `git log --follow -- androidApp/build.gradle.kts`
  for `versionName =` / `versionCode =` lines that predate the switch to
  `version.properties`.
- If `scripts/release-notes.json` does not exist yet, every version is missing.

Done when every `VERSION_NAME` ever released, plus any pre-`version.properties` version,
is on one list in bump-commit order.

## First-ever version (step 2)

The first version has no prior release to diff against. Don't run a deep per-commit
archaeology pass over the entire pre-history. Either hand-author a short "initial release"
entry from the file tree at that commit, or run one lighter-touch agent instructed to
summarize the app's core capability set at that point rather than itemize every commit.

## Fan-out (step 3)

Run one agent per range, in parallel (Agent tool), with the model, effort, brief and JSON
output that step 3 sets. Give each agent the previous version's published entry in its
prompt so it can apply the content-not-ancestry check. The editorial pass (step 4) then
runs once over all ranges' findings.

Done when every range has one agent result and the first-ever version has its entry.
