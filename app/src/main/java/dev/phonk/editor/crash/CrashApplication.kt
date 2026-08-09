package dev.phonk.editor.crash

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

/**
 * Entry point that registers the crash pipeline before any app code runs:
 *
 * 1. Installs [CrashHandler] (default uncaught-exception handler).
 * 2. Optionally arms the native signal handler (see [CrashNativeHandler]).
 * 3. Imports any native crash marker left by a previous process.
 * 4. If a crash from the previous process is pending, opens
 *    [CrashLogActivity] instead of letting the home screen take over.
 *
 * The `wasCrashScreenOpened` guard guarantees the crash screen can never be
 * reopened recursively, so the app cannot enter a crash -> crash-screen ->
 * crash-loop.
 */
class CrashApplication : Application() {

    private var startedCount = 0
        set(value) {
            field = value
            foreground = value > 0
        }

    @Volatile
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(newCallbacks())
        CrashHandler.install(this) { foreground }

        // Optional native (C++/JNI) crash support. Purely additive and clearly
        // separated from the Java/Kotlin handler above. Must never break
        // startup: a failure here would silently swallow the crash screen.
        runCatching { CrashNativeHandler.install(this) }

        val repo = CrashLogRepository(this)
        val imported = runCatching { repo.importNativePendingMarker() }.getOrNull()
        if (imported != null) {
            runCatching {
                repo.saveCrash(imported)
                CrashLogRepository.markPendingCrash(this)
                CrashLogRepository.clearCrashScreenOpened(this)
            }
        }

        if (CrashLogRepository.hasPendingCrash(this) &&
            !CrashLogRepository.wasCrashScreenOpened(this)
        ) {
            // Only one in-process launch; the guard stops any loop.
            CrashLogRepository.markCrashScreenOpened(this)
            val intent = Intent(this, CrashLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(CrashLogActivity.EXTRA_CRASH_SCREEN, true)
            runCatching { startActivity(intent) }.onFailure {
                CrashLogRepository.clearCrashScreenOpened(this)
            }
        }
    }

    private fun newCallbacks(): Application.ActivityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedCount++
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedCount > 0) startedCount--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        }
}