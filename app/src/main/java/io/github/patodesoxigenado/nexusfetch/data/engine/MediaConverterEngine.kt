package io.github.patodesoxigenado.nexusfetch.data.engine

import android.net.Uri
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.UriFileResolver
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.ConversionSpec
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Mp4Codec
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import io.github.patodesoxigenado.nexusfetch.domain.model.ResolutionPreset
import io.github.patodesoxigenado.nexusfetch.domain.policy.AggregateProgressCalculator
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch media converter supporting MP4/MKV/AVI/MOV/WEBM (up to 4K + H264/HEVC/AV1/VP9)
 * and audio extraction/transcoding (MP3 320k, WAV, FLAC, M4A, OGG, OPUS).
 */
@Singleton
class MediaConverterEngine @Inject constructor(
    private val uriResolver: UriFileResolver,
    private val media3Transcoder: Media3Transcoder,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val youtubeDlEngine: YoutubeDlEngine,
    private val publisher: MediaStorePublisher,
    private val logger: NexusLogger,
) {
    suspend fun convertBatch(
        files: List<MediaFileInfo>,
        spec: ConversionSpec,
        workDir: File,
        onProgress: (overallPct: Float, speedText: String, statusMsg: String) -> Unit,
    ): List<PublishedMedia> {
        if (files.isEmpty()) throw NexusError.ValidationError("Selecione pelo menos um arquivo para converter.")
        youtubeDlEngine.ensureInitialized()

        val outputs = mutableListOf<PublishedMedia>()
        val total = files.size
        val targetExt = spec.targetExtension.lowercase().trimStart('.')

        files.forEachIndexed { index, fileInfo ->
            if (!spec.isAudioTarget && !fileInfo.isVideo) {
                throw NexusError.ValidationError(
                    "O arquivo ${fileInfo.name} é apenas áudio. Selecione 'Áudio / Extrair MP3' para converter áudios.",
                )
            }

            val baseName = MediaSanitizer.sanitizeFilename(fileInfo.name.substringBeforeLast('.'))
            onProgress(
                AggregateProgressCalculator.calculateOverallPercent(index, 0.01f, total),
                "",
                "[${index + 1}/$total] Preparando: ${fileInfo.name.take(26)}",
            )
            val resolved = uriResolver.copyToWorkDir(Uri.parse(fileInfo.uriString), workDir, "conv_$index") { copyFrac ->
                val overall = AggregateProgressCalculator.calculateOverallPercent(index, copyFrac * 0.10f, total)
                onProgress(overall, "", "[${index + 1}/$total] Copiando: ${fileInfo.name.take(26)}")
            }
            val tempOut = File(workDir, "${baseName}_converted.$targetExt")

            try {
                convertSingleItem(resolved.file, tempOut, fileInfo, spec, targetExt, index, total, onProgress)
                val published = publisher.publishFile(
                    sourceFile = tempOut,
                    desiredBaseName = baseName,
                    module = JobModule.CONVERTER,
                    mediaType = if (spec.isAudioTarget) MediaType.AUDIO else MediaType.VIDEO,
                    durationSec = fileInfo.durationSec.toInt().takeIf { it > 0 },
                )
                outputs.add(published)
            } finally {
                resolved.file.delete()
                tempOut.delete()
            }
        }

        onProgress(100f, "", "Conversão concluída!")
        return outputs
    }

    private suspend fun convertSingleItem(
        inputFile: File,
        tempOut: File,
        fileInfo: MediaFileInfo,
        spec: ConversionSpec,
        targetExt: String,
        index: Int,
        total: Int,
        onProgress: (overallPct: Float, speedText: String, statusMsg: String) -> Unit,
    ) {
        if (canRemuxLosslessly(fileInfo, spec)) {
            val remuxed = tryRemuxLosslessly(inputFile, tempOut, fileInfo, targetExt, index, total, onProgress)
            if (remuxed) return
            if (tempOut.exists()) tempOut.delete()
        }

        val supportsMedia3 = targetExt == "mp4" && (spec.mp4Codec == Mp4Codec.H264 || spec.mp4Codec == Mp4Codec.HEVC) ||
            targetExt == "mkv" ||
            targetExt == "mov"
        val eligibleForMedia3 = fileInfo.isVideo && !spec.isAudioTarget && supportsMedia3

        val usedHw = if (eligibleForMedia3) {
            tryHardwareTranscode(inputFile, tempOut, fileInfo, spec, targetExt, index, total, onProgress)
        } else {
            false
        }

        if (!usedHw) {
            if (tempOut.exists()) tempOut.delete()
            if (eligibleForMedia3) {
                logger.warn("converter", "fallback_cpu", "Fallback FFmpeg para ${fileInfo.name}")
            }
            val args = buildFfmpegArgs(inputFile.absolutePath, tempOut.absolutePath, spec)
            ffmpegExecutor.runFfmpeg(args, fileInfo.durationSec) { frac, speed ->
                val scaled = 0.10f + frac * 0.88f
                val overall = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
                onProgress(overall, speed, "[${index + 1}/$total] Convertendo: ${fileInfo.name.take(26)}")
            }
        }
    }

    private suspend fun tryRemuxLosslessly(
        inputFile: File,
        tempOut: File,
        fileInfo: MediaFileInfo,
        targetExt: String,
        index: Int,
        total: Int,
        onProgress: (overallPct: Float, speedText: String, statusMsg: String) -> Unit,
    ): Boolean = try {
        val args = buildRemuxArgs(inputFile.absolutePath, tempOut.absolutePath, targetExt)
        ffmpegExecutor.runFfmpeg(args, fileInfo.durationSec) { frac, speed ->
            val scaled = 0.10f + frac * 0.88f
            val overall = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
            onProgress(overall, speed, "[${index + 1}/$total] Remux sem perda: ${fileInfo.name.take(26)}")
        }
        val ok = tempOut.exists() && tempOut.length() > 0L
        if (ok) {
            logger.info("converter", "remux_success", "Remux -c copy concluiu ${fileInfo.name} -> $targetExt")
        }
        ok
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        logger.warn("converter", "remux_fallback", "Remux falhou para ${fileInfo.name}: ${t.message}")
        false
    }

    private suspend fun tryHardwareTranscode(
        inputFile: File,
        tempOut: File,
        fileInfo: MediaFileInfo,
        spec: ConversionSpec,
        targetExt: String,
        index: Int,
        total: Int,
        onProgress: (overallPct: Float, speedText: String, statusMsg: String) -> Unit,
    ): Boolean {
        val hwOut = if (targetExt == "mp4") tempOut else File(tempOut.parentFile, "${tempOut.nameWithoutExtension}_hw.mp4")
        return try {
            val useHevc = targetExt == "mp4" && spec.mp4Codec == Mp4Codec.HEVC
            media3Transcoder.transcodeVideo(
                inputFile = inputFile,
                outputFile = hwOut,
                scaleHeight = spec.resolution.targetHeight,
                useHevc = useHevc,
            ) { frac ->
                val scaled = 0.10f + frac * if (targetExt == "mp4") 0.88f else 0.80f
                val overall = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
                onProgress(overall, "HW", "[${index + 1}/$total] Convertendo (Hardware): ${fileInfo.name.take(26)}")
            }
            if (!hwOut.exists() || hwOut.length() <= 0L) return false
            if (targetExt != "mp4") {
                val remuxArgs = buildRemuxArgs(hwOut.absolutePath, tempOut.absolutePath, targetExt)
                ffmpegExecutor.runFfmpeg(remuxArgs, fileInfo.durationSec) { frac, _ ->
                    val scaled = 0.90f + frac * 0.08f
                    val overall = AggregateProgressCalculator.calculateOverallPercent(index, scaled, total)
                    onProgress(overall, "HW", "[${index + 1}/$total] Empacotando ${targetExt.uppercase()}...")
                }
            }
            val ok = tempOut.exists() && tempOut.length() > 0L
            if (ok) logger.info("converter", "hw_success", "Media3 concluiu ${fileInfo.name} -> $targetExt", encoder = "MediaCodec")
            ok
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            logger.warn("converter", "hw_fallback", "Media3 falhou para ${fileInfo.name}: ${t.message}")
            false
        } finally {
            if (hwOut != tempOut && hwOut.exists()) hwOut.delete()
        }
    }

    private fun buildRemuxArgs(inputPath: String, outputPath: String, ext: String): List<String> =
        buildList {
            addAll(listOf("-y", "-nostdin", "-progress", "pipe:1", "-i", inputPath, "-c", "copy"))
            if (ext == "mp4" || ext == "mov") {
                addAll(listOf("-movflags", "+faststart"))
            }
            add(outputPath)
        }

    fun buildFfmpegArgs(
        inputPath: String,
        outputPath: String,
        spec: ConversionSpec,
    ): List<String> {
        val ext = spec.targetExtension.lowercase().trimStart('.')
        val args = mutableListOf(
            "-y",
            "-nostdin",
            "-progress", "pipe:1",
            "-i", inputPath,
        )

        if (spec.isAudioTarget) {
            args.add("-vn")
            when (ext) {
                "mp3" -> args.addAll(listOf("-c:a", "libmp3lame", "-b:a", "320k"))
                "wav" -> args.addAll(listOf("-c:a", "pcm_s16le"))
                "flac" -> args.addAll(listOf("-c:a", "flac"))
                "m4a" -> args.addAll(listOf("-c:a", "aac", "-b:a", "320k"))
                "ogg" -> args.addAll(listOf("-c:a", "libvorbis", "-b:a", "320k"))
                "opus" -> args.addAll(listOf("-c:a", "libopus", "-b:a", "320k"))
                else -> args.addAll(listOf("-c:a", "libmp3lame", "-b:a", "320k"))
            }
        } else {
            when (ext) {
                "mp4" -> {
                    args.addAll(mp4VideoCodecArgs(spec.mp4Codec))
                    args.addAll(listOf("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"))
                }
                "mkv" -> {
                    args.addAll(mp4VideoCodecArgs(Mp4Codec.H264))
                    args.addAll(listOf("-c:a", "aac", "-b:a", "192k"))
                }
                "mov" -> {
                    args.addAll(mp4VideoCodecArgs(Mp4Codec.H264))
                    args.addAll(listOf("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"))
                }
                "avi" -> args.addAll(listOf("-c:v", "mpeg4", "-q:v", "3", "-c:a", "libmp3lame", "-b:a", "192k"))
                "webm" -> args.addAll(
                    listOf(
                        "-c:v", "libvpx-vp9", "-crf", "30", "-b:v", "0",
                        "-row-mt", "1", "-speed", "3", "-c:a", "libopus", "-b:a", "128k",
                    ),
                )
                else -> {
                    args.addAll(mp4VideoCodecArgs(Mp4Codec.H264))
                    args.addAll(listOf("-c:a", "aac", "-b:a", "192k"))
                }
            }

            spec.resolution.targetHeight?.let { h ->
                args.addAll(listOf("-vf", "scale=-2:$h"))
            }
        }

        args.addAll(listOf("-threads", "0", outputPath))
        return args
    }

    private fun mp4VideoCodecArgs(codec: Mp4Codec): List<String> =
        when (codec) {
            Mp4Codec.H264 -> listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p")
            Mp4Codec.HEVC -> listOf("-c:v", "libx265", "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p")
            Mp4Codec.AV1 -> listOf("-c:v", "libaom-av1", "-crf", "28", "-b:v", "0", "-cpu-used", "6", "-pix_fmt", "yuv420p")
            Mp4Codec.VP9 -> listOf("-c:v", "libvpx-vp9", "-crf", "30", "-b:v", "0", "-row-mt", "1", "-speed", "4", "-pix_fmt", "yuv420p")
        }

    companion object {
        fun canRemuxLosslessly(fileInfo: MediaFileInfo, spec: ConversionSpec): Boolean {
            if (spec.isAudioTarget || !fileInfo.isVideo || spec.resolution != ResolutionPreset.ORIGINAL) {
                return false
            }
            val targetExt = spec.targetExtension.lowercase().trimStart('.')
            val srcExt = fileInfo.ext.lowercase().trimStart('.')
            val vcodec = fileInfo.vcodec?.lowercase()
            val acodec = fileInfo.acodec?.lowercase()
            return when (targetExt) {
                "mkv" -> true
                "mov" -> vcodec in setOf("h264", "hevc") && (acodec == null || acodec in setOf("aac", "mp3"))
                "mp4" -> srcExt != "mp4" &&
                    vcodec == spec.mp4Codec.key &&
                    (acodec == null || acodec in setOf("aac", "mp3", "opus"))
                else -> false
            }
        }
    }
}
