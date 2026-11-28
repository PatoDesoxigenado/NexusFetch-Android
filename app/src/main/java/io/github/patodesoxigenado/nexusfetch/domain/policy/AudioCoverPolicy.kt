package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pixel crop rectangle used to extract a centered 1:1 album cover from YouTube/YT Music thumbnails.
 */
data class CoverCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Pure Kotlin policy mirroring NexusFetch Desktop v1.45 MP3 album cover art selection,
 * 1:1 center cropping (with 4:3 black-bar letterbox stripping), and FFmpeg ID3v2.3 attachment args.
 */
object AudioCoverPolicy {

    private const val RATIO_4_3 = 4.0 / 3.0
    private const val RATIO_4_3_TOLERANCE = 0.08
    private const val LETTERBOX_BAR_FRACTION = 0.125
    private const val MIN_4_3_HEIGHT_PX = 40
    private const val BLACK_BAND_MAX_MEAN_LUMA = 12.0

    fun buildThumbnailCandidates(
        sourceUrl: String,
        fallbackThumbnailUrl: String?,
    ): List<String> {
        if (UrlValidator.detectPlatform(sourceUrl) != Platform.YOUTUBE) {
            return emptyList()
        }
        val videoId = YouTubeFastMetaParser.extractVideoId(sourceUrl)
        val cleanFallback = fallbackThumbnailUrl?.trim()?.takeIf(::isHttpsUrl)
        if (videoId.isNullOrBlank()) {
            return listOfNotNull(cleanFallback)
        }

        val ordered = linkedSetOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
        )
        if (cleanFallback != null) {
            ordered.add(cleanFallback)
        }
        ordered.add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        return ordered.toList()
    }

    fun isApprox43Ratio(width: Int, height: Int): Boolean {
        if (width <= height || height < MIN_4_3_HEIGHT_PX) return false
        return abs((width.toDouble() / height.toDouble()) - RATIO_4_3) < RATIO_4_3_TOLERANCE
    }

    fun letterboxBandHeight(height: Int): Int =
        maxOf(1, (height * LETTERBOX_BAR_FRACTION).roundToInt())

    fun computeCoverCrop(
        width: Int,
        height: Int,
        topBandMeanLuma: Double? = null,
        bottomBandMeanLuma: Double? = null,
    ): CoverCropRect {
        if (width <= 0 || height <= 0) {
            return CoverCropRect(0, 0, maxOf(0, width), maxOf(0, height))
        }

        var activeTop = 0
        var activeHeight = height
        if (hasDarkLetterboxBands(width, height, topBandMeanLuma, bottomBandMeanLuma)) {
            val barH = letterboxBandHeight(height)
            if (barH * 2 < height) {
                activeTop = barH
                activeHeight = height - barH * 2
            }
        }

        val side = minOf(width, activeHeight)
        val left = (width - side) / 2
        val top = activeTop + (activeHeight - side) / 2
        return CoverCropRect(left = left, top = top, width = side, height = side)
    }

    fun buildFfmpegEmbedArgs(
        mp3Path: String,
        coverJpgPath: String,
        outputMp3Path: String,
    ): List<String> = listOf(
        "-y",
        "-i", mp3Path,
        "-i", coverJpgPath,
        "-map", "0:a",
        "-map", "1:0",
        "-c", "copy",
        "-id3v2_version", "3",
        "-metadata:s:v", "title=Album cover",
        "-metadata:s:v", "comment=Cover (front)",
        outputMp3Path,
    )

    private fun hasDarkLetterboxBands(
        width: Int,
        height: Int,
        topBandMeanLuma: Double?,
        bottomBandMeanLuma: Double?,
    ): Boolean {
        if (!isApprox43Ratio(width, height)) return false
        if (topBandMeanLuma == null || bottomBandMeanLuma == null) return false
        return topBandMeanLuma < BLACK_BAND_MAX_MEAN_LUMA &&
            bottomBandMeanLuma < BLACK_BAND_MAX_MEAN_LUMA
    }

    private fun isHttpsUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true)
}
