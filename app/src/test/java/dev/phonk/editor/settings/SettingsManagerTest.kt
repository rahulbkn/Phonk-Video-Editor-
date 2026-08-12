package dev.phonk.editor.settings

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Tests for SettingsManager locale handling and persistence.
 */
class SettingsManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SettingsManager.init(context)
        val prefs = context.getSharedPreferences(SettingsManager.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SettingsManager.init(context)
    }

    @Test
    fun wrapLocaleDoesNotModifyGlobalDefault() {
        val original = Locale.getDefault()
        SettingsManager.setLanguage(context, SettingsManager.LANG_HI)
        val wrapped = SettingsManager.wrapLocale(context)
        assertNotEquals("wrapLocale must not change the global default locale", original, Locale.getDefault())
        val config = Configuration(wrapped.resources.configuration)
        assertEquals("Hindi locale should be applied to the wrapped context", "hi", config.locales.get(0)?.language)
    }

    @Test
    fun wrapLocaleReturnsOriginalForSystem() {
        val original = context
        val wrapped = SettingsManager.wrapLocale(context)
        assertEquals("System language must return the original context", original, wrapped)
    }

    @Test
    fun languageChangePersistsAcrossInit() {
        SettingsManager.setLanguage(context, SettingsManager.LANG_EN)
        assertEquals(SettingsManager.LANG_EN, SettingsManager.languageCode)
        SettingsManager.init(context)
        assertEquals(SettingsManager.LANG_EN, SettingsManager.languageCode)
    }

    @Test
    fun themeModePersists() {
        SettingsManager.setThemeMode(context, SettingsManager.THEME_DARK)
        assertEquals(SettingsManager.THEME_DARK, SettingsManager.themeMode)
        SettingsManager.init(context)
        assertEquals(SettingsManager.THEME_DARK, SettingsManager.themeMode)
    }

    @Test
    fun resetAllRestoresDefaults() {
        SettingsManager.setThemeMode(context, SettingsManager.THEME_DARK)
        SettingsManager.setLanguage(context, SettingsManager.LANG_HI)
        SettingsManager.setDefaultResolution(context, "720p")
        SettingsManager.resetAll(context)
        assertEquals(SettingsManager.THEME_SYSTEM, SettingsManager.themeMode)
        assertEquals(SettingsManager.LANG_SYSTEM, SettingsManager.languageCode)
        assertEquals("1080p", SettingsManager.defaultResolution)
    }
}
