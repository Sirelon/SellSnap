---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/announcements/**"
---

# Announcements

- Launch dialog fed by the Firestore `announcements` collection (one doc = content + rules,
  filtered on device; schema in `AnnouncementResponse`). Publish with
  `scripts/publish-announcement.mjs` (the `announce` skill). `content.link` adds a button: `store` opens the platform store page,
  an https URL opens the browser, and an in-app link (`sellsnap://account`, routes in `navigation/AppDeepLink.kt`)
  closes the dialog and opens that screen. `content.buttonText` (per language, like `title`)
  labels the button. A doc with `rules.dismissible: false` plus a link has no
  other exit, so `maxVersion` + `showMode: everyLaunch` + `link: store` gates a version range
  that no longer works (the 3.1-3.3 clients broke silently when the AI proxy went live on
  2026-10-05; those builds predate this feature and cannot be reached). Host images in Firebase Storage: the Android emulator's DNS fails on
  some third-party hosts (`fastly.picsum.photos` → `UnknownHostException`) while Google hosts
  resolve, and the dialog silently drops an image that fails to load.

`rules.platforms` (`android`, `ios`, `web`, `desktop`; absent or empty = every platform) limits
a document to those platforms. `rules.olxConnection` (`none`, `expired`, `connected`; absent or
empty = every device) limits it to devices with one of those OLX account states, read from every
account on the device (`olxConnectionsOf`); `expired` is an account marked `NeedsReconnect`. All
rules must match: `olxConnection` narrows `userIds` / `olxUserIds`, it does not widen them.

Apps before 4.2 drop an in-app link (the dialog shows without its button) and ignore
`olxConnection` (the dialog reaches every device). A document using either carries
`rules.minVersion` "4.2" or later; the publish script refuses one that does not. To show a dialog to sellers, use the `announce` skill; to force an
update, use the `force-update` skill.
