package dev.phonk.editor.ui

import android.app.Activity
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.SavedSearch
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.BuildConfig
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.components.SettingsCard
import dev.phonk.editor.ui.components.SettingsDestructiveRow
import dev.phonk.editor.ui.components.SettingsInfoRow
import dev.phonk.editor.ui.components.SettingsNavigationRow
import dev.phonk.editor.ui.components.SettingsRow
import dev.phonk.editor.ui.components.SettingsRadioRow
import dev.phonk.editor.ui.components.SettingsSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Settings screen: all app preferences organized into clear sections. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // State for dialogs
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAspectPicker by remember { mutableStateOf(false) }
    var showResolutionPicker by remember { mutableStateOf(false) }
    var showFpsPicker by remember { mutableStateOf(false) }
    var showVideoQualityPicker by remember { mutableStateOf(false) }
    var showAudioQualityPicker by remember { mutableStateOf(false) }

    // Read current settings
    val themeMode = SettingsManager.themeMode
    val languageCode = SettingsManager.languageCode
    val devRevealed = SettingsManager.devRevealed
    val defaultResolution = SettingsManager.defaultResolution
    val defaultFps = SettingsManager.defaultFps
    val videoBitrate = SettingsManager.videoBitrateMbps
    val audioBitrate = SettingsManager.audioBitrateKbps
    val hardwareAccel = SettingsManager.hardwareAccel
    val addToGallery = SettingsManager.addToGallery
    val defaultAspect = SettingsManager.defaultAspect
    val autosave = SettingsManager.autosave

    // Hidden developer reveal gesture
    var devTaps by remember { mutableIntStateOf(0) }

    fun applyLanguage(code: String) {
        if (languageCode == code) return
        SettingsManager.setLanguage(context, code)
        (context as? Activity)?.recreate()
    }

    fun storageUsage(): Triple<String, String, String> {
        val projectsDir = File(context.filesDir, "projects")
        val cacheDir = context.cacheDir
        val exportDir = File(Environment.getExternalStorageDirectory(), "Movies/Phonk")

        fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

        val projectsSize = dirSize(projectsDir)
        val cacheSize = dirSize(cacheDir)
        val exportsSize = dirSize(exportDir)

        val formatter = Formatter.formatShortFileSize(context, projectsSize)
        val cacheStr = Formatter.formatShortFileSize(context, cacheSize)
        val exportsStr = Formatter.formatShortFileSize(context, exportsSize)

        return Triple(formatter, cacheStr, exportsStr)
    }

    fun clearCache(): String {
        val cacheDir = context.cacheDir
        val sizeBefore = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return Formatter.formatShortFileSize(context, sizeBefore)
    }

    fun ffmpegAvailable(): Boolean {
        val ffmpegFile = File(context.filesDir, "ffmpeg")
        return ffmpegFile.exists() && ffmpegFile.canExecute()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.background)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = scheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable {
                            devTaps += 1
                            scope.launch {
                                delay(2500)
                                devTaps = 0
                            }
                            if (devTaps >= SettingsManager.DEV_REVEAL_TAPS) {
                                if (!SettingsManager.devRevealed) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.debug_revealed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                SettingsManager.setDevRevealed(context, true)
                                devTaps = 0
                            }
                        },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.background,
                        1f to scheme.surfaceContainerLowest,
                    )
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ===== APPEARANCE =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_appearance),
                        icon = Icons.Filled.Palette,
                    ) {
                        SettingsRadioRow(
                            icon = Icons.Filled.SettingsBrightness,
                            title = stringResource(R.string.theme_system),
                            selected = themeMode == SettingsManager.THEME_SYSTEM,
                            onClick = { SettingsManager.setThemeMode(context, SettingsManager.THEME_SYSTEM) },
                        )
                        SettingsRadioRow(
                            icon = Icons.Filled.LightMode,
                            title = stringResource(R.string.theme_light),
                            selected = themeMode == SettingsManager.THEME_LIGHT,
                            onClick = { SettingsManager.setThemeMode(context, SettingsManager.THEME_LIGHT) },
                        )
                        SettingsRadioRow(
                            icon = Icons.Filled.DarkMode,
                            title = stringResource(R.string.theme_dark),
                            selected = themeMode == SettingsManager.THEME_DARK,
                            onClick = { SettingsManager.setThemeMode(context, SettingsManager.THEME_DARK) },
                        )
                    }
                }

                // ===== LANGUAGE =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_language),
                        icon = Icons.Filled.Translate,
                    ) {
                        SettingsRadioRow(
                            icon = Icons.Filled.Translate,
                            title = stringResource(R.string.language_system),
                            selected = languageCode == SettingsManager.LANG_SYSTEM,
                            onClick = { applyLanguage(SettingsManager.LANG_SYSTEM) },
                        )
                        SettingsRadioRow(
                            icon = Icons.Filled.Translate,
                            title = stringResource(R.string.language_english),
                            selected = languageCode == SettingsManager.LANG_EN,
                            onClick = { applyLanguage(SettingsManager.LANG_EN) },
                        )
                        SettingsRadioRow(
                            icon = Icons.Filled.Translate,
                            title = stringResource(R.string.language_hindi),
                            selected = languageCode == SettingsManager.LANG_HI,
                            onClick = { applyLanguage(SettingsManager.LANG_HI) },
                        )
                    }
                }

                // ===== EDITOR =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_editor),
                        icon = Icons.Filled.Tune,
                    ) {
                        SettingsNavigationRow(
                            icon = Icons.Filled.Rotate90DegreesCcw,
                            title = stringResource(R.string.settings_default_aspect),
                            subtitle = stringResource(R.string.settings_default_aspect_hint),
                            value = defaultAspect,
                            onClick = { showAspectPicker = true },
                        )
                        SettingsNavigationRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.settings_default_fps),
                            subtitle = stringResource(R.string.settings_default_fps_hint),
                            value = "$defaultFps FPS",
                            onClick = { showFpsPicker = true },
                        )
                    }
                }

                // ===== EXPORT =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_export),
                        icon = Icons.Filled.Videocam,
                    ) {
                        SettingsNavigationRow(
                            icon = Icons.Filled.Movie,
                            title = stringResource(R.string.settings_default_resolution),
                            subtitle = stringResource(R.string.settings_default_resolution_hint),
                            value = defaultResolution,
                            onClick = { showResolutionPicker = true },
                        )
                        SettingsNavigationRow(
                            icon = Icons.Filled.SavedSearch,
                            title = stringResource(R.string.settings_video_quality),
                            value = "${videoBitrate} Mbps",
                            onClick = { showVideoQualityPicker = true },
                        )
                        SettingsNavigationRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.settings_audio_quality),
                            value = "${audioBitrate} kbps",
                            onClick = { showAudioQualityPicker = true },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Filled.Memory,
                            title = stringResource(R.string.settings_hardware_accel),
                            subtitle = stringResource(R.string.settings_hardware_accel_hint),
                            checked = hardwareAccel,
                            onCheckedChange = { SettingsManager.setHardwareAccel(context, it) },
                        )
                        SettingsSwitchRow(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.settings_add_to_gallery),
                            subtitle = stringResource(R.string.settings_add_to_gallery_hint),
                            checked = addToGallery,
                            onCheckedChange = { SettingsManager.setAddToGallery(context, it) },
                        )
                    }
                }

                // ===== STORAGE =====
                item {
                    val (projectsSize, cacheSize, exportsSize) = storageUsage()
                    SettingsCard(
                        title = stringResource(R.string.settings_section_storage),
                        icon = Icons.Filled.Storage,
                    ) {
                        SettingsInfoRow(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.settings_storage_projects),
                            value = projectsSize,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Timer,
                            title = stringResource(R.string.settings_storage_cache),
                            value = cacheSize,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Movie,
                            title = stringResource(R.string.settings_storage_exports),
                            value = exportsSize,
                        )
                        SettingsRow(
                            icon = Icons.Filled.Delete,
                            title = stringResource(R.string.settings_clear_cache),
                            subtitle = stringResource(R.string.settings_clear_cache_hint),
                            onClick = { showClearCacheDialog = true },
                        )
                    }
                }

                // ===== PROJECT =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_project),
                        icon = Icons.Filled.Timer,
                    ) {
                        SettingsSwitchRow(
                            icon = Icons.Filled.Timer,
                            title = stringResource(R.string.settings_autosave),
                            subtitle = stringResource(R.string.settings_autosave_hint),
                            checked = autosave,
                            onCheckedChange = { SettingsManager.setAutosave(context, it) },
                        )
                    }
                }

                // ===== DIAGNOSTICS =====
                item {
                    val appVersion = BuildConfig.VERSION_NAME
                    val buildNumber = BuildConfig.VERSION_CODE
                    val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    val cpuArch = Build.SUPPORTED_ABIS.joinToString(", ")
                    val ffmpegStatus = if (ffmpegAvailable()) {
                        stringResource(R.string.settings_ffmpeg_available)
                    } else {
                        stringResource(R.string.settings_ffmpeg_missing)
                    }

                    SettingsCard(
                        title = stringResource(R.string.settings_section_diagnostics),
                        icon = Icons.Filled.BugReport,
                    ) {
                        SettingsInfoRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_app_version),
                            value = appVersion,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_build_number),
                            value = buildNumber.toString(),
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_android_version),
                            value = androidVersion,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_device_model),
                            value = deviceModel,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.Memory,
                            title = stringResource(R.string.settings_cpu_arch),
                            value = cpuArch,
                        )
                        SettingsInfoRow(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.settings_ffmpeg_status),
                            value = ffmpegStatus,
                        )
                        SettingsRow(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.settings_copy_diagnostics),
                            onClick = {
                                val diagnostics = """
                                    |Phonk Drop Editor Diagnostics
                                    |App Version: $appVersion
                                    |Build: $buildNumber
                                    |Android: $androidVersion
                                    |Device: $deviceModel
                                    |CPU: $cpuArch
                                    |FFmpeg: $ffmpegStatus
                                """.trimMargin()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", diagnostics))
                                Toast.makeText(context, context.getString(R.string.diagnostics_copied), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }

                // ===== ABOUT =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_about),
                        icon = Icons.Filled.Info,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_about_description),
                                fontSize = 13.sp,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ===== DANGER ZONE =====
                item {
                    SettingsCard(
                        title = stringResource(R.string.settings_section_danger),
                        icon = Icons.Filled.Delete,
                    ) {
                        SettingsDestructiveRow(
                            icon = Icons.Filled.RestartAlt,
                            title = stringResource(R.string.settings_reset_settings),
                            subtitle = stringResource(R.string.settings_reset_settings_hint),
                            onClick = { showResetDialog = true },
                        )
                    }
                }

                // Developer entry (hidden unless revealed)
                if (devRevealed) {
                    item {
                        SettingsCard(
                            title = stringResource(R.string.developer),
                            icon = Icons.Filled.BugReport,
                        ) {
                            SettingsRow(
                                icon = Icons.Filled.BugReport,
                                title = stringResource(R.string.debug_screen_title),
                                onClick = onOpenDeveloper,
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== DIALOGS =====

    // Clear cache dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = { Text(stringResource(R.string.dialog_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleared = clearCache()
                        showClearCacheDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.cache_cleared, cleared),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_cache),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Reset settings dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_reset_settings_title)) },
            text = { Text(stringResource(R.string.dialog_reset_settings_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        SettingsManager.resetAll(context)
                        showResetDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_reset_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_reset_settings),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Aspect ratio picker
    if (showAspectPicker) {
        AlertDialog(
            onDismissRequest = { showAspectPicker = false },
            title = { Text(stringResource(R.string.settings_default_aspect)) },
            text = {
                Column {
                    listOf("9:16", "1:1", "16:9", "4:5").forEach { aspect ->
                        SettingsRadioRow(
                            icon = Icons.Filled.Rotate90DegreesCcw,
                            title = aspect,
                            selected = defaultAspect == aspect,
                            onClick = {
                                SettingsManager.setDefaultAspect(context, aspect)
                                showAspectPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAspectPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Resolution picker
    if (showResolutionPicker) {
        AlertDialog(
            onDismissRequest = { showResolutionPicker = false },
            title = { Text(stringResource(R.string.settings_default_resolution)) },
            text = {
                Column {
                    listOf("720p", "1080p", "1440p", "4K").forEach { res ->
                        SettingsRadioRow(
                            icon = Icons.Filled.Movie,
                            title = res,
                            selected = defaultResolution == res,
                            onClick = {
                                SettingsManager.setDefaultResolution(context, res)
                                showResolutionPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showResolutionPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // FPS picker
    if (showFpsPicker) {
        AlertDialog(
            onDismissRequest = { showFpsPicker = false },
            title = { Text(stringResource(R.string.settings_default_fps)) },
            text = {
                Column {
                    listOf(24, 30, 60).forEach { fps ->
                        SettingsRadioRow(
                            icon = Icons.Filled.Speed,
                            title = "$fps FPS",
                            selected = defaultFps == fps,
                            onClick = {
                                SettingsManager.setDefaultFps(context, fps)
                                showFpsPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFpsPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Video quality picker
    if (showVideoQualityPicker) {
        AlertDialog(
            onDismissRequest = { showVideoQualityPicker = false },
            title = { Text(stringResource(R.string.settings_video_quality)) },
            text = {
                Column {
                    listOf(
                        6 to stringResource(R.string.settings_video_quality_low),
                        12 to stringResource(R.string.settings_video_quality_medium),
                        20 to stringResource(R.string.settings_video_quality_high),
                    ).forEach { (bitrate, label) ->
                        SettingsRadioRow(
                            icon = Icons.Filled.SavedSearch,
                            title = "$label ($bitrate Mbps)",
                            selected = videoBitrate == bitrate,
                            onClick = {
                                SettingsManager.setVideoBitrate(context, bitrate)
                                showVideoQualityPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVideoQualityPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Audio quality picker
    if (showAudioQualityPicker) {
        AlertDialog(
            onDismissRequest = { showAudioQualityPicker = false },
            title = { Text(stringResource(R.string.settings_audio_quality)) },
            text = {
                Column {
                    listOf(
                        128 to stringResource(R.string.settings_audio_quality_low),
                        192 to stringResource(R.string.settings_audio_quality_medium),
                        256 to stringResource(R.string.settings_audio_quality_high),
                    ).forEach { (bitrate, label) ->
                        SettingsRadioRow(
                            icon = Icons.Filled.Speed,
                            title = label,
                            selected = audioBitrate == bitrate,
                            onClick = {
                                SettingsManager.setAudioBitrate(context, bitrate)
                                showAudioQualityPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAudioQualityPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
