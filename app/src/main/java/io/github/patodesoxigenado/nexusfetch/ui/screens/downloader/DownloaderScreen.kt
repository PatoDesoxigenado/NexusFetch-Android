package io.github.patodesoxigenado.nexusfetch.ui.screens.downloader

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer
import io.github.patodesoxigenado.nexusfetch.ui.components.CellularWarningDialog
import io.github.patodesoxigenado.nexusfetch.ui.components.IntentActions
import io.github.patodesoxigenado.nexusfetch.ui.components.MediaPreviewCard
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Downloader Universal",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Cole qualquer link do YouTube, Instagram ou Twitter/X. A plataforma é detectada automaticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.urlInput,
            onValueChange = viewModel::onUrlChanged,
            label = { Text("Link da mídia ou playlist") },
            placeholder = { Text("https://youtube.com/... ou instagram.com/... ou x.com/...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Row {
                    if (state.urlInput.isNotBlank()) {
                        IconButton(onClick = viewModel::clearSource) {
                            Icon(Icons.Rounded.Close, contentDescription = "Limpar")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                IntentActions.readClipboardText(context)?.let { clip ->
                                    viewModel.analyzeWithUrl(clip)
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Colar")
                        }
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.detectedPlatform?.let { plat ->
                Surface(
                    color = Color(plat.badgeColorHex).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "Detectado: ${plat.label}",
                        color = Color(plat.badgeColorHex),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } ?: Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::analyzeCurrentUrl,
                enabled = state.urlInput.isNotBlank() && state.sourceState !is DownloaderSourceState.Analyzing,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Analisar Link",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        when (val source = state.sourceState) {
            is DownloaderSourceState.Idle -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suporte Completo", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "• YouTube: Vídeos até 4K 2160p, MP3 320 kbps com capa automática, Thumbnails HD e Playlists.\n" +
                                "• Instagram: Posts, Reels, Fotos originais e Carrosséis mistos.\n" +
                                "• Twitter/X: Vídeos, GIFs animados (.gif) e Fotos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is DownloaderSourceState.Analyzing -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Extraindo metadados e formatos...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is DownloaderSourceState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    ),
                ) {
                    Text(
                        text = source.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            is DownloaderSourceState.Ready -> {
                val meta = source.metadata
                MediaPreviewCard(
                    metadata = meta,
                    selectedItemIds = state.selectedItemKeys,
                    onToggleItem = viewModel::toggleItem,
                    onSelectAll = viewModel::selectAllItems,
                    onDeselectAll = viewModel::deselectAllItems,
                )

                // Format / Mode selection
                if (meta.mediaType == MediaType.VIDEO || meta.mediaType == MediaType.PLAYLIST) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedMode == "video",
                            onClick = { viewModel.selectMode("video") },
                            label = { Text("Vídeo MP4") },
                        )
                        val audioTag = meta.estimatedAudioSizeBytes?.let { " (~${MediaSanitizer.formatBytes(it)})" }.orEmpty()
                        FilterChip(
                            selected = state.selectedMode == "audio",
                            onClick = { viewModel.selectMode("audio") },
                            label = { Text("Áudio MP3 320k$audioTag") },
                        )
                    }

                    if (state.selectedMode == "video" && meta.qualities.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                        ) {
                            OutlinedTextField(
                                value = state.selectedQuality?.label ?: "Máxima Resolução",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Qualidade do Vídeo") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                meta.qualities.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt.label) },
                                        onClick = {
                                            viewModel.selectQuality(opt)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (meta.mediaType == MediaType.GIF && meta.qualities.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        meta.qualities.forEach { opt ->
                            FilterChip(
                                selected = state.selectedQuality?.formatId == opt.formatId,
                                onClick = { viewModel.selectQuality(opt) },
                                label = { Text(opt.label) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::startDownload,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val label = when {
                            meta.isPlaylist -> "Baixar Selecionados (${state.selectedItemKeys.size})"
                            state.selectedMode == "audio" -> "Baixar MP3"
                            state.selectedMode == "image" -> "Baixar Foto HD"
                            state.selectedMode == "gif" -> "Salvar GIF"
                            else -> "Baixar Vídeo"
                        }
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (!meta.thumbnailUrl.isNullOrBlank() && meta.mediaType != MediaType.IMAGE) {
                        OutlinedButton(
                            onClick = viewModel::downloadThumbnailHd,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = "Baixar Thumbnail",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Thumb",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                state.thumbnailDownloadMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = extra.success,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
