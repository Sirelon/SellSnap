# CLAUDE.md

See [AGENTS.md](AGENTS.md) for the full repo map, module breakdown, and source-set layout — read it first.

## Build Commands

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Compile-check only (fast)
./gradlew :composeApp:compileAndroidMain

# All unit tests (KMP — no Android host tests enabled)
./gradlew :composeApp:jvmTest

# Desktop run
./gradlew :composeApp:run
```

Always run `:composeApp:compileAndroidMain` (or the relevant compile task) before committing or pushing.

## Architecture Conventions

- Shared app logic lives in `composeApp/src/commonMain`; platform specifics go in `androidMain`, `iosMain`, etc.
- Business logic belongs in ViewModels or domain services — not in navigation/route layers or composables
- Reuse existing design-system components from `designsystem/` before creating new ones; search for similar names first
- Fix root causes: don't mask NPEs or missing DI bindings with null-safety workarounds
- Use `getString` / resource APIs for user-facing strings; do not use `String.format` as a workaround

## Git Conventions

- Branch naming: `feature/SIR-XX-short-description` or `bugfix/SIR-XX-short-description`
- Commit prefix: `SIR-XX: short imperative description`
- Never push without explicit permission (see global CLAUDE.md)
