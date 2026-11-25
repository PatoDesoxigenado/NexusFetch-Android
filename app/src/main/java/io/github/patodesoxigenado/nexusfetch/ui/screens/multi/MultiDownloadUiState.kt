package io.github.patodesoxigenado.nexusfetch.ui.screens.multi

import io.github.patodesoxigenado.nexusfetch.domain.model.MultiDownloadItem
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.CellularPromptState

data class MultiDownloadUiState(
    val items: List<MultiDownloadItem> = emptyList(),
    val isAnalyzing: Boolean = false,
    val allValidReady: Boolean = false,
    val globalError: String? = null,
    val cellularPrompt: CellularPromptState? = null,
)
