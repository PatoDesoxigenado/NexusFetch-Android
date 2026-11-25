package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.PlaylistItem
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class TwitterSyndicationResponse(
    val text: String = "",
    val user: TwitterUserDto? = null,
    val mediaDetails: List<TwitterMediaDetailDto> = emptyList(),
    val photos: List<TwitterPhotoDto> = emptyList(),
)

@Serializable
data class TwitterUserDto(
    val name: String = "Usuário do X",
    @SerialName("screen_name") val screenName: String = "",
)

@Serializable
data class TwitterMediaDetailDto(
    val type: String = "",
    @SerialName("media_url_https") val mediaUrlHttps: String? = null,
    @SerialName("original_info") val originalInfo: TwitterOriginalInfoDto? = null,
    @SerialName("video_info") val videoInfo: TwitterVideoInfoDto? = null,
)

@Serializable
data class TwitterOriginalInfoDto(
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class TwitterPhotoDto(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class TwitterVideoInfoDto(
    @SerialName("duration_millis") val durationMillis: Long? = null,
    val variants: List<TwitterVideoVariantDto> = emptyList(),
)

@Serializable
data class TwitterVideoVariantDto(
    val bitrate: Long = 0L,
    @SerialName("content_type") val contentType: String = "",
    val url: String = "",
)

@Serializable
data class FxTwitterResponse(
    val code: Int = 0,
    val tweet: FxTweetDto? = null,
)

@Serializable
data class FxTweetDto(
    val text: String = "",
    val author: FxAuthorDto? = null,
    val media: FxMediaDto? = null,
)

@Serializable
data class FxAuthorDto(
    val name: String = "Usuário do X",
    @SerialName("screen_name") val screenName: String = "",
)

@Serializable
data class FxMediaDto(
    val photos: List<FxPhotoDto> = emptyList(),
    val videos: List<FxVideoDto> = emptyList(),
)

@Serializable
data class FxPhotoDto(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class FxVideoDto(
    val url: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val type: String = "video",
    val formats: List<FxVideoFormatDto> = emptyList(),
)

@Serializable
data class FxVideoFormatDto(
    val url: String = "",
    val bitrate: Long = 0L,
    @SerialName("container") val container: String = "",
)

/**
 * Extracts Twitter/X media strictly from the root tweet, preventing quote-tweet media leaks
 * and upgrading images to original quality (`?name=orig`) without duplicates.
 */
object TwitterMediaFilter {

    private val json = Json { ignoreUnknownKeys = true }
    private val tweetIdRegex = Regex("""status/(\d+)""")
    private val videoResolutionRegex = Regex("""/vid/(?:[a-zA-Z0-9]+/)?(\d+)x(\d+)/""")

    fun extractTweetId(url: String): String? =
        tweetIdRegex.find(url)?.groupValues?.getOrNull(1)

    fun requiresSingleItemFilter(url: String): Boolean =
        url.contains("twitter.com/", ignoreCase = true) || url.contains("x.com/", ignoreCase = true)

    fun parseSyndicationPayload(url: String, jsonBody: String): MediaMetadata? {
        val dto = runCatching { json.decodeFromString<TwitterSyndicationResponse>(jsonBody) }.getOrNull()
            ?: return null

        val user = dto.user
        val authorName = user?.name?.ifBlank { "Usuário do X" } ?: "Usuário do X"
        val authorStr = if (!user?.screenName.isNullOrBlank()) {
            "$authorName (@${user?.screenName})"
        } else {
            authorName
        }

        val flatText = dto.text.ifBlank { "Post do X" }.replace("\n", " ").trim()
        val cleanTitle = if (flatText.length > 85) "${flatText.take(85)}..." else flatText

        val details = dto.mediaDetails
        val photos = dto.photos

        if (photos.isNotEmpty() || details.firstOrNull()?.type == "photo") {
            return buildPhotoOrCarouselMetadata(url, cleanTitle, authorStr, photos, details)
        }

        if (details.any { it.type == "animated_gif" }) {
            return buildGifMetadata(url, cleanTitle, authorStr, details)
        }

        if (details.any { it.type == "video" }) {
            return buildVideoMetadata(url, cleanTitle, authorStr, details)
        }

        return null
    }

    private fun buildPhotoOrCarouselMetadata(
        url: String,
        title: String,
        uploader: String,
        photos: List<TwitterPhotoDto>,
        details: List<TwitterMediaDetailDto>,
    ): MediaMetadata {
        val rawUrls = if (photos.isNotEmpty()) {
            photos.mapNotNull { it.url }
        } else {
            details.filter { it.type == "photo" }.mapNotNull { it.mediaUrlHttps }
        }
        val upgraded = MediaSanitizer.deduplicateMediaUrls(
            rawUrls.map { MediaSanitizer.upgradeTwitterImageUrlToOrig(it) },
        )

        if (upgraded.size > 1) {
            val items = upgraded.mapIndexed { idx, imgUrl ->
                PlaylistItem(
                    index = idx + 1,
                    videoId = "slide_${idx + 1}",
                    title = "Imagem ${String.format(Locale.US, "%02d", idx + 1)}",
                    durationSec = 0,
                    durationStr = "Foto HD",
                    url = imgUrl,
                    thumbnailUrl = imgUrl,
                    uploader = uploader,
                    isSelected = true,
                    itemType = MediaType.IMAGE,
                )
            }
            return MediaMetadata(
                url = url,
                platform = Platform.TWITTER,
                title = title,
                uploader = uploader,
                durationSec = 0,
                durationStr = "${items.size} mídias",
                thumbnailUrl = upgraded.firstOrNull(),
                qualities = listOf(
                    VideoQualityOption(
                        label = "Mídias Originais (Máxima Qualidade)",
                        formatId = "carousel_orig",
                    ),
                ),
                mediaType = MediaType.CAROUSEL,
                imageUrls = upgraded,
                isPlaylist = true,
                playlistItems = items,
                playlistCount = items.size,
            )
        }

        val firstPhoto = photos.firstOrNull()
        val firstDetail = details.firstOrNull()
        val width = firstPhoto?.width ?: firstDetail?.originalInfo?.width
        val height = firstPhoto?.height ?: firstDetail?.originalInfo?.height
        val dimTag = if (width != null && height != null) " (${width}x$height)" else " (Alta Resolução)"

        return MediaMetadata(
            url = url,
            platform = Platform.TWITTER,
            title = title,
            uploader = uploader,
            durationSec = 0,
            durationStr = "Foto / Imagem",
            thumbnailUrl = upgraded.firstOrNull(),
            qualities = listOf(
                VideoQualityOption(
                    label = "Imagem Original$dimTag",
                    height = height,
                    formatId = "photo_orig",
                ),
            ),
            mediaType = MediaType.IMAGE,
            imageUrls = upgraded,
        )
    }

    private fun buildGifMetadata(
        url: String,
        title: String,
        uploader: String,
        details: List<TwitterMediaDetailDto>,
    ): MediaMetadata {
        val gifDetail = details.first { it.type == "animated_gif" }
        val mp4Url = gifDetail.videoInfo?.variants
            ?.filter { it.contentType == "video/mp4" }
            ?.maxByOrNull { it.bitrate }
            ?.url

        return MediaMetadata(
            url = url,
            platform = Platform.TWITTER,
            title = "[GIF] $title",
            uploader = uploader,
            durationSec = 0,
            durationStr = "Animação GIF",
            thumbnailUrl = gifDetail.mediaUrlHttps,
            qualities = listOf(
                VideoQualityOption(
                    label = "Salvar como Animação (.GIF)",
                    formatId = "gif_convert",
                    directUrl = mp4Url,
                ),
                VideoQualityOption(
                    label = "Salvar como Vídeo (.MP4)",
                    formatId = "gif_mp4",
                    directUrl = mp4Url,
                ),
            ),
            mediaType = MediaType.GIF,
            videoStreamUrl = mp4Url,
        )
    }

    private fun buildVideoMetadata(
        url: String,
        title: String,
        uploader: String,
        details: List<TwitterMediaDetailDto>,
    ): MediaMetadata {
        val videoDetail = details.first { it.type == "video" }
        val durationSec = videoDetail.videoInfo?.durationMillis?.let { (it / 1000L).toInt() }
        val variants = videoDetail.videoInfo?.variants
            ?.filter { it.contentType == "video/mp4" && it.url.startsWith("https://") }
            ?.sortedByDescending { it.bitrate }
            .orEmpty()

        val bestUrl = variants.firstOrNull()?.url
        val qualities = mutableListOf(
            VideoQualityOption(
                label = "Máxima Resolução (HD)",
                height = null,
                formatId = "bestvideo+bestaudio/best",
                directUrl = bestUrl,
            ),
        )
        val seenHeights = mutableSetOf<Int>()
        for (variant in variants) {
            val h = videoResolutionRegex.find(variant.url)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: continue
            if (seenHeights.add(h)) {
                qualities.add(
                    VideoQualityOption(
                        label = if (h >= 720) "${h}p (HD)" else "${h}p",
                        height = h,
                        formatId = "bestvideo[height<=$h]+bestaudio/best[height<=$h]",
                        directUrl = variant.url,
                    ),
                )
            }
        }

        return MediaMetadata(
            url = url,
            platform = Platform.TWITTER,
            title = title,
            uploader = uploader,
            durationSec = durationSec,
            durationStr = if (durationSec != null) MediaSanitizer.formatDuration(durationSec) else "--:--",
            thumbnailUrl = videoDetail.mediaUrlHttps,
            qualities = qualities,
            mediaType = MediaType.VIDEO,
            videoStreamUrl = bestUrl,
        )
    }

    fun parseFxTwitterPayload(url: String, jsonBody: String): MediaMetadata? {
        val dto = runCatching { json.decodeFromString<FxTwitterResponse>(jsonBody) }.getOrNull() ?: return null
        val tweet = dto.tweet ?: return null
        val media = tweet.media ?: return null

        val authorName = tweet.author?.name?.ifBlank { "Usuário do X" } ?: "Usuário do X"
        val handle = tweet.author?.screenName.orEmpty()
        val uploader = if (handle.isNotBlank()) "$authorName (@$handle)" else authorName
        val flatText = tweet.text.ifBlank { "Post do X" }.replace("\n", " ").trim()
        val title = if (flatText.length > 85) "${flatText.take(85)}..." else flatText

        val video = media.videos.firstOrNull()
        if (video != null) {
            return buildFxVideoOrGifMetadata(url, title, uploader, video)
        }
        val photos = media.photos.mapNotNull { it.url }
        if (photos.isNotEmpty()) {
            val photoDtos = media.photos.map { TwitterPhotoDto(it.url, it.width, it.height) }
            return buildPhotoOrCarouselMetadata(url, title, uploader, photoDtos, emptyList())
        }
        return null
    }

    private fun buildFxVideoOrGifMetadata(
        url: String,
        title: String,
        uploader: String,
        video: FxVideoDto,
    ): MediaMetadata? {
        val mp4Variants = video.formats
            .filter { it.url.startsWith("https://") && (it.container == "mp4" || it.url.contains(".mp4")) }
            .sortedByDescending { it.bitrate }
        val bestUrl = mp4Variants.firstOrNull()?.url ?: video.url?.takeIf { it.startsWith("https://") }
            ?: return null

        if (video.type == "gif") {
            return MediaMetadata(
                url = url,
                platform = Platform.TWITTER,
                title = "[GIF] $title",
                uploader = uploader,
                durationSec = 0,
                durationStr = "Animação GIF",
                thumbnailUrl = video.thumbnailUrl,
                qualities = listOf(
                    VideoQualityOption(label = "Salvar como Animação (.GIF)", formatId = "gif_convert", directUrl = bestUrl),
                    VideoQualityOption(label = "Salvar como Vídeo (.MP4)", formatId = "gif_mp4", directUrl = bestUrl),
                ),
                mediaType = MediaType.GIF,
                videoStreamUrl = bestUrl,
            )
        }

        val durSec = video.duration?.toInt()
        val qualities = mutableListOf(
            VideoQualityOption(label = "Máxima Resolução (HD)", formatId = "bestvideo+bestaudio/best", directUrl = bestUrl),
        )
        val seenHeights = mutableSetOf<Int>()
        for (variant in mp4Variants) {
            val h = videoResolutionRegex.find(variant.url)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: continue
            if (seenHeights.add(h)) {
                qualities.add(
                    VideoQualityOption(
                        label = if (h >= 720) "${h}p (HD)" else "${h}p",
                        height = h,
                        formatId = "bestvideo[height<=$h]+bestaudio/best[height<=$h]",
                        directUrl = variant.url,
                    ),
                )
            }
        }
        return MediaMetadata(
            url = url,
            platform = Platform.TWITTER,
            title = title,
            uploader = uploader,
            durationSec = durSec,
            durationStr = if (durSec != null && durSec > 0) MediaSanitizer.formatDuration(durSec) else "--:--",
            thumbnailUrl = video.thumbnailUrl,
            qualities = qualities,
            mediaType = MediaType.VIDEO,
            videoStreamUrl = bestUrl,
        )
    }
}
