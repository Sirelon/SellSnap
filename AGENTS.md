# AGENTS

## Purpose
AI-optimized repo map for agents working in this workspace. Read this first; only crawl deeper when the task clearly needs it.

These rules apply to every task in this project unless explicitly overridden.
Bias: caution over speed on non-trivial work. Use judgment on trivial tasks.

### Rule 1 — Think Before Coding
State assumptions explicitly. If uncertain, ask rather than guess.
Present multiple interpretations when ambiguity exists.
Push back when a simpler approach exists.
Stop when confused. Name what's unclear.

### Rule 2 — Simplicity First
Minimum code that solves the problem. Nothing speculative.
No features beyond what was asked. No abstractions for single-use code.
Test: would a senior engineer say this is overcomplicated? If yes, simplify.

### Rule 3 — Surgical Changes
Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting.
Don't refactor what isn't broken. Match existing style.

### Rule 4 — Goal-Driven Execution
Define success criteria. Loop until verified.
Don't follow steps. Define success and iterate.
Strong success criteria let you loop independently.

### Rule 5 — Use the model only for judgment calls
Use me for: classification, drafting, summarization, extraction.
Do NOT use me for: routing, retries, deterministic transforms.
If code can answer, code answers.

### Rule 6 — Token budgets are not advisory
Per-task: 4,000 tokens. Per-session: 30,000 tokens.
If approaching budget, summarize and start fresh.
Surface the breach. Do not silently overrun.

### Rule 7 — Surface conflicts, don't average them
If two patterns contradict, pick one (more recent / more tested).
Explain why. Flag the other for cleanup.
Don't blend conflicting patterns.

### Rule 8 — Read before you write
Before adding code, read exports, immediate callers, shared utilities.
"Looks orthogonal" is dangerous. If unsure why code is structured a way, ask.

### Rule 9 — Tests verify intent, not just behavior
Tests must encode WHY behavior matters, not just WHAT it does.
A test that can't fail when business logic changes is wrong.

### Rule 10 — Checkpoint after every significant step
Summarize what was done, what's verified, what's left.
Don't continue from a state you can't describe back.
If you lose track, stop and restate.

### Rule 11 — Match the codebase's conventions, even if you disagree
Conformance > taste inside the codebase.
If you genuinely think a convention is harmful, surface it. Don't fork silently.

### Rule 12 — Fail loud
"Completed" is wrong if anything was skipped silently.
"Tests pass" is wrong if any were skipped.
Default to surfacing uncertainty, not hiding it.

### Small Fix Fast Lane
For narrow bug fixes where the affected files are obvious:
- Skip memory lookup unless the bug depends on prior decisions or workspace history.
- Inspect only the direct implementation files and immediate UI/state callers first.
- Keep progress updates to major phase changes only.
- Limit command output aggressively.
- Validate with the narrowest meaningful build/test command plus `git diff --check`.
- If asked to publish, use the standard git/PR flow without re-reading workflow docs unless blocked.

## Modules

### `composeApp`
- Shared Compose Multiplatform UI module.
- Targets:
  - Android
  - iOS framework
  - Desktop JVM
  - Web JS
  - Web Wasm
- Main responsibilities:
  - app shell and top-level navigation
  - feature UIs and view models
  - shared design system
  - Koin wiring for UI/domain layer
  - Ktor/OpenAI client setup
  - media upload, camera, file picker, permissions

### `shared`
- Cross-platform domain/config module used by app and server.
- Main responsibilities:
  - BuildKonfig-backed secrets
  - Supabase client wrapper
  - shared models / config / platform helpers

### `androidApp`
- Thin Android application wrapper around `:composeApp`.
- Hosts `MainActivity`.
- Initializes Android-specific storage and receives OLX auth deep links.

### `iosApp`
- Xcode entrypoint.
- SwiftUI wrapper around the shared Compose app/framework.

### `server`
- Minimal Ktor JVM backend.
- Depends on `:shared`.
- Current implementation is tiny; do not assume backend business logic lives here.

## Gradle Structure
- Root includes exactly:
  - `:composeApp`
  - `:androidApp`
  - `:server`
  - `:shared`
- Version catalog: `gradle/libs.versions.toml`
- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is enabled in `settings.gradle.kts`.
- Gradle JVM/toolchain resolution is wired through `org.gradle.toolchains.foojay-resolver-convention`.
- `kotlin.mpp.applyDefaultHierarchyTemplate=false` is intentionally set in `gradle.properties`.

## Source Set Map

### `composeApp/src/commonMain`
- Most app logic lives here.
- High-value packages:
  - `features/` feature code
  - `designsystem/` reusable UI primitives/tokens
  - `navigation/` destination types and layouts
  - `startup/` startup state and top-level nav view model
  - `di/` Koin modules
  - `network/` Ktor/OpenAI client setup
  - `camera/`, `datastore/`, `features/media/` platform-facing abstractions

### `composeApp` platform source sets
- `androidMain`: Android actuals for camera, image conversion, datastore, OLX web view.
- `jvmMain`: Desktop entrypoint and desktop actuals.
- `jsMain` / `wasmJsMain`: web entrypoints and web actuals.
- `iosMain`: shared iOS source set exists and depends on `dataStoreMain`.
- `dataStoreMain`: common source set used by Android/JVM/iOS for datastore support.
- `jsWasmMain`: shared source set for JS + Wasm web code.

### `shared/src/commonMain`
- Core packages:
  - `supabase/`
  - `config/`
  - `platform/`
- This module is intentionally small but important.

## App Entry Points
- Android: `androidApp/src/main/kotlin/com/sirelon/aicalories/MainActivity.kt`
- Desktop: `composeApp/src/jvmMain/kotlin/com/sirelon/aicalories/main.kt`
- Web Wasm: `composeApp/src/wasmJsMain/kotlin/com/sirelon/aicalories/main.kt`
- Root composable: `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/App.kt`
- iOS: `iosApp/iosApp/iOSApp.swift`
- iOS Xcode sync/build bridge: `:composeApp:embedAndSignAppleFrameworkForXcode` is invoked from `iosApp/iosApp.xcodeproj/project.pbxproj`
- Xcode compile phase skips the Gradle bridge when `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`
- Server: `server/src/main/kotlin/com/sirelon/aicalories/Application.kt`

## Navigation Rules
- `App.kt` is intentionally thin. Do not move app navigation state into composables.
- Top-level destinations are defined in `navigation/AppDestination.kt`.
- Top-level back stack ownership lives in `startup/AppNavigationViewModel`.
- Startup routing logic also lives in `AppNavigationViewModel`:
  - splash
  - onboarding gate
  - seller auth/session gate
- Current top-level destinations:
  - `Splash`
  - `SellerOnboarding`
  - `SellerLanding`
  - `Seller`
- If adding new app-level navigation, prefer:
  1. add destination to `AppDestination`
  2. update `AppNavigationViewModel`
  3. register the entry in `App.kt`

## DI Rules
- DI framework is Koin.
- Top-level modules are registered in `composeApp/.../di/KoinModules.kt`.
- `appModule` includes feature modules; `networkModule` provides shared networking clients.
- Feature modules typically live in each feature’s `di/` package.
- Prefer adding dependencies via Koin modules, not manual singleton objects.
- When calling constructors or factory functions with more than 2 arguments, use named parameters.

## Feature Layout Patterns

### Common pattern
Most features use some combination of:
- `data/`
- `di/`
- `presentation/`
- `ui/`
- `model/`

### ViewModel pattern
- Common base lives at `features/common/presentation/BaseViewModel.kt`.
- Contracts are usually split into:
  - `...Contract.kt`
  - `...ViewModel.kt`
  - screen/render layer in `ui/` or feature root file

### Feature inventory
- `features/seller`
  - OLX auth/onboarding/ad-generation flow.
  - Main subareas: `auth/`, `ad/`, `onboarding/`, `profile/`.
- `features/media`
  - Upload, permission, picker, format conversion helpers used by seller ad photos.

## Supabase Flow
- Shared Supabase wrapper: `shared/.../supabase/SupabaseClient.kt`
- Current responsibility is seller media upload:
  - auth with default test credentials when needed
  - file upload to Supabase Storage bucket `test`
  - public URL lookup for uploaded listing photos
- Food-analysis tables/functions/realtime observers do not belong in this seller-only checkout.

## Design System Rules
- Prefer `AppTheme.typography` and `AppTheme.colors`.
- Prefer `AppDimens` tokens over raw `dp`.
- Add new size tokens only when the exact value matters.
- Design system code lives under `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/designsystem/`.
- Reusable templates already exist in `designsystem/templates/`.
- Avoid reaching for raw Material APIs first when an app component/token already exists.
- Use the 40 custom icons (`ic_*.xml`) when suitable instead of Material Design icons.

## Edge-to-Edge / Insets Rules
- New Android-facing Compose screens must respect edge-to-edge.
- Prefer `AppScaffold`, which defaults to `WindowInsets.safeDrawing`, for screen-level layout.
- If using raw Material `Scaffold`, set `contentWindowInsets = WindowInsets.safeDrawing` unless there is a specific reason not to.
- Always apply scaffold `PaddingValues` to screen content and immediately call `consumeWindowInsets(paddingValues)` on the same content container.
- For `LazyColumn`, `LazyRow`, and grids, pass scaffold insets through `contentPadding`; do not only pad a parent container around the list.
- For vertical scroll content with text inputs, keep `android:windowSoftInputMode="adjustResize"` on the hosting activity and ensure the scroll/content container receives safe/IME-aware padding from `AppScaffold` or `WindowInsets.safeDrawing`.
- Avoid screen-level `systemBarsPadding()`, `statusBarsPadding()`, or `navigationBarsPadding()` on parent containers because it prevents true edge-to-edge drawing. Use those modifiers only on individual controls that must stay tappable, such as overlay close buttons.
- Bottom bars and FABs must account for navigation bars, either by living in `Scaffold` slots or by applying navigation-bar padding to the bar/control itself.
- Full-screen dialogs should use edge-to-edge-safe internal content padding. In common Compose Multiplatform code, do not assume Android-only `DialogProperties` parameters are available.

## Platform Abstractions
- Camera launcher uses expect/actual style placement under `camera/`.
- Image conversion is platform-specific under `features/media/ImageFormatConverter.*`.
- Datastore abstraction lives under `datastore/KeyValueStore*`.
- Platform checks are centralized in `shared/.../platform/PlatformTargets.kt`.

## Secrets And Config
- Secrets are resolved in `shared/build.gradle.kts` from:
  - Gradle properties
  - environment variables
  - `local.properties`
- BuildKonfig object:
  - package: `com.sirelon.sellsnap.supabase`
  - object: `SupabaseConfig`
- Fallback defaults exist for local/dev builds; do not mistake them for production values.

## Important Build Notes
- `./gradlew` and the Xcode bridge both depend on `gradle/wrapper/gradle-wrapper.jar`; if it disappears again, shell builds can fall back to local Gradle `9.4.1`, but Xcode sync/build needs the wrapper jar restored.
- `:composeApp` is an Android KMP library target, not the app wrapper. It does not expose `assembleDebug`; use `:composeApp:assemble` for the library artifact or `:androidApp:assembleDebug` for the installable Android APK.

## Common Commands
- Build `composeApp` Android library artifact: `./gradlew :composeApp:assemble`
- Build Android app wrapper APK: `./gradlew :androidApp:assembleDebug`
- Build desktop JVM artifact: `./gradlew :composeApp:jvmJar`
- Run desktop app: `./gradlew :composeApp:run`
- Package desktop native app for current OS: `./gradlew :composeApp:packageDistributionForCurrentOS`
- Build shared module: `./gradlew :shared:build`
- Build server: `./gradlew :server:build`
- Run server: `./gradlew :server:run`
- Run server in Ktor development mode: `./gradlew :server:run -Pdevelopment`
- Build web Wasm production bundle: `./gradlew :composeApp:wasmJsBrowserProductionWebpack`
- Run web Wasm: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- Build web JS production bundle: `./gradlew :composeApp:jsBrowserProductionWebpack`
- Run web JS: `./gradlew :composeApp:jsBrowserDevelopmentRun`
- Build/sign Apple framework for Xcode: `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
- Run all tests: `./gradlew allTests`
- Android lint: `./gradlew lint`
- JS tests: `./gradlew jsTest`
- JVM tests: `./gradlew jvmTest`
- iOS simulator tests: `./gradlew iosSimulatorArm64Test`

## Fast “Where Do I Edit?” Guide
- Add app-level screen/navigation:
  - `navigation/AppDestination.kt`
  - `startup/AppNavigationViewModel.kt`
  - `App.kt`
- Add a feature dependency or ViewModel:
  - feature `di/*Module.kt`
  - `di/KoinModules.kt` if it is a new top-level feature module
- Change app theme/tokens/components:
  - `designsystem/`
- Change Supabase or secret-backed config:
  - `shared/build.gradle.kts`
  - `shared/src/commonMain/kotlin/com/sirelon/aicalories/config/`
  - `shared/src/commonMain/kotlin/com/sirelon/aicalories/supabase/`
- Change OLX auth behavior:
  - `features/seller/auth/`
- Change Android deep link behavior:
  - `androidApp/.../MainActivity.kt`
- Change web callback behavior:
  - `composeApp/src/wasmJsMain/.../main.kt`
- Change ad publish behavior / publish button state machine:
  - `features/seller/ad/preview_ad/PreviewAdViewModel.kt`
  - `features/seller/ad/data/PostAdvertRequestMapper.kt`
- Change seller profile fetch/edit/logout or publish-blocking contact-name recovery:
  - `features/seller/profile/`
  - `features/seller/ad/AdRootScreen.kt`
  - `features/seller/ad/preview_ad/PreviewAdViewModel.kt`
- Change seller ad flow timing / ready-to-publish elapsed-time behavior:
  - `features/seller/ad/AdFlowTimerStore.kt`
  - `features/seller/ad/ElapsedTimeFormatter.kt`
  - `features/seller/ad/generate_ad/GenerateAdViewModel.kt`
  - `features/seller/ad/preview_ad/PreviewAdScreen.kt`
  - `features/seller/ad/publish_success/PublishSuccessScreen.kt`
- Change AI ad generation pipeline:
  - `features/seller/ad/generate_ad/GenerateAdViewModel.kt`
  - `features/seller/openai/OpenAIClient.kt`
- Change which OLX top-level categories are user-facing:
  - `features/seller/categories/data/CategoriesRepository.kt` (`notSupportedParentIds`)
- Change attribute validation rules:
  - `features/seller/categories/domain/AttributeValidator.kt`
- Change price formatting / thousand-separator behavior:
  - `designsystem/InputTransformations.kt` (`DigitOnlyInputTransformation`, `ThousandSeparatorOutputTransformation`)

## Documentation / Lookup Rules
- For Android or Google APIs/libraries, use the Google dev MCP tools instead of memory or generic web search.
- Good examples:
  - Jetpack Compose
  - AndroidX
  - Material
  - Google Play services
  - Firebase

## API Response Class Conventions

These rules apply to every class that directly maps a JSON API response (OLX, Supabase, or any external service). Violating them will be rejected in review.

### Naming
- Suffix is `Response`, never `Dto` or `Model`. Example: `OlxAttributeResponse`, not `OlxAttributeDto`.
- File lives in a `response/` sub-package inside the relevant `data/` package.

### Visibility
- Always `internal`. Response classes are an implementation detail of the data layer; nothing outside `data/` should reference them directly.

### Class kind
- Plain `class`, never `data class`. Response classes are deserialization targets, not value objects.

### Serialization
- Always annotate with `@Serializable`.
- Every field must have `@SerialName("json_key_name")` — even when the Kotlin name matches the JSON key exactly. This makes the contract explicit and rename-safe.

### Nullability and defaults
- Every field must be nullable (`?`). The backend cannot be trusted to always send every field.
- No default values in response classes. All defaults belong in the mapper.
- The mapper must handle every `null` case explicitly and supply appropriate fallback values.

### Mapper responsibilities
- Skip / filter out response items that are missing essential identity fields (e.g., a `code` that is `null`). Use `mapNotNull` for list transformations.
- Supply domain-layer defaults for missing optional fields (empty string, `false`, `emptyList()`, etc.).

### Custom serializers
- Avoid custom `KSerializer` implementations where the built-in behavior is sufficient.
- The OLX Ktor client is configured with `isLenient = true`, which means numeric JSON primitives are accepted where a `String` is expected — no custom serializer needed for mixed int/string id fields.

### ViewModel / state pattern (seller-wide)

- Every seller VM extends `BaseViewModel<State, Event, Effect>` from `features/common/presentation/BaseViewModel.kt`.
- Contracts live in `*Contract.kt` files with sealed `Event` and sealed `Effect` interfaces.
- State updates use `setState { it.copy(...) }`. One-shot side effects use `postEffect(...)`.
- Repositories return `Flow<T>`; VMs subscribe via `.launchIn(viewModelScope)` and use `.catch { ... }` to keep the stream alive across transient errors. `PreviewAdViewModel` is the canonical reference.
- `CategoriesRepository` caches the (filtered) category tree via `shareIn(GlobalScope, Lazily, 1)` — see `BUGS.md` for why this is on the cleanup list.

### Category filtering

`CategoriesRepository.notSupportedParentIds` blocklist removes top-level OLX categories that aren't part of this product:

| ID | Category |
| --- | --- |
| 1 | Real estate (нерухомість) |
| 6 | Work (робота) |
| 7 | Business & services (бізнес і послуги) |
| 35 | Animals (тварини — incl. zoo goods, currently unsupported) |
| 1532 | Auto transport |
| 3428 | Rental & leasing (Оренда та прокат) |
| 3709 | Daily rentals (житло подобово) |

Update this list when adding or hiding categories.

### Currency / formatting

- Currency is hardcoded to UAH (₴) in `PostAdvertRequestMapper` and the price card UI. SIR-15 deferred — see `BUGS.md`.
- Thousand-separator handling uses `DigitOnlyInputTransformation` and `ThousandSeparatorOutputTransformation` from `designsystem/InputTransformations.kt`.

### OLX deep-link / callback wiring

- Default redirect URI: `selolxai://olx-auth/callback` (configurable via `OLX_REDIRECT_URI` BuildKonfig key).
- Android — `MainActivity.publishOlxCallback(intent)` reads `intent.data` and forwards the URL to `OlxAuthCallbackBridge.publishCallback(url)`. The intent filter must match the configured scheme; verify `androidApp/.../AndroidManifest.xml` whenever the scheme changes.
- Web (Wasm) — `composeApp/src/wasmJsMain/.../main.kt` checks `window.location` for `code=` / `error=` query parameters before mounting Compose, then calls `OlxAuthCallbackBridge.publishCallback`.
- The bridge is a global object (`OlxAuthCallbackBridge`); listeners are collected from both the seller landing and seller profile flows. Concurrency / replay caveats in `BUGS.md`.
