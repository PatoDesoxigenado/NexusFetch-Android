package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.OutputFormatOption

/**
 * Enforces per-item output format options in Multi-Download queues so photos/GIFs never receive video-only formats.
 */
object MultiDownloadFormatPolicy {

    private val videoDefaults = listOf(
        OutputFormatOption(
            key = "mp4_best",
            label = "MP4 (Melhor Qualidade)",
            mode = "video",
            formatId = "bestvideo+bestaudio/best",
        ),
        OutputFormatOption(
            key = "mp4_1080",
            label = "MP4 (1080p Full HD)",
            mode = "video",
            formatId = "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
        ),
        OutputFormatOption(
            key = "mp4_720",
            label = "MP4 (720p HD)",
            mode = "video",
            formatId = "bestvideo[height<=720]+bestaudio/best[height<=720]",
        ),
        OutputFormatOption(
            key = "mp3_320",
            label = "MP3 (Áudio 320 kbps)",
            mode = "audio",
            formatId = "bestaudio/best",
        ),
    )

    private val imageOptions = listOf(
        OutputFormatOption(key = "img_orig", label = "Imagem Original", mode = "image", targetImageExt = null),
        OutputFormatOption(key = "img_png", label = "PNG", mode = "image", targetImageExt = "png"),
        OutputFormatOption(key = "img_jpg", label = "JPG", mode = "image", targetImageExt = "jpg"),
        OutputFormatOption(key = "img_webp", label = "WEBP", mode = "image", targetImageExt = "webp"),
    )

    private val gifOptions = listOf(
        OutputFormatOption(key = "gif_anim", label = "GIF Animado (.gif)", mode = "gif", formatId = "gif_convert"),
        OutputFormatOption(key = "gif_mp4", label = "Vídeo (.mp4)", mode = "video", formatId = "gif_mp4"),
    )

    private val audioOptions = listOf(
        OutputFormatOption(key = "aud_mp3", label = "MP3 (320 kbps)", mode = "audio", formatId = "mp3"),
        OutputFormatOption(key = "aud_m4a", label = "M4A (AAC)", mode = "audio", formatId = "m4a"),
        OutputFormatOption(key = "aud_flac", label = "FLAC", mode = "audio", formatId = "flac"),
        OutputFormatOption(key = "aud_wav", label = "WAV", mode = "audio", formatId = "wav"),
    )

    fun getAllowedOutputOptions(metadata: MediaMetadata): List<OutputFormatOption> =
        when (metadata.mediaType) {
            MediaType.IMAGE -> imageOptions
            MediaType.GIF -> gifOptions
            MediaType.AUDIO -> audioOptions
            MediaType.CAROUSEL -> listOf(
                OutputFormatOption(
                    key = "carousel_orig",
                    label = "Mídias Originais (${metadata.playlistCount} itens)",
                    mode = "carousel",
                    formatId = "carousel_orig",
                ),
            )
            MediaType.PLAYLIST, MediaType.VIDEO -> {
                val dynamicOptions = metadata.qualities
                    .filter { it.formatId != "gif_convert" && it.formatId != "photo_orig" }
                    .map { q ->
                        OutputFormatOption(
                            key = "vid_${q.height ?: "best"}",
                            label = "MP4 — ${q.label}",
                            mode = "video",
                            formatId = q.formatId,
                        )
                    }
                val base = dynamicOptions.ifEmpty { videoDefaults.dropLast(1) }
                base + OutputFormatOption(
                    key = "mp3_320",
                    label = "MP3 (Extrair Áudio 320 kbps)",
                    mode = "audio",
                    formatId = "bestaudio/best",
                )
            }
        }
}
