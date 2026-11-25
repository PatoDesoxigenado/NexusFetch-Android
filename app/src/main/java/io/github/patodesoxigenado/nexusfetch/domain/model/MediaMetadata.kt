package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Canonical media categories extracted across YouTube, Instagram, and Twitter/X.
 */
enum class MediaType(val id: String) {
    VIDEO("video"),
    AUDIO("audio"),
    IMAGE("image"),
    GIF("gif"),
    PLAYLIST("playlist"),
    CAROUSEL("carousel"),
}

/**
 * Downloadable quality or format variant presented for a single media item or playlist.
 */
data class VideoQualityOption(
    val label: String,
    val height: Int? = null,
    val formatId: String,
    val filesizeBytes: Long? = null,
    val directUrl: String? = null,
)

/**
 * Single selectable entry inside a YouTube playlist or an Instagram/Twitter carousel.
 */
data class PlaylistItem(
    val index: Int,
    val videoId: String,
    val title: String,
    val durationSec: Int? = null,
    val durationStr: String = "--:--",
    val url: String,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val isSelected: Boolean = true,
    val itemType: MediaType = MediaType.VIDEO,
)

/**
 * Normalized metadata returned after analyzing a supported URL.
 */
data class MediaMetadata(
    val url: String,
    val platform: Platform? = null,
    val title: String,
    val uploader: String,
    val durationSec: Int? = null,
    val durationStr: String = "--:--",
    val thumbnailUrl: String? = null,
    val qualities: List<VideoQualityOption> = emptyList(),
    val mediaType: MediaType = MediaType.VIDEO,
    val imageUrls: List<String> = emptyList(),
    val videoStreamUrl: String? = null,
    val estimatedAudioSizeBytes: Long? = null,
    val isPlaylist: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
    val playlistCount: Int = 0,
    val error: String? = null,
)
