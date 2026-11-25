---
paths:
  - "composeApp/src/commonMain/kotlin/**/features/notifications/**"
  - "androidApp/src/main/kotlin/**/Push*.kt"
  - "iosApp/iosApp/iOSApp.swift"
  - "iosApp/iosApp/*.entitlements"
---

# Push notifications

FCM, shared code in `composeApp/.../features/notifications/`. Every launch reports the user property `notifications_enabled` and subscribes the device to its topics.

- **Topics:** `all`, `all-<lang>` (en/uk/pl/pt/ro/bg/kk; `ru` → `uk`; anything else → `en`), and `qa` (debug builds only, subscribed natively).
- **`link` data key** sets the tap target: `store` opens the store page of the receiving platform, `https://…` opens as given, anything else just opens the app.
- **Android:** channel `updates`; the SDK displays background messages, `PushMessagingService` displays foreground ones.
- **iOS:** the APNs key is uploaded in Firebase; `aps-environment` lives in the single `iosApp/iosApp/iosAppRelease.entitlements` (`development`; App Store export re-signs it to `production`, so the `com.sirelon.sellsnap AppStore` profile must include Push Notifications or the export fails). Without notification permission the device still gets the APNs token and topics. FCM refuses topic operations on each launch until the APNs token is set (error 505, no retry), so topic calls wait for `PushTokenBridge`, which the `MessagingDelegate` registration-token callback signals. Under SwiftUI, FCM's swizzled handler takes the APNs token and the app delegate's `didRegisterForRemoteNotificationsWithDeviceToken` is not called, so nothing may depend on it. The share extension asks for permission when it is undecided.
- **Permission prompt:** a one-time bottom sheet at the start of a returning session in the seller flow, third in the launch-dialog order after the announcement and What's New (`NotificationsPromptViewModel`); once shown it is never offered again. Settings has a Notifications row (mobile only): it shows the OS prompt while the OS can still show it, otherwise it opens the app's system settings (`notificationsTapAction`).
- **Sending a push** (test or campaign): the `send-push` skill.
