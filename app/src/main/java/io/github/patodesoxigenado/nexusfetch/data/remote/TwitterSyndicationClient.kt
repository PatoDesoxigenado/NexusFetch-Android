package io.github.patodesoxigenado.nexusfetch.data.remote

import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.policy.TwitterMediaFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast-path Twitter/X metadata fetcher via Syndication API with strict HTTPS/TLS.
 */
@Singleton
class TwitterSyndicationClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: NexusLogger,
) {
    suspend fun fetchMetadata(url: String): MediaMetadata? = withContext(Dispatchers.IO) {
        val tweetId = TwitterMediaFilter.extractTweetId(url) ?: return@withContext null
        fetchFromSyndication(url, tweetId)?.let { return@withContext it }
        fetchFromFxTwitter(url, tweetId)
    }

    private fun fetchFromSyndication(url: String, tweetId: String): MediaMetadata? {
        val apiUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&token=x"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("twitter", "syndication", "HTTP ${response.code} para tweet $tweetId")
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                TwitterMediaFilter.parseSyndicationPayload(url, body)
            }
        }.onFailure { err ->
            logger.warn("twitter", "syndication", "Syndication falhou: ${err.message}")
        }.getOrNull()
    }

    private fun fetchFromFxTwitter(url: String, tweetId: String): MediaMetadata? {
        val apiUrl = "https://api.fxtwitter.com/status/$tweetId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "NexusFetch-Android/1.0")
            .header("Accept", "application/json")
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("twitter", "fxtwitter", "HTTP ${response.code} para tweet $tweetId")
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                TwitterMediaFilter.parseFxTwitterPayload(url, body)
            }
        }.onFailure { err ->
            logger.warn("twitter", "fxtwitter", "FxTwitter falhou: ${err.message}")
        }.getOrNull()
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
