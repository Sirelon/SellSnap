package com.sirelon.sellsnap.content

import kotlin.test.Test
import kotlin.test.assertEquals

class LinksTest {
    private val website = "https://sirelon.github.io/SellSnap/?lang={lang}&p={code}"

    @Test
    fun theWebsiteTemplateCarriesTheLanguageAndTheCode() {
        assertEquals("https://sirelon.github.io/SellSnap/?lang=uk&p=ukt0042", postLinkUrl(website, "ukt0042", "uk"))
        assertEquals("https://sirelon.github.io/SellSnap/?lang=pl&p=pld0007", postLinkUrl(website, "pld0007", "pl"))
    }

    @Test
    fun theDefaultTemplateIsTheRedirectAndIgnoresTheLanguage() {
        val template = expandLinkTemplate(DEFAULT_POST_LINK_TEMPLATE, "https://agent.example.run.app/")
        assertEquals("https://agent.example.run.app/go/{code}", template)
        assertEquals("https://agent.example.run.app/go/ukt0042", postLinkUrl(template, "ukt0042", "uk"))
    }

    @Test
    fun theBaseUrlPlaceholderIsFilledWithoutATrailingSlash() {
        assertEquals("https://a.test/x/{code}", expandLinkTemplate("{PUBLIC_BASE_URL}/x/{code}", "https://a.test/"))
    }
}
