package io.github.patodesoxigenado.nexusfetch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.storage.WorkDirCleaner
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entrypoint: cleans orphaned temporary artifacts and warms up yt-dlp/FFmpeg off the main thread.
 */
@HiltAndroidApp
class NexusFetchApp : Application() {

    @Inject lateinit var workDirCleaner: WorkDirCleaner
    @Inject lateinit var youtubeDlEngine: YoutubeDlEngine
    @Inject lateinit var nexusLogger: NexusLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                nexusLogger.error("crash", "uncaught", "Crash não tratado em ${thread.name}", throwable)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        appScope.launch(Dispatchers.IO) {
            workDirCleaner.cleanupOrphanedWork()
            runCatching {
                youtubeDlEngine.ensureInitialized()
                youtubeDlEngine.autoUpdateIfNeeded(force = false)
            }
        }
    }
}
