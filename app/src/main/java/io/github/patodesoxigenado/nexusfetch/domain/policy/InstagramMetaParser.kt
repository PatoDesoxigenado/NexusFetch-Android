package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.PlaylistItem
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URI

/**
 * Represents a single slide parsed from an Instagram post or carousel embed.
 */
data class InstagramSlide(
    val mediaUrl: String,
    val thumbnailUrl: String?,
    val isVideo: Boolean,
)

/**
 * Structured media and caption payload extracted from Instagram embed HTML.
 */
data class InstagramEmbedData(
    val slides: List<InstagramSlide> = emptyList(),
    val fallbackVideoUrl: String? = null,
    val fallbackImages: List<String> = emptyList(),
    val ownerUsername: String? = null,
    val caption: String? = null,
)

private data class ParsedContextJson(
    val slides: List<InstagramSlide>,
    val ownerUsername: String?,
    val caption: String?,
)

/**
 * Pure Kotlin OpenGraph and embed HTML/JSON parser for public Instagram posts, reels, and carousels.
 */
object InstagramMetaParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val shortcodeRegex = Regex(
        """(?:instagram\.com|instagr\.am)(?:/[a-zA-Z0-9_.]+)?/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val metaTagRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([a-zA-Z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val htmlEntityRegex = Regex("""&(?:#(\d+)|#x([0-9a-fA-F]+)|([a-zA-Z]+));""")

    private val embedVideoJsonRegex = Regex(""""video_url"\s*:\s*"(https:[^"]+)"""")
    private val embedVideoTagRegex = Regex("""<video\b[^>]*\bsrc\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val embedMediaImgClassFirstRegex = Regex(
        """<img\b[^>]*class\s*=\s*"[^"]*EmbeddedMediaImage[^"]*"[^>]*\bsrc\s*=\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val embedMediaImgSrcFirstRegex = Regex(
        """<img\b[^>]*\bsrc\s*=\s*"([^"]+)"[^>]*class\s*=\s*"[^"]*EmbeddedMediaImage[^"]*"""",
        RegexOption.IGNORE_CASE,
    )
    private val embedDisplayUrlJsonRegex = Regex(""""display_url"\s*:\s*"(https:[^"]+)"""")

    private val namedEntities = mapOf(
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
    )

    private val avatarIndicators = listOf("s150x150", "s100x100", "s64x64", "profile_pic", "t51.2885-19")

    fun extractShortcode(url: String): String? =
        shortcodeRegex.find(url)?.groupValues?.getOrNull(1)

    fun decodeHtmlEntities(input: String): String =
        htmlEntityRegex.replace(input) { match ->
            val dec = match.groupValues[1]
            val hex = match.groupValues[2]
            val named = match.groupValues[3]
            when {
                dec.isNotEmpty() -> dec.toIntOrNull()?.toChar()?.toString() ?: match.value
                hex.isNotEmpty() -> hex.toIntOrNull(16)?.toChar()?.toString() ?: match.value
                else -> namedEntities[named.lowercase()] ?: match.value
            }
        }

    fun extractMetaTag(html: String, vararg names: String): String? {
        val targets = names.map { it.lowercase() }.toSet()
        for (tagMatch in metaTagRegex.findAll(html)) {
            val attrs = parseTagAttributes(tagMatch.value)
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
            if (key in targets) {
                val content = attrs["content"]?.trim().orEmpty()
                if (content.isNotEmpty()) {
                    return decodeHtmlEntities(content)
                }
            }
        }
        return null
    }

    /**
     * Parses both embedded `contextJSON` (GraphSidecar slides) and legacy DOM/script fallbacks.
     */
    fun extractEmbedData(embedHtml: String): InstagramEmbedData {
        if (embedHtml.isBlank()) return InstagramEmbedData()

        val parsedContext = parseContextJson(embedHtml)
        val rawFallbackVideo = embedVideoJsonRegex.find(embedHtml)?.groupValues?.getOrNull(1)?.let(::unescapeJsonUrl)
            ?: embedVideoTagRegex.find(embedHtml)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)
        val fallbackVideoUrl = rawFallbackVideo?.takeIf { it.startsWith("https://", ignoreCase = true) }

        val candidates = mutableListOf<String>()
        embedDisplayUrlJsonRegex.findAll(embedHtml).forEach { match ->
            candidates.add(unescapeJsonUrl(match.groupValues[1]))
        }
        embedMediaImgClassFirstRegex.findAll(embedHtml).forEach { match ->
            candidates.add(decodeHtmlEntities(match.groupValues[1]))
        }
        embedMediaImgSrcFirstRegex.findAll(embedHtml).forEach { match ->
            candidates.add(decodeHtmlEntities(match.groupValues[1]))
        }
        val fallbackImages = MediaSanitizer.deduplicateMediaUrls(candidates.filter(::isValidPublicMediaUrl))

        return InstagramEmbedData(
            slides = parsedContext?.slides.orEmpty(),
            fallbackVideoUrl = fallbackVideoUrl,
            fallbackImages = fallbackImages,
            ownerUsername = parsedContext?.ownerUsername,
            caption = parsedContext?.caption,
        )
    }

    fun buildMetadata(
        originalUrl: String,
        shortcode: String,
        pageHtml: String,
        embedHtml: String = "",
        fullResImageUrl: String? = null,
    ): MediaMetadata? {
        val ogImage = extractMetaTag(pageHtml, "og:image", "twitter:image")?.takeIf(::isValidPublicMediaUrl)
        val ogVideo = extractMetaTag(pageHtml, "og:video", "og:video:secure_url")
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }
        val ogType = extractMetaTag(pageHtml, "og:type").orEmpty()
        val embedData = extractEmbedData(embedHtml)

        val hasAnyMedia = embedData.slides.isNotEmpty() ||
            embedData.fallbackVideoUrl != null ||
            embedData.fallbackImages.isNotEmpty() ||
            ogVideo != null ||
            !ogImage.isNullOrBlank() ||
            !fullResImageUrl.isNullOrBlank()
        if (!hasAnyMedia) return null

        val rawTitle = extractMetaTag(pageHtml, "og:title")
        val ogDesc = extractMetaTag(pageHtml, "og:description").orEmpty()
        val (author, titleCandidate) = parseAuthorAndTitle(rawTitle, ogDesc, embedData)
        val finalTitle = titleCandidate.replace("\n", " ").trim().let {
            if (it.length > 90) "${it.take(90)}..." else it
        }

        // Priority 1: Carousels (including mixed photo + video or multi-video sidecar posts)
        val carouselSlides = when {
            embedData.slides.size > 1 -> embedData.slides
            embedData.fallbackImages.size > 1 -> embedData.fallbackImages.map { mediaUrl ->
                InstagramSlide(
                    mediaUrl = mediaUrl,
                    thumbnailUrl = mediaUrl,
                    isVideo = MediaSanitizer.isVideoMediaUrl(mediaUrl),
                )
            }
            else -> emptyList()
        }
        if (carouselSlides.size > 1) {
            return buildCarouselMetadata(
                originalUrl = originalUrl,
                shortcode = shortcode,
                title = finalTitle,
                author = author,
                ogImage = ogImage,
                slides = carouselSlides,
            )
        }

        // Priority 2: Single Video / Reel with direct MP4 stream
        val singleSlide = embedData.slides.singleOrNull()
        val videoUrl = singleSlide?.takeIf { it.isVideo }?.mediaUrl ?: ogVideo ?: embedData.fallbackVideoUrl
        if (videoUrl != null) {
            return MediaMetadata(
                url = originalUrl,
                platform = Platform.INSTAGRAM,
                title = finalTitle.ifBlank { "Instagram Vídeo ($shortcode)" },
                uploader = author,
                durationStr = "Vídeo / Reel",
                thumbnailUrl = singleSlide?.thumbnailUrl ?: ogImage ?: embedData.fallbackImages.firstOrNull(),
                qualities = listOf(
                    VideoQualityOption(
                        label = "Vídeo Original (Instagram MP4)",
                        formatId = "direct_best",
                        directUrl = videoUrl,
                    ),
                ),
                mediaType = MediaType.VIDEO,
                videoStreamUrl = videoUrl,
            )
        }

        if (ogType.contains("video", ignoreCase = true)) {
            // Let yt-dlp extract the actual MP4 stream for video posts lacking direct og:video tags
            return null
        }

        // Priority 3: Single Photo
        val resolvedImages = resolveSingleImageList(singleSlide, embedData.fallbackImages, fullResImageUrl, ogImage)
        if (resolvedImages.isEmpty()) return null

        return MediaMetadata(
            url = originalUrl,
            platform = Platform.INSTAGRAM,
            title = finalTitle.ifBlank { "Instagram Foto ($shortcode)" },
            uploader = author,
            durationSec = 0,
            durationStr = "Foto / Imagem",
            thumbnailUrl = resolvedImages.firstOrNull(),
            qualities = listOf(VideoQualityOption(label = "Imagem Original", formatId = "photo_orig")),
            mediaType = MediaType.IMAGE,
            imageUrls = resolvedImages,
        )
    }

    private fun parseContextJson(embedHtml: String): ParsedContextJson? {
        val quotedLiteral = extractJsonStringLiteral(embedHtml, "contextJSON") ?: return null
        val innerJson = runCatching { json.decodeFromString<String>(quotedLiteral) }.getOrNull() ?: return null
        val rootObj = runCatching { json.parseToJsonElement(innerJson) as? JsonObject }.getOrNull() ?: return null
        val gqlData = rootObj["gql_data"] as? JsonObject
        val shortcodeMedia = gqlData?.get("shortcode_media") as? JsonObject ?: return null

        val ownerUsername = (shortcodeMedia["owner"] as? JsonObject)
            ?.stringField("username")
            ?.trim()
            ?.ifEmpty { null }
        val caption = extractCaption(shortcodeMedia)

        val sidecarEdges = (shortcodeMedia["edge_sidecar_to_children"] as? JsonObject)
            ?.get("edges") as? JsonArray
        val rawSlides = if (!sidecarEdges.isNullOrEmpty()) {
            sidecarEdges.mapNotNull { edge ->
                val node = (edge as? JsonObject)?.get("node") as? JsonObject
                node?.let(::parseSlideNode)
            }
        } else {
            listOfNotNull(parseSlideNode(shortcodeMedia))
        }

        val uniqueSlides = deduplicateSlides(rawSlides)
        return ParsedContextJson(
            slides = uniqueSlides,
            ownerUsername = ownerUsername,
            caption = caption,
        )
    }

    private fun extractJsonStringLiteral(source: String, key: String): String? {
        val token = "\"$key\""
        var searchFrom = 0
        while (searchFrom < source.length) {
            val keyIdx = source.indexOf(token, searchFrom)
            if (keyIdx < 0) return null

            val colonIdx = skipWhitespace(source, keyIdx + token.length)
            if (colonIdx < source.length && source[colonIdx] == ':') {
                val valueIdx = skipWhitespace(source, colonIdx + 1)
                if (valueIdx < source.length && source[valueIdx] == '"') {
                    return scanQuotedLiteral(source, valueIdx)
                }
            }
            searchFrom = keyIdx + token.length
        }
        return null
    }

    private fun parseSlideNode(node: JsonObject): InstagramSlide? {
        val typeName = node.stringField("__typename")
        val isVideoFlag = (node["is_video"] as? JsonPrimitive)?.booleanOrNull == true
        val isVideo = isVideoFlag || typeName == "GraphVideo"
        val displayUrl = node.stringField("display_url")?.takeIf(::isValidPublicMediaUrl)
            ?: pickBestDisplayResource(node["display_resources"] as? JsonArray)
        val videoUrl = node.stringField("video_url")?.takeIf { it.startsWith("https://", ignoreCase = true) }

        return when {
            isVideo && videoUrl != null -> InstagramSlide(
                mediaUrl = videoUrl,
                thumbnailUrl = displayUrl,
                isVideo = true,
            )
            displayUrl != null -> InstagramSlide(
                mediaUrl = displayUrl,
                thumbnailUrl = displayUrl,
                isVideo = false,
            )
            else -> null
        }
    }

    private fun pickBestDisplayResource(resources: JsonArray?): String? =
        resources.orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { obj ->
                val src = obj.stringField("src")?.takeIf(::isValidPublicMediaUrl) ?: return@mapNotNull null
                val area = (obj.longField("config_width") ?: 0L) * (obj.longField("config_height") ?: 0L)
                area to src
            }
            .maxByOrNull { it.first }
            ?.second

    private fun extractCaption(shortcodeMedia: JsonObject): String? {
        val edges = (shortcodeMedia["edge_media_to_caption"] as? JsonObject)
            ?.get("edges") as? JsonArray ?: return null
        val firstNode = (edges.firstOrNull() as? JsonObject)?.get("node") as? JsonObject ?: return null
        return firstNode.stringField("text")?.trim()?.ifEmpty { null }
    }

    private fun deduplicateSlides(rawSlides: List<InstagramSlide>): List<InstagramSlide> {
        val unique = mutableListOf<InstagramSlide>()
        val seenKeys = mutableSetOf<String>()
        for (slide in rawSlides) {
            val isHttps = slide.mediaUrl.startsWith("https://", ignoreCase = true)
            val isValidMedia = slide.isVideo || isValidPublicMediaUrl(slide.mediaUrl)
            if (!isHttps || !isValidMedia) continue
            val parsedPath = runCatching { URI(slide.mediaUrl).path.orEmpty() }.getOrDefault("")
            val basename = parsedPath.substringAfterLast('/').ifEmpty { parsedPath }
            val key = basename.ifEmpty { slide.mediaUrl }
            if (seenKeys.add(key)) {
                unique.add(slide)
            }
        }
        return unique
    }

    private fun isValidPublicMediaUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) &&
            avatarIndicators.none { marker -> url.contains(marker, ignoreCase = true) }

    private fun resolveSingleImageList(
        singleSlide: InstagramSlide?,
        fallbackImages: List<String>,
        fullResImageUrl: String?,
        ogImage: String?,
    ): List<String> =
        when {
            !fullResImageUrl.isNullOrBlank() -> listOf(fullResImageUrl)
            singleSlide != null && !singleSlide.isVideo -> listOf(singleSlide.mediaUrl)
            fallbackImages.isNotEmpty() -> listOf(fallbackImages.first())
            !ogImage.isNullOrBlank() -> listOf(ogImage)
            else -> emptyList()
        }.let(MediaSanitizer::deduplicateMediaUrls)

    private fun buildCarouselMetadata(
        originalUrl: String,
        shortcode: String,
        title: String,
        author: String,
        ogImage: String?,
        slides: List<InstagramSlide>,
    ): MediaMetadata {
        val items = slides.mapIndexed { idx, slide ->
            PlaylistItem(
                index = idx + 1,
                videoId = "slide_${idx + 1}",
                title = if (slide.isVideo) "Vídeo ${idx + 1}" else "Imagem ${idx + 1}",
                durationStr = if (slide.isVideo) "Vídeo MP4" else "Foto HD",
                url = slide.mediaUrl,
                thumbnailUrl = slide.thumbnailUrl ?: ogImage,
                uploader = author,
                isSelected = true,
                itemType = if (slide.isVideo) MediaType.VIDEO else MediaType.IMAGE,
            )
        }
        val imageUrls = slides.filterNot { it.isVideo }.map { it.mediaUrl }
        return MediaMetadata(
            url = originalUrl,
            platform = Platform.INSTAGRAM,
            title = title.ifBlank { "Instagram Carrossel ($shortcode)" },
            uploader = author,
            durationStr = "${items.size} mídias",
            thumbnailUrl = slides.firstOrNull()?.thumbnailUrl ?: ogImage ?: imageUrls.firstOrNull(),
            qualities = listOf(VideoQualityOption(label = "Mídias Originais", formatId = "carousel_orig")),
            mediaType = MediaType.CAROUSEL,
            imageUrls = imageUrls,
            isPlaylist = true,
            playlistItems = items,
            playlistCount = items.size,
        )
    }

    private fun parseAuthorAndTitle(
        rawTitle: String?,
        ogDesc: String,
        embedData: InstagramEmbedData,
    ): Pair<String, String> {
        var author = "Instagram"
        var cleanTitle = ""
        val sourceTitle = rawTitle.orEmpty()
        when {
            " on Instagram: " in sourceTitle -> {
                val parts = sourceTitle.split(" on Instagram: ", limit = 2)
                author = parts[0].trim()
                cleanTitle = parts[1].trim().trim('"')
            }
            " no Instagram: " in sourceTitle -> {
                val parts = sourceTitle.split(" no Instagram: ", limit = 2)
                author = parts[0].trim()
                cleanTitle = parts[1].trim().trim('"')
            }
            " (@" in sourceTitle -> {
                author = sourceTitle.substringBefore(" (@").trim()
            }
        }
        if (author == "Instagram" && !embedData.ownerUsername.isNullOrBlank()) {
            author = embedData.ownerUsername
        }
        val resolvedTitle = cleanTitle.ifBlank {
            ogDesc.trim().ifBlank {
                embedData.caption.orEmpty().trim().ifBlank { sourceTitle.trim() }
            }
        }
        return author to resolvedTitle
    }

    private fun parseTagAttributes(tagHtml: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        for (match in attrRegex.findAll(tagHtml)) {
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            attrs[name] = value
        }
        return attrs
    }

    private fun unescapeJsonUrl(raw: String): String =
        raw.replace("\\/", "/").replace("\\u0026", "&").let(::decodeHtmlEntities)
}

private fun skipWhitespace(source: String, startIdx: Int): Int {
    var idx = startIdx
    while (idx < source.length && source[idx].isWhitespace()) {
        idx++
    }
    return idx
}

private fun scanQuotedLiteral(source: String, openQuoteIdx: Int): String? {
    var idx = openQuoteIdx + 1
    var escaped = false
    while (idx < source.length) {
        val ch = source[idx]
        if (escaped) {
            escaped = false
        } else if (ch == '\\') {
            escaped = true
        } else if (ch == '"') {
            return source.substring(openQuoteIdx, idx + 1)
        }
        idx++
    }
    return null
}

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longField(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull
