---
paths:
  - "composeApp/src/commonMain/composeResources/**/strings.xml"
---

# String localization

Adding or changing a key in `values/strings.xml` creates a translation debt across seven locales.
Pay it once: after the owner has approved the English `key → text` list (AGENTS.md,
"Localization"), launch the `localize` agent (Agent tool, `subagent_type: localize`) with the
final key list, then post the Ukrainian back for his check.

Do not hand-translate inline, do not run `localize` mid-task, and do not run it again for a
rewording the owner has not seen. (If you are the localize agent, proceed with your instructions.)
