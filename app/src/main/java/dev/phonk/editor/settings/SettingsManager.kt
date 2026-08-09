package dev.phonk.editor.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Persistent app preferences (theme + language) backed by SharedPreferences.
 * The Compose-observable [themeMode] / [languageCode] states let the UI react
 * immediately; the language is fully applied on activity recreation via
 * [wrapLocale].
 */
object SettingsManager {

    private const val PREFS = "phonk_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language_code"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_HI = "hi"

    var themeMode by mutableStateOf(THEME_SYSTEM)
        private set

    var languageCode by mutableStateOf(LANG_SYSTEM)
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted values into the Compose-observable state. */
    fun init(context: Context) {
        themeMode = prefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        languageCode = prefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME, mode).apply()
    }

    fun setLanguage(context: Context, code: String) {
        languageCode = code
        prefs(context).edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun storedLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM

    /**
     * Wraps a base context with the stored app locale so string resources and
     * configuration follow the user's chosen language. No-op for "system".
     */
    fun wrapLocale(base: Context): Context {
        val code = storedLanguage(base)
        if (code == LANG_SYSTEM) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
