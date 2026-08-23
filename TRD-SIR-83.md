# TRD — SIR-83: Multiple OLX accounts in one country

**Ticket:** [SIR-83](https://linear.app/sirelon/issue/SIR-83/support-multiple-olx-accounts-in-the-same-country) (blocks SIR-84) ·
**Companion:** [PRD-SIR-83.md](PRD-SIR-83.md) · **Status:** agreed, ready to schedule
**Scope:** `composeApp/src/commonMain/.../features/seller`

This document is written to the decisions in PRD §5 (**D1–D13**) and uses the PRD's story IDs
(**U1–U10**), QA cases (**Q1–Q23**), metrics (**M1–M5**) and guardrails (**G1–G5**). Findings
(**F1–F6**) and required additions (**A1–A8**) below are the IDs the PRD refers back to.

---

## 0. Verdict

The feature is buildable and the data model the PRD asks for is the right one. The refactor is
smaller than it looks: only **three** of the OLX endpoints we call are account-scoped, so most of the
API layer does not need to know accounts exist.

One thing gates the release and it is not the token work. Both platforms launch the OAuth page in a
browser that already holds an OLX session, so "Add account" will hand back the account the seller
already has (F3). Solvable on iOS in one line; on Android only by one of three routes that differ in
security posture rather than in effort. One day of spike before anything else (§1).

| PRD area | Verdict | Why |
|---|---|---|
| Keyed account set + active pointer, `(country, account)` | buildable | Clean fit; also fixes a latent country/token mismatch |
| Switch with no login (U4) | buildable with A1 | Only true beyond 30 days with keep-alive refresh (D1) |
| Silent migration, no re-login (U10) | buildable | One store, one key, one blob, idempotent |
| Per-account failure isolation | buildable | Requires removing the hidden second refresh path (F1) |
| Add a second account (U2) | **gate — F3** | Shared browser cookies. Solvable; the Android route is a product call (§11) |
| Retarget from the publish screen by switching (U6, D2) | buildable, cheap | One notion of "current account", so no draft binding and no per-request account targeting |
| Identity assertion before publish (D6) | free | Reuses the `users/me` call publish already makes |
| Session state from storage, no auto-switch (D7) | buildable | Fixes two existing bugs on the way |
| Server-side revoke on Disconnect | not possible | OLX documents no revocation endpoint (§6) |

---

## 1. Phase 0 — prove this first

### The gate: can a seller add a second account at all?

F3 decides whether this release is worth starting. If a seller cannot get a second account into the
app, none of the storage, switching, publishing or My Ads work has a user. Answer in order, stop at
the first "yes".

**Does the OLX authorize endpoint honour a force-reauthentication parameter?** Try `prompt=login`,
then `max_age=0`, against a live authorize URL while signed in to OLX in the same browser. OLX's
partner API is plain OAuth 2.0 rather than OIDC, so I do not expect either to be honoured, but it is
fifteen minutes and it is the only answer that solves both platforms while keeping the system
browser. Watch for the parameter being *ignored* rather than rejected — ignored is the likely outcome
and it looks like success until you notice you were never asked to log in.

**iOS: does an ephemeral session behave?** `prefersEphemeralWebBrowserSession = true` on
`ASWebAuthenticationSession`, add-account path only. This is the clean fix and I am confident in it;
the spike confirms OLX's login page works cookie-less and that the one-time system consent alert is
acceptable. First connect stays non-ephemeral, so nothing changes for single-account sellers.

**Android: does OLX's login page work inside our own WebView?** The real unknown, worth about half
the spike, and only needed if the two better routes fail. Load the authorize URL in a bare `WebView`
after `CookieManager.removeAllCookies()` and check three things: does OLX serve the login page at all
(many providers sniff the user agent and refuse embedded browsers); does the redirect fire when
intercepted in `shouldOverrideUrlLoading` rather than via the manifest intent filter; and is the
login form usable, given that password managers behave worse in a WebView. See F3 for what I would
and would not accept about this route. There is no WebView in the codebase today —
`AGENTS.md:140` claims one under `androidMain` and is stale.

**Capture one number while there:** the real `expires_in` for the `authorization_code` grant (the FAQ
implies ~1h; our `client_credentials` calls return 86,400). It only affects how often keep-alive
fires, so it folds into P1 rather than gating anything.

**Constraint on the spike itself:** all three questions involve real interactive logins against real
OLX accounts, and repeated failures get accounts suspended (OLX.ro already did). Two accounts (PRD
O7), one attempt each per sitting, and script every repeatable part so the browser step happens once
per question rather than once per iteration.

### Not a concern: two concurrent tokens under one `client_id`

Every SellSnap user in a country already shares one `client_id`. If a new authorization invalidated
existing ones, every signup would log out every existing seller in that country. The app works, so
OLX holds concurrent per-user tokens — proven in production at a scale no test could reproduce.

---

## 2. How it works today

Tokens live in a single JSON blob: `OlxTokenStore` writes `OlxTokens` under key `"tokens"` in a
DataStore Preferences file named `olx_tokens` (`OlxContracts.kt:18`). One blob, so "connect a second
account" means "overwrite the first".

Every authenticated request goes through one Ktor `HttpClient` — a Koin `single` under
`olxAuthorizedHttpClientQualifier`, built by `createOlxAuthorizedHttpClient` with the `Auth`/`bearer`
plugin closed over that one store. One `OlxApiClient` singleton wraps it; six repositories share it.

```
today   repositories×6 → OlxApiClient → HttpClient + Auth(bearer, cached) → OlxTokenStore["tokens"]
target  repositories×6 → OlxApiClient (unchanged) → HttpClient + Auth(bearer, cleared on switch) → OlxAccountStore["accounts"]
```

Two independent refresh paths write that one key today: `OlxAuthRepository.refreshIfNeeded()`
(proactive, 60s safety window) and the plugin's `refreshTokens` (reactive, on 401).

### Only three endpoints care who you are

| Endpoint | Method | Account-scoped? |
|---|---|---|
| `users/me` | `getAuthenticatedUser` | **Yes** — identity, contact name, business flag |
| `GET adverts` | `getCurrentUserAdverts` | **Yes** — My Ads |
| `POST adverts` | `postAdvert` | **Yes** — the publish target |
| `categories` | `loadCategories` | No — country-scoped reference data |
| `categories/suggestion` | `loadCategorySuggestionId` | No |
| `categories/{id}/attributes` | `loadAttributes` | No |
| `currencies` | `loadCurrencies` | No |
| `locations` | `getLocations` | No |

`CategoriesRepository`, `CurrencyRepository` and `LocationRepository` — and the process-wide category
cache — need **no changes**. Same country means the same tree; PRD constraint 7 holds at the code
level, not just on paper. Only `SellerAccountRepository`, `MyAdvertsRepository` and
`PreviewAdViewModel` become account-aware.

### What already exists in our favour

- **An add-account entry point.** `ProfileEvent.LoginClicked` already calls
  `createAuthorizationRequest()` directly, skipping the country picker — exactly what U2 asks for. It
  just overwrites today.
- **A pending-session store with state validation.** `OlxAuthSessionStore` holds one session;
  `validateCallback` already rejects a state mismatch. U2's "one pending authorization" is current
  behaviour.
- **A pre-publish `users/me` call.** `PreviewAdViewModel` already fetches the authenticated user
  before posting, for the contact name. That call becomes the wrong-account guard at zero cost (A5).
- **Analytics user properties.** `Analytics.setUserProperty` exists, so `connected_account_count` is
  a one-liner.

---

## 3. Findings

### F1 — The Ktor bearer plugin caches one token per client, and two refresh paths write one key

Ktor's `Auth`/`bearer` provider caches whatever `loadTokens` returned and reuses it until
`clearToken()` is called. On a shared singleton client, switching the active account without clearing
sends the **previous** account's token — a request that succeeds, returns the wrong user's data, and
looks normal. That is G4 firing silently.

Separately, refresh happens in two places that both write the single key. OLX rotates refresh tokens
(a new one issued daily), so two concurrent refreshes can persist the older value and hand us
`invalid_grant` on the next call. Rare with one account; routine with several. That half is a
correctness bug regardless of which option below we take.

**Three ways to make the client account-aware.** Which one is right depends entirely on whether two
accounts are ever in play at the same instant. Under D2 and D8 they never are.

| Option | Mechanism | Cost | Breaks when… |
|---|---|---|---|
| **A — clear the cache on switch** *(chosen)* | Keep one client and the `Auth` plugin. `client.authProvider<BearerAuthProvider>()?.clearToken()` on every change of active account, so the next request re-runs `loadTokens` | Smallest by a wide margin — a handful of lines and one discipline rule | …any path changes the active account without clearing. Mitigated by routing every such change through one method that clears as its last act, and by A5 as a backstop |
| **B — a client per account** | Cached `Map<localIndex, HttpClient>`, each provider closed over that account's slot. Pass one shared `HttpClientEngine` so there is still a single connection pool | Moderate. Client lifecycle on disconnect; each provider still needs the F5 mutex | …nothing functionally. The right answer if concurrent per-account requests return |
| **C — token per request** | Drop the plugin; attach `Authorization` explicitly per call, retry once on 401 | ~120 lines replacing ~60, plus re-implementing refresh-on-401 correctly | …never, but it buys per-request targeting that nothing in this release uses |

**Consequence — option A.** One client, plugin retained, `clearToken()` at a single choke point, and
the duplicate refresh path removed so the plugin is the only writer. Add-account needs exactly one
call with a token not yet in the store — the `users/me` that identifies the new account before we
persist it — and that is a single explicit `bearerAuth(token)` on the unauthenticated client, not a
reason to redesign anything.

If PRD §16.2 (a per-listing target) or §16.3 (a My Ads filter) is ever picked up, this becomes option
B rather than a rewrite: the store, the mutex, the failure isolation and the choke point all carry
over, and only client construction changes.

One race is common to all three options and is not really about tokens: if My Ads is mid-load for
account A when the seller switches to B, the in-flight response arrives after the switch and renders
under B's header. Invalidate in-flight account-scoped loads on switch — a monotonically increasing
switch epoch compared on delivery is enough (Q14).

### F2 — OLX refresh tokens expire after 30 days unused, so lazy-only refresh guarantees dead accounts

OLX documents this explicitly: a refresh token is valid for one month (2,592,000 s), a new one is
issued daily, and **if a refresh token remains unused for a month it becomes invalid and the user
must fully re-authenticate**.

Refreshing only on use makes the outcome arithmetic: any account unused for 30 days is dead, and
reviving it costs an interactive OLX login. That would break PRD goal 2 and walk into the §6.3
lockout risk the release exists to avoid. The dual-identity seller who lists household items in March
and business stock in December is exactly the target user and exactly who it hits.

**Consequence — A1, adopted as D1.** Bounded keep-alive refresh: on app foreground, refresh each
usable account whose last refresh is older than 20 days. Worst case one request per account per 20
days — three accounts is ~0.15 requests/day against OLX's documented ceiling of 4,500 requests per IP
per 5 minutes. It is a refresh, not a login, so PRD §6.3 permits it explicitly.

It cannot cover a device offline for over a month; nothing can. `account_token_expired_unused` (A6)
measures the residual, and the cap (D4) bounds it: every extra account is an extra 30-day expiry
liability whose remedy is the risky interactive path.

### F3 — "Add account" will re-authorize the account you already have, on both platforms

iOS launches the OAuth page in `ASWebAuthenticationSession` without
`prefersEphemeralWebBrowserSession`, so it shares Safari's cookie jar. Android uses a plain
`CustomTabsIntent`, sharing the browser's. In both cases the seller is already signed in to OLX in
that browser as account #1. "Add OLX account" will often show a consent page with no login form,
return a code for the same user, and land on the already-connected branch. On the evidence of the
code that is the **default path**, and a seller who cannot see why has no way forward — which is
exactly what produces repeated login attempts and suspended accounts. Hence D5.

**iOS is solved in one line.** `prefersEphemeralWebBrowserSession = true` on the add-account path
only: no shared cookies, a guaranteed login form, and first connect keeps the convenience of an
existing Safari session. Cost is a one-time system consent alert and the seller typing credentials,
which adding a second identity requires anyway.

**Android has no equivalent.** Custom Tabs deliberately exposes no incognito mode, and the
Chrome-specific incognito extra was never public API and no longer works. Three routes, in the order
the PRD asks for them (O6):

1. **A force-reauthentication parameter** on the authorize URL. Best outcome — keeps the system
   browser on both platforms, one line. Probably unsupported; §1 finds out in fifteen minutes.
2. **OLX's own logout URL loaded in the same Custom Tab immediately before the authorize URL.**
   Standards-clean, we never see a password. Costs a flash of a logout page and signs the seller out
   of OLX in their own browser.
3. **An in-app `WebView`,** which has an app-scoped cookie jar we can clear with
   `CookieManager.removeAllCookies()`, and lets us intercept the redirect in
   `shouldOverrideUrlLoading` so the callback never leaves the process. On API 34+
   `androidx.webkit`'s multi-profile support gives a genuinely separate profile rather than a cleared
   shared one. No new dependency either way.

Route 3 works, and three things about it should be a conscious decision rather than a discovery:

- **It is against OAuth spec for native apps.** RFC 8252 §8.12 says native apps must not use embedded
  user-agents, precisely because the app can observe the credentials typed into them — this is why
  Google and others refuse WebView sign-in. We would host an OLX password field inside our own
  process, a real change to the app's security posture, sitting next to two open `BUGS.md` items (the
  `client_secret` in the binary, the hijackable `selolxai://` scheme). It also makes PRD §12's
  privacy and terms review mandatory rather than a formality.
- **OLX may simply refuse.** Providers commonly sniff the user agent and block embedded browsers. If
  OLX does, we find out in the §1 spike rather than after building the screen.
- **Autofill degrades.** Password managers work less reliably in a WebView, so more sellers type
  passwords by hand — and hand-typed passwords are where failed attempts, and therefore lockouts,
  come from. An uncomfortable interaction with the one risk this feature is built around.

If it comes down to route 3, scope it strictly to add-account and leave first connect and reconnect on
Custom Tabs, so the spec violation covers the narrowest possible path.

**Regardless of which route wins, the floor:** when the callback resolves to an already-connected OLX
user id, say so precisely and say what to do next. Never a generic failure, never an automatic retry
(U2, Q3, Q4).

Duplicate detection also constrains sequencing: the OLX user id is only known **after** the code
exchange, so the flow is exchange → `users/me` with the new token → dedupe → persist. The token
provider must therefore serve a token not yet in the store.

### F4 — Two current behaviours read "one account failed" as "the user is logged out"

This describes a bug in *today's* code, not a proposal to block anyone.

`AppNavigationViewModel.sessionDestination()` calls `users/me` at startup and on **any** failure
routes to `AppDestination.SellerLanding` — the guest landing screen. Ported unchanged to three
accounts, a dead active account would throw the seller out of an app where the other two are
perfectly usable. Likewise `ProfileContract.ProfileState.isGuest` is derived as `user == null`, so a
failed profile fetch already renders a connected seller as a guest today; multi-account makes that
misread routine, because "needs reconnect" becomes a normal state rather than an anomaly.

**Consequence — D7.** Startup routing and Profile read the account set, never a network call: any
account present → `Seller`; no accounts and not guest → `SellerLanding`. Session mode becomes explicit
state on `ProfileState`. Guest mode unchanged (Q9).

And when the active account is the dead one, **we do not auto-switch to a working account.** It stays
active, marked `Needs reconnect`, with a notice naming it and a Reconnect action; the others stay one
tap away in Profile. Silently changing which identity the app acts as, at launch, without the seller
asking, is the same class of surprise as publishing to the wrong account.

### F5 — The account store introduces a read-modify-write the current code never had

`OlxTokenStore.write` replaces the whole value; nothing to merge. An account list is different:
refreshing account B means read blob → replace B → write blob. Two concurrent refreshes on different
accounts clobber each other, and with token rotation a clobbered entry is **dead**, not stale.

**Consequence:** every mutation goes through a single `Mutex` inside `OlxAccountStore`, with an
in-memory `StateFlow` of the record as the read path.

### F6 — The publish button keeps plain copy, so the identity assertion carries G4

The publish action reads "Publish on OLX", not "Publish to \<account>": the shipped strings
(`publish_on_olx`, `publish_confirm_yes` = "Yep, publish") already say so, and D3 settles it. That
resolves the store-screenshot exposure for the button and the confirmation sheet.

It has one consequence that needs owning. Naming the destination only on the preview row and the
success screen leaves the **identity assertion (A5) as G4's primary defence**, not a recommended
extra. That is why A5 is required rather than optional, and why it is also the backstop for option A's
one discipline rule.

**The preview row does show the account** (D3), which keeps a visual mitigation but exposes the
Maestro store-screenshot flows: they capture that screen across four device classes and eight locales
with `screenshotMode = true`, signed in with the real credentials from `.maestro/.env`. Every uploaded
App Store / Play Store image would otherwise carry that account's real profile name and email. Under
`screenshotMode`, render a fixed placeholder identity, add it to the `app-store-screenshots`
preflight, and re-shoot the preview screen for all devices and locales (A7, Q22). `screenshotMode`
already bypasses the confirmation sheet, so only the preview row is exposed.

### Also worth knowing, not blocking

- **Tokens are stored in plaintext.** `createKeyValueStore` is DataStore Preferences on a plain file
  path on both platforms — no Keystore, no Keychain. PRD §12 states this honestly and defers
  hardening to §16.5.
- **`BUGS.md` #1 and #2 scale with this feature.** The `client_secret` ships in the binary and the
  redirect uses a hijackable custom scheme (`selolxai://`). Neither is introduced here, but the number
  of credentials behind them goes from one to three per device.
- **OLX tokens are becoming JWTs** of up to 4,096 characters. Trivial for a preferences file, but
  nothing may assume a ~40-character token.
- **The callback bridge has two collectors.** `OlxAuthCallbackBridge` is a global replay-1
  `SharedFlow` collected by both `OlxCountryPickerScreen` and `ProfileScreen`. Different destinations
  today, but if both are ever composed at once the same callback completes twice.
- **We never send a phone number.** `PostAdvertRequestMapper` sets
  `contact = AdvertContactRequest(name = contactName, phone = null)` — the listing phone comes from
  the OLX account server-side. Hence D12.
- **`AGENTS.md:140` is stale:** it claims an OLX WebView exists under `androidMain`. It does not.

---

## 4. Target design

### Data model

```kotlin
// features/seller/auth/data/OlxAccountRecords.kt — internal, data layer
@Serializable
internal data class OlxAccountRecord(
    val localIndex: Int,                 // stable, never reused; the only analytics handle
    val countryCode: String,             // "pl" — half of the key; SIR-84 needs no re-key
    val olxUserId: Long?,                // null only for the migrated account, until first users/me
    val tokens: OlxTokens,
    val profile: OlxProfileSnapshot?,    // name, email, avatar, isBusiness — for offline render
    val lastUsedAtEpochSeconds: Long,
    val lastRefreshedAtEpochSeconds: Long,
    val state: OlxAccountState,          // Usable | NeedsReconnect
)

@Serializable
internal data class OlxAccountsRecord(
    val schemaVersion: Int = 1,
    val accounts: List<OlxAccountRecord> = emptyList(),
    val activeByCountry: Map<String, Int> = emptyMap(),  // countryCode -> localIndex
    val nextLocalIndex: Int = 1,
)
```

Three non-obvious choices:

- `activeByCountry` rather than a single active pointer. Costs nothing now, keeps one active account
  per country as required, and is the reason SIR-84 will not need a second migration. It also closes a
  latent bug: today a token from country A survives a country change and gets sent to country B.
- `olxUserId` is nullable because we never stored it. The migrated account gets it on its first
  successful `users/me`. Duplicate detection and the publish assertion must tolerate the null once.
- `localIndex` is assigned from `nextLocalIndex` and never reused, so analytics indices stay
  comparable after a disconnect. A positional index would silently re-map.

### Storage

`OlxAccountStore` over `createKeyValueStore("olx_accounts")`, one JSON blob under key `"accounts"`,
all mutations serialized through an internal `Mutex` (F5), exposing `StateFlow<OlxAccountsRecord>` as
the read path. `OlxTokenStore` is retained only to be read once by the migration, then deleted (D11).

### Token resolution — option A

The `Auth`/`bearer` plugin stays. `loadTokens` resolves the active account for the current country out
of the store instead of reading a single blob, and every change of active account clears the
provider's cache so the next request re-reads. `OlxApiClient` keeps all eight current signatures
unchanged.

The whole mechanism is one choke point. Nothing else in the app writes the active pointer:

```kotlin
// SellerAccountRepository — the only writer of the active-account pointer.
suspend fun setActiveAccount(localIndex: Int) {
    accountStore.setActive(country = countryStore.current.code, localIndex = localIndex)
    // Without this the plugin keeps serving the previous account's cached
    // access token and the next request silently acts as the wrong seller.
    authorizedClient.authProvider<BearerAuthProvider>()?.clearToken()
    switchEpoch.increment()      // in-flight account-scoped loads compare and discard
}
```

Switch from Profile, retarget from the publish screen (U6), add-account-becomes-active,
disconnect-the-active-account and a successful reconnect all route through this one function. That is
the entire discipline rule, and it is worth a test asserting the token cache is empty afterwards
rather than trusting review to catch a sixth call site later.

Refresh keeps a single writer. `OlxAuthRepository.refreshIfNeeded()`'s proactive path is removed and
the plugin's `refreshTokens` becomes the only one, writing through the store's mutex (F5) and marking
just the affected account `NeedsReconnect` on `invalid_grant` or `invalid_token` — replacing
`handleTerminalRefreshFailure`, which today clears everything.

Two things sit outside the plugin, and neither needs a handle threaded through the API:

- **Identifying a freshly authorized account.** Exchange the code, then call `users/me` with an
  explicit `bearerAuth(newAccessToken)` on the unauthenticated client. The token is deliberately not
  in the store yet — we do not know whether it is a new account or a duplicate until this returns
  (F3).
- **Keep-alive refresh (A1).** A token-endpoint call for a possibly non-active account, so it never
  touches the authorized client at all.

### Migration

Runs in `AppNavigationViewModel.init`, right after `olxCountryStore.loadFromStorage()` and before
`resolveStartupDestination()` — the only point where the country is known and nothing has issued a
request yet.

```kotlin
suspend fun migrateIfNeeded() {
    if (accountStore.readRaw() != null) return              // idempotent: new store wins
    val legacy = runCatching { legacyTokenStore.read() }.getOrNull()
    val record = if (legacy == null) {
        OlxAccountsRecord()                                 // guest or never connected
    } else {
        OlxAccountsRecord(
            accounts = listOf(legacy.asAccount(localIndex = 1, country = countryStore.current.code)),
            activeByCountry = mapOf(countryStore.current.code to 1),
            nextLocalIndex = 2,
        )
    }
    accountStore.write(record)
    legacyTokenStore.clear()                                // D11
}
```

Idempotent by construction: the new store existing is the "already migrated" flag, so a half-finished
run re-runs safely. An unreadable legacy blob yields an empty record set, which lands the seller in
the existing disconnected state with a connect action — never a crash, never a silent no-op.

### Publish targeting

```kotlin
// PreviewAdViewModel.publishAdvert() — the users/me call it already makes,
// now checked against the account we told the seller we are acting as.
val target = accountRepository.activeAccount()
val user = olxApiClient.getAuthenticatedUser()
if (target.olxUserId != null && user.id != target.olxUserId) {
    // A5: the token the client is holding does not belong to the account named
    // on the preview row. A missed clearToken(), a mid-flight switch, a bad
    // migration — all land here. Abort before POST; with D3's copy this is G4's
    // main defence. Emits publish_account_mismatch_aborted.
    return abortPublishWrongAccount(target)
}
val contactName = user.name   // name only — we never send a phone (D12)
```

Zero cost — the app already makes that exact call before every publish. It converts the app's most
embarrassing possible failure from silent to impossible, and it is the backstop for exactly the
discipline rule that a future call site could break.

Because the draft always targets the active account (D2), there is nothing to persist in
`PreviewAdSavedState` and no process-death retargeting hazard. Reopening PRD §16.2 changes that: a
listing-scoped target must be serialized into `SavedStateHandle` as `targetAccountLocalIndex: Int?`,
or process death silently retargets an open draft to whatever is active on restore.

### Authorization attempt state

The cooldown and the consecutive-failure counter (U3) must survive a force-quit or the safety rule is
one app restart from being bypassed (Q7). Both live in the account store, keyed by `localIndex` for
reconnects and by country code for a first connect or an add, where no account exists yet.

---

## 5. PRD requirement → implementation

| PRD | Requirement | Engineering note |
|---|---|---|
| 6.1 U1 | Accounts list, active marked and first, per-row actions | New section in `ProfileScreen` (757 lines today); `AccountCard` becomes a list item |
| 6.1 U1 | One account → today's layout exactly | Straight `if (accounts.size == 1)`; protects the majority (Q20) |
| 6.1 U1 | `Needs reconnect` row selectable, offers Reconnect in place (D9) | Fewer states than a disabled row; the previous account stays active either way (Q10) |
| 6.2 U2 | Add an account, country step skipped | `ProfileEvent.LoginClicked` already bypasses the picker |
| 6.2 U2 | Forced fresh authentication (D5) | F3. iOS ephemeral session; Android per §11 O6. The gate on the whole release |
| 6.2 U2 | New account becomes active, profile fetched on success | Required anyway — the fetch is how we learn the OLX user id |
| 6.2 U2 | Already-connected account → one entry, refreshed, active, named in the message | The common outcome, not an edge case (F3). Copy must be actionable |
| 6.2 U2 | Cap of 3 per country (D4) | One `canAddAccount(): Boolean`, which is also the entitlement seam |
| 6.2 U2 | One pending authorization | `OlxAuthSessionStore` + `validateCallback`, unchanged |
| 6.3 U3 | No automatic authorization retry | Nothing retries today; add the regression test |
| 6.3 U3 | 60s cooldown + failure counter, persisted | An in-memory timer is bypassed by force-quit (§4) |
| 6.3 U3 | Second failure → OLX recovery | Needs the per-account failure counter |
| 6.4 U4 | Switch with no network call, never a browser | Pointer write plus `clearToken()`; the profile snapshot is cached per account so nothing needs fetching |
| 6.4 U4 | Keep-alive refresh (D1) | A1. Foreground pass, >20 days since last refresh, usable accounts only |
| 6.4 U4 | Terminal refresh failure isolated, no auto-switch | Replaces `handleTerminalRefreshFailure`, which clears the whole store (F4) |
| 6.5 U5 | Disconnect, local only, with the OLX settings link (D10) | A8. Copy must not imply server-side revocation |
| 6.5 U5 | Terminology fixed as "Disconnect" | Current string is `profile_logout` = "Log out"; renaming across 8 locales is real work |
| 6.5 U5 | Last account = today's logout; delete-my-data clears everything | Existing paths, widened to the account set |
| 6.6 U6 | "Publish to" row on preview, tap → picker → switches the active account (D2) | The picker calls `setActiveAccount`; the draft needs no binding because there is one source of truth |
| 6.6 U6 | Button and confirmation keep shipped copy (D3) | `publish_on_olx`, `publish_confirm_yes` already agree (F6) |
| 6.6 U6 | Changing the target never invalidates the draft | Verified in code: the category cache is process-wide and country-scoped; currency comes from `OlxCountry` |
| 6.6 U6 | Identity assertion before posting (D6) | A5. Free — reuses the existing pre-publish `users/me` |
| 6.6 U6 | Contact name only (D12) | `PostAdvertRequestMapper` already sends `phone = null` |
| 6.6 U6 | Dead token at publish → inline Reconnect | Today this is a generic `error_publish_failed`; needs a distinct branch |
| 6.6 U6 | `screenshotMode` placeholder identity | A7. Re-shoot the preview screen, 4 device classes × 8 locales (Q22) |
| 6.7 U8 | My Ads header + empty state name the account; no filter (D8) | Cheap, and the anti-panic fix |
| 6.7 U8 | In-flight loads discarded on switch | Switch epoch compared on delivery (F1, Q14) |
| 6.8 U9 | First run, country picker, guest unchanged | `GuestModeStore` untouched |
| 6.8 U9 | Session state from storage, not from a fetch (D7) | F4. Fixes two existing bugs (Q9) |
| 7 U10 | Silent migration, idempotent, legacy key deleted (D11) | §4. Highest-value test in the release (Q1) |
| 11 | New events + `connected_account_count` | `logEvent(name, params)` and `setUserProperty` both exist; use `localIndex` only |
| 11 | Entitlement seam | The same `canAddAccount()` as the cap |
| 12 | At-rest protection unchanged | Plaintext DataStore either way; hardening deferred to PRD §16.5 |

---

## 6. Answered questions

**Does the OLX partner API expose token revocation? No.** The documented OAuth surface is a single
endpoint, `POST /api/open/oauth/token`, with the `authorization_code`, `refresh_token` and
`client_credentials` grants. No revoke, no introspect. So Disconnect forgets tokens locally and
nothing more, and the still-valid refresh token ages out up to 30 days later. Hence D10: the
confirmation copy must not imply we withdraw access on OLX's side, and the honest completion of the
flow is a secondary link to OLX's own connected-applications settings (A8). Worth confirming against
the live API during §1 in case an undocumented endpoint exists.

**Keep the old storage key for one release? No** — accept "reads as disconnected" and delete the old
key at migration (D11). Keeping it buys almost nothing: OLX rotates refresh tokens daily, so the copy
under `olx_tokens/tokens` goes stale within about a day of the new build refreshing. A downgraded
build would read it, attempt a refresh, get `invalid_grant`, and land disconnected anyway — same
destination, one failed call later. Meanwhile it leaves a dead credential at rest in plaintext
indefinitely. No crash either way: an old build reading an absent key resolves to "not connected", a
state it already handles. Dual-writing both formats would preserve downgrade properly but means two
sources of truth for a rotating credential, which is how you lose tokens.

**Why three accounts (D4), and why not more?** Not for request volume — bounding refresh traffic
against the shared `client_id` does not hold up: the documented limit is 4,500 requests per IP per five minutes, and
keep-alive costs about one request per account per 20 days. The real cost is that every connected
account is an independent 30-day expiry clock whose only remedy is an interactive OLX login — the
action this feature exists to minimise. Five accounts means up to five forced logins after a quiet
month. Three is materially smaller for a use case that two accounts already covers, and A6 provides
the evidence for raising it later.

**Demand evidence (PRD O1) and per-category defaults (O5).** Product questions. The release does not
depend on O1 — the token work in §4 is worth doing regardless, because it removes the double refresh
path and the country/token mismatch. On O5, the §4 model supports a default per category without
change (it is a lookup, not a schema), so it need not be decided now.

---

## 7. Deferred scope, and what deferring it saves

The PRD defers three things (D2, D8, D9). Each is a real saving, not just a smaller backlog.

**A per-listing publish target, independent of the active account** (PRD §16.2). Retargeting by
switching the active account gives the same seller-visible outcome — destination named before the
irreversible tap, changeable in place, nothing reset, because same country means the same tree,
attributes and currency.

| Aspect | Per-listing target | Switch-in-place (D2) |
|---|---|---|
| Retarget from the publish screen | Yes | Yes |
| Destination visible before the irreversible tap | Yes | Yes |
| Draft binding + persistence across process death + acknowledgement flow | Required | Not needed |
| Two sources of truth for "which account" | Yes | No |
| Two accounts in play at the same instant | Yes → F1 option B | No → F1 option A |
| Side effect on global state | None | Changes the active account |

The cost is real: a per-listing decision changes global state. For a seller switching identity to publish this listing, "and now you are working as that
account" reads as expected rather than surprising — and M4 measures whether they want the
finer-grained version.

**A per-account My Ads filter** (PRD §16.3). The header and empty state naming the account are what
stop an empty list reading as "my listings vanished", and they cost almost nothing. The filter is a
second switching affordance for something Profile does one tap away, and it introduces the "viewing B
while active is A" state every other screen then has to reason about — the other thing that would
push F1 to option B.

**Blocking selection of a `Needs reconnect` account** (D9). Letting the selection through and
surfacing "Reconnect \<account>" in place is fewer states, fewer strings, and a more obvious path than
a disabled row the seller cannot act on. The behaviour that matters — the previous account stays
active until a reconnect succeeds — is unchanged.

**Not deferred, deliberately:** the 60-second cooldown and the two-failure escalation stay in full and
get persisted. Cheapest insurance in the release against the one failure we cannot undo from inside
the app.

---

## 8. Required additions

The PRD's decisions rest on these; the IDs are what §5 and PRD §5 refer to.

| # | Addition | Status | Note |
|---|---|---|---|
| A1 | Keep-alive token refresh | **required** (D1) | On foreground, refresh usable accounts whose last refresh is older than 20 days. Without it, PRD goal 2 and U4 are false after a month (F2) |
| A2 | One refresh path, single-flight per account | **required** | Delete `refreshIfNeeded()`'s proactive path so the plugin is the only writer, serialized through the store's mutex (F1, F5). Independent of the rest; worth landing even if the UI slips |
| A3 | Force a fresh OLX login for add-account | **required** (D5) | Ephemeral session on iOS; on Android the first route in F3 that §1 proves works; plus the precise already-connected message as the floor |
| A4 | Invalidate in-flight account-scoped loads on switch | **required** | Switch epoch compared on delivery, so a list never renders under the wrong header (F1, Q14) |
| A5 | Assert account identity before every publish | **required** (D6) | Compare `users/me` id against the active account's stored `olxUserId`, abort on mismatch. Reuses an existing request, so it is free. With D3's copy it is G4's primary defence and the backstop for option A's discipline rule |
| A6 | `account_token_expired_unused` event | cheap | With `days_since_last_use`, fired on `invalid_grant`. Measures exactly what A1 prevents; the evidence for moving the cap |
| A7 | Store-screenshot placeholder identity | release gate | Fixed placeholder under `screenshotMode`; re-shoot the preview screen (F6, Q22) |
| A8 | Link to OLX's connected-applications settings on Disconnect | cheap | Since we cannot revoke (§6), the only way a seller can actually withdraw access (D10) |

---

## 9. Test plan

The existing suite is `commonTest` with a fake key-value store (`InMemoryOlxKeyValueStore`) and a mock
Ktor engine. Everything below fits that shape. PRD §13 owns the manual QA matrix (Q1–Q23); these are
its code-level equivalents.

**Store and migration**

- Migration from a legacy blob produces one active account keyed to the stored country with
  `localIndex = 1`; the legacy key is gone afterwards (Q1).
- Idempotent: running twice leaves one account; running with three existing accounts changes nothing.
- An unreadable or malformed legacy blob yields an empty record set, not an exception.
- `localIndex` is never reused: add, add, disconnect the first, add → the third gets index 3.
- Concurrent mutations serialize: two parallel refreshes on different accounts both persist (F5).

**Token resolution**

- A valid token resolves with no network call (assert request counts on the mock engine, as
  `OlxAuthRepositoryTest` already does).
- **Every path that changes the active account leaves the bearer cache empty** — switch from Profile,
  retarget from the publish screen, add-account, disconnect-the-active-account and reconnect, each
  asserted separately. This is the one discipline rule option A rests on, and a sixth call site added
  next year should fail a test, not pass review.
- After a switch, the next request carries the new account's token. Assert on the outgoing
  `Authorization` header via the mock engine, not on the response.
- An expired token triggers exactly one refresh under N concurrent callers (single-flight).
- `invalid_grant` on account B marks only B as needs-reconnect; A's tokens are byte-identical
  afterwards — the code-level form of Q8.
- Keep-alive refreshes an account 21 days stale, skips one 19 days stale, skips a needs-reconnect
  account entirely (Q15).
- A failed authorization results in exactly one token-endpoint request — the test that protects
  sellers' OLX accounts (Q5).
- The persisted cooldown is still in force after the store is re-read from scratch (Q7).

**Session state**

- With one account whose token is dead, startup resolves to the seller destination, not the landing
  screen, and the account is marked `NeedsReconnect` (Q9).
- A failed profile fetch does not produce a guest session state.

**Publish targeting**

- An id mismatch aborts before any `POST adverts` is issued — assert on the absence of the request,
  and on `publish_account_mismatch_aborted` being logged.
- A null `olxUserId` (migrated account, first publish) does not abort, and the id is recorded
  afterwards.
- The contact name in the mapped request comes from the target account, and `phone` is null.
- An in-flight My Ads load delivered after a switch is discarded (A4).

**Not a unit test.** Whether OLX's 30-day window behaves as documented can only be established by
leaving a real token untouched for a month — a calendar item to start at Phase 0, not a test to run at
the end. Q15 tests our staleness logic, not OLX's.

---

## 10. Work breakdown

Engineer-days for one developer across both platforms. Ranges because F3's Android answer is unknown.

| Phase | Work | Size | Shippable alone? |
|---|---|---|---|
| P0 | Add-account spike (§1): force-reauth parameter, iOS ephemeral session, Android WebView viability. Capture the real `expires_in` while there | 1 | Gate — if no Android route is acceptable, the release has no usable add flow |
| P1 | Account store, records, migration, active-pointer choke point with `clearToken()`, single refresh path, keep-alive (A1, A2). No UI | 3–4 | **Yes** — identical behaviour for one account, and it fixes the double refresh path |
| P2 | Per-account failure isolation; startup + Profile session-state fixes (F4); publish assertion (A5); in-flight load invalidation (A4) | 1.5–2 | Yes — still single-account externally |
| P3 | Add-account: whatever P0 picked, per platform. Duplicate detection and copy, persisted cooldown and failure counter (A3). The Android WebView route is the top of this range | 2–5 | No — needs P4 to be reachable |
| P4 | Profile accounts UI: list, active marking, add, switch, disconnect, reconnect, needs-reconnect, cap. Single-account layout preserved | 3–4 | Yes — minimum viable feature with P1–P3 |
| P5 | Publish: the "Publish to" row and its account picker, `screenshotMode` placeholder (A7), success screen names the account, dead-token reconnect branch | 1.5–2 | Yes |
| P6 | My Ads header and empty state naming the account | 0.5 | Yes |
| P7 | Analytics events, `connected_account_count`, entitlement seam, A6 | 1 | Yes |
| P8 | Strings: ~26 new keys plus the logout→disconnect rename, 8 locales. Placeholders via `stringResource(id, arg)` — `String.format` is banned | 1–2 | With its owner |
| P9 | Tests per §9, re-shoot store screenshots, privacy/terms page review (release-gating per PRD §12) | 2–3 | Release gate |

**Roughly 17–25 days.** The range is driven almost entirely by P3 — the Android add-account route —
rather than by the token layer, which is small once option A replaces a custom plugin. Reopening PRD
§16.2 or §16.3 adds 3–4 days and turns F1 into option B.

P1+P2 ship to beta ahead of the UI (D13): together they touch every authenticated request while being
externally identical for a single-account seller, and with no remote feature flag the only rollback is
a new build.

---

## 11. Open items

Everything else is settled in PRD §5. These three are open, and only the first blocks work.

1. **Android add-account mechanism** (PRD O6, F3, P0). iOS is a one-line ephemeral session and I have
   no concerns. Android has three routes and they are not equivalent: a force-reauth parameter if one
   exists (best, keeps the system browser, I doubt it exists); a logout redirect in the Custom Tab
   (standards-clean, we never see a password, but it signs the seller out of OLX in their own
   browser); or a WebView, which does work but means hosting an OLX password field in our process
   against RFC 8252 §8.12, with degraded autofill and the possibility that OLX refuses to serve the
   page at all. *Recommend taking them in that order and, if it comes down to the WebView, scoping it
   strictly to add-account.* The WebView needs an explicit yes: it is a security-posture call, not a
   technical one.
2. **Two real OLX accounts on one country, and the login budget** (PRD O7). P0 and Q2–Q4, Q11, Q14 all
   need them. Proposal: one attempt per account per sitting, every repeatable part scripted. Without
   two accounts this feature cannot be verified.
3. **Demand evidence** (PRD O1) and **per-category defaults** (PRD O5). Neither blocks the build; see
   §6.

---

## Sources and confidence

**Grounded in code:** `features/seller/auth/`, `features/seller/profile/`,
`features/seller/ad/preview_ad/`, `features/seller/my_ads/`, `startup/AppNavigationViewModel.kt`,
`datastore/`, `AGENTS.md`, `BUGS.md`.

**Grounded in external docs:** the OLX partner API token endpoint and grants, refresh-token lifetime
(2,592,000 s) and daily rotation, the 30-day unused-token invalidation, the
4,500-requests-per-IP-per-5-minutes rate limit, and the JWT token-length migration. Ktor 3.5.2
bearer-auth caching, `cacheTokens` and `clearToken` verified against the Ktor client documentation.
RFC 8252 §8.12 on embedded user-agents.

**Not verified, flagged as such:** whether OLX serves its login page inside an Android WebView,
whether the authorize URL honours any force-reauthentication parameter, the `authorization_code`
grant's real `expires_in`, and any undocumented revocation endpoint. All four are P0 (§1).
