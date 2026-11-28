package io.github.patodesoxigenado.nexusfetch.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.FfmpegProcessExecutor
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.local.storage.MediaStorePublisher
import io.github.patodesoxigenado.nexusfetch.data.local.storage.WorkDirCleaner
import io.github.patodesoxigenado.nexusfetch.data.remote.GitHubUpdateClient
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.ThemeMode
import io.github.patodesoxigenado.nexusfetch.domain.model.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsActionStatus(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val availableUpdate: UpdateInfo? = null,
    val showTopUpdateBanner: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateBannerError: String? = null,
    val installIntent: Intent? = null,
    val recentLogs: List<String> = emptyList(),
    val logsLoaded: Boolean = false,
    val hasLogFile: Boolean = false,
    val ffmpegAvailable: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val youtubeDlEngine: YoutubeDlEngine,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val gitHubUpdateClient: GitHubUpdateClient,
    private val networkGuard: NetworkGuard,
    private val publisher: MediaStorePublisher,
    private val workDirCleaner: WorkDirCleaner,
    private val logger: NexusLogger,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _actionStatus = MutableStateFlow(
        SettingsActionStatus(
            recentLogs = emptyList(),
            logsLoaded = false,
            hasLogFile = logger.hasLogs(),
            ffmpegAvailable = ffmpegExecutor.isAvailable(),
        ),
    )
    val actionStatus: StateFlow<SettingsActionStatus> = _actionStatus.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWifiOnly(enabled) }
    }

    fun setWarnOnCellular(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWarnOnCellular(enabled) }
    }

    fun updateYtDlpNow() {
        _actionStatus.update { it.copy(isBusy = true, statusMessage = "Verificando atualização do yt-dlp...") }
        viewModelScope.launch {
            val msg = runCatching { youtubeDlEngine.updateYtDlp() }
                .getOrElse { "Falha ao atualizar yt-dlp: ${it.message}" }
            _actionStatus.update {
                it.copy(
                    isBusy = false,
                    statusMessage = msg,
                    hasLogFile = logger.hasLogs(),
                )
            }
        }
    }

    fun checkAppUpdateOnLaunch() {
        viewModelScope.launch {
            val transport = networkGuard.currentTransport()
            val cfg = settingsRepository.settings.first()
            val blockedByNetwork = transport == NetworkTransport.OFFLINE ||
                (cfg.wifiOnly && transport == NetworkTransport.CELLULAR)
            if (blockedByNetwork) return@launch

            settingsRepository.recordAppUpdateCheck(System.currentTimeMillis())
            runCatching { gitHubUpdateClient.checkLatestUpdate() }
                .onSuccess { update ->
                    if (update != null) {
                        _actionStatus.update {
                            it.copy(
                                availableUpdate = update,
                                showTopUpdateBanner = true,
                                updateBannerError = null,
                            )
                        }
                    }
                }
        }
    }

    fun checkAppUpdate() {
        _actionStatus.update {
            it.copy(
                isBusy = true,
                statusMessage = "Verificando atualizações na pasta apk/...",
                availableUpdate = null,
            )
        }
        viewModelScope.launch {
            settingsRepository.recordAppUpdateCheck(System.currentTimeMillis())
            runCatching { gitHubUpdateClient.checkLatestUpdate() }
                .onSuccess { update ->
                    _actionStatus.update {
                        it.copy(
                            isBusy = false,
                            availableUpdate = update,
                            showTopUpdateBanner = update != null,
                            updateBannerError = null,
                            statusMessage = if (update != null) {
                                "Nova versão disponível (${update.channelLabel}): ${update.versionTag}"
                            } else {
                                "Você já está usando a versão mais recente do NexusFetch."
                            },
                        )
                    }
                }
                .onFailure { t ->
                    val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Falha ao checar atualizações.")
                    _actionStatus.update { it.copy(isBusy = false, statusMessage = msg) }
                }
        }
    }

    fun dismissTopUpdateBanner() {
        _actionStatus.update { it.copy(showTopUpdateBanner = false, updateBannerError = null) }
    }

    fun downloadAndPrepareUpdate() {
        val update = _actionStatus.value.availableUpdate ?: return
        _actionStatus.update {
            it.copy(
                isBusy = true,
                updateDownloadProgress = 0f,
                updateBannerError = null,
                statusMessage = "Baixando ${update.apkFileName} e validando SHA-256...",
            )
        }
        viewModelScope.launch {
            runCatching {
                val apk = gitHubUpdateClient.downloadAndVerifyUpdate(update) { pct ->
                    _actionStatus.update {
                        it.copy(
                            updateDownloadProgress = pct,
                            statusMessage = "Baixando atualização (${(pct * 100).toInt()}%)...",
                        )
                    }
                }
                gitHubUpdateClient.buildInstallIntent(apk)
            }.onSuccess { intent ->
                _actionStatus.update {
                    it.copy(
                        isBusy = false,
                        showTopUpdateBanner = false,
                        updateDownloadProgress = null,
                        updateBannerError = null,
                        installIntent = intent,
                        statusMessage = "SHA-256 verificado! Pronto para instalar.",
                    )
                }
            }.onFailure { t ->
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Erro ao baixar atualização.")
                _actionStatus.update {
                    it.copy(
                        isBusy = false,
                        updateDownloadProgress = null,
                        updateBannerError = msg,
                        statusMessage = msg,
                    )
                }
            }
        }
    }

    fun clearInstallIntent() {
        _actionStatus.update { it.copy(installIntent = null) }
    }

    fun refreshLogAvailability() {
        _actionStatus.update {
            it.copy(
                hasLogFile = logger.hasLogs(),
                ffmpegAvailable = ffmpegExecutor.isAvailable(),
            )
        }
    }

    fun loadLogs() {
        _actionStatus.update {
            it.copy(
                recentLogs = logger.readRecentLines(),
                logsLoaded = true,
                hasLogFile = logger.hasLogs(),
                ffmpegAvailable = ffmpegExecutor.isAvailable(),
            )
        }
    }

    fun downloadLogsToDevice() {
        _actionStatus.update { it.copy(isBusy = true, statusMessage = "Salvando arquivo de logs...") }
        val tempJobId = "logs_${System.currentTimeMillis()}"
        viewModelScope.launch {
            val workDir = workDirCleaner.createJobWorkDir(tempJobId)
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val baseName = "nexusfetch_logs_$stamp"
                val snapshot = logger.copyLogSnapshotTo(File(workDir, "$baseName.log"))
                    ?: throw NexusError.StorageError("Nenhum log registrado ainda.")
                val published = publisher.publishLogFile(snapshot, baseName)
                _actionStatus.update {
                    it.copy(
                        isBusy = false,
                        hasLogFile = logger.hasLogs(),
                        statusMessage = "Logs salvos em Download/NexusFetch/${published.fileName}",
                    )
                }
            } catch (t: Throwable) {
                val msg = (t as? NexusError)?.userMessage ?: (t.message ?: "Falha ao salvar logs.")
                _actionStatus.update { it.copy(isBusy = false, statusMessage = msg) }
            } finally {
                workDirCleaner.deleteJobWorkDir(tempJobId)
            }
        }
    }

    fun clearLogs() {
        logger.clearLogs()
        _actionStatus.update {
            it.copy(
                recentLogs = emptyList(),
                logsLoaded = false,
                hasLogFile = false,
                statusMessage = "Logs locais limpos.",
            )
        }
    }

    fun getExportLogUri(): Uri? = logger.getExportUri()
}
