package dev.phonk.editor.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.BuildConfig
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.components.BottomNav
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.ui.components.PhonkPanel

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigate: (NavTab) -> Unit = {},
) {
    val context = LocalContext.current
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground,
                    )
                }
                Text(
                    "Profile",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                // App info card
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surface, MaterialTheme.shapes.medium)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Phonk Drop Editor",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Edit. Sync. Drop.",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }

                // Stats
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        icon = Icons.Filled.Storage,
                        label = "Projects",
                        value = "$projectCount",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.Timer,
                        label = "Theme",
                        value = when (SettingsManager.themeMode) {
                            SettingsManager.THEME_LIGHT -> "Light"
                            SettingsManager.THEME_DARK -> "Dark"
                            else -> "System"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // About button
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { showAbout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("About", color = scheme.primary)
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
            title = { Text("About Phonk Drop Editor") },
            text = {
                Column {
                    Text("Version: ${BuildConfig.VERSION_NAME}")
                    Spacer(Modifier.height(8.dp))
                    Text("A professional mobile video editor for phonk music videos.")
                    Spacer(Modifier.height(8.dp))
                    Text("Features:")
                    Text("• Beat-synced editing")
                    Text("• Real-time effects")
                    Text("• Color grading")
                    Text("• Text & overlay support")
                    Text("• FFmpeg export")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("OK")
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}
