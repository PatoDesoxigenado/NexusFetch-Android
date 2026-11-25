package io.github.patodesoxigenado.nexusfetch.ui

import io.github.patodesoxigenado.nexusfetch.data.engine.MediaProber
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryDao
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.WorkDirCleaner
import io.github.patodesoxigenado.nexusfetch.data.remote.DirectMediaDownloader
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.DownloaderSourceState
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.DownloaderViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer.TrimSourceLoadState
import io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer.TrimmerViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateResetTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `downloader resets previous metadata and completion banner when analyzing a new url`() = runTest {
        val youtubeDl = mockk<YoutubeDlEngine>()
        val orchestrator = mockk<JobOrchestrator>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository> {
            every { settings } returns flowOf(AppSettings())
        }
        val vm = DownloaderViewModel(
            youtubeDlEngine = youtubeDl,
            jobOrchestrator = orchestrator,
            settingsRepository = settingsRepo,
            networkGuard = mockk<NetworkGuard>(relaxed = true),
            directDownloader = mockk<DirectMediaDownloader>(relaxed = true),
            publisher = mockk<MediaStorePublisher>(relaxed = true),
            workDirCleaner = mockk<WorkDirCleaner>(relaxed = true),
            historyDao = mockk<HistoryDao>(relaxed = true),
        )

        val meta1 = MediaMetadata(
            url = "https://www.youtube.com/watch?v=aaaaaaaaaaa",
            platform = Platform.YOUTUBE,
            title = "Primeiro Vídeo",
            uploader = "Canal 1",
            mediaType = MediaType.VIDEO,
            qualities = listOf(VideoQualityOption("2160p", 2160, "best")),
        )
        val meta2 = MediaMetadata(
            url = "https://x.com/user/status/999",
            platform = Platform.TWITTER,
            title = "Foto X",
            uploader = "XUser",
            mediaType = MediaType.IMAGE,
            imageUrls = listOf("https://pbs.twimg.com/media/abc.jpg?name=orig"),
        )
        coEvery { youtubeDl.extractMetadata("https://www.youtube.com/watch?v=aaaaaaaaaaa") } returns meta1
        coEvery { youtubeDl.extractMetadata("https://x.com/user/status/999") } returns meta2

        vm.analyzeWithUrl("https://www.youtube.com/watch?v=aaaaaaaaaaa")
        advanceUntilIdle()
        assertEquals("Primeiro Vídeo", (vm.uiState.value.sourceState as DownloaderSourceState.Ready).metadata.title)

        vm.analyzeWithUrl("https://x.com/user/status/999")
        advanceUntilIdle()

        val secondReady = vm.uiState.value.sourceState as DownloaderSourceState.Ready
        assertEquals("Foto X", secondReady.metadata.title)
        assertEquals("image", vm.uiState.value.selectedMode)
        assertTrue(vm.uiState.value.selectedItemKeys.isEmpty())
        verify(atLeast = 2) { orchestrator.clearCompletedBanner() }
    }

    @Test
    fun `trimmer resets start end timestamps and validation error when loading new source`() = runTest {
        val youtubeDl = mockk<YoutubeDlEngine>()
        val orchestrator = mockk<JobOrchestrator>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository> {
            every { settings } returns flowOf(AppSettings())
        }
        val vm = TrimmerViewModel(
            youtubeDlEngine = youtubeDl,
            mediaProber = mockk<MediaProber>(relaxed = true),
            settingsRepository = settingsRepo,
            networkGuard = mockk<NetworkGuard>(relaxed = true),
            jobOrchestrator = orchestrator,
        )

        coEvery { youtubeDl.analyzeTrimUrl("https://www.youtube.com/watch?v=11111111111") } returns TrimSourceInfo(
            sourceType = TrimSourceType.URL,
            source = "https://www.youtube.com/watch?v=11111111111",
            title = "Clip A",
            durationSec = 120.0,
        )
        coEvery { youtubeDl.analyzeTrimUrl("https://www.youtube.com/watch?v=22222222222") } returns TrimSourceInfo(
            sourceType = TrimSourceType.URL,
            source = "https://www.youtube.com/watch?v=22222222222",
            title = "Clip B",
            durationSec = 45.0,
        )

        vm.analyzeRemoteUrl("https://www.youtube.com/watch?v=11111111111")
        advanceUntilIdle()
        vm.onSliderRangeChanged(15f, 95f)
        assertEquals("00:01:35", vm.uiState.value.endText)

        vm.analyzeRemoteUrl("https://www.youtube.com/watch?v=22222222222")
        advanceUntilIdle()

        val ready = vm.uiState.value.sourceLoadState as TrimSourceLoadState.Ready
        assertEquals("Clip B", ready.info.title)
        assertEquals("00:00:00", vm.uiState.value.startText)
        assertEquals("00:00:45", vm.uiState.value.endText)
        assertEquals(0f, vm.uiState.value.startSec)
        assertEquals(45f, vm.uiState.value.endSec)
        verify(atLeast = 2) { orchestrator.clearCompletedBanner() }
    }
}
