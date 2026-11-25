package io.github.patodesoxigenado.nexusfetch.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import io.github.patodesoxigenado.nexusfetch.ui.components.IntentActions
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val selectedModule by viewModel.selectedModule.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalNexusColors.current
    val dateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Histórico Local",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = viewModel::clearAll) {
                    Text(
                        text = "Limpar Tudo",
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedModule == null,
                onClick = { viewModel.selectFilter(null) },
                label = { Text("Todos") },
            )
            JobModule.entries.forEach { mod ->
                FilterChip(
                    selected = selectedModule == mod,
                    onClick = { viewModel.selectFilter(mod) },
                    label = { Text(mod.label) },
                )
            }
        }

        if (entries.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Nenhum arquivo processado ainda. Seus downloads, compressões, conversões " +
                        "e recortes aparecerão aqui com acesso rápido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, extra.border),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = item.module.label,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val formattedSize = MediaSanitizer.formatBytes(item.sizeBytes)
                                    val formattedDate = dateFormatter.format(Date(item.createdAtEpochMs))
                                    Text(
                                        text = "$formattedSize • $formattedDate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteEntry(item.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remover")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { IntentActions.shareSingle(context, item.mediaStoreUri, item.mimeType) },
                                    modifier = Modifier.weight(1f),
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
                                    onClick = { IntentActions.openMedia(context, item.mediaStoreUri, item.mimeType) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
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
        }
    }
}
