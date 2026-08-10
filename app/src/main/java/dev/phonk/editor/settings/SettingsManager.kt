package dev.phonk.editor.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Persistent app preferences (theme, language, developer/debug flags) backed by
 * SharedPreferences. The Compose-observable states let the UI react immediately;
 * the language is fully applied on activity recreation via [wrapLocale].
 */
object SettingsManager {

    private const val PREFS = "phonk_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language_code"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_DEV_REVEALED = "dev_mode_revealed"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_HI = "hi"

    /** Number of consecutive rapid taps on the Settings title that reveals the dev screen. */
    const val DEV_REVEAL_TAPS = 7

    var themeMode by mutableStateOf(THEME_SYSTEM)
        private set

    var languageCode by mutableStateOf(LANG_SYSTEM)
        private set

    /**
     * Master app-level debug switch. When true, editor-only diagnostics (overlay
     * font/alignment/layer/playhead readouts) are drawn. Never surfaced to normal
     * users; only reachable through the developer screen.
     */
    var debugMode by mutableStateOf(false)
        private set

    /**
     * Whether the hidden developer entry has been revealed via the tap-gesture.
     * Reset to false hides the developer screen from normal users again.
     */
    var devRevealed by mutableStateOf(false)
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted values into the Compose-observable state. */
    fun init(context: Context) {
        themeMode = prefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        languageCode = prefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
        debugMode = prefs(context).getBoolean(KEY_DEBUG_MODE, false)
        devRevealed = prefs(context).getBoolean(KEY_DEV_REVEALED, false)
    }

    fun setThemeMode(context: Context, mode: String) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME, mode).apply()
    }

    fun setLanguage(context: Context, code: String) {
        languageCode = code
        prefs(context).edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun setDebugMode(context: Context, enabled: Boolean) {
        debugMode = enabled
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun setDevRevealed(context: Context, revealed: Boolean) {
        devRevealed = revealed
        prefs(context).edit().putBoolean(KEY_DEV_REVEALED, revealed).apply()
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
