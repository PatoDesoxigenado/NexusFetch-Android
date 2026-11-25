package io.github.patodesoxigenado.nexusfetch.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrimRangeValidatorTest {

    @Test
    fun `parses valid timestamps and rejects malformed ones`() {
        assertEquals(75.5, TrimRangeValidator.parseTimestamp("01:15,5"))
        assertEquals(3725.0, TrimRangeValidator.parseTimestamp("01:02:05"))
        assertEquals(45.0, TrimRangeValidator.parseTimestamp("45"))
        assertNull(TrimRangeValidator.parseTimestamp("01:65"))
        assertNull(TrimRangeValidator.parseTimestamp("-5"))
        assertNull(TrimRangeValidator.parseTimestamp("abc"))
    }

    @Test
    fun `validates trim boundaries strictly`() {
        assertTrue(TrimRangeValidator.validateTrimRange(10.0, 45.0, 120.0).isValid)
        assertFalse(TrimRangeValidator.validateTrimRange(45.0, 45.0, 120.0).isValid)
        assertFalse(TrimRangeValidator.validateTrimRange(130.0, 140.0, 120.0).isValid)
        assertFalse(TrimRangeValidator.validateTrimRange(10.0, 130.0, 120.0).isValid)
    }

    @Test
    fun `never restricts to avc1 for Best or 2K 4K remote trim`() {
        val bestFmt = TrimRangeValidator.selectRemoteFormatString(audioOnly = false, maxHeight = null)
        val uhdFmt = TrimRangeValidator.selectRemoteFormatString(audioOnly = false, maxHeight = 2160)
        val qhdFmt = TrimRangeValidator.selectRemoteFormatString(audioOnly = false, maxHeight = 1440)
        val fhdFmt = TrimRangeValidator.selectRemoteFormatString(audioOnly = false, maxHeight = 1080)

        assertFalse(bestFmt.contains("avc1"))
        assertFalse(uhdFmt.contains("avc1"))
        assertFalse(qhdFmt.contains("avc1"))
        assertTrue(fhdFmt.contains("avc1"))
        assertEquals("*10.000-75.500", TrimRangeValidator.formatDownloadSection(10.0, 75.5))
    }
}
