# BUGS

Known pitfalls, suspect decisions, and convention violations in the seller / OLX code path. Each item is verified against current source and includes a file reference, what's wrong, and a suggested fix. Use this as a backlog seed — link tickets back here when fixing.

Scope: seller / OLX flow only. Agile screens are intentionally excluded.

## Security / privacy

### 1. OAuth `client_secret` shipped in client binary
- **Where:** `OlxConfig` resolves `clientSecret` via BuildKonfig (`AppConfig.olxClientSecret`); used in `OlxAuthRepository.exchangeAuthorizationCode` and `OlxHttpClientFactory.refreshOlxBearerTokens` (the `RefreshTokenRequest` body).
- **What:** A public OAuth client (mobile app) cannot keep a secret. The value is embedded in the compiled binary and is trivially extractable from the APK with tools like `apktool` or `strings`.
- **Risk:** Anyone who extracts it can impersonate the app in token-exchange requests.
- **Fix:** Move token exchange to the `:server` module — the client sends the authorization code to your backend, which holds the secret and calls OLX. Alternatively, migrate to PKCE (`code_challenge` / `code_verifier`) which removes the need for a secret entirely on the client side.

### 2. Custom URI scheme is hijackable
- **Where:** `OlxConfig.redirectUri` defaults to `selolxai://olx-auth/callback`; registered as an intent filter in the Android manifest.
- **What:** Any other Android app can declare the same custom scheme. On devices running Android < 12, the OS presents a disambiguation dialog; on some OEMs the first-registered app wins silently. Either way, a malicious app can intercept the OAuth callback and steal the authorization code.
- **Risk:** Authorization code interception → account takeover.
- **Fix:** Migrate to [Android App Links](https://developer.android.com/training/app-links) (`https://` scheme + `/.well-known/assetlinks.json` on a domain you control). Pair with PKCE (item #1) so a stolen code is useless without the verifier.

## Auth / token handling

### 3. Privacy / Terms pages must stay in sync with data flows
- **Where:** `SellerAuthViewModel.kt` companion object; `OlxConfig.kt` — `scope` and `redirectUri`.
- **What:** The GitHub Pages policy URLs are hardcoded. There is no automated gate that flags when app behaviour diverges from what those pages say.
- **Risk:** App Store / Play Store review rejection; potential legal exposure.
- **Mitigated:** `OlxConfig.scope` and `SellerAuthViewModel` URL constants now carry `@see` doc comments listing the policy pages. Any diff touching those symbols will surface the reminder in review.
- **Remaining gap:** New SDK integrations (Supabase tables, analytics events, new permissions) added outside `OlxConfig` won't trigger the comments. Treat this as a PR checklist item for any change that touches data collection.

## Convention violations vs AGENTS.md

### 4. `OlxTokens` straddles domain and persistence
- **Where:** `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/domain/OlxModels.kt:7–21`
  ```kotlin
  @Serializable
  data class OlxTokens(
      @SerialName("access_token") val accessToken: String,
      @SerialName("refresh_token") val refreshToken: String? = null,
      @SerialName("expires_in") val expiresInSeconds: Long,
      @SerialName("token_type") val tokenType: String,
      @SerialName("scope") val scope: String,
      @SerialName("issued_at_epoch_seconds") val issuedAtEpochSeconds: Long,
  )
  ```
- **What:** `OlxTokens` is a domain value object but carries `@Serializable`/`@SerialName` because `OlxTokenStore` serializes it directly to disk (`OlxContracts.kt:25,28`). Renaming any field is a silent breaking migration — existing installs lose their stored tokens on upgrade.
- **Fix:** Introduce `OlxTokensRecord` (internal, in `auth/data/`) that owns the `@SerialName` annotations and maps to/from a plain domain `OlxTokens`. The domain class becomes a simple `data class` with no serialization annotations.
