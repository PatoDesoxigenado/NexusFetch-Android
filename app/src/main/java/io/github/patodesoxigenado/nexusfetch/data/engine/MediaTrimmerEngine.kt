package io.github.patodesoxigenado.nexusfetch.data.engine

import android.net.Uri
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.UriFileResolver
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimRequest
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import io.github.patodesoxigenado.nexusfetch.domain.policy.TrimRangeValidator
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes Fast Trim for remote links (via `yt-dlp --download-sections` without downloading full videos)
 * and local files (instant `-c copy` stream copy with silent re-encode fallback).
 */
@Singleton
class MediaTrimmerEngine @Inject constructor(
    private val youtubeDlEngine: YoutubeDlEngine,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val uriResolver: UriFileResolver,
    private val publisher: MediaStorePublisher,
    private val logger: NexusLogger,
) {
    suspend fun trim(
        request: TrimRequest,
        workDir: File,
        processId: String,
        onProgress: (overallPct: Float, statusMsg: String) -> Unit,
    ): PublishedMedia {
        val info = request.sourceInfo
        val validation = TrimRangeValidator.validateTrimRange(request.startSec, request.endSec, info.durationSec)
        if (!validation.isValid) {
            throw NexusError.ValidationError(validation.errorMessage)
        }

        youtubeDlEngine.ensureInitialized()
        val slug = "${TrimRangeValidator.formatTimestampSlug(request.startSec)}-${TrimRangeValidator.formatTimestampSlug(request.endSec)}"
        val safeTitle = MediaSanitizer.sanitizeFilename(info.title.ifBlank { "clip" }).take(80)
        val outTitle = "${safeTitle}_clip_$slug"
        val clipDuration = request.endSec - request.startSec

        val generatedFile: File = if (info.sourceType == TrimSourceType.URL) {
            onProgress(5f, "Conectando e baixando apenas o trecho selecionado...")
            youtubeDlEngine.downloadRemoteTrim(
                url = info.source,
                startSec = request.startSec,
                endSec = request.endSec,
                audioOnly = request.audioOnly,
                maxHeight = request.maxHeight,
                workDir = workDir,
                processId = processId,
            ) { frac, _, _ ->
                onProgress((5f + frac * 90f).coerceIn(5f, 96f), "Baixando e recortando trecho...")
            }
        } else {
            val resolved = uriResolver.copyToWorkDir(Uri.parse(info.source), workDir, "trim_in")
            val outExt = if (request.audioOnly) ".mp3" else info.fileExt.ifBlank { ".mp4" }
            val tempOut = File(workDir, "${outTitle}$outExt")
            trimLocalWithSilentFallback(
                inputFile = resolved.file,
                outputFile = tempOut,
                startSec = request.startSec,
                durationSec = clipDuration,
                audioOnly = request.audioOnly,
            ) { frac ->
                onProgress((frac * 95f).coerceIn(2f, 96f), "Recortando trecho selecionado...")
            }
            resolved.file.delete()
            tempOut
        }

        onProgress(98f, "Publicando arquivo na galeria...")
        val mediaType = if (request.audioOnly || !info.isVideo) MediaType.AUDIO else MediaType.VIDEO
        val published = publisher.publishFile(
            sourceFile = generatedFile,
            desiredBaseName = outTitle,
            module = JobModule.TRIMMER,
            mediaType = mediaType,
            durationSec = clipDuration.toInt().coerceAtLeast(1),
        )
        generatedFile.delete()
        onProgress(100f, "Recorte concluído!")
        return published
    }

    suspend fun convertMp4ToGif(mp4File: File, gifFile: File) {
        youtubeDlEngine.ensureInitialized()
        val args = listOf(
            "-y",
            "-i", mp4File.absolutePath,
            "-vf", "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
            gifFile.absolutePath,
        )
        ffmpegExecutor.runFfmpeg(args, durationSec = 15.0)
    }

    private suspend fun trimLocalWithSilentFallback(
        inputFile: File,
        outputFile: File,
        startSec: Double,
        durationSec: Double,
        audioOnly: Boolean,
        onFraction: (Float) -> Unit,
    ) {
        val fastCmd = buildLocalFastTrimCommand(
            inputPath = inputFile.absolutePath,
            outputPath = outputFile.absolutePath,
            startSec = startSec,
            durationSec = durationSec,
            audioOnly = audioOnly,
        )
        val fastResult = runCatching {
            ffmpegExecutor.runFfmpeg(fastCmd, durationSec) { frac, _ -> onFraction(frac) }
            outputFile.exists() && outputFile.length() > 0L
        }.getOrDefault(false)

        if (fastResult) return

        // Silent fallback: re-encode precisely with libx264/aac when stream copy fails
        logger.warn("trimmer", "fallback_reencode", "Stream copy falhou; executando fallback preciso")
        if (outputFile.exists()) outputFile.delete()
        val preciseOut = if (!audioOnly && !outputFile.name.endsWith(".mp4", ignoreCase = true)) {
            File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.mp4")
        } else {
            outputFile
        }
        val preciseCmd = buildLocalPreciseTrimCommand(
            inputPath = inputFile.absolutePath,
            outputPath = preciseOut.absolutePath,
            startSec = startSec,
            durationSec = durationSec,
        )
        ffmpegExecutor.runFfmpeg(preciseCmd, durationSec) { frac, _ -> onFraction(frac) }
        if (preciseOut != outputFile && preciseOut.exists()) {
            preciseOut.copyTo(outputFile, overwrite = true)
            preciseOut.delete()
        }
    }

    fun buildLocalFastTrimCommand(
        inputPath: String,
        outputPath: String,
        startSec: Double,
        durationSec: Double,
        audioOnly: Boolean,
    ): List<String> {
        val cmd = mutableListOf(
            "-y",
            "-hide_banner",
            "-ss", String.format(Locale.US, "%.3f", startSec),
            "-i", inputPath,
            "-t", String.format(Locale.US, "%.3f", durationSec),
        )
        if (audioOnly) {
            if (outputPath.endsWith(".mp3", ignoreCase = true)) {
                cmd.addAll(listOf("-vn", "-c:a", "libmp3lame", "-b:a", "320k"))
            } else {
                cmd.addAll(listOf("-vn", "-c:a", "copy"))
            }
        } else {
            cmd.addAll(listOf("-c", "copy", "-avoid_negative_ts", "make_zero"))
            if (outputPath.endsWith(".mp4", ignoreCase = true)) {
                cmd.addAll(listOf("-movflags", "+faststart"))
            }
        }
        cmd.add(outputPath)
        return cmd
    }

    private fun buildLocalPreciseTrimCommand(
        inputPath: String,
        outputPath: String,
        startSec: Double,
        durationSec: Double,
    ): List<String> = listOf(
        "-y",
        "-hide_banner",
        "-ss", String.format(Locale.US, "%.3f", startSec),
        "-i", inputPath,
        "-t", String.format(Locale.US, "%.3f", durationSec),
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "20",
        "-threads", "0",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "192k",
        "-movflags", "+faststart",
        outputPath,
    )
}
