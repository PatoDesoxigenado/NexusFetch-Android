package io.github.patodesoxigenado.nexusfetch.data.remote

import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.policy.UrlValidator
import io.github.patodesoxigenado.nexusfetch.domain.policy.YouTubeFastMetaParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast-path YouTube metadata fetcher using compressed Watch HTML (`ytInitialPlayerResponse`),
 * bypassing Python/QuickJS startup latency on Share Sheet and link analysis (~1-2s vs ~10s).
 */
@Singleton
class YouTubeFastMetaClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val logger: NexusLogger,
) {
    private val fastClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(FAST_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    suspend fun fetchMetadata(url: String): MediaMetadata? = withContext(Dispatchers.IO) {
        if (UrlValidator.isPlaylistUrl(url)) return@withContext null
        val videoId = YouTubeFastMetaParser.extractVideoId(url) ?: return@withContext null

        val startedNs = System.nanoTime()
        val watchUrl = "https://www.youtube.com/watch?v=$videoId&hl=pt&bpctr=9999999999"
        val request = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .header("Cookie", "CONSENT=YES+cb")
            .build()

        val result = try {
            fastClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    logger.warn("youtube", "fast_meta", "HTTP ${resp.code} para watch $videoId")
                    return@use null
                }
                val html = resp.body?.string().orEmpty()
                val playerJson = YouTubeFastMetaParser.extractPlayerResponseJson(html) ?: return@use null
                YouTubeFastMetaParser.parsePlayerResponse(url, videoId, playerJson)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logger.warn("youtube", "fast_meta", "Fast meta fallback para $videoId: ${t.message}")
            null
        }

        if (result != null) {
            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            logger.info("youtube", "fast_meta", "Metadados rápidos obtidos ($videoId)", durationMs = elapsedMs)
        }
        result
    }

    private companion object {
        const val FAST_TIMEOUT_SEC = 6L
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
