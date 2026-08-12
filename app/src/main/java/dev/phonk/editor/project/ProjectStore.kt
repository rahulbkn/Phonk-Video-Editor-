package dev.phonk.editor.project

import android.content.Context
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.ProjectCodec
import org.json.JSONObject
import java.io.File

/**
 * Persists project JSON under filesDir/projects and remembers recent projects
 * in SharedPreferences. Media URIs are stored as strings; media itself is
 * never copied into the JSON.
 */
class ProjectStore(private val context: Context) {

    private val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("recent_projects", Context.MODE_PRIVATE)

    fun save(project: PhonkProject): File {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val file = File(projectsDir, "${updated.id}.json")
        file.writeText(ProjectCodec().toJson(updated))
        rememberRecent(updated.id)
        return file
    }

    fun load(id: String): PhonkProject? {
        val file = File(projectsDir, "$id.json")
        if (!file.exists()) return null
        return runCatching { ProjectCodec().fromJson(file.readText()) }.getOrNull()
    }

    fun listRecent(): List<PhonkProject> {
        val ids = prefs.getStringSet("ids", emptySet()) ?: emptySet()
        return ids
            .sortedByDescending { prefs.getLong("ts_$it", 0L) }
            .mapNotNull { load(it) }
            .take(MAX_RECENT)
    }

    fun delete(id: String) {
        File(projectsDir, "$id.json").delete()
        val ids = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(id)
        prefs.edit().putStringSet("ids", ids).remove("ts_$id").apply()
    }

    fun rememberRecent(id: String) {
        val ids = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(id)
        val now = System.currentTimeMillis()
        if (ids.size > MAX_RECENT) {
            val toRemove = ids.sortedBy { prefs.getLong("ts_$it", 0L) }.take(ids.size - MAX_RECENT)
            ids.removeAll(toRemove.toSet())
            prefs.edit()
                .putStringSet("ids", ids)
                .putLong("ts_$id", now)
                .also { ed -> toRemove.forEach { ed.remove("ts_$it") } }
                .apply()
        } else {
            prefs.edit().putStringSet("ids", ids).putLong("ts_$id", now).apply()
        }
    }

    companion object {
        private const val MAX_RECENT = 50
        fun isValidJson(text: String): Boolean {
            return runCatching { JSONObject(text) }.isSuccess
        }
    }
}