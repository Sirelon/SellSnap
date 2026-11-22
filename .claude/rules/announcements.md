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

## Force an update

A gate is one announcement document: `active: true`, `content.link: "store"`,
`rules.dismissible: false`, `rules.showMode: "everyLaunch"`, `rules.maxVersion` set to the last
blocked version, and `rules.platforms: ["android"]` or `["ios"]`. Write `title` and `body` in
every app language, because the document carries the only copy the seller sees.

- One document per platform. Each store approves a build on its own day, so publish the gate
  for a platform only after its store serves the fixed version.
- `platforms` is read from 3.11. Builds 3.8 to 3.10 ignore it and show a gate on every
  platform, so while those builds are still in use, publish a gate only after both stores serve
  the fixed version.
- 3.4 to 3.7 ignore `link` and show OK, so they get a notice, not a gate. Older builds cannot be
  reached.
- The dialog opens only in the seller flow, and only when the announcements load within 3 s.
  A slow first launch skips it; the next launch reads Firestore's cache.
- Measure it with `announcement_shown` (`gate` true) against `announcement_link_tapped`.
