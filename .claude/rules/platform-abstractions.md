---
paths:
  - "composeApp/src/commonMain/kotlin/**/camera/**"
  - "composeApp/src/commonMain/kotlin/**/datastore/**"
  - "composeApp/src/commonMain/kotlin/**/features/media/**"
  - "composeApp/src/commonMain/kotlin/**/features/seller/drafts/**"
  - "composeApp/src/commonMain/sqldelight/**"
  - "composeApp/src/dataStoreMain/**"
---

# Platform abstractions

- Camera launcher uses expect/actual style placement under `camera/`.
- Image conversion is platform-specific under `features/media/ImageFormatConverter.*`.
- Datastore abstraction lives under `datastore/KeyValueStore*`.
- Drafts are stored with SQLDelight (schema in `composeApp/src/commonMain/sqldelight/`, generated `SellSnapDatabase`). The drivers per platform and `SqlDelightDraftsRepository` live under `features/seller/drafts/data/`; the repository is in `dataStoreMain`, where `Dispatchers.IO` is not visible, so it runs on `Dispatchers.Default`. The web targets use `InMemoryDraftsRepository`. The iOS framework is static, so it carries no `-lsqlite3` of its own; the app links sqlite3 because the FirebaseAnalytics package declares it. Room is unavailable until a KSP release exists for the project's Kotlin version.
- Platform checks are centralized in `shared/.../platform/PlatformTargets.kt`.
