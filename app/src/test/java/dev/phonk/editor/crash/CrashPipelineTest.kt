package dev.phonk.editor.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crash logic tests for tests for the crash pipeline: JSON round-trip,
 * redaction fidelity and report formatting. Runs on the local JVM:
 * ./gradlew :app:testDebugUnitTest
 */
class CrashPipelineTest {

    /**
     * English bundle used only by these JVM tests. Production code resolves
     * these from Android resources (CrashFormatter.Strings.from(context));
     * this mirrors the resource values so formatting can be tested on the JVM.
     */
    private fun testStrings() = CrashFormatter.Strings(
        unknown = "Unknown",
        deviceFormat = "%1\$s %2\$s",
        androidFormat = "%1\$s / SDK %2\$d",
        versionFormat = "%1\$s (%2\$s)",
        threadWithIdFormat = "%1\$s (id %2\$d)",
        reportHeader = "APP CRASH REPORT",
        reportField = "%1\$s: %2\$s",
        reportStackTraceHeading = "STACK TRACE",
        reportCauseHeading = "CAUSE",
        labelTime = "Time",
        labelAppVersion = "App Version",
        labelAndroid = "Android",
        labelDevice = "Device",
        labelThread = "Thread",
        labelException = "Exception",
        labelMessage = "Message",
        labelProcess = "Process",
        labelMemory = "Memory",
        labelForeground = "Foreground",
        labelAbis = "ABIs",
        labelDebugBuild = "Debug build",
        yes = "yes",
        no = "no",
        trueWord = "true",
        falseWord = "false",
    )

    private fun formatter() = CrashFormatter(testStrings())

    private fun sampleInfo() = CrashInfo(
        timestamp = 1786212345678L,
        exceptionType = "java.lang.NullPointerException",
        message = "Attempt to invoke member 'on a String' on a null object reference",
        stackTrace = "java.lang.NullPointerException\n    at dev.phonk.editor.MainScreen",
        causeChain = "java.lang.NullPointerException\n    at dev.phonk.editor.engine.Renderer",
        threadName = "main",
        threadId = 1L,
        appVersionName = "1.0.0",
        appVersionCode = 1,
        androidVersion = "13",
        sdkInt = 33,
        manufacturer = "Infinix",
        model = "X6850",
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        appProcess = "dev.phonk.editor (pid 12345)",
        memoryInfo = "available=512MB total=4096MB",
        backgrounded = false,
        isDebugBuild = true,
    )

    @Test
    fun codecRoundTripsEveryField() {
        val original = sampleInfo()
        val json = CrashInfoCodec().toJson(original)
        val parsed = CrashInfoCodec().fromJson(json)

        assertNotNull(parsed)
        parsed?.let {
            assertEquals(original.timestamp, it.timestamp)
            assertEquals(original.exceptionType, it.exceptionType)
            assertEquals(original.message, it.message)
            assertEquals(original.stackTrace, it.stackTrace)
            assertEquals(original.causeChain, it.causeChain)
            assertEquals(original.threadName, it.threadName)
            assertEquals(original.threadId, it.threadId)
            assertEquals(original.appVersionName, it.appVersionName)
            assertEquals(original.appVersionCode, it.appVersionCode)
            assertEquals(original.androidVersion, it.androidVersion)
            assertEquals(original.sdkInt, it.sdkInt)
            assertEquals(original.manufacturer, it.manufacturer)
            assertEquals(original.model, it.model)
            assertEquals(original.supportedAbis, it.supportedAbis)
            assertEquals(original.appProcess, it.appProcess)
            assertEquals(original.memoryInfo, it.memoryInfo)
            assertEquals(original.backgrounded, it.backgrounded)
            assertEquals(original.isDebugBuild, it.isDebugBuild)
        }
    }

    @Test
    fun codecHandlesNullFields() {
        val info = sampleInfo().copy(
            message = null,
            causeChain = null,
            threadName = null,
            threadId = null,
            appVersionName = null,
            appVersionCode = null,
            androidVersion = null,
            manufacturer = null,
            model = null,
            appProcess = null,
            memoryInfo = null,
            backgrounded = null,
            supportedAbis = emptyList(),
        )
        val parsed = CrashInfoCodec().fromJson(CrashInfoCodec().toJson(info))
        assertEquals(info, parsed)
    }

    @Test
    fun codecHandlesGarbage() {
        assertEquals(null, CrashInfoCodec().fromJson("{not json"))
        assertEquals(null, CrashInfoCodec().fromJson(""))
    }

    @Test
    fun redactionRemovesSecretsButKeepsNormalCode() {
        val info = sampleInfo().copy(
            message = "Authorization: Bearer eyJ0eXAi.abc.def",
            stackTrace =
                "token=sekrit123\n" +
                    "access_token=AAA111 secret\n" +
                    "password=pw-1234\n" +
                    "at dev.phonknative.ffmpeg.wrapper(FfmpegRenderer.kt:42)",
        )
        val report = formatter().fullReport(info)
        assertTrue(!report.contains("sekrit123"))
        assertTrue(!report.contains("AAA111"))
        assertTrue(!report.contains("pw-1234"))
        assertTrue(report.contains("[REDACTED]"))
        assertTrue(report.contains("FfmpegRenderer.kt:42"))
    }

    @Test
    fun fullReportContainsAllSections() {
        val report = formatter().fullReport(sampleInfo())
        assertTrue(report.contains("APP CRASH REPORT"))
        assertTrue(report.contains("STACK TRACE"))
        assertTrue(report.contains("CAUSE"))
        assertTrue(report.contains("java.lang.NullPointerException"))
        assertTrue(report.contains("Infinix"))
        assertTrue(report.contains("Thread: main"))
    }

    @Test
    fun codingAllFieldsRendering() {
        // thread name + id appear together for background-thread crashes.
        val report = formatter().fullReport(sampleInfo().copy(threadId = 42L))
        assertTrue(report.contains("Thread: main (id 42)"))
    }
}