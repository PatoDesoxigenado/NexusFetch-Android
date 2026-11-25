package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import java.net.URI

enum class ValidationStatus {
    VALID,
    CROSS_MATCH,
    INVALID,
}

data class UrlValidationResult(
    val status: ValidationStatus,
    val normalizedUrl: String,
    val detectedPlatform: Platform? = null,
    val suggestedPlatform: Platform? = null,
    val message: String = "",
)

/**
 * Validates URLs across YouTube, Instagram, and Twitter/X with strict HTTPS allowlisting.
 */
object UrlValidator {

    private val allowedHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "instagram.com",
        "www.instagram.com",
        "instagr.am",
        "www.instagr.am",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "x.com",
        "www.x.com",
        "mobile.x.com",
    )

    private const val IG_POST_PATH = """(?:/[a-zA-Z0-9_.]+)?/(?:p|reel|reels|tv)/[a-zA-Z0-9_-]+"""

    private val platformPatterns: Map<Platform, List<Regex>> = mapOf(
        Platform.YOUTUBE to listOf(
            Regex("""(?:https?://)?(?:www\.|m\.|music\.)?youtube\.com/watch\?(?:[^&\s]+&)*v=[a-zA-Z0-9_-]{11}""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?youtu\.be/[a-zA-Z0-9_-]{11}""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/shorts/[a-zA-Z0-9_-]{11}""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/live/[a-zA-Z0-9_-]+""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/embed/[a-zA-Z0-9_-]{11}""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|m\.|music\.)?youtube\.com/playlist\?list=[a-zA-Z0-9_-]+""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|m\.|music\.)?youtube\.com/watch\?(?:[^&\s]+&)*list=[a-zA-Z0-9_-]+""", RegexOption.IGNORE_CASE),
        ),
        Platform.INSTAGRAM to listOf(
            Regex("""(?:https?://)?(?:www\.)?instagram\.com$IG_POST_PATH""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.)?instagr\.am$IG_POST_PATH""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.)?instagram\.com/stories/[a-zA-Z0-9_.-]+/[0-9]+""", RegexOption.IGNORE_CASE),
        ),
        Platform.TWITTER to listOf(
            Regex("""(?:https?://)?(?:www\.|mobile\.)?(?:twitter\.com|x\.com)/[a-zA-Z0-9_]+/status/[0-9]+""", RegexOption.IGNORE_CASE),
            Regex("""(?:https?://)?(?:www\.|mobile\.)?(?:twitter\.com|x\.com)/i/status/[0-9]+""", RegexOption.IGNORE_CASE),
        ),
    )

    private val playlistRegex = Regex(
        """youtube\.com/playlist\?list=[a-zA-Z0-9_-]+|youtube\.com/watch\?(?:[^&\s]+&)*list=[a-zA-Z0-9_-]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Normalizes raw user/clipboard input into an HTTPS URL, rejecting dangerous schemes.
     */
    fun cleanUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val withHttps = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.contains("://") -> return "" // Reject file://, content://, ftp://, etc.
            else -> "https://$trimmed"
        }
        val uri = runCatching { URI(withHttps) }.getOrNull() ?: return ""
        val host = uri.host?.lowercase() ?: return ""
        return if (host in allowedHosts) withHttps else ""
    }

    /**
     * Detects whether a URL points to a YouTube playlist.
     */
    fun isPlaylistUrl(rawUrl: String): Boolean {
        val normalized = cleanUrl(rawUrl)
        if (normalized.isEmpty()) return false
        return playlistRegex.containsMatchIn(normalized)
    }

    /**
     * Detects which supported platform owns the URL.
     */
    fun detectPlatform(rawUrl: String): Platform? {
        val normalized = cleanUrl(rawUrl)
        if (normalized.isEmpty()) return null
        for ((platform, patterns) in platformPatterns) {
            if (patterns.any { it.containsMatchIn(normalized) }) {
                return platform
            }
        }
        return null
    }

    /**
     * Validates a link globally or relative to an expected platform tab (supports cross-platform redirect).
     */
    fun validateUrl(rawUrl: String, currentPlatform: Platform? = null): UrlValidationResult {
        if (rawUrl.isBlank()) {
            return UrlValidationResult(
                status = ValidationStatus.INVALID,
                normalizedUrl = "",
                message = "Por favor, insira uma URL válida.",
            )
        }

        val normalized = cleanUrl(rawUrl)
        val detected = detectPlatform(normalized)
        if (detected == null || normalized.isEmpty()) {
            return UrlValidationResult(
                status = ValidationStatus.INVALID,
                normalizedUrl = normalized,
                message = "Link não reconhecido ou plataforma não suportada.",
            )
        }

        if (currentPlatform == null || detected == currentPlatform) {
            return UrlValidationResult(
                status = ValidationStatus.VALID,
                normalizedUrl = normalized,
                detectedPlatform = detected,
                message = "Link válido do ${detected.label}.",
            )
        }

        return UrlValidationResult(
            status = ValidationStatus.CROSS_MATCH,
            normalizedUrl = normalized,
            detectedPlatform = detected,
            suggestedPlatform = detected,
            message = "Este link não é do ${currentPlatform.label}, mas pertence a ${detected.label}.",
        )
    }
}
