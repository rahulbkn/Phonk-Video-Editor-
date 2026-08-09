package dev.phonk.editor.crash

import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable snapshot of a single crash. Contains only safe, diagnostic
 * information: no user content, no credentials, no app-private storage dumps.
 *
 * Serialized to JSON with [CrashInfoCodec] and stored under the app's private
 * files directory (filesDir/crash_logs).
 */
data class CrashInfo(
    val timestamp: Long,
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
    val causeChain: String?,
    val threadName: String?,
    val threadId: Long?,
    val appVersionName: String?,
    val appVersionCode: Long?,
    val androidVersion: String?,
    val sdkInt: Int,
    val manufacturer: String?,
    val model: String?,
    val supportedAbis: List<String>,
    val appProcess: String?,
    val memoryInfo: String?,
    val backgrounded: Boolean?,
    val isDebugBuild: Boolean,
)

/** JSON <-> [CrashInfo] using org.json, mirroring the style of ProjectCodec. */
class CrashInfoCodec {

    fun toJson(info: CrashInfo): String {
        val o = JSONObject()
        o.put("timestamp", info.timestamp)
        o.put("exceptionType", info.exceptionType)
        o.put("message", info.message)
        o.put("stackTrace", info.stackTrace)
        o.put("causeChain", info.causeChain)
        o.put("threadName", info.threadName)
        o.put("threadId", info.threadId)
        o.put("appVersionName", info.appVersionName)
        o.put("appVersionCode", info.appVersionCode)
        o.put("androidVersion", info.androidVersion)
        o.put("sdkInt", info.sdkInt)
        o.put("manufacturer", info.manufacturer)
        o.put("model", info.model)
        o.put(
            "supportedAbis",
            JSONArray().also { arr -> info.supportedAbis.forEach(arr::put) },
        )
        o.put("appProcess", info.appProcess)
        o.put("memoryInfo", info.memoryInfo)
        o.put("backgrounded", info.backgrounded)
        o.put("isDebugBuild", info.isDebugBuild)
        return o.toString(2)
    }

    fun fromJson(text: String): CrashInfo? {
        return runCatching {
            val o = JSONObject(text)
            val abis = mutableListOf<String>()
            o.optJSONArray("supportedAbis")?.let { arr ->
                for (i in 0 until arr.length()) abis.add(arr.optString(i, ""))
            }
            CrashInfo(
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                exceptionType = o.optString("exceptionType", "UnknownException"),
                message = if (o.has("message") && !o.isNull("message")) o.optString("message") else null,
                stackTrace = o.optString("stackTrace", "(no stack trace)"),
                causeChain = if (o.has("causeChain") && !o.isNull("causeChain")) o.optString("causeChain") else null,
                threadName = if (o.has("threadName") && !o.isNull("threadName")) o.optString("threadName") else null,
                threadId = if (o.has("threadId") && !o.isNull("threadId")) o.optLong("threadId") else null,
                appVersionName = if (o.has("appVersionName") && !o.isNull("appVersionName")) o.optString("appVersionName") else null,
                appVersionCode = if (o.has("appVersionCode") && !o.isNull("appVersionCode")) o.optLong("appVersionCode") else null,
                androidVersion = if (o.has("androidVersion") && !o.isNull("androidVersion")) o.optString("androidVersion") else null,
                sdkInt = o.optInt("sdkInt", 0),
                manufacturer = if (o.has("manufacturer") && !o.isNull("manufacturer")) o.optString("manufacturer") else null,
                model = if (o.has("model") && !o.isNull("model")) o.optString("model") else null,
                supportedAbis = abis,
                appProcess = if (o.has("appProcess") && !o.isNull("appProcess")) o.optString("appProcess") else null,
                memoryInfo = if (o.has("memoryInfo") && !o.isNull("memoryInfo")) o.optString("memoryInfo") else null,
                backgrounded = if (o.has("backgrounded") && !o.isNull("backgrounded")) o.optBoolean("backgrounded") else null,
                isDebugBuild = o.optBoolean("isDebugBuild", false),
            )
        }.getOrNull()
    }
}