package io.github.patodesoxigenado.nexusfetch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.MainActivity
import io.github.patodesoxigenado.nexusfetch.R
import io.github.patodesoxigenado.nexusfetch.domain.model.PublishedMedia
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds Android O–15 progress and completion notifications with Cancel, Open, and Share actions.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notification_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_progress_desc)
            setShowBadge(false)
        }
        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            context.getString(R.string.notification_channel_complete),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_complete_desc)
        }
        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(completeChannel)
    }

    fun buildProgressNotification(
        title: String,
        statusText: String,
        progressPercent: Float,
    ): Notification {
        ensureChannels()
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, MediaJobForegroundService::class.java).apply {
                action = MediaJobForegroundService.ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val indeterminate = progressPercent <= 0f
        val pctInt = progressPercent.roundToInt().coerceIn(0, 100)

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "NexusFetch" })
            .setContentText(statusText.ifBlank { "Processando..." })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setProgress(100, pctInt, indeterminate)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                cancelIntent,
            )
            .build()
    }

    fun updateProgressNotification(title: String, statusText: String, progressPercent: Float) {
        manager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(title, statusText, progressPercent))
    }

    fun cancelProgressNotification() {
        manager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun showCompletionNotification(title: String, message: String, outputs: List<PublishedMedia>) {
        ensureChannels()
        val first = outputs.firstOrNull()
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (first != null) {
            val uri = Uri.parse(first.uriString)
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, first.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val openPending = PendingIntent.getActivity(
                context,
                3,
                Intent.createChooser(openIntent, "Abrir arquivo"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = first.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePending = PendingIntent.getActivity(
                context,
                4,
                Intent.createChooser(shareIntent, "Compartilhar"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_menu_view, "Abrir", openPending)
            builder.addAction(android.R.drawable.ic_menu_share, "Compartilhar", sharePending)
        }

        manager.notify((System.currentTimeMillis() % 10_000).toInt() + 100, builder.build())
    }

    fun showErrorNotification(title: String, errorText: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(errorText)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 10_000).toInt() + 100, notification)
    }

    companion object {
        const val CHANNEL_PROGRESS = "nexus_progress"
        const val CHANNEL_COMPLETE = "nexus_complete"
        const val PROGRESS_NOTIFICATION_ID = 1001
    }
}
