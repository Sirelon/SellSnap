package com.sirelon.sellsnap.features.notifications.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationsTapActionTest {

    private fun action(
        enabled: Boolean = false,
        osCanPrompt: Boolean = true,
        isAndroid: Boolean = true,
        wasRequested: Boolean = false,
        shouldShowRationale: Boolean = false,
    ) = notificationsTapAction(
        enabled = enabled,
        osCanPrompt = osCanPrompt,
        isAndroid = isAndroid,
        wasRequested = wasRequested,
        shouldShowRationale = shouldShowRationale,
    )

    @Test
    fun `enabled opens the app settings`() {
        assertEquals(NotificationsTapAction.OpenAppSettings, action(enabled = true))
        assertEquals(NotificationsTapAction.OpenAppSettings, action(enabled = true, isAndroid = false))
    }

    @Test
    fun `iOS that can still prompt asks the OS`() {
        assertEquals(NotificationsTapAction.RequestPermission, action(isAndroid = false))
        // The Android request history means nothing on iOS.
        assertEquals(NotificationsTapAction.RequestPermission, action(isAndroid = false, wasRequested = true))
    }

    @Test
    fun `Android that never requested asks the OS`() {
        assertEquals(NotificationsTapAction.RequestPermission, action())
    }

    @Test
    fun `Android that requested before asks again only after a rationale`() {
        assertEquals(
            NotificationsTapAction.RequestPermission,
            action(wasRequested = true, shouldShowRationale = true),
        )
        assertEquals(NotificationsTapAction.OpenAppSettings, action(wasRequested = true))
    }

    @Test
    fun `a platform that cannot prompt opens the app settings`() {
        assertEquals(NotificationsTapAction.OpenAppSettings, action(osCanPrompt = false))
        assertEquals(NotificationsTapAction.OpenAppSettings, action(osCanPrompt = false, isAndroid = false))
    }
}
