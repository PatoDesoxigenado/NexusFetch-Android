package io.github.patodesoxigenado.nexusfetch.ui.screens.compressor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressorScreen(
    viewModel: CompressorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalNexusColors.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.addUris(uris)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Compressor Discord & WhatsApp", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reduz vídeos e áudios para caber exatamente no limite escolhido com máxima nitidez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompressionPreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.selectedPreset == preset,
                    onClick = { viewModel.selectPreset(preset) },
                    label = { Text(preset.label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Selecionar Mídias",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.files.isNotEmpty()) {
                OutlinedButton(
                    onClick = viewModel::clearAll,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Limpar",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (state.isProbing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.errorMessage?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.files, key = { it.uriString }) { file ->
                val plan = state.plansByUri[file.uriString]
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
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${file.sizeStr} • ${file.durationStr} • ${file.resolutionStr}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.removeFile(file.uriString) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remover")
                            }
                        }

                        if (plan != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            if (plan.alreadyWithinLimit) {
                                Surface(
                                    color = extra.success.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = "Já cabe no limite (${state.selectedPreset.limitMb.toInt()} MB) — será pulado.",
                                        color = extra.success,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                val resLabel = plan.scaleHeight?.let { "${it}p" } ?: "Original"
                                val fpsLabel = plan.targetFps?.let { " @ ${it}fps" } ?: ""
                                val specs = if (plan.isVideo) {
                                    "Plano: Vídeo ${plan.videoKbps} kbps • Áudio ${plan.audioKbps} kbps • Escala $resLabel$fpsLabel"
                                } else {
                                    "Plano: Áudio MP3 ${plan.audioKbps} kbps"
                                }
                                Text(specs, style = MaterialTheme.typography.bodySmall)

                                if (plan.lowQualityWarning) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = extra.warning.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = extra.warning,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val limitMb = state.selectedPreset.limitMb.toInt()
                                            Text(
                                                text = "Aviso: vídeo longo para $limitMb MB " +
                                                    "(${plan.videoKbps} kbps). Pode perder nitidez.",
                                                color = extra.warning,
                                                style = MaterialTheme.typography.bodySmall,
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

        Button(
            onClick = viewModel::startCompression,
            enabled = state.files.isNotEmpty() && !state.isProbing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Comprimir (${state.files.size} arquivo(s))",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
