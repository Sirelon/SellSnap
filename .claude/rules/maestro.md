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

## OLX login flow (`olx_login_ro.yaml`)

- A clean OLX login on iOS needs `xcrun simctl erase` plus reinstall: `clearState` and even an
  uninstall keep OLX's web session, and the login then finishes without a password.
- `inputText` on a loaded device can add a stray first character ("6sirelon@…", "vsirelon@…").
  The flow retypes until the e-mail reads back exactly and stops before the password otherwise:
  a wrong login counts against the OLX account.
- Do not mix the Maestro MCP server and the `maestro` CLI on the same iOS simulator: the second
  one fails with "iOS driver not ready". Reboot the simulator to recover.
- The launch announcement dialog hides the home screen from Maestro; wait for `(OK|New listing)`.
