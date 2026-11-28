package io.github.patodesoxigenado.nexusfetch.ui.screens.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

private data class HelpSectionItem(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

private val helpSections = listOf(
    HelpSectionItem(
        icon = Icons.Rounded.Download,
        title = "Baixar (Downloader Universal)",
        body = "Cole um link do YouTube, Instagram ou Twitter/X e toque em Analisar Link. " +
            "No YouTube você escolhe vídeo (até 4K), Áudio MP3 320 kbps (já com capa quadrada 1:1 " +
            "automática), Thumbnail HD ou itens individuais de Playlists. No Instagram o app baixa " +
            "fotos, Reels e carrosséis mistos; no Twitter/X baixa vídeos, GIFs animados e fotos.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.Share,
        title = "Compartilhar Direto (Ação Rápida)",
        body = "Ao tocar em Compartilhar dentro do YouTube, Instagram, X ou da Galeria e escolher " +
            "NexusFetch, uma janela rápida abre por cima do app atual para você baixar o link ou " +
            "enviar vídeos/áudios direto para Comprimir, Converter ou Recortar.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.Queue,
        title = "Fila (Multi-Download)",
        body = "Adicione vários links seguidos, valide todos antes de iniciar e escolha individualmente " +
            "o formato (Vídeo MP4, MP3 320k, Foto ou GIF) de cada item para baixar em sequência.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.Compress,
        title = "Comprimir (Discord & WhatsApp)",
        body = "Selecione um ou mais vídeos/áudios da galeria e escolha um limite (10 MB, 16 MB, " +
            "50 MB ou 64 MB). O NexusFetch calcula o melhor bitrate e usa aceleração de hardware " +
            "para caber exatamente no limite escolhido.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.ContentCut,
        title = "Recortar (Trim / Clip)",
        body = "Cole um link do YouTube para recortar apenas o trecho desejado (sem baixar o vídeo " +
            "inteiro) ou carregue um arquivo local para cortar rapidamente o início e o fim.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.Transform,
        title = "Conversor Multimídia",
        body = "Converta vídeos em lote para MP4, MKV, AVI, MOV ou WEBM (com troca instantânea de " +
            "container quando compatível) ou extraia/converta áudios para MP3 320 kbps, WAV, FLAC, " +
            "M4A, OGG e OPUS.",
    ),
    HelpSectionItem(
        icon = Icons.Rounded.History,
        title = "Pastas, Histórico, Ajustes & Logs",
        body = "Todos os arquivos ficam visíveis na aba Histórico e nas pastas padrão do Android " +
            "(Movies/NexusFetch, Music/NexusFetch, Pictures/NexusFetch e Download/NexusFetch). Em Ajustes " +
            "você configura tema, Modo Apenas Wi-Fi, recebe avisos automáticos de atualização e pode " +
            "carregar, baixar (em Download/NexusFetch) ou exportar os logs caso precise relatar um bug.",
    ),
)

/**
 * Built-in user guide explaining every NexusFetch feature in simple Portuguese.
 */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val extra = LocalNexusColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajuda & Guia Rápido", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Entenda rapidamente como usar cada ferramenta do NexusFetch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        helpSections.forEach { section ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, extra.border),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
