package io.github.patodesoxigenado.nexusfetch.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateProgressCalculatorTest {

    @Test
    fun `never reports 100 percent until every item completes`() {
        val firstDone = AggregateProgressCalculator.calculateOverallPercent(
            completedItems = 1,
            currentItemFraction = 0f,
            totalItems = 4,
        )
        assertEquals(25f, firstDone, 0.01f)

        val almostAllDone = AggregateProgressCalculator.calculateOverallPercent(
            completedItems = 3,
            currentItemFraction = 1f,
            totalItems = 4,
        )
        assertEquals(99.9f, almostAllDone, 0.01f)

        val allDone = AggregateProgressCalculator.calculateOverallPercent(
            completedItems = 4,
            currentItemFraction = 0f,
            totalItems = 4,
        )
        assertEquals(100f, allDone, 0.01f)
    }

    @Test
    fun `speed smoother applies exponential moving average`() {
        var clock = 1000L
        val smoother = SpeedSmoother(alpha = 0.25) { clock }
        assertEquals(0.0, smoother.update(0L, 0.0))

        clock = 2000L
        val first = smoother.update(1_000_000L, null)
        assertTrue(first > 0.0)

        clock = 3000L
        val second = smoother.update(3_000_000L, null)
        assertTrue(second > first && second < 2_000_000.0)
    }
}
