package io.github.patodesoxigenado.nexusfetch.ui.screens.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
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
import java.util.UUID
import javax.inject.Inject

sealed interface QuickShareUiState {
    data object Idle : QuickShareUiState
    data class AnalyzingLink(val url: String) : QuickShareUiState
    data class LinkReady(
        val metadata: MediaMetadata,
        val selectedQuality: VideoQualityOption?,
        val cellularWarning: Boolean,
        val wifiBlocked: Boolean,
    ) : QuickShareUiState
    data class LocalFilesReady(val uris: List<Uri>) : QuickShareUiState
    data class Error(val message: String) : QuickShareUiState
}

@HiltViewModel
class QuickShareViewModel @Inject constructor(
    private val youtubeDlEngine: YoutubeDlEngine,
    private val jobOrchestrator: JobOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val networkGuard: NetworkGuard,
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuickShareUiState>(QuickShareUiState.Idle)
    val uiState: StateFlow<QuickShareUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun handleSharedText(rawText: String) {
        val candidate = urlRegex.find(rawText)?.value?.trimEnd('.', ',', ';', ')') ?: rawText.trim()
        val validation = UrlValidator.validateUrl(candidate)
        if (validation.status == ValidationStatus.INVALID) {
            _uiState.value = QuickShareUiState.Error(validation.message)
            return
        }

        _uiState.value = QuickShareUiState.AnalyzingLink(validation.normalizedUrl)
        viewModelScope.launch {
            try {
                val meta = youtubeDlEngine.extractMetadata(validation.normalizedUrl)
                val cfg = settings.value
                val transport = networkGuard.currentTransport()
                val isCellular = transport == NetworkTransport.CELLULAR
                val preferredHeight = cfg.defaultVideoQualityHeight
                val defaultQuality = if (preferredHeight != null) {
                    meta.qualities.firstOrNull { it.height == preferredHeight } ?: meta.qualities.firstOrNull()
                } else {
                    meta.qualities.firstOrNull()
                }

                _uiState.value = QuickShareUiState.LinkReady(
                    metadata = meta,
                    selectedQuality = defaultQuality,
                    cellularWarning = isCellular && cfg.warnOnCellular && !cfg.wifiOnly,
                    wifiBlocked = isCellular && cfg.wifiOnly,
                )
            } catch (t: Throwable) {
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Erro ao analisar o link.")
                _uiState.value = QuickShareUiState.Error(msg)
            }
        }
    }

    fun handleSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _uiState.value = QuickShareUiState.Error("Nenhum arquivo compartilhado foi encontrado.")
            return
        }
        _uiState.value = QuickShareUiState.LocalFilesReady(uris)
    }

    fun selectQuality(option: VideoQualityOption) {
        _uiState.update { state ->
            if (state is QuickShareUiState.LinkReady) state.copy(selectedQuality = option) else state
        }
    }

    fun dispatchQuickDownload(modeOverride: String? = null): Boolean {
        val ready = _uiState.value as? QuickShareUiState.LinkReady ?: return false
        if (ready.wifiBlocked) return false

        val meta = ready.metadata
        val mode = modeOverride ?: when (meta.mediaType) {
            MediaType.IMAGE, MediaType.CAROUSEL -> "image"
            MediaType.GIF -> "gif"
            MediaType.AUDIO -> "audio"
            else -> "video"
        }
        val jobId = "share_${UUID.randomUUID().toString().take(8)}"

        if (meta.isPlaylist && meta.playlistItems.isNotEmpty()) {
            jobOrchestrator.submit(
                MediaJobRequest.CollectionDownload(
                    jobId = jobId,
                    displayTitle = meta.title,
                    metadata = meta,
                    selectedItems = meta.playlistItems,
                    mode = mode,
                    formatId = ready.selectedQuality?.formatId,
                ),
            )
        } else {
            jobOrchestrator.submit(
                MediaJobRequest.SingleDownload(
                    jobId = jobId,
                    displayTitle = meta.title,
                    metadata = meta,
                    mode = mode,
                    formatId = ready.selectedQuality?.formatId,
                ),
            )
        }
        return true
    }
}
