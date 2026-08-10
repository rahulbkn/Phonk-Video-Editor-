package dev.phonk.editor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the timeline split-point logic.
 *
 * Splitting a clip trimmed down to ~1ms must not crash: the coerce range
 * [destStartMs+1, destEndMs-1] is empty for such clips, which previously threw
 * `IllegalArgumentException: Cannot coerce value to an empty range` on tap.
 */
class SplitPointTest {

    @Test
    fun shortClipIsNotSplittable() {
        val split = splitPointFor(destStartMs = 19540L, destEndMs = 19541L, ms = 19540L)
        assertNull("a 1ms clip must not produce a split point", split)
    }

    @Test
    fun zeroWidthClipIsNotSplittable() {
        val split = splitPointFor(destStartMs = 100L, destEndMs = 100L, ms = 100L)
        assertNull("a zero-width clip must not produce a split point", split)
    }

    @Test
    fun twoMsClipProducesSplittableRange() {
        val split = splitPointFor(destStartMs = 100L, destEndMs = 102L, ms = 101L)
        assertEquals("a 2ms clip splits at the interior position", 101L, split)
    }

    @Test
    fun normalClipClampsIntoSplittableRange() {
        val split = splitPointFor(destStartMs = 0L, destEndMs = 1000L, ms = 750L)
        assertEquals(750L, split)
    }

    @Test
    fun msOutsideClipStillClampedIntoRange() {
        val split = splitPointFor(destStartMs = 0L, destEndMs = 1000L, ms = 2000L)
        assertEquals("over-long request clamps to max in-range point", 999L, split)
    }
}