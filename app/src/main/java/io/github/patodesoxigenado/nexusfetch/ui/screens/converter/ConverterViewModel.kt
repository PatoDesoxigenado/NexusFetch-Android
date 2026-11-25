package io.github.patodesoxigenado.nexusfetch.ui.screens.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaProber
import io.github.patodesoxigenado.nexusfetch.domain.model.ConversionSpec
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.Mp4Codec
import io.github.patodesoxigenado.nexusfetch.domain.model.ResolutionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetAudioFormat
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetVideoFormat
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val mediaProber: MediaProber,
    private val jobOrchestrator: JobOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isProbing = true, errorMessage = null) }
        viewModelScope.launch {
            val probed = uris.mapNotNull { runCatching { mediaProber.probeUri(it) }.getOrNull() }
            _uiState.update { state ->
                val merged = (state.files + probed).distinctBy { it.uriString }
                val autoAudio = if (merged.isNotEmpty() && merged.all { !it.isVideo }) true else state.isAudioTarget
                state.copy(
                    files = merged,
                    isAudioTarget = autoAudio,
                    isProbing = false,
                )
            }
        }
    }

    fun removeFile(uriString: String) {
        _uiState.update { state ->
            state.copy(files = state.files.filterNot { it.uriString == uriString })
        }
    }

    fun setAudioTarget(isAudio: Boolean) {
        _uiState.update { it.copy(isAudioTarget = isAudio) }
    }

    fun selectVideoFormat(format: TargetVideoFormat) {
        _uiState.update { it.copy(videoFormat = format) }
    }

    fun selectAudioFormat(format: TargetAudioFormat) {
        _uiState.update { it.copy(audioFormat = format) }
    }

    fun selectResolution(preset: ResolutionPreset) {
        _uiState.update { it.copy(resolution = preset) }
    }

    fun selectMp4Codec(codec: Mp4Codec) {
        _uiState.update { it.copy(mp4Codec = codec) }
    }

    fun startConversion() {
        val state = _uiState.value
        if (state.files.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Adicione arquivos antes de iniciar a conversão.") }
            return
        }
        val targetExt = if (state.isAudioTarget) state.audioFormat.extension else state.videoFormat.extension
        val spec = ConversionSpec(
            targetExtension = targetExt,
            isAudioTarget = state.isAudioTarget,
            resolution = state.resolution,
            mp4Codec = state.mp4Codec,
        )
        val jobId = "conv_${UUID.randomUUID().toString().take(8)}"
        jobOrchestrator.submit(
            MediaJobRequest.ConvertBatch(
                jobId = jobId,
                displayTitle = "Conversor -> ${targetExt.uppercase()} (${state.files.size})",
                files = state.files,
                spec = spec,
            ),
        )
    }
}
