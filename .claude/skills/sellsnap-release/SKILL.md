---
name: sellsnap-release
description: >-
  Release SellSnap end to end in one run: version bump, store release notes in every language, Play internal +
  TestFlight upload, App Store draft, in-app What's New, and website and store descriptions when a capability is
  complete, then App Store review submission with automatic release. Use when the user says "release" or "ship
  version X.Y". Promoting to Play production stays manual.
---

# SellSnap release

One request runs steps 1 to 12 in one go. The owner pre-approved every decision inside them. Decide each one
yourself, publish it, and report once at the end. Stop to ask only when a step fails in a way you cannot fix.

The run uploads to Play internal testing and TestFlight, prepares the App Store draft (`fastlane ios release`,
which never submits), and submits that draft for review as its last action (step 12). The draft's release type is
`AFTER_APPROVAL`, so Apple publishes it on approval. Promoting on Play (`fastlane android release`) is the owner's
one manual step. Background for each step: [reference.md](reference.md).

## 1. Pull the latest

```bash
git fetch origin && git merge origin/main
```

The notes come from the git range, so unmerged work cannot appear in them. Resolve conflicts, then run
`./gradlew :composeApp:compileAndroidMain` and `./gradlew :composeApp:jvmTest`; a broken merge found after the bump
wastes a build number. `.claude/tmp/release-metadata/` is gitignored, so the merge never touches it. When uncommitted
edits to the tracked `store/copy/store-listing.md` make the merge refuse, run `git checkout --` on that file; step 5
rewrites it.

Done when: the merge is complete with no conflict, and compile and `jvmTest` pass.

## 2. Decide the version

`NEW_CODE = VERSION_CODE + 1` (from `version.properties`), every release.

`VERSION_NAME` is `MAJOR.MINOR` or `MAJOR.MINOR.PATCH`. A version named in the request ("release 4.0") is `NEW_NAME`.
Otherwise the range decides (`git log --oneline <last bump commit>..HEAD`, the range step 3 reads); the highest level
in it wins:

| The range ships | Bump | From 3.9 |
| --- | --- | --- |
| Fixes only | patch | 3.9.1 |
| Small updates to features that already exist | minor | 3.10 |
| A big new feature or a big update (the kind step 9 puts on the website) | major | 4.0 |

Pass `NEW_NAME` to `ship.sh` as `$1`. Only "just bump the build" in the request keeps `VERSION_NAME`; then run
`ship.sh` without an argument.

Then read the live App Store state:

```bash
bundle exec ruby -r spaceship -e '
Spaceship::ConnectAPI.token = Spaceship::ConnectAPI::Token.create(
  key_id: "G5TTXS7GV3",
  issuer_id: "8873b38c-775e-4a3e-baf5-4e2bada765a8",
  filepath: File.expand_path("fastlane/AuthKey_G5TTXS7GV3.p8")
)
app = Spaceship::ConnectAPI::App.find("com.sirelon.sellsnap")
puts "live:  #{app.get_live_app_store_version&.version_string}"
edit = app.get_edit_app_store_version
puts "edit:  #{edit&.version_string} #{edit&.app_store_state}"
Spaceship::ConnectAPI.get_builds(filter: { app: app.id }, sort: "-uploadedDate",
                                 limit: 5, includes: "preReleaseVersion").to_models.each do |b|
  puts "build: #{b.pre_release_version&.version} (#{b.version})"
end
'
```

When the resulting `VERSION_NAME` equals `live:`, bump it by the table above (a patch at least) whatever the request
said, and pass it to `ship.sh`: Apple refuses builds for a released version (error 90186). After a forced bump, step 3's base is
the bump commit of the last public version (`live:`). Reference: "Apple's pre-release train rule".

Done when: `NEW_CODE`, the `VERSION_NAME` to ship and the step 3 base are fixed, and that `VERSION_NAME` differs from `live:`.

## 3. Draft the English What's New

Find the base bump commit with `git log --oneline -- version.properties`, then read `git log --oneline <base>..HEAD`.
Write 1 to 5 user-facing bullets, chosen and worded by the rules in "Writing for sellers" below. Follow the voice in `store/copy/store-listing.md`: headline `SellSnap X.Y — <short theme>`,
blank line, `•` bullets. Use no emoji: App Store Connect rejects metadata fields that contain one. The same text
serves Play, iOS release notes and TestFlight.

### Writing for sellers

The owner's rules. They apply to these store notes and to the in-app cards (`release-notes` skill):

- Tell the seller what got better for them, as a short story. Never describe how it was built.
- Lead with what every seller feels. A change to the core (photo in, listing out) goes first, even when the diff
  is small: "We improved what matters most: SellSnap recognizes your items better and writes even better listings.
  Try it, you'll feel the difference." Not "a newer AI model", not a list of what the AI now reads.
- A new market is one plain line: "New country: SellSnap now works in Kazakhstan. Publish your listings on OLX.kz."
  Leave out its mechanics (listing language, currency, price rounding).
- Drop fixes and tweaks that only a small slice of sellers meets (a per-country city, a button on an expired
  account). Two strong bullets beat five mixed ones.
- Order by how many sellers gain, never by change type.

Worked example, 4.1. The owner rejected the first draft and approved the second.

Rejected (ordered by change type, written from the diff):

```
SellSnap 4.1 — Kazakhstan joins, smoother OLX accounts

• Sell on OLX.kz: Kazakhstan is now in the country picker
• Your saved city is kept separately for each OLX country
• When your OLX account needs reconnecting, the Reconnect button shows right away
• Sharper listings from a newer AI model
```

What was wrong: the core improvement came last and named the mechanism ("a newer AI model"). Two bullets were
small-slice fixes. The headline sold the minor items. A later fix still listed mechanics ("takes the brand and model
printed on the item", "listings in Kazakh, prices in tenge").

Approved:

```
SellSnap 4.1 — Even better listings

• We improved what matters most: SellSnap recognizes your items better and writes even better listings. Try it, you'll feel the difference
• New country: SellSnap now works in Kazakhstan. Publish your listings on OLX.kz
```

Done when: the text has 1 to 5 bullets that pass the rules above, no emoji, at most 500 characters (Google Play's
limit; App Store allows 4000).

## 4. Translate

Adapt, rather than machine-translate, into `uk`, `pl`, `ro`, `bg`, `pt-PT`, `kk`. Reuse the terminology of each
language's section in `store/copy/store-listing.md` ("privacy", "analytics", "bug fixes"). That doc flags Kazakh as
machine-quality; write it anyway, Play needs the file.

No russian store listing, on either store, by product decision: add no `ru` or `ru-RU` folder under
`.claude/tmp/release-metadata/`, add no russian section to `store-listing.md`, and delete a stray `ios/ru/` or
`android/ru-RU/` directory. The in-app `values-ru` shim: `.claude/agents/localize.md`.

Done when: all 6 translations exist and each is at most 500 characters.

## 5. Write the files

Reference: "Locales", "Where the text goes". Paths are under `.claude/tmp/release-metadata/`.

- Android, 6 Play locales `uk, pl-PL, ro, bg, pt-PT, kk`: `android/<play-locale>/changelogs/<NEW_CODE>.txt`. Write
  no `en-US` file and keep `android/` free of other locale folders.
- iOS, 5 App Store locales `en-US, uk, pl, ro, pt-PT`: overwrite `ios/<appstore-locale>/release_notes.txt`, then
  overwrite `ios/RELEASE_NOTES_VERSION` with `<NEW_CODE>` (bare number).
- `store/copy/store-listing.md`: in each of the 7 language sections (English included), replace the
  `**What's New (vX.Y):**` block with the new version and text, and update the top line "Written for SellSnap X.Y
  (build N)". Keep current state only; git holds old blocks.
- Leave `ios/<locale>/promotional_text.txt` as it is unless the request asks to change the pitch; `fastlane ios release`
  resupplies it on every ship. Reference: "Promotional text and the App Store draft".
- A fresh worktree lacks these gitignored files: copy any missing one from another checkout.

Done when: 6 non-empty `<NEW_CODE>.txt` changelogs, 5 `release_notes.txt` files and a matching `RELEASE_NOTES_VERSION`
exist, and `store-listing.md` shows the new version in all 7 sections. `ship.sh` hard-fails otherwise.

## 6. Ship it

```bash
./scripts/ship.sh        # build-only bump
./scripts/ship.sh 2.2    # marketing version bump to 2.2
```

`ship.sh` compiles, bumps `version.properties`, runs `fastlane android beta` and `fastlane ios beta` (Play internal
track, TestFlight), then `fastlane ios release` (App Store draft). Run it in the background and wait for the
completion notification; do not poll.

The App Store step is non-fatal: both binaries are uploaded before it runs, and its one expected failure is a version
in review, which cannot be edited. That warning leaves the ship successful. An out-of-memory failure in the iOS
archive: reference, "Gradle memory".

Done when: `ship.sh` has exited, its log shows the Play and TestFlight uploads succeeded, `ios release` succeeded or
printed the in-review warning, and `version.properties` holds `NEW_CODE`.

## 7. Copy the App Store banners

```bash
ruby .claude/skills/sellsnap-release/copy-banners.rb
```

When step 6 printed the in-review warning, run this later instead, once that version is released, passing its version
string: a version in review rejects placements (409 `waitingForReview`). Making or replacing a banner belongs to the
`store-banners` skill. Reference: "App Store banners".

Done when: the script exits 0 with `OK` or `HAVE` for each locale and banner type that has a source, or the deferral
to the released version is noted for the report.

## 8. Publish the in-app What's New

Run this step only when `VERSION_NAME` changed (a forced bump counts); skip it for a build-only bump. Run the
`release-notes` skill (`.claude/skills/release-notes/`) through its Firestore publish in this run. It drafts the
in-app copy for all 8 app languages (russian included), publishes to the `release-notes` Firestore collection that
`features/whatsnew/` reads, and owns the schema, voice and pipeline.

Done when: that skill reports the Firestore publish complete and `scripts/release-notes.json` has an entry for the new version.

## 9. Update the feature lists

Run this step only when the range completes a capability sellers could not use before (dictation, AI polish, buyer
messages once replies and notifications ship). Skip it for improvements, fixes and partial slices: a partial feature
on the website promises what the app cannot do yet.

1. Website grid: `docs/index.html` (`.features-grid`, `feature_*` i18n keys). The grid is 3×2, so a new card replaces
   the weakest one. Translate it into all 7 site languages in `docs/assets/landing.js`.
2. Store descriptions: the Description block per language in `store/copy/store-listing.md`. Write each to
   `ios/<locale>/description.txt` and `android/<locale>/full_description.txt`, then upload:
   ```bash
   bundle exec fastlane run upload_to_play_store package_name:com.sirelon.sellsnap \
     json_key:fastlane/google-play-key.json metadata_path:.claude/tmp/release-metadata/android \
     skip_upload_aab:true skip_upload_apk:true skip_upload_changelogs:true \
     skip_upload_images:true skip_upload_screenshots:true
   bundle exec fastlane ios release
   ```
3. Store screenshots, when the capability has its own screen: the `sellsnap-screenshots` skill.
4. Onboarding (`composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/onboarding/OnboardingScreen.kt`,
   the same three steps as the website hero): change it only when the capability changes the snap, write, publish loop.

Reference: "Description uploads".

Done when: the new card exists in `docs/index.html` and all 7 languages of `landing.js`, both upload commands exited 0,
and `store-listing.md` carries the new descriptions.

## 10. Update the legal pages

Run this step only when the range adds an SDK, a permission or a new data flow. Check the range's diff in
`gradle/libs.versions.toml`, `androidApp/src/main/AndroidManifest.xml`, `iosApp/iosApp/Info.plist` and any new network
call that sends user data. The pages are `docs/privacy-policy/index.html` and `docs/terms-and-conditions/index.html`
(both English-only).

Legal text is the one decision in this run that stays with the owner (AGENTS.md rule 6). Write the changed paragraphs
into the final report and leave the two pages as they are; edit and push them after the owner's yes.

Done when: the report lists each added SDK, permission and data flow with the paragraph that covers it.

## 11. Check whether OLX's browser login works again

Run while the Firestore switch `config/olx_login.systemBrowser` is `false` (SIR-195): on the booted Android emulator,
`maestro test -e OLX_EMAIL=… -e OLX_PASSWORD=… .maestro/olx_login_canary.yaml` (credentials in `.maestro/.env`). It logs
in once; never re-run it on a failure in the same release.

Done when: the report says "OLX browser login: still broken" (the flow failed at "Open SellSnap") or "works again —
owner can set `systemBrowser` to `true`" (the flow passed). A failure at any earlier step is a broken flow, not an
answer: say so.

## 12. Commit, push, report

Commit and push to `main` everything the run changed in tracked files: `version.properties`,
`iosApp/iosApp.xcodeproj/project.pbxproj`, `store/copy/store-listing.md`, `scripts/release-notes.json`, and `docs/`
when step 9 ran (GitHub Pages serves `docs/` from `main`). The release-metadata files are gitignored.

Then submit the App Store draft for review. It comes last because a version in review rejects banner placements
(step 7) and `fastlane ios release` (step 9):

```bash
bundle exec ruby -r spaceship -e '
Spaceship::ConnectAPI.token = Spaceship::ConnectAPI::Token.create(key_id: "G5TTXS7GV3",
  issuer_id: "8873b38c-775e-4a3e-baf5-4e2bada765a8", filepath: File.expand_path("fastlane/AuthKey_G5TTXS7GV3.p8"))
app = Spaceship::ConnectAPI::App.find("com.sirelon.sellsnap")
v = app.get_edit_app_store_version(includes: "build")
abort "release_type #{v.release_type}" unless v.release_type == "AFTER_APPROVAL"
abort "build #{v.build&.version}" unless v.build&.version.to_s == File.read("version.properties")[/VERSION_CODE=(\d+)/, 1]
sub = app.create_review_submission(platform: "IOS")
sub.add_app_store_version_to_review_items(app_store_version_id: v.id)
sub.submit_for_review
'
```

Skip it when step 6 printed the in-review warning: the earlier version is still in review.

Report once: the version shipped, the English store notes (other languages live in `store-listing.md`), the surfaces
updated, what was verified live, and the App Store state (`WAITING_FOR_REVIEW`). End with the owner's one step:
promote on Play. Then list every open Linear ticket in the Sirelon team whose title starts with "After release:"
(today SIR-198, Kazakhstan on both stores): each is a step for once this version is live.

Done when: tracked files are clean in `git status`, `main` is pushed, and the new App Store version is
`WAITING_FOR_REVIEW`.
