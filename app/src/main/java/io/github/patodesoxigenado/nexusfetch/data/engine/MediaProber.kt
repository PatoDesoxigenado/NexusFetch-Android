package io.github.patodesoxigenado.nexusfetch.data.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.data.local.storage.UriFileResolver
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FfprobeResultDto(
    val format: FfprobeFormatDto? = null,
    val streams: List<FfprobeStreamDto> = emptyList(),
)

@Serializable
private data class FfprobeFormatDto(
    val duration: String? = null,
)

@Serializable
private data class FfprobeStreamDto(
    @SerialName("codec_name") val codecName: String? = null,
    @SerialName("codec_type") val codecType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

private data class RetrieverProbeResult(
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val durationSec: Double = 0.0,
    val width: Int? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val fps: Double? = null,
)

/**
 * Probes local video/audio files via fast Android MediaMetadataRetriever with ffprobe JSON fallback.
 */
@Singleton
class MediaProber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uriFileResolver: UriFileResolver,
    private val ffmpegExecutor: FfmpegProcessExecutor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fast zero-copy probe directly from a SAF `content://` URI for instant UI preview before any heavy copy.
     */
    suspend fun probeUri(uri: Uri): MediaFileInfo = withContext(Dispatchers.IO) {
        val (displayName, sizeBytes) = uriFileResolver.queryDisplayNameAndSize(uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extFromName = displayName.substringAfterLast('.', "")
        val ext = if (extFromName.isNotBlank()) ".${extFromName.lowercase()}" else ".mp4"
        val retrieved = readWithRetriever(
            defaultIsVideo = mime.startsWith("video/") || ext in VIDEO_EXTS,
            defaultIsAudio = mime.startsWith("audio/") || ext in AUDIO_EXTS,
        ) { setDataSource(context, uri) }
        val enriched = enrichWithMediaExtractor(retrieved) { setDataSource(context, uri, null) }

        MediaFileInfo(
            uriString = uri.toString(),
            name = displayName,
            sizeBytes = sizeBytes,
            isVideo = enriched.isVideo,
            isAudio = enriched.isAudio || !enriched.isVideo,
            ext = ext,
            durationSec = enriched.durationSec,
            width = enriched.width,
            height = enriched.height,
            vcodec = enriched.vcodec,
            acodec = enriched.acodec,
            fps = enriched.fps,
        )
    }

    suspend fun probe(
        file: File,
        uriString: String,
        displayName: String,
    ): MediaFileInfo = withContext(Dispatchers.IO) {
        val ext = ".${displayName.substringAfterLast('.', file.extension).lowercase()}"
        val retrieved = readWithRetriever(
            defaultIsVideo = ext in VIDEO_EXTS,
            defaultIsAudio = ext in AUDIO_EXTS,
        ) { setDataSource(file.absolutePath) }
        val extracted = enrichWithMediaExtractor(retrieved) { setDataSource(file.absolutePath) }

        val needsFallback = extracted.durationSec <= 0.0 ||
            (extracted.isVideo && (extracted.width == null || extracted.vcodec == null))
        val finalResult = if (needsFallback) enrichWithFfprobe(file, extracted) else extracted

        MediaFileInfo(
            uriString = uriString,
            name = displayName,
            sizeBytes = file.length(),
            isVideo = finalResult.isVideo,
            isAudio = finalResult.isAudio || !finalResult.isVideo,
            ext = ext,
            durationSec = finalResult.durationSec,
            width = finalResult.width,
            height = finalResult.height,
            vcodec = finalResult.vcodec,
            acodec = finalResult.acodec,
            fps = finalResult.fps,
        )
    }

    private fun readWithRetriever(
        defaultIsVideo: Boolean,
        defaultIsAudio: Boolean,
        bindSource: MediaMetadataRetriever.() -> Unit,
    ): RetrieverProbeResult {
        var isVideo = defaultIsVideo
        var isAudio = defaultIsAudio
        var durationSec = 0.0
        var width: Int? = null
        var height: Int? = null
        var fps: Double? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.bindSource()
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durationSec = durMs / 1000.0
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            fps = extractRetrieverFps(retriever, durationSec)
            val hasDimensions = (w ?: 0) > 0 && (h ?: 0) > 0
            if (hasVideo || hasDimensions) {
                isVideo = true
                width = w
                height = h
            } else if (hasAudio) {
                isAudio = true
            }
        } catch (_: Throwable) {
            // Keep extension/MIME fallback values when retriever cannot parse exotic containers
        } finally {
            runCatching { retriever.release() }
        }
        return RetrieverProbeResult(isVideo, isAudio, durationSec, width, height, fps = fps)
    }

    private fun extractRetrieverFps(retriever: MediaMetadataRetriever, durationSec: Double): Double? {
        val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toDoubleOrNull()?.takeIf { it > 0.0 }
        if (captureFps != null) return captureFps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && durationSec > 0.0) {
            val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
            if (frames != null && frames > 0L) {
                return (frames.toDouble() / durationSec).takeIf { it in 1.0..240.0 }
            }
        }
        return null
    }

    private fun enrichWithMediaExtractor(
        base: RetrieverProbeResult,
        bindSource: MediaExtractor.() -> Unit,
    ): RetrieverProbeResult {
        var result = base
        val extractor = MediaExtractor()
        try {
            extractor.bindSource()
            for (idx in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase().orEmpty()
                if (mime.startsWith("video/") && result.vcodec == null) {
                    val fps = readTrackFps(format) ?: result.fps
                    val w = result.width ?: readTrackInt(format, MediaFormat.KEY_WIDTH)
                    val h = result.height ?: readTrackInt(format, MediaFormat.KEY_HEIGHT)
                    result = result.copy(isVideo = true, vcodec = normalizeCodecMime(mime), width = w, height = h, fps = fps)
                } else if (mime.startsWith("audio/") && result.acodec == null) {
                    result = result.copy(isAudio = !result.isVideo, acodec = normalizeCodecMime(mime))
                }
            }
        } catch (_: Throwable) {
            // Fallback gracefully when container is not supported by MediaExtractor
        } finally {
            runCatching { extractor.release() }
        }
        return result
    }

    private fun readTrackFps(format: MediaFormat): Double? {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return null
        val intFps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull()
        val valFps = intFps ?: runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull()
        return valFps?.takeIf { it in 1.0..240.0 }
    }

    private fun readTrackInt(format: MediaFormat, key: String): Int? =
        if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrNull()?.takeIf { it > 0 } else null

    private fun normalizeCodecMime(mime: String): String =
        when {
            "av01" in mime -> "av1"
            "avc" in mime || "h264" in mime -> "h264"
            "hevc" in mime || "hvc" in mime || "h265" in mime -> "hevc"
            "vp9" in mime || "vp09" in mime -> "vp9"
            "vp8" in mime -> "vp8"
            "mp4v" in mime -> "mpeg4"
            "mp4a" in mime || "aac" in mime -> "aac"
            "opus" in mime -> "opus"
            "vorbis" in mime -> "vorbis"
            "flac" in mime -> "flac"
            "mpeg" in mime || "mp3" in mime -> "mp3"
            else -> mime.substringAfter('/').substringAfter("x-vnd.on2.")
        }

    private suspend fun enrichWithFfprobe(file: File, base: RetrieverProbeResult): RetrieverProbeResult {
        val rawJson = ffmpegExecutor.runSimpleProbeJson(file) ?: return base
        val dto = runCatching { json.decodeFromString<FfprobeResultDto>(rawJson) }.getOrNull() ?: return base
        val probeDur = dto.format?.duration?.toDoubleOrNull() ?: 0.0
        var result = if (base.durationSec <= 0.0 && probeDur > 0.0) base.copy(durationSec = probeDur) else base

        for (stream in dto.streams) {
            if (stream.codecType == "video" && result.width == null) {
                result = result.copy(
                    width = stream.width,
                    height = stream.height,
                    vcodec = stream.codecName,
                    isVideo = true,
                )
            } else if (stream.codecType == "audio" && result.acodec == null) {
                result = result.copy(
                    acodec = stream.codecName,
                    isAudio = !result.isVideo,
                )
            }
        }
        return result
    }

    companion object {
        val VIDEO_EXTS = setOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".ts", ".wmv", ".m4v", ".3gp")
        val AUDIO_EXTS = setOf(".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wma")
    }
}
