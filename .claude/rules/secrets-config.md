---
paths:
  - "shared/build.gradle.kts"
  - "shared/src/commonMain/kotlin/**"
  - "composeApp/src/commonMain/kotlin/**/network/**"
  - "functions/**"
  - "androidApp/build.gradle.kts"
  - "iosApp/Configuration/**"
---

# Secrets, config, Supabase, the OpenAI proxy

- Secrets are resolved in `shared/build.gradle.kts` from:
  - Gradle properties
  - environment variables
  - `local.properties`
- BuildKonfig object:
  - package: `com.sirelon.sellsnap.supabase`
  - object: `SupabaseConfig`
- Fallback defaults exist for local/dev builds; do not mistake them for production values.
- The OpenAI key is not in any build. Android and iOS call the `openai` Cloud Function with a Firebase App Check token (`composeApp/.../network/OpenAIEndpoint.kt`, platform modules `OpenAIEndpointModule.*.kt`); the key lives in Secret Manager as `OPENAI_KEY`. Desktop calls OpenAI directly with `OPENAI_KEY` read from the environment at launch. Web has no App Check and cannot generate listings.
- App Check providers: Play Integrity (Android release), App Attest (iOS release), debug providers in debug builds. A debug build's token must be registered in App Check or the proxy answers 401. Dev machines use fixed tokens: `APP_CHECK_DEBUG_TOKEN` in `local.properties` (Android) and `iosApp/Configuration/AppCheckDebugToken.local.xcconfig` (iOS), both gitignored; the `appcheck-debug-token` skill (`.claude/skills/`) registers them and handles the per-install fallback.
- The `openai` function (`functions/src/index.ts`) is an HTTP proxy in `europe-west1`. It verifies the `X-Firebase-AppCheck` header, allows only `POST /v1/responses` with `gpt-6.1-terra` (listings) or `gpt-5.6-luna` (reply suggestions), and forwards OpenAI's status and body unchanged. Installed app versions that still send `gpt-4.1` get `gpt-6.1-terra` with effort `none` and no `temperature` (`REPLACED_MODELS`); all other request bodies pass through as sent. A new model or endpoint in the app needs the proxy's allow-list changed and deployed first.
- Deploy: `cd functions && npm install && npm run deploy`. The Firebase CLI runs through `npx firebase-tools`. When `firebase login:list` shows a work account as the default, the deploy fails with `iam.serviceAccounts.ActAs`: deploy as the project owner with `npx firebase-tools deploy --only functions --project sellsnap-6e85c --account sasha.sirelon@gmail.com`.
- Set the proxy's key once: `npx firebase-tools functions:secrets:set OPENAI_KEY --project sellsnap-6e85c`.

## Supabase

- Shared Supabase wrapper: `shared/.../supabase/SupabaseClient.kt`
- Current responsibility is seller media upload:
  - auth with default test credentials when needed
  - file upload to Supabase Storage bucket `test`
  - public URL lookup for uploaded listing photos
