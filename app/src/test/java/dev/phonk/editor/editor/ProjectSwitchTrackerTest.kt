package dev.phonk.editor.editor

import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.ui.ProjectSwitchTracker
import dev.phonk.editor.ui.projectIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the per-project reset seam used by EditorViewModel.setProject. */
class ProjectSwitchTrackerTest {

    @Test
    fun firstProjectIsARealSwitch() {
        val tracker = ProjectSwitchTracker()
        assertTrue(tracker.onProjectSet("projA"))
        assertEquals("projA", tracker.currentIdentity)
    }

    @Test
    fun sameProjectReEmissionIsNotASwitch() {
        val tracker = ProjectSwitchTracker()
        tracker.onProjectSet("projA")
        assertFalse("re-emission must not wipe state", tracker.onProjectSet("projA"))
    }

    @Test
    fun differentProjectIsASwitch() {
        val tracker = ProjectSwitchTracker()
        tracker.onProjectSet("projA")
        assertTrue(tracker.onProjectSet("projB"))
        assertEquals("projB", tracker.currentIdentity)
    }

    @Test
    fun identityFallsBackToUriWhenIdBlank() {
        val a = PhonkProject(id = "", videoUri = "content://media/a")
        val b = PhonkProject(id = "", videoUri = "content://media/b")
        assertEquals("content://media/a", projectIdentity(a))
        assertEquals("content://media/b", projectIdentity(b))
        val tracker = ProjectSwitchTracker()
        tracker.onProjectSet(projectIdentity(a))
        assertTrue("distinct URIs must count as a switch", tracker.onProjectSet(projectIdentity(b)))
    }
}
