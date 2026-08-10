package dev.phonk.editor.export

import android.content.ContextWrapper
import dev.phonk.editor.ffmpeg.RenderCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: a cancel issued mid-export must reach the renderer that is
 * actually running (so the live ffmpeg process is stopped), not just flip a
 * flag that is only consulted after the render completes.
 */
class ExportCancelTest {

    private class FakeRenderer : RenderCancellable {
        var cancelled = false
        override fun cancel() {
            cancelled = true
        }
    }

    private fun newRunner() =
        ExportRunner(ContextWrapper(null), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun cancelPropagatesToActiveRenderer() {
        val runner = newRunner()
        val renderer = FakeRenderer()
        runner.activeRenderer = renderer
        runner.cancel()
        assertTrue("cancel() must reach the active renderer", renderer.cancelled)
        assertTrue("cancel() must also set the queued/preparing flag", runner.cancelRequested)
    }

    @Test
    fun cancelBeforeRendererIsAttachedStillRequestsCancellation() {
        val runner = newRunner()
        runner.cancel()
        assertTrue("no renderer attached yet, flag alone must prevent render", runner.cancelRequested)
    }
}
