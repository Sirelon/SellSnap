---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/announcements/**"
---

# Announcements

- Launch dialog fed by the Firestore `announcements` collection (one doc = content + rules,
  filtered on device; schema in `AnnouncementResponse`). Publish via Firestore REST as in the
  `release-notes` skill. `content.link` adds a button: `store` opens the platform store page,
  an https URL opens the browser. A doc with `rules.dismissible: false` plus a link has no
  other exit, so `maxVersion` + `showMode: everyLaunch` + `link: store` gates a version range
  that no longer works (the 3.1-3.3 clients broke silently when the AI proxy went live on
  2026-10-05; those builds predate this feature and cannot be reached). Host images in Firebase Storage: the Android emulator's DNS fails on
  some third-party hosts (`fastly.picsum.photos` → `UnknownHostException`) while Google hosts
  resolve, and the dialog silently drops an image that fails to load.
