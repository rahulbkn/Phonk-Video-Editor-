package dev.phonk.editor.project

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for ProjectStore persistence and recent-project list behavior.
 */
class ProjectStoreTest {

    private lateinit var context: Context
    private lateinit var store: ProjectStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dir = File(context.filesDir, "projects")
        dir.deleteRecursively()
        context.getSharedPreferences("recent_projects", Context.MODE_PRIVATE).edit().clear().commit()
        store = ProjectStore(context)
    }

    @Test
    fun saveAndLoadRoundTrips() {
        val project = PhonkProject(name = "Test Project", videoDurationMs = 5000L)
        val file = store.save(project)
        assertTrue("project file must exist", file.exists())
        val loaded = store.load(project.id)
        assertEquals("Test Project", loaded?.name)
        assertEquals(5000L, loaded?.videoDurationMs)
    }

    @Test
    fun deleteRemovesProjectAndRecent() {
        val project = PhonkProject(name = "Delete Me")
        store.save(project)
        store.delete(project.id)
        assertEquals("project file must be removed", null, store.load(project.id))
        val recent = store.listRecent()
        assertFalse("deleted project must not appear in recent", recent.any { it.id == project.id })
    }

    @Test
    fun rememberRecentCapsAtMax() {
        val ids = mutableListOf<String>()
        repeat(60) { i ->
            val p = PhonkProject(name = "Project $i")
            store.save(p)
            ids.add(p.id)
        }
        val recent = store.listRecent()
        assertEquals("recent list must be capped", ProjectStore.MAX_RECENT, recent.size)
        val recentIds = recent.map { it.id }.toSet()
        ids.take(10).forEach { oldest ->
            assertFalse("oldest entries must be evicted", recentIds.contains(oldest))
        }
    }

    @Test
    fun listRecentReturnsMostRecentFirst() {
        val p1 = PhonkProject(name = "Old")
        val p2 = PhonkProject(name = "New")
        store.save(p1)
        store.save(p2)
        val recent = store.listRecent()
        assertEquals("most recent must be first", "New", recent.first().name)
        assertEquals("older must be second", "Old", recent.last().name)
    }
}
