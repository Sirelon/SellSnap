---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/seller/auth/**"
  - "composeApp/src/commonMain/kotlin/**/features/seller/**/data/**"
  - "composeApp/src/commonTest/kotlin/**/features/seller/**"
  - "androidApp/src/main/kotlin/**/MainActivity.kt"
  - "composeApp/src/wasmJsMain/**"
---

# OLX partner API

Every market has its own portal — `developer.olx.pl`, `.ua`, `.pt`, `.ro`, `.bg`, `.kz` — and
they are not equally complete. PL omits the advert-status definitions and lists 4 of the 11
`Advert.status` values; UA and PT define all 11. Reading one portal and assuming it speaks for
the rest is how this app shipped two wrong status meanings.

- **Treat `developer.olx.ua` as the source of truth.** Ukraine is the primary market, and its
  portal is one of the complete ones.
- **Before relying on any behaviour, check whether the markets differ.** Read UA plus at least
  one other portal for the same endpoint and compare. Where a difference exists, it belongs in
  code as a per-country capability — `OlxCountry.supportsExtendCommand` is the pattern — never as
  a single global assumption. A feature that works in Poland may be refused in Romania.
- The only market difference found so far is the `extend` command, which the specs annotate as
  unavailable in UA and PT. That is one data point, not a guarantee that the rest is uniform.
- **Read the yaml itself.** It is at `https://developer.olx.<tld>/swagger/v2/partner_api.yaml`,
  nested `$ref` files resolve against the same path, and plain `curl` gets a CloudFront 403.
  `curl https://r.jina.ai/<url>` returns the whole file - about 145 KB - and from there the schema
  is `grep -n` and `sed -n` like any other file. When r.jina.ai refuses the yaml (422), the
  Wayback raw copy works: `curl -sL "https://web.archive.org/web/2026id_/https://developer.olx.ua/swagger/v2/partner_api.yaml"`. WebFetch and context7 answer in prose: they
  summarize, and the summary is where `location`'s real position, the absence of `ad_delivery`
  from the update schema, and the `value`/`values` split all went missing. Each was five lines of
  yaml and each cost a release.
- A request body this app assembles is pinned by a test against the spec's own example payload -
  `AdvertUpdateBodyTest` is the pattern. A body whose only check is installing on a phone is a
  guess with a delay attached.
- Cite the portal and section in the KDoc next to the code that relies on the fact, and mark
  anything you could not confirm `Inferred:` (rule 3). Do not invent a restriction the API does
  not state (rule 4).
- For live probes without a user account — token minting, routing checks — use
  `.claude/skills/olx-api-verify`. Never attempt an OLX login.

## Auth callback wiring

- Default redirect URI: `selolxai://olx-auth/callback` (configurable via `OLX_REDIRECT_URI` BuildKonfig key).
- Android — `MainActivity.publishOlxCallback(intent)` reads `intent.data` and forwards the URL to `OlxAuthCallbackBridge.publishCallback(url)`. The intent filter must match the configured scheme; verify `androidApp/.../AndroidManifest.xml` whenever the scheme changes.
- Web (Wasm) — `composeApp/src/wasmJsMain/.../main.kt` checks `window.location` for `code=` / `error=` query parameters before mounting Compose, then calls `OlxAuthCallbackBridge.publishCallback`.
- The bridge is a global object (`OlxAuthCallbackBridge`); listeners are collected from both the seller landing and seller profile flows.
