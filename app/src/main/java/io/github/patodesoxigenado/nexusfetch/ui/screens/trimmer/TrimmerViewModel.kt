package io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaProber
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.RemoteTrimQuality
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.domain.policy.TrimRangeValidator
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.CellularPromptState
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
class TrimmerViewModel @Inject constructor(
    private val youtubeDlEngine: YoutubeDlEngine,
    private val mediaProber: MediaProber,
    private val settingsRepository: SettingsRepository,
    private val networkGuard: NetworkGuard,
    private val jobOrchestrator: JobOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrimmerUiState())
    val uiState: StateFlow<TrimmerUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun switchSourceMode(mode: TrimSourceType) {
        if (_uiState.value.sourceMode == mode) return
        resetSourceState(mode = mode, urlInput = if (mode == TrimSourceType.URL) _uiState.value.urlInput else "")
    }

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(urlInput = url) }
    }

    fun analyzeRemoteUrl(overrideUrl: String? = null) {
        val raw = (overrideUrl ?: _uiState.value.urlInput).trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(sourceLoadState = TrimSourceLoadState.Error("Informe um link válido.")) }
            return
        }
        // Complete state reset before loading new source
        resetSourceState(mode = TrimSourceType.URL, urlInput = raw, analyzing = true)

        viewModelScope.launch {
            try {
                val info = youtubeDlEngine.analyzeTrimUrl(raw)
                applyLoadedSource(info)
            } catch (t: Throwable) {
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Não foi possível analisar o link.")
                _uiState.update { it.copy(sourceLoadState = TrimSourceLoadState.Error(msg)) }
            }
        }
    }

    fun loadLocalUri(uri: Uri) {
        // Complete state reset before loading new local file
        resetSourceState(mode = TrimSourceType.FILE, urlInput = "", analyzing = true)
        viewModelScope.launch {
            try {
                val fileInfo = mediaProber.probeUri(uri)
                val sourceInfo = TrimSourceInfo(
                    sourceType = TrimSourceType.FILE,
                    source = uri.toString(),
                    title = fileInfo.name.substringBeforeLast('.'),
                    durationSec = fileInfo.durationSec,
                    isVideo = fileInfo.isVideo,
                    uploader = "Arquivo Local",
                    fileExt = fileInfo.ext,
                )
                applyLoadedSource(sourceInfo)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(sourceLoadState = TrimSourceLoadState.Error("Falha ao ler arquivo local: ${t.message}"))
                }
            }
        }
    }

    fun onSliderRangeChanged(start: Float, end: Float) {
        val safeStart = start.coerceAtLeast(0f)
        val safeEnd = end.coerceAtLeast(safeStart + 0.5f)
        _uiState.update {
            it.copy(
                startSec = safeStart,
                endSec = safeEnd,
                startText = TrimRangeValidator.formatTimestampHms(safeStart.toDouble()),
                endText = TrimRangeValidator.formatTimestampHms(safeEnd.toDouble()),
                validationError = null,
            )
        }
    }

    fun onStartTextChanged(text: String) {
        val parsed = TrimRangeValidator.parseTimestamp(text)?.toFloat()
        _uiState.update { state ->
            state.copy(
                startText = text,
                startSec = parsed ?: state.startSec,
                validationError = null,
            )
        }
    }

    fun onEndTextChanged(text: String) {
        val parsed = TrimRangeValidator.parseTimestamp(text)?.toFloat()
        _uiState.update { state ->
            state.copy(
                endText = text,
                endSec = parsed ?: state.endSec,
                validationError = null,
            )
        }
    }

    fun setAudioOnly(audioOnly: Boolean) {
        _uiState.update { it.copy(audioOnly = audioOnly) }
    }

    fun selectRemoteQuality(quality: RemoteTrimQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun dismissCellularPrompt() {
        _uiState.update { it.copy(cellularPrompt = null) }
    }

    fun startTrim() {
        val state = _uiState.value
        val ready = state.sourceLoadState as? TrimSourceLoadState.Ready ?: return
        val startParsed = TrimRangeValidator.parseTimestamp(state.startText)
        val endParsed = TrimRangeValidator.parseTimestamp(state.endText)
        val validation = TrimRangeValidator.validateTrimRange(
            startSec = startParsed,
            endSec = endParsed,
            totalDurationSec = ready.info.durationSec,
        )
        if (!validation.isValid || startParsed == null || endParsed == null) {
            _uiState.update { it.copy(validationError = validation.errorMessage) }
            return
        }

        val trimReq = TrimRequest(
            sourceInfo = ready.info,
            startSec = startParsed,
            endSec = endParsed,
            audioOnly = state.audioOnly,
            maxHeight = state.selectedQuality.maxHeight,
        )

        if (ready.info.sourceType == TrimSourceType.URL) {
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
                                dispatchTrim(trimReq)
                            },
                        )
                    }
                    return
                }
            }
        }
        dispatchTrim(trimReq)
    }

    private fun dispatchTrim(req: TrimRequest) {
        val jobId = "trim_${UUID.randomUUID().toString().take(8)}"
        jobOrchestrator.submit(
            MediaJobRequest.TrimMedia(
                jobId = jobId,
                displayTitle = "Recorte: ${req.sourceInfo.title}",
                request = req,
            ),
        )
    }

    private fun resetSourceState(
        mode: TrimSourceType,
        urlInput: String,
        analyzing: Boolean = false,
    ) {
        jobOrchestrator.clearCompletedBanner()
        _uiState.value = TrimmerUiState(
            sourceMode = mode,
            urlInput = urlInput,
            sourceLoadState = if (analyzing) TrimSourceLoadState.Analyzing else TrimSourceLoadState.Idle,
        )
    }

    private fun applyLoadedSource(info: TrimSourceInfo) {
        val dur = info.durationSec.toFloat().coerceAtLeast(0f)
        val defaultEnd = if (dur > 0f) dur else 60f
        _uiState.update {
            it.copy(
                sourceLoadState = TrimSourceLoadState.Ready(info),
                startSec = 0f,
                endSec = defaultEnd,
                startText = "00:00:00",
                endText = TrimRangeValidator.formatTimestampHms(defaultEnd.toDouble()),
                audioOnly = !info.isVideo,
                validationError = null,
            )
        }
    }
}
