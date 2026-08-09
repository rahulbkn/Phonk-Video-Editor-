package dev.phonk.editor.crash

import android.content.Context
import dev.phonk.editor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds human-readable text from a [CrashInfo] for the UI, the clipboard and
 * the sharesheet. Every user-visible word comes from a [Strings] bundle
 * resolved from Android resources ([Strings.from]); the pure-JVM unit tests
 * pass an English bundle built in test code. Everything produced here is
 * already defanged by [CrashRedactor] so even a stored log containing an old
 * secret is redacted again before it leaves the device.
 */
class CrashFormatter(private val strings: Strings) {

    /**
     * Localized words and formats. No user-visible text is ever hardcoded
     * in the production formatting path — the default lives in res/values.
     */
    class Strings(
        val unknown: String,
        val deviceFormat: String,
        val androidFormat: String,
        val versionFormat: String,
        val threadWithIdFormat: String,
        val reportHeader: String,
        val reportField: String,
        val reportStackTraceHeading: String,
        val reportCauseHeading: String,
        val labelTime: String,
        val labelAppVersion: String,
        val labelAndroid: String,
        val labelDevice: String,
        val labelThread: String,
        val labelException: String,
        val labelMessage: String,
        val labelProcess: String,
        val labelMemory: String,
        val labelForeground: String,
        val labelAbis: String,
        val labelDebugBuild: String,
        val yes: String,
        val no: String,
        val trueWord: String,
        val falseWord: String,
    ) {
        companion object {
            /** Builds the localized bundle from Android resources. */
            fun from(context: Context): Strings = Strings(
                unknown = context.getString(R.string.crash_unknown),
                deviceFormat = context.getString(R.string.crash_device_format),
                androidFormat = context.getString(R.string.crash_android_format),
                versionFormat = context.getString(R.string.crash_version_format),
                threadWithIdFormat = context.getString(R.string.crash_thread_with_id),
                reportHeader = context.getString(R.string.crash_report_header),
                reportField = context.getString(R.string.crash_report_field),
                reportStackTraceHeading = context.getString(R.string.crash_report_stack_trace),
                reportCauseHeading = context.getString(R.string.crash_report_cause),
                labelTime = context.getString(R.string.crash_time),
                labelAppVersion = context.getString(R.string.crash_app_version),
                labelAndroid = context.getString(R.string.crash_android_version),
                labelDevice = context.getString(R.string.crash_device),
                labelThread = context.getString(R.string.crash_thread),
                labelException = context.getString(R.string.crash_exception),
                labelMessage = context.getString(R.string.crash_message),
                labelProcess = context.getString(R.string.crash_report_process),
                labelMemory = context.getString(R.string.crash_report_memory),
                labelForeground = context.getString(R.string.crash_report_foreground),
                labelAbis = context.getString(R.string.crash_report_abis),
                labelDebugBuild = context.getString(R.string.crash_report_debug),
                yes = context.getString(R.string.crash_yes),
                no = context.getString(R.string.crash_no),
                trueWord = context.getString(R.string.crash_true),
                falseWord = context.getString(R.string.crash_false),
            )
        }
    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun time(timestamp: Long): String = runCatching {
        timeFormat.format(Date(timestamp))
    }.getOrDefault(timestamp.toString())

    fun deviceLine(info: CrashInfo): String {
        val man = info.manufacturer ?: strings.unknown
        val model = info.model ?: strings.unknown
        return String.format(strings.deviceFormat, man, model).trim()
    }

    fun androidLine(info: CrashInfo): String {
        val version = info.androidVersion ?: strings.unknown
        return String.format(strings.androidFormat, version, info.sdkInt)
    }

    fun versionLine(info: CrashInfo): String {
        val name = info.appVersionName ?: strings.unknown
        val code = info.appVersionCode ?: 0L
        return String.format(strings.versionFormat, name, code)
    }

    fun threadLine(info: CrashInfo): String {
        val name = info.threadName ?: strings.unknown
        val id = info.threadId
        return if (id != null) String.format(strings.threadWithIdFormat, name, id) else name
    }

    /**
     * Complete, clipboard/share ready report. Copies every field, not just the
     * visible part of the stack trace.
     */
    fun fullReport(rawInfo: CrashInfo): String {
        // Defensive redaction at the output boundary: even a stored log that
        // predates the redactor (or an imported native marker) still gets
        // scrubbed before it is copied or shared.
        val info = rawInfo.let {
            it.copy(
                message = CrashRedactor.redact(it.message),
                stackTrace = CrashRedactor.redact(it.stackTrace) ?: it.stackTrace,
                causeChain = CrashRedactor.redact(it.causeChain),
            )
        }
        val bar = "----------------------------------------"
        val sb = StringBuilder(4096)
        sb.append("========================================\n")
        sb.append(strings.reportHeader).append('\n')
        sb.append("========================================\n")
        appendField(sb, strings.labelTime, time(info.timestamp))
        appendField(sb, strings.labelAppVersion, versionLine(info))
        appendField(sb, strings.labelAndroid, androidLine(info))
        appendField(sb, strings.labelDevice, deviceLine(info))
        appendField(sb, strings.labelThread, threadLine(info))
        appendField(sb, strings.labelException, info.exceptionType)
        info.message?.let { appendField(sb, strings.labelMessage, it) }
        info.appProcess?.let { appendField(sb, strings.labelProcess, it) }
        info.memoryInfo?.let { appendField(sb, strings.labelMemory, it) }
        info.backgrounded?.let {
            appendField(sb, strings.labelForeground, if (it) strings.no else strings.yes)
        }
        appendField(sb, strings.labelAbis, info.supportedAbis.joinToString(", "))
        appendField(sb, strings.labelDebugBuild, if (info.isDebugBuild) strings.trueWord else strings.falseWord)

        sb.append("\n").append(bar).append('\n')
        sb.append(strings.reportStackTraceHeading).append('\n')
        sb.append(bar).append('\n')
        sb.append('\n').append(info.stackTrace).append('\n')

        if (info.causeChain?.isNotBlank() == true) {
            sb.append("\n").append(bar).append('\n')
            sb.append(strings.reportCauseHeading).append('\n')
            sb.append(bar).append('\n')
            sb.append(info.causeChain).append('\n')
        }
        return sb.toString()
    }

    private fun appendField(sb: StringBuilder, label: String, value: String) {
        sb.append(String.format(strings.reportField, label, value)).append('\n')
    }
}
