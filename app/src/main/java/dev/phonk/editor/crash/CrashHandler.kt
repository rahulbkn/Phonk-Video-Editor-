package dev.phonk.editor.crash

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.Process
import dev.phonk.editor.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-wide [Thread.UncaughtExceptionHandler].
 *
 * Installed once from [CrashApplication]. Android consults the default
 * uncaught-exception handler for any thread (main or background) that dies
 * without a catch, so this captures both kinds of Java/Kotlin crashes.
 *
 * The handler keeps its work small: capture an immutable snapshot, persist it,
 * reset session flags, best-effort start [CrashLogActivity], then delegate to
 * the previous handler so the platform can terminate the process normally.
 *
 * Native SIGSEGV/SIGABRT/SIGBUS cannot be caught here; the optional
 * [CrashNativeHandler] covers that path, clearly separated.
 */
class CrashHandler private constructor(
    private val appContext: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val isForeground: () -> Boolean,
) : Thread.UncaughtExceptionHandler {

    companion object {
        private val installed = AtomicBoolean(false)
        private val handling = AtomicBoolean(false)

        /**
         * Installs the handler exactly once, chaining to whatever was already
         * registered so nothing else (e.g. ANR logging) is damaged.
         */
        fun install(appContext: Context, isForeground: () -> Boolean = { false }): Boolean {
            if (!installed.compareAndSet(false, true)) return false
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext, previous, isForeground))
            return true
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!handling.compareAndSet(false, true)) {
            // A second fatal on this thread path; just hand back to Android.
            previousHandler?.uncaughtException(thread, throwable)
            Process.killProcess(Process.myPid())
            return
        }

        try {
            foregroundHint = isForeground()
            val info = buildCrashInfo(thread, throwable)
            val repo = CrashLogRepository(appContext)
            repo.saveCrash(info)
            CrashLogRepository.markPendingCrash(appContext)
            // Reset the crash-screen guard so the *next* process launch shows it.
            CrashLogRepository.clearCrashScreenOpened(appContext)
            launchCrashScreen()
        } catch (ignored: Throwable) {
            // The crash report must never take the process down harder.
        } finally {
            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (ignored: Throwable) {
                // previous handler is allowed to do anything; keep going.
            }
            Process.killProcess(Process.myPid())
            runCatching { System.exit(1) }
        }
    }

    private var foregroundHint: Boolean = false

    private fun buildCrashInfo(thread: Thread, throwable: Throwable): CrashInfo {
        val message = CrashRedactor.redact(runCatching { throwable.message }.getOrNull())
        val stack = CrashRedactor.redact(stackTraceText(throwable)) ?: "(no stack trace)"
        val cause = buildCauseChain(throwable)
        return CrashInfo(
            timestamp = System.currentTimeMillis(),
            exceptionType = runCatching { throwable.javaClass.name }.getOrDefault("UnknownException"),
            message = message,
            stackTrace = stack,
            causeChain = CrashRedactor.redact(cause) ?: cause,
            threadName = runCatching { thread.name }.getOrNull(),
            threadId = runCatching { thread.id }.getOrNull(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            androidVersion = runCatching { Build.VERSION.RELEASE }.getOrNull(),
            sdkInt = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0),
            manufacturer = runCatching { Build.MANUFACTURER }.getOrNull(),
            model = runCatching { Build.MODEL }.getOrNull(),
            supportedAbis = supportedAbis(),
            appProcess = runCatching { "${appContext.packageName} (pid ${Process.myPid()})" }.getOrNull(),
            memoryInfo = memoryInfo(appContext),
            backgrounded = !foregroundHint,
            isDebugBuild = BuildConfig.DEBUG,
        )
    }

    /** Full stack printable text including "Caused by", "Suppressed" etc. */
    private fun stackTraceText(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun buildCauseChain(t: Throwable): String {
        val chain = mutableListOf<String>()
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 32) {
            val name = runCatching { current.javaClass.name }.getOrNull() ?: "Exception"
            val msg = current.message
            chain += if (msg.isNullOrBlank()) name else "$name: $msg"
            current = current.cause
            depth++
        }
        return chain.joinToString("\n")
    }

    private fun supportedAbis(): List<String> =
        if (Build.VERSION.SDK_INT >= 21) {
            runCatching { Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList())
        } else {
            listOfNotNull(runCatching { Build.CPU_ABI }.getOrNull())
        }

    private fun memoryInfo(ctx: Context): String? {
        return runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = mi.availMem / 1048576L
            val totalMb = mi.totalMem / 1048576L
            val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / 1048576L
            "available=%dMB total=%dMB nativeHeap=%dMB lowMemory=%b".format(
                availMb, totalMb, nativeHeapMb, mi.lowMemory,
            )
        }.getOrNull()
    }

    /**
     * Best-effort: bring the user back to the crash screen immediately. This is
     * intentionally not the primary delivery path; the persisted report plus
     * the pending flag guarantees the screen on the next launch even if this
     * intent dies with the process.
     */
    private fun launchCrashScreen() {
        try {
            val intent = Intent(appContext, CrashLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(CrashLogActivity.EXTRA_CRASH_SCREEN, true)
            appContext.startActivity(intent)
        } catch (ignored: Throwable) {
            // Next launch handles it.
        }
    }
}