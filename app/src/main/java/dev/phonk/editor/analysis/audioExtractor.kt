package dev.phonk.editor.analysis

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decodes audio to a mono FloatArray of normalized samples [-1, 1] using the
 * platform MediaExtractor + MediaCodec pipeline (no FFmpeg dependency for
 * analysis). The audio is streamed in bounded chunks so we never hold the
 * whole media in RAM at once.
 *
 * Long files can be truncated to [maxSeconds] to protect low-RAM devices; the
 * beat grid at the core of the timeline remains usable.
 */
object AudioExtractor {

    /** Target analysis sample rate. 11025 Hz is plenty for beat/drop DSP. */
    const val TARGET_RATE = 11025
    const val ERR_AUDIO_DECODE_FAILED = "AudioDecodeFailed"

    /** Fast metadata-only duration probe, falls back to decoding. */
    fun queryDuration(resolver: ContentResolver, uri: Uri): Long {
        return resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                    .takeIf { it >= 0 }
                    ?: c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                if (idx != null && idx >= 0) c.getLong(idx) else 0L
            } else 0L
        } ?: 0L
    }

    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long,
    )

    fun decode(
        resolver: ContentResolver,
        uri: Uri,
        maxSeconds: Int = 15 * 60,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): DecodedAudio {
        val extractor = MediaExtractor()
        val fd = resolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open audio source")
        try {
            extractor.setDataSource(fd.fileDescriptor)
        } catch (t: Throwable) {
            throw IllegalStateException("Cannot open audio source", t)
        } finally {
            fd.close()
        }
        val trackIndex = findAudioTrack(extractor)
        if (trackIndex < 0) throw IllegalStateException(ERR_AUDIO_DECODE_FAILED)
        extractor.selectTrack(trackIndex)

        val format = extractor.getTrackFormat(trackIndex)
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (sourceRate <= 0) throw IllegalStateException(ERR_AUDIO_DECODE_FAILED)
        var duration = 0L
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            duration = format.getLong(MediaFormat.KEY_DURATION)
        }

        val mime = format.getString(MediaFormat.KEY_MIME)
        val decoder = MediaCodec.createDecoderByType(mime ?: "audio/mpeg")
        decoder.configure(format, null, null, 0)
        decoder.start()

        val maxSamples = (maxSeconds * TARGET_RATE).toInt()
        val pcm = ArrayList<Float>(minOf(maxSamples, 4 * 1024 * 1024))

        val bufferInfo = MediaCodec.BufferInfo()
        try {
            var sawInputEos = false
            var sawOutputEos = false
            var stallIterations = 0
            while (!sawOutputEos && pcm.size < maxSamples) {
                if (cancelled()) throw CancellationException("Analysis cancelled")
                if (!sawInputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        if (buffer != null) {
                            val chunk = extractor.readSampleData(buffer, 0)
                            if (chunk < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                decoder.queueInputBuffer(
                                    inIndex, 0, chunk, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outIndex)
                        if (outBuf != null) {
                            addMonoFrames(outBuf, bufferInfo.size, pcm)
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                        val done = minOf(1f, pcm.size.toFloat() / maxSamples)
                        progress(done)
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        stallIterations++
                        if (stallIterations > 300_000) break
                    }
                }
            }
        } finally {
            decoder.stop()
            decoder.release()
            extractor.release()
        }

        val raw = FloatArray(pcm.size)
        for (i in raw.indices) raw[i] = pcm[i]
        val resampled = resampleMean(raw, sourceRate, TARGET_RATE)
        val durationFromSamples = if (sourceRate > 0) {
            raw.size.toLong() * 1000L / sourceRate
        } else 0L
        val detectedDuration = if (duration > 0) duration / 1000L else durationFromSamples
        return DecodedAudio(resampled, TARGET_RATE, detectedDuration)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun addMonoFrames(out: ByteBuffer, size: Int, target: ArrayList<Float>) {
        out.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = out.duplicate().asShortBuffer()
        val n = (size / 2).coerceAtMost(shorts.remaining())
        for (i in 0 until n) {
            target.add(shorts.get(i) / 32768f)
        }
    }

    /** Mean downsampling to a target rate. Preserves the onset envelope. */
    private fun resampleMean(src: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate <= dstRate) return src
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = (src.size / ratio).toInt()
        if (outLen <= 0) return src
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val start = (i * ratio).toInt()
            val end = ((i + 1) * ratio).toInt().coerceAtMost(src.size)
            if (end <= start) continue
            var sum = 0f
            for (j in start until end) sum += src[j]
            out[i] = sum / (end - start)
        }
        return out
    }
}