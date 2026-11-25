package io.github.patodesoxigenado.nexusfetch.data.local.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ResolvedLocalInput(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

/**
 * Safely copies SAF `content://` URIs into a job's private work directory via chunked streaming.
 */
@Singleton
class UriFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "media_input"
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return displayName to size
    }

    suspend fun copyToWorkDir(
        uri: Uri,
        workDir: File,
        prefix: String,
        onProgress: ((Float) -> Unit)? = null,
    ): ResolvedLocalInput =
        withContext(Dispatchers.IO) {
            val (rawName, queriedSize) = queryDisplayNameAndSize(uri)
            val mimeType = context.contentResolver.getType(uri)
            val extFromName = rawName.substringAfterLast('.', "")
            val ext = if (extFromName.isNotBlank()) {
                ".${extFromName.lowercase()}"
            } else {
                val mapped = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (!mapped.isNullOrBlank()) ".$mapped" else ".mp4"
            }

            val baseName = MediaSanitizer.sanitizeFilename(rawName.substringBeforeLast('.'))
            val targetFile = File(workDir, "${prefix}_$baseName$ext")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw NexusError.StorageError("Não foi possível abrir o arquivo selecionado: $rawName")

            val copiedBytes = inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    var total = 0L
                    var lastReportMs = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        if (onProgress != null && queriedSize > 0L) {
                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= COPY_PROGRESS_INTERVAL_MS || total >= queriedSize) {
                                lastReportMs = now
                                onProgress((total.toFloat() / queriedSize.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.flush()
                    total
                }
            }

            if (!targetFile.exists() || copiedBytes <= 0L) {
                throw NexusError.StorageError("O arquivo selecionado está vazio ou ilegível: $rawName")
            }

            ResolvedLocalInput(
                file = targetFile,
                displayName = rawName,
                sizeBytes = if (queriedSize > 0L) queriedSize else copiedBytes,
                mimeType = mimeType,
            )
        }

    private companion object {
        const val IO_BUFFER_SIZE = 256 * 1024
        const val COPY_PROGRESS_INTERVAL_MS = 150L
    }
}
