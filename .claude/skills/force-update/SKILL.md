---
name: force-update
description: >-
  Force SellSnap users on old versions to update: publish a gate that blocks the app until the
  seller opens the store, or lift that gate. Use when the owner says "force update", "block
  version X", "make users update", or "lift the gate".
---

# Force an update

A **gate** is one Firestore document in `announcements`. On launch the app shows it as a dialog
with one button, "Open store", and no way to close it. You publish the gate with a script; no
app code, no Firebase console.

## 1. Get the facts

You need three things. Take them from the owner's request; ask only for what is missing.

- **Platform**: `android`, `ios`, or both. Both means two gates, one per platform.
- **Fixed version**: the first version that works.
- **Last broken version**: the highest released version to block. List the releases with
  `git log origin/main --grep="^Release version" --format=%s`. The gate's version bound is
  inclusive, so name an exact released version (`3.9`), never a guess like `3.9.9`.

Done when you can fill in platform, fixed version and last broken version for every gate.

## 2. Check the store serves the fixed version

A gate sends the seller to the store. If the store still has the old version, the seller is
stuck behind the gate with nothing to install.

- **iOS**: `curl -s "https://itunes.apple.com/lookup?id=6776001373&country=ua"` and read
  `results[0].version`. Without `country` the lookup returns nothing.
- **Android**: Play has no public version lookup. Ask the owner whether the fixed version is
  live in production on Play.

**Versions 3.8, 3.9 and 3.10 ignore `platforms`**: they show every gate, whatever platform it
names. If the gate's range includes any of them, both stores must serve the fixed version
before you publish, even for a one-platform gate.

Done when every store the gate reaches serves the fixed version. If one does not, stop and tell
the owner which store and which version it serves.

## 3. Write the gate file

Copy [`gate-template.json`](gate-template.json) to
`scripts/announcements/force-update-<platform>-<fixed version>.json`. The file name is the
document id, for example `force-update-ios-3.11.json`. Then:

- Replace `LAST_BROKEN_VERSION` and `PLATFORM`.
- Replace the template's generic title and body with the real cause in every app language:
  what broke, that the new version fixes it, and "update" (AGENTS.md § Localization, be direct
  and friendly). The owner's request names the cause; write from it and publish, no extra
  approval round. `force-update-ios-4.0.json` is the reference.
- `ru` is a byte copy of `uk`. The script refuses a file where they differ.

Done when the file has no placeholder left.

## 4. Dry run, confirm, publish

```bash
node scripts/publish-announcement.mjs scripts/announcements/<id>.json --dry-run
```

The owner's request to force an update is the go: when the dry run passes and every store
serves the fixed version, run the same command without `--dry-run` straight away. Stop and ask
only when a check in steps 1–3 failed or a fact was missing.

Done when the script prints `Published announcements/<id>`.

## 5. Record it

Commit the gate file with a subject such as `Force iOS 3.9 and older to update to 3.11`, then
push as AGENTS.md § Git says. Report to the owner: the document id, platform, blocked range,
and how to lift it (below).

## Lift a gate

Set `"active": false` in the gate's file, publish it as in step 4, and commit. The app drops the
gate on its next launch. Lift a gate when the fixed version is out of use, or when the gate was
published by mistake.

## What each app version does

| App version | What a gate does there |
| --- | --- |
| 3.11 and newer | Blocks, and only on the platforms named in `platforms` |
| 3.8 to 3.10 | Blocks on every platform; `platforms` is ignored |
| 3.4 to 3.7 | Shows the text with an OK button; the seller can close it |
| 3.3 and older | Nothing; these builds have no announcements |

The dialog opens on the main seller screens, not during onboarding or login. If Firestore takes
longer than 3 seconds, that launch skips it and the next launch reads the cached copy.

## How well it works

Firebase Analytics: `announcement_shown` with `gate` = true counts blocked launches;
`announcement_link_tapped` with the same `announcement_id` counts sellers who went to the store.
