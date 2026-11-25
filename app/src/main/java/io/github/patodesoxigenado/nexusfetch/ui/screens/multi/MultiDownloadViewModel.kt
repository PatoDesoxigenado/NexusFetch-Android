package io.github.patodesoxigenado.nexusfetch.ui.screens.multi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.local.db.PendingQueueDao
import io.github.patodesoxigenado.nexusfetch.data.local.db.PendingQueueEntity
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.MultiDownloadItem
import io.github.patodesoxigenado.nexusfetch.domain.model.MultiItemStatus
import io.github.patodesoxigenado.nexusfetch.domain.model.OutputFormatOption
import io.github.patodesoxigenado.nexusfetch.domain.policy.MultiDownloadFormatPolicy
import io.github.patodesoxigenado.nexusfetch.domain.policy.UrlValidator
import io.github.patodesoxigenado.nexusfetch.domain.policy.ValidationStatus
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.CellularPromptState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MultiDownloadViewModel @Inject constructor(
    private val youtubeDlEngine: YoutubeDlEngine,
    private val pendingQueueDao: PendingQueueDao,
    private val settingsRepository: SettingsRepository,
    private val networkGuard: NetworkGuard,
    private val jobOrchestrator: JobOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MultiDownloadUiState(items = initialEmptyRows()),
    )
    val uiState: StateFlow<MultiDownloadUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        viewModelScope.launch {
            val saved = pendingQueueDao.getQueueSnapshot()
            if (saved.isNotEmpty()) {
                val restored = saved.mapIndexed { idx, entity ->
                    MultiDownloadItem(
                        id = entity.id,
                        position = idx + 1,
                        rawUrl = entity.rawUrl,
                        platform = UrlValidator.detectPlatform(entity.rawUrl),
                        status = MultiItemStatus.PENDING_INPUT,
                    )
                }
                _uiState.update { it.copy(items = restored, allValidReady = false) }
            }
        }
    }

    fun addRow() {
        _uiState.update { state ->
            if (state.items.size >= MAX_LINKS) return@update state
            val updated = state.items + MultiDownloadItem(
                id = UUID.randomUUID().toString(),
                position = state.items.size + 1,
            )
            state.copy(items = updated, allValidReady = false)
        }
        persistRows()
    }

    fun removeRow(id: String) {
        _uiState.update { state ->
            val updated = if (state.items.size <= 1) {
                listOf(MultiDownloadItem(id = UUID.randomUUID().toString(), position = 1))
            } else {
                state.items.filterNot { it.id == id }
                    .mapIndexed { idx, item -> item.copy(position = idx + 1) }
            }
            val readyCount = updated.count { it.status == MultiItemStatus.READY }
            val nonBlankCount = updated.count { it.rawUrl.isNotBlank() }
            state.copy(
                items = updated,
                allValidReady = nonBlankCount > 0 && readyCount == nonBlankCount,
            )
        }
        persistRows()
    }

    fun updateUrl(id: String, url: String) {
        val detected = UrlValidator.detectPlatform(url)
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.id == id) {
                    item.copy(
                        rawUrl = url,
                        platform = detected,
                        metadata = null,
                        allowedFormats = emptyList(),
                        selectedFormat = null,
                        status = MultiItemStatus.PENDING_INPUT,
                        errorMessage = null,
                    )
                } else {
                    item
                }
            }
            state.copy(items = updated, allValidReady = false, globalError = null)
        }
        persistRows()
    }

    fun selectFormat(id: String, option: OutputFormatOption) {
        _uiState.update { state ->
            val updated = state.items.map {
                if (it.id == id) it.copy(selectedFormat = option) else it
            }
            state.copy(items = updated)
        }
    }

    fun analyzeQueue() {
        val nonBlank = _uiState.value.items.filter { it.rawUrl.isNotBlank() }
        if (nonBlank.isEmpty()) {
            _uiState.update { it.copy(globalError = "Adicione pelo menos um link válido antes de analisar.") }
            return
        }

        _uiState.update { state ->
            state.copy(
                isAnalyzing = true,
                globalError = null,
                allValidReady = false,
                items = state.items.map {
                    if (it.rawUrl.isNotBlank()) it.copy(status = MultiItemStatus.ANALYZING, errorMessage = null) else it
                },
            )
        }

        viewModelScope.launch {
            val analyzed = coroutineScope {
                _uiState.value.items.map { item ->
                    async {
                        if (item.rawUrl.isBlank()) return@async item
                        val validation = UrlValidator.validateUrl(item.rawUrl)
                        if (validation.status == ValidationStatus.INVALID) {
                            return@async item.copy(
                                status = MultiItemStatus.INVALID,
                                errorMessage = validation.message,
                                metadata = null,
                                allowedFormats = emptyList(),
                                selectedFormat = null,
                            )
                        }
                        runCatching {
                            val meta = youtubeDlEngine.extractMetadata(validation.normalizedUrl)
                            val allowed = MultiDownloadFormatPolicy.getAllowedOutputOptions(meta)
                            item.copy(
                                rawUrl = validation.normalizedUrl,
                                platform = meta.platform,
                                metadata = meta,
                                allowedFormats = allowed,
                                selectedFormat = allowed.firstOrNull(),
                                status = MultiItemStatus.READY,
                                errorMessage = null,
                            )
                        }.getOrElse { err ->
                            item.copy(
                                status = MultiItemStatus.INVALID,
                                errorMessage = err.message ?: "Falha na análise.",
                                metadata = null,
                                allowedFormats = emptyList(),
                                selectedFormat = null,
                            )
                        }
                    }
                }.awaitAll()
            }

            val nonEmpty = analyzed.filter { it.rawUrl.isNotBlank() }
            val ready = nonEmpty.isNotEmpty() && nonEmpty.all { it.status == MultiItemStatus.READY }
            _uiState.update {
                it.copy(
                    items = analyzed,
                    isAnalyzing = false,
                    allValidReady = ready,
                    globalError = if (!ready) "Corrija ou remova os links inválidos antes de iniciar o download." else null,
                )
            }
            persistRows()
        }
    }

    fun startBatchDownload() {
        val state = _uiState.value
        val readyItems = state.items.filter { it.status == MultiItemStatus.READY && it.metadata != null }
        if (!state.allValidReady || readyItems.isEmpty()) {
            _uiState.update { it.copy(globalError = "Analise e valide todos os links antes de baixar.") }
            return
        }

        val cfg = settings.value
        val transport = networkGuard.currentTransport()
        if (transport == NetworkTransport.CELLULAR) {
            if (cfg.wifiOnly) {
                _uiState.update { it.copy(cellularPrompt = CellularPromptState(true) {}) }
                return
            }
            if (cfg.warnOnCellular) {
                _uiState.update {
                    it.copy(
                        cellularPrompt = CellularPromptState(false) {
                            _uiState.update { cur -> cur.copy(cellularPrompt = null) }
                            dispatchBatch(readyItems)
                        },
                    )
                }
                return
            }
        }
        dispatchBatch(readyItems)
    }

    fun dismissCellularPrompt() {
        _uiState.update { it.copy(cellularPrompt = null) }
    }

    private fun dispatchBatch(readyItems: List<MultiDownloadItem>) {
        val jobId = "multi_${UUID.randomUUID().toString().take(8)}"
        jobOrchestrator.submit(
            MediaJobRequest.MultiDownloadBatch(
                jobId = jobId,
                displayTitle = "Multi-Download (${readyItems.size} links)",
                items = readyItems,
            ),
        )
    }

    private fun persistRows() {
        val snapshot = _uiState.value.items
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            pendingQueueDao.replaceQueue(
                snapshot.map { item ->
                    PendingQueueEntity(
                        id = item.id,
                        position = item.position,
                        rawUrl = item.rawUrl,
                        platformId = item.platform?.id,
                        title = item.metadata?.title,
                        mediaTypeId = item.metadata?.mediaType?.id,
                        selectedFormatKey = item.selectedFormat?.key,
                        statusName = item.status.name,
                        updatedAtEpochMs = now,
                    )
                },
            )
        }
    }

    private fun initialEmptyRows(): List<MultiDownloadItem> = listOf(
        MultiDownloadItem(id = UUID.randomUUID().toString(), position = 1),
        MultiDownloadItem(id = UUID.randomUUID().toString(), position = 2),
    )

    companion object {
        const val MAX_LINKS = 10
    }
}
