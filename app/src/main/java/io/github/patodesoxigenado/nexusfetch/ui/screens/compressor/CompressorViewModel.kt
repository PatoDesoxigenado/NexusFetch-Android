package io.github.patodesoxigenado.nexusfetch.ui.screens.compressor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.MediaProber
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPlan
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaJobRequest
import io.github.patodesoxigenado.nexusfetch.domain.policy.CompressionPlanCalculator
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CompressorViewModel @Inject constructor(
    private val mediaProber: MediaProber,
    private val jobOrchestrator: JobOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressorUiState())
    val uiState: StateFlow<CompressorUiState> = _uiState.asStateFlow()

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isProbing = true, errorMessage = null) }
        viewModelScope.launch {
            val probed = uris.mapNotNull { uri ->
                runCatching { mediaProber.probeUri(uri) }.getOrNull()
            }
            _uiState.update { state ->
                val merged = (state.files + probed).distinctBy { it.uriString }
                state.copy(
                    files = merged,
                    plansByUri = buildPlans(merged, state.selectedPreset),
                    isProbing = false,
                )
            }
        }
    }

    fun selectPreset(preset: CompressionPreset) {
        _uiState.update { state ->
            state.copy(
                selectedPreset = preset,
                plansByUri = buildPlans(state.files, preset),
            )
        }
    }

    fun removeFile(uriString: String) {
        _uiState.update { state ->
            val filtered = state.files.filterNot { it.uriString == uriString }
            state.copy(
                files = filtered,
                plansByUri = buildPlans(filtered, state.selectedPreset),
            )
        }
    }

    fun clearAll() {
        _uiState.update { it.copy(files = emptyList(), plansByUri = emptyMap(), errorMessage = null) }
    }

    fun startCompression() {
        val state = _uiState.value
        if (state.files.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Selecione ao menos um vídeo ou áudio.") }
            return
        }
        val jobId = "comp_${UUID.randomUUID().toString().take(8)}"
        jobOrchestrator.submit(
            MediaJobRequest.CompressBatch(
                jobId = jobId,
                displayTitle = "Compressor (${state.selectedPreset.limitMb.toInt()} MB)",
                files = state.files,
                preset = state.selectedPreset,
            ),
        )
    }

    private fun buildPlans(
        files: List<MediaFileInfo>,
        preset: CompressionPreset,
    ): Map<String, CompressionPlan> =
        files.associate { it.uriString to CompressionPlanCalculator.calculate(it, preset) }
}
