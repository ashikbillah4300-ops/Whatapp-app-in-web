package com.example

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Manages cookie persistence and user-initiated session clearing for WhatsApp Web.
 */
object SessionManager {

    /**
     * Configures the CookieManager for persistent session storage across app restarts.
     */
    fun configureCookieManager(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Flushes buffered cookies to disk storage.
     */
    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
            // Ignore in environments where WebView provider is not ready
        }
    }

    /**
     * Completely resets and clears the current web session upon user request.
     * Clears cookies, WebStorage (IndexedDB, LocalStorage), and WebView cache.
     */
    fun clearSession(context: Context, webView: WebView?, onCompleted: () -> Unit) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()

                try {
                    WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {
                }

                webView?.post {
                    webView.clearCache(true)
                    webView.clearFormData()
                    webView.clearHistory()
                    webView.clearSslPreferences()
                    onCompleted()
                } ?: onCompleted()
            }
        } catch (e: Exception) {
            onCompleted()
        }
    }
}
