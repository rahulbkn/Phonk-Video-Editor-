package dev.phonk.editor.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.phonk.editor.BuildConfig
import dev.phonk.editor.R
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.settings.AppearanceBottomSheet
import dev.phonk.editor.ui.settings.AspectRatioBottomSheet
import dev.phonk.editor.ui.settings.ClearCacheDialog
import dev.phonk.editor.ui.settings.DeleteAccountDialog
import dev.phonk.editor.ui.settings.ExportQualityBottomSheet
import dev.phonk.editor.ui.settings.FrameRateBottomSheet
import dev.phonk.editor.ui.settings.LanguageBottomSheet
import dev.phonk.editor.ui.settings.PreviewQualityBottomSheet
import dev.phonk.editor.ui.settings.ProfileCard
import dev.phonk.editor.ui.settings.PhonkSettingsCard
import dev.phonk.editor.ui.settings.ProUpgradeCard
import dev.phonk.editor.ui.settings.SettingsActionRow
import dev.phonk.editor.ui.settings.SettingsDivider
import dev.phonk.editor.ui.settings.SettingsFooter
import dev.phonk.editor.ui.settings.SettingsHeader
import dev.phonk.editor.ui.settings.SettingsInfoDialog
import dev.phonk.editor.ui.settings.SettingsNavigationRow
import dev.phonk.editor.ui.settings.SettingsRow
import dev.phonk.editor.ui.settings.SettingsSectionHeader
import dev.phonk.editor.ui.settings.SettingsSwitchRow
import dev.phonk.editor.ui.settings.StorageCard
import dev.phonk.editor.ui.settings.settingsPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * PHONK EDITOR settings screen — always-dark premium redesign.
 *
 * Matches the home screen identity (near-black surfaces, neon purple accents)
 * and keeps the full real-data plumbing: every picker/toggle persists through
 * [SettingsManager], storage sizes are computed from the device, cache is
 * actually cleared and destructive actions are confirmed before running.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onManageStorage: (() -> Unit)? = null,
) {
    val palette = settingsPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { ProjectStore(context) }

    // Sheet/dialog state
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showExportQualitySheet by remember { mutableStateOf(false) }
    var showFrameRateSheet by remember { mutableStateOf(false) }
    var showAspectRatioSheet by remember { mutableStateOf(false) }
    var showPreviewQualitySheet by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Info dialog content (hoisted so it can be referenced from non-composable lambdas)
    val privacyInfo = stringResource(R.string.settings_privacy_dialog_title) to
        stringResource(R.string.settings_privacy_dialog_message)
    val termsInfo = stringResource(R.string.settings_terms_dialog_title) to
        stringResource(R.string.settings_terms_dialog_message)
    val permissionsInfo = stringResource(R.string.settings_permissions_dialog_title) to
        stringResource(R.string.settings_permissions_dialog_message)
    val helpInfo = stringResource(R.string.settings_help_dialog_title) to
        stringResource(R.string.settings_help_dialog_message)
    val licensesInfo = stringResource(R.string.settings_licenses_dialog_title) to
        stringResource(R.string.settings_licenses_dialog_message)
    val websiteInfo = stringResource(R.string.settings_website_dialog_title) to
        stringResource(R.string.settings_website_dialog_message)

    // Current persisted values
    val themeMode = SettingsManager.themeMode
    val languageCode = SettingsManager.languageCode
    val devRevealed = SettingsManager.devRevealed
    val defaultResolution = SettingsManager.defaultResolution
    val defaultFps = SettingsManager.defaultFps
    val defaultAspect = SettingsManager.defaultAspect
    val previewQuality = SettingsManager.previewQuality
    val hardwareAccel = SettingsManager.hardwareAccel
    val autosave = SettingsManager.autosave
    val autoBackup = SettingsManager.autoBackup
    val lowPowerMode = SettingsManager.lowPowerMode
    val notificationsEnabled = SettingsManager.notificationsEnabled
    val exportNotifications = SettingsManager.exportNotifications
    val featureNotifications = SettingsManager.featureNotifications

    // Hidden developer reveal gesture (7 rapid taps on the title)
    var devTaps by remember { mutableIntStateOf(0) }

    fun applyLanguage(code: String) {
        if (languageCode == code) return
        SettingsManager.setLanguage(context, code)
        (context as? Activity)?.recreate()
    }

    fun storageUsage(): Triple<Long, Long, Long> {
        val projectsDir = File(context.filesDir, "projects")
        val cacheDir = context.cacheDir

        fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

        val projectsSize = dirSize(projectsDir)
        val cacheSize = dirSize(cacheDir)
        val total = runCatching {
            Environment.getExternalStorageDirectory().totalSpace
        }.getOrDefault(0L)
        val free = maxOf(total - projectsSize - cacheSize, 0L)
        return Triple(projectsSize, cacheSize, free)
    }

    fun clearCache(): String {
        val cacheDir = context.cacheDir
        val sizeBefore = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return Formatter.formatShortFileSize(context, sizeBefore)
    }

    fun deleteAccount() {
        store.listRecent().forEach { store.delete(it.id) }
        SettingsManager.resetAll(context)
        Toast.makeText(context, R.string.account_deleted, Toast.LENGTH_SHORT).show()
    }

    fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_name))
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_social_share)))
        }.onFailure {
            Toast.makeText(context, R.string.settings_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun rateApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    fun openSocial() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.app_name))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_social_share_subject))
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_social_share)))
        }.onFailure {
            Toast.makeText(context, R.string.settings_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }

    // Real storage numbers
    val (projectsBytes, cacheBytes, freeBytes) = storageUsage()
    val usedBytes = projectsBytes + cacheBytes
    val totalBytes = usedBytes + freeBytes
    val usedPercent = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
    val usedLabel = Formatter.formatShortFileSize(context, usedBytes)

    val languageLabel = when (languageCode) {
        SettingsManager.LANG_EN -> stringResource(R.string.language_english)
        SettingsManager.LANG_HI -> stringResource(R.string.language_hindi)
        else -> stringResource(R.string.language_system)
    }
    val themeLabel = when (themeMode) {
        SettingsManager.THEME_LIGHT -> stringResource(R.string.theme_light)
        SettingsManager.THEME_DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

    // Always light status/nav bar icons over the dark settings background.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = statusTop + 8.dp,
                bottom = navBottom + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsHeader(
                    onBack = onBack,
                    title = stringResource(R.string.settings),
                    onTitleClick = {
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

            // ===== PROFILE =====
            item {
                ProfileCard(onClick = { onOpenProfile?.invoke() })
            }

            // ===== PHONK PRO =====
            item {
                ProUpgradeCard(onUpgrade = { showProDialog = true })
            }

            // ===== APP SETTINGS =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_app))
            }
            item {
                PhonkSettingsCard {
                    SettingsNavigationRow(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.language),
                        subtitle = languageLabel,
                        value = languageLabel,
                        onClick = { showLanguageSheet = true },
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(R.string.settings_appearance),
                        subtitle = themeLabel,
                        value = themeLabel,
                        onClick = { showAppearanceSheet = true },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.Save,
                        title = stringResource(R.string.settings_auto_save),
                        subtitle = stringResource(R.string.settings_auto_save_hint),
                        checked = autosave,
                        onCheckedChange = { SettingsManager.setAutosave(context, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.CloudUpload,
                        title = stringResource(R.string.settings_auto_backup),
                        subtitle = stringResource(R.string.settings_auto_backup_hint),
                        checked = autoBackup,
                        onCheckedChange = { SettingsManager.setAutoBackup(context, it) },
                    )
                }
            }

            // ===== EDITOR =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_editor))
            }
            item {
                PhonkSettingsCard {
                    SettingsNavigationRow(
                        icon = Icons.Filled.HighQuality,
                        title = stringResource(R.string.settings_export_quality),
                        subtitle = defaultResolution,
                        value = defaultResolution,
                        onClick = { showExportQualitySheet = true },
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Filled.Speed,
                        title = stringResource(R.string.settings_frame_rate),
                        subtitle = "$defaultFps FPS",
                        value = "$defaultFps FPS",
                        onClick = { showFrameRateSheet = true },
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Filled.AspectRatio,
                        title = stringResource(R.string.settings_aspect_ratio),
                        subtitle = defaultAspect,
                        value = defaultAspect,
                        onClick = { showAspectRatioSheet = true },
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Filled.Visibility,
                        title = stringResource(R.string.settings_preview_quality),
                        subtitle = previewQuality,
                        value = previewQuality,
                        onClick = { showPreviewQualitySheet = true },
                    )
                }
            }

            // ===== PERFORMANCE =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_performance))
            }
            item {
                PhonkSettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.settings_hardware_accel),
                        subtitle = stringResource(R.string.settings_hardware_accel_hint),
                        checked = hardwareAccel,
                        onCheckedChange = { SettingsManager.setHardwareAccel(context, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.BatteryStd,
                        title = stringResource(R.string.settings_low_power_mode),
                        subtitle = stringResource(R.string.settings_low_power_mode_hint),
                        checked = lowPowerMode,
                        onCheckedChange = { SettingsManager.setLowPowerMode(context, it) },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.settings_cache),
                        subtitle = Formatter.formatShortFileSize(context, cacheBytes),
                        actionLabel = stringResource(R.string.clear),
                        onClick = { showClearCacheDialog = true },
                    )
                }
            }

            // ===== NOTIFICATIONS =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_notifications))
            }
            item {
                PhonkSettingsCard {
                    SettingsSwitchRow(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.settings_notifications),
                        subtitle = stringResource(R.string.settings_notifications_hint),
                        checked = notificationsEnabled,
                        onCheckedChange = { SettingsManager.setNotifications(context, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.VideoLibrary,
                        title = stringResource(R.string.settings_export_completed),
                        subtitle = stringResource(R.string.settings_export_completed_hint),
                        checked = exportNotifications,
                        onCheckedChange = { SettingsManager.setExportNotifications(context, it) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.settings_new_features),
                        subtitle = stringResource(R.string.settings_new_features_hint),
                        checked = featureNotifications,
                        onCheckedChange = { SettingsManager.setFeatureNotifications(context, it) },
                    )
                }
            }

            // ===== STORAGE =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_storage))
            }
            item {
                StorageCard(
                    usedLabel = usedLabel,
                    usedPercent = usedPercent,
                    projectsSize = Formatter.formatShortFileSize(context, projectsBytes),
                    cacheSize = Formatter.formatShortFileSize(context, cacheBytes),
                    freeSize = Formatter.formatShortFileSize(context, freeBytes),
                    onManage = { onManageStorage?.invoke() },
                )
            }

            // ===== PRIVACY & SECURITY =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_privacy))
            }
            item {
                PhonkSettingsCard {
                    SettingsRow(
                        icon = Icons.Filled.PrivacyTip,
                        title = stringResource(R.string.settings_privacy_policy),
                        onClick = { infoDialog = privacyInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.settings_terms_of_service),
                        onClick = { infoDialog = termsInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.settings_data_permissions),
                        onClick = { infoDialog = permissionsInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.DeleteForever,
                        title = stringResource(R.string.settings_delete_account),
                        onClick = { showDeleteAccountDialog = true },
                        iconTint = palette.danger,
                        titleColor = palette.danger,
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = palette.danger,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }

            // ===== SUPPORT =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_support))
            }
            item {
                PhonkSettingsCard {
                    SettingsRow(
                        icon = Icons.Filled.Help,
                        title = stringResource(R.string.settings_help_center),
                        onClick = { infoDialog = helpInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Headset,
                        title = stringResource(R.string.settings_contact_support),
                        onClick = ::shareApp,
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.BugReport,
                        title = stringResource(R.string.settings_report_problem),
                        onClick = ::shareApp,
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.settings_rate_phonk),
                        onClick = ::rateApp,
                        trailing = { Chevron() },
                    )
                }
            }

            // ===== ABOUT =====
            item {
                SettingsSectionHeader(stringResource(R.string.settings_sec_about))
            }
            item {
                PhonkSettingsCard {
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_app_version),
                        trailing = {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textSecondary,
                            )
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.settings_open_source_licenses),
                        onClick = { infoDialog = licensesInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.settings_website),
                        onClick = { infoDialog = websiteInfo },
                        trailing = { Chevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Share,
                        title = stringResource(R.string.settings_follow_us),
                        onClick = ::openSocial,
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SocialDot(Icons.Filled.PhotoCamera, onClick = ::openSocial)
                                SocialDot(Icons.Filled.PlayCircleOutline, onClick = ::openSocial)
                                SocialDot(Icons.Filled.MusicNote, onClick = ::openSocial)
                            }
                        },
                    )
                }
            }

            // ===== DEVELOPER (hidden until revealed) =====
            if (devRevealed) {
                item {
                    SettingsRow(
                        icon = Icons.Filled.BugReport,
                        title = stringResource(R.string.debug_screen_title),
                        onClick = onOpenDeveloper,
                        trailing = { Chevron() },
                    )
                }
            }

            item {
                SettingsFooter()
            }
        }
    }

    // ===== SHEETS =====
    if (showLanguageSheet) {
        LanguageBottomSheet(
            selected = languageCode,
            onSelect = { applyLanguage(it) },
            onDismiss = { showLanguageSheet = false },
        )
    }
    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            selected = themeMode,
            onSelect = { SettingsManager.setThemeMode(context, it) },
            onDismiss = { showAppearanceSheet = false },
        )
    }
    if (showExportQualitySheet) {
        ExportQualityBottomSheet(
            selected = defaultResolution,
            onSelect = {
                SettingsManager.setDefaultResolution(context, it)
                showExportQualitySheet = false
            },
            onDismiss = { showExportQualitySheet = false },
        )
    }
    if (showFrameRateSheet) {
        FrameRateBottomSheet(
            selected = defaultFps,
            onSelect = {
                SettingsManager.setDefaultFps(context, it)
                showFrameRateSheet = false
            },
            onDismiss = { showFrameRateSheet = false },
        )
    }
    if (showAspectRatioSheet) {
        AspectRatioBottomSheet(
            selected = defaultAspect,
            onSelect = {
                SettingsManager.setDefaultAspect(context, it)
                showAspectRatioSheet = false
            },
            onDismiss = { showAspectRatioSheet = false },
        )
    }
    if (showPreviewQualitySheet) {
        PreviewQualityBottomSheet(
            selected = previewQuality,
            onSelect = {
                SettingsManager.setPreviewQuality(context, it)
                showPreviewQualitySheet = false
            },
            onDismiss = { showPreviewQualitySheet = false },
        )
    }

    // ===== DIALOGS =====
    if (showClearCacheDialog) {
        ClearCacheDialog(
            onConfirm = {
                val cleared = clearCache()
                showClearCacheDialog = false
                Toast.makeText(context, context.getString(R.string.cache_cleared, cleared), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearCacheDialog = false },
        )
    }
    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                deleteAccount()
                showDeleteAccountDialog = false
            },
            onDismiss = { showDeleteAccountDialog = false },
        )
    }
    if (showProDialog) {
        SettingsInfoDialog(
            title = stringResource(R.string.pro_billing_title),
            message = stringResource(R.string.pro_billing_message),
            onDismiss = { showProDialog = false },
        )
    }
    infoDialog?.let { (title, message) ->
        SettingsInfoDialog(
            title = title,
            message = message,
            onDismiss = { infoDialog = null },
        )
    }
}

@Composable
private fun Chevron() {
    val palette = settingsPalette()
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = palette.muted,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun SocialDot(icon: ImageVector, onClick: () -> Unit) {
    val palette = settingsPalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(palette.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
