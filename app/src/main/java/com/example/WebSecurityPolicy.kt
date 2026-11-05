package com.example

import android.webkit.SslErrorHandler
import java.net.URI

/**
 * Enforces strict web security policies for Helian WhatsApp Web.
 * - Only HTTPS connections to verified WhatsApp / Meta infrastructure are permitted.
 * - Disallows plain HTTP, file://, and untrusted schemes.
 * - Cancels SSL error connections to prevent man-in-the-middle attacks.
 */
object WebSecurityPolicy {

    const val OFFICIAL_WHATSAPP_WEB_URL = "https://web.whatsapp.com/"

    // Whitelist of allowed domain suffixes for WhatsApp Web and its required CDN/media assets.
    private val ALLOWED_DOMAIN_PATTERNS = listOf(
        "web.whatsapp.com",
        "whatsapp.com",
        "whatsapp.net",
        "fbcdn.net",
        "facebook.com"
    )

    /**
     * Checks if the given URL is an allowed official WhatsApp Web or asset origin.
     */
    fun isUrlAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val scheme = try {
            val colonIndex = url.indexOf(':')
            if (colonIndex != -1) url.substring(0, colonIndex).lowercase() else null
        } catch (_: Exception) {
            null
        } ?: return false

        // Allow blob/data schemes internally used by web apps for local media rendering
        if (scheme == "blob" || scheme == "data") {
            return true
        }

        // Strictly require HTTPS for all web content
        if (scheme != "https") {
            return false
        }

        val host = try {
            val uri = URI(url)
            uri.host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: run {
            val doubleSlash = url.indexOf("://")
            if (doubleSlash != -1) {
                val pathStart = url.indexOf('/', doubleSlash + 3)
                val hostPart = if (pathStart != -1) {
                    url.substring(doubleSlash + 3, pathStart)
                } else {
                    url.substring(doubleSlash + 3)
                }
                hostPart.split(':')[0].lowercase()
            } else null
        } ?: return false

        return ALLOWED_DOMAIN_PATTERNS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    /**
     * Determines whether an intent-based scheme (e.g., whatsapp://, mailto:, tel:, sms:)
     * is valid and should be opened via an external intent rather than in WebView.
     */
    fun isExternalActionScheme(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val colonIndex = url.indexOf(':')
        if (colonIndex == -1) return false
        val scheme = url.substring(0, colonIndex).lowercase()
        return scheme in listOf("whatsapp", "mailto", "tel", "sms")
    }

    /**
     * Safely handles SSL error: cancels connection to prevent MITM attacks.
     */
    fun handleSslError(handler: SslErrorHandler?) {
        // Under zero-trust security policy, SSL errors must abort the request immediately.
        handler?.cancel()
    }
}
