package io.github.patodesoxigenado.nexusfetch.data.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Executes bundled `libffmpeg.so` and `libffprobe.so` safely via ProcessBuilder (`List<String>`),
 * reusing the binaries already packaged in `youtubedl-android:ffmpeg` without duplicating APKs/libs.
 */
@Singleton
class FfmpegProcessExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: NexusLogger,
) {
    private val timeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
    private val activeProcesses = ConcurrentHashMap.newKeySet<Process>()

    fun ffmpegExecutable(): File? = resolveNativeBinary("libffmpeg.so")

    fun ffprobeExecutable(): File? = resolveNativeBinary("libffprobe.so")

    fun isAvailable(): Boolean = ffmpegExecutable() != null

    fun destroyAllActiveProcesses() {
        for (process in activeProcesses) {
            runCatching { process.destroyForcibly() }
        }
        activeProcesses.clear()
    }

    suspend fun runFfmpeg(
        args: List<String>,
        durationSec: Double,
        onProcessCreated: ((Process) -> Unit)? = null,
        onProgress: ((fraction: Float, speed: String) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val ffmpeg = ffmpegExecutable()
            ?: throw NexusError.EncoderError("Binário FFmpeg não encontrado no pacote do app.")

        val cmd = listOf(ffmpeg.absolutePath) + args
        val pb = ProcessBuilder(cmd).apply {
            redirectErrorStream(true)
            configureNativeLibraryEnv(environment())
        }

        val process = pb.start()
        activeProcesses.add(process)
        onProcessCreated?.invoke(process)

        var currentSpeed = "--"
        val tailLines = ArrayDeque<String>(16)

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    coroutineContext.ensureActive()
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        if (tailLines.size >= 14) tailLines.removeFirst()
                        tailLines.addLast(trimmed)
                        currentSpeed = handleProgressLine(trimmed, durationSec, currentSpeed, onProgress)
                    }
                    line = reader.readLine()
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val reason = tailLines.lastOrNull() ?: "código $exitCode"
                logger.warn("ffmpeg", "exec", "FFmpeg saiu com $exitCode: $reason")
                throw NexusError.EncoderError("FFmpeg falhou ($reason)")
            }
        } catch (ce: CancellationException) {
            process.destroyForcibly()
            throw ce
        } catch (ne: NexusError) {
            process.destroyForcibly()
            throw ne
        } catch (t: Throwable) {
            process.destroyForcibly()
            throw NexusError.EncoderError("Erro ao executar FFmpeg: ${t.message}", t)
        } finally {
            activeProcesses.remove(process)
        }
    }

    private fun handleProgressLine(
        trimmed: String,
        durationSec: Double,
        currentSpeed: String,
        onProgress: ((fraction: Float, speed: String) -> Unit)?,
    ): String {
        when {
            trimmed.startsWith("speed=") -> return trimmed.substringAfter("speed=").trim()
            trimmed.startsWith("out_time_us=") -> {
                val us = trimmed.substringAfter("out_time_us=").trim().toLongOrNull()
                if (us != null && durationSec > 0.0) {
                    val pct = (us.toDouble() / (durationSec * 1_000_000.0)).toFloat().coerceIn(0f, 0.99f)
                    onProgress?.invoke(pct, currentSpeed)
                }
            }
            trimmed == "progress=end" -> onProgress?.invoke(1f, currentSpeed)
            durationSec > 0.0 -> {
                timeRegex.find(trimmed)?.let { match ->
                    val h = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    val m = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val s = match.groupValues[3].toDoubleOrNull() ?: 0.0
                    val currentSec = h * 3600.0 + m * 60.0 + s
                    val pct = (currentSec / durationSec).toFloat().coerceIn(0.01f, 0.99f)
                    onProgress?.invoke(pct, currentSpeed)
                }
            }
        }
        return currentSpeed
    }

    suspend fun runSimpleProbeJson(file: File): String? = withContext(Dispatchers.IO) {
        val ffprobe = ffprobeExecutable() ?: return@withContext null
        val cmd = listOf(
            ffprobe.absolutePath,
            "-v", "error",
            "-show_entries", "format=duration:stream=codec_name,codec_type,width,height",
            "-of", "json",
            file.absolutePath,
        )
        runCatching {
            val pb = ProcessBuilder(cmd).apply {
                redirectErrorStream(false)
                configureNativeLibraryEnv(environment())
            }
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code == 0 && out.isNotBlank()) out else null
        }.getOrNull()
    }

    private fun resolveNativeBinary(libName: String): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val direct = File(nativeDir, libName)
        if (direct.exists() && direct.canExecute()) return direct

        // Fallback search inside youtubedl-android extracted packages directory
        val searchRoots = listOfNotNull(
            File(context.noBackupFilesDir, "youtubedl-android"),
            File(context.filesDir, "youtubedl-android"),
        )
        for (root in searchRoots) {
            if (!root.exists()) continue
            val match = root.walkTopDown()
                .firstOrNull { it.isFile && (it.name == libName || it.name == libName.removePrefix("lib").removeSuffix(".so")) }
            if (match != null && match.canExecute()) return match
        }
        return direct.takeIf { it.exists() }
    }

    private fun configureNativeLibraryEnv(env: MutableMap<String, String>) {
        val paths = linkedSetOf(context.applicationInfo.nativeLibraryDir)
        val ytdlBase = File(context.noBackupFilesDir, "youtubedl-android")
        if (ytdlBase.exists()) {
            ytdlBase.walkTopDown()
                .filter { it.isDirectory && it.name == "lib" }
                .forEach { paths.add(it.absolutePath) }
        }
        val existing = env["LD_LIBRARY_PATH"]?.takeIf { it.isNotBlank() }
        val combined = if (existing != null) (paths + existing).joinToString(":") else paths.joinToString(":")
        env["LD_LIBRARY_PATH"] = combined
        env["TMPDIR"] = context.cacheDir.absolutePath
    }
}
