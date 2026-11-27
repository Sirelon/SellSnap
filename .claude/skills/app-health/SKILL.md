---
name: app-health
description: >-
  Check how SellSnap is doing: crashes and errors, and how sellers use the app (login,
  listings, publishing, copy-paste, buyer chat). Use when the owner says "app health", "how is
  the app doing", "how do people use the app", or asks about crashes or usage.
---

# App health

Two questions, one report: **is the app broken** and **how do sellers use it**. Every number
comes from a query run in this session; the queries live in [queries.md](queries.md).

## Data rules

- BigQuery project `sellsnap-6e85c`. Run `bq query --project_id sellsnap-6e85c --use_legacy_sql=false`.
  SQL must not start with `--`, which bq reads as a flag.
- Usage comes from the view `analytics_538260747.humans_events`: all events minus Play
  pre-launch robots. Installs that are still robots slip through sometimes: one-session
  Android installs in the US, Canada or Korea, model `IN2025`, or a null model on a single
  day. US iPhone 16 Pro-class installs that loop SellerLanding ↔ OlxCountryPicker are App
  Review. Name them as such and leave them out of seller counts.
- Crashes come from `firebase_crashlytics.com_sirelon_sellsnap_{ANDROID,IOS}_REALTIME` (30-day
  window). iOS non-fatals all share the title `FirebaseAnalyticsImpl#recordException`; the
  real error is the `KotlinException` custom key, and `issue_id` splits them apart.
- Owner and test devices: an Android emulator reports `sdk_gphone*` in Crashlytics and a null
  model in Analytics. The owner's own phones are not recorded yet: ask him once which rows
  are his and write their `user_pseudo_id` prefixes here.

## Steps

1. **Pull `main`** (`git pull --rebase`), so the fix check in step 3 sees the latest work.
2. **Run every query in queries.md** for the last 14 days.
3. **Match each crash and error to a fix.** For each Crashlytics issue: read the message, find
   the code it points at, and check `git log` since the last release bump
   (`git log --oneline -- version.properties`) for a fix. Classify it as `fixed on main`,
   `fixed in a release` (name the version), `open bug`, or `not ours` (no Play Store,
   network, App Attest). An open bug gets its causal chain in one line.
4. **Close fixed issues** in Crashlytics right away; the regression email is the safety net.
   PATCH `https://firebasecrashlytics.googleapis.com/v1alpha/projects/186709313778/apps/<appId>/issues/<issueId>?updateMask=state`
   with body `{"state":"CLOSED"}`, a `gcloud auth print-access-token` bearer and header
   `x-goog-user-project: sellsnap-6e85c`. App ids: Android `1:186709313778:android:934749849d0b3b9dfc286b`,
   iOS `1:186709313778:ios:62e5f879f0dc1a83fc286b`. Leave `not ours` and expected shown
   errors (`photos_unusable`) open.
5. **Read the seller table as stories.** One row is one install. For each real seller, say in
   one line what they do: connect OLX and publish, generate and copy the text into OLX by
   hand, chat with buyers, or stop somewhere (login quit, no generation, failed publish).

Done when every crash issue has a class, every fixed one is closed, and every seller row
with activity has its one-line story.

## Report

Open with one line: is anything broken for sellers right now. Then, in this order:

1. **Breakage**: open bugs with seller count, and what was closed.
2. **Sellers**: how many real sellers were active, and their stories grouped by pattern
   (publishers, copy-paste users, chatters, the ones who stopped and where).
3. **Funnel**: login started → finished, generation started → succeeded, publish started →
   succeeded, with counts of sellers, not events.
4. **Versions**: which versions real sellers run, and how many are behind the latest.

Each number appears once. End with the one question the data cannot answer, if any.
