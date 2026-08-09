package dev.phonk.editor.native

/**
 * JNI surface backed by native code (see app/src/main/cpp/phonk_jni.cpp).
 *
 * All methods return compact JSON strings. The C++ side owns the DSP work;
 * Kotlin only passes decoded mono PCM and consumes structured output.
 */
object PhonkNative {

    init {
        System.loadLibrary("phonknative")
    }

    /**
     * Full analysis: bpm, beats, drops, sections and energy/flux curves.
     * @param pcm mono samples normalized to [-1, 1]
     */
    external fun nativeAnalyzeAudio(pcm: FloatArray, sampleRate: Int): String

    /** Beat-only analysis. Returns {bpm:x,beats:[...]}. */
    external fun nativeDetectBeats(pcm: FloatArray, sampleRate: Int): String

    /** Drop-only analysis. Returns {bpm:x,drops:[...]}. */
    external fun nativeDetectDrops(pcm: FloatArray, sampleRate: Int): String

    /** Coarse feature frames (rms/flux/bass/snare) for waveform UIs. */
    external fun nativeExtractFrames(
        pcm: FloatArray,
        sampleRate: Int,
        windowSize: Int,
        hopSize: Int,
    ): String

    /**
     * Deterministic beat-synchronized timeline plan.
     * @param analysisJson output produced by nativeAnalyzeAudio
     */
    external fun nativeProcessTimeline(
        analysisJson: String,
        cutSubdivision: Double,
        windowHalfBeats: Int,
        emphasizeDrops: Boolean,
        effectsEnabled: Boolean,
    ): String

    external fun nativeVersion(): String
}