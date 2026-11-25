package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Supported target container formats for video conversion.
 */
enum class TargetVideoFormat(val label: String, val extension: String) {
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    AVI("AVI", "avi"),
    MOV("MOV", "mov"),
    WEBM("WEBM", "webm"),
}

/**
 * Supported target formats for audio extraction or conversion.
 */
enum class TargetAudioFormat(val label: String, val extension: String) {
    MP3("MP3 (320 kbps)", "mp3"),
    WAV("WAV", "wav"),
    FLAC("FLAC", "flac"),
    M4A("M4A (AAC)", "m4a"),
    OGG("OGG (Vorbis)", "ogg"),
    OPUS("OPUS", "opus"),
}

/**
 * Target video height presets up to 4K UHD.
 */
enum class ResolutionPreset(val key: String, val label: String, val targetHeight: Int?) {
    ORIGINAL("original", "Manter Resolução Original", null),
    P2160("2160p", "4K UHD (2160p)", 2160),
    P1440("1440p", "2K QHD (1440p)", 1440),
    P1080("1080p", "1080p (Full HD)", 1080),
    P720("720p", "720p (HD)", 720),
    P480("480p", "480p (SD)", 480),
}

/**
 * Video codecs selectable when converting to MP4.
 */
enum class Mp4Codec(val key: String, val label: String) {
    H264("h264", "H.264 / AVC (Alta Compatibilidade)"),
    HEVC("hevc", "H.265 / HEVC (Menor Tamanho)"),
    AV1("av1", "AV1 (Nova Geração)"),
    VP9("vp9", "VP9 (Aberto / Web)"),
}

/**
 * Unified conversion specification passed to the converter engine.
 */
data class ConversionSpec(
    val targetExtension: String,
    val isAudioTarget: Boolean,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val mp4Codec: Mp4Codec = Mp4Codec.H264,
)
