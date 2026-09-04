---
paths:
  - "composeApp/src/commonMain/kotlin/**/ui/**/*.kt"
  - "composeApp/src/commonMain/kotlin/**/*Screen.kt"
  - "composeApp/src/commonMain/kotlin/**/designsystem/**/*.kt"
---

# Edge-to-edge and insets
- New Android-facing Compose screens must respect edge-to-edge.
- Prefer `AppScaffold`, which defaults to `WindowInsets.safeDrawing`, for screen-level layout.
- If using raw Material `Scaffold`, set `contentWindowInsets = WindowInsets.safeDrawing` unless there is a specific reason not to.
- Always apply scaffold `PaddingValues` to screen content and immediately call `consumeWindowInsets(paddingValues)` on the same content container.
- For `LazyColumn`, `LazyRow`, and grids, pass scaffold insets through `contentPadding`; do not only pad a parent container around the list.
- For vertical scroll content with text inputs, keep `android:windowSoftInputMode="adjustResize"` on the hosting activity and ensure the scroll/content container receives safe/IME-aware padding from `AppScaffold` or `WindowInsets.safeDrawing`.
- Avoid screen-level `systemBarsPadding()`, `statusBarsPadding()`, or `navigationBarsPadding()` on parent containers because it prevents true edge-to-edge drawing. Use those modifiers only on individual controls that must stay tappable, such as overlay close buttons.
- Bottom bars and FABs must account for navigation bars, either by living in `Scaffold` slots or by applying navigation-bar padding to the bar/control itself.
- Full-screen dialogs should use edge-to-edge-safe internal content padding. In common Compose Multiplatform code, do not assume Android-only `DialogProperties` parameters are available.
