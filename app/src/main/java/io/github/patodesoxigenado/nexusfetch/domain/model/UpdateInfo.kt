package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * App release or debug update metadata retrieved from the repository `apk/version.json` manifest
 * with strictly verified SHA-256 checksum.
 */
data class UpdateInfo(
    val versionTag: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val expectedSha256: String,
    val sizeBytes: Long,
    val versionCode: Int = 0,
    val channelLabel: String = "Release",
)
