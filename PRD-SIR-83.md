# PRD — Multiple OLX accounts in one country

**Ticket:** [SIR-83](https://linear.app/sirelon/issue/SIR-83/support-multiple-olx-accounts-in-the-same-country) ·
**Blocks:** [SIR-84](https://linear.app/sirelon/issue/SIR-84/support-olx-accounts-across-different-countries) ·
**Companion:** [TRD-SIR-83.md](TRD-SIR-83.md) · **Status:** agreed, ready to schedule

Cross-references in this document use the companion TRD's IDs: **F1–F6** findings, **A1–A8** required
engineering additions.

---

## 1. Summary

SellSnap connects to exactly one OLX account. A seller who lists under two identities on the same OLX
site — a personal profile and a business shop on `olx.pl`, say — has to disconnect one to use the
other, losing their session and re-entering credentials on OLX every time they switch.

This release lets a seller connect several OLX accounts within one OLX country, keep them all
connected, and pick which one is active — including from the publish screen, where the destination
account is named.

One listing goes to exactly one account. Cross-country accounts stay in
[SIR-84](https://linear.app/sirelon/issue/SIR-84/support-olx-accounts-across-different-countries).

---

## 2. Problem

**What a seller hits today.** Connecting a second OLX account is destructive: `logout()` clears the
stored token, then the seller runs the full OAuth flow again — country picker, OLX web login,
credentials, consent. Coming back means repeating it. The app has no concept of "my other account",
so the switch costs a login every time.

**Why that is worse than it sounds.** OLX blocks sign-ins after repeated failures, and a blocked or
suspended OLX account is unrecoverable from inside SellSnap. A workflow that forces a fresh
interactive login on every switch pushes the highest-value sellers — the ones who list most, under
more than one identity — into the exact behaviour that risks their account.

**Why now.** Multi-identity selling is normal on OLX: private profile for household items, business
shop for stock. SIR-83 and SIR-84 were filed off the same root cause, and SIR-84 cannot be built
until accounts are modelled as a set rather than a singleton. Doing the model once, now, avoids two
migrations of stored credentials.

> **Evidence gap.** No quantified demand yet: no support-ticket count, app-store review count, or
> funnel data on repeat disconnect→connect cycles (O1). This sets the priority of the release, not its
> design, and it does not gate the build: the token work in §6 removes a duplicate refresh path and a
> latent country/token mismatch that are worth fixing on their own.

---

## 3. Who this is for

| Segment | Situation | What they need |
|---|---|---|
| **Dual-identity seller** (primary) | Private profile + business shop on the same OLX site | Keep both connected; choose the target without a login |
| **Household sharer** | One phone, listings for two family members | Fast switch, obvious labelling, no accidental cross-posting |
| **Small trader with two storefronts** | Two OLX business accounts, different categories or brands | Reliable active-account context |
| **Single-account seller** (majority) | One account, always | **Must see no change at all.** No new steps, no new decisions |

The last row is a constraint, not a nice-to-have: the feature has to be invisible until a second
account exists.

---

## 4. Goals and non-goals

### Goals

1. A seller can connect two or more OLX accounts of the same OLX country and all stay connected.
2. Switching the active account requires no OLX login and no credential entry — including after a
   month of not using that account (D1).
3. Every screen that acts on an account states which account it is acting on — above all the publish
   screen.
4. One expired or revoked account never blocks the app or disturbs the others.
5. Existing single-account users are migrated silently, with no forced re-login.
6. The data model keys accounts as `(country, account)`, so SIR-84 needs no second migration.

### Non-goals

1. **Cross-country accounts** — SIR-84.
2. **Cross-posting one listing to several accounts** — explicitly out. One listing → one account.
3. **A per-listing publish target independent of the active account** — deferred (D2, §16.2).
4. **A merged or filtered "all accounts" My Ads feed** — deferred (D8, §16.3).
5. **Paywalling or capping multi-account as a paid tier** — free this release; the entitlement seam in
   §11 lets it be turned on later without rework.
6. **User-set account nicknames** — OLX profile name + email is enough to disambiguate in v1.
7. **Team or delegated access** (managing someone else's account without their credentials).
8. **Per-account app settings** (theme, language, saved location) — these stay device-level.
9. **Hardening token storage at rest** — see §12; a separate ticket.

---

## 5. Decisions

Every item below is settled. Both documents are written to these decisions; nothing downstream may
assume the alternative.

| # | Decision | Rationale | TRD |
|---|---|---|---|
| **D1** | **Bounded keep-alive refresh is required.** On app foreground, refresh each usable account whose last refresh is older than 20 days. Purely lazy refresh is not sufficient | OLX invalidates a refresh token left unused for 30 days, and reviving it costs an interactive login — the thing this release exists to avoid. A refresh is not a login, so §6.3 permits it | F2 / A1 |
| **D2** | **No per-listing override.** The publish screen retargets by *switching the active account*; the draft follows it | One source of truth for "which account", no draft binding, no persistence across process death, no acknowledgement flow. Same country ⇒ same category tree, attributes and currency, so nothing the seller entered is reset | §7 |
| **D3** | **The publish button and confirmation keep their shipped copy** ("Publish on OLX", "Yep, publish"). The target account is named on the **preview row** and on the **success screen** | Naming the account where the seller can also change it, and again after the fact, without rewriting settled strings | F6 |
| **D4** | **Cap is 3 accounts per country** | Each connected account is an independent 30-day expiry clock whose only remedy is an interactive login. Not a request-volume limit — keep-alive costs ~1 request per account per 20 days against OLX's 4,500 per IP per 5 minutes | §6 |
| **D5** | **Add-account must force a fresh OLX authentication**, and being handed the already-connected account is the expected outcome to design for, not an edge case | Both platforms launch the OAuth page in a browser already holding an OLX session, so without this the seller re-authorizes the account they already have | F3 / A3 |
| **D6** | **Publish asserts account identity before posting** and aborts on mismatch | The pre-publish `users/me` call already exists, so this is free, and with D3 it is the primary defence for guardrail G4 | A5 |
| **D7** | **Session state is read from the stored account set, never from a network call.** A dead active account is never auto-switched away from | Any account present → signed in. Silently changing which identity the app acts as, at launch, is the same class of surprise as publishing to the wrong account | F4 |
| **D8** | **My Ads names the active account** in its header and empty state. No per-account filter | The header and empty state are what stop an empty list reading as "my listings vanished". A filter is a second switching affordance for something Profile does one tap away | §7 |
| **D9** | **A `Needs reconnect` account is selectable.** Selecting it surfaces `Reconnect <account>` in place and leaves the previous account active | Fewer states and fewer strings than a disabled row the seller cannot act on | §7 |
| **D10** | **Disconnect is local only.** OLX documents no revocation endpoint, so the copy must not imply we withdraw access on OLX's side, and it links to OLX's own connected-applications settings | Honesty: the refresh token ages out up to 30 days later | §6 / A8 |
| **D11** | **Migration deletes the legacy token key.** An older build reads "not connected" | OLX rotates refresh tokens daily, so a retained copy is stale within a day and would fail its first refresh anyway — same destination, one dead credential left at rest | §6 |
| **D12** | **Publish sends the contact name only.** The listing phone comes from the OLX account server-side | We never send a phone number today | F6 |
| **D13** | **The token layer ships to beta ahead of the accounts UI** | It touches every authenticated request while being externally identical for one account, so it can be proven in production before any new surface exists | §10 |

---

## 6. The experience

### 6.1 Accounts list — Profile

**U1** — *As a seller I want to see every OLX account I have connected, so I know what SellSnap can
post as.*

* The Profile screen shows an **Accounts** section listing each connected account: avatar, OLX
  profile name, email, a business badge where `isBusiness` is true, and a `Needs reconnect` chip
  where the account is not usable.
* The active account is visually marked as active and sorted first.
* With exactly one connected account, the section renders as it does today — a single account, no
  switcher affordance, no "active" marking. The feature is invisible.
* An **Add OLX account** action is always present.
* Each row exposes **Set as active** (non-active rows only) and **Disconnect**.
* A `Needs reconnect` row is selectable (D9): choosing it does not complete a switch, and instead
  offers **Reconnect <account>** in place, subject to the §6.3 cooldown.

**AC:** one account → today's Profile layout plus the add action · two or more → list, active marked
and first, per-row actions reachable · selecting a `Needs reconnect` account leaves the previous
account active and shows a reconnect action on that row.

### 6.2 Add an account

**U2** — *As a seller I want to add a second OLX account without losing the first.*

* **Add OLX account** starts the OAuth flow. The country step is skipped: this release adds accounts
  within the already-selected country, and the country is stated on the confirmation ("You'll be
  adding an account on olx.pl").
* **The flow must present an OLX login form** (D5). Reaching the OLX consent page already signed in
  as the connected account is a defect, not an acceptable outcome — the seller has no way forward
  from it, and trying repeatedly is exactly what gets OLX accounts suspended. The mechanism is an
  engineering choice with a security dimension; see §15 O6.
* On success the new account is stored alongside the existing ones and **becomes the active account**
  — the seller just proved intent to use it.
* On success SellSnap fetches the account profile, so name, email, avatar and contact name are
  available before the seller reaches a publish screen. This fetch is also how the account is
  identified, so it is not optional.
* **Already-connected account returned:** no second entry is created. The existing entry's tokens are
  refreshed, it becomes active, and the seller is told **precisely** which account came back and what
  to do to add a different one. Never a generic failure, never an automatic retry.
* **Cap:** 3 accounts per country (D4). At the cap the add action is disabled with an explanation.
* **Only one authorization may be pending at a time.** Starting a new one replaces any pending
  session, so a stale callback can never be mistaken for the new one.

**AC:** existing accounts and their tokens survive the add · the add flow reaches a login form, not a
bare consent page · cancelling or backing out of the OLX page leaves state untouched, nothing
half-added · re-authorizing an already-connected account produces one entry and a message naming it ·
at 3 accounts the add action is disabled with copy explaining why.

### 6.3 Failed authorization — the OLX lockout rule

**U3** — *As a seller I must not be nudged into repeated failed OLX logins, because that gets my OLX
account blocked.*

This is a hard product rule, not a polish item.

* SellSnap **never retries an authorization automatically.** No background retry, no
  retry-on-transient-error, no "trying again…" state.
* After a failed authorization attempt, the add/reconnect action enters a **60-second cooldown** with
  a visible countdown. The cooldown and the consecutive-failure count are **persisted**, so a
  force-quit does not bypass them.
* The failure message names the likely cause and warns about the lockout risk in the seller's own
  language, e.g. *"Couldn't connect that account. OLX temporarily blocks sign-ins after repeated
  failed attempts — check your details on OLX before trying again."*
* A second consecutive failure on the same account stops offering an immediate retry and instead
  links to OLX's own sign-in/recovery page.
* **Token refresh is not a login.** Refreshing with a stored `refresh_token` does not enter
  credentials and is not covered by this rule — it may proceed silently, including the keep-alive
  refresh in D1.

**AC:** no code path retries authorization without a fresh user tap · cooldown is enforced per
account, visible, and survives a force-quit · after two consecutive failures the primary action points
at OLX recovery, not at another attempt · automated tests cover "authorization fails → exactly one
attempt was made".

### 6.4 Switch the active account

**U4** — *As a seller I want to switch which account I'm working as, without logging in.*

* Switching uses stored tokens only. No OAuth, no browser, no credential entry.
* The switch changes: the account new listings go to, the account My Ads shows, the profile shown in
  Profile, and the contact name sent at publish (D12).
* An open draft follows the active account, and the publish screen always displays the current target
  (§6.6), so there is one source of truth and nothing to reconcile.
* If the target account's tokens are expired, SellSnap refreshes them in the background. If the
  refresh terminally fails (`invalid_grant` or `invalid_token`), that account is marked
  `Needs reconnect`, the switch does not complete, the previous active account stays active, and the
  seller is offered **Reconnect** — a deliberate action, subject to the §6.3 cooldown.
* Keep-alive refresh (D1) runs on app foreground for usable accounts whose last refresh is older than
  20 days, so an account left unused for a month is still switchable without a login.

**AC:** switch completes with no network call when tokens are valid · switch never opens a browser ·
a terminally-failed refresh leaves the previous account active and the app usable · other accounts'
tokens are untouched by one account's failure · a request issued after a switch carries the new
account's token, never the previous one.

### 6.5 Disconnect an account

**U5** — *As a seller I want to remove an account from SellSnap without touching my OLX account.*

* Copy is **Disconnect**, never *Delete* or *Remove account*: it must be unmistakable that the OLX
  account and its live listings are unaffected.
* A confirmation states what happens: SellSnap forgets the account's access on this device; listings
  already on OLX stay published.
* The confirmation must **not** imply that access is withdrawn on OLX's side, because it is not
  (D10). It carries a secondary link to OLX's connected-applications settings, which is the only
  place a seller can actually revoke access.
* Disconnecting deletes that account's tokens and cached profile locally, and leaves all other
  accounts untouched.
* **Disconnecting the active account:** the most recently used remaining account becomes active.
* **Disconnecting the last account:** the app returns to the landing/guest state, exactly as today's
  logout does.
* "Delete my SellSnap data" clears **every** connected account, alongside the device-level data it
  already clears today (saved location, draft photos, stored country, analytics consent) — unchanged
  scope.

**AC:** confirmation copy states OLX listings are unaffected and does not claim server-side
revocation · other accounts keep working, no re-login needed · disconnecting the last account matches
today's logout behaviour · the delete-my-data path leaves no stored token for any account.

### 6.6 Publish — the target account, named and changeable

**U6** — *As a seller I want the publish screen to tell me which account this listing goes to, and let
me change it before I publish.*

* The Preview/Publish screen shows a **Publish to** row with the target account: avatar, name,
  business badge. The target is the active account — there is no second notion of "the current
  account" anywhere in the app (D2).
* Tapping the row opens the account picker. Choosing another account **switches the active account**
  and the draft follows it. Nothing the seller entered is reset or re-validated, because the same
  country means the same category tree, the same attributes and the same currency.
* The publish button and the confirmation keep their existing copy (D3). The target account is named
  on this row, above the button, and again on the success screen.
* Contact name comes from the target account. Only the name is sent; OLX takes the phone from the
  account (D12). The blank-contact-name recovery flow that currently blocks publishing applies to
  whichever account is targeted.
* **Before posting, SellSnap verifies that the token it is about to use belongs to the account named
  on this screen, and aborts with an explanatory message if it does not** (D6). This is the primary
  defence for G4.
* Publish uses the target account's token. If its refresh terminally fails, publishing is blocked with
  an inline **Reconnect <account>** action — it never auto-launches an OLX login mid-publish.
* Under `screenshotMode` the row renders a fixed placeholder identity, so store screenshots never
  carry a real profile name or email (A7).

**AC:** the target account is visible on the preview screen without extra taps · changing it from the
publish screen does not clear or re-validate any drafted field · the contact name comes from the
target account · an identity mismatch aborts before any listing is posted · publishing with a dead
token surfaces a reconnect action instead of a generic failure · no store screenshot contains a real
account name or email.

**U7** — *As a seller I want the success screen to confirm which account the listing went to.*

* The publish success screen names the account and links to the listing on OLX.

**AC:** the account is named on the success screen · the link resolves to the listing under that
account.

### 6.7 My Ads

**U8** — *As a seller I want to know which account the listings I'm looking at belong to.*

* My Ads shows the **active account's** listings and names that account in the header.
* The empty state names the account: *"No listings on <account> yet"* — so an empty list reads as
  "this account is empty", never as "your listings vanished".
* Switching accounts is done in Profile or from the publish screen; My Ads has no filter of its own
  (D8).
* A load in flight when the active account changes is discarded, so a list never renders under the
  wrong header.

**AC:** the header always names the account whose listings are shown · the empty state names the
account · switching accounts repeatedly and opening My Ads after each switch always shows the account
just selected.

### 6.8 First run, guest mode, and session state

**U9** — First-run onboarding, the country picker, guest mode, and the single-account connect flow are
unchanged. Guest mode has no accounts, so nothing in this feature is reachable from it. Connecting
from guest mode produces exactly one account, which is active.

Session state is decided by the stored account set, not by a network call (D7): any account present →
the seller is signed in; no accounts and not guest → landing. A failed profile fetch never renders a
connected seller as a guest, and a dead active account never routes them to the landing screen.

**AC:** first-run flows are untouched · a new user with one account never sees an account switcher,
picker, or "active" marking · a cold start with the active account's token revoked lands in the app,
on that account marked `Needs reconnect`, never on the landing screen.

---

## 7. Migration of existing users

**U10** — *As an existing connected seller I want the update to change nothing for me.*

* On first launch after the update, the stored single token becomes account #1, active, keyed to the
  currently stored country.
* **No re-login. No re-authorization. No re-consent.** Forcing existing users through OLX login would
  trip the lockout risk in §6.3 for the entire installed base at once — this is the single
  highest-risk failure mode of the release.
* If the stored token cannot be read or migrated, the seller lands in the same state today's terminal
  refresh failure produces (disconnected, offered a connect action) — never a crash, never a silent
  no-op that looks like data loss.
* Migration runs once and is idempotent; a partially completed migration must be safe to re-run.
* The legacy storage key is deleted once migrated (D11). An older build installed afterwards reads no
  key and resolves to "not connected", a state it already handles — no crash, and no dead credential
  left at rest.

**AC:** an existing connected user updates and is still connected, same account, no prompts ·
migration is idempotent · a corrupt or unreadable stored token yields the disconnected state, never a
crash.

---

## 8. Rules and edge cases

| Situation | Expected behaviour |
|---|---|
| Add-account returns the already-connected OLX user | One entry. Tokens refreshed, becomes active, seller told which account came back and how to add a different one |
| Add-account reaches OLX consent without a login form | Defect (D5). The flow must force fresh authentication |
| One account's refresh token revoked | Only that account is marked `Needs reconnect`. Every other account keeps working, tokens untouched |
| The **active** account's token is dead | It stays active, marked `Needs reconnect`, with a Reconnect action. No auto-switch to a working account (D7) |
| Every account needs reconnect | The app stays usable and signed in, showing reconnect actions. The landing state is reached only when zero accounts are stored |
| An account is unused for 20+ days | Keep-alive refreshes it on foreground (D1), so it stays switchable without a login |
| Account cap reached (3) | Add action disabled with an explanatory message |
| Authorization callback arrives for a replaced pending session | Discarded. State mismatch is treated as a failure, not applied to whichever account happens to be active |
| Active account changes while a draft is open | The draft follows the active account and the publish row shows the new target (D2) |
| Seller disconnects the account they were drafting under | The most recently used remaining account becomes active; the publish row shows it before publish |
| Publish token does not match the named account | Publish aborts before posting, with an explanatory message (D6) |
| App killed mid-authorization | No partial account is stored. Returning shows the accounts list unchanged |
| Two accounts with identical OLX profile names | Email is the disambiguator and is always shown alongside the name |
| Business account with different OLX advert limits | Publish failure is surfaced with OLX's own reason, attributed to the named account, never as a generic error |
| Delete my SellSnap data | All accounts, all tokens, all cached profiles cleared, plus today's device-level data |

---

## 9. Constraints that shaped this design

1. **OLX blocks sign-ins after repeated failures, and a blocked account cannot be recovered from
   inside SellSnap.** Everything about authorization is one-shot-and-cool-down (§6.3), and no existing
   user is asked to re-login (§7).
2. **An OLX refresh token left unused for 30 days is invalid, and reviving it costs an interactive
   login.** So refresh is lazy on use *plus* a bounded keep-alive pass on foreground for accounts
   older than 20 days (D1). Refresh volume is not the binding cost — OLX's documented ceiling is 4,500
   requests per IP per 5 minutes and keep-alive costs about one request per account per 20 days. The
   binding cost is one expiry clock per account, which is what sets the cap (D4).
3. **Both platforms launch the OAuth page in a browser that already holds an OLX session,** so
   add-account must force fresh authentication or it silently re-authorizes the connected account
   (D5).
4. **All SellSnap users of one country share one OLX partner `client_id`.** Concurrent per-user tokens
   under that shared credential already work in production — every signup would otherwise log out
   every existing seller in that country.
5. **OLX exposes no token revocation endpoint.** Disconnect can only forget tokens locally (D10).
6. **Country is currently global app state** driving API host, credentials, currency, and language.
   This release keeps one active country and does not touch that; the account key is
   `(country, accountId)` from day one, so SIR-84 only adds a second live country rather than a
   re-key.
7. **Same country ⇒ same category tree, attributes, and currency.** This is why changing the target
   from the publish screen cannot invalidate a draft, and it is exactly what SIR-84 will not be able
   to assume.
8. **8 locales** (en, bg, kk, pl, pt, ro, ru, uk) — every new string ships in all of them, from string
   resources.

---

## 10. Copy and localisation

* All new strings are localised resources in all 8 locales; nothing composed at runtime from
  fragments. Placeholders go through `stringResource(id, arg)`; `String.format` is banned.
* **Terminology, fixed:** "Disconnect" for removing an account from SellSnap. Never *Delete*, never
  *Remove account* — sellers must never fear for their OLX account or live listings. The current
  `profile_logout` string ("Log out") is renamed accordingly in all 8 locales.
* **Terminology, fixed:** the publish destination is stated as a **"Publish to <account>" row on the
  preview screen**, not in the button label. The button and confirmation keep their shipped copy (D3).
* Failure copy names the account it concerns. "Couldn't connect" without a name is not acceptable in a
  multi-account app.
* The already-connected message names the account that came back and says what to do next.
* The lockout warning (§6.3) must be accurate, not alarmist: OLX blocks sign-ins temporarily after
  repeated failures; it is not SellSnap doing it, and the seller's remedy is on OLX.
* The disconnect confirmation must not claim SellSnap revokes access on OLX (D10).
* UK English for en (house style).

---

## 11. Analytics and the paywall hook

New events, alongside the existing `auth_*` and `ad_publish_*` set:

| Event | Properties | Answers |
|---|---|---|
| `account_add_started` | `country`, `existing_account_count` | Add funnel entry; separates add from first connect (M5) |
| `account_add_completed` | `country`, `new_account_count`, `was_duplicate` | M1, M5, and how often D5's mechanism fails to force a fresh login |
| `account_add_failed` | `country`, `reason`, `consecutive_failures` | G2, and whether the cooldown is doing its job |
| `account_switched` | `from_index`, `to_index`, `account_count`, `from_publish_screen` | M2, M4 |
| `account_disconnected` | `remaining_account_count`, `was_active` | Churn out of multi-account |
| `account_reconnect_started` / `_completed` | `reason` | Token-death frequency, feeds G1 |
| `account_token_expired_unused` | `days_since_last_use` | Fired on `invalid_grant`. Measures exactly what keep-alive (D1) prevents, and is the evidence for moving the cap (A6) |
| `publish_account_mismatch_aborted` | `account_count` | D6 firing at all means something upstream is broken; must be zero in the wild |

* **User property** `connected_account_count` on every user. This is the paywall hook: it makes the
  "how many users would a 1-account free tier affect" question answerable before any pricing decision,
  at zero extra cost later.
* `ad_publish_*` gains an `account_index` property so publish success can be read per account (G3).
* No event carries an email, an OLX user id, an account name, or a token. Accounts are referenced by a
  stable local index only, never reused after a disconnect.
* Existing analytics consent governs all of it; nothing new is collected without consent.

**Entitlement seam:** the add-account action routes through a single "can add another account?" check
that this release always answers yes to (below the cap). A future paid tier changes that answer and
the upsell surface — nothing else.

---

## 12. Privacy and legal

* Storing credentials for several OLX accounts changes what the app holds. The privacy policy and
  terms pages (`sirelon.github.io/SellSnap/privacy-policy/`, `/terms-and-conditions/`) must be
  reviewed and updated **before** the release goes out — the codebase already flags these pages as
  release-gating whenever data flows change. If the add-account mechanism ends up hosting an OLX login
  form inside the app (§15 O6), that review is mandatory rather than a formality.
* OLX scope is unchanged, so no new consent screen and no new data categories.
* No new OS permissions, so no store-listing permission changes.
* Tokens are held with the same at-rest protection as today: DataStore Preferences inside the app
  sandbox, with no Keystore or Keychain involvement on either platform. Multiplying stored credentials
  is a reasonable moment to revisit that; it is a separate ticket (§16.5) and not a condition of this
  release.

---

## 13. Release plan

**Prerequisite.** Two genuine OLX accounts on one country are required to verify this feature at all
(§15 O7). The login budget is one attempt per account per sitting, with every repeatable step
scripted, per the lockout rule.

**Phase 0 — spike (1 day).** Answer how add-account forces a fresh login on each platform (§15 O6),
and capture the real `authorization_code` grant `expires_in` while there. Everything else is gated on
this: without a working add flow the release has no user.

**Phase 1 — token layer to beta ahead of the UI (D13).** The account store, migration, active-account
switching, single refresh path, keep-alive, failure isolation, session-state fixes and the publish
assertion are externally identical for a single-account seller while touching every authenticated
request. Ship them, watch G1–G3 for at least two weeks, then build UI on top.

**Phase 2 — internal.** Migration from a real single-account install, add a second account, switch,
retarget from the publish screen, disconnect. Both platforms. Verify no re-login on update.

**Phase 3 — beta** (Play internal testing + TestFlight). Watch G1–G5 and M5. Recruit at least two
genuine dual-identity sellers; synthetic accounts will not surface the confusion risk behind G4.

**Phase 4 — production**, once no guardrail has regressed.

There is no remote-config or feature-flag infrastructure in the app, so a bad release is rolled back
by shipping a build, not by a switch. That is why the token layer goes out on its own first, and it is
itself an argument for adding flag infrastructure later (§16.6).

**Release notes** in all 8 locales for both stores, per the existing release process.

### QA matrix — must all pass

This is the single QA matrix for the release; the TRD's test plan covers the code-level equivalents.

| # | Case |
|---|---|
| Q1 | Update from single-account build → still connected, no prompt, no login |
| Q2 | Add a second account → first account still works without re-login |
| Q3 | Add-account while signed in to OLX in the device browser → a login form appears, or the seller is told exactly which account came back and what to do |
| Q4 | Authorize an already-connected account → one entry, not two, named in the message |
| Q5 | Failed authorization → exactly one attempt, cooldown shown, no auto-retry |
| Q6 | Two consecutive failures → primary action points at OLX recovery |
| Q7 | Force-quit during the 60s cooldown, relaunch → cooldown still in force |
| Q8 | Revoke account B's access on OLX → B shows `Needs reconnect`, A unaffected |
| Q9 | Active account's token revoked, cold start → lands in the app on that account marked `Needs reconnect`, never on the landing screen |
| Q10 | Select a `Needs reconnect` account → previous account stays active, Reconnect offered in place |
| Q11 | Retarget from the publish screen → listing appears under the chosen account with that account's contact name, and no drafted field was reset |
| Q12 | Switch the active account from Profile with a draft open → the publish row shows the new account before publish |
| Q13 | Disconnect the account a draft was under → publish row shows the new active account before publish |
| Q14 | Switch A → B → A repeatedly, opening My Ads after each switch → every list belongs to the account just selected |
| Q15 | Clock forward 25 days, foreground the app → the unused account refreshes silently and stays usable |
| Q16 | Disconnect the active account → most recent remaining becomes active |
| Q17 | Disconnect the last account → landing state, as today's logout |
| Q18 | Delete my SellSnap data → no token remains for any account |
| Q19 | Guest mode → no account UI anywhere |
| Q20 | Single-account user → no switcher, picker, or active marking anywhere |
| Q21 | Kill the app mid-authorization, and again between code exchange and profile fetch → no partial account |
| Q22 | Store-screenshot run → no real name or email in any capture of the preview screen |
| Q23 | All 8 locales → no truncation or untranslated string in the new UI |

---

## 14. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Seller publishes to the wrong account | High — reputational, invisible until a buyer replies | The identity assertion before posting (D6) makes it impossible rather than unlikely; the target account is named on the preview row and the success screen; there is only one notion of the current account (D2). G4 is a P1 guardrail |
| Add-account cannot force a fresh login on Android | Severe — the feature has no usable entry point | Phase 0 spike gates the release; ordered fallbacks in §15 O6 |
| Migration forces the installed base to re-login | Severe — mass exposure to the OLX lockout risk | §7 is non-negotiable; Q1 gates the release |
| A secondary account dies after a month unused | Breaks goal 2 and pushes the seller into an interactive login | Keep-alive refresh (D1); `account_token_expired_unused` measures residual cases; cap of 3 bounds the exposure |
| One dead account degrades the whole app | Loss of trust in multi-account | Per-account failure isolation (§6.4) and session state read from storage (D7); Q8, Q9 |
| Feature complexity leaks into the single-account experience | Hurts the majority to serve a minority | Q20 — no multi-account affordance renders below two accounts |
| No rollback switch | A bad release needs a new build | Token layer shipped and watched separately (D13); longer beta; consider flag infrastructure next |
| Hosting an OLX login form in-app, if it comes to that | Security posture change, degraded autofill, more hand-typed passwords and therefore more lockouts | Only as a last resort, scoped to add-account alone, with the privacy/terms review in §12 (§15 O6) |
| Business-account OLX rules differ from private ones | Confusing publish failures | Surface OLX's own reason, attributed to the named account |

---

## 15. Success metrics and open questions

Baselines are unknown (O1), so targets are proposals to confirm against the first 30 days of data
rather than commitments.

| # | Metric | Definition | Proposed target |
|---|---|---|---|
| M1 | Multi-account adoption | Share of connected users with ≥2 accounts, 30 days after release | ≥5% |
| M2 | Switch usage | Median account switches per multi-account user per week | ≥1 |
| M3 | Listing volume lift | Published listings per multi-account user vs. single-account user | ≥1.5× |
| M4 | Retarget usage | Share of switches initiated from the publish screen rather than Profile | Measure only — tells us whether a per-listing target (§16.2) is worth reopening |
| M5 | Add-account completion | `auth_completed` ÷ `auth_started` for add-account attempts (not first connect) | ≥85% |

### Guardrails — a regression here blocks the release

| # | Guardrail | Threshold |
|---|---|---|
| G1 | Interactive OLX logins per connected account per week | Must fall vs. today, never rise |
| G2 | `auth_failed` rate per authorization attempt | No increase vs. pre-release baseline |
| G3 | `ad_publish_failed` ÷ `ad_publish_started` | No increase |
| G4 | Reports of a listing published to the wrong account | Zero. Any single confirmed case is treated as a P1 |
| G5 | Reports of OLX accounts blocked or suspended after using SellSnap | Zero |

G4 is the one that would sink the feature: publishing to the wrong identity is embarrassing in a way a
crash is not, and it is invisible until a buyer replies.

### Open questions

Only these remain. Everything else is settled in §5.

| # | Question | Needed by | Owner |
|---|---|---|---|
| O6 | **How does add-account force a fresh OLX login on Android?** Take the first that works: a force-reauthentication parameter on the authorize URL (best — keeps the system browser, probably unsupported), OLX's own logout URL loaded in the Custom Tab immediately before the authorize URL (standards-clean, we never see a password, but it signs the seller out of OLX in their browser), or an in-app WebView (works, but hosts an OLX password field in our process against RFC 8252 §8.12, degrades autofill, and OLX may refuse to serve the page). iOS is settled: an ephemeral `ASWebAuthenticationSession` on the add-account path only. If it comes down to the WebView, that is a security-posture decision and needs an explicit yes | Phase 0, before any add-account work | Product + Eng |
| O7 | Two real OLX accounts on one country for P0 and QA (Q2–Q4, Q11, Q14), and confirmation of the one-attempt-per-sitting login budget | Before Phase 0 | Product |
| O1 | What is the actual demand? Support tickets, reviews, and how many users currently cycle disconnect→connect | Sets priority, does not gate the build | Product |
| O5 | If a seller has a business and a private account, should the app suggest a default per category? | Post-release, on evidence. The data model supports it without change | Product |

---

## 16. Follow-ups

1. **SIR-84 — accounts across OLX countries.** Directly unblocked by this release's
   `(country, account)` key.
2. **A per-listing publish target** independent of the active account (D2), if M4 shows sellers
   retargeting often enough to want a listing-scoped choice. Needs draft binding persisted across
   process death, an acknowledgement flow when the bound target disappears, and per-request account
   targeting in the HTTP layer.
3. **A per-account or merged My Ads view** (D8). A merged feed means N paginated calls interleaved by
   date, with per-account rate limits and partial-failure states, for a view whose main job — "did my
   listing go up?" — is answered fine per account.
4. **Cross-posting** one listing to several accounts. Only on evidence; needs partial-failure
   semantics and per-account duplicate detection.
5. **Token storage hardening** (Keystore / Keychain), now that a device can hold three sets of
   credentials instead of one (§12). Related: the two open `BUGS.md` items — the `client_secret` in
   the binary and the hijackable `selolxai://` redirect scheme — whose blast radius scales with
   account count.
6. **Remote feature flags**, so a feature of this blast radius can be switched off without a store
   release.
7. **User-set account nicknames**, if two accounts with the same profile name turns out to be common.
8. **Per-account defaults** (location, contact preference), if O5 turns up demand.

---

## Appendix — implementation touchpoints

Non-binding, for estimation only. The TRD owns the design.

| Area | Today | Change implied |
|---|---|---|
| Token storage | `OlxTokenStore` — one `tokens` blob | `OlxAccountStore` — keyed account set + per-country active pointer, mutations serialized |
| HTTP client | `OlxHttpClientFactory` binds one token store via the Ktor `Auth`/`bearer` plugin | Same client and plugin; token resolution reads the active account, and the provider's cache is cleared at a single choke point on every change of active account |
| Refresh | Two writers: `OlxAuthRepository.refreshIfNeeded()` and the plugin's `refreshTokens` | One writer, single-flight per account, plus the keep-alive pass (D1) |
| Terminal refresh failure | `handleTerminalRefreshFailure` clears the single store | Marks only the affected account `NeedsReconnect` |
| Startup routing | `AppNavigationViewModel.sessionDestination()` calls `users/me` and routes any failure to the landing screen | Reads the stored account set (D7) |
| Profile session state | `isGuest` derived as `user == null` | Explicit session state, independent of a fetch result |
| Country | `OlxCountryStore` + a global current-country read by `OlxConfig` | Unchanged this release; the account key includes country |
| Pending auth session | `OlxAuthSessionStore` — one session, state-validated | Unchanged, plus a persisted cooldown and failure counter |
| Profile | `SellerAccountRepository` exposes one `OlxUser` | Accounts list + active pointer; the only writer of the active pointer |
| Publish | `PreviewAdViewModel` reads the contact name from `users/me` just before posting | Same call, now also the identity assertion (D6) |
| My Ads | `MyAdvertsRepository` follows the single token | Follows the active account; in-flight loads discarded on switch |
| Reference data | `CategoriesRepository`, `CurrencyRepository`, `LocationRepository` | **No change** — country-scoped, not account-scoped |
| Guest mode | `GuestModeStore` | Unchanged |
