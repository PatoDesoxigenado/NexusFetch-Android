package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Supported media platforms with display metadata and brand accent colors.
 */
enum class Platform(
    val id: String,
    val label: String,
    val badgeColorHex: Long,
) {
    YOUTUBE(id = "youtube", label = "YouTube", badgeColorHex = 0xFFE50914),
    INSTAGRAM(id = "instagram", label = "Instagram", badgeColorHex = 0xFFE1306C),
    TWITTER(id = "twitter", label = "Twitter / X", badgeColorHex = 0xFF1D9BF0),
}
