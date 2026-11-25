package io.github.patodesoxigenado.nexusfetch.ui.screens.multi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import io.github.patodesoxigenado.nexusfetch.domain.model.MultiItemStatus
import io.github.patodesoxigenado.nexusfetch.ui.components.CellularWarningDialog
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiDownloadScreen(
    viewModel: MultiDownloadViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extra = LocalNexusColors.current

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Multi-Download (${state.items.size}/10)", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Analise a fila antes de baixar para liberar formatos individuais por mídia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = viewModel::addRow,
                enabled = state.items.size < MultiDownloadViewModel.MAX_LINKS && !state.isAnalyzing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Link",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        state.globalError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                val border = when (item.status) {
                    MultiItemStatus.READY -> extra.success
                    MultiItemStatus.INVALID -> MaterialTheme.colorScheme.error
                    else -> extra.border
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, border),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = item.rawUrl,
                                onValueChange = { viewModel.updateUrl(item.id, it) },
                                label = { Text("Link #${item.position}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeRow(item.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remover item #${item.position}")
                            }
                        }

                        if (item.status == MultiItemStatus.ANALYZING) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        item.errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        item.metadata?.let { meta ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${meta.platform?.label ?: ""} • ${meta.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = extra.success,
                            )
                            if (item.allowedFormats.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded },
                                ) {
                                    OutlinedTextField(
                                        value = item.selectedFormat?.label ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Formato de Saída") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth(),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                    ) {
                                        item.allowedFormats.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.label) },
                                                onClick = {
                                                    viewModel.selectFormat(item.id, opt)
                                                    expanded = false
                                                },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::analyzeQueue,
                enabled = !state.isAnalyzing,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isAnalyzing) "Analisando..." else "Analisar Fila",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = viewModel::startBatchDownload,
                enabled = state.allValidReady && !state.isAnalyzing,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Baixar Tudo",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
