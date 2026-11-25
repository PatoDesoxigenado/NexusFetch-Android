package io.github.patodesoxigenado.nexusfetch.data.engine

import android.net.Uri
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.UriFileResolver
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPlan
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import io.github.patodesoxigenado.nexusfetch.domain.policy.AggregateProgressCalculator
import io.github.patodesoxigenado.nexusfetch.domain.policy.CompressionPlanCalculator
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid batch media compressor: tries hardware Media3 Transformer first for battery/thermal efficiency,
 * validates strict output size <= preset limit, and silently falls back to 2-pass libx264 (or libmp3lame) via FFmpeg.
 */
@Singleton
class MediaCompressorEngine @Inject constructor(
    private val uriResolver: UriFileResolver,
    private val media3Transcoder: Media3Transcoder,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val youtubeDlEngine: YoutubeDlEngine,
    private val publisher: MediaStorePublisher,
    private val logger: NexusLogger,
) {
    suspend fun compressBatch(
        files: List<MediaFileInfo>,
        preset: CompressionPreset,
        workDir: File,
        onProgress: (overallPct: Float, statusMsg: String) -> Unit,
    ): List<PublishedMedia> {
        if (files.isEmpty()) throw NexusError.ValidationError("Nenhum arquivo selecionado para comprimir.")
        youtubeDlEngine.ensureInitialized()

        val outputs = mutableListOf<PublishedMedia>()
        val total = files.size

        files.forEachIndexed { index, fileInfo ->
            val plan = CompressionPlanCalculator.calculate(fileInfo, preset)
            val baseName = MediaSanitizer.sanitizeFilename(fileInfo.name.substringBeforeLast('.'))
            val outTitle = "${baseName}_${preset.tag}"

            if (plan.alreadyWithinLimit) {
                val pct = AggregateProgressCalculator.calculateOverallPercent(index + 1, 0f, total)
                onProgress(pct, "[${index + 1}/$total] ${fileInfo.name} já cabe no limite (${fileInfo.sizeStr}).")
                return@forEachIndexed
            }

            onProgress(
                AggregateProgressCalculator.calculateOverallPercent(index, 0.01f, total),
                "[${index + 1}/$total] Preparando: ${fileInfo.name.take(26)}",
            )
            val resolved = uriResolver.copyToWorkDir(Uri.parse(fileInfo.uriString), workDir, "comp_$index") { copyFrac ->
                val pct = AggregateProgressCalculator.calculateOverallPercent(index, copyFrac * 0.10f, total)
                onProgress(pct, "[${index + 1}/$total] Copiando arquivo: ${fileInfo.name.take(24)}")
            }
            val tempOut = File(workDir, "${outTitle}_out${plan.outputExt}")

            try {
                if (plan.isVideo) {
                    compressVideoHybrid(resolved.file, fileInfo, plan, tempOut) { itemFrac, stageLabel ->
                        val scaled = 0.10f + itemFrac * 0.88f
                        val pct = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
                        onProgress(pct, "[${index + 1}/$total] $stageLabel: ${fileInfo.name.take(26)}")
                    }
                } else {
                    compressAudio(resolved.file, fileInfo.durationSec, plan, tempOut) { itemFrac ->
                        val scaled = 0.10f + itemFrac * 0.88f
                        val pct = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
                        onProgress(pct, "[${index + 1}/$total] Comprimindo áudio (${plan.audioKbps} kbps)")
                    }
                }

                if (!tempOut.exists() || tempOut.length() <= 0L) {
                    throw NexusError.EncoderError("Arquivo comprimido vazio: ${fileInfo.name}")
                }

                val published = publisher.publishFile(
                    sourceFile = tempOut,
                    desiredBaseName = outTitle,
                    module = JobModule.COMPRESSOR,
                    mediaType = if (plan.isVideo) MediaType.VIDEO else MediaType.AUDIO,
                    durationSec = fileInfo.durationSec.toInt().takeIf { it > 0 },
                )
                outputs.add(published)
            } finally {
                resolved.file.delete()
                tempOut.delete()
            }
        }

        val doneMsg = if (outputs.isEmpty()) {
            "Todos os arquivos já cabem no limite (${preset.limitMb.toInt()} MB)."
        } else {
            "Compressão concluída!"
        }
        onProgress(100f, doneMsg)
        return outputs
    }

    private suspend fun compressVideoHybrid(
        inputFile: File,
        info: MediaFileInfo,
        plan: CompressionPlan,
        outputFile: File,
        onItemProgress: (fraction: Float, stageLabel: String) -> Unit,
    ) {
        val hwTargetKbps = (plan.videoKbps * HW_BITRATE_SAFETY_FACTOR).toInt().coerceAtLeast(MIN_VIDEO_KBPS)
        val hwSuccess = try {
            media3Transcoder.transcodeVideo(
                inputFile = inputFile,
                outputFile = outputFile,
                videoBitrateBps = hwTargetKbps * 1000,
                scaleHeight = plan.scaleHeight,
                targetFps = plan.targetFps,
                useHevc = false,
            ) { frac ->
                onItemProgress(frac, "Comprimindo (MediaCodec HW)")
            }
            val fitsLimit = outputFile.exists() && outputFile.length() in 1L..plan.limitBytes
            if (!fitsLimit && outputFile.exists()) {
                val actualStr = MediaSanitizer.formatBytes(outputFile.length())
                val limitStr = MediaSanitizer.formatBytes(plan.limitBytes)
                logger.warn("compressor", "hw_oversize", "Media3 gerou $actualStr (> limite $limitStr)")
            }
            fitsLimit
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logger.warn("compressor", "hw_fallback", "Media3 indisponível/falhou: ${t.message}")
            false
        }

        if (hwSuccess) {
            logger.info("compressor", "hw_success", "Media3 atingiu ${outputFile.length()} bytes", encoder = "MediaCodec")
            return
        }

        if (outputFile.exists()) outputFile.delete()
        logger.warn("compressor", "fallback_vbv", "Fallback rápido FFmpeg libx264 VBV", encoder = "libx264")
        runFfmpegConstrainedEncode(inputFile, info.durationSec, plan, outputFile, onItemProgress)
    }

    private suspend fun runFfmpegConstrainedEncode(
        inputFile: File,
        durationSec: Double,
        plan: CompressionPlan,
        outputFile: File,
        onItemProgress: (fraction: Float, stageLabel: String) -> Unit,
    ) {
        val vfParts = buildList {
            plan.scaleHeight?.let { add("scale=-2:$it") }
            plan.targetFps?.let { add("fps=$it") }
        }
        val vfArgs = if (vfParts.isNotEmpty()) listOf("-vf", vfParts.joinToString(",")) else emptyList()

        val initialCmd = buildVbvCommand(
            inputFile = inputFile,
            outputFile = outputFile,
            videoKbps = plan.videoKbps,
            audioKbps = plan.audioKbps,
            preset = "veryfast",
            vfArgs = vfArgs,
        )
        ffmpegExecutor.runFfmpeg(initialCmd, durationSec) { frac, _ ->
            onItemProgress(frac, "Comprimindo vídeo (CPU rápida)")
        }

        // Safety net: if 1-pass VBV overshot preset limit, run one tighter pass cleanly
        if (!outputFile.exists() || outputFile.length() > plan.limitBytes) {
            val tighterKbps = (plan.videoKbps * HW_BITRATE_SAFETY_FACTOR).toInt().coerceAtLeast(MIN_VIDEO_KBPS)
            logger.warn("compressor", "vbv_retry", "Ajustando bitrate para ${tighterKbps}k visando caber no limite")
            if (outputFile.exists()) outputFile.delete()
            val retryCmd = buildVbvCommand(
                inputFile = inputFile,
                outputFile = outputFile,
                videoKbps = tighterKbps,
                audioKbps = plan.audioKbps,
                preset = "superfast",
                vfArgs = vfArgs,
            )
            ffmpegExecutor.runFfmpeg(retryCmd, durationSec) { frac, _ ->
                onItemProgress(frac, "Ajustando tamanho final")
            }
        }
    }

    private fun buildVbvCommand(
        inputFile: File,
        outputFile: File,
        videoKbps: Int,
        audioKbps: Int,
        preset: String,
        vfArgs: List<String>,
    ): List<String> =
        listOf(
            "-y",
            "-nostdin",
            "-progress", "pipe:1",
            "-i", inputFile.absolutePath,
            "-c:v", "libx264",
            "-b:v", "${videoKbps}k",
            "-maxrate", "${(videoKbps * VBV_MAXRATE_FACTOR).toInt()}k",
            "-bufsize", "${videoKbps * 2}k",
            "-preset", preset,
            "-threads", "0",
            "-pix_fmt", "yuv420p",
        ) + vfArgs + listOf(
            "-c:a", "aac",
            "-b:a", "${audioKbps}k",
            "-movflags", "+faststart",
            "-f", "mp4",
            outputFile.absolutePath,
        )

    private suspend fun compressAudio(
        inputFile: File,
        durationSec: Double,
        plan: CompressionPlan,
        outputFile: File,
        onItemProgress: (Float) -> Unit,
    ) {
        val args = listOf(
            "-y",
            "-nostdin",
            "-progress", "pipe:1",
            "-i", inputFile.absolutePath,
            "-vn",
            "-c:a", "libmp3lame",
            "-b:a", "${plan.audioKbps}k",
            "-f", "mp3",
            outputFile.absolutePath,
        )
        ffmpegExecutor.runFfmpeg(args, durationSec) { frac, _ ->
            onItemProgress(frac)
        }
    }

    private companion object {
        const val HW_BITRATE_SAFETY_FACTOR = 0.82
        const val VBV_MAXRATE_FACTOR = 1.15
        const val MIN_VIDEO_KBPS = 100
    }
}
