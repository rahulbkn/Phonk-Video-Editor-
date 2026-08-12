package dev.phonk.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.phonk.editor.BuildConfig
import dev.phonk.editor.R
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.ui.components.UiDimens
import dev.phonk.editor.ui.home.BottomNav

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (NavTab) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ProjectStore(context) }
    val projectCount = remember { store.listRecent().size }
    var showAbout by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme

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
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiDimens.spaceSm, vertical = UiDimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(UiDimens.touchTargetIcon)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = scheme.onBackground,
                    )
                }
                Text(
                    stringResource(R.string.profile_title),
                    fontSize = UiDimens.textSizeXl,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(start = UiDimens.spaceXs),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiDimens.screenPadding),
            ) {
                // App info card
                Spacer(Modifier.height(UiDimens.spaceLg))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surface, MaterialTheme.shapes.medium)
                        .padding(UiDimens.dialogPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(UiDimens.iconSizeLg * 2),
                    )
                    Spacer(Modifier.height(UiDimens.spaceMd))
                    Text(
                        stringResource(R.string.profile_app_name),
                        fontSize = UiDimens.textSizeSectionTitle,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.profile_about_version, BuildConfig.VERSION_NAME),
                        fontSize = UiDimens.textSizeSm,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(UiDimens.spaceXs))
                    Text(
                        stringResource(R.string.profile_tagline),
                        fontSize = UiDimens.textSizeSm,
                        color = scheme.onSurfaceVariant,
                    )
                }

                // Stats
                Spacer(Modifier.height(UiDimens.spaceLg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UiDimens.itemSpacing),
                ) {
                    StatCard(
                        icon = Icons.Filled.Storage,
                        label = stringResource(R.string.profile_stat_projects),
                        value = "$projectCount",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.Timer,
                        label = stringResource(R.string.profile_stat_theme),
                        value = when (SettingsManager.themeMode) {
                            SettingsManager.THEME_LIGHT -> stringResource(R.string.profile_theme_light)
                            SettingsManager.THEME_DARK -> stringResource(R.string.profile_theme_dark)
                            else -> stringResource(R.string.profile_theme_system)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // About button
                Spacer(Modifier.height(UiDimens.spaceLg))
                TextButton(
                    onClick = { showAbout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.size(UiDimens.spaceSm))
                    Text(stringResource(R.string.profile_about_button), color = scheme.primary)
                }

                Spacer(Modifier.height(80.dp))
            }

            BottomNav(
                activeTab = NavTab.PROFILE,
                onTabSelected = { tab ->
                    if (tab != NavTab.PROFILE) onNavigate(tab)
                },
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.profile_about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_about_version, BuildConfig.VERSION_NAME))
                    Spacer(Modifier.height(UiDimens.spaceSm))
                    Text(stringResource(R.string.profile_about_description))
                    Spacer(Modifier.height(UiDimens.spaceSm))
                    Text(stringResource(R.string.profile_about_features))
                    Text(stringResource(R.string.profile_about_feature_beatsync))
                    Text(stringResource(R.string.profile_about_feature_effects))
                    Text(stringResource(R.string.profile_about_feature_color))
                    Text(stringResource(R.string.profile_about_feature_text))
                    Text(stringResource(R.string.profile_about_feature_ffmpeg))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(scheme.surface, MaterialTheme.shapes.medium)
            .padding(UiDimens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(UiDimens.iconSizeLg),
        )
        Spacer(Modifier.height(UiDimens.spaceSm))
        Text(
            value,
            fontSize = UiDimens.textSizeXl,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Text(
            label,
            fontSize = UiDimens.textSizeSm,
            color = scheme.onSurfaceVariant,
        )
    }
}
