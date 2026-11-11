# AGENTS

Repo map and rules for agents working in SellSnap. They apply to every task unless the owner
overrides them.

## Rules

Each one is checkable. Where you cannot show it was met, say so (rule 7).

1. Read before you write: exports, callers, shared utilities, and the rule file for the
   area (Area rules). State assumptions. A decision that is not yours — product shape, user-facing copy,
   what a user sees — is asked, in one line, with your recommended answer.
2. Minimum code that solves the task. No speculative abstractions, no touching adjacent code,
   match the existing style even where you disagree.
3. External facts carry their source. Anything asserted about OLX — a status meaning, a
   restriction, a market difference — cites portal and section in the KDoc next to the code.
   Everything else is marked `Inferred:`. See `.claude/rules/olx-api.md`.
4. **Never invent a limitation.** Withhold a user action only on a documented refusal; if the
   API does not say no, offer it and let the API's own error speak. A missing button is
   invisible in telemetry, and every restriction this app invented cost the seller something.
5. A user-facing state, label, or paragraph exists only if the user does something different
   because of it. Two API values the user treats the same way are one state.
6. Sentences, explanations, new states and legal text are approved before they are translated:
   post the English `key → text` list, wait, then run `localize` once. A short label that names
   its action ("Mark as read", "Retry") needs no approval: use it, translate it in the same pass,
   and list it in the report. See Localization.
7. Report what was skipped. "Completed" with a silent gap is wrong; name the gap.
8. Name the cause before the second fix. If a symptom survives a fix, write the causal chain —
   what was observed, what produced it — before touching code again.
9. A gotcha that cost more than one attempt is written into this file, or the matching
   `.claude/rules/` file, in the same commit as the fix.
10. **Read the source, not a summary of it.** A schema, a status list, a limit: quote it from
    the spec file, fetched and opened and searched. A tool that answers in prose has already
    dropped whatever you did not know to ask about. Three fixes in a row were refused by OLX
    because each came from a paraphrase rather than from the schema. See `.claude/rules/olx-api.md`.
11. **Every feature works on every window size and for every connected account.** SellSnap runs
    on phones, foldables, tablets and desktop, and a seller can connect several OLX accounts per
    country (SIR-83). A plan for a new screen says how it lays out on a wide window — a list
    whose rows open a detail uses list-detail panes (Messages is the reference) — and how it
    behaves with two or more accounts: one page per account like My Ads, and every OLX call
    carries the `localIndex` it acts for instead of assuming the active account. A ticket that
    says "active account only" or says nothing about tablets is not an exemption; raise it
    before building.
12. **Every feature ships with the analytics to judge it.** Before building, the plan lists the
    questions the data must answer — is it used, does it work, where do sellers stop, which
    option they pick — and the events that answer them. Events and their params go in
    `AnalyticsEvents` with a KDoc per event (what fires it, every param and its values), codes
    in snake_case, never user text or buyer data. Screen views are already automatic; count
    the actions and outcomes inside the screen. See `.claude/rules/analytics.md`.

Small fixes with obvious files: read the implementation and its immediate callers only,
validate with the narrowest meaningful build or test command plus `git diff --check`, and skip
anything above that does not apply.

## Git

- In a worktree (Conductor, Paseo or any `git worktree`), work on a branch: commit there, push
  the branch and open a PR against `main`. Never push a worktree's commits to `main` directly.
- On a checkout of `main` itself, commit and push to `main` once the change compiles. Create a
  branch or a PR there only when the owner asks.
- Name a new branch `feature/SIR-XX-short-description` or `bugfix/SIR-XX-short-description`.
- Commit subject: one imperative sentence that says what changed, with no ticket prefix. When the
  commit implements a Linear ticket, name the ticket id in the body.
- Compile once before pushing when Kotlin or Gradle files changed
  (`./gradlew :composeApp:compileAndroidMain`). Commits that touch only docs, skills or config
  need no compile.

## Linear ticket status after merge
Agents merge finished work into the default branch through its PR. The owner later installs the fresh
build and checks every pending ticket in one pass.

- **Human Check** — the change has behaviour a person must see on a device: UI, flows, copy,
  notifications, store or ads behaviour, anything that only shows in a real install. Add a
  comment with numbered check steps and the expected result for each.
- **Done** — only when the build and tests fully prove the change: refactors, docs, tests,
  build config.
- Never move a ticket you implemented to Done when it needs a device check. The owner moves it
  from Human Check to Done after checking.

## Subagent limits (append to every brief, after the global block)

> No russian text in any file. Never commit `screenshotMode = true`. Never attempt an OLX
> login — accounts are banned after repeated failures; the owner logs in himself. Never call
> anything that mutates the owner's OLX account (commands, PUT, DELETE, packets).

## Repo map

| Path | What it is |
| --- | --- |
| `composeApp/` | Shared Compose Multiplatform module: app shell, navigation, feature UIs and view models, design system, Koin wiring, Ktor clients. Targets Android, iOS, Desktop JVM, Web JS, Web Wasm. It is an Android KMP library, not the app, so it has no `assembleDebug` |
| `androidApp/` | Thin Android wrapper: `MainActivity`, the push service, OLX auth deep links |
| `iosApp/` | Xcode entry point, a SwiftUI wrapper around the shared framework |
| `shared/` | Config and platform helpers used by app and server: BuildKonfig secrets, the Supabase client, `PlatformTargets` |
| `server/` | Minimal Ktor JVM backend. App business logic does not live here |
| `functions/` | Firebase Cloud Functions (TypeScript, Node 22), outside the Gradle build: the `openai` proxy |
| `docs/` | The public website; GitHub Pages serves it from `main` (landing page, privacy policy, terms, support). Documentation goes elsewhere |
| `store/`, `brand/`, `Design/` | Store listing text and images, brand sources, store banner sources |
| `research/` | Spike write-ups that code comments cite as their source |
| `.maestro/`, `scripts/` | UI flows and their runner scripts; `scripts/ship.sh` is the release script |
| `fastlane/` | Store upload lanes |

- Gradle projects: `:composeApp`, `:androidApp`, `:server`, `:shared`. Version catalog: `gradle/libs.versions.toml`.
- **Directories say `com/sirelon/aicalories/`; the Kotlin package is `com.sirelon.sellsnap`.** A path in this file that starts with `features/`, `navigation/`, `designsystem/`, `startup/` or `di/` is relative to `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/`. Analytics is the exception: `composeApp/src/commonMain/kotlin/com/sirelon/sellsnap/analytics/`.
- Source sets beyond the usual ones: `dataStoreMain` (Android, JVM, iOS: datastore and SQLDelight) and `jsWasmMain` (JS + Wasm). `kotlin.mpp.applyDefaultHierarchyTemplate=false` is set on purpose.
- Entry points: `App.kt` (root composable), `androidApp/.../MainActivity.kt`, `iosApp/iosApp/iOSApp.swift`, `composeApp/src/jvmMain/.../main.kt`, `composeApp/src/wasmJsMain/.../main.kt`, `server/.../Application.kt`, `functions/src/index.ts`.
- Xcode builds the shared framework through `:composeApp:embedAndSignAppleFrameworkForXcode`; the build phase skips it when `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`.

## Commands

- Compile check (fast): `./gradlew :composeApp:compileAndroidMain`
- Unit tests: `./gradlew :composeApp:jvmTest` (the shared tests run on the JVM target)
- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Desktop run: `OPENAI_KEY=sk-... ./gradlew :composeApp:run` (desktop calls OpenAI directly)
- Deploy the OpenAI proxy: `cd functions && npm install && npm run deploy`
- Install on a device: the `install-mobile-app` skill. Release: the `sellsnap-release` skill.

## Conventions

- Shared logic lives in `composeApp/src/commonMain`; platform code lives in `androidMain`, `iosMain` and the other platform source sets.
- Business logic lives in view models or domain services. Composables and navigation render and route.
- Every view model extends `BaseViewModel<State, Event, Effect>` (`features/common/presentation/BaseViewModel.kt`) with its contract in `*Contract.kt`. A feature is some of `data/`, `di/`, `presentation/`, `ui/`, `model/`.
- Repair a crash or a missing DI binding where it starts; a null check that hides it is a second bug.
- For Android or Google APIs and libraries (Compose, AndroidX, Material, Play services, Firebase), look them up with the Google dev MCP tools.

## Navigation
- `App.kt` is intentionally thin. Do not move app navigation state into composables.
- Destinations are the `AppKey` subtypes in `navigation/AppKey.kt`.
- Top-level back stack ownership lives in `startup/AppNavigationViewModel`.
- Startup routing logic also lives in `AppNavigationViewModel`:
  - splash
  - onboarding gate
  - seller auth/session gate
- If adding new app-level navigation, prefer:
  1. add the destination to `AppKey`
  2. update `AppNavigationViewModel`
  3. register the entry in `App.kt`
  4. register the key in `navigation/AppNavigationSavedState.kt` — the back stack is persisted
     through its explicit `subclass(...)` list, so `@Serializable` alone is not enough

## Dependency injection
- DI framework is Koin.
- Top-level modules are registered in `composeApp/.../di/KoinModules.kt`.
- `appModule` includes feature modules; `networkModule` provides shared networking clients.
- Feature modules typically live in each feature’s `di/` package.
- Prefer adding dependencies via Koin modules, not manual singleton objects.
- When calling constructors or factory functions with more than 2 arguments, use named parameters.

## Design system
- Prefer `AppTheme.typography` and `AppTheme.colors`.
- Prefer `AppDimens` tokens over raw `dp`.
- Add new size tokens only when the exact value matters.
- Design system code lives under `composeApp/src/commonMain/kotlin/com/sirelon/aicalories/designsystem/`.
- Reusable templates already exist in `designsystem/templates/`.
- Avoid reaching for raw Material APIs first when an app component/token already exists.
- Use the custom icons (`ic_*.xml` in `composeResources/drawable/`) when one fits, before a Material icon.

## Localization
- New user-facing strings go in `composeApp/src/commonMain/composeResources/values/strings.xml` (English base). Interpolate with `stringResource(id, arg)`; never `String.format`.
- **English copy is approved before any locale is touched.** When a ticket adds or rewords
  user-facing strings, post the list — one line per key, `key → text` — and wait for the owner's
  answer. Then run the `localize` agent once, with the final key list. One `localize` run per
  ticket is the budget; six runs on one milestone is what this rule exists to stop. Each batch of
  follow-ups the owner orders inside the ticket gets one more run, which carries all of its keys.
- **Then show the Ukrainian.** Ukrainian is the language the owner actually reads and the primary
  market, so after `localize` returns, post the `key → text` list for `values-uk` before calling
  the ticket done. The other locales follow from it and are not posted.
- Copy is written from the seller's side and answers two questions: what is happening to my
  listing, and do I need to do anything. Words that fail: `status`, `moderation`, `verification`,
  `API`, and anything describing how SellSnap talks to OLX ("Changes go straight to OLX"). A badge
  is one or two words the seller sees in their own OLX cabinet. An explanation paragraph exists
  only where the seller must act somewhere else or the marketplace said no; a state whose badge and
  buttons already say everything has none.
- Translation of new or changed keys into every locale is done by the `localize` agent — `.claude/agents/localize.md` holds the locale map and all translation rules. `.claude/rules/localization.md` loads the sequence when you open a `strings.xml`.
- **No russian on any SellSnap surface, by product decision.** `values-ru/strings.xml` is a byte-identical copy of `values-uk/strings.xml`, so a `ru` device falls back to Ukrainian; the website, store listings, store release notes and screenshots have no `ru` at all; the in-app What's New carries `ru` as a copy of `uk`. The why and the sync step: `.claude/agents/localize.md`.

## Where do I edit?
- Add app-level screen/navigation:
  - `navigation/AppKey.kt`, `navigation/AppNavigationSavedState.kt`
  - `startup/AppNavigationViewModel.kt`
  - `App.kt`
- Add a feature dependency or ViewModel:
  - feature `di/*Module.kt`
  - `di/KoinModules.kt` if it is a new top-level feature module
- Change app theme/tokens/components:
  - `designsystem/`
- Change Supabase or secret-backed config:
  - `shared/build.gradle.kts`
  - `shared/src/commonMain/kotlin/com/sirelon/aicalories/config/`
  - `shared/src/commonMain/kotlin/com/sirelon/aicalories/supabase/`
- Change OLX auth behavior:
  - `features/seller/auth/`
- Change Android deep link behavior:
  - `androidApp/.../MainActivity.kt`
- Change web callback behavior:
  - `composeApp/src/wasmJsMain/.../main.kt`
- Change ad publish behavior / publish button state machine:
  - `features/seller/ad/preview_ad/PreviewAdViewModel.kt`
  - `features/seller/ad/data/PostAdvertRequestMapper.kt`
- Change seller profile fetch/edit/logout or publish-blocking contact-name recovery:
  - `features/seller/profile/`
  - `features/seller/ad/AdRootScreen.kt`
  - `features/seller/ad/preview_ad/PreviewAdViewModel.kt`
- Change seller ad flow timing / ready-to-publish elapsed-time behavior:
  - `features/seller/ad/AdFlowTimerStore.kt`
  - `features/seller/ad/ElapsedTimeFormatter.kt`
  - `features/seller/ad/generate_ad/GenerateAdViewModel.kt`
  - `features/seller/ad/preview_ad/PreviewAdScreen.kt`
  - `features/seller/ad/publish_success/PublishSuccessScreen.kt`
- Change AI ad generation pipeline:
  - `features/seller/ad/generate_ad/GenerateAdViewModel.kt`
  - `features/seller/openai/OpenAIClient.kt`
- Change drafts (what is kept, the generate-screen section, the Drafts screen):
  - `features/seller/drafts/`
- Change which OLX top-level categories are user-facing:
  - `features/seller/categories/domain/RootCategoryTaxonomy.kt` (`EXCLUDED_ROOT_CATEGORY_KINDS`, per market)
- Change attribute validation rules:
  - `features/seller/categories/domain/AttributeValidator.kt`
- Change price formatting / thousand-separator behavior:
  - `designsystem/InputTransformations.kt` (`DigitOnlyInputTransformation`, `ThousandSeparatorOutputTransformation`)
- Change what a seller can do to a published listing (deactivate / reactivate / finish / delete /
  extend / edit / statistics / expiry):
  - `features/seller/my_ads/domain/AdvertAction.kt` (which actions each `AdvertStatus` may offer)
  - `features/seller/my_ads/data/AdvertLifecycleRepository.kt` (the OLX calls)
  - `features/seller/my_ads/presentation/MyAdvertsViewModel.kt` (the flow between them)
  - `features/seller/my_ads/ui/AdvertActionsSheet.kt` (the one surface it all renders in)
- Change the sold / not-sold outcome data or the AI price-accuracy measurement:
  - `features/seller/my_ads/data/AdvertOutcomeStore.kt`
  - `features/seller/my_ads/domain/AdvertAnalyticsBuckets.kt`
- Change push notifications (topics, permission, tap target, display):
  - `features/notifications/`
  - `androidApp/.../PushMessagingService.kt`, `PushNotifications.kt`
  - `iosApp/iosApp/iOSApp.swift`
- Change whether a market may extend listings:
  - `features/seller/auth/domain/OlxCountry.kt` (`supportsExtendCommand`)

## Area rules

Each file loads on its own when you open a matching file. Read it directly when you plan work in
its area.

| File | Area |
| --- | --- |
| `.claude/rules/olx-api.md` | OLX partner API: the portals, reading the spec, market differences, auth callback wiring |
| `.claude/rules/ad-lifecycle.md` | What a seller can do to a published listing, `AdvertState`, OLX's eventual consistency |
| `.claude/rules/api-response.md` | Classes that map a JSON response. Review rejects violations |
| `.claude/rules/viewmodels.md` | The view model pattern and the traps of testing it |
| `.claude/rules/analytics.md` | Events, consent, and how a shown error is reported |
| `.claude/rules/localization.md` | The translation sequence for `strings.xml` |
| `.claude/rules/edge-to-edge.md` | Insets for screens and design-system components |
| `.claude/rules/push-notifications.md` | FCM topics, the `link` key, platform specifics, the permission prompt |
| `.claude/rules/announcements.md` | The launch dialog fed by Firestore |
| `.claude/rules/secrets-config.md` | BuildKonfig secrets, App Check, the OpenAI proxy, Supabase |
| `.claude/rules/platform-abstractions.md` | Camera, image conversion, datastore, SQLDelight drafts |
| `.claude/rules/store-assets.md` | What lives where under `store/` and `brand/` |
| `.claude/rules/maestro.md` | Writing Maestro flows |
