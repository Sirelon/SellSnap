package com.sirelon.sellsnap.features.auth.presentation

import com.sirelon.sellsnap.features.seller.auth.presentation.OlxLoginPage
import com.sirelon.sellsnap.features.seller.auth.presentation.desktopUserAgent
import kotlin.test.Test
import kotlin.test.assertEquals

class OlxLoginPageTest {

    @Test
    fun `pages seen on the olx kz and olx ro login are classified`() {
        assertEquals(OlxLoginPage.Authorize, OlxLoginPage.of("www.olx.kz", "/oauth/authorize/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("www.olx.kz", "/account/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("m.olx.kz", "/account/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("login.olx.kz", "/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("auth.olx.ro", "/u/login/identifier"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("www.olx.kz", "/d/callback/"))
        assertEquals(OlxLoginPage.OlxHome, OlxLoginPage.of("www.olx.kz", "/"))
        assertEquals(OlxLoginPage.OlxHome, OlxLoginPage.of("m.olx.ro", ""))
        assertEquals(OlxLoginPage.OlxHome, OlxLoginPage.of("www.olx.pl", "/d/"))
        assertEquals(OlxLoginPage.OlxHome, OlxLoginPage.of("www.olx.ua", "/uk/"))
        assertEquals(OlxLoginPage.Authorize, OlxLoginPage.of("www.olx.ua", "/uk/oauth/authorize/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("www.olx.ua", "/uk/account/"))
        assertEquals(OlxLoginPage.SignIn, OlxLoginPage.of("www.olx.ro", "/cont/"))
    }

    @Test
    fun `the Android WebView user agent becomes desktop Chrome of the same version`() {
        val webView = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/149.0.0.0 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/149.0.0.0 Safari/537.36",
            desktopUserAgent(webView),
        )
    }
}
