package io.github.patodesoxigenado.nexusfetch.ui.screens.downloader

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption

sealed interface DownloaderSourceState {
    data object Idle : DownloaderSourceState
    data class Analyzing(val url: String) : DownloaderSourceState
    data class Ready(val metadata: MediaMetadata) : DownloaderSourceState
    data class Error(val message: String) : DownloaderSourceState
}

data class CellularPromptState(
    val wifiOnlyBlocked: Boolean,
    val onConfirm: () -> Unit,
)

data class DownloaderUiState(
    val urlInput: String = "",
    val detectedPlatform: Platform? = null,
    val sourceState: DownloaderSourceState = DownloaderSourceState.Idle,
    val selectedMode: String = "video", // "video", "audio", "image", "gif"
    val selectedQuality: VideoQualityOption? = null,
    val selectedItemKeys: Set<String> = emptySet(),
    val cellularPrompt: CellularPromptState? = null,
    val thumbnailDownloadMessage: String? = null,
)
