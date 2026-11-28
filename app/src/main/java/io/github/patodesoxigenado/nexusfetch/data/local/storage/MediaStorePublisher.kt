package io.github.patodesoxigenado.nexusfetch.data.local.storage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Atomically publishes processed temp files into public Android MediaStore collections
 * (`Movies/NexusFetch`, `Music/NexusFetch`, `Pictures/NexusFetch`, `Download/NexusFetch`)
 * using `IS_PENDING = 1 -> 0` on API 29+ so files appear immediately in Gallery, WhatsApp, and Discord.
 */
@Singleton
class MediaStorePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun publishFile(
        sourceFile: File,
        desiredBaseName: String,
        module: JobModule,
        mediaType: MediaType,
        durationSec: Int? = null,
        subFolder: String? = null,
    ): PublishedMedia = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            throw NexusError.StorageError("Arquivo gerado está vazio ou ausente.")
        }

        val ext = sourceFile.extension.lowercase().ifBlank { defaultExtension(mediaType) }
        val mimeType = resolveMimeType(ext, mediaType)
        val safeBase = MediaSanitizer.sanitizeFilename(desiredBaseName)
        val finalFileName = "$safeBase.$ext"

        val publishedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScopedApi29Plus(sourceFile, finalFileName, mimeType, mediaType, subFolder)
        } else {
            publishLegacyApi26To28(sourceFile, finalFileName, mimeType, subFolder)
        }

        PublishedMedia(
            uriString = publishedUri.toString(),
            fileName = finalFileName,
            mimeType = mimeType,
            sizeBytes = sourceFile.length(),
            module = module,
            mediaType = mediaType,
            durationSec = durationSec,
            title = desiredBaseName,
        )
    }

    suspend fun publishLogFile(
        sourceFile: File,
        desiredBaseName: String,
    ): PublishedMedia = publishFile(
        sourceFile = sourceFile,
        desiredBaseName = desiredBaseName,
        module = JobModule.DOWNLOADER,
        mediaType = MediaType.VIDEO,
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publishScopedApi29Plus(
        sourceFile: File,
        finalFileName: String,
        mimeType: String,
        mediaType: MediaType,
        subFolder: String?,
    ): Uri {
        val resolver = context.contentResolver
        val itemUri = insertWithDownloadsFallback(resolver, mediaType, finalFileName, mimeType, subFolder)
            ?: throw NexusError.StorageError("Falha ao criar entrada no MediaStore para $finalFileName")

        try {
            writePendingUriStream(resolver, itemUri, sourceFile)
            val completeValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, completeValues, null, null)
            return itemUri
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertWithDownloadsFallback(
        resolver: android.content.ContentResolver,
        mediaType: MediaType,
        finalFileName: String,
        mimeType: String,
        subFolder: String?,
    ): Uri? {
        val (collectionUri, rootDirName) = targetCollectionApi29(mediaType, mimeType)
        val cleanSub = subFolder?.let { MediaSanitizer.sanitizeFilename(it) }?.takeIf { it.isNotBlank() }
        val primaryPath = if (cleanSub != null) "$rootDirName/NexusFetch/$cleanSub" else "$rootDirName/NexusFetch"
        tryInsertEntry(resolver, collectionUri, finalFileName, mimeType, primaryPath)?.let { return it }

        val downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val downloadsPath = if (cleanSub != null) {
            "${Environment.DIRECTORY_DOWNLOADS}/NexusFetch/$cleanSub"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/NexusFetch"
        }
        return tryInsertEntry(resolver, downloadsUri, finalFileName, mimeType, downloadsPath)
            ?: tryInsertEntry(resolver, downloadsUri, finalFileName, "application/octet-stream", downloadsPath)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun tryInsertEntry(
        resolver: android.content.ContentResolver,
        collectionUri: Uri,
        finalFileName: String,
        mimeType: String,
        relativePath: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return runCatching { resolver.insert(collectionUri, values) }.getOrNull()
    }

    private suspend fun writePendingUriStream(
        resolver: android.content.ContentResolver,
        itemUri: Uri,
        sourceFile: File,
    ) {
        val outStream = resolver.openOutputStream(itemUri)
            ?: throw NexusError.StorageError("Não foi possível abrir fluxo de escrita no MediaStore.")
        outStream.use { out -> copyFileToStream(sourceFile, out) }
    }

    @Suppress("DEPRECATION")
    private suspend fun publishLegacyApi26To28(
        sourceFile: File,
        finalFileName: String,
        mimeType: String,
        subFolder: String?,
    ): Uri {
        val hasLegacyWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

        val publicBase = resolveLegacyBaseDir(hasLegacyWrite, mimeType)
        val targetDir = if (!subFolder.isNullOrBlank()) {
            File(publicBase, MediaSanitizer.sanitizeFilename(subFolder))
        } else {
            publicBase
        }.apply { mkdirs() }

        val destFile = resolveUniqueFile(targetDir, finalFileName)
        val tmpDest = File(targetDir, ".tmp_${System.nanoTime()}_${destFile.name}")
        try {
            FileOutputStream(tmpDest).use { out -> copyFileToStream(sourceFile, out) }
            if (!tmpDest.renameTo(destFile)) {
                tmpDest.copyTo(destFile, overwrite = true)
                tmpDest.delete()
            }
        } catch (t: Throwable) {
            tmpDest.delete()
            throw t
        }

        return if (hasLegacyWrite) {
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(destFile)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacyBaseDir(hasLegacyWrite: Boolean, mimeType: String): File {
        if (!hasLegacyWrite) return File(context.cacheDir, "exports")
        val envType = when {
            mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        return File(Environment.getExternalStoragePublicDirectory(envType), "NexusFetch")
    }

    private suspend fun copyFileToStream(sourceFile: File, out: OutputStream) {
        FileInputStream(sourceFile).use { input ->
            val buffer = ByteArray(IO_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val bytes = input.read(buffer)
                if (bytes < 0) break
                out.write(buffer, 0, bytes)
            }
            out.flush()
        }
    }

    private fun resolveUniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var idx = 2
        while (candidate.exists()) {
            val numbered = if (ext.isNotBlank()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, numbered)
            idx++
        }
        return candidate
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun targetCollectionApi29(mediaType: MediaType, mimeType: String): Pair<Uri, String> =
        when {
            mimeType.startsWith("video/") ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio/") ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            mimeType.startsWith("image/") || mediaType == MediaType.GIF ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            else ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }

    private fun defaultExtension(mediaType: MediaType): String =
        when (mediaType) {
            MediaType.AUDIO -> "mp3"
            MediaType.IMAGE -> "jpg"
            MediaType.GIF -> "gif"
            else -> "mp4"
        }

    private fun resolveMimeType(ext: String, mediaType: MediaType): String =
        EXPLICIT_MIME_TYPES[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (mediaType) {
                MediaType.AUDIO -> "audio/mpeg"
                MediaType.IMAGE -> "image/jpeg"
                MediaType.GIF -> "image/gif"
                else -> "video/mp4"
            }

    private companion object {
        const val IO_BUFFER_SIZE = 256 * 1024
        val EXPLICIT_MIME_TYPES = mapOf(
            "mp4" to "video/mp4",
            "m4v" to "video/mp4",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "mov" to "video/quicktime",
            "avi" to "video/x-msvideo",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "wav" to "audio/wav",
            "flac" to "audio/flac",
            "ogg" to "audio/ogg",
            "opus" to "audio/opus",
            "gif" to "image/gif",
            "png" to "image/png",
            "webp" to "image/webp",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "log" to "text/plain",
            "txt" to "text/plain",
        )
    }
}
