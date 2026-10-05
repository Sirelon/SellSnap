# Decider rules

- **Posture**: solo. Smallest shippable slice, iterate. Ships to Play internal testing and TestFlight via the `sellsnap-release` skill; store promotion is manual.
- **Users**: people selling second-hand items on OLX marketplaces (PT, RO, PL, UA, BG). Audience is small, so no live A/B testing; prompt and model changes are verified with a local batch run.
- **Collaborators**: none. The OLX partner API is the only external contract; changes to how it is called are not cross-team but are checked with the `olx-api-verify` skill.
- **Stack constraints**: Kotlin Multiplatform + Compose Multiplatform, Android and iOS from `composeApp/src/commonMain`; platform code only in `androidMain` / `iosMain`.
- **Non-negotiables**: reuse `designsystem/` components; user-facing strings via resources, never `String.format`; `:composeApp:compileAndroidMain` before every commit; commit prefix `SIR-XX:`.
- **Tie-breaker override**: position 2 is "fastest to ship", position 3 is "easiest to undo".
- **Copy**: English base in `composeApp/src/commonMain/composeResources/values/strings.xml`; 7 other locales are filled by the `localize` agent, so flag `needs-translation` and name the keys.
