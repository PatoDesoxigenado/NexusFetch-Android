package io.github.patodesoxigenado.nexusfetch.domain.policy

/**
 * Prevents false 100% progress reporting in multi-item playlists, carousels, and batch jobs.
 */
object AggregateProgressCalculator {

    fun calculateOverallPercent(
        completedItems: Int,
        currentItemFraction: Float,
        totalItems: Int,
    ): Float {
        if (totalItems <= 0) return 0f
        if (completedItems >= totalItems) return 100f
        val clampedFraction = currentItemFraction.coerceIn(0f, 1f)
        val rawPercent = ((completedItems.toFloat() + clampedFraction) / totalItems.toFloat()) * 100f
        return rawPercent.coerceIn(0f, 99.9f)
    }
}

/**
 * Exponential moving average speed smoother (alpha = 0.25) matching Desktop SpeedSmoother.
 */
class SpeedSmoother(
    private val alpha: Double = 0.25,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var smoothedBytesPerSec: Double = 0.0
    private var lastBytes: Long = 0L
    private var lastTimeMs: Long = 0L

    fun update(currentBytes: Long, reportedSpeedBytesPerSec: Double? = null): Double {
        val now = nowMsProvider()
        if (lastTimeMs == 0L) {
            lastTimeMs = now
            lastBytes = currentBytes
            smoothedBytesPerSec = reportedSpeedBytesPerSec ?: 0.0
            return smoothedBytesPerSec
        }

        val deltaMs = now - lastTimeMs
        if (deltaMs >= 250L) {
            val deltaBytes = (currentBytes - lastBytes).coerceAtLeast(0L)
            val instSpeed = deltaBytes.toDouble() / (deltaMs / 1000.0)
            smoothedBytesPerSec = if (smoothedBytesPerSec <= 0.0) {
                instSpeed
            } else {
                alpha * instSpeed + (1.0 - alpha) * smoothedBytesPerSec
            }
            lastTimeMs = now
            lastBytes = currentBytes
        }

        if (smoothedBytesPerSec <= 0.0 && reportedSpeedBytesPerSec != null) {
            return reportedSpeedBytesPerSec
        }
        return smoothedBytesPerSec
    }
}
