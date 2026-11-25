package io.github.patodesoxigenado.nexusfetch.ui.screens.compressor

import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPlan
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo

data class CompressorUiState(
    val files: List<MediaFileInfo> = emptyList(),
    val selectedPreset: CompressionPreset = CompressionPreset.DISCORD_10,
    val plansByUri: Map<String, CompressionPlan> = emptyMap(),
    val isProbing: Boolean = false,
    val errorMessage: String? = null,
)
