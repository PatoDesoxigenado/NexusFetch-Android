package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class YtPlayerResponseDto(
    val playabilityStatus: YtPlayabilityDto? = null,
    val videoDetails: YtVideoDetailsDto? = null,
    val streamingData: YtStreamingDataDto? = null,
)

@Serializable
private data class YtPlayabilityDto(
    val status: String? = null,
)

@Serializable
private data class YtVideoDetailsDto(
    val videoId: String? = null,
    val title: String? = null,
    val lengthSeconds: String? = null,
    val author: String? = null,
    val isLiveContent: Boolean? = null,
    val thumbnail: YtThumbnailContainerDto? = null,
)

@Serializable
private data class YtThumbnailContainerDto(
    val thumbnails: List<YtThumbnailDto> = emptyList(),
)

@Serializable
private data class YtThumbnailDto(
    val url: String? = null,
)

@Serializable
private data class YtStreamingDataDto(
    val formats: List<YtFormatDto> = emptyList(),
    val adaptiveFormats: List<YtFormatDto> = emptyList(),
)

@Serializable
private data class YtFormatDto(
    val height: Int? = null,
    val mimeType: String? = null,
    val contentLength: String? = null,
    val bitrate: Long? = null,
)

/**
 * Pure Kotlin YouTube watch-page `ytInitialPlayerResponse` parser for sub-2s metadata extraction
 * without initializing Python/QuickJS.
 */
object YouTubeFastMetaParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val videoIdPatterns = listOf(
        Regex("""(?:youtube\.com|youtu\.be)/(?:shorts|live|embed)/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE),
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE),
        Regex("""[?&]v=([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE),
    )

    fun extractVideoId(url: String): String? {
        for (pattern in videoIdPatterns) {
            val match = pattern.find(url)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) return match
        }
        return null
    }

    fun extractPlayerResponseJson(html: String): String? {
        val markerIdx = html.indexOf("ytInitialPlayerResponse")
        if (markerIdx < 0) return null
        val openBraceIdx = html.indexOf('{', startIndex = markerIdx)
        if (openBraceIdx < 0) return null
        val closeBraceIdx = findMatchingBraceEnd(html, openBraceIdx)
        return if (closeBraceIdx > openBraceIdx) html.substring(openBraceIdx, closeBraceIdx + 1) else null
    }

    fun parsePlayerResponse(
        originalUrl: String,
        videoId: String,
        playerJson: String,
    ): MediaMetadata? {
        val dto = runCatching { json.decodeFromString<YtPlayerResponseDto>(playerJson) }.getOrNull() ?: return null
        if (dto.playabilityStatus?.status != "OK") return null

        val details = dto.videoDetails ?: return null
        val title = details.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val allFormats = dto.streamingData?.let { it.adaptiveFormats + it.formats }.orEmpty()
        if (allFormats.isEmpty()) return null

        val durationSec = details.lengthSeconds?.toIntOrNull()?.takeIf { it > 0 }
        val durationStr = durationSec?.let(MediaSanitizer::formatDuration) ?: "Ao vivo / N/A"
        val bestAudioBytes = computeBestAudioBytes(allFormats, durationSec)
        val sizeByHeight = computeVideoSizesByHeight(allFormats, durationSec, bestAudioBytes)
        if (sizeByHeight.isEmpty()) return null

        val thumb = details.thumbnail?.thumbnails
            ?.lastOrNull { !it.url.isNullOrBlank() && it.url.startsWith("https://") }
            ?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return MediaMetadata(
            url = originalUrl,
            platform = Platform.YOUTUBE,
            title = title,
            uploader = details.author?.ifBlank { "YouTube" } ?: "YouTube",
            durationSec = durationSec,
            durationStr = durationStr,
            thumbnailUrl = thumb,
            qualities = buildVideoQualities(sizeByHeight, isGif = false),
            mediaType = MediaType.VIDEO,
            estimatedAudioSizeBytes = bestAudioBytes.takeIf { it > 0L },
        )
    }

    fun buildVideoQualities(sizeByHeight: Map<Int, Long>, isGif: Boolean): List<VideoQualityOption> {
        val sortedHeights = sizeByHeight.keys.sortedDescending()
        val qualities = mutableListOf<VideoQualityOption>()
        if (isGif) {
            qualities.add(VideoQualityOption("Salvar como Animação (.GIF)", null, "gif_convert"))
        }
        val maxSize = sortedHeights.firstOrNull()?.let { sizeByHeight[it] } ?: 0L
        val maxTag = if (maxSize > 0L) " ~ ${MediaSanitizer.formatBytes(maxSize)}" else ""
        qualities.add(
            VideoQualityOption(
                label = "Máxima Resolução$maxTag",
                height = null,
                formatId = "bestvideo+bestaudio/best",
                filesizeBytes = maxSize.takeIf { it > 0L },
            ),
        )
        for (h in sortedHeights) {
            val resTag = when {
                h >= 2160 -> " (4K UHD)"
                h >= 1440 -> " (2K QHD)"
                h >= 1080 -> " (FHD)"
                h >= 720 -> " (HD)"
                else -> ""
            }
            val hSize = sizeByHeight[h] ?: 0L
            val sizeTag = if (hSize > 0L) " ~ ${MediaSanitizer.formatBytes(hSize)}" else ""
            qualities.add(
                VideoQualityOption(
                    label = "${h}p$resTag$sizeTag",
                    height = h,
                    formatId = "bestvideo[height<=$h]+bestaudio/best[height<=$h]",
                    filesizeBytes = hSize.takeIf { it > 0L },
                ),
            )
        }
        return qualities
    }

    private fun computeBestAudioBytes(formats: List<YtFormatDto>, durationSec: Int?): Long {
        var best = 0L
        for (fmt in formats) {
            if (fmt.mimeType?.startsWith("audio/", ignoreCase = true) != true) continue
            val size = estimateFormatBytes(fmt, durationSec)
            if (size > best) best = size
        }
        if (best == 0L && durationSec != null && durationSec > 0) {
            best = durationSec * 40L * 1024L
        }
        return best
    }

    private fun computeVideoSizesByHeight(
        formats: List<YtFormatDto>,
        durationSec: Int?,
        bestAudioBytes: Long,
    ): Map<Int, Long> {
        val sizeByHeight = mutableMapOf<Int, Long>()
        for (fmt in formats) {
            val h = fmt.height
            val isVideo = fmt.mimeType?.startsWith("video/", ignoreCase = true) == true
            if (h != null && h >= 144 && isVideo) {
                val vBytes = estimateFormatBytes(fmt, durationSec)
                val totalBytes = if (vBytes > 0L) vBytes + bestAudioBytes else 0L
                val current = sizeByHeight[h] ?: -1L
                if (totalBytes > current) {
                    sizeByHeight[h] = totalBytes
                }
            }
        }
        return sizeByHeight
    }

    private fun estimateFormatBytes(fmt: YtFormatDto, durationSec: Int?): Long {
        fmt.contentLength?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        val br = fmt.bitrate ?: return 0L
        val dur = durationSec ?: return 0L
        return if (br > 0L && dur > 0) (br * dur) / 8L else 0L
    }

    private fun findMatchingBraceEnd(text: String, openBraceIdx: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openBraceIdx until text.length) {
            val ch = text[i]
            if (inString) {
                val wasEscaped = escaped
                escaped = !wasEscaped && ch == '\\'
                if (!wasEscaped && ch == '"') inString = false
            } else if (ch == '"') {
                inString = true
            } else if (ch == '{') {
                depth++
            } else if (ch == '}' && --depth == 0) {
                return i
            }
        }
        return -1
    }
}
