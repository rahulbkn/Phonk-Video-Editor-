package dev.phonk.editor.crash

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.phonk.editor.R
import dev.phonk.editor.ui.MainActivity
import java.io.File

/**
 * Simple, stable crash report screen. Deliberately framework-light: plain
 * Activity + Views, mono/selectable text, clipboard + sharesheet + delete.
 * It never initializes video/FFmpeg/database/network code, so it cannot drag
 * the whole app into a crash -> crash-screen -> crash loop.
 */
class CrashLogActivity : Activity() {

    companion object {
        /** Marks an intent coming from the startup crash redirect. */
        const val EXTRA_CRASH_SCREEN = "extra_crash_screen"
        internal const val EXTRA_CRASH_FILENAME = "extra_crash_filename"
        private const val SHARE_FILE_NAME = "crash_report.txt"

        /**
         * Called on main-launch. Returns true when a pending crash exists and
         * guarantees [CrashLogActivity] is on screen; false when the app should
         * start normally. The startup guard prevents any recursive relaunch.
         */
        fun startIfPending(context: Context): Boolean {
            if (!CrashLogRepository.hasPendingCrash(context)) return false
            if (!CrashLogRepository.wasCrashScreenOpened(context)) {
                CrashLogRepository.markCrashScreenOpened(context)
                val intent = Intent(context, CrashLogActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CRASH_SCREEN, true)
                runCatching { context.startActivity(intent) }.onFailure {
                    // Process is too unstable to open the screen now; the next
                    // launch retries because the pending flag is still set.
                    CrashLogRepository.clearCrashScreenOpened(context)
                }
            }
            return true
        }
    }

    private val repo by lazy { CrashLogRepository(this) }
    private val formatter by lazy { CrashFormatter(CrashFormatter.Strings.from(this)) }

    private var currentFile: File? = null
    private var crashInfo: CrashInfo? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(dev.phonk.editor.settings.SettingsManager.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_log)

        val requestedName = intent.getStringExtra(EXTRA_CRASH_FILENAME)
        val entry = requestedName?.let { name ->
            repo.listCrashFiles().firstOrNull { it.name == name }
                ?.let { f -> repo.readCrash(f)?.let { f to it } }
        } ?: repo.loadLatest()

        currentFile = entry?.first
        crashInfo = entry?.second

        if (crashInfo == null) {
            // Nothing to display; do not block the app on an empty screen.
            Toast.makeText(this, R.string.crash_no_logs, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews(crashInfo!!)
        bindButtons()
    }

    private fun bindViews(info: CrashInfo) {
        text(R.id.tv_crash_value_exception).text = info.exceptionType
        text(R.id.tv_crash_value_message).text =
            CrashRedactor.redact(info.message) ?: getString(R.string.crash_no_message)
        text(R.id.tv_crash_value_thread).text = formatter.threadLine(info)
        text(R.id.tv_crash_value_time).text = formatter.time(info.timestamp)
        text(R.id.tv_crash_value_version).text = formatter.versionLine(info)
        text(R.id.tv_crash_value_android).text = formatter.androidLine(info)
        text(R.id.tv_crash_value_device).text = formatter.deviceLine(info)

        val stackTraceView = text(R.id.tv_crash_stack_trace)
        val stack = CrashRedactor.redact(info.stackTrace)
        stackTraceView.text = stack?.takeIf { it.isNotBlank() } ?: getString(R.string.crash_no_stack_trace)

        // Long-press anywhere in the trace copies the complete report; text is
        // still selectable so the system "select all / copy" menu also works.
        stackTraceView.setOnLongClickListener {
            copyCompleteCrashLog(showToast = true)
            true
        }
    }

    private fun bindButtons() {
        button(R.id.btn_crash_copy).setOnClickListener {
            if (copyCompleteCrashLog(showToast = false)) {
                toast(R.string.crash_log_copied)
            }
        }
        button(R.id.btn_crash_share).setOnClickListener { shareCrashLog() }
        button(R.id.btn_crash_continue).setOnClickListener { markReviewedAndGoToApp() }
        button(R.id.btn_crash_clear).setOnClickListener { confirmClearCrashLog() }
        button(R.id.btn_crash_history).setOnClickListener {
            startActivity(Intent(this, CrashHistoryActivity::class.java))
        }
    }

    /**
     * Copies the complete formatted report (every field, full stack trace,
     * cause chain) and returns whether it succeeded.
     */
    internal fun copyCompleteCrashLog(showToast: Boolean = true): Boolean {
        val info = crashInfo ?: return false
        val text = formatter.fullReport(info)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_clipboard_label), text))
        if (showToast) toast(R.string.crash_log_copied)
        return true
    }

    private fun shareCrashLog() {
        val info = crashInfo ?: return
        runCatching {
            val text = formatter.fullReport(info)
            val shareFile = File(cacheDir, SHARE_FILE_NAME)
            shareFile.writeText(text)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", shareFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.crash_share_chooser)))
        }.onFailure {
            // Fall back to a plain text share if streaming fails.
            val text = formatter.fullReport(info)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runCatching {
                startActivity(Intent.createChooser(send, getString(R.string.crash_share_chooser)))
            }
        }
    }

    private fun confirmClearCrashLog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_delete_title)
            .setMessage(R.string.crash_delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                repo.clearLatest()
                toast(R.string.crash_log_deleted)
                markReviewedAndGoToApp()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Clears the session guard/pending flags (so the same crash is never shown
     * again) and returns the user to the normal MainActivity.
     */
    internal fun markReviewedAndGoToApp() {
        CrashLogRepository.resetSessionState(this)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    @Deprecated("Back = leave the crash screen safely (same effect as Continue)")
    override fun onBackPressed() {
        CrashLogRepository.resetSessionState(this)
        finish()
    }

    private fun text(id: Int): TextView = findViewById(id)
    private fun button(id: Int): Button = findViewById(id)

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }
}
