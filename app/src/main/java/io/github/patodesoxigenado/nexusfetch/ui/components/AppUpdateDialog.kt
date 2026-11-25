package io.github.patodesoxigenado.nexusfetch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.patodesoxigenado.nexusfetch.domain.model.UpdateInfo
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer

/**
 * Prompts the user when a newer Release or ABI-matched Debug APK is published in the repository `apk/` folder.
 */
@Composable
fun AppUpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Float?,
    errorMessage: String?,
    onDownloadAndInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Text("Atualização Disponível (${updateInfo.versionTag})")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val sizeLabel = if (updateInfo.sizeBytes > 0L) {
                    " • ${MediaSanitizer.formatBytes(updateInfo.sizeBytes)}"
                } else {
                    ""
                }
                Text(
                    text = "Canal ${updateInfo.channelLabel} • ${updateInfo.apkFileName}$sizeLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isDownloading) {
                    val pct = ((downloadProgress ?: 0f) * 100f).toInt().coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { downloadProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Baixando e validando SHA-256 ($pct%)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadAndInstall,
                enabled = !isDownloading,
            ) {
                Text("Baixar e Instalar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading,
            ) {
                Text("Agora não")
            }
        },
    )
}
