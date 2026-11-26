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
    fun `each link opens its screen`() {
        assertEquals(AppDeepLinkMatch(AppKey.Profile(), "account"), matchAppDeepLink("sellsnap://account"))
        assertEquals(AppDeepLinkMatch(AppKey.MyAdverts, "my_ads"), matchAppDeepLink("sellsnap://my-ads"))
        assertEquals(AppDeepLinkMatch(AppKey.Messages, "messages"), matchAppDeepLink("sellsnap://messages"))
        assertEquals(AppDeepLinkMatch(AppKey.GenerateAd, "generate"), matchAppDeepLink("sellsnap://generate"))
    }

    @Test
    fun `surrounding spaces and a query are ignored`() {
        assertEquals(AppKey.Profile(), matchAppDeepLink(" sellsnap://account ")?.key)
        assertEquals(AppKey.MyAdverts, matchAppDeepLink("sellsnap://my-ads?utm=push")?.key)
    }

    @Test
    fun `other links are not in-app links`() {
        assertNull(matchAppDeepLink("sellsnap://settings-unknown"))
        assertNull(matchAppDeepLink("sellsnap://"))
        assertNull(matchAppDeepLink("selolxai://olx-auth/callback?code=1"))
        assertNull(matchAppDeepLink("https://sellsnap.app/account"))
        assertNull(matchAppDeepLink("store"))
        assertNull(matchAppDeepLink(""))
        assertNull(matchAppDeepLink(null))
    }

    @Test
    fun `the bridge queues in-app links only and hands each out once`() {
        assertFalse(AppDeepLinkBridge.publish("https://sellsnap.app", AppDeepLinkBridge.SOURCE_PUSH))
        assertNull(AppDeepLinkBridge.pending.value)

        assertTrue(AppDeepLinkBridge.publish("sellsnap://messages", AppDeepLinkBridge.SOURCE_PUSH))
        assertEquals(
            AppDeepLinkBridge.Pending(AppDeepLinkMatch(AppKey.Messages, "messages"), AppDeepLinkBridge.SOURCE_PUSH),
            AppDeepLinkBridge.consume(),
        )
        assertNull(AppDeepLinkBridge.consume())
    }
}
