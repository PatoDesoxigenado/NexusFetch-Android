package io.github.patodesoxigenado.nexusfetch.data.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.patodesoxigenado.nexusfetch.data.remote.DirectMediaDownloader
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.policy.AudioCoverPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the highest-resolution YouTube/YT Music thumbnail, crops a 1:1 center square
 * (removing 4:3 letterbox bars if present), and embeds ID3v2.3 cover art into MP3 via FFmpeg.
 */
@Singleton
class AudioCoverEmbedder @Inject constructor(
    private val directDownloader: DirectMediaDownloader,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val logger: NexusLogger,
) {
    suspend fun tryEmbedYouTubeCover(
        mp3File: File,
        sourceUrl: String,
        fallbackThumbnailUrl: String?,
        workDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!mp3File.exists() || !mp3File.extension.equals("mp3", ignoreCase = true)) {
            return@withContext false
        }
        if (!ffmpegExecutor.isAvailable()) {
            return@withContext false
        }

        val candidates = AudioCoverPolicy.buildThumbnailCandidates(sourceUrl, fallbackThumbnailUrl)
        if (candidates.isEmpty()) return@withContext false

        val coverWorkDir = File(workDir, "cover_${System.nanoTime()}").apply { mkdirs() }
        var sourceBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        try {
            sourceBitmap = fetchBestBitmap(candidates, coverWorkDir) ?: return@withContext false
            val (topLuma, bottomLuma) = measure43BandsIfNeeded(sourceBitmap)
            val rect = AudioCoverPolicy.computeCoverCrop(
                width = sourceBitmap.width,
                height = sourceBitmap.height,
                topBandMeanLuma = topLuma,
                bottomBandMeanLuma = bottomLuma,
            )
            if (rect.width <= 0 || rect.height <= 0) return@withContext false

            croppedBitmap = Bitmap.createBitmap(sourceBitmap, rect.left, rect.top, rect.width, rect.height)
            val coverJpg = File(coverWorkDir, "cover_square.jpg")
            FileOutputStream(coverJpg).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            val tempOutMp3 = File(coverWorkDir, "tagged_${mp3File.name}")
            val args = AudioCoverPolicy.buildFfmpegEmbedArgs(
                mp3Path = mp3File.absolutePath,
                coverJpgPath = coverJpg.absolutePath,
                outputMp3Path = tempOutMp3.absolutePath,
            )
            ffmpegExecutor.runFfmpeg(args, durationSec = 0.0)
            if (!tempOutMp3.exists() || tempOutMp3.length() <= 0L) return@withContext false

            replaceAtomically(tempOutMp3, mp3File)
            logger.info("cover", "embed", "Capa 1:1 (${rect.width}x${rect.height}) embutida em ${mp3File.name}")
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logger.warn("cover", "embed_skip", "Não foi possível embutir capa no MP3: ${t.message}")
            false
        } finally {
            if (croppedBitmap != null && croppedBitmap !== sourceBitmap) {
                croppedBitmap.recycle()
            }
            sourceBitmap?.recycle()
            coverWorkDir.deleteRecursively()
        }
    }

    private suspend fun fetchBestBitmap(candidates: List<String>, dir: File): Bitmap? {
        var fallbackSmall: Bitmap? = null
        candidates.forEachIndexed { idx, candidateUrl ->
            val rawFile = File(dir, "thumb_$idx.img")
            val downloaded = try {
                directDownloader.downloadToFile(candidateUrl, rawFile)
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                false
            }
            if (downloaded) {
                val bmp = BitmapFactory.decodeFile(rawFile.absolutePath)
                rawFile.delete()
                if (bmp != null) {
                    if (bmp.width >= PREFERRED_MIN_DIM_PX && bmp.height >= PREFERRED_MIN_DIM_PX) {
                        fallbackSmall?.recycle()
                        return bmp
                    }
                    if (fallbackSmall == null && bmp.width >= FALLBACK_MIN_DIM_PX && bmp.height >= FALLBACK_MIN_DIM_PX) {
                        fallbackSmall = bmp
                    } else {
                        bmp.recycle()
                    }
                }
            }
        }
        return fallbackSmall
    }

    private fun measure43BandsIfNeeded(bitmap: Bitmap): Pair<Double?, Double?> {
        if (!AudioCoverPolicy.isApprox43Ratio(bitmap.width, bitmap.height)) {
            return null to null
        }
        val barH = AudioCoverPolicy.letterboxBandHeight(bitmap.height)
        if (barH * 2 >= bitmap.height) return null to null
        val topMean = computeBandMeanRgb(bitmap, startY = 0, bandHeight = barH)
        val bottomMean = computeBandMeanRgb(bitmap, startY = bitmap.height - barH, bandHeight = barH)
        return topMean to bottomMean
    }

    private fun computeBandMeanRgb(bitmap: Bitmap, startY: Int, bandHeight: Int): Double {
        val width = bitmap.width
        val pixels = IntArray(width * bandHeight)
        bitmap.getPixels(pixels, 0, width, 0, startY, width, bandHeight)
        if (pixels.isEmpty()) return 255.0
        var sum = 0.0
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            sum += (r + g + b) / 3.0
        }
        return sum / pixels.size
    }

    private fun replaceAtomically(source: File, target: File) {
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private companion object {
        const val PREFERRED_MIN_DIM_PX = 200
        const val FALLBACK_MIN_DIM_PX = 64
        const val JPEG_QUALITY = 95
    }
}
