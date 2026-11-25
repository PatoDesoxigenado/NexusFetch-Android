package io.github.patodesoxigenado.nexusfetch.data.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.remote.InstagramMetaClient
import io.github.patodesoxigenado.nexusfetch.data.remote.TwitterSyndicationClient
import io.github.patodesoxigenado.nexusfetch.data.remote.YouTubeFastMetaClient
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.PlaylistItem
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import io.github.patodesoxigenado.nexusfetch.domain.policy.TrimRangeValidator
import io.github.patodesoxigenado.nexusfetch.domain.policy.TwitterMediaFilter
import io.github.patodesoxigenado.nexusfetch.domain.policy.UrlValidator
import io.github.patodesoxigenado.nexusfetch.domain.policy.ValidationStatus
import io.github.patodesoxigenado.nexusfetch.domain.policy.YouTubeFastMetaParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class YtDlpDumpDto(
    val id: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val creator: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    @SerialName("_type") val type: String? = null,
    @SerialName("extractor_key") val extractorKey: String? = null,
    @SerialName("is_gif") val isGif: Boolean? = null,
    val formats: List<YtDlpFormatDto> = emptyList(),
    val entries: List<YtDlpDumpDto?> = emptyList(),
    val thumbnails: List<YtDlpThumbDto> = emptyList(),
)

@Serializable
private data class YtDlpFormatDto(
    @SerialName("format_id") val formatId: String? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Long? = null,
    @SerialName("filesize_approx") val filesizeApprox: Long? = null,
)

@Serializable
private data class YtDlpThumbDto(
    val url: String? = null,
)

private data class CachedMetadataEntry(
    val metadata: MediaMetadata,
    val cachedAtMs: Long,
)

/**
 * Wraps `junkfood02/youtubedl-android` (`yt-dlp` + `ffmpeg`) for metadata extraction,
 * single/playlist downloads, 4K `--download-sections` remote trimming, and in-app self-updates.
 */
@Singleton
class YoutubeDlEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val twitterClient: TwitterSyndicationClient,
    private val instagramClient: InstagramMetaClient,
    private val youtubeFastMetaClient: YouTubeFastMetaClient,
    private val settingsRepository: SettingsRepository,
    private val logger: NexusLogger,
) {
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    private val activeProcessIds = ConcurrentHashMap<String, String>()
    private val metadataCache = ConcurrentHashMap<String, CachedMetadataEntry>()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var isInitialized = false

    suspend fun ensureInitialized() {
        if (isInitialized) return
        initMutex.withLock {
            if (isInitialized) return
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    isInitialized = true
                    val installedVer = resolveInstalledVersion()
                    if (installedVer != null) {
                        settingsRepository.recordYtDlpVersion(installedVer)
                    }
                    val displayVer = installedVer ?: BUNDLED_VERSION_LABEL
                    logger.info("ytdlp", "init", "YoutubeDL + FFmpeg inicializados (versão=$displayVer)")
                } catch (t: Throwable) {
                    logger.error("ytdlp", "init", "Falha ao inicializar motor YoutubeDL", t)
                    throw NexusError.ExtractionError("Falha ao inicializar motor yt-dlp: ${t.message}", t)
                }
            }
        }
    }

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        resolveInstalledVersion() ?: BUNDLED_VERSION_LABEL
    }

    /**
     * Updates yt-dlp asynchronously if running on the stale bundled binary or past the refresh window.
     */
    suspend fun autoUpdateIfNeeded(force: Boolean = false): Boolean = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val installedVer = resolveInstalledVersion()
            val lastUpdateMs = settingsRepository.settings.first().lastYtDlpUpdateEpochMs
            val ageMs = System.currentTimeMillis() - lastUpdateMs
            if (!force && installedVer != null && ageMs in 0L until AUTO_UPDATE_INTERVAL_MS) {
                return@withContext false
            }

            runCatching {
                var status = runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                }.getOrNull()
                if (status == null || (force && status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)) {
                    status = runCatching {
                        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                    }.getOrNull() ?: status
                }
                val resolved = resolveInstalledVersion()
                if (resolved != null) {
                    settingsRepository.recordYtDlpVersion(resolved)
                }
                logger.info("ytdlp", "auto_update", "Auto-update yt-dlp: status=$status versao=${resolved ?: BUNDLED_VERSION_LABEL}")
                status == YoutubeDL.UpdateStatus.DONE || (installedVer == null && resolved != null)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                logger.warn("ytdlp", "auto_update", "Auto-update yt-dlp ignorado: ${err.message}")
            }.getOrDefault(false)
        }
    }

    suspend fun updateYtDlp(): String = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val stableStatus = runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            }
            val finalStatus = if (stableStatus.isSuccess && stableStatus.getOrNull() == YoutubeDL.UpdateStatus.DONE) {
                YoutubeDL.UpdateStatus.DONE
            } else {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            }
            val ver = resolveInstalledVersion() ?: BUNDLED_VERSION_LABEL
            settingsRepository.recordYtDlpVersion(ver)
            logger.info("ytdlp", "update", "Resultado update yt-dlp: $finalStatus ($ver)")
            when (finalStatus) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp atualizado para $ver!"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp já está na versão mais recente ($ver)."
                else -> "Versão ativa do yt-dlp: $ver"
            }
        }
    }

    fun destroyProcess(processId: String) {
        activeProcessIds[processId]?.let { activeAttempt ->
            runCatching { YoutubeDL.getInstance().destroyProcessById(activeAttempt) }
        }
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    suspend fun extractMetadata(rawUrl: String): MediaMetadata = withContext(Dispatchers.IO) {
        val validation = UrlValidator.validateUrl(rawUrl)
        if (validation.status == ValidationStatus.INVALID || validation.detectedPlatform == null) {
            throw NexusError.UnsupportedUrlError(validation.message)
        }
        val cleanUrl = validation.normalizedUrl
        val platform = validation.detectedPlatform

        val nowMs = System.currentTimeMillis()
        metadataCache[cleanUrl]?.takeIf { nowMs - it.cachedAtMs < METADATA_CACHE_TTL_MS }?.let {
            return@withContext it.metadata
        }

        val fastMeta = fetchPlatformFastPath(cleanUrl, platform)
        if (fastMeta != null) {
            storeCachedMetadata(cleanUrl, fastMeta)
            return@withContext fastMeta
        }

        ensureInitialized()
        if (platform == Platform.YOUTUBE && resolveInstalledVersion() == null) {
            autoUpdateIfNeeded(force = false)
        }

        val isPlaylist = UrlValidator.isPlaylistUrl(cleanUrl)
        val dto = try {
            executeMetadataDumpWithRecovery(cleanUrl, platform, isPlaylist)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (platform == Platform.INSTAGRAM) {
                instagramClient.fetchMetadata(cleanUrl)?.let { fallback ->
                    storeCachedMetadata(cleanUrl, fallback)
                    return@withContext fallback
                }
            }
            logger.error("ytdlp", "extract_meta", "Falha ao analisar $cleanUrl", t)
            throw NexusError.ExtractionError("Não foi possível ler dados do link: ${t.message?.take(140)}", t)
        }

        // Guard against quote-tweet media cascade when yt-dlp returns multiple entries for an X tweet
        val nonNullEntries = dto.entries.filterNotNull()
        val resolved = if (platform == Platform.TWITTER && nonNullEntries.isNotEmpty()) {
            buildSingleVideoMetadata(cleanUrl, platform, nonNullEntries.first())
        } else if (dto.type == "playlist" || nonNullEntries.isNotEmpty()) {
            buildPlaylistMetadata(cleanUrl, platform, dto, nonNullEntries)
        } else {
            buildSingleVideoMetadata(cleanUrl, platform, dto)
        }
        storeCachedMetadata(cleanUrl, resolved)
        resolved
    }

    private suspend fun fetchPlatformFastPath(cleanUrl: String, platform: Platform): MediaMetadata? =
        when (platform) {
            Platform.TWITTER -> twitterClient.fetchMetadata(cleanUrl)
            Platform.INSTAGRAM -> if (cleanUrl.contains("/p/", ignoreCase = true)) {
                instagramClient.fetchMetadata(cleanUrl)
            } else {
                null
            }
            Platform.YOUTUBE -> if (!UrlValidator.isPlaylistUrl(cleanUrl)) {
                youtubeFastMetaClient.fetchMetadata(cleanUrl)
            } else {
                null
            }
        }

    private fun storeCachedMetadata(cleanUrl: String, metadata: MediaMetadata) {
        if (metadataCache.size >= MAX_METADATA_CACHE_ENTRIES) {
            val oldestKey = metadataCache.entries.minByOrNull { it.value.cachedAtMs }?.key
            if (oldestKey != null) metadataCache.remove(oldestKey)
        }
        metadataCache[cleanUrl] = CachedMetadataEntry(metadata, System.currentTimeMillis())
    }

    suspend fun analyzeTrimUrl(rawUrl: String): TrimSourceInfo = withContext(Dispatchers.IO) {
        val meta = extractMetadata(rawUrl)
        TrimSourceInfo(
            sourceType = TrimSourceType.URL,
            source = meta.url,
            title = meta.title,
            durationSec = (meta.durationSec ?: 0).toDouble(),
            isVideo = meta.mediaType != MediaType.AUDIO,
            uploader = meta.uploader,
            platform = meta.platform,
            fileExt = ".mp4",
        )
    }

    suspend fun downloadSingleMedia(
        url: String,
        workDir: File,
        mode: String,
        formatId: String?,
        processId: String,
        onProgress: (fraction: Float, etaSec: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        executeWithRecovery(url, workDir, processId, onProgress) { extraArgs ->
            buildSingleDownloadRequest(url, workDir, mode, formatId, extraArgs)
        }
    }

    suspend fun downloadRemoteTrim(
        url: String,
        startSec: Double,
        endSec: Double,
        audioOnly: Boolean,
        maxHeight: Int?,
        workDir: File,
        processId: String,
        onProgress: (fraction: Float, etaSec: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val formatSelector = TrimRangeValidator.selectRemoteFormatString(audioOnly, maxHeight)
        val sectionArg = TrimRangeValidator.formatDownloadSection(startSec, endSec)
        executeWithRecovery(url, workDir, processId, onProgress) { extraArgs ->
            buildRemoteTrimRequest(url, formatSelector, sectionArg, audioOnly, workDir, extraArgs)
        }
    }

    private fun resolveInstalledVersion(): String? {
        val dynamicVer = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()?.takeIf { it.isNotBlank() }
        if (dynamicVer != null) return dynamicVer
        return runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun executeMetadataDumpWithRecovery(
        cleanUrl: String,
        platform: Platform,
        isPlaylist: Boolean,
    ): YtDlpDumpDto {
        val buildRequest: (String?) -> YoutubeDLRequest = { extractorArgs ->
            YoutubeDLRequest(cleanUrl).apply {
                addOption("--dump-single-json")
                addOption("--skip-download")
                addOption("--no-warnings")
                addOption("--socket-timeout", "20")
                if (isPlaylist) {
                    addOption("--flat-playlist")
                    addOption("--ignore-errors")
                } else {
                    addOption("--no-playlist")
                }
                if (!extractorArgs.isNullOrBlank()) {
                    addOption("--extractor-args", extractorArgs)
                }
            }
        }

        val firstAttempt = runCatching {
            val response = YoutubeDL.getInstance().execute(buildRequest(null), "meta_${System.nanoTime()}") { _, _, _ -> }
            json.decodeFromString<YtDlpDumpDto>(response.out)
        }
        val firstErr = firstAttempt.exceptionOrNull() ?: return firstAttempt.getOrThrow()
        rethrowIfCancelled(firstErr)

        if (platform == Platform.YOUTUBE && isRecoverableDownloadError(firstErr.message)) {
            logger.warn("ytdlp", "meta_retry", "Erro recuperável ao analisar YouTube; atualizando yt-dlp e tentando fallback")
            autoUpdateIfNeeded(force = true)
            val secondAttempt = runCatching {
                val response = YoutubeDL.getInstance().execute(buildRequest(null), "meta_${System.nanoTime()}") { _, _, _ -> }
                json.decodeFromString<YtDlpDumpDto>(response.out)
            }
            if (secondAttempt.isSuccess) return secondAttempt.getOrThrow()
            rethrowIfCancelled(secondAttempt.exceptionOrNull())

            val fallbackResp = YoutubeDL.getInstance().execute(
                buildRequest(YOUTUBE_FALLBACK_EXTRACTOR_ARGS),
                "meta_${System.nanoTime()}",
            ) { _, _, _ -> }
            return json.decodeFromString<YtDlpDumpDto>(fallbackResp.out)
        }
        throw firstErr
    }

    private suspend fun executeWithRecovery(
        url: String,
        workDir: File,
        processId: String,
        onProgress: (fraction: Float, etaSec: Long, line: String) -> Unit,
        requestFactory: (extraArgs: List<Pair<String, String?>>) -> YoutubeDLRequest,
    ): File {
        ensureInitialized()
        val platform = UrlValidator.detectPlatform(url)
        if (platform == Platform.YOUTUBE && resolveInstalledVersion() == null) {
            autoUpdateIfNeeded(force = false)
        }

        val attempt1 = executeAttempt(requestFactory(emptyList()), workDir, "${processId}_a1", processId, onProgress)
        val firstErr = attempt1.exceptionOrNull() ?: return attempt1.getOrThrow()
        rethrowIfCancelled(firstErr)

        var lastError: Throwable = firstErr
        var userErrorMsg = "Falha durante o download: ${firstErr.message?.take(140)}"

        if (isRecoverableDownloadError(firstErr.message)) {
            logger.warn("ytdlp", "retry_403", "Erro recuperável detectado; atualizando yt-dlp e tentando novamente")
            clearWorkDirFiles(workDir)
            val updated = autoUpdateIfNeeded(force = true)
            if (updated || platform == Platform.YOUTUBE) {
                val attempt2 = executeAttempt(requestFactory(emptyList()), workDir, "${processId}_a2", processId, onProgress)
                if (attempt2.isSuccess) return attempt2.getOrThrow()
                attempt2.exceptionOrNull()?.let { err ->
                    rethrowIfCancelled(err)
                    lastError = err
                }
            }

            if (platform == Platform.YOUTUBE) {
                clearWorkDirFiles(workDir)
                val extra = listOf("--extractor-args" to YOUTUBE_FALLBACK_EXTRACTOR_ARGS)
                val attempt3 = executeAttempt(requestFactory(extra), workDir, "${processId}_a3", processId, onProgress)
                if (attempt3.isSuccess) return attempt3.getOrThrow()
                attempt3.exceptionOrNull()?.let { err ->
                    rethrowIfCancelled(err)
                    lastError = err
                }
                userErrorMsg = "YouTube bloqueou temporariamente (HTTP 403). Atualize o yt-dlp em Configurações e tente novamente."
            }
        }

        throw NexusError.ExtractionError(userErrorMsg, lastError)
    }

    private fun rethrowIfCancelled(err: Throwable?) {
        if (err is CancellationException) throw err
    }

    private fun executeAttempt(
        request: YoutubeDLRequest,
        workDir: File,
        attemptId: String,
        logicalProcessId: String,
        onProgress: (fraction: Float, etaSec: Long, line: String) -> Unit,
    ): Result<File> {
        activeProcessIds[logicalProcessId] = attemptId
        return try {
            val execResult = runCatching {
                YoutubeDL.getInstance().execute(request, attemptId) { progress, etaInSeconds, line ->
                    val clamped = if (progress >= 0f) (progress / 100f).coerceIn(0f, 0.99f) else 0f
                    onProgress(clamped, etaInSeconds, line)
                }
            }
            if (execResult.isFailure) {
                Result.failure(execResult.exceptionOrNull() ?: IllegalStateException("Falha desconhecida"))
            } else {
                runCatching { resolveOutputFile(workDir) }
            }
        } finally {
            if (activeProcessIds[logicalProcessId] == attemptId) {
                activeProcessIds.remove(logicalProcessId)
            }
        }
    }

    private fun buildSingleDownloadRequest(
        url: String,
        workDir: File,
        mode: String,
        formatId: String?,
        extraArgs: List<Pair<String, String?>>,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("-o", "${workDir.absolutePath}/%(title).90s.%(ext)s")
        addOption("--no-warnings")
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--restrict-filenames")
        addOption("--retries", "3")
        addOption("--fragment-retries", "3")
        addOption("--socket-timeout", "20")
        if (TwitterMediaFilter.requiresSingleItemFilter(url)) {
            addOption("--playlist-items", "1")
        }
        if (mode == "audio") {
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "320K")
            addOption("--embed-metadata")
        } else {
            addOption("-f", formatId ?: "bestvideo+bestaudio/best")
            addOption("--merge-output-format", "mp4")
        }
        extraArgs.forEach { (flag, value) ->
            if (value == null) addOption(flag) else addOption(flag, value)
        }
    }

    private fun buildRemoteTrimRequest(
        url: String,
        formatSelector: String,
        sectionArg: String,
        audioOnly: Boolean,
        workDir: File,
        extraArgs: List<Pair<String, String?>>,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("-o", "${workDir.absolutePath}/clip_%(title).70s.%(ext)s")
        addOption("--no-warnings")
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--restrict-filenames")
        addOption("--retries", "3")
        addOption("--fragment-retries", "3")
        addOption("--socket-timeout", "20")
        addOption("-f", formatSelector)
        addOption("--download-sections", sectionArg)
        if (TwitterMediaFilter.requiresSingleItemFilter(url)) {
            addOption("--playlist-items", "1")
        }
        if (audioOnly) {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "320K")
        } else {
            addOption("--merge-output-format", "mp4")
        }
        extraArgs.forEach { (flag, value) ->
            if (value == null) addOption(flag) else addOption(flag, value)
        }
    }

    private fun isRecoverableDownloadError(message: String?): Boolean {
        val lower = message.orEmpty().lowercase()
        return lower.contains("403") ||
            lower.contains("forbidden") ||
            lower.contains("i/o operation on closed file") ||
            lower.contains("sign in to confirm") ||
            lower.contains("nsig") ||
            lower.contains("unable to extract")
    }

    private fun clearWorkDirFiles(workDir: File) {
        workDir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
    }

    private fun resolveOutputFile(workDir: File): File =
        workDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
            ?: throw NexusError.StorageError("Nenhum arquivo válido foi gerado após o download.")

    private fun buildPlaylistMetadata(
        url: String,
        platform: Platform,
        dto: YtDlpDumpDto,
        rawEntries: List<YtDlpDumpDto>,
    ): MediaMetadata {
        val playlistTitle = dto.title?.ifBlank { "Playlist" } ?: "Playlist do YouTube"
        val uploader = dto.uploader ?: dto.channel ?: dto.creator ?: platform.label
        val items = mutableListOf<PlaylistItem>()
        var totalDurSec = 0

        rawEntries.forEach { entry ->
            val vTitle = entry.title ?: return@forEach
            if (vTitle == "[Private video]" || vTitle == "[Deleted video]") return@forEach
            val vId = entry.id.orEmpty()
            val vUrl = entry.url?.takeIf { it.startsWith("http") }
                ?: if (vId.isNotBlank()) "https://www.youtube.com/watch?v=$vId" else ""
            if (vUrl.isBlank()) return@forEach
            val vDur = entry.duration?.toInt()
            if (vDur != null && vDur > 0) totalDurSec += vDur
            val thumb = entry.thumbnail
                ?: entry.thumbnails.lastOrNull()?.url
                ?: if (vId.isNotBlank()) "https://i.ytimg.com/vi/$vId/hqdefault.jpg" else null

            items.add(
                PlaylistItem(
                    index = items.size + 1,
                    videoId = vId,
                    title = vTitle,
                    durationSec = vDur,
                    durationStr = if (vDur != null) MediaSanitizer.formatDuration(vDur) else "--:--",
                    url = vUrl,
                    thumbnailUrl = thumb,
                    uploader = entry.uploader ?: uploader,
                    isSelected = true,
                    itemType = MediaType.VIDEO,
                ),
            )
        }

        val qualities = listOf(
            VideoQualityOption("Máxima Resolução (Melhor Qualidade)", null, "bestvideo+bestaudio/best"),
            VideoQualityOption("2160p (4K UHD)", 2160, "bestvideo[height<=2160]+bestaudio/best[height<=2160]"),
            VideoQualityOption("1440p (2K QHD)", 1440, "bestvideo[height<=1440]+bestaudio/best[height<=1440]"),
            VideoQualityOption("1080p (FHD)", 1080, "bestvideo[height<=1080]+bestaudio/best[height<=1080]"),
            VideoQualityOption("720p (HD)", 720, "bestvideo[height<=720]+bestaudio/best[height<=720]"),
            VideoQualityOption("480p", 480, "bestvideo[height<=480]+bestaudio/best[height<=480]"),
            VideoQualityOption("360p", 360, "bestvideo[height<=360]+bestaudio/best[height<=360]"),
        )

        return MediaMetadata(
            url = url,
            platform = platform,
            title = playlistTitle,
            uploader = uploader,
            durationSec = totalDurSec,
            durationStr = "${items.size} vídeos",
            thumbnailUrl = dto.thumbnail ?: items.firstOrNull()?.thumbnailUrl,
            qualities = qualities,
            mediaType = MediaType.PLAYLIST,
            isPlaylist = true,
            playlistItems = items,
            playlistCount = items.size,
        )
    }

    private fun buildSingleVideoMetadata(
        url: String,
        platform: Platform,
        dto: YtDlpDumpDto,
    ): MediaMetadata {
        val title = dto.title?.ifBlank { "Sem título" } ?: "Sem título"
        val uploader = dto.uploader ?: dto.channel ?: dto.creator ?: "Autor Desconhecido"
        val durationSec = dto.duration?.toInt()
        val durationStr = durationSec?.takeIf { it > 0 }?.let(MediaSanitizer::formatDuration) ?: "Ao vivo / N/A"

        val (bestAudioBytes, hasAudioFormat, sizeByHeight) = summarizeFormats(dto.formats, durationSec)
        val isGif = (dto.isGif == true) ||
            (dto.extractorKey == "Twitter" && !hasAudioFormat && durationSec != null && durationSec <= 15)
        val qualities = YouTubeFastMetaParser.buildVideoQualities(sizeByHeight, isGif)

        return MediaMetadata(
            url = url,
            platform = platform,
            title = title,
            uploader = uploader,
            durationSec = durationSec,
            durationStr = durationStr,
            thumbnailUrl = dto.thumbnail,
            qualities = qualities,
            mediaType = if (isGif) MediaType.GIF else MediaType.VIDEO,
            videoStreamUrl = dto.url,
            estimatedAudioSizeBytes = bestAudioBytes.takeIf { it > 0L },
        )
    }

    private fun summarizeFormats(
        formats: List<YtDlpFormatDto>,
        durationSec: Int?,
    ): Triple<Long, Boolean, Map<Int, Long>> {
        var bestAudioBytes = 0L
        var hasAudioFormat = false
        for (fmt in formats) {
            if (fmt.vcodec == "none" && fmt.acodec != null && fmt.acodec != "none") {
                hasAudioFormat = true
                val size = fmt.filesize ?: fmt.filesizeApprox ?: 0L
                if (size > bestAudioBytes) bestAudioBytes = size
            }
        }
        if (bestAudioBytes == 0L && durationSec != null && durationSec > 0) {
            bestAudioBytes = durationSec * 40L * 1024L
        }

        val sizeByHeight = mutableMapOf<Int, Long>()
        for (fmt in formats) {
            val h = fmt.height
            val vcodec = fmt.vcodec ?: "none"
            if (h != null && vcodec != "none" && h >= 144) {
                val vsize = fmt.filesize ?: fmt.filesizeApprox ?: 0L
                val totalSize = if (vsize > 0L) vsize + bestAudioBytes else 0L
                val current = sizeByHeight[h] ?: -1L
                if (totalSize > current) sizeByHeight[h] = totalSize
            }
        }
        return Triple(bestAudioBytes, hasAudioFormat, sizeByHeight)
    }

    private companion object {
        const val BUNDLED_VERSION_LABEL = "2025.11.12 (empacotada)"
        const val AUTO_UPDATE_INTERVAL_MS = 86_400_000L
        const val METADATA_CACHE_TTL_MS = 300_000L
        const val MAX_METADATA_CACHE_ENTRIES = 16
        const val YOUTUBE_FALLBACK_EXTRACTOR_ARGS = "youtube:player_client=android_vr,android,web"
    }
}
