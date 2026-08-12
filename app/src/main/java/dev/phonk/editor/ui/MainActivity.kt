package dev.phonk.editor.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.BeatAnalyzerScreen
import dev.phonk.editor.ui.ProfileScreen
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.ui.home.PhonkHomeScreen
import dev.phonk.editor.ui.home.ProScreen
import dev.phonk.editor.ui.home.TemplatesScreen

/**
 * Navigation state holder that survives activity recreation (e.g., language change).
 * Maintains a proper back stack so system Back navigates through the history.
 * Scoped to the activity's ViewModelStore so it persists across recreate() calls.
 */
class NavigationViewModel : ViewModel() {
    /** Navigation stack — first element is always Home (the root). */
    private val stack: MutableList<Route> = mutableListOf(Route.Home)

    /** Current route (top of stack). */
    var route: Route by mutableStateOf(Route.Home)
        private set

    /** Navigate to a new screen, pushing it onto the stack. */
    fun navigateTo(route: Route) {
        if (route == this.route) return
        when (route) {
            // Root screens replace the entire stack
            Route.Home -> {
                stack.clear()
                stack.add(Route.Home)
            }
            else -> {
                // Avoid duplicate consecutive entries
                if (stack.lastOrNull() != route) {
                    stack.add(route)
                }
            }
        }
        this.route = route
    }

    /**
     * Pop the current route and return true if there was a previous screen.
     * Returns false if we're at the root (caller should allow activity to finish).
     */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        route = stack.last()
        return true
    }

    /** Whether we're at the root of the navigation stack. */
    val isAtRoot: Boolean get() = stack.size <= 1
}

/** Compose content; a tiny hand-rolled router keeps deps (and APK) small. */
@Composable
fun PhonkApp() {
    val navViewModel: NavigationViewModel = viewModel()
    val route = navViewModel.route
    var openCreateSheet by remember { mutableStateOf(false) }

    // System Back button handler: pops the navigation stack instead of closing the app.
    // When at the root screen, disables itself so the system can finish the activity.
    BackHandler(enabled = !navViewModel.isAtRoot) {
        navViewModel.pop()
    }

    fun navigateTab(tab: NavTab) {
        when (tab) {
            NavTab.CREATE -> {
                openCreateSheet = true
                navViewModel.navigateTo(Route.Home)
            }
            NavTab.HOME -> navViewModel.navigateTo(Route.Home)
            NavTab.TEMPLATES -> navViewModel.navigateTo(Route.Templates)
            NavTab.PROJECTS -> navViewModel.navigateTo(Route.Projects)
            NavTab.PROFILE -> navViewModel.navigateTo(Route.Profile)
        }
    }

    when (route) {
        Route.Home -> PhonkHomeScreen(
            onOpen = { p ->
                navViewModel.navigateTo(Route.Editor(p.id))
            },
            onOpenSettings = {
                navViewModel.navigateTo(Route.Settings)
            },
            onOpenPro = {
                navViewModel.navigateTo(Route.Pro)
            },
            onOpenBeatAnalyzer = {
                navViewModel.navigateTo(Route.Beats)
            },
            onNavigate = { tab ->
                if (tab == NavTab.CREATE) {
                    openCreateSheet = true
                } else {
                    navigateTab(tab)
                }
            },
            openCreateSheet = openCreateSheet,
            onCreateSheetHandled = { openCreateSheet = false },
        )
        Route.Templates -> TemplatesScreen(
            onBack = { navViewModel.pop() },
            onOpen = { p -> navViewModel.navigateTo(Route.Editor(p.id)) },
            onNavigate = { tab -> navigateTab(tab) },
        )
        Route.Pro -> ProScreen(
            onBack = { navViewModel.pop() },
        )
        Route.Projects -> ProjectsScreen(
            onBack = { navViewModel.pop() },
            onOpen = { p -> navViewModel.navigateTo(Route.Editor(p.id)) },
            onNavigate = { tab -> navigateTab(tab) },
        )
        Route.Beats -> BeatAnalyzerScreen(
            onBack = { navViewModel.pop() },
        )
        Route.Profile -> ProfileScreen(
            onBack = { navViewModel.pop() },
            onNavigate = { tab -> navigateTab(tab) },
        )
        is Route.Editor -> EditorScreen(
            projectId = route.projectId,
            onBack = { navViewModel.pop() },
        )
        Route.Settings -> SettingsScreen(
            onBack = { navViewModel.pop() },
            onOpenDeveloper = { navViewModel.navigateTo(Route.Debug) },
            onOpenProfile = { navViewModel.navigateTo(Route.Profile) },
            onManageStorage = { navViewModel.navigateTo(Route.Projects) },
        )
        Route.Debug -> DebugScreen(
            onBack = { navViewModel.pop() },
        )
    }
}

sealed interface Route {
    data object Home : Route
    data object Templates : Route
    data object Projects : Route
    data object Beats : Route
    data object Profile : Route
    data object Pro : Route
    data class Editor(val projectId: String) : Route
    data object Settings : Route
    data object Debug : Route
}

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
