package io.github.patodesoxigenado.nexusfetch.data.remote

import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.policy.InstagramMetaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts public Instagram photos, carousels (GraphSidecar `contextJSON`), and direct video metadata
 * using parallel crawler-compatible requests (`/p/<shortcode>/`, `/embed/captioned/`, and `/media/?size=l`).
 */
@Singleton
class InstagramMetaClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: NexusLogger,
) {
    suspend fun fetchMetadata(url: String): MediaMetadata? = withContext(Dispatchers.IO) {
        val shortcode = InstagramMetaParser.extractShortcode(url) ?: return@withContext null
        val startedNs = System.nanoTime()
        val postUrl = "https://www.instagram.com/p/$shortcode/"

        val (pageHtml, embedHtml) = coroutineScope {
            val pageDeferred = async(Dispatchers.IO) { fetchHtmlWithCrawlerFallback(postUrl, shortcode) }
            val embedDeferred = async(Dispatchers.IO) { fetchEmbedWithCrawlerFallback(shortcode) }
            pageDeferred.await() to embedDeferred.await()
        }

        if (pageHtml.isBlank() && embedHtml.isBlank()) {
            return@withContext null
        }

        val ogVideo = InstagramMetaParser.extractMetaTag(pageHtml, "og:video", "og:video:secure_url")
        val ogType = InstagramMetaParser.extractMetaTag(pageHtml, "og:type").orEmpty()
        val embedData = InstagramMetaParser.extractEmbedData(embedHtml)
        val isCarousel = embedData.slides.size > 1 || embedData.fallbackImages.size > 1
        val isLikelyVideo = ogVideo != null ||
            embedData.fallbackVideoUrl != null ||
            embedData.slides.any { it.isVideo } ||
            ogType.contains("video", ignoreCase = true)

        val fullResUrl = if (!isCarousel && !isLikelyVideo) {
            resolveUncroppedMediaUrl(shortcode)
        } else {
            null
        }

        val meta = InstagramMetaParser.buildMetadata(
            originalUrl = url,
            shortcode = shortcode,
            pageHtml = pageHtml,
            embedHtml = embedHtml,
            fullResImageUrl = fullResUrl,
        )

        if (meta != null) {
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            val slidesNote = if (meta.playlistCount > 1) " (${meta.playlistCount} slides)" else ""
            logger.info(
                module = "instagram",
                stage = "fast_meta",
                message = "Post $shortcode tipo=${meta.mediaType.id}$slidesNote",
                durationMs = elapsedMs,
            )
        }
        meta
    }

    private fun fetchHtmlWithCrawlerFallback(targetUrl: String, shortcode: String): String {
        val primary = fetchHtml(targetUrl, CRAWLER_UA, shortcode)
        if (primary.isNotBlank() && primary.contains("og:", ignoreCase = true)) {
            return primary
        }
        val secondary = fetchHtml(targetUrl, CURL_UA, shortcode)
        return if (secondary.contains("og:", ignoreCase = true)) secondary else primary
    }

    private fun fetchEmbedWithCrawlerFallback(shortcode: String): String {
        val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
        val primary = fetchHtml(embedUrl, CRAWLER_UA, shortcode)
        if (primary.isNotBlank()) {
            return primary
        }
        return fetchHtml(embedUrl, CURL_UA, shortcode)
    }

    private fun fetchHtml(targetUrl: String, userAgent: String, shortcode: String): String {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        }.onFailure { err ->
            logger.warn("instagram", "crawler_html", "Falha HTML ($shortcode): ${err.message}")
        }.getOrDefault("")
    }

    private fun resolveUncroppedMediaUrl(shortcode: String): String? {
        val mediaEndpoint = "https://www.instagram.com/p/$shortcode/media/?size=l"
        val request = Request.Builder()
            .url(mediaEndpoint)
            .header("User-Agent", CRAWLER_UA)
            .header("Accept", "image/*,*/*;q=0.8")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                val resolved = resp.request.url.toString()
                if (resp.isSuccessful &&
                    contentType.startsWith("image/", ignoreCase = true) &&
                    resolved.startsWith("https://", ignoreCase = true)
                ) {
                    resolved
                } else {
                    null
                }
            }
        }.onFailure { err ->
            logger.warn("instagram", "media_size_l", "Falha /media/?size=l ($shortcode): ${err.message}")
        }.getOrNull()
    }

    private companion object {
        const val CRAWLER_UA = "facebookexternalhit/1.1"
        const val CURL_UA = "curl/8.5.0"
    }
}
