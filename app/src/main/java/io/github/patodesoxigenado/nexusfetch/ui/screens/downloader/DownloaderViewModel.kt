package io.github.patodesoxigenado.nexusfetch.ui.screens.downloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryDao
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryEntity
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.WorkDirCleaner
import io.github.patodesoxigenado.nexusfetch.data.remote.DirectMediaDownloader
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.HistoryEntry
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import io.github.patodesoxigenado.nexusfetch.domain.policy.UrlValidator
import io.github.patodesoxigenado.nexusfetch.domain.policy.ValidationStatus
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DownloaderViewModel @Inject constructor(
    private val youtubeDlEngine: YoutubeDlEngine,
    private val jobOrchestrator: JobOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val networkGuard: NetworkGuard,
    private val directDownloader: DirectMediaDownloader,
    private val publisher: MediaStorePublisher,
    private val workDirCleaner: WorkDirCleaner,
    private val historyDao: HistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun onUrlChanged(newUrl: String) {
        val detected = UrlValidator.detectPlatform(newUrl)
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                detectedPlatform = detected,
                thumbnailDownloadMessage = null,
            )
        }
    }

    fun analyzeWithUrl(url: String) {
        onUrlChanged(url)
        analyzeCurrentUrl()
    }

    fun clearSource() {
        jobOrchestrator.clearCompletedBanner()
        _uiState.value = DownloaderUiState()
    }

    fun analyzeCurrentUrl() {
        val raw = _uiState.value.urlInput.trim()
        val validation = UrlValidator.validateUrl(raw)
        if (validation.status == ValidationStatus.INVALID) {
            _uiState.update {
                it.copy(
                    sourceState = DownloaderSourceState.Error(validation.message),
                    selectedQuality = null,
                    selectedItemKeys = emptySet(),
                    thumbnailDownloadMessage = null,
                )
            }
            return
        }

        // Full state reset before starting new analysis
        jobOrchestrator.clearCompletedBanner()
        _uiState.update {
            it.copy(
                urlInput = validation.normalizedUrl,
                detectedPlatform = validation.detectedPlatform,
                sourceState = DownloaderSourceState.Analyzing(validation.normalizedUrl),
                selectedMode = "video",
                selectedQuality = null,
                selectedItemKeys = emptySet(),
                thumbnailDownloadMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val meta = youtubeDlEngine.extractMetadata(validation.normalizedUrl)
                val defaultMode = when (meta.mediaType) {
                    MediaType.IMAGE -> "image"
                    MediaType.GIF -> "gif"
                    MediaType.AUDIO -> "audio"
                    else -> "video"
                }
                val preferredHeight = settings.value.defaultVideoQualityHeight
                val defaultQuality = if (preferredHeight != null) {
                    meta.qualities.firstOrNull { it.height == preferredHeight } ?: meta.qualities.firstOrNull()
                } else {
                    meta.qualities.firstOrNull()
                }
                val keys = meta.playlistItems.map { "${it.index}_${it.videoId}" }.toSet()

                _uiState.update {
                    it.copy(
                        detectedPlatform = meta.platform,
                        sourceState = DownloaderSourceState.Ready(meta),
                        selectedMode = defaultMode,
                        selectedQuality = defaultQuality,
                        selectedItemKeys = keys,
                    )
                }
            } catch (t: Throwable) {
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Erro ao analisar o link.")
                _uiState.update {
                    it.copy(sourceState = DownloaderSourceState.Error(msg))
                }
            }
        }
    }

    fun selectMode(mode: String) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun selectQuality(option: VideoQualityOption) {
        val mode = if (option.formatId == "gif_convert") "gif" else _uiState.value.selectedMode
        _uiState.update { it.copy(selectedQuality = option, selectedMode = mode) }
    }

    fun toggleItem(itemKey: String) {
        _uiState.update { state ->
            val updated = state.selectedItemKeys.toMutableSet()
            if (!updated.add(itemKey)) updated.remove(itemKey)
            state.copy(selectedItemKeys = updated)
        }
    }

    fun selectAllItems() {
        val ready = _uiState.value.sourceState as? DownloaderSourceState.Ready ?: return
        val allKeys = ready.metadata.playlistItems.map { "${it.index}_${it.videoId}" }.toSet()
        _uiState.update { it.copy(selectedItemKeys = allKeys) }
    }

    fun deselectAllItems() {
        _uiState.update { it.copy(selectedItemKeys = emptySet()) }
    }

    fun dismissCellularPrompt() {
        _uiState.update { it.copy(cellularPrompt = null) }
    }

    fun startDownload() {
        val ready = _uiState.value.sourceState as? DownloaderSourceState.Ready ?: return
        val meta = ready.metadata
        val state = _uiState.value

        runWithNetworkGuard {
            dispatchDownloadJob(meta, state)
        }
    }

    fun downloadThumbnailHd() {
        val ready = _uiState.value.sourceState as? DownloaderSourceState.Ready ?: return
        val thumbUrl = ready.metadata.thumbnailUrl ?: return
        runWithNetworkGuard {
            viewModelScope.launch {
                val tempJobId = "thumb_${UUID.randomUUID().toString().take(8)}"
                val workDir = workDirCleaner.createJobWorkDir(tempJobId)
                try {
                    val base = "${MediaSanitizer.sanitizeFilename(ready.metadata.title)}_thumb"
                    val tempFile = File(workDir, "$base.jpg")
                    directDownloader.downloadToFile(thumbUrl, tempFile)
                    val published = publisher.publishFile(
                        sourceFile = tempFile,
                        desiredBaseName = base,
                        module = JobModule.DOWNLOADER,
                        mediaType = MediaType.IMAGE,
                    )
                    historyDao.insert(
                        HistoryEntity.fromDomain(
                            HistoryEntry(
                                title = published.title,
                                module = JobModule.DOWNLOADER,
                                mediaType = MediaType.IMAGE,
                                mimeType = published.mimeType,
                                mediaStoreUri = published.uriString,
                                fileName = published.fileName,
                                sizeBytes = published.sizeBytes,
                                sourceUrl = ready.metadata.url,
                                createdAtEpochMs = System.currentTimeMillis(),
                            ),
                        ),
                    )
                    _uiState.update { it.copy(thumbnailDownloadMessage = "Thumbnail HD salva na Galeria!") }
                } catch (t: Throwable) {
                    _uiState.update { it.copy(thumbnailDownloadMessage = "Falha ao baixar thumbnail: ${t.message}") }
                } finally {
                    workDirCleaner.deleteJobWorkDir(tempJobId)
                }
            }
        }
    }

    private fun runWithNetworkGuard(action: () -> Unit) {
        val cfg = settings.value
        val transport = networkGuard.currentTransport()
        if (transport == NetworkTransport.CELLULAR) {
            if (cfg.wifiOnly) {
                _uiState.update {
                    it.copy(cellularPrompt = CellularPromptState(wifiOnlyBlocked = true, onConfirm = {}))
                }
                return
            }
            if (cfg.warnOnCellular) {
                _uiState.update {
                    it.copy(
                        cellularPrompt = CellularPromptState(
                            wifiOnlyBlocked = false,
                            onConfirm = {
                                _uiState.update { cur -> cur.copy(cellularPrompt = null) }
                                action()
                            },
                        ),
                    )
                }
                return
            }
        }
        action()
    }

    private fun dispatchDownloadJob(meta: MediaMetadata, state: DownloaderUiState) {
        val jobId = "dl_${UUID.randomUUID().toString().take(8)}"
        if (meta.isPlaylist && meta.playlistItems.isNotEmpty()) {
            val selected = meta.playlistItems.filter { "${it.index}_${it.videoId}" in state.selectedItemKeys }
            if (selected.isEmpty()) {
                _uiState.update { it.copy(thumbnailDownloadMessage = "Selecione pelo menos um item.") }
                return
            }
            jobOrchestrator.submit(
                MediaJobRequest.CollectionDownload(
                    jobId = jobId,
                    displayTitle = meta.title,
                    metadata = meta,
                    selectedItems = selected,
                    mode = state.selectedMode,
                    formatId = state.selectedQuality?.formatId,
                ),
            )
        } else {
            jobOrchestrator.submit(
                MediaJobRequest.SingleDownload(
                    jobId = jobId,
                    displayTitle = meta.title,
                    metadata = meta,
                    mode = state.selectedMode,
                    formatId = state.selectedQuality?.formatId,
                ),
            )
        }
    }
}
