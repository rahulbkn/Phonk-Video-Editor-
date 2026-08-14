package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the needle/playhead disappearing after split + move.
 *
 * The needle is GLOBAL TIMELINE TIME. Its X is always needleX =
 * timeToX(globalTime) and the global time must stay a valid destination
 * timestamp inside [0, timelineDuration] even when the playhead sits in a
 * source gap or a destination gap (which splits, trims, deletes and moves
 * create). Previously sourceToDest fell back to `clips.lastOrNull()`, which
 * teleported the needle to the last clip's destination region (usually
 * off-viewport) for any source position in a gap.
 */
class TimelineTimeTest {

    private val mediaEnd = 100_000L

    private fun clip(id: String, srcStart: Long, srcEnd: Long, destStart: Long, destEnd: Long) =
        ClipSegment(
            id = id,
            sourceStartMs = srcStart,
            sourceEndMs = srcEnd,
            destStartMs = destStart,
            destEndMs = destEnd,
        )

    /** Main video split into A and B, B moved to a later timeline position.
     *  Dest gap [40000, 60000] and (after the trim below) source gaps. */
    private fun splitAndMoveProject(): List<ClipSegment> {
        val a = clip("A", 0, 40_000, 0, 40_000)
        val b = clip("B", 40_000, 100_000, 60_000, 120_000)
        return listOf(a, b)
    }

    @Test
    fun playheadBeforeAStaysAtZero() {
        val clips = splitAndMoveProject()
        assertEquals(0L, TimelineTime.sourceToDest(clips, 0L, mediaEnd, 120_000L))
        assertEquals(0L, TimelineTime.destToSource(clips, 0L, mediaEnd, 120_000L))
    }

    @Test
    fun playheadInsideAMapsIntoA() {
        val clips = splitAndMoveProject()
        assertEquals(10_000L, TimelineTime.sourceToDest(clips, 10_000L, mediaEnd, 120_000L))
        assertEquals(10_000L, TimelineTime.destToSource(clips, 10_000L, mediaEnd, 120_000L))
    }

    @Test
    fun playheadInDestGapSeekPointsAtNextClip() {
        val clips = splitAndMoveProject()
        // Seeking into the dest gap [40000,60000] must land on B's source
        // start, and the settled needle must be B's dest start (valid/visible),
        // NOT a jump to some arbitrary clip.
        val src = TimelineTime.destToSource(clips, 50_000L, mediaEnd, 120_000L)
        assertEquals(40_000L, src)
        val settled = TimelineTime.sourceToDest(clips, src, mediaEnd, 120_000L)
        assertEquals(60_000L, settled)
    }

    @Test
    fun playheadInsideBMapsIntoB() {
        val clips = splitAndMoveProject()
        // source 70000 is B's content -> dest 60000 + (70000-40000) = 90000.
        assertEquals(90_000L, TimelineTime.sourceToDest(clips, 70_000L, mediaEnd, 120_000L))
        // dest 90000 is B's content -> source 40000 + (90000-60000) = 70000.
        assertEquals(70_000L, TimelineTime.destToSource(clips, 90_000L, mediaEnd, 120_000L))
    }

    @Test
    fun playheadAfterBClampsToEnd() {
        val clips = splitAndMoveProject()
        assertEquals(120_000L, TimelineTime.sourceToDest(clips, 150_000L, mediaEnd, 120_000L))
        assertEquals(100_000L, TimelineTime.destToSource(clips, 130_000L, mediaEnd, 120_000L))
    }

    @Test
    fun sourceGapPointsAtNextClipInsteadOfLastClip() {
        // A trimmed and B moved left: source gap [20000, 50000]. The source
        // position 30000 must resolve to B (dest 20000), NOT to the last clip
        // in the list (which the old clips.lastOrNull() fallback returned).
        val a = clip("A", 0, 20_000, 0, 20_000)
        val b = clip("B", 50_000, 90_000, 20_000, 60_000)
        val c = clip("C", 90_000, 100_000, 60_000, 70_000)
        val clips = listOf(a, b, c)
        assertEquals(20_000L, TimelineTime.sourceToDest(clips, 30_000L, mediaEnd, 70_000L))
        assertEquals(20_000L, TimelineTime.sourceToDest(clips, 49_000L, mediaEnd, 70_000L))
        // And the last clip still resolves to its own dest.
        assertEquals(65_000L, TimelineTime.sourceToDest(clips, 95_000L, mediaEnd, 70_000L))
    }

    @Test
    fun needleNeverDisappearsForAnyGlobalTime() {
        val clips = splitAndMoveProject()
        val timelineMs = 120_000L
        // Every source position (playback) yields a valid global dest time.
        var src = 0L
        while (src <= mediaEnd) {
            val dest = TimelineTime.sourceToDest(clips, src, mediaEnd, timelineMs)
            assertTrue("src=$src -> dest=$dest out of range", dest in 0L..timelineMs)
            src += 250L
        }
        // Every dest position (seek/scrub, including gaps) yields a valid
        // source time, and the settled needle is a valid global time too.
        var destMs = 0L
        while (destMs <= timelineMs) {
            val srcTime = TimelineTime.destToSource(clips, destMs, mediaEnd, timelineMs)
            assertTrue("dest=$destMs -> src=$srcTime out of range", srcTime in 0L..mediaEnd)
            val settled = TimelineTime.sourceToDest(clips, srcTime, mediaEnd, timelineMs)
            assertTrue("dest=$destMs settled=$settled out of range", settled in 0L..timelineMs)
            destMs += 250L
        }
    }

    @Test
    fun needleXIsFiniteAndOnScreenWhenPlayheadIsVisible() {
        val clips = splitAndMoveProject()
        val controller = TimelineController { PhonkProject() }.apply {
            totalMs = 120_000L
            setViewportWidth(936f)
        }
        // Scrub across the whole timeline; whenever the global time is inside
        // the visible window the needle X must be finite and on-screen, even
        // for gap positions that previously teleported it off-canvas.
        val viewportStart = controller.viewportStartMs
        val viewportEnd = viewportStart + controller.viewportMs
        var destMs = 0L
        while (destMs <= controller.totalMs) {
            val x = controller.timeToX(destMs)
            assertTrue("x=$x for dest=$destMs", x.isFinite())
            if (destMs in viewportStart..viewportEnd) {
                assertTrue("dest=$destMs x=$x off-screen", x in 0f..936f)
            }
            destMs += 1000L
        }
        // The exact split+move case the user reported: playhead parked in the
        // gap must settle at a valid, visible global position after seeking.
        val gapDest = 50_000L
        val settled = TimelineTime.sourceToDest(
            clips, TimelineTime.destToSource(clips, gapDest, mediaEnd, 120_000L), mediaEnd, 120_000L)
        assertEquals(60_000L, settled)
        // Scroll the viewport to include the settled needle: it must render
        // on-screen (never teleport to an arbitrary clip off the canvas).
        controller.scrollBy(40_000L)
        val x = controller.timeToX(settled)
        assertTrue("settled=$settled x=$x off-screen", x in 0f..936f)
    }

    @Test
    fun pausedPumpKeepsPlayheadParkedInDestGap() {
        val clips = splitAndMoveProject()
        // User scrubbed into the dest gap [40000,60000]; while paused the
        // needle must STAY parked at 50000 instead of snapping to B's dest
        // start (60000), so it renders in the gap and never disappears.
        assertEquals(
            50_000L,
            TimelineTime.nextPlayhead(
                currentPlayheadMs = 50_000L,
                pendingSeekDestMs = null,
                isPlaying = false,
                playerPositionMs = 40_000L, // player landed on B's source start
                clips = clips,
                mediaEndMs = mediaEnd,
                timelineDurationMs = 120_000L,
            ),
        )
    }

    @Test
    fun pendingSeekIsHeldUntilPlayerLands() {
        val clips = splitAndMoveProject()
        // In-flight manual seek: the requested destination wins regardless of
        // the current player position.
        assertEquals(
            50_000L,
            TimelineTime.nextPlayhead(
                currentPlayheadMs = 10_000L,
                pendingSeekDestMs = 50_000L,
                isPlaying = true,
                playerPositionMs = 40_000L,
                clips = clips,
                mediaEndMs = mediaEnd,
                timelineDurationMs = 120_000L,
            ),
        )
    }

    @Test
    fun playingPumpFollowsMediaPosition() {
        val clips = splitAndMoveProject()
        assertEquals(
            90_000L,
            TimelineTime.nextPlayhead(
                currentPlayheadMs = 50_000L,
                pendingSeekDestMs = null,
                isPlaying = true,
                playerPositionMs = 70_000L,
                clips = clips,
                mediaEndMs = mediaEnd,
                timelineDurationMs = 120_000L,
            ),
        )
    }
}
