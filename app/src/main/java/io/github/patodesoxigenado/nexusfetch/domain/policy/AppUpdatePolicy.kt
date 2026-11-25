package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.UpdateInfo
import kotlinx.serialization.Serializable

@Serializable
data class ApkManifestArtifact(
    val fileName: String = "",
    val path: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val versionCode: Int? = null,
    val versionName: String? = null,
)

@Serializable
data class ApkFolderManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val updatedAt: String = "",
    val releaseNotes: String = "",
    val release: ApkManifestArtifact? = null,
    val debug: Map<String, ApkManifestArtifact> = emptyMap(),
)

/**
 * Pure Kotlin update resolution policy for APKs hosted in the repository root `apk/` directory,
 * supporting both Release (`apk/NexusFetch.apk`) and ABI-matched Debug (`NexusFetch_<abi>-debug.apk`) channels.
 */
object AppUpdatePolicy {

    const val PERIODIC_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L
    private const val SHA256_HEX_LENGTH = 64

    fun shouldRunPeriodicCheck(
        lastCheckEpochMs: Long,
        nowEpochMs: Long,
        intervalMs: Long = PERIODIC_INTERVAL_MS,
    ): Boolean {
        if (lastCheckEpochMs <= 0L) return true
        val elapsed = nowEpochMs - lastCheckEpochMs
        return elapsed < 0L || elapsed >= intervalMs
    }

    fun resolveUpdateFromManifest(
        manifest: ApkFolderManifest,
        isDebugBuild: Boolean,
        installedVersionCode: Int,
        installedVersionName: String,
        supportedAbis: List<String>,
        rawBaseUrl: String,
    ): UpdateInfo? {
        val (channelLabel, artifact) = selectArtifact(manifest, isDebugBuild, supportedAbis) ?: return null
        if (!isValidArtifactPath(artifact.path)) return null

        val cleanSha256 = artifact.sha256.trim().lowercase()
        if (!isValidSha256(cleanSha256)) return null

        val remoteCode = artifact.versionCode ?: manifest.versionCode
        val remoteName = (artifact.versionName ?: manifest.versionName).trim()
        val remoteSemVer = normalizeSemVer(remoteName)
        val localSemVer = normalizeSemVer(installedVersionName)

        val hasNewerCode = remoteCode > installedVersionCode
        val hasNewerSemVer = remoteSemVer.isNotEmpty() && isVersionNewer(remoteSemVer, localSemVer)
        if (!hasNewerCode && !hasNewerSemVer) return null

        val fileName = artifact.fileName.trim()
            .ifEmpty { artifact.path.substringAfterLast('/') }
        if (fileName.contains('/') || fileName.contains("..")) return null

        val tagBase = remoteName.ifEmpty { remoteCode.toString() }
        val cleanBase = rawBaseUrl.trimEnd('/')
        return UpdateInfo(
            versionTag = "v$tagBase",
            versionName = remoteName.ifEmpty { "v$remoteCode" },
            releaseNotes = manifest.releaseNotes,
            apkDownloadUrl = "$cleanBase/${artifact.path}",
            apkFileName = fileName,
            expectedSha256 = cleanSha256,
            sizeBytes = artifact.sizeBytes.coerceAtLeast(0L),
            versionCode = remoteCode,
            channelLabel = channelLabel,
        )
    }

    fun isVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val lParts = local.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun selectArtifact(
        manifest: ApkFolderManifest,
        isDebugBuild: Boolean,
        supportedAbis: List<String>,
    ): Pair<String, ApkManifestArtifact>? {
        if (!isDebugBuild) {
            return manifest.release?.let { "Release" to it }
        }
        for (abi in supportedAbis) {
            manifest.debug[abi]?.let { artifact ->
                return "Debug ($abi)" to artifact
            }
        }
        val fallback = manifest.debug.entries.firstOrNull() ?: return null
        return "Debug (${fallback.key})" to fallback.value
    }

    private fun normalizeSemVer(rawVersion: String): String =
        rawVersion.trim()
            .removePrefix("v")
            .substringBefore('-')
            .substringBefore('+')
            .trim()

    private fun isValidArtifactPath(path: String): Boolean =
        path.startsWith("apk/") &&
            path.endsWith(".apk", ignoreCase = true) &&
            !path.startsWith("/") &&
            !path.contains("..") &&
            !path.contains('\\') &&
            path.none { it.isWhitespace() }

    private fun isValidSha256(hash: String): Boolean =
        hash.length == SHA256_HEX_LENGTH &&
            hash.all { it in '0'..'9' || it in 'a'..'f' }
}
