# BUGS

Known pitfalls, suspect decisions, and convention violations in the seller / OLX code path. Each item is verified against current source and includes a file reference, what's wrong, and a suggested fix. Use this as a backlog seed — link tickets back here when fixing.

Scope: seller / OLX flow only. Agile screens are intentionally excluded.

## Security / privacy

### 1. OAuth state logged in plaintext
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/data/OlxAuthRepository.kt:46`
- **What:** `println("URL: $url")` logs the entire authorization URL, which contains the `state` parameter (CSRF guard) and `client_id`.
- **Fix:** Remove the `println`. If the URL is genuinely useful for debugging, gate it behind a debug-only flag or `Logger` and redact `state`.

### 2. Ktor `LogLevel.INFO` for OLX clients
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/data/OlxHttpClientFactory.kt:97`
- **What:** `Logging { level = LogLevel.INFO }` emits request and response bodies, including `Authorization: Bearer …` headers and refresh tokens, on every authorized request.
- **Fix:** Drop to `LogLevel.NONE` for release builds; pick `HEADERS` or `INFO` only in debug, and add a header sanitizer for `Authorization`.

### 3. OAuth `client_secret` shipped in client binary
- **Where:** `OlxConfig` resolves `clientSecret` via BuildKonfig (`AppConfig.olxClientSecret`); used in `OlxAuthRepository.exchangeAuthorizationCode` and `OlxHttpClientFactory.refreshOlxBearerTokens`.
- **What:** A public OAuth client (mobile / web) cannot keep a secret. Anyone can extract it from the APK/JS bundle.
- **Fix:** Move the token-exchange step to the `:server` module and call it from the client. Or migrate to PKCE (`code_challenge` / `code_verifier`) and remove `client_secret` from token requests.

### 4. Custom URI scheme is hijackable
- **Where:** Default `OLX_REDIRECT_URI = selolxai://olx-auth/callback`.
- **What:** Any Android app can register the same scheme and intercept callbacks.
- **Fix:** Migrate to App Links / Universal Links — register a verified `https://` callback on a domain you control.

## Auth / token handling

### 5. ~~Callback bridge is a global mutable singleton with no consumed-flag~~ ✅ FIXED (SIR-43)
- **Where:** `OlxAuthCallbackBridge.kt`
- **What:** Android intent replay could re-deliver an already-consumed OAuth callback URL.
- **Fix applied:** `seenUrls: MutableStateFlow<Set<String>>` tracks published URLs; `publishCallback` uses `compareAndSet` to atomically suppress duplicates.

### 6. ~~Two refresh paths can disagree on cleanup~~ ✅ FIXED (SIR-43)
- **Where:** `OlxAuthRepository.refreshIfNeeded` and the Ktor `Auth { bearer { refreshTokens { … } } }` block.
- **What:** Explicit refresh and bearer auto-refresh had separate terminal-failure cleanup paths.
- **Fix applied:** Both paths now call `handleTerminalRefreshFailure(...)`, clearing token and pending auth session state for terminal OLX refresh failures.

### 7. Privacy / Terms URLs are placeholder garbage
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/presentation/SellerAuthViewModel.kt:24` and `:29`
- **What:** Both post `LaunchBrowser("https:google.com")` — note the missing `//`. Resolves to a malformed URI on every platform; users tapping the buttons see a browser error.
- **Fix:** Replace with the real URLs, or hide the buttons until URLs are decided.

## Concurrency

### 8. `GlobalScope.shareIn` for category cache
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/categories/data/CategoriesRepository.kt:33–39`
- **What:** `categoriesFlow` is shared into `GlobalScope` with `SharingStarted.Lazily`. The coroutine outlives any caller, can't be cancelled, and ties testing to `GlobalScope`.
- **Fix:** Inject an external `CoroutineScope` (data-layer application scope) via Koin and share within it. Consider `SharingStarted.WhileSubscribed(stopTimeout = 5_000)` to release memory when no consumer is active.

### 9. ~~`OlxAuthCallbackBridge` mutable state without sync~~ ✅ FIXED (SIR-43)
- **Where:** Same file as #5.
- **What:** Duplicate callback delivery could race when Android intent and WebView redirect publish the same URL close together.
- **Fix applied:** Callback replay uses `MutableSharedFlow`; consumed callback URLs are guarded by a `MutableStateFlow` compare-and-set loop, and replay-cache reset remains mutex-protected.

### 10. OLX HTTP base URL leaks through `SupabaseConfig`
- **Where:** `OlxHttpClientFactory.kt:105` — `defaultRequest.url(SupabaseConfig.OLX_API_BASE_URL)`.
- **What:** OLX's base URL is read from a class named `SupabaseConfig`. Confusing to readers and creates cross-package coupling. There's already an `OlxConfig` that should own this constant.
- **Fix:** Move the constant to `OlxConfig.apiBaseUrl` (mirroring `OlxConfig.authBaseUrl`) and read from there. Update `BuildKonfig` wiring accordingly.

## Network / API

### 11. No HTTP timeout configured
- **Where:** `OlxHttpClientFactory.commonOlxHttpClientConfig` (`OlxHttpClientFactory.kt:86–107`)
- **What:** Neither factory installs `HttpTimeout`. Hanging requests can wedge the UI without recovery.
- **Fix:** `install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }`.

### 12. Inconsistent `Result<T>` vs throwing API surface
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/data/OlxApiClient.kt`
- **What:** `getAuthenticatedUser()` and `postAdvert()` return `Result<T>`; `loadCategories()`, `loadCategorySuggestionId()`, `loadAttributes()`, `getLocations()` throw. Callers (`CategoriesRepository`, `LocationRepository`, `PreviewAdViewModel`) handle the two flavors differently.
- **Fix:** Pick one — recommend `Result<T>` everywhere — and update the call sites.

### 13. Empty-data success is collapsed into `IllegalStateException`
- **Where:** `OlxApiClient.postAdvert` (in `OlxApiClient.kt`)
- **What:** Throws `IllegalStateException` when the OLX response is `200` but `body.data?.id` is null. Indistinguishable from a network blip downstream.
- **Fix:** Surface as a typed `OlxApiError.Unknown(reason)` or new `EmptyResponse` variant so the UI can show a meaningful message.

## Convention violations vs AGENTS.md

### 14. `OlxMeResponse` / `OlxUserResponse` violate response-class rules
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/domain/OlxModels.kt:51–67`
- **What:** Both are public `data class`, declared in `domain/`, with `@SerialName` plus default values directly on the response.
- **Fix:** Move to `seller/auth/data/response/` (or `response/me/`), change to `internal class`, drop the `data` modifier, make every field nullable with no defaults, and let the mapper handle nulls (per the rules in AGENTS.md "API Response Class Conventions").

### 15. `response/` vs `responses/` folder naming inconsistency
- **Where:**
  - Singular: `seller/auth/data/response/`, `seller/location/data/response/`
  - Plural: `seller/categories/data/responses/`, `seller/openai/responses/`
- **What:** AGENTS.md prescribes a `response/` sub-package.
- **Fix:** Rename the plural folders to `response/` and update imports.

### 16. `OlxTokens` straddles domain and persistence
- **Where:** `OlxModels.kt:7` (the `data class OlxTokens`).
- **What:** It is a domain value type, but it's `@Serializable` with `@SerialName` because it's also persisted via `OlxTokenStore` (`KeyValueStore`).
- **Fix:** Confirm whether tokens are serialized as JSON on disk. If so, split into a plain domain `OlxTokens` and a separate `OlxTokensRecord` (data-layer) so persistence concerns don't bleed into the domain model.

## Stale TODOs to triage

(Each maps to a Linear ticket where labelled in code.)

### 17. SIR-15 — currency picker not implemented
- **Where:** `PreviewAdScreen.kt:719,727` and `PostAdvertRequestMapper.kt:33`
- **What:** UAH is hardcoded; the comment promises a "change currency" affordance.
- **Fix:** Either deliver the picker or close out SIR-15 and remove the TODOs.

### 18. SIR-34 — auto-open first failing required attribute
- **Where:** `PreviewAdScreen.kt:249`
- **What:** When publish is tapped with invalid attributes, validation runs but nothing scrolls/expands the offending field.
- **Fix:** When `publishAdvert()` short-circuits on `hasErrors`, find the first invalid attribute and emit an effect to scroll its row into view and expand its editor.

### 19. SIR-40 — account info / logout menu stubbed
- **Where:** `GenerateAdScreen.kt:291,299`
- **What:** Account row shows placeholder, logout is a no-op click handler.
- **Fix:** Wire to `OlxApiClient.getAuthenticatedUser()` for the display name and `OlxAuthRepository.logout()` for the menu action.

### 20. SIR-41 — fullscreen lightbox for image gallery
- **Where:** `PreviewAdScreen.kt:529`
- **What:** Tap-to-open lightbox not implemented.
- **Fix:** Add a fullscreen route or modal sheet for the gallery; reuse the existing `HorizontalPager` setup.

### 21. Untracked TODO: "not implemented yet"
- **Where:** `PreviewAdScreen.kt:858`
- **What:** Bare TODO with no ticket reference.
- **Fix:** Determine whether the branch is dead. If it has a real intent, file a ticket and link it; otherwise delete.

### 22. Untracked TODO: "it's incorrect"
- **Where:** `CategoryPickerViewModel.kt:33`
- **What:** Single-line TODO with no detail.
- **Fix:** Investigate intent (likely a wrong navigation/selection branch); replace with concrete description or fix.

## UX / robustness

### 23. Silent location-fetch failure
- **Where:** `PreviewAdViewModel.fetchLocation` (`PreviewAdViewModel.kt:151–160`)
- **What:** Catches all `Exception`s, prints a stack trace, sets `locationLoading = false`, and stops. The user sees the chip stay empty with no explanation.
- **Fix:** Surface as `ShowMessage` or an inline location-chip error; differentiate "permission denied" from "network failed".

### 24. Silent early-return when category / location are null on publish
- **Where:** `PreviewAdViewModel.publishAdvert` (`PreviewAdViewModel.kt:165–166`)
- **What:** `val category = s.selectedCategory ?: return` and `val location = s.location ?: return`. The publish button is supposed to be disabled in these states, but a state desync (rapid re-render + click) silently no-ops with no log or toast.
- **Fix:** Either assert the invariant via the publish-button enabled state and treat null as a programmer error (log + crash in debug), or post a `ShowMessage` so the user gets feedback.

### 25. Stacked `.catch` handlers swallow errors
- **Where:** `GenerateAdViewModel.kt` (multiple `.catch { }` blocks across the upload + AI pipeline) and `PreviewAdViewModel.kt:64–66, 86–90` (which intentionally keeps the stream alive).
- **What:** When the second `.catch` re-raises or the first transforms, error provenance is lost and only `printStackTrace()` is left.
- **Fix:** Reduce to one `.catch` per pipeline; route every error to a single `showError(message)` helper that posts `ShowMessage` and updates state consistently.

### 26. Hardcoded English error strings
- **Where:** `OlxApiError.userMessage` defaults in `OlxModels.kt:74–108`; ad-hoc fallback strings in `SellerAuthViewModel.kt`, `PreviewAdViewModel.kt`, `GenerateAdViewModel.kt`.
- **What:** All error copy is English while the rest of the seller UI is Ukrainian-leaning (UAH, UA-locale separators).
- **Fix:** Move user-facing strings into Compose resources and document the language policy in AGENTS.md.

### 27. Float → Int price truncation
- **Where:** `PostAdvertRequestMapper.map` — converts a `Float` price to `Int` UAH.
- **What:** A price of `1500.7` becomes `1500` silently. No rounding, no validation.
- **Fix:** Use `.roundToInt()` with explicit rounding rules; document that fractional UAH is intentionally dropped.
