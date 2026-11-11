---
paths:
  - ".maestro/**"
  - "scripts/maestro-*.sh"
---

# Maestro flows

Flows live in `.maestro/`, runner scripts in `scripts/maestro-*.sh`.

Prefer `testTag` ids over visible text in selectors — flows run in 4+ locales. Two exceptions bite: a `ModalBottomSheet` or dialog is its own window, where `testTagsAsResourceId` does not reach, so select its content by text; and `launchApp` grants every runtime permission by default (`POST_NOTIFICATIONS` included), so a flow that needs a permission undecided must pass `permissions: { all: unset }` or launch with `adb shell monkey`. Photos are never
picked through the OS picker.

Running a capture session, and the traps of a run (API level, one simulator, `screenshotMode`, App Check): the `sellsnap-screenshots` skill.
