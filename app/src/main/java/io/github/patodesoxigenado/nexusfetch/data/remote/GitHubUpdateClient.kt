package io.github.patodesoxigenado.nexusfetch.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.BuildConfig
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.UpdateInfo
import io.github.patodesoxigenado.nexusfetch.domain.policy.ApkFolderManifest
import io.github.patodesoxigenado.nexusfetch.domain.policy.AppUpdatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the repository `apk/version.json` manifest for newer Release or ABI-matched Debug APKs,
 * verifies SHA-256 strictly, and prepares a FileProvider installation Intent.
 */
@Singleton
class GitHubUpdateClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val directDownloader: DirectMediaDownloader,
    private val logger: NexusLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatestUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(APK_MANIFEST_URL)
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json")
            .header("User-Agent", "NexusFetch-Android/${BuildConfig.VERSION_NAME}")
            .build()

        val manifest = okHttpClient.newCall(request).execute().use { resp ->
            if (resp.code == HTTP_NOT_FOUND) {
                logger.info("updater", "check", "Manifesto apk/version.json ainda não publicado (HTTP 404)")
                return@withContext null
            }
            if (!resp.isSuccessful) {
                throw NexusError.NetworkError("Pasta apk/ retornou HTTP ${resp.code}.")
            }
            val body = resp.body?.string() ?: throw NexusError.NetworkError("Resposta vazia ao consultar apk/version.json.")
            json.decodeFromString<ApkFolderManifest>(body)
        }

        val resolved = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = BuildConfig.DEBUG,
            installedVersionCode = BuildConfig.VERSION_CODE,
            installedVersionName = BuildConfig.VERSION_NAME,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            rawBaseUrl = RAW_REPO_BASE_URL,
        )

        if (resolved != null) {
            logger.info(
                module = "updater",
                stage = "found",
                message = "Atualização disponível [${resolved.channelLabel}]: ${resolved.versionTag} (${resolved.apkFileName})",
            )
        }
        resolved
    }

    suspend fun downloadAndVerifyUpdate(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, updateInfo.apkFileName)
        if (apkFile.exists()) apkFile.delete()

        directDownloader.downloadToFile(updateInfo.apkDownloadUrl, apkFile) { downloaded, total ->
            if (total > 0L) {
                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }

        val actualHash = computeSha256(apkFile)
        if (!actualHash.equals(updateInfo.expectedSha256, ignoreCase = true)) {
            apkFile.delete()
            logger.error("updater", "verify", "SHA-256 mismatch para ${updateInfo.apkFileName}")
            throw NexusError.ChecksumMismatchError()
        }
        apkFile
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val RAW_REPO_BASE_URL =
            "https://raw.githubusercontent.com/PatoDesoxigenado/NexusFetch-Android/main"
        const val APK_MANIFEST_URL = "$RAW_REPO_BASE_URL/apk/version.json"
    }
}
