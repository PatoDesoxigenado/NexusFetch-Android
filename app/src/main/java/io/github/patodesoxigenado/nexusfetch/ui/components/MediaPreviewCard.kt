package io.github.patodesoxigenado.nexusfetch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

/**
 * Displays rich thumbnail, uploader, duration, and interactive Playlist/Carousel selection items.
 */
@Composable
fun MediaPreviewCard(
    metadata: MediaMetadata,
    selectedItemIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalNexusColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.border),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!metadata.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = metadata.thumbnailUrl,
                        contentDescription = metadata.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 112.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    metadata.platform?.let { plat ->
                        Surface(
                            color = Color(plat.badgeColorHex).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = plat.label,
                                color = Color(plat.badgeColorHex),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = metadata.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${metadata.uploader} • ${metadata.durationStr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (metadata.isPlaylist && metadata.playlistItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Selecionados: ${selectedItemIds.size}/${metadata.playlistItems.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Marcar Todos",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Desmarcar",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(metadata.playlistItems, key = { "${it.index}_${it.videoId}" }) { item ->
                        val itemKey = "${item.index}_${item.videoId}"
                        val checked = itemKey in selectedItemIds
                        Surface(
                            color = extra.cardSubtle,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleItem(itemKey) },
                                )
                                if (!item.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 34.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.index}. ${item.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.durationStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
