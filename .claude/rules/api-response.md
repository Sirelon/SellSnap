---
paths:
  - "composeApp/src/commonMain/kotlin/**/data/response/**"
  - "composeApp/src/commonMain/kotlin/**/data/*Response*.kt"
  - "composeApp/src/commonMain/kotlin/**/data/*Models.kt"
---

# API response class conventions

These rules apply to every class that directly maps a JSON API response (OLX, Supabase, or any external service). Violating them will be rejected in review.

## Naming
- Suffix is `Response`, never `Dto` or `Model`. Example: `OlxAttributeResponse`, not `OlxAttributeDto`.
- File lives in a `response/` sub-package inside the relevant `data/` package.

## Visibility
- Always `internal`. Response classes are an implementation detail of the data layer; nothing outside `data/` should reference them directly.

## Class kind
- Plain `class`, never `data class`. Response classes are deserialization targets, not value objects.

## Serialization
- Always annotate with `@Serializable`.
- Every field must have `@SerialName("json_key_name")` — even when the Kotlin name matches the JSON key exactly. This makes the contract explicit and rename-safe.

## Nullability and defaults
- Every field must be nullable (`?`). The backend cannot be trusted to always send every field.
- No default values in response classes. All defaults belong in the mapper.
- The mapper must handle every `null` case explicitly and supply appropriate fallback values.

## Mapper responsibilities
- Skip / filter out response items that are missing essential identity fields (e.g., a `code` that is `null`). Use `mapNotNull` for list transformations.
- Supply domain-layer defaults for missing optional fields (empty string, `false`, `emptyList()`, etc.).

## Custom serializers
- Avoid custom `KSerializer` implementations where the built-in behavior is sufficient.
- The OLX Ktor client is configured with `isLenient = true`, which means numeric JSON primitives are accepted where a `String` is expected — no custom serializer needed for mixed int/string id fields.
