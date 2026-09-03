# Ad lifecycle milestone — implementation record

Linear milestone `Ad lifecycle` (project SellSnap). Nine tickets, SIR-98 … SIR-106.

## Dependency order

```
SIR-98  (API client)  ─┬─> SIR-101 (row actions) ──> SIR-106 (analytics)
                       ├─> SIR-102 (mark as sold)
                       └─> SIR-103 (statistics)
SIR-99  (spike GET->PUT) ──> SIR-104 (edit)
SIR-100 (spike auto_extend) ──> SIR-105 (expiry + renewal)
```

## Verified API facts (official OpenAPI spec, developer.olx.pl/swagger/v2)

| Path | Method | Scope | Notes |
| -- | -- | -- | -- |
| `adverts/{id}` | GET | v2 read | `{ "data": Advert }` |
| `adverts/{id}` | PUT | v2 write | full create payload, no patch; returns `{ "data": Advert }` |
| `adverts/{id}` | DELETE | v2 write | 204; 400 when advert is not inactive |
| `adverts/{id}/commands` | POST | v2 write | `{command, is_success?}`; 204 no body |
| `adverts/{id}/statistics` | GET | v2 read | `{advert_views, phone_views, users_observing}` — **unwrapped** in the spec example |
| `adverts/{id}/statistics/{name}` | DELETE | v2 write | reset; unused |
| `adverts/{id}/moderation-reason` | GET | v2 read | reason for a moderated/blocked advert |

`command` enum: `activate | deactivate | finish | extend`. `is_success` required for `deactivate`.

Documented failure payloads (both use `validation[].title`, no `detail`):
- delete of an active advert -> `field: "ad"`, `title: "Invalid status"`
- deactivate of a non-active advert -> `field: "ad"`, `title: "Ad has to be active"`

`OlxRemoteErrorParser` already turns those into `OlxApiError.ValidationError("ad", "<title>")`.

The `Advert` GET response is field-for-field symmetric with the PUT request body:
`attributes[] {code, value, values}`, `location {city_id, district_id, latitude, longitude}`,
`images[] {url}`, `price {value, currency, negotiable, trade, budget}`, `contact {name, phone}`,
`category_id`, `advertiser_type`, `auto_extend_enabled`. See `SPIKE-SIR-99-advert-edit-round-trip.md`.

## Architecture decisions

1. **Per-account explicit tokens.** Every lifecycle call goes through
   `SellerAccountRepository.accessTokenFor(localIndex)` on the *unauthenticated* client, exactly
   like `MyAdvertsRepository` (SIR-87) — the shared authorized client only ever serves whichever
   account is globally active, and My Ads shows all of them. The token + single-reactive-refresh
   recipe is extracted to `my_ads/data/AccountScopedCall.kt` and shared by both repositories.

2. **Card tap opens an actions sheet, not the browser.** `AdvertActionsSheet` is the single
   surface for the whole milestone: status explanation, expiry, statistics, lifecycle actions,
   edit, and "Open on OLX" (which is what the tap used to do). Reason: statistics, edit and
   expiry all need somewhere to live and "no new screens" is a ticket constraint. There is no
   ellipsis icon in the 48-icon set, so a separate row affordance would need a new asset.

3. **Feature-local `ModalBottomSheet`, not a nav key.** The sheet needs the tapped
   `MyAdvertItem` plus per-advert async state (statistics, pending action). Routing it through
   `AppKey` would mean either serializing the item into the key or wiring
   `SharedViewModelStoreNavEntryDecorator` onto the `MyAdverts` tab entry to share the
   ViewModel with sheet entries. Deviation from the `BottomSheetSceneStrategy` convention used
   by the PreviewAd flow, taken deliberately for AGENTS.md Rule 2/3.

4. **One outcome store covers SIR-102 and the SIR-90 slice it needs.**
   `my_ads/data/AdvertOutcomeStore` keeps one record per advert id: publish-time
   (`suggestedPrice`, `minPrice`, `maxPrice`, `publishedPrice`, `currency`, `publishedAt`) written
   by `PreviewAdViewModel` on publish success, and close-time (`isSold`, `achievedPrice`,
   `closedAt`) written by the mark-as-sold sheet. Both halves are needed for SIR-106's
   `price_delta_percent` / `days_live`. Cleared by `deleteSellSnapAccountData()`.

5. **Delete of an active advert is deactivate-then-delete, surfaced honestly.** The seller
   answers `is_success` on the way through, and a failed second half reports "deactivated but not
   deleted" rather than a generic error.

6. **Edit is text + price only in this pass.** The spec says the GET response round-trips
   field-for-field, but whether OLX accepts its own CDN image URLs back on PUT is unverified
   against a live advert. Title / description / price are re-sent from seller input; every other
   field is echoed back verbatim from the GET payload, so nothing the seller did not touch can be
   lost. Attribute and photo editing stays out until the live check in
   `SPIKE-SIR-99-advert-edit-round-trip.md` is done.

7. **No publish-time `auto_extend_enabled` toggle.** Unverifiable without publishing a real
   advert on olx.ua. A toggle that silently does nothing is worse than no toggle. `extend` is
   offered only in markets that document it (not UA, not PT) via a new `OlxCountry` capability
   flag; expiry is surfaced in every market.

## Files

New:
- `features/seller/auth/data/response/OlxAdvertDetailResponse.kt`
- `features/seller/auth/data/response/OlxAdvertStatisticsResponse.kt`
- `features/seller/auth/domain/OlxAdvertDetail.kt`
- `features/seller/my_ads/data/AccountScopedCall.kt`
- `features/seller/my_ads/data/AdvertLifecycleRepository.kt`
- `features/seller/my_ads/data/AdvertOutcomeStore.kt`
- `features/seller/my_ads/domain/AdvertAction.kt`
- `features/seller/my_ads/domain/AdvertExpiry.kt`
- `features/seller/my_ads/ui/AdvertActionsSheet.kt`
- `features/seller/my_ads/ui/MarkAsSoldSheet.kt`
- `features/seller/my_ads/ui/AdvertConfirmSheet.kt`
- `features/seller/my_ads/ui/AdvertEditSheet.kt`

Touched:
- `features/seller/auth/data/OlxApiClient.kt`, `OlxAdvertModels.kt`
- `features/seller/auth/domain/OlxCountry.kt` (extend capability)
- `features/seller/my_ads/{data,di,model,presentation,ui}/**`
- `features/seller/ad/preview_ad/PreviewAdViewModel.kt` (publish-time outcome record)
- `features/seller/profile/data/SellerAccountRepository.kt` (clear outcomes on data delete)
- `analytics/AnalyticsEvents.kt`
- `composeResources/values*/strings.xml`

## Status

All nine tickets implemented in this worktree. `:androidApp:assembleDebug`,
`:composeApp:compileKotlinIosSimulatorArm64` and `:composeApp:jvmTest` (130 tests, 0 failures,
45 of them new) all pass, the last over three consecutive `--rerun-tasks` runs.

| Ticket | What shipped |
| -- | -- |
| SIR-98 | `getAdvert`, `putAdvert`, `sendAdvertCommand`, `deleteAdvert`, `getAdvertStatistics` on `OlxApiClient`; `AdvertLifecycleRepository`; typed response classes; 11 API tests |
| SIR-99 | Written verdict in `SPIKE-SIR-99-advert-edit-round-trip.md`; six residual risks need a real advert |
| SIR-100 | Written per-market answer in `SPIKE-SIR-100-auto-extend.md`; outcome (b) |
| SIR-101 | `availableActions` status mapping, `AdvertActionsSheet`, confirmations, two-step delete, per-action pending state, server row refresh |
| SIR-102 | `MarkAsSoldSheet` (two steps, skippable price), `AdvertOutcomeStore`, plus the SIR-90 slice it needs — publish-time AI price range written by `PreviewAdViewModel` |
| SIR-103 | Statistics fetched when the sheet opens, three counters plus a one-line diagnosis, "too early to tell" empty state |
| SIR-104 | Text + price edit via raw-JSON echo of the GET payload |
| SIR-105 | Remaining validity on every row and in the sheet, expiring-soon emphasis, resource plurals, `extend` gated per market |
| SIR-106 | `advert_action`, `advert_sold`, `advert_closed_unsold`, `advert_statistics_viewed`, `advert_edited`, all bucketed |

Deliberately not shipped, with reasons, in `.claude/tmp/ad-lifecycle-followups.md`: photo and
attribute editing, edit on inactive adverts, a publish-time renewal toggle, and per-row
statistics.

## Review pass

An independent adversarial review of the diff found nine confirmed defects, all fixed:

1. An edit load for one advert could seed - and then save onto - a different advert's form.
   `openEdit`'s handlers now drop a result whose advert is no longer the one being edited.
2. A command that landed but whose follow-up `GET adverts/{id}` failed was reported as a failure,
   sending the seller to retry into OLX's "Ad has to be active". The row read-back is now
   best-effort and never turns a landed command into an error.
3. `pendingAction` lived only on the sheet, so dismissing and reopening it re-enabled every
   button mid-command. In-flight actions are now tracked per advert id outside the sheet state.
4. Clearing the price field counted as a price-only edit that changed nothing. An empty field now
   means "leave the price alone", in both the ViewModel and the change summary.
5. `ad_delivery.delivery_change_allowed` is response-only and nested, so the top-level
   `AdvertResponseOnlyKeys` filter could not reach it and it was echoed into every PUT.
6. `advert_sold` was suppressed forever after any earlier close, so a listing closed unsold,
   reactivated, then genuinely sold never logged the milestone's headline metric. Reactivating
   now clears the outcome.
7. A dead account produced "try again in a moment", a loop that cannot succeed. It now names
   reconnecting, through `advert_action_needs_reconnect` rather than the publish flow's wording.
8. `putAdvert` could leak a raw `SerializationException` on an accepted edit. It no longer parses
   the response at all - the row is re-read regardless.
9. A delete closed whatever sheet was open rather than only the deleted advert's.

Also fixed: a latent race in two effect-assertion tests (compose-resources `getString` hops off
the test dispatcher, so `advanceUntilIdle()` cannot see the effect; and `withTimeout` inside
`runTest` fires on virtual time). Six regression tests were added for the defects above.

Findings deliberately not acted on, with reasons, are in `.claude/tmp/ad-lifecycle-followups.md`.
