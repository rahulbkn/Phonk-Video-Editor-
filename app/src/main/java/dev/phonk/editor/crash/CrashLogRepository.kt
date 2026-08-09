package dev.phonk.editor.crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dev.phonk.editor.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the app-private crash log store (filesDir/crash_logs) plus the tiny
 * session flags used to drive the crash screen and prevent crash loops.
 *
 * - JSON files are written atomically (tmp file + fsync + rename).
 * - Up to [MAX_CRASH_FILES] logs are kept (newest first).
 * - No storage permission is used; everything stays under the app's private
 *   data directory.
 */
class CrashLogRepository(private val context: Context) {

    companion object {
        const val MAX_CRASH_FILES = 20

        private const val PREF_NAME = "crash_logs"
        private const val KEY_PENDING = "crash_pending"
        private const val KEY_SCREEN_OPENED = "crash_screen_opened"
        private const val NATIVE_MARKER = "native_crash_pending"

        private val stampFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        /** True when a crash needs attention on this launch/session. */
        fun hasPendingCrash(context: Context): Boolean =
            prefs(context).getBoolean(KEY_PENDING, false)

        /**
         * Pending/screen-opened flags are written with commit() (synchronous):
         * the crash handler runs moments before Process.killProcess, and an
         * async apply() can be lost on process death — which would silently
         * skip the crash screen on the next launch.
         */
        fun markPendingCrash(context: Context) {
            prefs(context).edit().putBoolean(KEY_PENDING, true).commit()
        }

        fun clearPendingCrash(context: Context) {
            prefs(context).edit().putBoolean(KEY_PENDING, false).apply()
        }

        /**
         * Guard that stops CrashLogActivity from being reopened recursively, or
         * launched again after the crash screen itself died. Only cleared once
         * the user has safely left the crash screen.
         */
        fun wasCrashScreenOpened(context: Context): Boolean {
            // Recursive re-entry guard: only meaningful inside the crashing
            // process check with the same prefs as the launcher.
            return prefs(context).getBoolean(KEY_SCREEN_OPENED, false)
        }

        fun markCrashScreenOpened(context: Context) {
            prefs(context).edit().putBoolean(KEY_SCREEN_OPENED, true).commit()
        }

        fun clearCrashScreenOpened(context: Context) {
            prefs(context).edit().putBoolean(KEY_SCREEN_OPENED, false).commit()
        }

        /** Reset every session flag (used by Clear/Continue flows). */
        fun resetSessionState(context: Context) {
            prefs(context).edit()
                .putBoolean(KEY_PENDING, false)
                .putBoolean(KEY_SCREEN_OPENED, false)
                .commit()
        }
    }

    private val logDir: File
        get() = File(context.filesDir, "crash_logs").apply { mkdirs() }

    /** Marker path handed to the optional native signal handler. */
    fun nativePendingMarkerPath(): String =
        File(logDir, NATIVE_MARKER).absolutePath

    private fun stampFileName(timeMillis: Long): String {
        val stamp = runCatching { stampFormat.format(Date(timeMillis)) }
            .getOrDefault(timeMillis.toString())
        return "crash_${stamp}.json"
    }

    /**
     * Persists a crash synchronously: write temp file, fsync, rename into
     * place. The crash handler runs under severe constraints (the process is
     * about to die) so this is deliberately simple and defensive.
     */
    fun saveCrash(info: CrashInfo): File? {
        return runCatching {
            val json = CrashInfoCodec().toJson(info)
            val target = File(logDir, stampFileName(info.timestamp))
            val tmp = File(logDir, "${target.name}.tmp")
            tmp.writeText(json)
            runCatching {
                FileOutputStream(tmp, true).use { it.fd.sync() }
            }
            val renamed = tmp.renameTo(target)
            if (!renamed) {
                target.writeText(json)
                runCatching { tmp.delete() }
            }
            enforceRetention()
            target
        }.getOrNull()
    }

    /** Keeps at most [MAX_CRASH_FILES] logs, deleting oldest first. */
    fun enforceRetention() {
        runCatching {
            listCrashFiles().drop(MAX_CRASH_FILES).forEach { f -> runCatching { f.delete() } }
        }
    }

    fun listCrashFiles(): List<File> =
        logDir.listFiles { f -> f.isFile && f.name.startsWith("crash_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latest(): File? = listCrashFiles().firstOrNull()

    fun readCrash(file: File): CrashInfo? =
        runCatching { file.readText() }.getOrNull()?.let { CrashInfoCodec().fromJson(it) }

    fun loadLatest(): Pair<File, CrashInfo>? {
        val file = latest() ?: return null
        val info = readCrash(file) ?: return null
        return file to info
    }

    fun loadAll(): List<Pair<File, CrashInfo>> =
        listCrashFiles().mapNotNull { f -> readCrash(f)?.let { f to it } }

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    /** Removes the reported crash and resets session flags. */
    fun clearLatest() {
        latest()?.let { delete(it) }
        clearPendingCrash(context)
        clearCrashScreenOpened(context)
    }

    fun clearHistory() {
        listCrashFiles().forEach { delete(it) }
        clearPendingCrash(context)
        clearCrashScreenOpened(context)
    }

    /**
     * Converts a marker written by the optional native signal handler into a
     * first-class [CrashInfo] log entry and removes the marker. Returns the
     * imported info so the caller can treat it as pending, or null.
     */
    fun importNativePendingMarker(): CrashInfo? {
        val marker = File(logDir, NATIVE_MARKER)
        if (!marker.exists()) return null
        val raw = runCatching { marker.readText() }.getOrNull()
        marker.delete()
        if (raw.isNullOrBlank()) return null

        var timestamp = System.currentTimeMillis()
        var signalName = "SIG(?)"
        runCatching {
            raw.lineSequence().forEach { line ->
                val parts = line.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    when (parts[0]) {
                        "ts" -> timestamp = parts[1].toLongOrNull() ?: timestamp
                        "sig" -> signalName = parts[1]
                    }
                }
            }
        }
        return CrashInfo(
            timestamp = timestamp,
            exceptionType = "NativeCrash($signalName)",
            message = "The app terminated in native code with signal $signalName.",
            stackTrace = CrashRedactor.redact(raw) ?: "(native stack not available)",
            causeChain = null,
            threadName = null,
            threadId = null,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            androidVersion = androidVersion(),
            sdkInt = androidSdkInt(),
            manufacturer = manufacturer(),
            model = model(),
            supportedAbis = supportedAbis(),
            appProcess = appProcess(),
            memoryInfo = memoryInfo(context),
            backgrounded = null,
            isDebugBuild = BuildConfig.DEBUG,
        )
    }

    /** Whether a native signal-handler marker is waiting to be imported. */
    fun hasNativeMarker(): Boolean = File(logDir, NATIVE_MARKER).exists()

    private fun androidVersion(): String? =
        runCatching { android.os.Build.VERSION.RELEASE }.getOrNull()

    private fun androidSdkInt(): Int =
        runCatching { android.os.Build.VERSION.SDK_INT }.getOrDefault(0)

    private fun manufacturer(): String? =
        runCatching { android.os.Build.MANUFACTURER }.getOrNull()

    private fun model(): String? =
        runCatching { android.os.Build.MODEL }.getOrNull()

    private fun supportedAbis(): List<String> =
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            runCatching { android.os.Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList())
        } else {
            listOfNotNull(runCatching { android.os.Build.CPU_ABI }.getOrNull())
        }

    private fun appProcess(): String? =
        runCatching { "${context.packageName} (pid ${android.os.Process.myPid()})" }.getOrNull()

    private fun memoryInfo(context: Context): String? {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availMb = mi.availMem / 1048576L
            val totalMb = mi.totalMem / 1048576L
            val low = mi.lowMemory
            val heapTotalMb = Runtime.getRuntime().totalMemory() / 1048576L
            val heapFreeMb = Runtime.getRuntime().freeMemory() / 1048576L
            "avail %d MB / total %d MB | heap %d/%d MB | low=%b".format(
                Locale.US, availMb, totalMb, heapFreeMb, heapTotalMb, low,
            )
        }.getOrNull()
    }
}