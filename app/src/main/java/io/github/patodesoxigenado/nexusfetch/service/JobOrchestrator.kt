package io.github.patodesoxigenado.nexusfetch.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.data.engine.AudioCoverEmbedder
import io.github.patodesoxigenado.nexusfetch.data.engine.FfmpegProcessExecutor
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaCompressorEngine
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaConverterEngine
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaTrimmerEngine
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryDao
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryEntity
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.WorkDirCleaner
import io.github.patodesoxigenado.nexusfetch.data.remote.DirectMediaDownloader
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.di.ApplicationScope
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.ActiveJobState
import io.github.patodesoxigenado.nexusfetch.domain.model.HistoryEntry
import io.github.patodesoxigenado.nexusfetch.domain.model.JobStatus
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import io.github.patodesoxigenado.nexusfetch.domain.policy.AggregateProgressCalculator
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single sequential job queue shared by all UI ViewModels, ShareSheetActivity, and MediaJobForegroundService.
 * Tracks active native process IDs so user cancellation immediately stops yt-dlp/FFmpeg and cleans temp files.
 */
@Singleton
class JobOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val youtubeDlEngine: YoutubeDlEngine,
    private val directDownloader: DirectMediaDownloader,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val audioCoverEmbedder: AudioCoverEmbedder,
    private val compressorEngine: MediaCompressorEngine,
    private val converterEngine: MediaConverterEngine,
    private val trimmerEngine: MediaTrimmerEngine,
    private val publisher: MediaStorePublisher,
    private val workDirCleaner: WorkDirCleaner,
    private val historyDao: HistoryDao,
    private val notificationHelper: NotificationHelper,
    private val logger: NexusLogger,
) {
    private val _activeState = MutableStateFlow(ActiveJobState())
    val activeState: StateFlow<ActiveJobState> = _activeState.asStateFlow()

    private val queue = Channel<MediaJobRequest>(capacity = Channel.UNLIMITED)
    private val pendingJobs = AtomicInteger(0)

    @Volatile
    private var currentExecutionJob: Job? = null

    @Volatile
    private var currentProcessId: String? = null

    @Volatile
    private var currentWorkDirJobId: String? = null

    @Volatile
    var onQueueDrained: (() -> Unit)? = null

    init {
        appScope.launch {
            for (request in queue) {
                try {
                    executeJob(request)
                } finally {
                    if (pendingJobs.decrementAndGet() <= 0) {
                        pendingJobs.set(0)
                        onQueueDrained?.invoke()
                    }
                }
            }
        }
    }

    fun submit(request: MediaJobRequest) {
        pendingJobs.incrementAndGet()
        val serviceIntent = Intent(context, MediaJobForegroundService::class.java).apply {
            action = MediaJobForegroundService.ACTION_START
            putExtra(MediaJobForegroundService.EXTRA_MODULE_ID, request.module.id)
            putExtra(MediaJobForegroundService.EXTRA_TITLE, request.displayTitle)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        if (_activeState.value.status == JobStatus.IDLE) {
            _activeState.value = ActiveJobState(
                jobId = request.jobId,
                module = request.module,
                status = JobStatus.QUEUED,
                title = request.displayTitle,
                statusMessage = "Na fila...",
            )
        }
        queue.trySend(request)
    }

    fun cancelActiveJob() {
        val procId = currentProcessId
        if (!procId.isNullOrBlank()) {
            youtubeDlEngine.destroyProcess(procId)
        }
        ffmpegExecutor.destroyAllActiveProcesses()
        currentExecutionJob?.cancel()
        currentWorkDirJobId?.let { workDirCleaner.deleteJobWorkDir(it) }
        notificationHelper.cancelProgressNotification()
        _activeState.update {
            it.copy(
                status = JobStatus.CANCELLED,
                statusMessage = "Operação cancelada pelo usuário.",
            )
        }
        onQueueDrained?.invoke()
    }

    fun clearCompletedBanner() {
        val st = _activeState.value.status
        if (st == JobStatus.COMPLETED || st == JobStatus.FAILED || st == JobStatus.CANCELLED) {
            _activeState.value = ActiveJobState()
        }
    }

    private suspend fun executeJob(request: MediaJobRequest) {
        val workDir = workDirCleaner.createJobWorkDir(request.jobId)
        val processId = "proc_${request.jobId}_${UUID.randomUUID().toString().take(6)}"
        currentWorkDirJobId = request.jobId
        currentProcessId = processId

        _activeState.value = ActiveJobState(
            jobId = request.jobId,
            module = request.module,
            status = JobStatus.RUNNING,
            title = request.displayTitle,
            statusMessage = "Iniciando...",
            progressPercent = 1f,
        )
        notificationHelper.updateProgressNotification(request.displayTitle, "Iniciando...", 1f)

        val job = appScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                val outputs: List<PublishedMedia> = when (request) {
                    is MediaJobRequest.SingleDownload -> runSingleDownload(request, workDir, processId)
                    is MediaJobRequest.CollectionDownload -> runCollectionDownload(request, workDir, processId)
                    is MediaJobRequest.MultiDownloadBatch -> runMultiDownload(request, workDir, processId)
                    is MediaJobRequest.CompressBatch -> compressorEngine.compressBatch(
                        files = request.files,
                        preset = request.preset,
                        workDir = workDir,
                    ) { pct, msg -> updateProgress(request.displayTitle, pct, "", "", msg) }
                    is MediaJobRequest.ConvertBatch -> converterEngine.convertBatch(
                        files = request.files,
                        spec = request.spec,
                        workDir = workDir,
                    ) { pct, spd, msg -> updateProgress(request.displayTitle, pct, spd, "", msg) }
                    is MediaJobRequest.TrimMedia -> listOf(
                        trimmerEngine.trim(
                            request = request.request,
                            workDir = workDir,
                            processId = processId,
                        ) { pct, msg -> updateProgress(request.displayTitle, pct, "", "", msg) },
                    )
                }

                persistHistory(outputs, sourceUrlFrom(request))
                val durationMs = System.currentTimeMillis() - t0
                logger.info(
                    module = request.module.id,
                    stage = "job_complete",
                    message = "Concluído: ${outputs.size} arquivo(s)",
                    jobId = request.jobId,
                    durationMs = durationMs,
                )

                val summary = if (outputs.size > 1) {
                    "${outputs.size} arquivos salvos com sucesso!"
                } else if (outputs.size == 1) {
                    "Salvo: ${outputs.first().fileName}"
                } else {
                    "Concluído!"
                }

                _activeState.value = ActiveJobState(
                    jobId = request.jobId,
                    module = request.module,
                    status = JobStatus.COMPLETED,
                    title = request.displayTitle,
                    statusMessage = summary,
                    progressPercent = 100f,
                    completedOutputs = outputs,
                )
                notificationHelper.showCompletionNotification(request.displayTitle, summary, outputs)
            } catch (ce: CancellationException) {
                logger.warn(request.module.id, "job_cancel", "Job cancelado", request.jobId)
                _activeState.update {
                    it.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelado pelo usuário.")
                }
            } catch (t: Throwable) {
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Erro desconhecido.")
                logger.error(request.module.id, "job_error", msg, t, request.jobId)
                _activeState.update {
                    it.copy(
                        status = JobStatus.FAILED,
                        statusMessage = msg,
                        errorMessage = msg,
                    )
                }
                notificationHelper.showErrorNotification("Falha: ${request.displayTitle}", msg)
            } finally {
                currentProcessId = null
                currentExecutionJob = null
                currentWorkDirJobId = null
                workDirCleaner.deleteJobWorkDir(request.jobId)
                if (pendingJobs.get() <= 1) {
                    notificationHelper.cancelProgressNotification()
                }
            }
        }
        currentExecutionJob = job
        job.join()
    }

    private suspend fun runSingleDownload(
        req: MediaJobRequest.SingleDownload,
        workDir: File,
        processId: String,
    ): List<PublishedMedia> {
        val meta = req.metadata
        if (req.mode == "image" || meta.mediaType == MediaType.IMAGE) {
            return downloadSingleImages(req, meta, workDir)
        }

        val directUrl = resolveDirectMediaUrl(meta, req.formatId)
        val isGifOutput = req.formatId == "gif_convert" || (req.mode == "gif" && req.formatId != "gif_mp4")
        if (isGifOutput) {
            return downloadAsGif(req, meta, directUrl, workDir, processId)
        }

        if (directUrl != null) {
            val directAttempt = runCatching { downloadDirectSingle(req, meta, directUrl, workDir) }
            val err = directAttempt.exceptionOrNull()
            if (err == null) {
                return directAttempt.getOrThrow()
            }
            if (err is CancellationException) throw err
            logger.warn(req.module.id, "direct_fallback", "Download direto falhou (${err.message}); tentando yt-dlp", req.jobId)
        }

        val outFile = youtubeDlEngine.downloadSingleMedia(
            url = meta.url,
            workDir = workDir,
            mode = req.mode,
            formatId = req.formatId,
            processId = processId,
        ) { frac, etaSec, _ ->
            val etaStr = if (etaSec > 0) "ETA: ${MediaSanitizer.formatDuration(etaSec.toInt())}" else ""
            updateProgress(req.displayTitle, frac * 96f, "", etaStr, "Baixando mídia...")
        }

        if (req.mode == "audio" && meta.platform == Platform.YOUTUBE) {
            updateProgress(req.displayTitle, 97f, "", "", "Embutindo capa do álbum no MP3...")
            audioCoverEmbedder.tryEmbedYouTubeCover(
                mp3File = outFile,
                sourceUrl = meta.url,
                fallbackThumbnailUrl = meta.thumbnailUrl,
                workDir = workDir,
            )
        }

        val mediaType = if (req.mode == "audio") MediaType.AUDIO else MediaType.VIDEO
        val published = publisher.publishFile(
            sourceFile = outFile,
            desiredBaseName = MediaSanitizer.sanitizeFilename(meta.title),
            module = req.module,
            mediaType = mediaType,
            durationSec = meta.durationSec,
        )
        return listOf(published)
    }

    private suspend fun downloadSingleImages(
        req: MediaJobRequest.SingleDownload,
        meta: MediaMetadata,
        workDir: File,
    ): List<PublishedMedia> {
        val urls = meta.imageUrls.ifEmpty { listOfNotNull(meta.thumbnailUrl).ifEmpty { listOf(meta.url) } }
        val outputs = mutableListOf<PublishedMedia>()
        urls.forEachIndexed { idx, imgUrl ->
            val ext = MediaSanitizer.inferExtension(imgUrl, MediaType.IMAGE)
            val baseName = MediaSanitizer.sanitizeFilename(meta.title).let {
                if (urls.size > 1) "${it}_${idx + 1}" else it
            }
            val dest = File(workDir, "$baseName$ext")
            directDownloader.downloadToFile(imgUrl, dest)
            outputs.add(
                publisher.publishFile(dest, baseName, req.module, MediaType.IMAGE),
            )
            val pct = AggregateProgressCalculator.calculateOverallPercent(idx + 1, 0f, urls.size)
            updateProgress(req.displayTitle, pct, "${idx + 1}/${urls.size}", "", "Baixando imagem...")
        }
        return outputs
    }

    private suspend fun downloadAsGif(
        req: MediaJobRequest.SingleDownload,
        meta: MediaMetadata,
        directUrl: String?,
        workDir: File,
        processId: String,
    ): List<PublishedMedia> {
        updateProgress(req.displayTitle, 10f, "", "", "Baixando mídia para GIF...")
        val directMp4 = directUrl?.let { url ->
            runCatching {
                val target = File(workDir, "raw_gif.mp4")
                downloadDirectWithProgress(url, target, req.displayTitle, 10f, 65f, "Baixando base do GIF...")
            }.onFailure { err ->
                if (err is CancellationException) throw err
                logger.warn(req.module.id, "gif_direct_fallback", "Fallback yt-dlp para GIF: ${err.message}", req.jobId)
            }.getOrNull()
        }

        val rawMp4 = directMp4 ?: youtubeDlEngine.downloadSingleMedia(
            url = meta.url,
            workDir = workDir,
            mode = "video",
            formatId = "best",
            processId = processId,
        ) { frac, _, _ ->
            updateProgress(req.displayTitle, 15f + frac * 55f, "", "", "Baixando base do GIF...")
        }
        val safeBase = MediaSanitizer.sanitizeFilename(meta.title)
        val gifFile = File(workDir, "$safeBase.gif")
        updateProgress(req.displayTitle, 75f, "", "", "Convertendo paleta GIF...")
        trimmerEngine.convertMp4ToGif(rawMp4, gifFile)
        val published = publisher.publishFile(
            sourceFile = gifFile,
            desiredBaseName = safeBase,
            module = req.module,
            mediaType = MediaType.GIF,
        )
        return listOf(published)
    }

    private suspend fun downloadDirectSingle(
        req: MediaJobRequest.SingleDownload,
        meta: MediaMetadata,
        directUrl: String,
        workDir: File,
    ): List<PublishedMedia> {
        val safeBase = MediaSanitizer.sanitizeFilename(meta.title)
        val rawMp4 = File(workDir, "${safeBase}_src.mp4")
        val isAudio = req.mode == "audio"
        val endPct = if (isAudio) 65f else 95f

        downloadDirectWithProgress(directUrl, rawMp4, req.displayTitle, 2f, endPct, "Baixando mídia...")

        val finalFile = if (isAudio) {
            extractMp3Locally(rawMp4, File(workDir, "$safeBase.mp3"), meta.durationSec, req.displayTitle)
        } else {
            rawMp4
        }

        val mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO
        val published = publisher.publishFile(
            sourceFile = finalFile,
            desiredBaseName = safeBase,
            module = req.module,
            mediaType = mediaType,
            durationSec = meta.durationSec,
        )
        return listOf(published)
    }

    private suspend fun extractMp3Locally(
        sourceMp4: File,
        destMp3: File,
        durationSec: Int?,
        displayTitle: String,
    ): File {
        if (!ffmpegExecutor.isAvailable()) {
            youtubeDlEngine.ensureInitialized()
        }
        updateProgress(displayTitle, 66f, "", "", "Convertendo para MP3 320 kbps...")
        val args = listOf(
            "-y",
            "-i", sourceMp4.absolutePath,
            "-vn",
            "-c:a", "libmp3lame",
            "-b:a", "320k",
            "-progress", "pipe:1",
            destMp3.absolutePath,
        )
        ffmpegExecutor.runFfmpeg(args, durationSec = (durationSec ?: 0).toDouble()) { frac, spd ->
            updateProgress(displayTitle, 66f + frac * 30f, spd, "", "Extraindo áudio MP3 320k...")
        }
        sourceMp4.delete()
        return destMp3
    }

    private suspend fun downloadDirectWithProgress(
        directUrl: String,
        destFile: File,
        displayTitle: String,
        startPct: Float,
        endPct: Float,
        statusMsg: String,
    ): File {
        val startedMs = System.currentTimeMillis()
        var lastUiUpdateMs = 0L
        return directDownloader.downloadToFile(directUrl, destFile) { downloaded, total ->
            val now = System.currentTimeMillis()
            if (now - lastUiUpdateMs >= DIRECT_PROGRESS_INTERVAL_MS || (total > 0L && downloaded >= total)) {
                lastUiUpdateMs = now
                val frac = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
                val pct = startPct + frac * (endPct - startPct)
                val elapsedSec = ((now - startedMs).coerceAtLeast(100L)) / 1000.0
                val bytesPerSec = (downloaded / elapsedSec).toLong().coerceAtLeast(0L)
                val speedText = if (bytesPerSec > 0L) "${MediaSanitizer.formatBytes(bytesPerSec)}/s" else ""
                updateProgress(displayTitle, pct, speedText, "", statusMsg)
            }
        }
    }

    private fun resolveDirectMediaUrl(meta: MediaMetadata, formatId: String?): String? {
        if (meta.platform != Platform.TWITTER && meta.platform != Platform.INSTAGRAM) {
            return null
        }
        val candidate = meta.qualities.firstOrNull { it.formatId == formatId }?.directUrl
            ?: meta.qualities.firstOrNull { !it.directUrl.isNullOrBlank() }?.directUrl
            ?: meta.videoStreamUrl
            ?: return null
        val lower = candidate.lowercase()
        val isDirectHttp = lower.startsWith("https://")
        val isPageUrl = lower.contains("x.com/") || lower.contains("twitter.com/") || lower.contains("instagram.com/p/")
        val isHlsManifest = lower.contains(".m3u8")
        return if (isDirectHttp && !isPageUrl && !isHlsManifest) candidate else null
    }

    private suspend fun runCollectionDownload(
        req: MediaJobRequest.CollectionDownload,
        workDir: File,
        processId: String,
    ): List<PublishedMedia> {
        val items = req.selectedItems
        if (items.isEmpty()) throw NexusError.ValidationError("Nenhum item selecionado.")
        val outputs = mutableListOf<PublishedMedia>()
        val folderName = MediaSanitizer.sanitizeFilename(req.metadata.title)

        items.forEachIndexed { idx, item ->
            val itemDir = File(workDir, "item_$idx").apply { mkdirs() }
            val paddedIndex = String.format(Locale.US, "%02d", item.index)
            if (req.metadata.mediaType == MediaType.CAROUSEL) {
                val ext = MediaSanitizer.inferExtension(item.url, item.itemType)
                val dest = File(itemDir, "${folderName}_$paddedIndex$ext")
                directDownloader.downloadToFile(item.url, dest)
                outputs.add(
                    publisher.publishFile(
                        sourceFile = dest,
                        desiredBaseName = "${folderName}_$paddedIndex",
                        module = req.module,
                        mediaType = item.itemType,
                        subFolder = folderName,
                    ),
                )
                val pct = AggregateProgressCalculator.calculateOverallPercent(idx + 1, 0f, items.size)
                updateProgress(req.displayTitle, pct, "${idx + 1}/${items.size}", "", "Baixando ${item.title}...")
            } else {
                val outFile = youtubeDlEngine.downloadSingleMedia(
                    url = item.url,
                    workDir = itemDir,
                    mode = req.mode,
                    formatId = req.formatId,
                    processId = "${processId}_$idx",
                ) { frac, etaSec, _ ->
                    val pct = AggregateProgressCalculator.calculateOverallPercent(idx, frac, items.size)
                    val etaStr = if (etaSec > 0) "ETA: ${MediaSanitizer.formatDuration(etaSec.toInt())}" else ""
                    val msg = "[${idx + 1}/${items.size}] ${item.title.take(28)}"
                    updateProgress(req.displayTitle, pct, "${idx + 1}/${items.size}", etaStr, msg)
                }
                if (req.mode == "audio" && req.metadata.platform == Platform.YOUTUBE) {
                    val coverPct = AggregateProgressCalculator.calculateOverallPercent(idx, 0.97f, items.size)
                    updateProgress(
                        req.displayTitle,
                        coverPct,
                        "${idx + 1}/${items.size}",
                        "",
                        "[${idx + 1}/${items.size}] Embutindo capa no MP3...",
                    )
                    audioCoverEmbedder.tryEmbedYouTubeCover(
                        mp3File = outFile,
                        sourceUrl = item.url,
                        fallbackThumbnailUrl = item.thumbnailUrl ?: req.metadata.thumbnailUrl,
                        workDir = itemDir,
                    )
                }
                val base = "$paddedIndex - ${MediaSanitizer.sanitizeFilename(item.title)}"
                outputs.add(
                    publisher.publishFile(
                        sourceFile = outFile,
                        desiredBaseName = base,
                        module = req.module,
                        mediaType = if (req.mode == "audio") MediaType.AUDIO else MediaType.VIDEO,
                        durationSec = item.durationSec,
                        subFolder = folderName,
                    ),
                )
            }
            itemDir.deleteRecursively()
        }
        return outputs
    }

    private suspend fun runMultiDownload(
        req: MediaJobRequest.MultiDownloadBatch,
        workDir: File,
        processId: String,
    ): List<PublishedMedia> {
        val items = req.items.filter { it.metadata != null && it.selectedFormat != null }
        if (items.isEmpty()) throw NexusError.ValidationError("Analise os links válidos antes de iniciar o download.")
        val outputs = mutableListOf<PublishedMedia>()

        items.forEachIndexed { idx, item ->
            val meta = item.metadata ?: return@forEachIndexed
            val format = item.selectedFormat ?: return@forEachIndexed
            val subWork = File(workDir, "multi_$idx").apply { mkdirs() }

            if (meta.mediaType == MediaType.CAROUSEL && meta.playlistItems.isNotEmpty()) {
                val colReq = MediaJobRequest.CollectionDownload(
                    jobId = "${req.jobId}_$idx",
                    displayTitle = meta.title,
                    metadata = meta,
                    selectedItems = meta.playlistItems,
                    mode = format.mode,
                    formatId = format.formatId,
                )
                outputs.addAll(runCollectionDownload(colReq, subWork, "${processId}_$idx"))
            } else {
                val singleReq = MediaJobRequest.SingleDownload(
                    jobId = "${req.jobId}_$idx",
                    displayTitle = meta.title,
                    metadata = meta,
                    mode = format.mode,
                    formatId = format.formatId,
                    targetImageExt = format.targetImageExt,
                )
                outputs.addAll(runSingleDownload(singleReq, subWork, "${processId}_$idx"))
            }
            val overall = AggregateProgressCalculator.calculateOverallPercent(idx + 1, 0f, items.size)
            val statusMsg = "Concluído [${idx + 1}/${items.size}]: ${meta.title.take(24)}"
            updateProgress(req.displayTitle, overall, "${idx + 1}/${items.size}", "", statusMsg)
            subWork.deleteRecursively()
        }
        return outputs
    }

    private fun updateProgress(
        title: String,
        percent: Float,
        speed: String,
        eta: String,
        message: String,
    ) {
        _activeState.update {
            it.copy(
                progressPercent = percent.coerceIn(0f, 100f),
                speedText = speed,
                etaText = eta,
                statusMessage = message,
            )
        }
        notificationHelper.updateProgressNotification(title, message, percent)
    }

    private suspend fun persistHistory(outputs: List<PublishedMedia>, sourceUrl: String?) {
        val now = System.currentTimeMillis()
        for (item in outputs) {
            historyDao.insert(
                HistoryEntity.fromDomain(
                    HistoryEntry(
                        title = item.title,
                        module = item.module,
                        mediaType = item.mediaType,
                        mimeType = item.mimeType,
                        mediaStoreUri = item.uriString,
                        fileName = item.fileName,
                        sizeBytes = item.sizeBytes,
                        durationSec = item.durationSec,
                        sourceUrl = sourceUrl,
                        createdAtEpochMs = now,
                    ),
                ),
            )
        }
    }

    private fun sourceUrlFrom(req: MediaJobRequest): String? =
        when (req) {
            is MediaJobRequest.SingleDownload -> req.metadata.url
            is MediaJobRequest.CollectionDownload -> req.metadata.url
            is MediaJobRequest.TrimMedia -> req.request.sourceInfo.source
            else -> null
        }

    private companion object {
        const val DIRECT_PROGRESS_INTERVAL_MS = 150L
    }
}
