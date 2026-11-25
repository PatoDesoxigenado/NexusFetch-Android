package io.github.patodesoxigenado.nexusfetch.ui.screens.share

import android.net.Uri
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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import io.github.patodesoxigenado.nexusfetch.ui.navigation.NexusDestination
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickShareBottomSheet(
    state: QuickShareUiState,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onQuickDownload: (modeOverride: String?) -> Unit,
    onOpenInApp: (route: String, url: String?, uris: List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = LocalNexusColors.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NexusFetch — Ação Rápida", style = MaterialTheme.typography.titleLarge)

            when (state) {
                is QuickShareUiState.Idle -> Unit

                is QuickShareUiState.AnalyzingLink -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Analisando link compartilhado...")
                    }
                }

                is QuickShareUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Fechar")
                    }
                }

                is QuickShareUiState.LinkReady -> {
                    val meta = state.metadata
                    Text(
                        text = meta.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${meta.platform?.label ?: ""} • ${meta.uploader} • ${meta.durationStr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.wifiBlocked) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "Bloqueado pelo modo Apenas Wi-Fi. Conecte-se ao Wi-Fi para baixar.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    } else if (state.cellularWarning) {
                        Text(
                            text = "Aviso: você está no 4G/5G.",
                            color = extra.warning,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (meta.mediaType == MediaType.VIDEO && meta.qualities.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                        ) {
                            OutlinedTextField(
                                value = state.selectedQuality?.label ?: "Máxima Resolução",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Qualidade") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                meta.qualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q.label) },
                                        onClick = {
                                            onSelectQuality(q)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onQuickDownload(null) },
                            enabled = !state.wifiBlocked,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val txt = when (meta.mediaType) {
                                MediaType.IMAGE -> "Baixar Foto"
                                MediaType.CAROUSEL -> "Baixar Carrossel (${meta.playlistCount})"
                                MediaType.GIF -> "Salvar GIF"
                                else -> "Baixar"
                            }
                            Text(
                                text = txt,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        if (meta.mediaType == MediaType.VIDEO || meta.mediaType == MediaType.PLAYLIST) {
                            OutlinedButton(
                                onClick = { onQuickDownload("audio") },
                                enabled = !state.wifiBlocked,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MP3 320k",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (meta.mediaType == MediaType.VIDEO) {
                            TextButton(
                                onClick = { onOpenInApp(NexusDestination.TRIMMER.route, meta.url, emptyList()) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Recortar Trecho",
                                    color = extra.trimmerAccent,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        TextButton(
                            onClick = { onOpenInApp(NexusDestination.DOWNLOADER.route, meta.url, emptyList()) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Abrir no App",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                is QuickShareUiState.LocalFilesReady -> {
                    Text(
                        text = "${state.uris.size} arquivo(s) selecionado(s). Para qual ferramenta deseja enviar?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { onOpenInApp(NexusDestination.COMPRESSOR.route, null, state.uris) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Comprimir para Discord / WhatsApp")
                    }
                    Button(
                        onClick = { onOpenInApp(NexusDestination.CONVERTER.route, null, state.uris) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = extra.converterAccent),
                    ) {
                        Icon(Icons.Rounded.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conversor Multimídia")
                    }
                    if (state.uris.size == 1) {
                        OutlinedButton(
                            onClick = { onOpenInApp(NexusDestination.TRIMMER.route, null, state.uris) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recorte Rápido")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
