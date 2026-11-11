---
name: desktop-ui-verify
description: >-
  See a `commonMain` UI change working on the desktop target before declaring it done: run the
  app under Compose Hot Reload and drive its window through the `compose-hot-reload` MCP server
  (screenshot, semantic tree, click, type). Use after editing a shared screen, design-system
  component, navigation or view model, or when the user asks to check a UI change on desktop.
---

# Desktop UI verification (Compose Hot Reload MCP)

The desktop JVM target runs under Compose Hot Reload, and its MCP server (`compose-hot-reload` in
`.mcp.json`, task `:composeApp:hotMcpServerJvm`) lets an agent drive the running window. Use it to
see a `commonMain` UI change working before declaring it done; the shared UI (screens, design system,
navigation, view models) is what it exercises. Plugin `1.2.0` on Compose Multiplatform `1.12.0`.

The server is not approved by default, because it starts a Gradle JVM with the session. When its
tools (`status`, `reload`, `take_screenshot`, …) are missing, ask the owner to enable
`compose-hot-reload` in `/mcp`, or to start the session with
`claude --settings '{"enabledMcpjsonServers":["compose-hot-reload"]}'`.

The loop:

1. **Start once, in the background:** `./gradlew :composeApp:hotRunJvm --auto` (blocks while the app
   runs; first run provisions JetBrains Runtime 25 through the foojay resolver). Without `--auto`,
   reloads are explicit.
2. **Wait for the connection:** poll `status` until `connected: true`. The server connects a moment
   after it starts, so the very first call of a session is usually `connected: false`. `status` also
   reports `buildContinuous` — `true` means `--auto`.
3. **Edit** a composable, then **`await_reload`** in `--auto` mode (the continuous build already
   recompiled; a `reload` call there reports "no changed classes to reload"). Without `--auto`, call
   `reload` — it recompiles `:composeApp` and swaps the classes (~25 s in this repo).
4. **Look:** `take_screenshot` (pass `save_to`, then Read the PNG) and `get_semantic_tree` (node
   `id`, `role`, `text`, `testTag`, `selected`, `actions`, `bounds`). Confirm the change in the tree,
   not only in the picture.
5. **Drive:** `click` / `long_click` a node whose `actions` include `onClick` / `onLongClick`,
   `type_text` into a node with `editableText`, `scroll` / `scroll_to_index`, `resize_window`.
   The tree is a snapshot: re-read it after each interaction, ids change on recomposition.
6. **Before declaring the change working:** `get_ui_error` must report `hasError: false` and
   `get_logs` must show no new exception. `reset_ui` drops all `remember`-ed state; `restart`
   relaunches the process and keeps the build mode, but the original `hotRunJvm` Gradle
   invocation exits — the app itself stays connected.
7. **Stop the app when done:** `kill` the `pid=` value from `composeApp/build/run/jvmMain/jvmMain.pid`.

What it does and does not cover:

- Runs the `jvmMain` entry point: Koin wiring, Supabase/OLX network code, datastore, navigation and
  every `commonMain` screen. Camera, photo picking, deep links, Firebase, insets and anything with
  an `androidMain` / `iosMain` actual still need a device or simulator.
- `take_screenshot` is a `java.awt.Robot` screen grab at 1x (800×572 for the default 800×600
  window, while the semantic tree reports 2x bounds on a Retina display). It captures whatever is
  on screen at the window's position: an occluded window yields the covering window or the
  wallpaper. Trust `get_semantic_tree` for text and state; use the screenshot for layout only. An
  in-process capture needs Compose Multiplatform 1.13+ together with a Hot Reload build from
  2026-09-01 or later, neither of which is on Maven Central; revisit when they are released.
