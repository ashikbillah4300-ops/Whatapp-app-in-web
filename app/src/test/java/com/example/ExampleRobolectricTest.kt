package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sunflower", appName)
    }

    @Test
    fun `test app settings default values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettings(context)
        assertTrue(settings.isDesktopMode)
        assertTrue(settings.isKeepScreenOn)
        assertEquals(110, settings.zoomPercent)
    }

    @Test
    fun `test navigation tabs count and labels`() {
        assertEquals(4, NavigationTab.entries.size)
        assertEquals("Chats", NavigationTab.CHATS.label)
        assertEquals("Updates", NavigationTab.UPDATES.label)
        assertEquals("Communities", NavigationTab.COMMUNITIES.label)
        assertEquals("Calls", NavigationTab.CALLS.label)
    }
}
