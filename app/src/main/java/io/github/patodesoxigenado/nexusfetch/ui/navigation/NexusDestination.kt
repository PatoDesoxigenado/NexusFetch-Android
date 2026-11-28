package io.github.patodesoxigenado.nexusfetch.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.ui.graphics.vector.ImageVector

enum class NexusDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val isPrimaryTab: Boolean,
) {
    DOWNLOADER("downloader", "Baixar", Icons.Rounded.Download, true),
    MULTI("multi", "Fila", Icons.Rounded.Queue, true),
    COMPRESSOR("compressor", "Comprimir", Icons.Rounded.Compress, true),
    TRIMMER("trimmer", "Recortar", Icons.Rounded.ContentCut, true),
    CONVERTER("converter", "Conversor", Icons.Rounded.Transform, false),
    HISTORY("history", "Histórico", Icons.Rounded.History, false),
    SETTINGS("settings", "Ajustes", Icons.Rounded.Settings, false),
    HELP("help", "Ajuda", Icons.AutoMirrored.Rounded.HelpOutline, false);

    companion object {
        val primaryTabs = entries.filter { it.isPrimaryTab }
        val moreDestinations = entries.filter { !it.isPrimaryTab }
        fun fromRoute(route: String?): NexusDestination =
            entries.firstOrNull { it.route == route } ?: DOWNLOADER
    }
}
