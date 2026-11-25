package io.github.patodesoxigenado.nexusfetch.data.telemetry

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.BuildConfig
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class NexusLogRecord(
    val ts: String,
    val level: String,
    val module: String,
    val stage: String,
    val jobId: String? = null,
    val encoder: String? = null,
    val durationMs: Long? = null,
    val message: String,
)

/**
 * Local-only structured OpenTelemetry-aligned logger with automatic secret/path redaction and <= 2 MB rotation.
 */
@Singleton
class NexusLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private val json = Json { encodeDefaults = false }
    private val logsDir: File
        get() = File(context.cacheDir, "logs").apply { mkdirs() }
    private val primaryLogFile: File
        get() = File(logsDir, "nexus.log")
    private val backupLogFile: File
        get() = File(logsDir, "nexus.log.1")

    fun info(
        module: String,
        stage: String,
        message: String,
        jobId: String? = null,
        encoder: String? = null,
        durationMs: Long? = null,
    ) = write("INFO", module, stage, message, jobId, encoder, durationMs, null)

    fun warn(
        module: String,
        stage: String,
        message: String,
        jobId: String? = null,
        encoder: String? = null,
        durationMs: Long? = null,
    ) = write("WARN", module, stage, message, jobId, encoder, durationMs, null)

    fun error(
        module: String,
        stage: String,
        message: String,
        throwable: Throwable? = null,
        jobId: String? = null,
        encoder: String? = null,
        durationMs: Long? = null,
    ) = write("ERROR", module, stage, message, jobId, encoder, durationMs, throwable)

    fun readRecentLines(maxLines: Int = 150): List<String> = synchronized(lock) {
        if (!primaryLogFile.exists()) return emptyList()
        runCatching {
            primaryLogFile.readLines().takeLast(maxLines)
        }.getOrDefault(emptyList())
    }

    fun clearLogs() = synchronized(lock) {
        runCatching { primaryLogFile.delete() }
        runCatching { backupLogFile.delete() }
    }

    fun getExportUri(): Uri? = synchronized(lock) {
        if (!primaryLogFile.exists() || primaryLogFile.length() == 0L) return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", primaryLogFile)
    }

    private fun write(
        level: String,
        module: String,
        stage: String,
        message: String,
        jobId: String?,
        encoder: String?,
        durationMs: Long?,
        throwable: Throwable?,
    ) {
        val rawMsg = if (throwable != null) {
            val cause = throwable.cause
            val causeSuffix = if (cause != null) {
                " (causa: ${cause::class.simpleName}: ${cause.message.orEmpty()})"
            } else {
                ""
            }
            "$message | ${throwable::class.simpleName}: ${throwable.message.orEmpty()}$causeSuffix"
        } else {
            message
        }
        val cleanMsg = MediaSanitizer.redactSensitiveText(rawMsg)
        val record = NexusLogRecord(
            ts = Instant.now().toString(),
            level = level,
            module = module,
            stage = stage,
            jobId = jobId,
            encoder = encoder,
            durationMs = durationMs,
            message = cleanMsg,
        )

        if (BuildConfig.DEBUG) {
            when (level) {
                "ERROR" -> Log.e("NexusFetch/$module", "[$stage] $cleanMsg")
                "WARN" -> Log.w("NexusFetch/$module", "[$stage] $cleanMsg")
                else -> Log.i("NexusFetch/$module", "[$stage] $cleanMsg")
            }
        }

        val line = json.encodeToString(record) + "\n"
        synchronized(lock) {
            runCatching {
                rotateIfNeeded()
                primaryLogFile.appendText(line)
            }
        }
    }

    private fun rotateIfNeeded() {
        if (primaryLogFile.exists() && primaryLogFile.length() >= MAX_LOG_BYTES) {
            if (backupLogFile.exists()) backupLogFile.delete()
            primaryLogFile.renameTo(backupLogFile)
        }
    }

    private companion object {
        const val MAX_LOG_BYTES = 2L * 1024L * 1024L
    }
}
