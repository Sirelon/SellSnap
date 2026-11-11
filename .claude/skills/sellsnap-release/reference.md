# Release reference

Facts behind the steps in `SKILL.md`. Read the section a step points to.

## Locales

Google Play and App Store Connect use different locale strings for the same language. Neither store covers all 8 in-app languages: each store has only the listing locales someone configured for it.

| Language | Google Play locale | Play listing exists? | App Store locale | Apple support |
| --- | --- | --- | --- | --- |
| English | `en-US` | No (no title configured; confirmed by downloading the live listing) | `en-US` | yes (live in App Store Connect) |
| Ukrainian | `uk` | yes | `uk` | yes |
| Polish | `pl-PL` | yes | `pl` | yes |
| Romanian | `ro` | yes | `ro` | yes |
| Bulgarian | `bg` | yes | none | not supported by Apple |
| Portuguese (Portugal) | `pt-PT` | yes | `pt-PT` | yes |
| Kazakh | `kk` | yes | none | not supported by Apple |

- Android has 6 real locales: `uk, pl-PL, ro, bg, pt-PT, kk`.
- iOS has 5 real locales: `en-US, uk, pl, ro, pt-PT`.
- Download the live Play listing with `bundle exec fastlane run download_from_play_store package_name:com.sirelon.sellsnap json_key:fastlane/google-play-key.json metadata_path:<fresh-empty-dir>`. Use a directory that does not exist yet: supply silently does nothing on an existing one.
- Play rejects a changelog for a locale without a base listing, and the whole edit fails. Google validates atomically at commit time, so the error can name any locale, not the one that lacks the listing. Add `en-US` to the Android changelog automation only after creating that Play listing in full (title, descriptions, everything; drafted copy is in `store/copy/store-listing.md`).
- App Store `en-US` state on 2026-08-27 (first production submission): the localization existed but had no Description, Keywords or Support URL. `uk`, `pl`, `ro` and `pt-PT` had complete listings. A missing-field error from `fastlane ios release` points at `en-US` first.
- When the real locale sets change, update this table and `ANDROID_LOCALES` in `scripts/ship.sh` together. The `ship.sh` changelog guard checks `ANDROID_LOCALES`; a mismatch makes it check locales that `metadata_path` no longer contains.

## Where the text goes

The files below are mechanical fastlane inputs, not content worth versioning. They live under `.claude/tmp/release-metadata/` (gitignored) and are regenerated every release. The Fastfile points `supply` and `deliver` at that directory through `metadata_path:`. The default location, inside `fastlane/`, stays unused so these files are never tracked or pushed.

| File | Read by | Notes |
| --- | --- | --- |
| `android/<play-locale>/changelogs/<version_code>.txt` | `upload_to_play_store` (supply), through `metadata_path: ANDROID_METADATA_DIR` | The filename is the new numeric version code, e.g. `9.txt`. One file per real Play locale (6). supply scans every locale folder under `android/`, so a stray folder for a locale without a Play listing breaks the upload. |
| `ios/<appstore-locale>/release_notes.txt` | `upload_to_app_store` (deliver, `ios release` lane, `metadata_path: IOS_METADATA_DIR`) and the `ios beta` lane's `localized_build_info` | pilot has no folder convention, so the Fastfile reads these files by hand for TestFlight. One text serves TestFlight "What to Test" and the App Store release notes. One file per App Store locale (5), overwritten each release, no version in the filename. |
| `ios/RELEASE_NOTES_VERSION` | `scripts/ship.sh` guard only | Plain text, the version code, e.g. `9`. It proves the iOS notes were refreshed for this build. |
| `ios/<appstore-locale>/promotional_text.txt` | `upload_to_app_store` (deliver, `ios release` lane only) | TestFlight has no promotional text, so the `ios beta` lane never reads it. See "Promotional text and the App Store draft". |
| `ios/<appstore-locale>/description.txt`, `android/<play-locale>/full_description.txt` | deliver (`ios release`), supply (`upload_to_play_store`) | Written only when the descriptions change (SKILL.md step 9). |
| `store/copy/store-listing.md` | nothing automatic | Tracked human reference doc that describes the current state. It is the only file of this flow that belongs in git. |

## Apple's pre-release train rule

Apple closes a version's pre-release train when that version goes `READY_FOR_SALE`. Every later build under the same `VERSION_NAME` is refused with `Invalid Pre-Release Train ... is closed for new build submissions (90186)`. The refusal comes after the archive is built and after the Android half of `ship.sh` has already uploaded. Play has no such rule, so a missed bump leaves the two stores on different builds mid-run.

The version check in SKILL.md step 2 prints three values, and the decision needs all three:

- `live:` is the version on sale. It forces the bump when it equals the chosen `VERSION_NAME`.
- `edit:` shows whether a version is already open for the next submission.
- The build list shows which trains exist.

A forced bump changes the notes too. The skipped train's features are already in users' hands, so the notes cover only what landed after the last public version.

Use the spaceship script rather than `fastlane run app_store_build_number ... api_key_path:`. That action's `api_key_path` wants fastlane's own JSON key-file format; the raw `.p8` fails with `JSON::ParserError: invalid number: '-----BEGIN'`. The key id, issuer id and `.p8` path in the script are the ones `fastlane/Fastfile` hardcodes (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_FILEPATH`).

## Promotional text and the App Store draft

Apple carries neither promotional text nor What's New into a new App Store version. A version created by hand in App Store Connect comes up with both fields empty and no build selected.

`ship.sh` closes that gap. After the two beta uploads it runs `fastlane ios release`, which:

- creates or updates the version draft;
- resupplies `release_notes.txt` and `promotional_text.txt` for all 5 App Store locales through deliver's folder convention (`skip_metadata: false`);
- attaches the build just uploaded (`skip_binary_upload: true`, no rebuild);
- never submits for review: the draft waits for a deliberate click, and releases automatically once Apple approves it (`automatic_release: true`).

The resupply keeps the promotional text from going blank, so the wording stays as it is. Rewrite `promotional_text.txt` only when the request asks to change the pitch.

deliver picks a build only as part of submitting for review, so passing it `build_number` attaches nothing. `attach_build_to_app_store_version` in the Fastfile attaches the build explicitly. The API rejects server-side filtering on `preReleaseVersion.version`, so that function matches train and build number locally over the 20 most recent uploads. Keep the local match.

## Description uploads

Play shows a new description at once. Apple accepts a description only on the unreleased version draft, so iOS users see it when that version goes live.

## Gradle memory

The iOS framework's release link is the peak-memory task of the build. It needs `org.gradle.jvmargs=-Xmx8g` (set in `gradle.properties`). At 4g it died with a Kotlin/Native `OutOfMemoryError` partway through the archive, after Android had already uploaded.

## App Store banners

Banners (product-page header and search-results images) are placements on one App Store version, and a new version starts with none. `copy-banners.rb` takes the newest iOS version as target and, per locale, the newest earlier version with banners as source. It skips placements the target already has. A draft (`PREPARE_FOR_SUBMISSION`) accepts them at once and they go live with that release. A version in review rejects placements with 409 `waitingForReview`.
