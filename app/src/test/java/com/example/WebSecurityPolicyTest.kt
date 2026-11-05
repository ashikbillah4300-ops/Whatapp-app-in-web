package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSecurityPolicyTest {

    @Test
    fun testOfficialWhatsAppWebUrlAllowed() {
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://web.whatsapp.com/"))
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://web.whatsapp.com/app"))
    }

    @Test
    fun testSubdomainsAndAssetsAllowed() {
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://static.whatsapp.net/rsrc.php/v3/yO/r/"))
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://pps.whatsapp.net/v/t61.24694-24/test.jpg"))
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://scontent.whatsapp.net/v/t39.8562-6/logo.png"))
        assertTrue(WebSecurityPolicy.isUrlAllowed("https://static.xx.fbcdn.net/rsrc.php/"))
    }

    @Test
    fun testPlainHttpBlocked() {
        assertFalse(WebSecurityPolicy.isUrlAllowed("http://web.whatsapp.com/"))
        assertFalse(WebSecurityPolicy.isUrlAllowed("http://whatsapp.com/"))
    }

    @Test
    fun testArbitraryDomainsBlocked() {
        assertFalse(WebSecurityPolicy.isUrlAllowed("https://malicious-site.com/"))
        assertFalse(WebSecurityPolicy.isUrlAllowed("https://evil-phishing-whatsapp.com/"))
        assertFalse(WebSecurityPolicy.isUrlAllowed("https://google.com/"))
    }

    @Test
    fun testFileAndJavascriptSchemesBlocked() {
        assertFalse(WebSecurityPolicy.isUrlAllowed("file:///android_asset/test.html"))
        assertFalse(WebSecurityPolicy.isUrlAllowed("javascript:alert(1)"))
    }

    @Test
    fun testBlobAndDataAllowedForMedia() {
        assertTrue(WebSecurityPolicy.isUrlAllowed("blob:https://web.whatsapp.com/488582d2-8fc4-47b7"))
        assertTrue(WebSecurityPolicy.isUrlAllowed("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg=="))
    }

    @Test
    fun testExternalActionSchemes() {
        assertTrue(WebSecurityPolicy.isExternalActionScheme("whatsapp://send?phone=123456789"))
        assertTrue(WebSecurityPolicy.isExternalActionScheme("tel:+1234567890"))
        assertTrue(WebSecurityPolicy.isExternalActionScheme("mailto:support@example.com"))
        assertFalse(WebSecurityPolicy.isExternalActionScheme("https://web.whatsapp.com/"))
        assertFalse(WebSecurityPolicy.isExternalActionScheme("file:///test"))
    }
}
