package io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
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
import io.github.patodesoxigenado.nexusfetch.domain.model.RemoteTrimQuality
import io.github.patodesoxigenado.nexusfetch.domain.model.TrimSourceType
import io.github.patodesoxigenado.nexusfetch.domain.policy.TrimRangeValidator
import io.github.patodesoxigenado.nexusfetch.ui.components.CellularWarningDialog
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrimmerScreen(
    viewModel: TrimmerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalNexusColors.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.loadLocalUri(uri)
    }

    state.cellularPrompt?.let { prompt ->
        CellularWarningDialog(
            wifiOnlyBlocked = prompt.wifiOnlyBlocked,
            onProceedAnyway = prompt.onConfirm,
            onDismiss = viewModel::dismissCellularPrompt,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Recorte Rápido", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Recorta apenas o trecho desejado de links (sem baixar o vídeo inteiro) ou arquivos do aparelho.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sourceMode == TrimSourceType.URL,
                onClick = { viewModel.switchSourceMode(TrimSourceType.URL) },
                label = { Text("Link Remoto (4K/MP3)") },
            )
            FilterChip(
                selected = state.sourceMode == TrimSourceType.FILE,
                onClick = { viewModel.switchSourceMode(TrimSourceType.FILE) },
                label = { Text("Arquivo Local") },
            )
        }

        if (state.sourceMode == TrimSourceType.URL) {
            OutlinedTextField(
                value = state.urlInput,
                onValueChange = viewModel::onUrlChanged,
                label = { Text("Link do YouTube, Instagram ou X") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.analyzeRemoteUrl() },
                enabled = state.urlInput.isNotBlank() && state.sourceLoadState !is TrimSourceLoadState.Analyzing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = extra.trimmerAccent),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Analisar Duração do Link",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Escolher Vídeo ou Áudio Local",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        when (val load = state.sourceLoadState) {
            is TrimSourceLoadState.Idle -> Unit
            is TrimSourceLoadState.Analyzing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Carregando informações da mídia...")
                }
            }
            is TrimSourceLoadState.Error -> {
                Text(load.message, color = MaterialTheme.colorScheme.error)
            }
            is TrimSourceLoadState.Ready -> {
                val info = load.info
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extra.trimmerAccent.copy(alpha = 0.6f)),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${info.uploader.ifBlank { "Mídia" }} • Duração total: ${
                                if (info.durationSec > 0.0) TrimRangeValidator.formatTimestampHms(info.durationSec) else "N/A"
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (info.durationSec > 1.0) {
                            val totalF = info.durationSec.toFloat()
                            val sliderStart = state.startSec.coerceIn(0f, totalF)
                            val sliderEnd = state.endSec.coerceIn(sliderStart, totalF)
                            RangeSlider(
                                value = sliderStart..sliderEnd,
                                onValueChange = { range ->
                                    viewModel.onSliderRangeChanged(range.start, range.endInclusive)
                                },
                                valueRange = 0f..totalF,
                                colors = SliderDefaults.colors(
                                    thumbColor = extra.trimmerAccent,
                                    activeTrackColor = extra.trimmerAccent,
                                ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.startText,
                                onValueChange = viewModel::onStartTextChanged,
                                label = { Text("Início") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.endText,
                                onValueChange = viewModel::onEndTextChanged,
                                label = { Text("Fim") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !state.audioOnly,
                                onClick = { viewModel.setAudioOnly(false) },
                                enabled = info.isVideo,
                                label = { Text("Vídeo") },
                            )
                            FilterChip(
                                selected = state.audioOnly,
                                onClick = { viewModel.setAudioOnly(true) },
                                label = { Text("Apenas Áudio (MP3 320k)") },
                            )
                        }

                        if (info.sourceType == TrimSourceType.URL && !state.audioOnly) {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                            ) {
                                OutlinedTextField(
                                    value = state.selectedQuality.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Resolução Máxima") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    RemoteTrimQuality.entries.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q.label) },
                                            onClick = {
                                                viewModel.selectRemoteQuality(q)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        state.validationError?.let { err ->
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = viewModel::startTrim,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = extra.trimmerAccent),
                        ) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recortar Trecho",
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
