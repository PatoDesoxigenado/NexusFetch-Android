package io.github.patodesoxigenado.nexusfetch.ui.screens.converter

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.Mp4Codec
import io.github.patodesoxigenado.nexusfetch.domain.model.ResolutionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetAudioFormat
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetVideoFormat

data class ConverterUiState(
    val files: List<MediaFileInfo> = emptyList(),
    val isAudioTarget: Boolean = false,
    val videoFormat: TargetVideoFormat = TargetVideoFormat.MP4,
    val audioFormat: TargetAudioFormat = TargetAudioFormat.MP3,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val mp4Codec: Mp4Codec = Mp4Codec.H264,
    val isProbing: Boolean = false,
    val errorMessage: String? = null,
)
