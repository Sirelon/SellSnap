# SIR-99 Spike: GET → PUT round-trip fidelity for advert edit

**Question.** `PUT /adverts/{id}` takes the full create payload (no patch semantics). Can we
reconstruct that payload from `GET /adverts/{id}` well enough that editing one field (e.g. price)
never silently wipes another (e.g. trade/budget flags, phone, attributes)?

**Sources.** Official OpenAPI spec, `https://developer.olx.pl/swagger/v2/partner_api.yaml`
(fetched via WebFetch — plain curl 403s on CloudFront). Our request DTO,
`composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/auth/data/OlxAdvertModels.kt`
(`PostAdvertRequest` + nested types). Our create-time mapper,
`composeApp/src/commonMain/kotlin/com/sirelon/aicalories/features/seller/ad/data/PostAdvertRequestMapper.kt`.
Live probes against `olx.ua` and `olx.pl` on 2026-09-03 (see §3).

As of this spike, SellSnap has **no code path that calls `GET/PUT/DELETE /adverts/{id}`** —
`OlxApiClient.kt` only implements `GET /adverts` (list), `POST /adverts` (create), `users/me`,
`categories`, `currencies`, `locations`. Everything below about the single-advert endpoints is
spec-only except where §3 says otherwise.

## 1. Field-by-field: `PostAdvertRequest` vs. `GET /adverts/{id}` response

| Our field (Kotlin) | Wire key | GET response | Verdict | Notes |
|---|---|---|---|---|
| `title` | `title` (required) | `title` (required) | **Round-trips** | direct passthrough |
| `description` | `description` (required) | `description` (required) | **Round-trips** | |
| `categoryId` | `category_id` (required, Int) | `category_id` (required, number) | **Round-trips** | |
| `advertiserType` | `advertiser_type` (required) | `advertiser_type` (required) | **Round-trips** | `"private"` / `"business"` |
| `contact.name` | `contact.name` (required) | `contact.name` (required) | **Round-trips** | |
| `contact.phone` | `contact.phone` (nullable; request requires only `name`) | `contact.phone` (established: **both fields required** in the response object) | **Needs reconstruction** | `PostAdvertRequestMapper.kt:33` hardcodes `phone = null`, and the client's `explicitNulls = false` (`OlxHttpClientFactory.kt:112`) means `phone` is **omitted from the wire entirely** on every create today. Omitting is spec-legal on write (only `name` required), but if a real advert has a phone number attached and an edit-PUT keeps omitting it, that's a live check to make (§2). At minimum, edit must feed the GET response's `contact.phone` back in rather than perpetuating `null`. |
| `location.cityId` | `location.city_id` (required) | `location.city_id` (required) | **Round-trips** | |
| `location.districtId` | `location.district_id` (nullable) | `location.district_id` (optional) | **Round-trips** | |
| *(not in our class)* | *(not sent)* | `location.latitude`, `location.longitude` (optional) | **Not sent / not reconstructible** | `AdvertLocationRequest` has no lat/long fields at all — this is a DTO gap, not just a mapper gap. If OLX ever stores a precise pin distinct from the city/district centroid, an edit can't preserve it without adding fields to the request class first. Low blast radius today since our create flow never sets a custom pin either — editing doesn't regress anything we currently support, it just can't fix/preserve precision if OLX independently derives one. |
| `images[].url` | `images[].url` (optional array) | `images[].url` (optional array) | **Structurally symmetric, live-untested** | Same shape both directions. Whether OLX's own CDN URL is *accepted back* on PUT is unverified — see §2(a), the single highest-value unknown. |
| `price.value` | `price.value` (Int) | `price.value` (number) | **Round-trips** | |
| `price.currency` | `price.currency` (String) | `price.currency` (String) | **Round-trips** | |
| `price.negotiable` | `price.negotiable` (Boolean) | `price.negotiable` (Boolean) | **Round-trips** | |
| *(not in our class)* | *(not sent)* | `price.trade` (optional Boolean) | **Needs reconstruction** | `AdvertPriceRequest` has no `trade` field. See the `auto_extend_enabled` contrast below — the spec calls out *only* `auto_extend_enabled` as safe-to-omit-on-PUT, implying other optional fields reset to default when the full payload omits them. If a seller ever sets "willing to trade" (via the OLX web UI, not our app) before opening our edit sheet, a PUT built from our narrower DTO would silently turn it off. |
| *(not in our class)* | *(not sent)* | `price.budget` (optional Boolean) | **Needs reconstruction** | Same mechanism as `trade`. |
| `attributes[].code` | `attributes[].code` (required) | `attributes[].code` (required) | **Round-trips** | |
| `attributes[].values` | `attributes[].values` (List\<String\>) — **we always send the array form**, even for single-select attributes (`PostAdvertRequestMapper.kt:40-46` wraps a single code in a 1-element list) | `attributes[].value` (scalar string, "required if attribute requires single value") **or** `attributes[].values` (array, "required if attribute allows multiple values") | **Needs verification** | See §2(b). Our request DTO (`AdvertAttributeRequest`) has **no `value` scalar field at all** — structurally it cannot send the shape the spec documents for single-select. That this already works at create time today is decent evidence OLX's validator accepts array-wrapped single values, but PUT could run different/stricter validation, and if the GET response for a single-select attribute comes back as `value: "123"` (scalar, no `values` key), our mapper needs to know to read `value` too, not just `values`. |
| *(not in our class)* | *(not sent)* | `external_id` (optional) | **Not applicable today** | We never set it at create, so it's null/absent on GET too — nothing to lose by continuing to omit it, unless OLX or another partner integration populates it out-of-band. |
| *(not in our class)* | *(not sent)* | `external_url` (optional) | Same as `external_id` | |
| *(not in our class)* | *(not sent)* | `salary` (optional object: `value_from`, `value_to`, `currency`, `negotiable`, `type`) | **Not applicable to current scope** | Job-category-only field; SellSnap's supported categories should be confirmed as non-job before assuming this is moot. |
| *(not in our class)* | *(not sent)* | `courier` (optional Boolean — "Available in BG, KZ, PT, RO") | **Needs reconstruction if OLX auto-populates** | Unverified whether OLX derives a default independent of our create payload (e.g. per-category default). If so, an edit-PUT that omits it could flip delivery availability off. |
| *(not in our class)* | *(not sent)* | `ad_delivery` (optional object, `delivery_package_ids[]`) | **Needs reconstruction if OLX auto-populates** | Same open question as `courier`. |
| *(not in our class)* | *(not sent)* | `auto_extend_enabled` (optional Boolean) | **Safe to omit on PUT** | The spec explicitly documents asymmetric omission behavior: **PUT** — "Omitting this field means that auto extend is not changed." **POST** — "Omitting this field means that auto extend is disabled." This is the *only* optional field the spec calls out as non-destructive-by-omission on PUT, which is itself indirect evidence that every other omitted optional field above (`trade`, `budget`, `courier`, `ad_delivery`, `product_safety_regulation`) does **not** get this treatment and resets to default. (Related: SIR-100 spike covers `auto_extend_enabled` at create time specifically.) |
| *(not in our class)* | *(not sent)* | `product_safety_regulation` (optional object) | **Needs reconstruction if OLX auto-populates** | Possible EU GPSR-mandated field auto-set by category; unverified. |
| — | — | `id`, `status`, `url`, `created_at`, `activated_at`, `valid_to` | **Response-only** | Already established; not accepted in the request body at all. |
| — | — | `delivery_change_allowed` (Boolean) | **Response-only** | New finding from this spec fetch — not in the original response-only list, and absent from both the POST and PUT request schemas, so it's read-only metadata about whether `ad_delivery` can currently be edited. |

**Summary of DTO narrowness vs. the spec** (as the ticket flagged going in, now confirmed against
the live class):
- `attributes[]`: we send `values[]` only, never scalar `value` — §2(b).
- `contact.phone`: we hardcode `null` (omitted) at create — needs to become GET-echoed on edit.
- We never send `auto_extend_enabled`, `courier`, `trade`, `budget`, `ad_delivery`,
  `product_safety_regulation`, `external_id`, `external_url`, `salary`, or `location`
  lat/long — confirmed by reading `PostAdvertRequest`/`AdvertPriceRequest`/`AdvertLocationRequest`
  directly; none of these fields exist on the Kotlin side at all, so "reconstruction" for these
  means adding fields to the DTO, not just changing the mapper.

## 2. Residual risks the spec cannot settle

| # | Risk | Live check that settles it | Cost if wrong |
|---|---|---|---|
| (a) | Does OLX accept its **own CDN image URL** (from the GET response's `images[].url`) fed back into `images[].url` on a PUT? Or does it require a partner-hosted / freshly-uploaded URL each time? | `GET` a real advert with photos, PUT it back unchanged except `title`, inspect whether `images` round-trips or the response comes back empty/errored. Needs a **user OAuth token** — not possible with `client_credentials` (no user context to own an advert). | If rejected: any edit that doesn't re-upload every photo **silently drops all images** the moment the seller changes just the price or title. This is the single highest-value unknown in the whole spike — it decides whether "edit anything" or "edit text/price only" is safe. |
| (b) | Is `attributes[].value` (scalar) or `attributes[].values` (array) actually returned for single-select attributes, and does PUT accept our array-wrapped-single-value shape (`AdvertAttributeRequest` has no scalar `value` field)? | `GET` a real advert with a single-select attribute set (e.g. brand, condition) and inspect whether the JSON key is `value` or `values`; then PUT it back through our exact DTO shape and confirm no validation error. Needs a **user token** to read a real advert with attributes. | If the response uses scalar `value` and our mapper only reads `values`, editing silently drops every single-select attribute (condition, brand, etc.) the first time the seller edits anything else. If PUT's validator is stricter than POST's and rejects array-wrapped single values, **every edit that includes attributes fails outright** — safer failure mode than (a), but still blocks the feature. |
| (c) | Is PUT accepted on an **inactive/expired advert**, or only on `active`? "Fix it and republish" (an advert that expired or was rejected) is the most valuable edit case in the milestone. | PUT a real advert while manually driving it through `deactivate`/expiry via the `commands` endpoint first, then attempt the edit PUT and record the status code. Needs a **user token** and a disposable test advert. | If PUT 400s on non-active adverts, the edit feature is only useful for adverts already live — the "something got rejected, let me fix and resubmit" flow (arguably the main reason to build edit at all) doesn't work, and SIR-104 needs a status-gated UI instead of a blanket "Edit" action. |
| (d) | Does PUT **re-run create-time validation** such that an edit can be rejected for reasons the original advert passed under (e.g. category attribute requirements changed, price floor changed, category itself deprecated)? | PUT an unchanged payload (or a single trivial field change) on a real, currently-valid advert and confirm 200. Then try PUT with a payload that's missing a since-added-required attribute and confirm the failure shape matches the documented `validation[].title` format already handled by `OlxRemoteErrorParser`. Needs a **user token**. | If yes and unhandled: a seller editing their price gets a cryptic validation failure for a field they never touched and didn't set — the error message on screen won't map to any field the edit UI actually exposes. Determines whether SIR-104 needs to surface `OlxApiError.ValidationError` messages generically rather than assuming errors map 1:1 to editable fields. |
| (e) | Does a PUT send the advert **back to moderation** (status flips to `moderated`/`unconfirmed`, ad disappears from search while pending)? | PUT a small change to a real `active` advert and poll `status` immediately after; compare `activated_at` before/after. Needs a **user token**. | If yes: every edit — even fixing a typo — takes the ad offline for a moderation window. This changes the UX from "instant edit" to "edit, then wait," and should be disclosed in the edit sheet copy before SIR-104 ships, not discovered by sellers after the fact. |

Two more worth tracking alongside (a)-(e), surfaced by the field table above rather than
originally listed:

- **`contact.phone` omission on edit** — same mechanism as (a)/(b): needs a user token against a
  real advert with a phone number to confirm PUT doesn't require it explicitly even though the
  *response* schema marks it required.
- **Auto-populated optional fields** (`courier`, `ad_delivery`, `product_safety_regulation`) —
  needs a real advert in a category where OLX is known to auto-set these (e.g. a PT/RO/BG/KZ
  category for `courier`) to see whether GET ever returns them non-null despite our create flow
  never sending them.

All six checks above require a **user OAuth access token** scoped to a real advert. None of them
can be completed with `client_credentials`. Per the ticket's constraint, no OAuth login flow was
attempted in this spike.

## 3. Live verification performed (client_credentials, no user token)

Minted a `client_credentials` token per country (scope `v2 read write`) from the hardcoded
credentials in `OlxCountry.kt`, per `.claude/skills/olx-api-verify/SKILL.md`. No credential or
token value is recorded anywhere in this file or elsewhere. Probed a syntactically-plausible but
almost-certainly-nonexistent advert id (`123456789`) against `GET`/`PUT`/`DELETE
adverts/{id}`, a deliberately-bogus sibling path, and the already-implemented `GET adverts` list
endpoint for comparison. Run 2026-09-03.

**olx.ua** (client_id `202504`):

| Call | HTTP | Body |
|---|---|---|
| `GET adverts/123456789` | 404 | `{"error":{"status":404,"title":"Not Found","detail":"Оголошення не знайдено"}}` (*"Advert not found"* — domain-specific) |
| `PUT adverts/123456789` (minimal well-formed body) | 404 | same domain-specific "advert not found" body as GET |
| `DELETE adverts/123456789` | 404 | same domain-specific "advert not found" body |
| `GET advertzzz-bogus/123456789` (bogus sibling, control) | 404 | `{"error":{"type":"NotFoundException","message":"Unsupported API version"}}` — **generic routing-level 404, different shape** |
| `GET adverts?offset=0&limit=1` (list, already implemented) | 400 | `{"error":{"status":400,"title":"Bad Request","detail":"Invalid user ID in token"}}` |

**olx.pl** (client_id `203018`):

| Call | HTTP | Body |
|---|---|---|
| `GET adverts/123456789` | 404 | `{"error":{"status":404,"title":"Not Found","detail":"Nie mogliśmy znaleźć tego ogłoszenia."}}` (*"We could not find this advert"* — domain-specific) |
| `PUT adverts/123456789` (minimal well-formed body) | 401 | `{"error":"insufficient_scope","error_description":"The request requires higher privileges than provided by the token"}` |
| `DELETE adverts/123456789` | 401 | same `insufficient_scope` body |
| `GET advertzzz-bogus/123456789` (bogus sibling, control) | 404 | `{"error":{"type":"NotFoundException","message":"Unsupported API version"}}` — identical generic shape to UA's |
| `GET adverts?offset=0&limit=1` (list, already implemented) | 400 | `{"error":{"status":400,"title":"Bad Request","detail":"Invalid user ID in token"}}` |

**What this confirms:**
- **Routing is live for `GET/PUT/DELETE adverts/{id}` on both countries.** The real path returns a
  domain-specific "advert not found" error distinguishable from the bogus sibling path's generic
  "Unsupported API version" routing-level 404. This is the routing confirmation the ticket asked
  for, in a stronger form than a bare 401/403 — the server evaluated the path as a real
  single-advert route and looked the id up, it didn't just reject the URL shape.
- **`client_credentials` tokens genuinely have no user context**, exactly as expected —
  `GET adverts` (list) consistently 400s with "Invalid user ID in token" on both countries. This
  is the same reason none of the §2 checks are possible without a real OAuth user token.
- **UA and PL gate PUT/DELETE differently**, and this divergence is itself a finding worth
  carrying into SIR-104's country-by-country testing plan: PL's gateway rejects PUT/DELETE with a
  scope-level 401 *before* reaching advert-lookup logic; UA's PUT/DELETE reached the same
  domain-specific 404 as GET, meaning UA's `client_credentials` token (scope `v2 read write`) was
  accepted far enough to run advert-lookup for a write verb. Cannot be explained further without a
  real advert — noted here so a future live check isn't surprised by inconsistent behavior between
  markets.

**Not attempted (needs a user token, explicitly out of scope for this spike):** any PUT/DELETE
against a real, existing advert; anything in §2(a)-(e); any OAuth authorization-code flow. Per the
ticket's instruction, no login flow was run and no auth call was retried after failure — every
call above succeeded on the first attempt.

## 4. Verdict

**Edit is not safe to build as an unrestricted full-form today.** Text and price fields
(`title`, `description`, `category_id`, `advertiser_type`, `contact.name`, `location.city_id`,
`location.district_id`, `price.value`, `price.currency`, `price.negotiable`) round-trip cleanly —
GET returns exactly what PUT expects, field-for-field, and our existing DTO already carries all of
them. Everything else falls into one of two buckets:

1. **Reconstructible with mapper/DTO work, no live check needed:** `price.trade`, `price.budget`,
   `contact.phone` — add the missing fields to `AdvertPriceRequest`/reuse `contact.phone` from the
   GET response instead of hardcoding `null`, and feed the GET value straight back on every edit.
2. **Genuinely blocked on a live check against a real advert with a user token** — images (§2a)
   and attributes (§2b) are the two that matter for a "full edit" scope, because they're the two
   most likely to be silently destructive rather than cleanly rejected. (c), (d), (e) don't block
   *building* edit, but they determine what the edit UI needs to say and gate (status-eligibility
   messaging, generic-error-surfacing, "your ad will be re-reviewed" copy).

**Recommended scope for SIR-104, given live verification isn't currently possible:** ship edit for
**title, description, and price only** (the fields already proven safe end-to-end, matching
the approach SIR-104 shipped), constructing the PUT payload by
taking the full GET response and overwriting only those fields — never re-deriving `images[]` or
`attributes[]` from app state, only echoing them back verbatim from GET so nothing the seller
didn't touch can be lost by our own mapping logic (images/attributes risk (a)/(b) is then OLX's
call to accept-or-reject the echoed payload, not ours to have mangled). Do not expose photo or
attribute editing until (a) and (b) are checked against a real advert with a user token — that
check is cheap (one GET + one no-op PUT on a disposable test advert) and should happen before
SIR-104 starts, not be discovered mid-implementation. Also resolve (c)/(d)/(e) before finalizing
edit-sheet copy, since each changes what the UI needs to tell the seller (status gating, generic
error surfacing, a possible "back to review" notice) even though none of them block the
title/description/price scope from shipping.
