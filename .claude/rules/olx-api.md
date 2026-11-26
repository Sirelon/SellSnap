---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/seller/auth/**"
  - "composeApp/src/commonMain/kotlin/**/features/seller/**/data/**"
  - "composeApp/src/commonTest/kotlin/**/features/seller/**"
  - "androidApp/src/main/kotlin/**/MainActivity.kt"
  - "composeApp/src/wasmJsMain/**"
---

# OLX partner API

Every market has its own portal — `developer.olx.pl`, `.ua`, `.pt`, `.ro`, `.bg`, `.kz` — and
they are not equally complete. These six are the only markets with partner-API access
(`developer.olx.kz/docs/faq`, country table, 2026-11-26). olx.uz runs the same API, but
`developer.olx.uz` has no portal, so an app cannot be registered there. olx.com.br has its own
separate API (`developers.olx.com.br`, SIR-204). olx.ba gives API access only to its PIK shops. PL omits the advert-status definitions and lists 4 of the 11
`Advert.status` values; UA and PT define all 11. Reading one portal and assuming it speaks for
the rest is how this app shipped two wrong status meanings.

- **Treat `developer.olx.ua` as the source of truth.** Ukraine is the primary market, and its
  portal is one of the complete ones.
- **Before relying on any behaviour, check whether the markets differ.** Read UA plus at least
  one other portal for the same endpoint and compare. Where a difference exists, it belongs in
  code as a per-country capability — `OlxCountry.supportsExtendCommand` is the pattern — never as
  a single global assumption. A feature that works in Poland may be refused in Romania.
- Known market differences: the `extend` command, which the specs annotate as unavailable in UA
  and PT; and the response language. OLX answers in the market's default language unless the
  request sends `Accept-Language` (swagger v2, "Language"). `GET /languages` (2026-11-26): kz
  offers `ru` (default) and `kk`, ua offers `uk` (default) and `ru`, the other markets one
  language each. `OlxCountry.apiLanguage` sends `kk` for kz, so category names, attributes and
  values come in Kazakh, never russian. Two data points, not a guarantee that the rest is uniform.
- **Read the yaml itself.** It is at `https://developer.olx.<tld>/swagger/v2/partner_api.yaml`,
  nested `$ref` files resolve against the same path, and plain `curl` gets a CloudFront 403.
  `curl https://r.jina.ai/<url>` returns the whole file - about 145 KB - and from there the schema
  is `grep -n` and `sed -n` like any other file. When r.jina.ai refuses the yaml (422), the
  Wayback raw copy works: `curl -sL "https://web.archive.org/web/2026id_/https://developer.olx.ua/swagger/v2/partner_api.yaml"`. WebFetch and context7 answer in prose: they
  summarize, and the summary is where `location`'s real position, the absence of `ad_delivery`
  from the update schema, and the `value`/`values` split all went missing. Each was five lines of
  yaml and each cost a release.
- A request body this app assembles is pinned by a test against the spec's own example payload -
  `AdvertUpdateBodyTest` is the pattern. A body whose only check is installing on a phone is a
  guess with a delay attached.
- Cite the portal and section in the KDoc next to the code that relies on the fact, and mark
  anything you could not confirm `Inferred:` (rule 3). Do not invent a restriction the API does
  not state (rule 4).
- **Every partner endpoint needs a token, the catalog included.** `categories`, `categories/suggestion`,
  `categories/{id}/attributes` and `currencies` answer 401 with no token (all six markets,
  2026-11-25), so a guest never calls them. `CategoriesRepository` reads with the seller's token
  and falls back to the app's own `client_credentials` token (`createOlxAppHttpClient`) when the
  market has no usable account. That token lives 900 s (86400 s in kz) and has no refresh token.
- For live probes without a user account — token minting, routing checks — use
  `.claude/skills/olx-api-verify`. Never attempt an OLX login.

## Auth callback wiring

- **OLX accepts only HTTPS redirect URIs** (`developer.olx.<tld>/docs/faq`, "How do I set or
  change the redirect URI"). With a custom scheme the login silently never returns: the seller
  ends on the OLX account page, and our analytics logs it as `user_cancelled`. The old
  `partner_api.yaml` does not state this rule; the newer `/docs` pages do.
- Redirect URI: `https://sirelon.github.io/SellSnap/olx-callback/` (`docs/olx-callback/`,
  configurable via the `OLX_REDIRECT_URI` BuildKonfig key). The page forwards `code` and `state`
  to `selolxai://olx-auth/callback` (`OlxConfig.appCallbackScheme`), which the app catches. It
  must be registered under "Your apps" on every market's developer portal; a market without it
  rejects the login.
- **On phones the login runs in an in-app web view, because OLX rejects a login that is too
  fresh (SIR-195, olx.ro, 2026-11-22).** A phone is always sent from `www.olx.<tld>` to
  `m.olx.<tld>` (no URL or path avoids it), signs in on `auth.olx.<tld>`, and OLX's page jumps
  straight back to `/oauth/authorize`. Within about a second of the login that request checks
  `login.olx.<tld>` silently, gets `login_required`, logs the seller out and shows the OLX home
  page. Five seconds later the same request redirects to `redirect_uri` with a code. A Custom
  Tab or `ASWebAuthenticationSession` cannot hold that jump, so `OlxExternalAuthLauncher.android.kt`
  and `.ios.kt` own a WebView/WKWebView: they delay OLX's jump by 5 s, retry up to twice if OLX
  still lands on its home page, and catch the HTTPS callback directly. Desktop browsers do not
  hit this. The browser login is kept beside it, behind the Firestore switch
  `config/olx_login.systemBrowser` (`OlxLoginModeRepository`, default `false`): turn it on once
  `.maestro/olx_login_canary.yaml`, run each release, passes again.
- **Per market, in-app login on Android (2026-11-22):** ro connects. pt reaches OLX's
  redirect check and stops at "The redirect URI provided is missing or does not match" until the
  HTTPS URI is on the pt portal. bg: `/oauth/authorize` checks `login.olx.bg` silently, and that
  page itself says the login moved to the home page, so the partner login cannot finish there; an
  OLX fix, not an app fix. pl still serves OLX's older single-page login and was not verified. ua:
  not tested (separate credentials). kz (2026-11-24, SIR-197): `/oauth/authorize` sends the
  phone to the older single-page form on `www.olx.kz/account/` (ids `username`, `password`,
  `Login`). Maestro typing into it got OLX's anti-bot page "Доступ временно ограничен" right
  after submit, before any password check, so a KZ login is checked by a person typing. To trace a login, make the WebView debuggable and attach to its DevTools socket
  (`adb shell cat /proc/net/unix | grep webview_devtools_remote`, then `adb forward`).
- Android — `MainActivity.publishOlxCallback(intent)` reads `intent.data` and forwards the URL to `OlxAuthCallbackBridge.publishCallback(url)`. The intent filter must match the configured scheme; verify `androidApp/.../AndroidManifest.xml` whenever the scheme changes.
- Web (Wasm) — `composeApp/src/wasmJsMain/.../main.kt` checks `window.location` for `code=` / `error=` query parameters before mounting Compose, then calls `OlxAuthCallbackBridge.publishCallback`.
- The bridge is a global object (`OlxAuthCallbackBridge`); listeners are collected from both the seller landing and seller profile flows.
