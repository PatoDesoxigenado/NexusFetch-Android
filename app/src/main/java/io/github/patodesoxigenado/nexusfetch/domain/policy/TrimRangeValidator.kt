package io.github.patodesoxigenado.nexusfetch.domain.policy

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class TrimValidationResult(
    val isValid: Boolean,
    val errorMessage: String = "",
)

/**
 * Pure timestamp parsing, validation, and yt-dlp 4K format selection for Fast Trim.
 */
object TrimRangeValidator {

    /**
     * Parses 'HH:MM:SS', 'MM:SS', or 'SS' (with '.' or ',' fractional seconds). Returns null if invalid.
     */
    fun parseTimestamp(value: String?): Double? {
        val raw = value?.trim()?.replace(',', '.') ?: return null
        if (raw.isEmpty()) return null

        val parts = raw.split(":")
        if (parts.size > 3) return null

        val nums = parts.map { it.toDoubleOrNull() ?: return null }
        if (nums.any { it.isNaN() || it.isInfinite() || it < 0.0 }) return null

        return when (parts.size) {
            1 -> nums[0]
            2 -> {
                val (mins, secs) = nums
                if (secs >= 60.0) null else mins * 60.0 + secs
            }
            else -> {
                val (hours, mins, secs) = nums
                if (mins >= 60.0 || secs >= 60.0) null else hours * 3600.0 + mins * 60.0 + secs
            }
        }
    }

    /**
     * Formats seconds into 'HH:MM:SS'.
     */
    fun formatTimestampHms(seconds: Double): String {
        val total = max(0, seconds.roundToInt())
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    /**
     * Formats seconds into filename-safe slug (e.g., '01h02m05s' or '02m00s').
     */
    fun formatTimestampSlug(seconds: Double): String {
        val total = max(0, seconds.roundToInt())
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.US, "%02dh%02dm%02ds", h, m, s)
        } else {
            String.format(Locale.US, "%02dm%02ds", m, s)
        }
    }

    /**
     * Validates [startSec, endSec] against optional source duration.
     */
    fun validateTrimRange(
        startSec: Double?,
        endSec: Double?,
        totalDurationSec: Double = 0.0,
    ): TrimValidationResult {
        if (startSec == null) {
            return TrimValidationResult(false, "Tempo inicial inválido. Use HH:MM:SS ou MM:SS.")
        }
        if (endSec == null) {
            return TrimValidationResult(false, "Tempo final inválido. Use HH:MM:SS ou MM:SS.")
        }
        if (startSec < 0.0) {
            return TrimValidationResult(false, "O tempo inicial não pode ser negativo.")
        }
        if (endSec <= startSec) {
            return TrimValidationResult(false, "O tempo final deve ser maior que o tempo inicial.")
        }
        if (totalDurationSec > 0.0 && startSec >= totalDurationSec) {
            return TrimValidationResult(
                false,
                "O tempo inicial ultrapassa a duração total (${formatTimestampHms(totalDurationSec)}).",
            )
        }
        if (totalDurationSec > 0.0 && endSec > totalDurationSec + 1.0) {
            return TrimValidationResult(
                false,
                "O tempo final (${formatTimestampHms(endSec)}) ultrapassa a duração total (${formatTimestampHms(totalDurationSec)}).",
            )
        }
        return TrimValidationResult(true)
    }

    /**
     * Builds the yt-dlp format selector for remote trimming.
     * NEVER forces `vcodec^=avc1` for Best or > 1080p (YouTube serves 2K/4K in VP9/AV1).
     */
    fun selectRemoteFormatString(audioOnly: Boolean, maxHeight: Int?): String =
        when {
            audioOnly -> "bestaudio[ext=m4a]/bestaudio/best"
            maxHeight == null -> "bestvideo+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
            maxHeight > 1080 ->
                "bestvideo[height<=$maxHeight]+bestaudio[ext=m4a]/" +
                    "bestvideo[height<=$maxHeight]+bestaudio/" +
                    "best[height<=$maxHeight]/best"
            else ->
                "bestvideo[vcodec^=avc1][height<=$maxHeight]+bestaudio[ext=m4a]/" +
                    "bestvideo[ext=mp4][height<=$maxHeight]+bestaudio[ext=m4a]/" +
                    "bestvideo[height<=$maxHeight]+bestaudio/" +
                    "best[height<=$maxHeight]/best"
        }

    /**
     * Formats the `--download-sections` argument value so yt-dlp downloads only the requested byte range.
     */
    fun formatDownloadSection(startSec: Double, endSec: Double): String =
        String.format(Locale.US, "*%.3f-%.3f", startSec, endSec)
}
