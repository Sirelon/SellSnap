---
name: announce
description: >-
  Show SellSnap sellers a launch dialog: one seller, a group (a market, sellers who never
  published, a list of installs or OLX accounts) or everyone. Use when the owner says
  "announce", "show a dialog to", "remind users to", or wants to reach a specific seller in
  the app. To block old versions, use the force-update skill.
---

# Announce

An **announcement** is one Firestore document in `announcements`. On launch the app picks the
highest-priority announcement that matches the device and shows it as a dialog. You write the
document as a JSON file and publish it with `scripts/publish-announcement.mjs`. No app code.

## 1. Get the facts

Take them from the owner's request; ask only for what is missing.

- **Audience**: everyone, one market, sellers by OLX connection, or chosen installs (step 2).
- **Message**: what the seller should know or do.
- **Button**: none, `store` (this platform's store page), an `https://` URL, or an in-app
  screen: `sellsnap://account` (connect or reconnect OLX), `sellsnap://my-ads`,
  `sellsnap://messages`, `sellsnap://generate` (new listing). An in-app link needs
  `buttonText` (for example "Connect OLX") and `minVersion` "4.2" or later.
- **Frequency**: `once` (default, shows until closed once) or `everyLaunch`.
- **Versions** (optional): `minVersion` / `maxVersion`, inclusive, exact released versions.

Done when audience, message, button and frequency are known.

## 2. Find the audience

Skip for "everyone". For sellers by OLX connection, set `rules.olxConnection` and skip the
lists: `["none"]` reaches guests who never connected, `["expired"]` sellers whose account needs
reconnecting, `["connected"]` sellers with a working account. It needs `minVersion` "4.2" or
later, because older apps ignore it and would show the dialog to everyone.

Otherwise list installs from the generation log:

```bash
node scripts/find-announcement-targets.mjs --country ua
node scripts/find-announcement-targets.mjs --never-published
node scripts/find-announcement-targets.mjs --since 2026-11-25T10:40 --until 2026-11-25T11:00
```

Times are UTC. Each row gives the Firebase installation ID (for `rules.userIds`) and the OLX
account IDs the install published with (for `rules.olxUserIds`). Prefer `olxUserIds` for a
seller who published: it follows them to a new phone. Use `userIds` for a guest.

Know the limits of this source:

- It holds only installs that generated a listing inside the log's retention window.
- `--never-published` is not "not connected to OLX": a seller who connected but never
  published looks the same. Use `rules.olxConnection` for that.
- To find one seller seen in BigQuery, take the time of their `ad_generation_*` events and
  pass a window around it with `--since` / `--until`. Analytics `user_pseudo_id` is a
  different ID and never goes into `userIds`.

Done when you have the ID list, or the owner chose everyone.

## 3. Write the file and get the copy approved

Copy [`announcement-template.json`](announcement-template.json) to
`scripts/announcements/<id>.json`. The file name is the document id; name it for the purpose
and date, for example `connect-olx-reminder-2026-11.json`. Fill in `rules` from steps 1–2;
delete every key you do not use.

Copy follows AGENTS.md § Localization: from the seller's side, warm, short, no cause, no
blame. Post the English title, body and button text to the owner and wait for approval (AGENTS.md rule 6).
Then write all eight languages. `ru` is a byte copy of `uk`; the script refuses a file where
they differ. Post the Ukrainian before publishing.

Done when the owner approved the English and the file has no placeholder left.

## 4. Dry run and publish

```bash
node scripts/publish-announcement.mjs scripts/announcements/<id>.json --dry-run
node scripts/publish-announcement.mjs scripts/announcements/<id>.json
```

Read the dry run: versions, platforms, OLX connection, link and title must match the request. A publish overwrites
the whole document, so editing a live announcement means editing its file and publishing again.

Done when the script prints `Published announcements/<id>`.

## 5. Record and follow up

Commit the file with a subject such as `Remind sellers without OLX to connect it`, and push as
AGENTS.md § Git says. Report the id, the audience size and how to stop it: set
`"active": false`, publish, commit.

To see whether it worked, query BigQuery (`analytics_538260747.events_*`):
`announcement_shown` and `announcement_link_tapped` with `announcement_id` = `<id>`. For an
in-app link, `deep_link_opened` with `source` = `announcement` and the same `destination`
counts the screens that opened.

## What each app version does

| App version | Announcement behaviour |
| --- | --- |
| 4.2 and newer | Everything above |
| 3.11 to 4.1 | No in-app links (the dialog shows without the button); `olxConnection` and `buttonText` are ignored |
| 3.8 to 3.10 | Shows it on every platform; `platforms` is ignored |
| 3.4 to 3.7 | Shows title and body with targeting; no `link` button |
| 3.3 and older | Nothing; these builds have no announcements |

The dialog opens on the main seller screens, not during onboarding or login. A `once`
announcement a device already closed never shows there again, even after a republish; give it a
new id to show it again.
