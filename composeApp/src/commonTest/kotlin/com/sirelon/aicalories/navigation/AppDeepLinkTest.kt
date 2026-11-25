package com.sirelon.sellsnap.navigation

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDeepLinkTest {

    @AfterTest
    fun tearDown() {
        AppDeepLinkBridge.consume()
    }

    @Test
    fun `every destination parses from its own url`() {
        AppDeepLink.entries.forEach { assertEquals(it, AppDeepLink.parse(it.url)) }
    }

    @Test
    fun `scheme and path ignore case, spaces, a trailing slash and a query`() {
        assertEquals(AppDeepLink.Account, AppDeepLink.parse(" SELLSNAP://Account/ "))
        assertEquals(AppDeepLink.MyAds, AppDeepLink.parse("sellsnap://my-ads?utm=push#top"))
    }

    @Test
    fun `other links are not in-app links`() {
        assertNull(AppDeepLink.parse("sellsnap://settings-unknown"))
        assertNull(AppDeepLink.parse("sellsnap://"))
        assertNull(AppDeepLink.parse("selolxai://olx-auth/callback?code=1"))
        assertNull(AppDeepLink.parse("https://sellsnap.app/account"))
        assertNull(AppDeepLink.parse("store"))
        assertNull(AppDeepLink.parse(null))
    }

    @Test
    fun `analytics codes are snake case`() {
        assertEquals("my_ads", AppDeepLink.MyAds.analyticsCode)
        assertEquals("account", AppDeepLink.Account.analyticsCode)
    }

    @Test
    fun `the bridge queues in-app links only and hands each out once`() {
        assertFalse(AppDeepLinkBridge.publish("https://sellsnap.app", AppDeepLinkBridge.SOURCE_PUSH))
        assertNull(AppDeepLinkBridge.pending.value)

        assertTrue(AppDeepLinkBridge.publish("sellsnap://messages", AppDeepLinkBridge.SOURCE_PUSH))
        assertEquals(
            AppDeepLinkBridge.Pending(AppDeepLink.Messages, AppDeepLinkBridge.SOURCE_PUSH),
            AppDeepLinkBridge.consume(),
        )
        assertNull(AppDeepLinkBridge.consume())
    }
}
