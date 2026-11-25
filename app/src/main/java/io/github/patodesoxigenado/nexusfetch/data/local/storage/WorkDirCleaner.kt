package io.github.patodesoxigenado.nexusfetch.data.local.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-job temporary working directories and purges orphaned artifacts on startup.
 */
@Singleton
class WorkDirCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workRoot: File
        get() = File(context.cacheDir, "work").apply { mkdirs() }

    private val updatesRoot: File
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun createJobWorkDir(jobId: String): File {
        val safeId = MediaSanitizer.sanitizeFilename(jobId)
        return File(workRoot, safeId).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
    }

    fun deleteJobWorkDir(jobId: String) {
        val safeId = MediaSanitizer.sanitizeFilename(jobId)
        File(workRoot, safeId).deleteRecursively()
    }

    fun cleanupOrphanedWork() {
        workRoot.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        // Keep APK updates younger than 24h in case user returns from permission settings
        val cutoffMs = System.currentTimeMillis() - 24L * 3600L * 1000L
        updatesRoot.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffMs) {
                runCatching { file.deleteRecursively() }
            }
        }
    }
}
