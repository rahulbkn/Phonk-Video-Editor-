package dev.phonk.editor.crash

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OPTIONAL native (C/C++) crash support.
 *
 * This is deliberately separated from [CrashHandler]: the Kotlin/Java
 * uncaught-exception handler cannot catch SIGSEGV / SIGABRT / SIGBUS / SIGILL /
 * SIGFPE raised in C/C++. Instead this module registers minimal, async-safe
 * signal handlers (see app/src/main/cpp/crash_signal.cpp) that write a tiny
 * marker file and then re-raise with the previous behavior so the process is
 * terminated by the OS as it would normally be.
 *
 * On the next launch [CrashLogRepository.importNativePendingMarker] converts
 * that marker into a regular crash log entry. The signal handler never touches
 * Android UI, never runs normal allocation and never launches an Activity.
 */
object CrashNativeHandler {

    private const val TAG = "CrashNative"

    private val loadAttempted = AtomicBoolean(false)

    @Volatile
    private var libraryReady = false

    private fun ensureLibraryReady() {
        if (loadAttempted.compareAndSet(false, true)) {
            libraryReady = runCatching {
                System.loadLibrary("phonknative")
                true
            }.getOrDefault(false)
        }
    }

    /**
     * Arms the signal handler. Called from [CrashApplication.onCreate].
     * Silent no-op (logged only) if the native build does not provide it.
     */
    fun install(context: Context) {
        ensureLibraryReady()
        if (!libraryReady) {
            Log.w(TAG, "native crash support not available; Java handler only")
            return
        }
        runCatching {
            val markerPath = CrashLogRepository(context).nativePendingMarkerPath()
            val ok = nativeInstallCrashHandler(markerPath)
            Log.i(TAG, "native crash signal handler installed=$ok")
        }.onFailure { t ->
            Log.w(TAG, "failed to arm native crash signal handler", t)
        }
    }

    fun isInstalled(): Boolean {
        ensureLibraryReady()
        return libraryReady && runCatching { nativeIsCrashHandlerInstalled() }.getOrDefault(false)
    }

    /** Registers the async-signal-safe C++ handlers. See crash_signal.cpp. */
    external fun nativeInstallCrashHandler(markerPath: String): Boolean

    external fun nativeIsCrashHandlerInstalled(): Boolean
}