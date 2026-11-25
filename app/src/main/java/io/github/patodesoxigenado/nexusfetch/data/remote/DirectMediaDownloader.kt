package io.github.patodesoxigenado.nexusfetch.data.remote

import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Streams remote images, direct media files, or APK updates to disk without buffering entire payloads in RAM.
 */
@Singleton
class DirectMediaDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun downloadToFile(
        url: String,
        destFile: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw NexusError.UnsupportedUrlError("Somente conexões HTTPS seguras são permitidas.")
        }

        destFile.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "*/*")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw NexusError.NetworkError("Servidor retornou HTTP ${response.code} ao baixar mídia.")
            }
            val body = response.body
                ?: throw NexusError.NetworkError("Resposta vazia do servidor.")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }
        }

        if (!destFile.exists() || destFile.length() <= 0L) {
            throw NexusError.StorageError("O arquivo baixado ficou vazio.")
        }
        destFile
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
