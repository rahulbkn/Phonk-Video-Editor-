package dev.phonk.editor.crash

/**
 * Lightweight, conservative redaction of obvious secrets in crash text
 * (messages, stack traces, cause chains). Matching is deliberately narrow so
 * normal stack traces pass through unmodified.
 */
object CrashRedactor {

    private val assignmentRedaction = Regex(
        "(?i)((password|passwd|token|access_token|refresh_token|apikey|api_key|api-key|" +
            "secret|client_secret|authorization))[\\s]*[:=][\\s]*['\"]?[^'\"\\s,&|}]+",
    )

    private val bearerRedaction = Regex(
        "(?i)(bearer[\\s]+)[A-Za-z0-9._~+/\\-]+=*",
    )

    fun redact(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        var out = assignmentRedaction.replace(text) { m ->
            m.groupValues[1] + "=[REDACTED]"
        }
        out = bearerRedaction.replace(out) { m ->
            m.groupValues[1] + "[REDACTED]"
        }
        return out
    }
}