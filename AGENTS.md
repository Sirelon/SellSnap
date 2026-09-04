# AGENTS

## Purpose
AI-optimized repo map for agents working in this workspace. Read this first; only crawl deeper when the task clearly needs it.

These rules apply to every task in this project unless explicitly overridden.

## Rules

Each one is checkable. Where you cannot show it was met, say so (rule 7).

1. Read before you write: exports, callers, shared utilities, and this file's section for the
   area. State assumptions. A decision that is not yours — product shape, user-facing copy,
   what a user sees — is asked, in one line, with your recommended answer.
2. Minimum code that solves the task. No speculative abstractions, no touching adjacent code,
   match the existing style even where you disagree.
3. External facts carry their source. Anything asserted about OLX — a status meaning, a
   restriction, a market difference — cites portal and section in the KDoc next to the code.
   Everything else is marked `Inferred:`. See Documentation / Lookup Rules.
4. **Never invent a limitation.** Withhold a user action only on a documented refusal; if the
   API does not say no, offer it and let the API's own error speak. A missing button is
   invisible in telemetry, and every restriction this app invented cost the seller something.
5. A user-facing state, label, or paragraph exists only if the user does something different
   because of it. Two API values the user treats the same way are one state.
6. User-facing copy is approved before it is translated: post the English `key → text` list,
   wait, then run `localize` once. See Localization.
7. Report what was skipped. "Completed" with a silent gap is wrong; name the gap.
8. Name the cause before the second fix. If a symptom survives a fix, write the causal chain —
   what was observed, what produced it — before touching code again.
9. A gotcha that cost more than one attempt is written into this file, or the matching
   `.claude/rules/` file, in the same commit as the fix.

Small fixes with obvious files: read the implementation and its immediate callers only,
validate with the narrowest meaningful build or test command plus `git diff --check`, and skip
anything above that does not apply.

## Subagent limits (append to every brief, after the global block)

> No russian text in any file. Never commit `screenshotMode = true`. Never attempt an OLX
> login — accounts are banned after repeated failures; the owner logs in himself. Never call
> anything that mutates the owner's OLX account (commands, PUT, DELETE, packets).

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

## Localization
- New user-facing strings go in `composeApp/src/commonMain/composeResources/values/strings.xml` (English base). Interpolate with `stringResource(id, arg)`; never `String.format`.
- **English copy is approved before any locale is touched.** When a ticket adds or rewords
  user-facing strings, post the list — one line per key, `key → text` — and wait for the owner's
  answer. Then run the `localize` agent once, with the final key list. One `localize` run per
  ticket is the budget; six runs on one milestone is what this rule exists to stop.
- **Then show the Ukrainian.** Ukrainian is the language the owner actually reads and the primary
  market, so after `localize` returns, post the `key → text` list for `values-uk` before calling
  the ticket done. The other locales follow from it and are not posted.
- Copy is written from the seller's side and answers two questions: what is happening to my
  listing, and do I need to do anything. Words that fail: `status`, `moderation`, `verification`,
  `API`, and anything describing how SellSnap talks to OLX ("Changes go straight to OLX"). A badge
  is one or two words the seller sees in their own OLX cabinet. An explanation paragraph exists
  only where the seller must act somewhere else or the marketplace said no; a state whose badge and
  buttons already say everything has none.
- Translation of new or changed keys into every locale is done by the `localize` agent — `.claude/agents/localize.md` holds the locale map and all translation rules. `.claude/rules/localization.md` loads the sequence when you open a `strings.xml`.
- **No russian, anywhere, by product decision.** `values-ru/strings.xml` is not a translation — it's a byte-identical copy of `values-uk/strings.xml` kept only so a `ru` device/store locale falls back to Ukrainian (see `.claude/agents/localize.md` for the why and the sync step). Do not "fix" it into real russian, and do not add a russian option to the landing page (`docs/index.html`, `docs/assets/landing.js`) or any other SellSnap-facing surface (store listings, screenshots, release notes) — those exclude `ru` entirely, with no fallback shim at all.

## Edge-to-Edge / Insets Rules

Rules: `.claude/rules/edge-to-edge.md` — loads when you open a `ui/`, `*Screen.kt` or
`designsystem/` file.

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

## UI Tests And Store Screenshots (Maestro)

Flows live in `.maestro/`, runner scripts in `scripts/maestro-*.sh`. Three things bite:

- **Android runs need API 33+** — per-country language uses `cmd locale set-app-locales`,
  which does not exist below 33; the scripts refuse to start rather than capture every country
  in the device language.
- **Boot only one iOS simulator per run.** Maestro starts one driver per device and they
  contend for the same port; the loser is silently ignored and hierarchy and screenshot calls
  return the *other* device's data.
- **`screenshotMode` is committed as `false` and must never be committed `true`.** It bypasses
  the publish confirmation, and `scripts/ship.sh` refuses to release while it is enabled.

Prefer `testTag` ids over visible text in selectors — flows run in 4+ locales. Photos are never
picked through the OS picker. Full workflow: the user-level `sellsnap-screenshots` skill (`~/.claude/skills/`).

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
- Change what a seller can do to a published listing (deactivate / reactivate / finish / delete /
  extend / edit / statistics / expiry):
  - `features/seller/my_ads/domain/AdvertAction.kt` (which actions each `AdvertStatus` may offer)
  - `features/seller/my_ads/data/AdvertLifecycleRepository.kt` (the OLX calls)
  - `features/seller/my_ads/presentation/MyAdvertsViewModel.kt` (the flow between them)
  - `features/seller/my_ads/ui/AdvertActionsSheet.kt` (the one surface it all renders in)
- Change the sold / not-sold outcome data or the AI price-accuracy measurement:
  - `features/seller/my_ads/data/AdvertOutcomeStore.kt`
  - `features/seller/my_ads/domain/AdvertAnalyticsBuckets.kt`
- Change whether a market may extend listings:
  - `features/seller/auth/domain/OlxCountry.kt` (`supportsExtendCommand`)

## Store Assets & Design Assets

Everything that ships to App Store Connect / Play Console lives under `store/`; brand source
material that is not a store asset lives under `brand/`.

| Directory | Holds |
| --- | --- |
| `store/copy/` | All listing text, plus `copy.json` — the localized screenshot captions |
| `store/captures/<device>/<locale>/` | Raw Maestro screenshots. Input, never uploaded |
| `store/assets/` | Finished App Store and Play Store images |
| `store/tools/` | `generate-store-screenshots.mjs` and its `scenes.json` manifest |
| `store/docs/` | `PROGRESS.md` history and per-device screen inventories |
| `brand/` | App icon sources, Claude Design prototype files |

Screenshot-to-caption pairing is an explicit manifest, never a filename index. Full workflow,
preflight checks and known source defects: `.claude/skills/app-store-screenshots/`.

## Documentation / Lookup Rules
- For Android or Google APIs/libraries, use the Google dev MCP tools instead of memory or generic web search.
- Good examples:
  - Jetpack Compose
  - AndroidX
  - Material
  - Google Play services
  - Firebase

### OLX partner API

Every market has its own portal — `developer.olx.pl`, `.ua`, `.pt`, `.ro`, `.bg`, `.kz` — and
they are not equally complete. PL omits the advert-status definitions and lists 4 of the 11
`Advert.status` values; UA and PT define all 11. Reading one portal and assuming it speaks for
the rest is how this app shipped two wrong status meanings.

- **Treat `developer.olx.ua` as the source of truth.** Ukraine is the primary market, and its
  portal is one of the complete ones.
- **Before relying on any behaviour, check whether the markets differ.** Read UA plus at least
  one other portal for the same endpoint and compare. Where a difference exists, it belongs in
  code as a per-country capability — `OlxCountry.supportsExtendCommand` is the pattern — never as
  a single global assumption. A feature that works in Poland may be refused in Romania.
- The only market difference found so far is the `extend` command, which the specs annotate as
  unavailable in UA and PT. That is one data point, not a guarantee that the rest is uniform.
- The OpenAPI yaml is `https://developer.olx.<tld>/swagger/v2/partner_api.yaml`, and nested
  `$ref` files resolve against the same path. Plain `curl` gets a CloudFront 403; fetch through a
  tool that renders (WebFetch, or context7 `/websites/developer_olx_pl_api_doc`).
- Cite the portal and section in the KDoc next to the code that relies on the fact, and mark
  anything you could not confirm `Inferred:` (rule 3). Do not invent a restriction the API does
  not state (rule 4).
- For live probes without a user account — token minting, routing checks — use
  `.claude/skills/olx-api-verify`. Never attempt an OLX login.

## API Response Class Conventions

Rules: `.claude/rules/api-response.md` — loads when you open a `data/response/` file. Review
rejects violations, so read it before adding a class that maps a JSON response.

### ViewModel / state pattern (seller-wide)

- Every seller VM extends `BaseViewModel<State, Event, Effect>` from `features/common/presentation/BaseViewModel.kt`.
- Contracts live in `*Contract.kt` files with sealed `Event` and sealed `Effect` interfaces.
- State updates use `setState { it.copy(...) }`. One-shot side effects use `postEffect(...)`.
- Repositories return `Flow<T>`; VMs subscribe via `.launchIn(viewModelScope)` and use `.catch { ... }` to keep the stream alive across transient errors. `PreviewAdViewModel` is the canonical reference.
- `CategoriesRepository` caches the (filtered) category tree via `shareIn(GlobalScope, Lazily, 1)` — see `BUGS.md` for why this is on the cleanup list.
- **Assert an effect with `viewModel.effects.awaitEffect<T>()`** (`commonTest`,
  `features/common/presentation/AwaitEffect.kt`); never drain-then-assert. Anything resolved
  through compose-resources `getString` is posted after the virtual scheduler has gone idle, so
  `advanceUntilIdle()` and `runCurrent()` cannot see it, and `withTimeout` inside `runTest` fires
  on the virtual clock. For state rather than an effect, await the state:
  `viewModel.state.first { ... }`.

### Ad lifecycle (post-publish actions)

Rules: `.claude/rules/ad-lifecycle.md` — loads when you open anything under
`features/seller/my_ads/`. Covers the seller-facing `AdvertState` model, what is documented
versus inferred about OLX's statuses, OLX's eventual consistency after a command, and the
effect-assertion trap.

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
