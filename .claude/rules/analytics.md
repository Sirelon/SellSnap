---
paths:
  - "composeApp/src/commonMain/kotlin/com/sirelon/sellsnap/analytics/**"
  - "composeApp/src/commonMain/kotlin/**/*ViewModel.kt"
---

# Analytics

Firebase Analytics and Crashlytics behind `composeApp/.../analytics/Analytics.kt`; event names and their params live in `AnalyticsEvents`. Analytics events wait for the seller's consent, Crashlytics is always on.

**Every error the seller is shown goes through `Analytics.reportShownError(screen, reason, throwable)`** — snackbar, inline error, failure sheet alike, next to the `postEffect`/`setState` that shows it. It logs `error_shown` (snake_case `screen` and `reason` codes, never user text) and records one Crashlytics non-fatal: the real throwable, or a `ShownErrorException("<screen>: <reason>")` when there is none. Do not call `recordException` beside it. `recordNonFatal = false` is only for input-validation nudges in normal use, such as a title that is still too short.
