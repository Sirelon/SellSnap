---
name: release-notes
description: >-
  Reconstruct what shipped in each SellSnap release from git diffs (not commit messages),
  write user-facing "What's New" copy in all 8 app languages, and publish it to the
  `release-notes` Firestore collection the in-app What's New feature reads from. Use when
  the user says "generate release notes", "backfill release notes", "publish what's new",
  or after `sellsnap-release` ships a version and the in-app content needs updating too.
  Separate from the `sellsnap-release` skill: that one writes store-listing changelogs
  (Play/TestFlight); this one writes the in-app dialog/version-history content.
---

# SellSnap release notes (in-app "What's New")

Mine git history for what actually shipped per version, turn it into user-facing copy in
all 8 app languages, and push it to Firestore so the app's What's New prompt and version
history screen (`composeApp/.../features/whatsnew/`) have real content.

## 0. What you're populating

- **Firestore collection**: `release-notes`, one document per version, keyed by version
  name (`release-notes/2.2`). Rules (`firestore.rules`) allow public read, deny all client
  writes — publishing requires an authenticated call (step 7), not the app's own SDK path.
- **Document shape** — every field optional/defaulted on the read side
  (`ReleaseResponse.kt`), so a bad write degrades gracefully, but write it complete:
  ```json
  {
    "version": "2.2",
    "date": "2026-08-19",
    "active": true,
    "changes": [
      {
        "id": "kebab-case-slug",
        "icon": "feature",
        "title": { "en": "...", "uk": "...", "ru": "...", "bg": "...", "kk": "...", "pl": "...", "pt": "...", "ro": "..." },
        "summary": { "en": "...", "...": "..." },
        "detail": { "en": "...", "...": "..." }
      }
    ]
  }
  ```
  `detail` is optional per change — omit the key entirely for a change with nothing more
  to say than its summary (the UI only makes a row tappable when `detail` is present).
- **Icon vocabulary** — `releaseChangeIcon()` in `WhatsNewIcons.kt` only recognizes:
  `feature`, `improvement`, `fix`, `gift`, `upload`, `magic` (anything else silently
  falls back to `feature`'s sparkle icon). Pick from this list; if a release genuinely
  needs a new one, add the drawable + `when` branch in that file first.
- **Language codes**: `en` (base, and the fallback `ReleaseMapper.kt` uses when a locale
  key is missing), `uk`, `ru`, `bg`, `kk`, `pl`, `pt`, `ro` — the same 8 as
  `composeApp/src/commonMain/composeResources/values*/`. **`ru` is a verbatim copy of
  `uk`, never a real Russian translation** — same rule as the `localize` agent
  (`.claude/agents/localize.md`), because the app has no audience that's Russian-speaking
  but not Ukrainian-speaking.
- **Source of truth**: `scripts/release-notes.json` — tracked in git (unlike
  `sellsnap-release`'s gitignored `.claude/tmp/release-metadata/`; this content is worth
  keeping history on). Array of release objects, newest first, same shape as the Firestore
  doc plus the `version` field doubling as its own key.

## 1. Scope the run

```bash
git log --reverse --format='%H|%ad|%s' --date=short -- version.properties
```

Compare versions already present in `scripts/release-notes.json` against every
`VERSION_NAME` that ever appeared in `version.properties` (and, if the file is younger
than the repo, in whatever tracked the version before it — check
`git log --follow -- androidApp/build.gradle.kts` for `versionName =` / `versionCode =`
lines predating the switch to `version.properties`). Generate only the versions missing
from the JSON — this is normally just the one version `sellsnap-release` most recently
shipped, not a full backfill, unless the JSON file doesn't exist yet.

**Order by version-bump commit, not by version name string** — names don't sort reliably
(`1.10` vs `1.9`). **Watch for version-code gaps** (e.g. code 3 → 5, or 7 → 10): a skipped
code usually means a build was cut in Play Console / Xcode without a matching git commit —
note it in a `VERSIONS.md` next to the JSON and move on; there's no diff to mine for a
code that was never committed.

## 2. Range boundaries

Version *N*'s range is `<bump-commit-of-(N-1)>..<bump-commit-of-N>` — the commit that
changed `version.properties` to *N* is what **closes** *N*'s range (confirmed for this
repo: version bumps land at/near ship time, not at the start of work on a release).

**The first-ever version is a special case** — no prior release to diff against. Don't
run a deep per-commit archaeology pass over the entire pre-history; either hand-author a
short "initial release" entry from the file tree at that commit, or run one lighter-touch
agent instructed to summarize the app's core capability set at that point rather than
itemize every commit.

## 3. Per-range archaeology (fan out, one agent per range)

Run agents in parallel (Agent tool, one per range), `sonnet` model, `high` effort — this
is diff-reading and classification against fairly detailed rules, not creative writing,
but the tag-tree-verification and relation logic (below) need real judgment.

Give each agent:
- The range (`git log <range>`, `git diff <range> -- <path>`, `git show <sha>`).
- In-scope paths: `composeApp/`, `androidApp/`, `iosApp/`, `shared/`. Out-of-scope (never
  report as a release item): `fastlane/`, `scripts/`, `store/`, `docs/`, `.claude/`,
  `AGENTS.md`, `CLAUDE.md`, CI config, this skill's own output files.
- **Commit messages are hints, never evidence.** If a subject contradicts the diff, the
  diff wins. Never conclude "nothing shipped" from unremarkable subjects alone.
- Classification: `features` (user couldn't do this before) / `improvements` (already
  worked, now better) / `fixes` (was wrong, now correct) / `internal` (refactors, DI,
  build, deps, tests — never user-visible). Don't file polish as a fix.
- `user_impact`: `major` (a skipped release would be noticeably missing this) / `minor`
  (real but easy to miss) / `invisible` (technically user-facing, nobody would mention
  it). Zero `major` items in a release is normal — don't grade on a curve.
- **Verify "not already shipped" by content, not ancestry**: before reporting something
  that touches the same screen/capability as an item already in the previous version's
  published entry (paste that entry into the prompt), diff the previous tag's actual file
  content — `git show <prev-version-tag-or-commit>:<path>` — not just
  `git merge-base --is-ancestor`, which can lie under this repo's branch topology.
- Output: strict JSON matching the schema in `RELEASE_NOTES_CONTENT_PIPELINE.md`-style
  (`features`/`improvements`/`fixes`/`internal`/`uncertain`, each finding with `title`,
  `what` (plain description, no marketing tone — editorial pass writes final copy),
  `evidence` (file/symbol, never a commit message), `user_impact`, `confidence`). Set
  `maintenance_only: true` with empty arrays if nothing user-facing shipped — a correct,
  expected answer for a chunk of releases. Never invent an item.

## 4. Editorial pass

One pass over all ranges' findings (do this yourself inline, or one strong-model agent —
this step needs better judgment than the extraction step):

- Drop `maintenance_only` releases' arrays (they stay in the output as `active: true` with
  `changes: []` only if you want them to appear as a version-history entry at all —
  usually just skip publishing a Firestore doc for a release with zero user-facing
  changes rather than showing an empty card).
- Drop everything `invisible`.
- Merge `related_to`-linked findings into one item.
- Assign an `icon` from the vocabulary in step 0.
- Write final English copy: `title` short (a few words), `summary` one sentence, `detail`
  only when there's genuinely more to say (omit the key otherwise).
- **Order by importance, not date**: new top-level capability first, then a new capability
  the user couldn't do at all, then restored/fixed access, then visible improvements,
  then fixes.
- Consider merging thin consecutive releases into one card (propose to the user, don't
  do it silently) — but don't do this for the currently-running backfill without asking,
  since it changes which version a change is attributed to.

## 5. Translate

The English copy is done — translate `title`/`summary`/`detail` into `uk`, `bg`, `kk`,
`pl`, `pt`, `ro` (and copy `uk`'s result verbatim into `ru`, never re-translate it).

Launch the `localize` agent (Agent tool, `subagent_type: localize`) with a prompt that
makes clear the input is **release-notes JSON content, not `strings.xml`** — it still
owns this repo's translation voice/rules (adapt don't transliterate, marketplace/OLX
domain terms, Ukrainian avoids broadcast/TV vocabulary for "publish", brand names stay
in Latin script) and the `ru`-copies-`uk` rule applies identically. Give it the drafted
English `changes` array and ask it to return the same array with `title`/`summary`/
`detail` each expanded from a bare string into the `{en, uk, ru, bg, kk, pl, pt, ro}` map.

## 6. Write `scripts/release-notes.json`

Merge the new version(s) into the array, newest first, `active: true`. This file is
tracked — commit it (or leave it for the user to commit, matching how `sellsnap-release`
leaves its own file changes uncommitted; ask which they want if unclear).

## 7. Publish to Firestore

`firestore.rules` blocks all client writes to `release-notes/*`, so this can't go through
the app's own Firebase config — it needs an authenticated call as a project member.
`gcloud` and `firebase` CLIs in this environment are already logged in as an account with
access to the `sellsnap-6e85c` project (`firebase projects:list` to confirm) — reuse that
rather than provisioning a new service-account key:

```bash
node scripts/publish-release-notes.mjs --dry-run   # prints what would be written, no network call
node scripts/publish-release-notes.mjs             # real publish
```

The script (create it if missing) should: read `scripts/release-notes.json`, get a bearer
token via `gcloud auth print-access-token`, and batch every release into **one** atomic
Firestore REST `:commit` call —
`POST https://firestore.googleapis.com/v1/projects/sellsnap-6e85c/databases/(default)/documents:commit`
— so a partial publish is never a state the app can observe. Warn (don't fail) on any
`icon` value not in `WhatsNewIcons.kt`'s vocabulary. Always dry-run first.

## 8. Report back

Show the user the English copy for every version just published (short enough to read in
full). Confirm the publish call succeeded and how many documents were written. Note that
`scripts/release-notes.json` changed and isn't committed unless they said to commit it.
