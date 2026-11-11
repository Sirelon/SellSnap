---
name: send-push
description: >-
  Send a push notification to SellSnap users through FCM: a test to the `qa` topic or one
  device, or a campaign to a language topic. Use when the user says "send a push", "test
  push", "notify users about the update", or wants a release announced by notification.
---

# Send a push notification

Topics and the `link` tap target are defined in `.claude/rules/push-notifications.md`. Read it first.

## 1. Pick the target

A test goes to the `qa` topic or to one device token. Debug and release builds share one Firebase
app, so a test sent to `all` or `all-<lang>` reaches real users.

A campaign goes to `all`, `all-<lang>` or a condition. Its title and body are user-facing copy:
the owner approves the text in every language before it is sent (AGENTS.md rule 6).

Done when the target is fixed and, for a campaign, the owner approved the text.

## 2. Send

- **Send, FCM v1 REST** (works with the owner's `gcloud` login; the Firebase MCP `messaging_send_message` tool failed without a reason on 2026-10-07):
  `curl -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" -H "x-goog-user-project: sellsnap-6e85c" -H "Content-Type: application/json" https://fcm.googleapis.com/v1/projects/sellsnap-6e85c/messages:send -d '{"message":{"topic":"qa","notification":{"title":"…","body":"…"},"data":{"link":"store"}}}'`.
  Everyone except Ukrainian: replace `"topic":"qa"` with `"condition":"'all' in topics && !('all-uk' in topics)"`.
- **Send, console:** Messaging → New campaign → Notifications → Target: Topic → Additional options → Custom data `link`.

Done when FCM answers with a message name (`projects/sellsnap-6e85c/messages/…`).

## 3. Pair a campaign with an announcement

Users who declined notifications see only the in-app announcement. Publish an `announcements`
doc with the same message (`.claude/rules/announcements.md`).

Done when the doc is live, or the owner said the push stands alone.
