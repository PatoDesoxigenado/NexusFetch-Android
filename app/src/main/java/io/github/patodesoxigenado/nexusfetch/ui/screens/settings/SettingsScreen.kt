package io.github.patodesoxigenado.nexusfetch.ui.screens.settings

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.patodesoxigenado.nexusfetch.BuildConfig
import io.github.patodesoxigenado.nexusfetch.domain.model.ThemeMode
import io.github.patodesoxigenado.nexusfetch.ui.theme.LocalNexusColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.actionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalNexusColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configurações & Diagnóstico", style = MaterialTheme.typography.headlineSmall)

        // Appearance
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extra.border),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aparência", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Cores Dinâmicas (Material You)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }
        }

        // Network & Privacy
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extra.border),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rede & Dados Móveis", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Apenas Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Bloqueia downloads e recortes remotos no 4G/5G.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Avisar antes de usar 4G/5G", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Pede confirmação ao iniciar tarefa remota em rede móvel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.warnOnCellular, onCheckedChange = viewModel::setWarnOnCellular)
                }
            }
        }

        // Engine & App Updates
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extra.border),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Motores & Atualizações", style = MaterialTheme.typography.titleMedium)
                Text(
                    "App v${BuildConfig.VERSION_NAME} • yt-dlp: ${settings.ytDlpVersion} • FFmpeg: ${
                        if (status.ffmpegAvailable) "Pronto" else "Sob demanda"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (status.isBusy) {
                    val dlProgress = status.updateDownloadProgress
                    if (dlProgress != null) {
                        LinearProgressIndicator(progress = { dlProgress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                status.statusMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = extra.info)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::updateYtDlpNow,
                        enabled = !status.isBusy,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Atualizar yt-dlp",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::checkAppUpdate,
                        enabled = !status.isBusy,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Checar App",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                status.availableUpdate?.let { upd ->
                    Button(
                        onClick = viewModel::downloadAndPrepareUpdate,
                        enabled = !status.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Instalar ${upd.versionTag} (SHA-256 verificado)",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Diagnostics & Sanitized Logs
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, extra.border),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Logs Locais (Sanitizados)", style = MaterialTheme.typography.titleMedium)

                Text(
                    "Zero telemetria externa. Tokens e caminhos privados são ocultados automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::refreshDiagnostics,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Atualizar",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::clearLogs,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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

                val logPreview = if (status.recentLogs.isEmpty()) {
                    "Nenhum evento registrado ainda."
                } else {
                    status.recentLogs.takeLast(12).joinToString("\n")
                }
                Text(
                    text = logPreview,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.getExportLogUri()?.let { uri ->
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "Exportar logs")) }
                        }
                    },
                    enabled = status.recentLogs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Exportar Arquivo de Log",
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
