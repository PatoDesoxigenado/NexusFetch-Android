package io.github.patodesoxigenado.nexusfetch.service

import android.content.pm.ServiceInfo
import android.os.Build
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ForegroundServiceTypeTest {

    @Test
    fun `returns zero below Android Q and never uses MEDIA_PROCESSING on Android 14`() {
        assertEquals(0, MediaJobForegroundService.resolveServiceTypeForSdk(Build.VERSION_CODES.P, JobModule.COMPRESSOR.id))

        val android14Compressor = MediaJobForegroundService.resolveServiceTypeForSdk(
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            JobModule.COMPRESSOR.id,
        )
        val android14Converter = MediaJobForegroundService.resolveServiceTypeForSdk(
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            JobModule.CONVERTER.id,
        )
        val android14Downloader = MediaJobForegroundService.resolveServiceTypeForSdk(
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            JobModule.DOWNLOADER.id,
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, android14Compressor)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, android14Converter)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, android14Downloader)
        assertNotEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, android14Compressor)
    }

    @Test
    fun `uses MEDIA_PROCESSING only on Android 15+ for local compression and conversion`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            MediaJobForegroundService.resolveServiceTypeForSdk(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                JobModule.COMPRESSOR.id,
            ),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            MediaJobForegroundService.resolveServiceTypeForSdk(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                JobModule.CONVERTER.id,
            ),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            MediaJobForegroundService.resolveServiceTypeForSdk(
                Build.VERSION_CODES.VANILLA_ICE_CREAM,
                JobModule.DOWNLOADER.id,
            ),
        )
    }
}
