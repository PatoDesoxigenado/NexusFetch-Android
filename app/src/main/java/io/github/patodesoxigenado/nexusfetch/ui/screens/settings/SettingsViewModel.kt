package io.github.patodesoxigenado.nexusfetch.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.engine.FfmpegProcessExecutor
import io.github.patodesoxigenado.nexusfetch.data.engine.YoutubeDlEngine
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.data.remote.GitHubUpdateClient
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkGuard
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NetworkTransport
import io.github.patodesoxigenado.nexusfetch.data.telemetry.NexusLogger
import io.github.patodesoxigenado.nexusfetch.domain.error.NexusError
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.ThemeMode
import io.github.patodesoxigenado.nexusfetch.domain.model.UpdateInfo
import io.github.patodesoxigenado.nexusfetch.domain.policy.AppUpdatePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsActionStatus(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val availableUpdate: UpdateInfo? = null,
    val pendingUpdatePrompt: UpdateInfo? = null,
    val updateDownloadProgress: Float? = null,
    val updateDialogError: String? = null,
    val installIntent: Intent? = null,
    val recentLogs: List<String> = emptyList(),
    val ffmpegAvailable: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val youtubeDlEngine: YoutubeDlEngine,
    private val ffmpegExecutor: FfmpegProcessExecutor,
    private val gitHubUpdateClient: GitHubUpdateClient,
    private val networkGuard: NetworkGuard,
    private val logger: NexusLogger,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _actionStatus = MutableStateFlow(
        SettingsActionStatus(
            recentLogs = logger.readRecentLines(),
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
                    recentLogs = logger.readRecentLines(),
                )
            }
        }
    }

    fun checkAppUpdatePeriodically() {
        viewModelScope.launch {
            val transport = networkGuard.currentTransport()
            val cfg = settingsRepository.settings.first()
            val blockedByNetwork = transport == NetworkTransport.OFFLINE ||
                (cfg.wifiOnly && transport == NetworkTransport.CELLULAR)
            val now = System.currentTimeMillis()
            if (blockedByNetwork || !AppUpdatePolicy.shouldRunPeriodicCheck(cfg.lastAppUpdateCheckEpochMs, now)) {
                return@launch
            }

            settingsRepository.recordAppUpdateCheck(now)
            runCatching { gitHubUpdateClient.checkLatestUpdate() }
                .onSuccess { update ->
                    if (update != null && update.versionTag != cfg.dismissedUpdateVersionTag) {
                        _actionStatus.update {
                            it.copy(
                                availableUpdate = update,
                                pendingUpdatePrompt = update,
                                updateDialogError = null,
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
            val now = System.currentTimeMillis()
            settingsRepository.recordAppUpdateCheck(now)
            runCatching { gitHubUpdateClient.checkLatestUpdate() }
                .onSuccess { update ->
                    if (update != null) {
                        settingsRepository.setDismissedUpdateVersionTag("")
                    }
                    _actionStatus.update {
                        it.copy(
                            isBusy = false,
                            availableUpdate = update,
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

    fun dismissUpdatePrompt() {
        val dismissedTag = _actionStatus.value.pendingUpdatePrompt?.versionTag
        _actionStatus.update { it.copy(pendingUpdatePrompt = null, updateDialogError = null) }
        if (!dismissedTag.isNullOrBlank()) {
            viewModelScope.launch {
                settingsRepository.setDismissedUpdateVersionTag(dismissedTag)
            }
        }
    }

    fun downloadAndPrepareUpdate() {
        val update = _actionStatus.value.availableUpdate ?: _actionStatus.value.pendingUpdatePrompt ?: return
        _actionStatus.update {
            it.copy(
                isBusy = true,
                updateDownloadProgress = 0f,
                updateDialogError = null,
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
                        pendingUpdatePrompt = null,
                        updateDownloadProgress = null,
                        updateDialogError = null,
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
                        updateDialogError = msg,
                        statusMessage = msg,
                    )
                }
            }
        }
    }

    fun clearInstallIntent() {
        _actionStatus.update { it.copy(installIntent = null) }
    }

    fun refreshDiagnostics() {
        _actionStatus.update {
            it.copy(
                recentLogs = logger.readRecentLines(),
                ffmpegAvailable = ffmpegExecutor.isAvailable(),
            )
        }
    }

    fun clearLogs() {
        logger.clearLogs()
        _actionStatus.update { it.copy(recentLogs = emptyList(), statusMessage = "Logs locais limpos.") }
    }

    fun getExportLogUri(): Uri? = logger.getExportUri()
}
