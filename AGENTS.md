# AGENTS

## Purpose
AI-optimized repo map for agents working in this workspace. Read this first; only crawl deeper when the task clearly needs it.

## Project Snapshot
- Product scope is seller-only:
  - OLX seller flow with AI ad generation
  - seller onboarding/auth/profile
  - seller media upload, category/attribute editing, location, and publishing
- Food analysis, history, agile estimation, and data-generator features were extracted out of this checkout. Do not reintroduce them unless explicitly requested.
- Tech stack:
  - Kotlin `2.3.20`
  - Compose Multiplatform `1.11.0-beta02`
  - Android Gradle Plugin `9.1.1`
  - Ktor `3.4.2`
  - Koin `4.2.1`
  - Supabase Kotlin `3.5.0`
  - Navigation3 runtime/UI
- JVM target is `11` across Android/JVM/server.

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
  - Main subareas: `auth/`, `ad/`, `onboarding/`.
- `features/media`
  - Upload, permission, picker, format conversion helpers used by seller ad photos.

## Seller / OLX Flow
- This is a real project concern, not sample code.
- Important classes:
  - auth repo: `features/seller/auth/data/OlxAuthRepository.kt`
  - API client: `features/seller/auth/data/OlxApiClient.kt`
  - HTTP client factory: `features/seller/auth/data/OlxHttpClientFactory.kt`
  - redirect parsing: `features/seller/auth/data/DefaultOlxRedirectHandler.kt`
  - error parsing: `features/seller/auth/data/OlxRemoteErrorParser.kt`
  - session/token stores: `OlxAuthSessionStore`, `OlxTokenStore` (in `OlxContracts.kt`)
  - redirect bridge: `OlxAuthCallbackBridge.kt`
  - domain models / errors: `features/seller/auth/domain/OlxModels.kt` (also currently holds `OlxMeResponse`/`OlxUserResponse`, which are misplaced — see `BUGS.md`)
- Deep link callback handling exists on:
  - Android via `MainActivity`
  - Web via `wasmJsMain/main.kt`
- Redirect URI default: `selolxai://olx-auth/callback`
- Full breakdown of the seller/OLX flow lives in **Seller / OLX Deep Dive** at the bottom of this file.

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
- Keys currently resolved:
  - `OPENAI_KEY` / `openai.key`
  - `SUPABASE_URL` / `supabase.url`
  - `SUPABASE_KEY` / `supabase.key`
  - `SUPABASE_DEFAULT_EMAIL` / `supabase.default.email`
  - `SUPABASE_DEFAULT_PASSWORD` / `supabase.default.password`
  - `OLX_CLIENT_ID` / `olx.client.id`
  - `OLX_CLIENT_SECRET` / `olx.client.secret`
  - `OLX_SCOPE` / `olx.scope`
  - `OLX_AUTH_BASE_URL` / `olx.auth.base.url`
  - `OLX_API_BASE_URL` / `olx.api.base.url`
  - `OLX_REDIRECT_URI` / `olx.redirect.uri`
- Fallback defaults exist for local/dev builds; do not mistake them for production values.

## Important Build Notes
- `composeApp/build.gradle.kts` has a custom Compose resources setup for Android.
- `composeApp` applies the Compose Hot Reload plugin; keep it in mind when touching desktop/web dev workflow or plugin configuration.
- Do not remove the `copyComposeResourcesToAndroidAssets` workaround if you encounter it elsewhere in the file/history.
- `copyAndroidMainComposeResourcesToAndroidAssets` may be disabled intentionally to avoid build failures.
- `compose.resources` generates public resources class:
  - package `com.sirelon.sellsnap.generated.resources`
- Android resources are enabled for the KMP library target.
- In this workspace, `./gradlew` may fail because `gradle/wrapper/gradle-wrapper.jar` is missing.
- If that happens, rerun builds with a local Gradle binary that matches `gradle/wrapper/gradle-wrapper.properties` (currently `9.4.1`).
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
- Change seller ad flow timing / ready-to-publish elapsed-time behavior:
  - `features/seller/ad/AdFlowTimerStore.kt`
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

## Working Assumptions
- This repo is seller-only. Do not add calorie-analysis, history, agile, or data-generator functionality back unless explicitly requested.
- `server` is currently minimal; most business logic is client/shared-side.
- `App.kt` should stay a rendering shell, not a dumping ground.
- When a change touches platform behavior, check for corresponding actual implementations in `androidMain`, `jvmMain`, `jsMain`, and `wasmJsMain`.

## Seller / OLX Deep Dive

Concrete map of the seller/OLX feature tree under `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/`. Pair this with `BUGS.md` for known issues in the same code paths.

### Seller package layout

- `auth/` — OAuth + token + API client
  - `data/`: `OlxAuthRepository.kt`, `OlxApiClient.kt`, `OlxAuthCallbackBridge.kt`, `OlxConfig.kt`, `OlxContracts.kt` (token/session stores), `OlxHttpClientFactory.kt`, `OlxRemoteErrorParser.kt`, `DefaultOlxRedirectHandler.kt`, `OlxAdvertModels.kt`, `response/PostAdvertResponse.kt`
  - `domain/OlxModels.kt` — tokens, callback, session state, errors. Currently also holds `OlxMeResponse` / `OlxUserResponse` which belong under `data/response/` (see `BUGS.md`).
  - `presentation/`: `SellerAuthViewModel.kt`, `SellerAuthContract.kt`, `SellerLandingScreen.kt`, `OlxAuthWebView.kt` (expect/actual)
  - `di/SellerAuthModule.kt`
- `ad/` — ad creation pipeline
  - `Advertisement.kt`, `AdvertisementWithAttributes.kt`, `AdRootScreen.kt`, `AdFlowTimerStore.kt`
  - `data/`: `GeneratedAdMapper.kt`, `PostAdvertRequestMapper.kt`
  - `generate_ad/`: `GenerateAdViewModel.kt`, `GenerateAdContract.kt`, `GenerateAdScreen.kt`, `AiProcessingContent.kt`, `di/GenerateAdModule.kt`
  - `preview_ad/`: `PreviewAdViewModel.kt`, `PreviewAdContract.kt`, `PreviewAdScreen.kt`, `di/PreviewAdModule.kt`
- `categories/` — OLX category tree + attribute editor
  - `data/CategoriesRepository.kt`, `data/responses/` (note: plural — convention says `response/`, see `BUGS.md`)
  - `domain/`: `CategoriesMapper.kt`, `AttributeValidator.kt`, `AttributeModels.kt`, `OlxCategory.kt`
  - `presentation/`: `CategoryPickerViewModel.kt`, `CategoryPickerContract.kt`, `CategoryPickerScreens.kt`, `CategoryIcons.kt`
  - `ui/AttributesScreen.kt`, `ui/AttributeItem.kt`
  - `CategoriesModule.kt`
- `location/` — geo lookup
  - `LocationProvider.kt` (expect/actual), `OlxLocation.kt`
  - `data/LocationRepository.kt`, `data/response/OlxLocationResponse.kt`
- `openai/` — OpenAI clients used during ad generation (separate from the analyze-flow OpenAI usage in `features/analyze/`)
  - `OpenAIClient.kt`, `requests/`, `responses/` (plural — see `BUGS.md`)
- `onboarding/OnboardingScreen.kt` — 3-page HorizontalPager carousel; no ViewModel.

### OLX OAuth flow (end-to-end)

1. `SellerAuthViewModel.startAuthorization()` calls `OlxAuthRepository.createAuthorizationRequest()`. The repository builds the auth URL with a UUID `state`, persists `OlxPendingAuthSession` in `OlxAuthSessionStore`, and returns the URL.
2. `OlxAuthWebView.kt` (expect/actual) loads the URL. Android intercepts the redirect URI in `WebViewClient.shouldOverrideUrlLoading`. The WebView also rewrites `m.olx.ua` → `www.olx.ua` to dodge OLX's mobile redirect.
3. The redirect URL routes through `OlxAuthCallbackBridge` (a global object that buffers a single URL until a listener subscribes) and ends in `SellerAuthViewModel.onCallbackReceived(url)`.
4. `OlxAuthRepository.completeAuthorization(url)` parses the callback (`DefaultOlxRedirectHandler`), validates that `state` matches the pending session, exchanges the code at `POST /api/{authTokenPath}`, persists the `OlxTokens` via `OlxTokenStore`, and clears the pending session.
5. `SellerAuthViewModel` emits `OpenHome`; `AppNavigationViewModel` switches the top-level destination to `Seller`.
6. Token refresh — two paths exist and must stay in agreement:
   - `OlxAuthRepository.refreshIfNeeded(force)` — called explicitly by repositories before sensitive calls; uses `OlxTokens.isExpired(now, safetyWindow=60s)`.
   - Ktor `Auth { bearer { ... } }` plugin in `OlxHttpClientFactory.createOlxAuthorizedHttpClient` — auto-refreshes on `401` via `refreshOlxBearerTokens`. On terminal errors (`InvalidGrant`, `InvalidToken`) the token store is cleared; on transient errors it isn't.

### Ad publishing state machine (delivered across SIR-32 / SIR-33 / SIR-34)

- `GenerateAdScreen` runs a 5-step async pipeline through `GenerateAdViewModel`:
  1. Upload images via `MediaUploadHelper.publicUrl(path)`.
  2. `OpenAIClient.analyzeThing()` produces title, description, suggested/min/max price, image list.
  3. `CategoriesRepository.categorySuggestion(title)` → suggested `OlxCategory`.
  4. `CategoriesRepository.getAttributes(categoryId)` → required + optional attributes.
  5. `OpenAIClient.fillAdditionalInfo()` → attribute values.

  Result is `AdvertisementWithAttributes`, surfaced via `OpenAdPreview` effect.
  Timing for this flow is tracked in `AdFlowTimerStore`, a Koin `singleOf` singleton. `GenerateAdViewModel` starts the timer before the pipeline and marks generation complete before opening preview.
- `PreviewAdScreen` shows:
  - Editable title / description (`TextFieldState` based)
  - Image gallery (HorizontalPager)
  - Green "ready in Xs" banner sourced from `PreviewAdViewModel`, which reads `AdFlowTimerStore.generationElapsedMs()`
  - Price card (Slider with min/max coercion + AI-suggested band — SIR-33)
  - Category pill with debounced re-suggestion (300 ms on title change; see `PreviewAdViewModel.init` block)
  - Location chip with on-demand geo fetch (`FetchLocation` event)
  - Attributes section driven by `AttributeInputType` (`SingleSelect`, `MultiSelect`, `NumericInput`, `TextInput`)
  - Page-level validation banner + status card + publish button state (SIR-34)
- Publish path: `PreviewAdViewModel.publishAdvert()` validates every attribute via `AttributeValidator`, fetches the contact name via `getAuthenticatedUser()`, builds `PostAdvertRequest` via `PostAdvertRequestMapper.map`, posts to `OlxApiClient.postAdvert`, then emits `PublishSuccess(advertUrl)` or `ShowMessage`. The `isPublishing` flag drives the button enabled/loading state.
  `AppNavigationViewModel.navigateToPublishSuccess()` reads `AdFlowTimerStore.totalElapsedMs()` into `AppDestination.SellerPublishSuccess`, and `popToAdRoot()` clears the store before returning to `Seller`.

### OLX API endpoints wrapped

All wrappers live in `OlxApiClient.kt` unless stated.

| Endpoint | Method | Wrapper | Returns |
| --- | --- | --- | --- |
| `users/me` | GET | `getAuthenticatedUser()` (returns `Result`) | `OlxMeResponse` |
| `categories` | GET | `loadCategories()` (throws) | `List<OlxCategoryResponse>` |
| `categories/suggestion?q=` | GET | `loadCategorySuggestionId(query)` (throws) | `Int?` |
| `categories/{id}/attributes` | GET | `loadAttributes(categoryId)` (throws) | `List<OlxAttributeResponse>` |
| `locations?latitude=&longitude=` | GET | `getLocations(lat, lng)` (throws) | `OlxLocationsRootResponse` |
| `adverts` | POST | `postAdvert(req)` (returns `Result`) | `PostAdvertResult` (carries `advertUrl`) |
| `/api/{authTokenPath}` | POST | internal — `OlxAuthRepository.exchangeAuthorizationCode` / `refreshAccessToken`, plus `OlxHttpClientFactory.refreshOlxBearerTokens` | `OlxTokens` |

The mixed throws-vs-`Result<T>` surface is **intentional today but should be unified** — see `BUGS.md`.

### Response-class conventions vs reality

- The general convention (declared earlier in this file) is followed by most files in `seller/.../data/response/` and `seller/.../data/responses/`.
- Two known violations: `OlxMeResponse` and `OlxUserResponse` live in `auth/domain/OlxModels.kt` as public `data class` with field defaults. They should move under `auth/data/response/` and become `internal class` with all fields nullable + no defaults. Tracked in `BUGS.md`.
- Folder name disagreement: some packages use `response/` (singular — what the rules say), others use `responses/`. Tracked in `BUGS.md`.

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

### Attribute validation

- `AttributeValidator.validate(attribute, selectedValues)` returns `AttributeValidationResult.Invalid` with a `reason` (`Required`, `MustBeNumeric`, `BelowMinimum`, `AboveMaximum`, `InvalidSelection`, `MultipleValuesNotAllowed`) or success.
- Errors are rendered inline in `PreviewAdScreen` via the `ErrorPill` component. The publish button stays interactive but `publishAdvert()` short-circuits when any required attribute fails. The follow-up to auto-scroll/expand the first failing field is tracked under SIR-34 (see `BUGS.md`).

### Currency / formatting

- Currency is hardcoded to UAH (₴) in `PostAdvertRequestMapper` and the price card UI. SIR-15 deferred — see `BUGS.md`.
- Thousand-separator handling uses `DigitOnlyInputTransformation` and `ThousandSeparatorOutputTransformation` from `designsystem/InputTransformations.kt`.

### OLX deep-link / callback wiring

- Default redirect URI: `selolxai://olx-auth/callback` (configurable via `OLX_REDIRECT_URI` BuildKonfig key).
- Android — `MainActivity.publishOlxCallback(intent)` reads `intent.data` and forwards the URL to `OlxAuthCallbackBridge.publishCallback(url)`. The intent filter must match the configured scheme; verify `androidApp/.../AndroidManifest.xml` whenever the scheme changes.
- Web (Wasm) — `composeApp/src/wasmJsMain/.../main.kt` checks `window.location` for `code=` / `error=` query parameters before mounting Compose, then calls `OlxAuthCallbackBridge.publishCallback`.
- The bridge is a global object (`OlxAuthCallbackBridge`); listener is set inside the seller landing screen lifecycle. Concurrency / replay caveats in `BUGS.md`.

### OLX HTTP client config (`OlxHttpClientFactory.kt`)

- `ContentNegotiation` JSON: `ignoreUnknownKeys = true`, `isLenient = true`, `explicitNulls = false`. The `isLenient = true` is intentional — OLX sends mixed int/string id fields that would otherwise need a custom serializer.
- `Logging` plugin set to `LogLevel.INFO`. This logs request/response bodies, including `Authorization: Bearer …` headers and refresh tokens — see `BUGS.md`.
- `defaultRequest.url(SupabaseConfig.OLX_API_BASE_URL)` — the OLX base URL is currently read from `SupabaseConfig`. Cross-package coupling, tracked in `BUGS.md`.
- `expectSuccess = false` — every endpoint must check `response.status.isSuccess()` manually and parse failures via `OlxRemoteErrorParser`.
- No `HttpTimeout` plugin installed — see `BUGS.md`.

### Top-level docs to know

- `SELLER_ICON_REPLACEMENTS.md` — five seller screens use custom `Res.drawable.ic_*` (VectorDrawable) instead of Material icons. Reference this when editing `PreviewAdScreen.kt`, `GenerateAdScreen.kt`, `AiProcessingContent.kt`, `SellerLandingScreen.kt`, `OnboardingScreen.kt`.
- `DEPENDENCY_UPDATE_SUMMARY.md` — record of recent dependency bumps (Kotlin 2.3, Koin 4.2, etc.). Useful when the build breaks after a dependency change.
- `BUGS.md` — known seller/OLX pitfalls and convention violations; drive future fix-up tickets from this list.
