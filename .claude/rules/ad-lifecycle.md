---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/seller/my_ads/**"
  - "composeApp/src/commonTest/kotlin/**/features/seller/my_ads/**"
---

# Ad lifecycle (post-publish actions)

- Every advert action resolves its OWN access token via `SellerAccountRepository.accessTokenFor`
  on the *unauthenticated* `OlxApiClient` (`olxUnauthenticatedApiClientQualifier`), through
  `my_ads/data/AccountScopedCall.kt`. The shared authorized client only ever serves whichever
  account is globally active, while My Ads shows every connected account at once — using it here
  would act on the wrong OLX account. `withAccountToken` wraps exactly ONE request, never a
  multi-call sequence, because its single reactive refresh retry would otherwise replay a command
  that already landed.
- `AdvertAction.availableActions(status, supportsExtendCommand)` is the single source of truth for
  which actions are offered. An action OLX would reject must never be rendered; `AdvertActionTest`
  iterates every `AdvertStatus` so a newly added status cannot silently gain one.
- **`AdvertState` is the seller-facing model, and the single input to badges, colours,
  explanations and `availableActions`.** OLX reports eleven statuses; the app shows seven states.
  The wire-status-to-state table, with OLX's own definition per status and a one-line reason for
  each grouping, is the KDoc on `AdvertStatus.state`. Both mappings are `when` expressions with no
  `else`, so a new wire value stops compiling until someone places it, and `AdvertActionTest` pins
  the whole table.
- `Unknown` is the one place to stay conservative: an unrecognised status could be an active
  advert, and deleting an active advert is the single case OLX documents as refused.
- Deactivate is `Active`-only for a product reason as well as OLX's rule — OLX requires an answer
  to "did it sell?" first, and that question is nonsense for a listing buyers never saw. Delete is
  the equivalent for those.
- **`DELETE` is refused for more statuses than `active`, and the set is not documented** — the docs
  say "a non-deletable status (e.g. `active`)", and a listing under moderation was refused on a
  real account. So no status is withheld from Delete on a guess: `AdvertLifecycleRepository.delete`
  answers a refusal on `field: ad` with the documented removal path, deactivate then delete, and
  passes every other failure through untouched. Match on the field, never the title — the titles
  come back in the market's own language.
- Everything inferred is watched: `advert_action` logs `result: rejected` with `from_status`, so a
  cell OLX actually refuses shows up as a pattern rather than staying a guess.
- For a reviewed or rejected listing the sheet prefers OLX's own text from
  `GET adverts/{id}/moderation-reason` and falls back to the app's copy only when OLX returns
  nothing. A 404 there is the ordinary answer, not an error.
- `NeedsPayment` covers `limited` and `unpaid`: money is the blocker and it is paid on OLX. The
  docs say to purchase a packet then send `activate`, but `POST adverts/{id}/packets` spends the
  seller's OLX balance, so no button in this app triggers it.
- Lifecycle analytics carry buckets and enums only — `AdvertAnalyticsBuckets`. No absolute prices,
  no advert ids, no titles. `AdvertOutcomeStore` holds the raw figures on-device and is cleared by
  `SellerAccountRepository.deleteSellSnapAccountData`.
- Only `OlxApiError.ValidationError.fieldDetail` may be shown to a seller — that is OLX's own
  response text. Every other `OlxApiError.userMessage` is an English developer diagnostic; the
  presentation layer must substitute a localized string.
- **OLX is eventually consistent after a command.** `POST adverts/{id}/commands` answers 204 with
  no body, and both `GET adverts/{id}` and `GET adverts` may report the pre-command status for a
  while afterwards. Never read back to confirm a command. `MyAdvertsViewModel` records the status
  a landed command implies, refetches, and lets that expectation override the server only while
  the server still reports the status it acted from; the moment OLX reports anything else, the
  server wins. Four fixes went into learning this.
