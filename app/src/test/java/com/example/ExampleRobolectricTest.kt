package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.JarvisSettings
import com.example.domain.tools.JarvisToolEngine
import com.example.data.local.JarvisDataRepository
import com.example.data.local.JarvisDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and default JARVIS configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS", appName)

        val defaultSettings = JarvisSettings()
        assertEquals("gemini-3.8-live", defaultSettings.liveVoiceModel)
        assertEquals("gemini-3.5-flash", defaultSettings.textBrainModel)
        assertTrue(defaultSettings.confirmSensitiveActions)

        val db = JarvisDatabase.getInstance(context)
        val repo = JarvisDataRepository(db)
        val toolEngine = JarvisToolEngine(context, repo)
        val toolsJson = toolEngine.getGeminiToolsJsonArray()
        assertTrue(toolsJson.length() > 0)
    }
}
