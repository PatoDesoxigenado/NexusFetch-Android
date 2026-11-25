package io.github.patodesoxigenado.nexusfetch.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdatePolicyTest {

    private val validSha = "a".repeat(64)
    private val validShaAlt = "b".repeat(64)
    private val baseUrl = "https://raw.githubusercontent.com/PatoDesoxigenado/NexusFetch-Android/main"

    private fun sampleManifest(
        versionCode: Int = 2,
        versionName: String = "1.1.0",
        releasePath: String = "apk/NexusFetch.apk",
        releaseSha: String = validSha,
    ): ApkFolderManifest = ApkFolderManifest(
        versionCode = versionCode,
        versionName = versionName,
        updatedAt = "2026-11-25T15:00:00Z",
        releaseNotes = "Melhorias de estabilidade.",
        release = ApkManifestArtifact(
            fileName = "NexusFetch.apk",
            path = releasePath,
            sha256 = releaseSha,
            sizeBytes = 56_000_000L,
            versionCode = versionCode,
            versionName = versionName,
        ),
        debug = mapOf(
            "arm64-v8a" to ApkManifestArtifact(
                fileName = "NexusFetch_arm64-v8a-debug.apk",
                path = "apk/debug/NexusFetch_arm64-v8a-debug.apk",
                sha256 = validSha,
                sizeBytes = 77_000_000L,
                versionCode = versionCode,
                versionName = "$versionName-debug",
            ),
            "armeabi-v7a" to ApkManifestArtifact(
                fileName = "NexusFetch_armeabi-v7a-debug.apk",
                path = "apk/debug/NexusFetch_armeabi-v7a-debug.apk",
                sha256 = validShaAlt,
                sizeBytes = 71_000_000L,
                versionCode = versionCode,
                versionName = "$versionName-debug",
            ),
        ),
    )

    @Test
    fun `resolves release update when versionCode or semver is newer and skips when up to date`() {
        val manifest = sampleManifest(versionCode = 2, versionName = "1.1.0")
        val update = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = false,
            installedVersionCode = 1,
            installedVersionName = "1.0.0",
            supportedAbis = listOf("arm64-v8a"),
            rawBaseUrl = baseUrl,
        )

        assertNotNull(update)
        assertEquals("Release", update?.channelLabel)
        assertEquals("NexusFetch.apk", update?.apkFileName)
        assertEquals("$baseUrl/apk/NexusFetch.apk", update?.apkDownloadUrl)
        assertEquals("v1.1.0", update?.versionTag)

        val upToDate = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = false,
            installedVersionCode = 2,
            installedVersionName = "1.1.0",
            supportedAbis = listOf("arm64-v8a"),
            rawBaseUrl = baseUrl,
        )
        assertNull(upToDate)
    }

    @Test
    fun `resolves ABI-matched debug update for arm64-v8a and armeabi-v7a`() {
        val manifest = sampleManifest(versionCode = 2, versionName = "1.1.0")

        val arm64Update = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = true,
            installedVersionCode = 1,
            installedVersionName = "1.0.0-debug",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            rawBaseUrl = baseUrl,
        )
        assertNotNull(arm64Update)
        assertEquals("Debug (arm64-v8a)", arm64Update?.channelLabel)
        assertEquals("NexusFetch_arm64-v8a-debug.apk", arm64Update?.apkFileName)
        assertEquals("$baseUrl/apk/debug/NexusFetch_arm64-v8a-debug.apk", arm64Update?.apkDownloadUrl)

        val armV7Update = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = true,
            installedVersionCode = 1,
            installedVersionName = "1.0.0-debug",
            supportedAbis = listOf("armeabi-v7a"),
            rawBaseUrl = baseUrl,
        )
        assertNotNull(armV7Update)
        assertEquals("Debug (armeabi-v7a)", armV7Update?.channelLabel)
        assertEquals("NexusFetch_armeabi-v7a-debug.apk", armV7Update?.apkFileName)
        assertEquals(validShaAlt, armV7Update?.expectedSha256)
    }

    @Test
    fun `does not trigger false positive when installed debug suffix matches manifest version`() {
        val manifest = sampleManifest(versionCode = 1, versionName = "1.0.0")
        val sameDebug = AppUpdatePolicy.resolveUpdateFromManifest(
            manifest = manifest,
            isDebugBuild = true,
            installedVersionCode = 1,
            installedVersionName = "1.0.0-debug",
            supportedAbis = listOf("arm64-v8a"),
            rawBaseUrl = baseUrl,
        )
        assertNull(sameDebug)
    }

    @Test
    fun `rejects path traversal and malformed sha256 checksums`() {
        val badPath = sampleManifest(releasePath = "apk/../evil.apk")
        assertNull(
            AppUpdatePolicy.resolveUpdateFromManifest(
                manifest = badPath,
                isDebugBuild = false,
                installedVersionCode = 1,
                installedVersionName = "1.0.0",
                supportedAbis = listOf("arm64-v8a"),
                rawBaseUrl = baseUrl,
            ),
        )

        val outsideApk = sampleManifest(releasePath = "dist/NexusFetch.apk")
        assertNull(
            AppUpdatePolicy.resolveUpdateFromManifest(
                manifest = outsideApk,
                isDebugBuild = false,
                installedVersionCode = 1,
                installedVersionName = "1.0.0",
                supportedAbis = listOf("arm64-v8a"),
                rawBaseUrl = baseUrl,
            ),
        )

        val badSha = sampleManifest(releaseSha = "not-a-valid-sha")
        assertNull(
            AppUpdatePolicy.resolveUpdateFromManifest(
                manifest = badSha,
                isDebugBuild = false,
                installedVersionCode = 1,
                installedVersionName = "1.0.0",
                supportedAbis = listOf("arm64-v8a"),
                rawBaseUrl = baseUrl,
            ),
        )
    }

    @Test
    fun `respects 6 hour periodic check interval cooldown`() {
        val t0 = 1_700_000_000_000L
        assertTrue(AppUpdatePolicy.shouldRunPeriodicCheck(lastCheckEpochMs = 0L, nowEpochMs = t0))
        assertFalse(
            AppUpdatePolicy.shouldRunPeriodicCheck(
                lastCheckEpochMs = t0,
                nowEpochMs = t0 + AppUpdatePolicy.PERIODIC_INTERVAL_MS - 1_000L,
            ),
        )
        assertTrue(
            AppUpdatePolicy.shouldRunPeriodicCheck(
                lastCheckEpochMs = t0,
                nowEpochMs = t0 + AppUpdatePolicy.PERIODIC_INTERVAL_MS,
            ),
        )
    }
}
