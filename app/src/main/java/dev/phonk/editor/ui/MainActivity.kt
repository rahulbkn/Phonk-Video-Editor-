package dev.phonk.editor.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.settings.SettingsManager

/** Compose content; a tiny hand-rolled router keeps deps (and APK) small. */
@Composable
fun PhonkApp() {
    // rememberSaveable keeps the current screen across activity recreation, so
    // switching the app language (which recreates the activity) does not kick
    // the user back to Home.
    var screen by rememberSaveable(stateSaver = routeSaver) {
        mutableStateOf<Route>(Route.Home)
    }
    when (val s = screen) {
        is Route.Home -> HomeScreen(
            onOpen = { p ->
                screen = Route.Editor(p.id)
            },
            onOpenSettings = {
                screen = Route.Settings
            },
        )
        is Route.Editor -> EditorScreen(
            projectId = s.projectId,
            onBack = { screen = Route.Home },
        )
        is Route.Settings -> SettingsScreen(
            onBack = { screen = Route.Home },
        )
    }
}

sealed interface Route {
    data object Home : Route
    data class Editor(val projectId: String) : Route
    data object Settings : Route
}

private val routeSaver = Saver<Route, String>(
    save = { route ->
        when (route) {
            Route.Home -> "home"
            is Route.Editor -> "editor:${route.projectId}"
            Route.Settings -> "settings"
        }
    },
    restore = { raw ->
        when {
            raw == "home" -> Route.Home
            raw == "settings" -> Route.Settings
            raw.startsWith("editor:") -> Route.Editor(raw.removePrefix("editor:"))
            else -> Route.Home
        }
    },
)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsManager.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)

        // If a previous process crashed, hand control to the crash screen
        // instead of starting the main UI; otherwise the crash report would be
        // buried under the launcher task.
        if (dev.phonk.editor.crash.CrashLogActivity.startIfPending(this)) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            PhonkTheme {
                PhonkApp()
            }
        }
    }
}