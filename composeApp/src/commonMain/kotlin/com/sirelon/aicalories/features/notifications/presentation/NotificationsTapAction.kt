package com.sirelon.sellsnap.features.notifications.presentation

enum class NotificationsTapAction {
    RequestPermission,
    OpenAppSettings,
}

/**
 * What tapping the Notifications row does. On, or when the OS will not prompt, only the app
 * settings can change anything. On iOS the OS prompt is available until the user answers it.
 * Android keeps offering the prompt, but after a denial it only shows it again once it reports a
 * rationale; before that, whether it was ever requested tells "never asked" from "denied for good".
 */
fun notificationsTapAction(
    enabled: Boolean,
    osCanPrompt: Boolean,
    isAndroid: Boolean,
    wasRequested: Boolean,
    shouldShowRationale: Boolean,
): NotificationsTapAction {
    val canRequest = osCanPrompt && (!isAndroid || !wasRequested || shouldShowRationale)
    return if (!enabled && canRequest) NotificationsTapAction.RequestPermission else NotificationsTapAction.OpenAppSettings
}
