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

    // Export defaults
    private const val KEY_DEFAULT_RESOLUTION = "default_resolution"
    private const val KEY_DEFAULT_FPS = "default_fps"
    private const val KEY_VIDEO_BITRATE = "video_bitrate"
    private const val KEY_AUDIO_BITRATE = "audio_bitrate"
    private const val KEY_HARDWARE_ACCEL = "hardware_accel"
    private const val KEY_ADD_TO_GALLERY = "add_to_gallery"

    // Editor defaults
    private const val KEY_DEFAULT_ASPECT = "default_aspect"

    // Project
    private const val KEY_AUTOSAVE = "autosave"

    // Application
    private const val KEY_AUTO_BACKUP = "auto_backup"
    private const val KEY_PREVIEW_QUALITY = "preview_quality"
    private const val KEY_LOW_POWER_MODE = "low_power_mode"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_EXPORT_NOTIFICATIONS = "export_notifications"
    private const val KEY_FEATURE_NOTIFICATIONS = "feature_notifications"

    const val PREVIEW_QUALITY_AUTO = "Auto"
    const val PREVIEW_QUALITY_LOW = "Low"
    const val PREVIEW_QUALITY_MEDIUM = "Medium"
    const val PREVIEW_QUALITY_HIGH = "High"

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

    // Export defaults
    var defaultResolution by mutableStateOf("1080p")
        private set

    var defaultFps by mutableStateOf(30)
        private set

    var videoBitrateMbps by mutableStateOf(12)
        private set

    var audioBitrateKbps by mutableStateOf(192)
        private set

    var hardwareAccel by mutableStateOf(true)
        private set

    var addToGallery by mutableStateOf(true)
        private set

    // Editor defaults
    var defaultAspect by mutableStateOf("9:16")
        private set

    // Project
    var autosave by mutableStateOf(true)
        private set

    // Application
    var autoBackup by mutableStateOf(false)
        private set

    var previewQuality by mutableStateOf(PREVIEW_QUALITY_HIGH)
        private set

    var lowPowerMode by mutableStateOf(false)
        private set

    var notificationsEnabled by mutableStateOf(true)
        private set

    var exportNotifications by mutableStateOf(true)
        private set

    var featureNotifications by mutableStateOf(true)
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted values into the Compose-observable state. */
    fun init(context: Context) {
        themeMode = prefs(context).getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        languageCode = prefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
        debugMode = prefs(context).getBoolean(KEY_DEBUG_MODE, false)
        devRevealed = prefs(context).getBoolean(KEY_DEV_REVEALED, false)
        defaultResolution = prefs(context).getString(KEY_DEFAULT_RESOLUTION, "1080p") ?: "1080p"
        defaultFps = prefs(context).getInt(KEY_DEFAULT_FPS, 30)
        videoBitrateMbps = prefs(context).getInt(KEY_VIDEO_BITRATE, 12)
        audioBitrateKbps = prefs(context).getInt(KEY_AUDIO_BITRATE, 192)
        hardwareAccel = prefs(context).getBoolean(KEY_HARDWARE_ACCEL, true)
        addToGallery = prefs(context).getBoolean(KEY_ADD_TO_GALLERY, true)
        defaultAspect = prefs(context).getString(KEY_DEFAULT_ASPECT, "9:16") ?: "9:16"
        autosave = prefs(context).getBoolean(KEY_AUTOSAVE, true)
        autoBackup = prefs(context).getBoolean(KEY_AUTO_BACKUP, false)
        previewQuality = prefs(context).getString(KEY_PREVIEW_QUALITY, PREVIEW_QUALITY_HIGH) ?: PREVIEW_QUALITY_HIGH
        lowPowerMode = prefs(context).getBoolean(KEY_LOW_POWER_MODE, false)
        notificationsEnabled = prefs(context).getBoolean(KEY_NOTIFICATIONS, true)
        exportNotifications = prefs(context).getBoolean(KEY_EXPORT_NOTIFICATIONS, true)
        featureNotifications = prefs(context).getBoolean(KEY_FEATURE_NOTIFICATIONS, true)
    }

    fun setThemeMode(context: Context, mode: String) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME, mode).commit()
    }

    fun setLanguage(context: Context, code: String) {
        languageCode = code
        prefs(context).edit().putString(KEY_LANGUAGE, code).commit()
    }

    fun setDebugMode(context: Context, enabled: Boolean) {
        debugMode = enabled
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun setDevRevealed(context: Context, revealed: Boolean) {
        devRevealed = revealed
        prefs(context).edit().putBoolean(KEY_DEV_REVEALED, revealed).apply()
    }

    fun setDefaultResolution(context: Context, resolution: String) {
        defaultResolution = resolution
        prefs(context).edit().putString(KEY_DEFAULT_RESOLUTION, resolution).apply()
    }

    fun setDefaultFps(context: Context, fps: Int) {
        defaultFps = fps
        prefs(context).edit().putInt(KEY_DEFAULT_FPS, fps).apply()
    }

    fun setVideoBitrate(context: Context, bitrate: Int) {
        videoBitrateMbps = bitrate
        prefs(context).edit().putInt(KEY_VIDEO_BITRATE, bitrate).apply()
    }

    fun setAudioBitrate(context: Context, bitrate: Int) {
        audioBitrateKbps = bitrate
        prefs(context).edit().putInt(KEY_AUDIO_BITRATE, bitrate).apply()
    }

    fun setHardwareAccel(context: Context, enabled: Boolean) {
        hardwareAccel = enabled
        prefs(context).edit().putBoolean(KEY_HARDWARE_ACCEL, enabled).commit()
    }

    fun setAddToGallery(context: Context, enabled: Boolean) {
        addToGallery = enabled
        prefs(context).edit().putBoolean(KEY_ADD_TO_GALLERY, enabled).commit()
    }

    fun setDefaultAspect(context: Context, aspect: String) {
        defaultAspect = aspect
        prefs(context).edit().putString(KEY_DEFAULT_ASPECT, aspect).apply()
    }

    fun setAutosave(context: Context, enabled: Boolean) {
        autosave = enabled
        prefs(context).edit().putBoolean(KEY_AUTOSAVE, enabled).apply()
    }

    fun setAutoBackup(context: Context, enabled: Boolean) {
        autoBackup = enabled
        prefs(context).edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    fun setPreviewQuality(context: Context, quality: String) {
        previewQuality = quality
        prefs(context).edit().putString(KEY_PREVIEW_QUALITY, quality).apply()
    }

    fun setLowPowerMode(context: Context, enabled: Boolean) {
        lowPowerMode = enabled
        prefs(context).edit().putBoolean(KEY_LOW_POWER_MODE, enabled).apply()
    }

    fun setNotifications(context: Context, enabled: Boolean) {
        notificationsEnabled = enabled
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun setExportNotifications(context: Context, enabled: Boolean) {
        exportNotifications = enabled
        prefs(context).edit().putBoolean(KEY_EXPORT_NOTIFICATIONS, enabled).apply()
    }

    fun setFeatureNotifications(context: Context, enabled: Boolean) {
        featureNotifications = enabled
        prefs(context).edit().putBoolean(KEY_FEATURE_NOTIFICATIONS, enabled).apply()
    }

    /** Reset all preferences to their default values. */
    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
        init(context)
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
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
