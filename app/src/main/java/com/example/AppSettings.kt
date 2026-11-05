package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages preferences and configurations for Helian WhatsApp Web.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("helian_whatsapp_web_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DESKTOP_MODE = "key_desktop_mode"
        private const val KEY_KEEP_SCREEN_ON = "key_keep_screen_on"
        private const val KEY_AUTO_RELOAD_ON_CONNECT = "key_auto_reload_on_connect"
        private const val KEY_ZOOM_PERCENT = "key_zoom_percent"

        // Modern Chromium desktop User-Agent recognized by WhatsApp Web to render the web client interface.
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    var isDesktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    var isKeepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var isAutoReloadOnConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RELOAD_ON_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RELOAD_ON_CONNECT, value).apply()

    var zoomPercent: Int
        get() = prefs.getInt(KEY_ZOOM_PERCENT, 110)
        set(value) = prefs.edit().putInt(KEY_ZOOM_PERCENT, value).apply()
}
