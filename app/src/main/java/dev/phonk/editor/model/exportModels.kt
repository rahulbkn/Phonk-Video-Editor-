package dev.phonk.editor.model

/** Resolution presets for export. 2160x3840 is 4K portrait. */
enum class Resolution(val width: Int, val height: Int, val label: String) {
    HD_720(720, 1280, "720p"),
    HD_1080(1080, 1920, "1080p"),
    HD_1440(1440, 2560, "1440p"),
    UHD_4K(2160, 3840, "4K");
}

/** Output frame rates. */
enum class FrameRate(val fps: Int, val label: String) {
    F24(24, "24 FPS"),
    F30(30, "30 FPS"),
    F60(60, "60 FPS");
}

/** H.264 or H.265/HEVC (when the device encodes it). */
enum class VideoCodec(val ffName: String, val label: String) {
    H264("h264", "H.264"),
    HEVC("hevc", "H.265/HEVC");
}

/** Audio bitrate options (kbps). */
enum class AudioBitrate(val kbps: Int, val label: String) {
    A128(128, "128 kbps"),
    A192(192, "192 kbps"),
    A256(256, "256 kbps"),
    A320(320, "320 kbps");
}

data class ExportConfig(
    val resolution: Resolution = Resolution.HD_1080,
    val fps: FrameRate = FrameRate.F30,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioBitrate: AudioBitrate = AudioBitrate.A192,
    val maintainAspect: Boolean = true,
    val hardwareAccel: Boolean = true,
    val videoBitrateMbps: Int = 12,
)