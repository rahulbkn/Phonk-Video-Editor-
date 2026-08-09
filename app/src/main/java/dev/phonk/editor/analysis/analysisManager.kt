package dev.phonk.editor.analysis

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.native.PhonkNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val phase: Phase, val progress: Float) : AnalysisState
    data class Done(val result: AnalysisResult) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

enum class Phase { DECODING, ANALYZING }

/**
 * Runs the analysis pipeline off the main thread: decode -> downsample ->
 * native DSP. Emits [AnalysisState] so the UI can render progress and result.
 */
private const val TAG = "AnalysisManager"

class AnalysisManager(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    var cancelled: Boolean = false

    fun analyze(uri: Uri, maxSeconds: Int = 15 * 60) {
        cancelled = false
        Log.i(TAG, "analyze start uri=" + uri)
        _state.value = AnalysisState.Idle
        scope.launch {
            _state.value = AnalysisState.Running(Phase.DECODING, 0f)
            try {
                val decoded = withContext(Dispatchers.Default) {
                    AudioExtractor.decode(
                        resolver = resolver,
                        uri = uri,
                        maxSeconds = maxSeconds,
                        progress = { p ->
                            _state.value = AnalysisState.Running(Phase.DECODING, p)
                        },
                        cancelled = { cancelled },
                    )
                }
                _state.value = AnalysisState.Running(Phase.ANALYZING, 0f)
                val json = withContext(Dispatchers.Default) {
                    PhonkNative.nativeAnalyzeAudio(decoded.samples, decoded.sampleRate)
                }
                val result = AnalysisJson.parseResult(json)
                val withDuration = result.copy(durationMs = decoded.durationMs)
                Log.i(TAG, "analyze done beats=" + withDuration.beats.size + " drops=" + withDuration.drops.size + " durationMs=" + withDuration.durationMs)
                _state.value = AnalysisState.Done(withDuration)
            } catch (e: CancellationException) {
                _state.value = AnalysisState.Failed("Analysis cancelled")
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "analyze FAILED", t)
                _state.value = AnalysisState.Failed(
                    t.message ?: t.javaClass.simpleName
                )
            }
        }
    }

    fun cancel() {
        cancelled = true
    }
}