package io.github.patodesoxigenado.nexusfetch.ui.screens.converter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patodesoxigenado.nexusfetch.domain.model.Mp4Codec
import io.github.patodesoxigenado.nexusfetch.domain.model.ResolutionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetAudioFormat
import io.github.patodesoxigenado.nexusfetch.domain.model.TargetVideoFormat
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel,
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Conversor Multimídia", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Converta vídeos em lote (até 4K, H.264/HEVC/AV1/VP9) ou extraia/converta áudios.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.isAudioTarget,
                onClick = { viewModel.setAudioTarget(false) },
                label = { Text("Vídeo") },
            )
            FilterChip(
                selected = state.isAudioTarget,
                onClick = { viewModel.setAudioTarget(true) },
                label = { Text("Áudio / Extrair MP3") },
            )
        }

        if (!state.isAudioTarget) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TargetVideoFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = state.videoFormat == fmt,
                        onClick = { viewModel.selectVideoFormat(fmt) },
                        label = { Text(fmt.label) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                var resExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = resExpanded,
                    onExpandedChange = { resExpanded = !resExpanded },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = state.resolution.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Resolução") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(resExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = resExpanded, onDismissRequest = { resExpanded = false }) {
                        ResolutionPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    viewModel.selectResolution(preset)
                                    resExpanded = false
                                },
                            )
                        }
                    }
                }

                if (state.videoFormat == TargetVideoFormat.MP4) {
                    var codecExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = codecExpanded,
                        onExpandedChange = { codecExpanded = !codecExpanded },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = state.mp4Codec.key.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Codec MP4") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(codecExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = codecExpanded, onDismissRequest = { codecExpanded = false }) {
                            Mp4Codec.entries.forEach { codec ->
                                DropdownMenuItem(
                                    text = { Text(codec.label) },
                                    onClick = {
                                        viewModel.selectMp4Codec(codec)
                                        codecExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TargetAudioFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = state.audioFormat == fmt,
                        onClick = { viewModel.selectAudioFormat(fmt) },
                        label = { Text(fmt.label) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Adicionar Arquivos",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        if (state.isProbing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.files, key = { it.uriString }) { file ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extra.converterAccent.copy(alpha = 0.45f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${file.sizeStr} • ${file.durationStr} • ${file.resolutionStr}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeFile(file.uriString) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remover")
                        }
                    }
                }
            }
        }

        Button(
            onClick = viewModel::startConversion,
            enabled = state.files.isNotEmpty() && !state.isProbing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = extra.converterAccent),
        ) {
            Icon(Icons.Rounded.Transform, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Converter (${state.files.size} arquivo(s))",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
