# Plan — My Ads account pager

One swipeable page of adverts per connected OLX account on the My Ads screen, instead of a single
list for the active account.

## Prerequisite

This plan builds on the SIR-83 multi-account layer — `OlxAccountStore`, `OlxAccountRecord`,
`SellerAccountRepository.accountsRecordFlow` / `setActiveAccount` / `runKeepAliveRefresh`, the
account-store-backed bearer provider in `createOlxAuthorizedHttpClient`, and the Profile accounts
list. That code lives on `Sirelon/sir-87-share-sheet-entry-point` (commit `b560d743`) and is not
on `main`. Every file path and line reference below is against that branch. On `main`,
`SellerAccountRepository` is still single-account with one global token, so the pager has nothing
to page over — land SIR-83 first.

## Goal

- One tab/page per account connected for the current OLX country (cap is 3 —
  `SellerAccountRepository.MAX_ACCOUNTS_PER_COUNTRY`).
- Each page loads, pages, errors and retries independently.
- Viewing another account's page does **not** change the app-wide active account (that stays a
  Profile/Publish action). Publishing, drafts and the rest of the app are untouched.
- With 1 connected account the screen looks exactly as it does today: no tab row, no pager chrome.

## The constraint that shapes everything

`MyAdvertsRepository.loadAdverts` calls the shared authorized `OlxApiClient`
(`olxAuthorizedHttpClientQualifier`). That client's bearer token is resolved per request from
whichever account is active for the current country — `OlxHttpClientFactory.kt:60-89`,
`loadTokens { activeAccount(accountStore, countryStore) }` — and its reactive 401 refresh writes
back to that same account. There is no way to address a *specific* account through it.

The existing precedent for a per-account call is
`OlxApiClient.getAuthenticatedUser(accessToken)` (`OlxApiClient.kt:60-67`), invoked on the
**unauthenticated** client (`olxUnauthenticatedApiClientQualifier`) with an explicit bearer token.
The unauthenticated client has no Auth plugin, so refresh-on-401 has to be done by the caller.
`SellerAccountRepository.runKeepAliveRefresh` already contains that per-account refresh recipe:
`refreshOlxTokens(unauthenticatedHttpClient, absolute per-country token URL, country creds,
account.tokens.refreshToken)` → `accountStore.updateTokens(...)`, and `markNeedsReconnect` on
`InvalidGrant`/`InvalidToken`.

Base URL is safe: `defaultRequest { url(OlxConfig.apiBaseUrl) }` re-evaluates per request against
the current country, and every account in the pager belongs to the current country.

**Decision:** every page — including the active account's — goes through the explicit-token path.
One code path, and the screen stops depending on `switchEpoch` entirely, which removes the
stale-result guards the ViewModel carries today.

## Step 1 — Per-account token access

`SellerAccountRepository` (`features/seller/profile/data/SellerAccountRepository.kt`)

```kotlin
/** Usable access token for one stored account, refreshing + persisting first when [forceRefresh]
 * or when the cached token is within [TOKEN_REFRESH_SKEW_SECONDS] of expiry. Returns null if the
 * account is gone or NeedsReconnect. Terminal refresh failure marks only that account. */
internal suspend fun accessTokenFor(localIndex: Int, forceRefresh: Boolean = false): String?
```

- Reads the record from `accountStore.recordFlow.value`; returns `null` for
  `OlxAccountState.NeedsReconnect`.
- Refresh path: extract the body of `runKeepAliveRefresh`'s per-account loop into a private
  `refreshAccountTokens(account): OlxTokens?` and call it from both places — that sweep and this
  method must not drift apart.
- Refresh uses the account's own country (`OlxCountry.fromCode(account.countryCode)`) for the token
  endpoint and credentials, and `accountStore.updateTokens(..., updateLastUsed = false)` — reading
  a list is not seller activity on that account.
- `OlxAccountStore`'s mutex already serialises this against a concurrent keep-alive sweep; do not
  add a second lock.

## Step 2 — Explicit-token adverts fetch

`OlxApiClient` — overload next to the existing pair, with the same KDoc convention:

```kotlin
suspend fun getCurrentUserAdverts(accessToken: String, offset: Int, limit: Int): List<OlxAdvert>
```

Body is the current `getCurrentUserAdverts` plus `bearerAuth(accessToken)`.

`MyAdvertsRepository` — inject `SellerAccountRepository` and the unauthenticated `OlxApiClient`
(`get(olxUnauthenticatedApiClientQualifier)` — needs an explicit constructor binding in
`MyAdvertsModule`, `factoryOf(::MyAdvertsRepository)` cannot resolve a qualifier):

```kotlin
suspend fun loadAdverts(localIndex: Int, offset: Int, limit: Int): List<MyAdvertItem>
```

- `accessTokenFor(localIndex)` → fetch. On `OlxApiException` with `InvalidToken`, retry **once**
  with `accessTokenFor(localIndex, forceRefresh = true)`; any second failure propagates. This
  reproduces the bearer plugin's single reactive refresh rather than inventing new retry
  behaviour — and note the OLX login-attempt limit: one retry, never a loop.
- `null` token → throw a dedicated `AccountNeedsReconnect` marker the ViewModel maps to the
  per-page reconnect state, so it is not rendered as a generic load error.
- Keep the old no-arg `loadAdverts` deleted, not deprecated — My Ads is its only caller.

## Step 3 — State model

`MyAdvertsContract.State` becomes a list of pages plus a selection. Today's flat fields
(`adverts`, `isLoading`, `canLoadMore`, `errorMessage`, `requiresOlxConnection`, `accountName`)
move to a per-page holder:

```kotlin
@Immutable
data class AccountPage(
    val localIndex: Int,
    val accountName: String?,      // null/blank falls back to my_ads_account_fallback_name
    val avatarUrl: String?,
    val isActiveAccount: Boolean,  // drives the "Active" badge on the tab
    val needsReconnect: Boolean,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val adverts: List<MyAdvertItem> = emptyList(),
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val hasLoaded: Boolean = false, // gate for lazy first load on page selection
)

data class State(
    val pages: List<AccountPage> = emptyList(),
    val selectedLocalIndex: Int? = null,
    val requiresOlxConnection: Boolean = false, // no accounts at all
)
```

Events gain the page they act on: `RefreshClicked(localIndex)`, `LoadMoreClicked(localIndex)`,
`PageSelected(localIndex)`, `ReconnectClicked(localIndex)`. `AdvertClicked` is unchanged.
`ReconnectClicked` posts a new `Effect.Reconnect(localIndex)` — reuse Profile's existing reconnect
route (`ProfileEvent.ReconnectClicked` → `LaunchOlxAuthFlow(forceReauth = true)`); do not build a
second auth entry point.

**ViewModel** (`MyAdvertsViewModel`)

- Replace the `switchEpoch` collector with an `accountsRecordFlow` collector that maps
  current-country accounts to `pages`, sorted by `localIndex` so tab order is stable across
  switches. Reuse the filter/sort/`toUiModel` shape from `ProfileViewModel.kt:51-68`.
- The `user` collector goes away: page names come from each account's cached
  `OlxProfileSnapshot`, not from the active user.
- Selection: initialise `selectedLocalIndex` to the active account. A later external switch (from
  Profile) updates `isActiveAccount` badges only — it must not yank the seller's current page.
- Lazy loading: `PageSelected` loads that page only when `!hasLoaded`. Load the initially selected
  page on init. Never prefetch all 3 — 3× `adverts` on every screen entry for lists the seller may
  not open.
- Every `setState` mutates one page by `localIndex`, never by list position; a disconnect
  mid-flight can shrink the list, and a result for a vanished page is dropped.
- Reconnected account: `accountsRecordFlow` re-emits with `state = Usable`; reset that page to
  `hasLoaded = false` so selecting it fetches again.

## Step 4 — UI

`MyAdvertsScreen.kt`

- `pages.size <= 1` → today's layout with the single page's state. No tab row.
- `pages.size >= 2` → `PrimaryTabRow` + `HorizontalPager` (`pageCount = pages.size`), tab label =
  account name, "Active" badge on the active account's tab, warning tint + reconnect affordance on
  a `needsReconnect` tab. Follow the `HorizontalPager` usage in
  `designsystem/pager/ImagesCarousel.kt` and `OnboardingScreen.kt:105`; check `designsystem/` for
  an existing tab component before writing one.
- Bidirectional sync: `LaunchedEffect(pagerState.settledPage)` → `PageSelected`, and a
  `LaunchedEffect(selectedLocalIndex)` → `animateScrollToPage` for programmatic moves. Key on
  `settledPage`, not `currentPage`, or every swipe frame fires a load.
- Each page keeps its own `LazyColumn` (own scroll position) with the existing header /
  empty / error / load-more blocks, driven by that page's `AccountPage`.
- `LoadingOverlay` currently wraps the whole screen on `state.isLoading`. With a pager it must not
  block the tab row — move the spinner inside the page body.
- `AdvertCard`, `StatusChip`, `AdvertThumbnail`, `DateLine` are unchanged.

## Step 5 — Strings

New keys in `composeApp/src/commonMain/composeResources/values/strings.xml`: tab "Active" badge,
per-page reconnect title/action, and a load-error variant naming the account. Existing
`my_ads_header_subtitle_account`, `my_ads_empty_description_account` and
`my_ads_account_fallback_name` already take an account name and carry over as-is. Run the
`localize` agent for all 8 locales afterwards; for Ukrainian copy, avoid broadcast/TV vocabulary.

## Step 6 — Tests (`composeApp/src/commonTest`)

Extend `MyAdvertsViewModelTest` (its `MockEngine` + `StandardTestDispatcher` harness already wires
a real `OlxAccountStore`, so multiple accounts are cheap to set up):

1. Two accounts → two pages, tab order stable, initially selected page = active account, only that
   page issues a request.
2. Selecting page 2 fetches with page 2's bearer token; assert the `Authorization` header the mock
   engine received, and that page 1's `adverts` are untouched.
3. Page 2 fails → only page 2 shows `errorMessage`; page 1 still renders its list.
4. 401 on a page → exactly one refresh + retry, and the retry carries the refreshed token.
5. Terminal refresh failure (`invalid_grant`) → that account is `NeedsReconnect`, its page shows
   the reconnect state, the other page is unaffected.
6. Active account switched externally (Profile) while page 2 is selected → selection stays on
   page 2, `isActiveAccount` badges move.
7. Selected account disconnected → its page disappears, selection falls back to a surviving page,
   an in-flight result for the removed page is dropped.

Run `./gradlew :composeApp:jvmTest` and `./gradlew :composeApp:compileAndroidMain` before any
commit.

## Out of scope

- Cross-country pages. The pager covers the current country only; the country switcher stays the
  way to reach another country's accounts.
- A "make this account active" action inside the pager.
- A merged all-accounts list.
- Any change to publish, drafts, or the Profile accounts list.

## Risks

- **Doubled token surface.** Two independent refresh paths now write account tokens (bearer plugin
  for the active account, `accessTokenFor` for pages). Both funnel through
  `OlxAccountStore.updateTokens` under its mutex, but a refresh racing between them can burn a
  rotating OLX refresh token. Mitigation: `accessTokenFor` only refreshes on expiry or a 401, never
  speculatively.
- **Rate limit** is 4500 req/IP/5min — 3 accounts × 50-item pages is nowhere near it.
- **`internal` visibility.** `accessTokenFor` returns nothing `internal`-typed, so no visibility
  leak; `MyAdvertsRepository` stays public.
