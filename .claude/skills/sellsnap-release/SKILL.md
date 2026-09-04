---
name: sellsnap-release
description: >-
  Bump SellSnap's version, write the "What's New" release notes for every supported
  language on both stores, and ship a new internal/beta build. Use when the user says
  "release a new version", "ship version X.Y", "cut a release", "publish an update",
  "bump the version and publish", or asks what's new in the current build. Covers Play
  Store internal testing + TestFlight beta only — promoting to production (Play) or
  submitting to the App Store is a separate manual step, never run by this skill. After
  a real ship, hand off to the `release-notes` skill to publish the in-app What's New
  content — this skill writes store-listing changelogs only, it doesn't touch Firestore.
---

# SellSnap release

Merge `origin/main`, bump `version.properties`, translate the release notes into all
supported languages, write them where each store's fastlane action actually reads them
from, then run `scripts/ship.sh` to build and upload. Fully autonomous once started — no pause before
upload, per standing preference — but everything it's about to publish gets generated
as files first, so it's all visible in the diff.

**Scope is beta/internal only.** This skill runs `fastlane android beta` (Play internal
track) and `fastlane ios beta` (TestFlight). It never runs `fastlane android release`
(promote to production) or `fastlane ios release` (App Store submission) — those stay
manual, separate actions the user triggers themselves when ready.

## 1. Pull the latest first

Before reading versions or drafting anything, bring the branch up to date:

```bash
git fetch origin && git merge origin/main
```

A release cut from a stale branch ships without whatever landed on `main` since, and its
changelog silently omits those features — the notes are drafted from the git range, so
anything not merged in cannot appear in them. Resolve any conflict, then run
`./gradlew :composeApp:compileAndroidMain` and `./gradlew :composeApp:jvmTest` before
going further; `ship.sh` compiles too, but finding a broken merge after the version has
already been bumped wastes a build number.

`.claude/tmp/release-metadata/` is gitignored, so a merge never touches the changelog
files — but `store/copy/store-listing.md` is tracked and `main` may have edited it. If
that file also has uncommitted edits from an earlier release attempt, the merge will
refuse; those edits get rewritten in step 7 anyway, so discard them (`git checkout --` on
that one file) and let the merge through rather than reconciling both by hand.

## 2. Locale codes (looked up once, reuse — don't re-derive)

Google Play and App Store Connect use different locale strings for the same language.
More importantly, **neither store's set is "all 8 in-app languages"** — each store only
has whatever listing locales someone actually configured for it, which is smaller than
and different from the in-app-supported language list. Confirmed for Play by actually
downloading the live listing (`bundle exec fastlane run download_from_play_store
package_name:com.sirelon.sellsnap json_key:fastlane/google-play-key.json
metadata_path:<fresh-empty-dir>` — use a directory that doesn't already exist, supply
silently no-ops on one that does):

| Language | Google Play locale | Play listing exists? | App Store / TestFlight locale | Apple support |
| --- | --- | --- | --- | --- |
| English | `en-US` | **No** — no title configured, confirmed by download | `en-US` | yes — confirmed live in App Store Connect |
| Ukrainian | `uk` | Yes | `uk` | yes |
| Polish | `pl-PL` | Yes | `pl` | yes |
| Romanian | `ro` | Yes | `ro` | yes |
| Bulgarian | `bg` | Yes | — | not supported by Apple |
| Portuguese (Portugal) | `pt-PT` | Yes | `pt-PT` | yes |
| Kazakh | `kk` | Yes | — | not supported by Apple |

**No russian *store listing*, on either store, by product decision** — not an
Apple/Google support gap. This is scoped to store metadata only (descriptions,
keywords, promotional text, this skill's release notes): never add a `ru`/`ru-RU`
folder anywhere under `.claude/tmp/release-metadata/`, never add a russian section to
`store/copy/store-listing.md`, and if a stray `ios/ru/` or `android/ru-RU/` directory
ever reappears, delete it rather than filling it in. **This does not apply to the
in-app UI** — `composeApp/.../composeResources/values-ru/` still exists and must stay a
byte-identical copy of `values-uk` (see `.claude/agents/localize.md`); it's a fallback
so russian-locale devices see Ukrainian, not a real translation. Don't conflate the two.

**Android: 6 real locales** — `uk, pl-PL, ro, bg, pt-PT, kk`. Do not add `en-US` back to
the Android changelog automation without first actually creating that Play Store listing
(title, descriptions, everything — see `store/copy/store-listing.md` for drafted copy) —
pushing just a changelog for a locale with no base listing fails the whole edit (Google
validates atomically at commit time, not per-locale, so the failure can surface on
whichever locale it happens to check first, not necessarily the one that's actually
missing something).

**iOS: 5 real locales** — `en-US, uk, pl, ro, pt-PT`. Confirmed live in App Store Connect
(2026-08-27, via an actual `fastlane ios release` run): `en-US` exists as a localization
but was still missing Description/Keywords/Support URL (first-ever production submission
for this app — those fields were never backfilled); `uk`/`pl`/`ro`/`pt-PT` already had
complete listings with no missing-field errors.

If the discovered reality ever changes (e.g. an English Play listing gets added
properly), update this table and `ANDROID_LOCALES` in `scripts/ship.sh` together — they
must stay in sync or the guard in step 9 will check locales that no longer match what
`metadata_path` actually contains.

## 3. Where the text actually goes

These are mechanical inputs for fastlane, not content worth versioning — they live under
`.claude/tmp/release-metadata/`, which is gitignored, and get regenerated fresh every
release. The Fastfile points `supply`/`deliver` at this directory explicitly via
`metadata_path:` (its default location is inside `fastlane/`, which we deliberately don't
use, so these never end up tracked or pushed to the repo).

| File | Read by | Notes |
| --- | --- | --- |
| `.claude/tmp/release-metadata/android/<play-locale>/changelogs/<version_code>.txt` | `upload_to_play_store` (supply), automatically via `metadata_path: ANDROID_METADATA_DIR` | Filename **must** be the new numeric version code, e.g. `9.txt`. One file per real Play locale (6 — see step 2, not all 8 in-app languages). supply scans every locale folder that exists under this path, so don't leave stray folders for locales Play doesn't actually have a listing for. |
| `.claude/tmp/release-metadata/ios/<appstore-locale>/release_notes.txt` | `upload_to_app_store` (deliver, `ios release` lane, via `metadata_path: IOS_METADATA_DIR`) **and** the `ios beta` lane's `localized_build_info` (hand-read in the Fastfile, since pilot has no folder convention) | Same file, same text serves both TestFlight's "What to Test" and the eventual App Store release notes. One file per App Store locale (5 files), overwritten each release — no version number in the filename. |
| `.claude/tmp/release-metadata/ios/RELEASE_NOTES_VERSION` | `scripts/ship.sh` (guard only, not read by fastlane) | Plain text, just the version code, e.g. `9`. Proves the iOS notes were actually refreshed for this build before shipping. |
| `.claude/tmp/release-metadata/ios/<appstore-locale>/promotional_text.txt` | `upload_to_app_store` (deliver, `ios release` lane only — **not** read by the beta/TestFlight lane, Apple has no such concept for TestFlight) | See step 6 — leave alone by default. |
| `store/copy/store-listing.md` | Nobody automatically — tracked, human reference doc | Update so it keeps describing current state, matching every other doc in this repo. This is the only piece of this whole flow that belongs in git. |

`ship.sh` hard-fails if any of the 6 Android changelog files or the iOS
`RELEASE_NOTES_VERSION` marker don't match the new version code — so do not skip a
locale, and do not run `ship.sh` before finishing this skill's writes.

## 4. Work out the version numbers

Read `version.properties`. `NEW_CODE = CURRENT_CODE + 1` — always, every release. Ask
the user (or infer from their message) whether `VERSION_NAME` changes this time:

- "release 2.2" / "bump minor to 2.2" → `NEW_NAME = 2.2`, pass it to `ship.sh` as `$1`.
- "ship a hotfix" / "just bump the build" / no version mentioned → `VERSION_NAME` stays
  as-is, run `ship.sh` with no argument.

**A `VERSION_NAME` already released on the App Store forces a marketing bump, whatever
the user asked for.** Apple closes a version's pre-release train the moment that version
goes `READY_FOR_SALE`, and every later build under the same `VERSION_NAME` is refused with
`Invalid Pre-Release Train ... is closed for new build submissions (90186)` — after the
archive is built, and after the Android half of `ship.sh` has already uploaded. Play has
no such rule, so this splits the two stores mid-run. Check the live version before
choosing the number, and bump `VERSION_NAME` if the current one is already public:

```bash
bundle exec fastlane run app_store_build_number app_identifier:com.sirelon.sellsnap \
  api_key_path:fastlane/AuthKey_G5TTXS7GV3.p8 live:true
```

When this forces a bump, the notes change too: draft them against the last *public*
version rather than the last build, since the skipped train's features are already in
users' hands and must not be announced a second time.

## 5. Draft the English "What's New" first

Look at what actually changed:

```bash
git log --oneline <last-version-bump-commit>..HEAD
```

(Find the last version-bump commit by searching for one that touched
`version.properties`, e.g. `git log --oneline -- version.properties`.)

Write 2-5 short, user-facing bullets — benefit language, not commit messages ("Improved
crash reporting" not "Enable Crashlytics and capture iOS Kotlin crashes"). Match the
existing voice in `store/copy/store-listing.md`: a one-line headline
(`SellSnap X.Y — <short theme>`), blank line, then `•` bullets. No emoji anywhere — App
Store Connect rejects metadata fields containing emoji.

**Keep it under Google Play's 500-character changelog limit.** The same text is reused
for the iOS release notes and TestFlight's what's-new (App Store's own limit is 4000
chars, so the tighter Play limit is the real ceiling — no need to maintain two versions
of the copy).

## 6. Translate to the other languages

Adapt (don't machine-translate flatly) into `uk`, `pl`, `ro`, `bg`, `pt-PT`, `kk` — no
`ru`, see step 2 — match each language's established terminology from that language's existing section in
`store/copy/store-listing.md` (e.g. reuse how "privacy"/"analytics"/"bug fixes" were
already phrased there, so the voice stays consistent release over release). Kazakh is
already flagged in that doc as machine-quality — keep that caveat in mind but don't
skip it, Google Play still needs the file.

## 7. Write the files

For the 6 real Play locales (`uk, pl-PL, ro, bg, pt-PT, kk` — not `en-US`, see
step 2): `.claude/tmp/release-metadata/android/<play-locale>/changelogs/<NEW_CODE>.txt`.
The English draft still gets written to `store/copy/store-listing.md` (every
language's section stays current there) — it just doesn't get a Play changelog file,
since pushing one for a locale with no base listing breaks the whole Play upload.

For all 5 App Store locales: overwrite `.claude/tmp/release-metadata/ios/<appstore-locale>/release_notes.txt`,
then overwrite `.claude/tmp/release-metadata/ios/RELEASE_NOTES_VERSION` with `<NEW_CODE>` (bare
number, no newline needed either way).

Update `store/copy/store-listing.md`: replace each language's `**What's New (vX.Y):**`
block with the new version/text, and update the top-of-file "Written for SellSnap X.Y
(build N)" line. This doc describes current state only — don't accumulate old versions'
blocks, git history already has them.

## 8. Promotional text — leave it alone unless asked

`promotional_text.txt` is **not** touched by this skill by default. Context: the user
once saw their App Store promotional text disappear after a release. That happens
because Apple doesn't carry promotional text or What's New forward to a new App Store
version draft automatically — creating that version by hand in App Store Connect leaves
both blank. The fix is that the `ios release` lane attaches the build already sitting in
TestFlight (`skip_binary_upload: true`, `build_number`/`app_version` read from
`version.properties` — no rebuild) and auto-uploads whatever is currently in
`.claude/tmp/release-metadata/ios/<locale>/{release_notes,promotional_text}.txt` at the
same time (via deliver's folder convention, `skip_metadata: false`). That resupply is
what prevents the wipe — it does not require the wording to change. Only rewrite
`promotional_text.txt` if the user explicitly asks to update the pitch this run;
otherwise the existing stable text keeps getting resupplied whenever `ios release` is
eventually run (separately, manually — this skill never runs that lane, so it never
touches Apple's live listing). These files are gitignored and don't propagate across git
worktrees — if a fresh worktree is missing them, copy them from another checkout rather
than regenerating with placeholder text.

## 9. Ship it

```bash
./scripts/ship.sh            # build-only bump
./scripts/ship.sh 2.2         # marketing version bump to 2.2
```

This compiles, bumps `version.properties`, and runs `fastlane android beta` +
`fastlane ios beta` — Play internal track and TestFlight. Let it run to completion
(background + wait for the notification if it takes a while; don't poll).

## 10. Hand off for in-app What's New

After a real version bump ships — skip this for a build-only/hotfix bump where
`VERSION_NAME` didn't change, same rule as step 4 — run the `release-notes` skill
(`.claude/skills/release-notes/`). It mines this range's git history, drafts the in-app
"What's New" copy in all 8 app languages (russian included — the store-listing exclusion
in step 2 does not apply here), and publishes it to the `release-notes` Firestore
collection that `features/whatsnew/` reads from. That skill owns the Firestore schema,
translation voice, and publish pipeline end to end — don't re-derive or duplicate any of
that here.

## 11. Report back

Show the user, per language, the What's New text that just shipped (it's short — showing
all of it is fine and lets them catch a bad translation before it's live to internal
testers). Confirm the Play and TestFlight uploads succeeded. Remind them that
`version.properties` and `store/copy/store-listing.md` are modified but not committed —
committing is their call, not automatic (the release-metadata files themselves are
gitignored, so they never show up in `git status` at all). Remind them production
promotion/submission is a separate manual step if they want to go further than beta, and
that step 10 (in-app What's New) is a separate hand-off, not automatic.
