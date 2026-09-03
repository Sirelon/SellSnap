# SIR-100 spike — does `auto_extend_enabled` work on OLX Ukraine

Date: 2026-09-03. Source of truth: `https://developer.olx.pl/swagger/v2/partner_api.yaml`
(fetched via WebFetch) cross-checked against the docs mirror (context7 MCP,
`/websites/developer_olx_pl_api_doc`), plus **live, unauthenticated-account
probes** against `olx.ua`, `olx.pl`, `olx.pt` using `client_credentials` tokens
minted from the hardcoded per-country creds in `OlxCountry.kt` (see
`.claude/skills/olx-api-verify/SKILL.md` for the recipe). No user OAuth login
was attempted, no advert was published, no token or secret was written to disk.

## Bottom line

**Recommendation: (b).** Build SIR-105 as "surface expiry for every market, wire
`extend` only where OLX documents and now live-confirms it, no publish-time
`auto_extend_enabled` toggle anywhere yet." This matches what
`OlxCountry.supportsExtendCommand` already implements (`code != "ua" && code !=
"pt"`) as of this spike. Outcome (c) — auto-extension being billable — is **not
supported by any evidence found**, live or documented, in UA/PL/PT: no paid
feature named anything like "extend"/"auto-extend"/"renewal" exists in the live
`paid-features` catalog of any of the three countries. But (c) can't be fully
closed without a real advert + real user token (see Check 5), so treat the
"free" conclusion as high-confidence, not proven.

---

## Check 1 — Does olx.ua accept `auto_extend_enabled: true` on publish?

**Unverifiable here.** Requires `POST /adverts` with a real user OAuth token,
which this spike was explicitly told not to attempt (OLX login retry limits ban
accounts on repeated failures).

- **Spec-backed:** `auto_extend_enabled` (boolean) is documented on `POST
  /adverts` with description "Should the advert be automatically extended when
  it expires. Omitting this field means that auto extend is disabled." The spec
  applies this field globally — it is not listed inside any per-region
  exclusion (unlike the `extend` command, which explicitly says "not available
  in UA, PT" in its own description). No UA-specific carve-out exists for the
  field anywhere in the spec text.
- **Live probe:** none possible without a real advert.
- **Minimal human procedure:** publish one throwaway advert on a real UA test
  account with `auto_extend_enabled: true`, confirm the `POST /adverts`
  response is 2xx (not a 400 validation error citing that field), then run
  Check 2 against the same advert.

## Check 2 — Does the flag persist when the advert is read back?

**Unverifiable here**, same reason as Check 1 — needs the advert created in
Check 1.

- **Spec-backed:** the `Advert` schema (returned by `GET /adverts/{id}` and by
  the `PUT` response) carries `auto_extend_enabled` (boolean) with description
  "Should the advert be automatically extended when it expires" — same field
  name, same type, round-trips symmetrically with create/update per the spec
  (consistent with the SIR-99 finding that the GET response is field-for-field
  symmetric with the create/update payload).
- **Codebase state:** SellSnap already parses this field —
  `OlxAdvertDetailResponse.kt:57-58` maps `auto_extend_enabled` →
  `autoExtendEnabled: Boolean?` (defaults to `false` if absent), and
  `OlxAdvertDetail.autoExtendEnabled` exposes it to the domain layer. So the
  *read* path is ready; nothing here is a parsing risk.
- **Minimal human procedure:** `GET /adverts/{id}` on the Check-1 advert
  immediately after publish, and again after OLX's regular re-index delay (a
  few minutes), and confirm `auto_extend_enabled` is still `true` both times —
  a flag that is accepted on write but silently dropped on the next internal
  sync would only show up on the second read.

## Check 3 — Is the field accepted on PT too?

**Same answer as Check 1, unverifiable here without a real PT advert** — but
the spec treats UA and PT identically for this field: `auto_extend_enabled` is
not region-restricted in the schema for either country. PT shares the `extend`
**command** restriction with UA (see Check 4), but the spec draws no such line
for the `auto_extend_enabled` **field**. Same minimal procedure as Check 1,
run against an `olx.pt` test account.

## Check 4 — Does `extend` genuinely fail on UA? What error, verbatim?

**Spec-backed, and now live-confirmed at the routing/scope level (not yet at
the business-rule level).**

- **Spec text (exact):** the `command` field on `POST /adverts/{id}/commands`
  documents: `` `extend` - to extend the activation period of the advert (not
  available in UA, PT).`` This is the *only* place in the whole spec the word
  "extend" appears alongside a region carve-out; `auto_extend_enabled` has no
  matching carve-out anywhere (see Check 1).
- **Live probe results** (client_credentials, scope `v2 read`, advert id
  `999999999` — deliberately non-existent, since no advert is owned by these
  app-level credentials):

  | Country | `POST .../adverts/999999999/commands {"command":"extend"}` | `GET /adverts/999999999` |
  |---|---|---|
  | UA | `HTTP 404` `{"error":{"status":404,"title":"Not Found","detail":"Оголошення не знайдено"}}` | same 404, same body shape |
  | PL | `HTTP 401` `{"error":"insufficient_scope","error_description":"The request requires higher privileges than provided by the token"}` | `HTTP 404` `{"error":{"status":404,"title":"Not Found","detail":"Nie mogliśmy znaleźć tego ogłoszenia."}}` |
  | PT | `HTTP 401` `{"error":"insufficient_scope","error_description":"The request requires higher privileges than provided by the token"}` | `HTTP 404` `{"error":{"status":404,"title":"Not Found","detail":"Advert not found"}}` |

  PL's `POST .../commands {"command":"activate"}` on the same fake id returned
  the identical `401 insufficient_scope` — confirming the 401 is a **generic
  write-scope gate**, not something specific to `extend`, and that a
  `client_credentials`/read-scope token cannot reach command validation logic
  on PL or PT at all.

  **Interesting asymmetry, noted but not over-interpreted:** UA let the same
  read-scope token past the scope gate and resolved straight to a 404
  "advert not found," while PL/PT rejected it with 401 before ever looking up
  the advert. This says something about UA's middleware ordering
  (existence-check before/instead-of scope-check on this route) but says
  nothing about the region-extend business rule itself, since the request never
  reached a real advert in any country.
- **What this does *not* prove:** the actual error `extend` returns on UA
  against a *real, existing* advert — i.e., the exact `validation[].field` /
  `title` string the UI would need to quote. The spec documents the general
  error envelope (`{error:{status,title,detail,validation:[{field,title,detail}]}}`,
  matching what `OlxRemoteErrorParser` already handles) but gives no worked
  example for a region-blocked `extend`.
- **Minimal human procedure:** on the Check-1 UA test advert (must be
  `active` status), call `POST /adverts/{id}/commands` with
  `{"command":"extend"}` using a real user token, capture the exact HTTP status
  and JSON body, and confirm it is a `400` with a `validation[]` entry (the
  existing `OlxRemoteErrorParser` shape) rather than a `404`/`403`/`501`. That
  string is what SIR-105's UI would surface if `extend` were ever offered in UA
  (it should not be, per the recommendation below).

## Check 5 — Is auto-extension billable in any market?

**No evidence found that it is — live-confirmed catalog check across UA/PL/PT
— but the check cannot be fully closed without a real account.**

**What was searched:** the full spec text for every occurrence of `extend`
(4 hits total: the `command` enum description, and the 3 `auto_extend_enabled`
field descriptions on create/update/response — quoted above, none reference
billing), plus `billing`, `postpaid`, `prepaid`, `invoice`, `packet`, `balance`,
`renewal`, `charge`, `cost`, `price`. Result: `auto_extend_enabled` and
`extend` are textually isolated from every payment/billing section of the spec
(`/users/me/account-balance`, `/users/me/billing`, `/users/me/prepaid-invoices`,
`/users/me/postpaid-invoices`, `/packets`) — no cross-reference either
direction.

**The `paid-features` resource, documented:**
- `GET /adverts/{advertId}/paid-features` — active paid features on one advert.
  Item shape: `{code, type, name, duration, valid_to}` (`PaidFeature` schema).
  **No price/amount/currency field anywhere in `PaidFeature`.**
- `POST /adverts/{advertId}/paid-features` — purchase one, body
  `{code, payment_method: "account"|"postpaid"}` → `204`. The **explicit
  `payment_method` choice on every purchase** is the spec's only signal for
  "this action costs money" — nothing resembling it exists on `auto_extend_enabled`
  or the `extend` command, both of which take no payment method and return
  either the plain advert object or `204` with no charge-related fields.
- `GET /paid-features` (no advert id) — the general catalog for the
  authenticated context.

**Live probe — the actual catalog, per country** (`client_credentials`,
`v2 read`, `GET /paid-features`, `HTTP 200` in all three):

| Country | `type` values present | `code`s present |
|---|---|---|
| UA | `bundle`, `topads`, `pushup`, `ad_homepage` | `bundle_premium`, `bundle_optimum`, `bundle_basic`, `topads_7`, `topads_30`, `pushup_automatic`, `pushup`, `ad_homepage_7` |
| PL | `topads`, `bundle`, `pushup`, `ad_homepage` | `promoted_ad_7/30/3`, `bundle_basic/optimum/premium`, `pushup`, `pushup_automatic`, `ad_homepage_7` |
| PT | `topads`, `bundle`, `pushup`, `ad_homepage` | `topads_7/28/30`, `bundle_basic/optimum/premium`, `pushup`, `pushup_automatic`, `homepageads_7` |

None of the 8-9 catalog items in any of the three countries is an
extension/renewal product — every code maps to "boost visibility" concepts
(top-of-list ads, refresh-to-top/pushup, homepage placement, bundles of the
above). **`auto_extend_enabled` and `extend` are not represented in the
paid-features catalog of UA, PL, or PT.** That is a real, live result, not an
inference from silence in the spec alone.

**What remains unverifiable here:**
1. `GET /adverts/{advertId}/paid-features` (the *per-advert active* list, as
   opposed to the catalog) returned `HTTP 400 {"error":{"status":400,"title":
   "Bad Request","detail":"Invalid user ID in token"}}` on UA when called with
   a `client_credentials` token — this endpoint requires a real user-context
   token, so it's impossible to confirm from here that flipping
   `auto_extend_enabled` never causes a paid feature to silently appear on an
   advert's active list after an extension actually fires.
2. Business/professional OLX accounts sometimes run on different (postpaid,
   contract) billing plans than the catalog shown to a fresh app-level token;
   the live catalog above reflects whatever pricing/catalog context
   `client_credentials` resolves to, which may not match every real seller's
   plan.
3. No live test of what happens when a real advert with `auto_extend_enabled:
   true` actually expires and OLX auto-extends it server-side — that is an
   asynchronous, days-later event that cannot be produced or observed in this
   spike.
- **Minimal human procedure:** on the Check-1 test advert, (a) call `GET
  /adverts/{id}/paid-features` with a real user token before and after
  publish to see the active-feature baseline, (b) either wait for a natural
  expiry+auto-extend cycle or shorten it if OLX support/sandbox allows, and
  (c) diff the paid-features list and `GET /users/me/billing` /
  `account-balance` before and after. Only that closes check 5 completely.

---

## Recommendation detail

- **Which of the three outcomes:** **(b)** — `auto_extend_enabled` is
  spec-documented as a plain, unrestricted boolean field (Checks 1–3, spec-backed
  but publish-unverified), so it is *not* known to be ignored either — the
  honest state is "unverified, assume nothing," which is why no publish-time
  toggle should ship yet. `extend` the **command** is spec-documented and now
  live-confirmed-at-the-routing-level as region-gated, with UA explicitly
  named. Outcome (c) has no supporting evidence in three live catalogs and no
  spec cross-reference — don't design a price-disclosure UI for a charge
  nothing points to, but don't rule it out either since Check 5's residual gap
  (per-advert paid-features list needs a user token) is real.
- **Markets to offer `extend` in:** every live OLX country **except UA and
  PT** — i.e. RO, PL, BG (and KZ if it ever gets credentials), matching the
  spec's explicit "not available in UA, PT" and matching
  `OlxCountry.supportsExtendCommand` (`code != "ua" && code != "pt"`) as
  already implemented in this codebase.
- **UA and PT specifically:** SIR-105 should surface expiry (`validTo`, already
  parsed) in every market including UA/PT, but must not render an `extend` /
  "renew" action there, and must not render an `auto_extend_enabled` toggle
  anywhere yet (Checks 1–3 unverified) — a toggle that silently does nothing,
  or a button that always 400s, is worse than omitting both.

## Confidence summary

| Claim | Confidence |
|---|---|
| `extend` command excluded in UA and PT | spec-backed (exact quote above) |
| `extend` write-scope gate exists and blocks unauthenticated probes on PL/PT | live-confirmed (401 insufficient_scope) |
| UA's commands route resolves advert-existence before/without the same scope gate | live-observed, cause not confirmed |
| `auto_extend_enabled` accepted on UA publish | unverifiable here — needs real advert |
| `auto_extend_enabled` persists on UA read-back | unverifiable here — needs real advert |
| `auto_extend_enabled` accepted on PT publish | unverifiable here — needs real advert |
| Exact 400 body `extend` returns on a real UA advert | unverifiable here — needs real advert |
| No paid-feature product for extend/auto-extend in UA/PL/PT catalogs | live-confirmed (3 catalogs fetched) |
| Auto-extension never triggers a hidden charge anywhere in the flow | inferred from (spec silence + 3 live catalogs), not proven — per-advert paid-features list and the actual server-side auto-extend event are unverifiable here |
