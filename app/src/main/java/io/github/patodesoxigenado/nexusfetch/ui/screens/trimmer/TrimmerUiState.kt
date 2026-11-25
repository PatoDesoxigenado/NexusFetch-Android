package io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer

import io.github.patodesoxigenado.nexusfetch.domain.model.RemoteTrimQuality
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.CellularPromptState

sealed interface TrimSourceLoadState {
    data object Idle : TrimSourceLoadState
    data object Analyzing : TrimSourceLoadState
    data class Ready(val info: TrimSourceInfo) : TrimSourceLoadState
    data class Error(val message: String) : TrimSourceLoadState
}

data class TrimmerUiState(
    val sourceMode: TrimSourceType = TrimSourceType.URL,
    val urlInput: String = "",
    val sourceLoadState: TrimSourceLoadState = TrimSourceLoadState.Idle,
    val startSec: Float = 0f,
    val endSec: Float = 0f,
    val startText: String = "00:00:00",
    val endText: String = "00:00:00",
    val audioOnly: Boolean = false,
    val selectedQuality: RemoteTrimQuality = RemoteTrimQuality.BEST,
    val validationError: String? = null,
    val cellularPrompt: CellularPromptState? = null,
)
