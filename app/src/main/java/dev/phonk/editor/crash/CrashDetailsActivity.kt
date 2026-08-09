package dev.phonk.editor.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import dev.phonk.editor.R
import java.io.File

/**
 * Full crash report for a historical entry selected from [CrashHistoryActivity].
 * Read-only viewer with copy support; no app subsystems are initialized.
 */
class CrashDetailsActivity : Activity() {

    companion object {
        const val EXTRA_CRASH_FILENAME = "crash_detail_filename"
    }

    private val repo by lazy { CrashLogRepository(this) }
    private val formatter by lazy { CrashFormatter(CrashFormatter.Strings.from(this)) }
    private var crashInfo: CrashInfo? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(dev.phonk.editor.settings.SettingsManager.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_details)

        val requestedName = intent.getStringExtra(EXTRA_CRASH_FILENAME)
        val entry = requestedName?.let { name ->
            repo.listCrashFiles().firstOrNull { it.name == name }
                ?.let { f -> repo.readCrash(f)?.let { f to it } }
        } ?: repo.loadLatest()

        val (file, info) = entry ?: run {
            Toast.makeText(this, R.string.crash_no_logs, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        crashInfo = info

        findViewById<TextView>(R.id.tv_details_filename).text = file.name
        findViewById<TextView>(R.id.tv_details_exception).text = info.exceptionType
        findViewById<TextView>(R.id.tv_details_message).text =
            CrashRedactor.redact(info.message) ?: getString(R.string.crash_no_message)
        findViewById<TextView>(R.id.tv_details_value_thread).text = formatter.threadLine(info)
        findViewById<TextView>(R.id.tv_details_value_time).text = formatter.time(info.timestamp)
        findViewById<TextView>(R.id.tv_details_value_version).text = formatter.versionLine(info)
        findViewById<TextView>(R.id.tv_details_value_android).text = formatter.androidLine(info)
        findViewById<TextView>(R.id.tv_details_value_device).text = formatter.deviceLine(info)
        findViewById<TextView>(R.id.tv_details_value_abi).text = info.supportedAbis.joinToString(", ")

        val stack = findViewById<TextView>(R.id.tv_details_stack_trace)
        stack.text = CrashRedactor.redact(info.stackTrace)
            ?.takeIf { it.isNotBlank() } ?: getString(R.string.crash_no_stack_trace)
        stack.setOnLongClickListener {
            copyComplete(info, showToast = true)
            true
        }

        val cause = findViewById<TextView>(R.id.tv_details_cause)
        val causeText = CrashRedactor.redact(info.causeChain)
        if (causeText?.isNotBlank() == true) {
            findViewById<TextView>(R.id.tv_details_cause_label).visibility = View.VISIBLE
            cause.visibility = View.VISIBLE
            cause.text = causeText
        }

        findViewById<Button>(R.id.btn_details_copy).setOnClickListener {
            if (copyCompleteCrashLog(showToast = false)) {
                Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btn_details_back).setOnClickListener { finish() }
    }

    private fun copyCompleteCrashLog(showToast: Boolean): Boolean {
        val info = crashInfo ?: return false
        return copyComplete(info, showToast)
    }

    private fun copyComplete(info: CrashInfo, showToast: Boolean): Boolean {
        val text = formatter.fullReport(info)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_clipboard_label), text))
        if (showToast) Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
        return true
    }
}
