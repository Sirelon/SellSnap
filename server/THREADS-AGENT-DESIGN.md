# SellSnap Threads agent — design

Settled in the 2026-10-04 design session and revised after an independent review on 2026-10-05.
This is the contract the implementation follows. Repo rules in [AGENTS.md](../AGENTS.md) apply
on top; nothing here repeats them. Work is tracked under Linear epic
[SIR-139](https://linear.app/sirelon/issue/SIR-139) (§8).

## 1. Purpose and scope

An autonomous service that promotes SellSnap on Threads in Ukrainian and Polish, with the
owner approving from his phone. Round one is Threads only, two brand accounts plus one private
staging account, four weeks from the first brand-account post. Other platforms and languages
come after the round-one bar in §10 is met.

Baseline at design time: about a dozen real users in the previous 90 days, all in Ukraine and
Poland (Firebase Analytics BigQuery export, 2026-10-04). The agent builds an audience from
zero; it does not amplify an existing one.

## 2. Settled decisions

| # | Decision |
|---|---|
| D1 | Graduated autonomy: every draft is approved by the owner; an account flips to auto-publish after 20 consecutive approved broadcast posts with no edits; engagement replies and replies under own posts never graduate. |
| D2 | Control surface is a private Telegram bot locked to the owner's user id. Posts may use Threads polls. |
| D3 | Brand accounts only, one per language: Ukrainian, Polish, plus one private staging account. No founder account automation, no persona accounts. |
| D4 | Broadcast from day one; engagement (keyword search and replies to strangers) after Meta App Review clears. The review is filed once the staging account has ten posts. |
| D5 | Platform: Threads via the official Threads API. |
| D6 | Markets: Ukraine and Poland first. One account per language, never mixed. |
| D7 | Pillars: demos, selling tips, interactive (price-guess polls, title picks), product updates. Demos call the real generation pipeline on owner-shot photos. Real user data is never a source, anonymized or not. |
| D8 | No video in round one. |
| D9 | Budget ceiling 20 USD per month, reported weekly. Threads API is free. |
| D10 | Guardrails in §5. |
| D11 | Runtime: Kotlin service in `server/`, Cloud Run scaling to zero, two Cloud Scheduler jobs (hourly tick, Sunday batch) authenticated with OIDC, Firestore queue, Telegram webhook verified by secret token. The LLM drafts; publishing, scheduling, token refresh and guardrails are code. |
| D12 | A Claude model drafts posts and replies; gpt-4.1 produces demo listings because that output is the product. |
| D13 | Weekly batch: Sunday 09:00 Kyiv time the agent drafts the next 7 days for every account; the owner approves in one sitting. Two slots per day, 08:30 and 19:30 local (Europe/Kyiv, Europe/Warsaw). Slot 1 is a broadcast post; slot 2 is a reply, a poll or a poll reveal and stays empty when nothing eligible exists. At most two broadcast posts per account per day. Launch week uses both slots until the account has ten posts. |
| D14 | Approval message: rendered preview, buttons Approve / Edit / Skip / Regenerate. Edit opens a reply prompt and only a Telegram reply to that prompt replaces the text. Unapproved scheduled posts roll to the next free slot and expire when the next batch runs. Engagement replies expire unsent after 6 hours. Bot speaks English; drafts appear in their own language. A `/post <account>` command lets the owner type a post by hand. |
| D15 | Attribution: one redirect URL per linked post, served by this service, cookie-free, logging the click and forwarding to Play or the App Store with campaign parameters keyed by a short post code (§3.1). The landing page stays the bio link. |
| D16 | Engagement rules in §6.3. |
| D17 | Polish drafts carry an English back-translation and pass a naturalness check before reaching the owner. |
| D18 | Demo photo set: about 30 household items photographed once by the owner. |
| D19 | Round-one bar in §10. |
| D20 | Handles `sellsnap.ua` and `sellsnap.pl`, fallback `sellsnap_ua` and `sellsnap_pl`. Bio copy is approved as an English key-to-text list before account creation. |
| D21 | Fixed weekly template (§6.1), variety inside each slot. |
| D22 | Demo anatomy: carousel of the raw photo and a card rendered by the server from the unedited pipeline output; one-line hook as caption. A pipeline refusal drops the run. |
| D23 | Only demo and product-update posts carry a link, and never the first five posts of a new account. |
| D24 | Graduation mechanics: Edit resets the counter, Skip does not. In auto mode the owner still receives every preview with a Delete-before-slot button and, after publishing, a Delete button. Any Delete returns the account to approval mode and resets the counter. One command does the same. |
| D25 | Replies under own posts: drafted only for questions and problem reports; emoji and praise ignored; nothing hidden or deleted; every draft approved. No auto-likes. |
| D26 | No automated following, ever. |
| D27 | Weekly digest content in §7. |
| D28 | Voice: the in-app voice from `strings.xml`, emoji allowed, competitors never named, demo prices always a range. Ukrainian never uses broadcast or TV wording for publishing. |
| D29 | Build in four phases (§8), each usable alone. |
| D30 | Every code path posts to the staging account before a brand account. |
| D31 | This doc plus epic SIR-139 with one ticket per phase. Independent Fable review of this doc before code (done 2026-10-05, findings folded in). |
| D32 | Owner hand-work and timing in §11. |
| D33 | Owner time: about 20 minutes on Sunday plus taps during the week. Less than that and the template drops to five posts a week per account. |
| D34 | New-account ramp (Inferred: Meta's new-account spam thresholds are not published): the staging account is created first, brand accounts at least a week later; no links in an account's first five posts; the same photo never appears on both brand accounts within 7 days; the tick reads the account's publishing limit before posting. |
| T1 | Static keys (Claude, OpenAI, Telegram) in Secret Manager; rotating Threads tokens per account in a Firestore collection readable only by the Admin SDK. |
| T2 | Public images in the existing Firebase Storage bucket under `social/`, public read on that prefix only, Admin SDK writes only. Live Storage rules are pulled into `storage.rules` first. |
| T3 | `OpenAIClient` and its models move to `shared/`; the prompt takes country name, language and currency only, decoupled from `OlxCountry`, which embeds per-country OLX client credentials and a composeApp expect/actual. Separate commit. |
| T4 | Transient errors retry 3 times with backoff inside the request. A non-transient 4xx or a moderation flag pauses the account and alerts the owner. Cloud Monitoring alerts on Cloud Scheduler failure. A token within 10 days of expiry alerts the owner. The Threads container id is stored before publish so a retry never double-posts. |
| T5 | Daily per-post insight snapshots, redirect clicks and spend in Firestore, kept indefinitely, Admin-only collection. |
| T6 | A price-guess poll and its next-day reveal are approved together as a pair. |
| T7 | People the agent replied to are stored as a hashed Threads user id with a 30-day TTL, nothing else. |

Decision log lines for T1–T5 are in `.claude/decider/decisions.jsonl`.

## 3. Architecture

### 3.1 Components

- **Tick** — Cloud Scheduler job `30 * * * *` (both markets sit at whole-hour UTC offsets with the same DST dates, so 08:30 and 19:30 local land on a tick) calls `POST /tick` with an OIDC token; the handler verifies audience and the scheduler service account before doing anything. The tick reads each account's publishing limit, publishes due approved posts, refreshes tokens older than 50 days, pulls insights for posts younger than 14 days, scans for engagement candidates (Phase 4), and expires stale drafts and replies.
- **Batch** — a second Cloud Scheduler job, `0 9 * * 0` in `Europe/Kyiv`, 30-minute attempt deadline, calls `POST /batch` with the same OIDC verification. The Cloud Run service timeout is raised to 30 minutes for this route. A batch is keyed by ISO week in `social_batches/{isoWeek}` and writes one post document per slot as it goes, so a timeout or crash resumes where it stopped on the next invocation instead of starting over.
- **Telegram webhook** — `POST /telegram`, registered with `setWebhook` and a `secret_token`; requests whose `X-Telegram-Bot-Api-Secret-Token` header does not match are rejected before parsing. Only updates from the owner's user id are processed. Every processed `update_id` is persisted and duplicates are ignored, because Telegram redelivers on any non-2xx and owners double-tap.
- **Redirect** — `GET /go/{code}` logs the click and 302s to the store chosen by User-Agent. `code` is a short post code: language, pillar letter, four-digit sequence, for example `pld0042` (`d` demo, `t` tip, `p` poll, `k` title pick, `u` update, `r` reveal). Play receives `referrer=utm_source%3Dthreads%26utm_medium%3Dsocial%26utm_campaign%3D{lang}%26utm_content%3D{code}`; the App Store receives `pt={providerToken}&ct={code}` (Apple caps `ct` at 30 alphanumeric characters). Unknown agents go to the landing page. Round one serves from the Cloud Run `run.app` URL; a custom domain is an open item (§9).
- **Drafter** — builds the brand context (voice samples from `values/strings.xml`, `values-uk`, `values-pl`; feature list and independence line from `store/copy/store-listing.md`; release notes from `scripts/release-notes.json`; guardrails) and asks the Claude model for the week's drafts per account, then for engagement replies. A second model pass checks every draft against the feature list (guardrail 1) and Polish drafts for naturalness (D17).
- **Demo runner** — picks a photo from `social/photos/` not used on either brand account in the last 7 days, calls `analyzeThing` from `shared/` with the account's country, renders the card, uploads both images under `social/renders/`.
- **Publisher** — Threads two-step publish: create container, store its id on the post, publish, store the media id. Carousels create item containers first. The status move `approved → publishing` is a conditional Firestore transaction, so two overlapping ticks cannot both publish.
- **Insights** — Threads media insights per post (views, likes, replies, reposts, quotes) and poll results.

### 3.2 Firestore collections (Admin-only; clients are default-denied)

- `social_accounts/{accountId}` — handle, language, country, timezone, mode (`approval` | `auto`), approvalStreak, paused, pausedReason, threadsUserId, token, tokenExpiresAt, permissionsExpireAt (private profiles, §3.6), postSeq.
- `social_posts/{postId}` — accountId, code, pillar, kind (`text` | `image` | `carousel` | `poll`), text, imagePaths, poll options, scheduledAt, status (`drafted` | `pending` | `approved` | `skipped` | `publishing` | `published` | `failed` | `deleted`), skipReason, containerId, mediaId, telegramMessageId, editedByOwner, needsAttention, pairedPostId (poll reveal), link, createdAt.
- `social_replies/{replyId}` — accountId, targetMediaId, targetUserHash, targetText, draft, mentionsApp, expiresAt, status, mediaId.
- `social_batches/{isoWeek}` — startedAt, finishedAt, slotsWritten, lastError.
- `social_updates/{updateId}` — processed Telegram update ids, 7-day TTL.
- `social_clicks/{clickId}` — code, ts, store, countryFromIp, uaFamily.
- `social_metrics/{postId}_{yyyymmdd}` — the insight snapshot.
- `social_spend/{yyyymmdd}` — model tokens and USD per vendor.
- `social_events/{eventId}` — pauses, resumes, mode changes, deletes, alerts, token refreshes, errors.

### 3.3 Weekly batch flow

1. Expire every still-`pending` post from the previous batch (`skipped`, reason `expired`), so an offline owner's week does not pile onto the next.
2. For each active account build 7 days of slots from the template (§6.1).
3. Demo slots run the demo runner first; a refusal retries once with another photo, then the slot becomes a tip.
4. The drafter writes every slot's text in the account language. Each draft passes the feature-list check; Polish drafts also get a back-translation and a naturalness pass. A failed pass regenerates once, then the draft is sent anyway with a visible "needs attention" label.
5. Each draft is sent to Telegram as one message with its preview: carousels as a media album followed by a text message that carries the buttons, because albums cannot carry inline keyboards. Status `pending`. Poll and reveal pairs are sent back to back and approved separately but published only if both are approved.
6. Approve → `approved`. Edit → the bot sends a reply prompt naming the post code; only a Telegram reply to that prompt replaces the text, sets `approved` and `editedByOwner = true`; any other text is answered with a hint and ignored. Skip → `skipped`. Regenerate → a new draft replaces the message. All transitions are conditional transactions from the expected previous status.
7. In `auto` mode the same previews are sent with a Delete-before-slot button instead of Approve, and the post is created as `approved`.
8. The digest message closes the batch.

### 3.4 Publish flow

A tick takes every `approved` post whose `scheduledAt` has passed, moves it to `publishing` in a transaction, creates the container, saves `containerId`, publishes, saves `mediaId` and `published`. The owner's preview message gains a Delete button; a Delete calls the Threads delete endpoint, sets `deleted`, returns the account to `approval` mode and resets the streak. Any non-transitory failure sets `failed`, pauses the account, writes `social_events`, alerts.

### 3.5 Graduation

`approvalStreak` increments on Approve without edit, resets on Edit or on any Delete, unchanged on Skip. At 20 the account's mode becomes `auto` and the owner is told. The Telegram command `/approval <account>` returns an account to approval mode and resets the streak. Replies never consult the mode.

### 3.6 Token and permission lifecycle

Threads long-lived tokens are valid 60 days and refreshable once at least 24 hours old and not yet expired; an unrefreshed token expires for good. Permissions granted by app users with private profiles are valid for 90 days, after which the owner must re-authorize (Threads docs, Long-Lived Access Tokens, fetched 2026-10-05). The staging account is private, so it needs owner re-authorization every 90 days; the tick alerts 10 days ahead. The tick refreshes any token older than 50 days and alerts at 10 days before expiry if a refresh keeps failing. Scopes requested for every account: `threads_basic`, `threads_content_publish`, `threads_manage_replies`, `threads_manage_insights`, `threads_delete`; after App Review also `threads_keyword_search` and `threads_manage_mentions`.

### 3.7 Phase 1 fixtures

- GCP project `sellsnap-6e85c`. One service account for the service with Firestore user, Storage object admin scoped to the bucket, and Secret Manager accessor; one for Cloud Scheduler with Cloud Run invoker. The service is publicly reachable (the redirect and webhook routes need that); `/tick` and `/batch` verify the Scheduler's OIDC token in code.
- Hostname: the Cloud Run `run.app` URL for round one.
- Tester tokens: the owner adds the three accounts as Threads testers in the Meta App Dashboard's Threads use case and generates their long-lived tokens from the dashboard's token generator; the tokens are entered once through a `/token <account>` Telegram command and stored per T1. The dashboard path is confirmed during Phase 1.
- Content before the drafter exists: a seed list of ten tips per language approved as an English key-to-text list and translated, loaded as `drafted` posts; plus the `/post <account>` command for owner-typed posts.
- Telegram previews: single image as a photo message with buttons; carousel as an album followed by a buttons message; poll as text showing the options.

## 4. Threads API facts relied on

Fetched 2026-10-04 and 2026-10-05 from developers.facebook.com/docs/threads.

- Post types TEXT, IMAGE, VIDEO, CAROUSEL. Text limit 500 characters. 250 published posts per profile per 24 hours. Images must be reachable by public URL. The account's remaining quota is readable through the publishing-limit endpoint.
- Polls: `poll_attachment` with `option_a`–`option_d`, 2 to 4 options, each option 1 to 25 characters, text-only posts, results readable on the media object.
- Deleting own posts is supported since 2025-03-06 (changelog) under the `threads_delete` permission; the troubleshooting page lists a delete quota of 100 per 24 hours (reviewer-fetched).
- Posting to accounts added as app testers needs no App Review. Keyword search and replying to posts the account does not own need `threads_keyword_search` or `threads_manage_mentions`, which need App Review; without it search covers only the account's own posts. Approved search quota 2,200 queries per 24 hours; 1,000 replies per 24 hours.
- Tokens: §3.6.
- Meta Developer Policies: "Don't confuse, deceive, defraud, mislead, spam or surprise anyone." Meta's spam standard covers posting "either manually or automatically, at very high frequencies."

## 5. Guardrails

Guardrail 1 is a second-model check; everything else is code.

The agent never:

1. claims a feature the app does not have — every draft is checked against the feature list in `store/copy/store-listing.md` by a second model pass, and a flagged draft is marked "needs attention" and can never be auto-published;
2. implies OLX affiliation — every linked post carries the independence line, unconditionally; 500 characters always fit hook, link and line;
3. emits russian text anywhere;
4. reads or shows user photos, listings or `ad_generations` documents;
5. sends DMs;
6. operates persona or sockpuppet accounts;
7. buys followers or engagement;
8. continues on an account after an API rejection, content flag or moderation warning — it pauses and alerts;
9. argues with a negative reply — those are drafted for the owner and never auto-sent;
10. follows, bulk-likes or bulk-interacts;
11. stores anything about third-party users beyond a hashed id with a 30-day TTL;
12. puts a link in an account's first five posts (D34);
13. posts the same photo on both brand accounts within 7 days (D34).

## 6. Content system

### 6.1 Weekly template

| Day | Slot 1 (08:30) | Slot 2 (19:30) |
|---|---|---|
| Mon | Demo | reply or poll |
| Tue | Selling tip | reply or poll |
| Wed | Price-guess poll | reply |
| Thu | Demo | price-guess reveal |
| Fri | Selling tip | reply or poll |
| Sat | Title pick | reply |
| Sun | Product update, or a demo if there is no release | reply or poll |

Slot 2 stays empty when nothing eligible exists; before App Review it holds only polls and reveals. Launch week: both slots are broadcast posts until the account has ten posts, none of the first five with a link.

Price-guess poll: text names the item, options are four price ranges in the local currency, each at most 25 characters. Reveal: a text post the next day with the range the pipeline produced and a link.

Title pick: a text post showing two generated titles labelled A and B, with a two-option poll whose options are "A" and "B".

### 6.2 Demo post

Image 1: the raw photo. Image 2: a card with the generated title, the price range with currency, and the first lines of the description, rendered server-side in the app's palette from `composeApp/.../designsystem/AppThemeColorMapping.md`. Caption: one-line hook in the account language, the redirect link, the independence line. The output is never edited; a refusal (`Unusable`) drops the run.

### 6.3 Engagement (Phase 4)

Seed queries per language cover selling on OLX, pricing an item, and writing a listing; the lists are owner-approved before use. A candidate is eligible when it is under 24 hours old, reads as a question, has fewer than 20 replies, and is not from a business. The reply answers the question first. The app is mentioned in at most one reply in three, and only when the question is about writing or pricing a listing. Never two replies in one thread; never the same user within 30 days. Every reply is owner-approved and expires after 6 hours; an expired reply is never published even if approved later.

### 6.4 Voice

The in-app voice, as written in `strings.xml`: playful, conversational, emoji-friendly. Competitors are never named. Demo prices are always a range.

## 7. Measurement and digest

Per linked post: Threads insights, redirect clicks by store, and on Android the Play install-referrer campaign on `first_open` in Firebase Analytics. iOS installs are not attributable here: App Store campaign tokens appear only in App Store Connect and only once a campaign reaches Apple's minimum of five (Apple help, reviewer-fetched). Sunday digest per account: follower change, total views and replies, top three and bottom three posts by views with clicks, attributed Android installs, errors, pauses and mode changes from `social_events`, model spend against the 20 USD ceiling.

## 8. Build phases

Four phases, each usable on its own, tracked in Linear under epic
[SIR-139](https://linear.app/sirelon/issue/SIR-139). The tickets hold the build lists and
acceptance criteria; this file holds the design they point back to. Phases 2 to 4 are blocked
by Phase 1.

| Ticket | Phase | Outcome |
|---|---|---|
| [SIR-140](https://linear.app/sirelon/issue/SIR-140) | 1 | Telegram bot, Firestore queue, Threads publishing, token refresh, redirect links, seed tips. Accounts go live with tips and polls. |
| [SIR-141](https://linear.app/sirelon/issue/SIR-141) | 2 | Weekly drafting batch, feature-list and Polish checks, spend tracking, Sunday digest. |
| [SIR-142](https://linear.app/sirelon/issue/SIR-142) | 3 | OpenAI client moved to `shared/` in its own PR, then demo runner, card renderer, carousel posts. |
| [SIR-143](https://linear.app/sirelon/issue/SIR-143) | 4 | Engagement: keyword scan, reply drafts, own-post comments. Enabled per account after App Review. |

## 9. Open items

- Meta App Review turnaround for `threads_keyword_search`; undocumented. Tracked as a risk, not part of the round-one bar.
- Whether App Review for keyword search needs Business Verification.
- App Store provider token for campaign links.
- Custom domain for the redirect and webhook routes; round one uses the `run.app` URL.
- Exact dashboard path for tester token generation (§3.7).

## 10. Round-one bar

Four weeks from the first brand-account post:

- zero owner interventions, defined as no pause, resume, `/approval` or Delete events in `social_events` (approval taps and edits do not count);
- each brand account published at least 25 posts;
- redirect clicks logged for every linked post;
- at least one Android install attributed through the Play install referrer.

Missing the bar means fixing the loop, not adding a platform. App Review status is reported alongside but is Meta's decision, not the loop's.

## 11. Owner hand-work

| What | When |
|---|---|
| Staging Instagram + Threads pair (private profile) | before Phase 1 is testable |
| Brand pairs `sellsnap.ua`, `sellsnap.pl` | at least a week after staging, before the first brand post (D34) |
| Meta developer app with the Threads use case, the accounts as testers, their long-lived tokens entered via `/token` | before Phase 1 is testable |
| Telegram bot via BotFather; bot token and owner user id | before Phase 1 is testable |
| Approve bio copy, the ten seed tips per language, and the engagement seed queries | before account creation / before Phase 1 launch / before Phase 4 |
| About 30 photos of household items | before Phase 3 |
| Submit Meta App Review with the prepared text | the day staging has ten posts |
| Re-authorize the private staging account | every 90 days, on the bot's reminder |
