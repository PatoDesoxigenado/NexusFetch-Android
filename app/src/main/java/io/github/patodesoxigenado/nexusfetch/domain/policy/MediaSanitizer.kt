package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import java.net.URI
import java.util.Locale

/**
 * Pure formatting, filename sanitization, URL deduplication, and log redaction utilities.
 */
object MediaSanitizer {

    private val illegalFilenameChars = Regex("""[<>:"/\\|?*\u0000-\u001F]""")
    private val multiUnderscoreRegex = Regex("""_{2,}""")
    private val sensitiveQueryRegex = Regex(
        """([?&])(token|sig|signature|cookie|auth|authorization|key|lsig|sparams)=([^&\s]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val localUserPathRegex = Regex("""/data/(?:user/\d+|data)/[^\s"']+""")

    /**
     * Converts raw byte counts to human-readable units (e.g., "15.4 MB").
     */
    fun formatBytes(bytesCount: Long?): String {
        if (bytesCount == null || bytesCount <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytesCount.toDouble()
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIdx])
    }

    /**
     * Formats integer seconds as MM:SS or HH:MM:SS.
     */
    fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds < 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /**
     * Sanitizes file names against path traversal and filesystem-reserved characters.
     */
    fun sanitizeFilename(filename: String): String {
        val cleaned = filename
            .replace("..", "_")
            .replace(illegalFilenameChars, "_")
            .replace(multiUnderscoreRegex, "_")
            .trim('_', ' ', '.')
        return cleaned.ifEmpty { "download" }.take(120)
    }

    /**
     * Rewrites Twitter/X image URLs (`pbs.twimg.com/media/...`) to request original full resolution (`?name=orig`).
     */
    fun upgradeTwitterImageUrlToOrig(imgUrl: String): String {
        if (imgUrl.isBlank() || !imgUrl.contains("pbs.twimg.com/media/", ignoreCase = true)) {
            return imgUrl
        }
        val uri = runCatching { URI(imgUrl) }.getOrNull() ?: return imgUrl
        val rawQuery = uri.rawQuery
        val filteredPairs = if (rawQuery.isNullOrBlank()) {
            mutableListOf()
        } else {
            rawQuery.split("&")
                .filter { it.isNotBlank() && !it.startsWith("name=", ignoreCase = true) }
                .toMutableList()
        }
        filteredPairs.add("name=orig")
        val newQuery = filteredPairs.joinToString("&")
        return URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toASCIIString()
    }

    /**
     * Deduplicates carousel/media URLs by canonical path basename while preserving original order.
     */
    fun deduplicateMediaUrls(rawUrls: List<String>): List<String> {
        val uniqueUrls = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()
        for (rawUrl in rawUrls) {
            if (!rawUrl.startsWith("https://", ignoreCase = true) &&
                !rawUrl.startsWith("http://", ignoreCase = true)
            ) {
                continue
            }
            val parsed = runCatching { URI(rawUrl) }.getOrNull()
            val path = parsed?.path.orEmpty()
            val basename = path.substringAfterLast('/').ifEmpty { path }
            val canonicalKey = basename.ifEmpty { rawUrl }
            if (seenKeys.add(canonicalKey)) {
                uniqueUrls.add(rawUrl)
            }
        }
        return uniqueUrls
    }

    /**
     * Checks whether a CDN URL points directly to a video file.
     */
    fun isVideoMediaUrl(mediaUrl: String): Boolean {
        val pathLower = runCatching { URI(mediaUrl).path.orEmpty().lowercase(Locale.US) }
            .getOrDefault(mediaUrl.lowercase(Locale.US))
        return pathLower.endsWith(".mp4") ||
            pathLower.endsWith(".mov") ||
            pathLower.endsWith(".m4v") ||
            pathLower.endsWith(".webm")
    }

    /**
     * Infers a clean file extension from a CDN URL or fallback media type.
     */
    fun inferExtension(mediaUrl: String, itemType: MediaType = MediaType.IMAGE): String {
        val pathLower = runCatching { URI(mediaUrl).path.orEmpty().lowercase(Locale.US) }
            .getOrDefault(mediaUrl.lowercase(Locale.US))
        val candidates = listOf(".mp4", ".mov", ".webm", ".png", ".webp", ".jpg", ".jpeg")
        for (ext in candidates) {
            if (pathLower.endsWith(ext)) {
                return if (ext == ".jpeg") ".jpg" else ext
            }
        }
        return if (itemType == MediaType.VIDEO) ".mp4" else ".jpg"
    }

    /**
     * Strips tokens, signatures, and private app sandbox paths before writing local diagnostic logs.
     */
    fun redactSensitiveText(text: String): String =
        text
            .replace(sensitiveQueryRegex, "$1$2=<REDACTED>")
            .replace(localUserPathRegex, "<APP_PRIVATE_DIR>")
}
