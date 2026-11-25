package io.github.patodesoxigenado.nexusfetch.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import javax.inject.Inject

/**
 * Keeps downloads, batch compression, conversion, and trimming alive across backgrounding and screen lock.
 * Compliant with Android 14 (`foregroundServiceType`) and Android 15 (`onTimeout`) policies.
 */
@AndroidEntryPoint
class MediaJobForegroundService : Service() {

    @Inject lateinit var jobOrchestrator: JobOrchestrator
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var logger: NexusLogger

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        jobOrchestrator.onQueueDrained = {
            mainHandler.post {
                notificationHelper.cancelProgressNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                jobOrchestrator.cancelActiveJob()
                notificationHelper.cancelProgressNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val moduleId = intent.getStringExtra(EXTRA_MODULE_ID)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "NexusFetch"
                val notification = notificationHelper.buildProgressNotification(
                    title = title,
                    statusText = "Processando mídia...",
                    progressPercent = 1f,
                )
                startForegroundSafely(moduleId, notification)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        logger.warn("service", "timeout", "Android 15 FGS timeout atingido para type=$fgsType")
        jobOrchestrator.cancelActiveJob()
        notificationHelper.cancelProgressNotification()
        notificationHelper.showErrorNotification(
            "Tarefa pausada pelo sistema",
            "O limite de tempo em segundo plano do Android foi atingido.",
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        jobOrchestrator.onQueueDrained = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(moduleId: String?, notification: android.app.Notification) {
        val serviceType = resolveServiceTypeForSdk(Build.VERSION.SDK_INT, moduleId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Direct platform call avoids core-1.15.0 Api34Impl mask (0x40000FFF) zeroing MEDIA_PROCESSING (0x2000)
                startForeground(NotificationHelper.PROGRESS_NOTIFICATION_ID, notification, serviceType)
            } else {
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.PROGRESS_NOTIFICATION_ID,
                    notification,
                    serviceType,
                )
            }
        } catch (t: Throwable) {
            logger.warn("service", "fgs_fallback", "Fallback FGS para type=$serviceType: ${t.message}")
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationHelper.PROGRESS_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    ServiceCompat.startForeground(this, NotificationHelper.PROGRESS_NOTIFICATION_ID, notification, 0)
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "io.github.patodesoxigenado.nexusfetch.action.START_JOB"
        const val ACTION_CANCEL = "io.github.patodesoxigenado.nexusfetch.action.CANCEL_JOB"
        const val EXTRA_MODULE_ID = "extra_module_id"
        const val EXTRA_TITLE = "extra_title"

        @SuppressLint("InlinedApi")
        fun resolveServiceTypeForSdk(sdkInt: Int, moduleId: String?): Int {
            if (sdkInt < Build.VERSION_CODES.Q) return 0
            val isLocalProcessing = moduleId == JobModule.COMPRESSOR.id || moduleId == JobModule.CONVERTER.id
            return if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && isLocalProcessing) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        }
    }
}
