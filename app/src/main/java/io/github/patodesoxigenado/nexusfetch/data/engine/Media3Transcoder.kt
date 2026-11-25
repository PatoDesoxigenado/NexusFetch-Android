package io.github.patodesoxigenado.nexusfetch.data.engine

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Hardware-accelerated MediaCodec transcoder backed by AndroidX Media3 Transformer.
 * Always paired with automatic FFmpeg software fallback when a SoC encoder rejects a profile or exceeds size limits.
 */
@Singleton
class Media3Transcoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: NexusLogger,
) {
    fun hasHardwareEncoder(mimeType: String = MimeTypes.VIDEO_H264): Boolean =
        runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)

    @OptIn(UnstableApi::class)
    suspend fun transcodeVideo(
        inputFile: File,
        outputFile: File,
        videoBitrateBps: Int? = null,
        scaleHeight: Int? = null,
        targetFps: Int? = null,
        useHevc: Boolean = false,
        onProgress: ((Float) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.Main.immediate) {
        val targetMime = if (useHevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
        if (!hasHardwareEncoder(targetMime)) {
            throw NexusError.EncoderError("Encoder hardware $targetMime indisponível neste aparelho.")
        }

        if (outputFile.exists()) outputFile.delete()
        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        suspendCancellableCoroutine { continuation ->
            val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
            if (videoBitrateBps != null && videoBitrateBps > 0) {
                val videoSettings = VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrateBps)
                    .build()
                encoderFactoryBuilder.setRequestedVideoEncoderSettings(videoSettings)
            }

            val videoEffects = mutableListOf<Effect>()
            if (scaleHeight != null && scaleHeight > 0) {
                videoEffects.add(Presentation.createForHeight(scaleHeight))
            }
            if (targetFps != null && targetFps > 0) {
                videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat()))
            }

            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            var progressRunnable: Runnable? = null
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(targetMime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactoryBuilder.build())
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressRunnable?.let { handler.removeCallbacks(it) }
                            onProgress?.invoke(1f)
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            progressRunnable?.let { handler.removeCallbacks(it) }
                            logger.warn("media3", "transcode", "Media3 falhou: ${exportException.message}")
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    NexusError.EncoderError(
                                        "MediaCodec falhou: ${exportException.message}",
                                        exportException,
                                    ),
                                )
                            }
                        }
                    },
                )
                .build()

            val poller = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val stateResult = runCatching { transformer.getProgress(progressHolder) }
                    if (stateResult.isFailure) {
                        val msg = stateResult.exceptionOrNull()?.message
                        logger.warn("media3", "progress", "Polling Media3 interrompido: $msg")
                        return
                    }
                    if (stateResult.getOrNull() == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress?.invoke((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    handler.postDelayed(this, 250L)
                }
            }
            progressRunnable = poller

            continuation.invokeOnCancellation {
                handler.post {
                    progressRunnable?.let { handler.removeCallbacks(it) }
                    runCatching { transformer.cancel() }
                }
            }

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
                handler.postDelayed(poller, 250L)
            } catch (t: Throwable) {
                handler.removeCallbacks(poller)
                logger.warn("media3", "start_error", "Media3 start falhou: ${t.message}")
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        NexusError.EncoderError("MediaCodec indisponível: ${t.message}", t),
                    )
                }
            }
        }
    }
}
