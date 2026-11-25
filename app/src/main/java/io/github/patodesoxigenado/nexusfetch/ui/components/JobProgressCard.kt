package io.github.patodesoxigenado.nexusfetch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.patodesoxigenado.nexusfetch.domain.model.ActiveJobState
import io.github.patodesoxigenado.nexusfetch.domain.model.JobStatus
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors
import kotlin.math.roundToInt

/**
 * Global real-progress & completion action banner offering instant Share & Open buttons on mobile.
 */
@Composable
fun JobProgressCard(
    state: ActiveJobState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.status == JobStatus.IDLE) return

    val context = LocalContext.current
    val extra = LocalNexusColors.current
    val isRunning = state.status == JobStatus.RUNNING || state.status == JobStatus.QUEUED
    val isSuccess = state.status == JobStatus.COMPLETED

    val borderColor = when {
        isSuccess -> extra.success
        state.status == JobStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> extra.border
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { state.module?.label ?: "NexusFetch" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isSuccess -> extra.success
                            state.status == JobStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isRunning) {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = "Cancelar",
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar")
                    }
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${state.progressPercent.roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val metaText = listOf(state.speedText, state.etaText)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isSuccess && state.completedOutputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { IntentActions.shareOutputs(context, state.completedOutputs) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = extra.success),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Compartilhar",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val first = state.completedOutputs.first()
                            IntentActions.openMedia(context, first.uriString, first.mimeType)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Abrir",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
