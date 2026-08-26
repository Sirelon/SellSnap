---
paths:
  - "composeApp/src/commonMain/composeResources/**/strings.xml"
---

# String localization

After adding or changing any key in `values/strings.xml`, launch the `localize` agent (Agent tool, `subagent_type: localize`) with the list of keys before finishing the task. Do not hand-translate inline and do not leave any locale out of sync — the agent owns all translation rules. (If you are the localize agent, proceed with your instructions.)
