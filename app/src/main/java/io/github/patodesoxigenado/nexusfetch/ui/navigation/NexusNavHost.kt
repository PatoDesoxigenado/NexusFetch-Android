package io.github.patodesoxigenado.nexusfetch.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import io.github.patodesoxigenado.nexusfetch.ui.components.AppUpdateBanner
import io.github.patodesoxigenado.nexusfetch.ui.components.JobProgressCard
import io.github.patodesoxigenado.nexusfetch.ui.screens.compressor.CompressorScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.compressor.CompressorViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.converter.ConverterScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.converter.ConverterViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.DownloaderScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.downloader.DownloaderViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.help.HelpScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.history.HistoryScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.history.HistoryViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.multi.MultiDownloadScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.multi.MultiDownloadViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.settings.SettingsScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.settings.SettingsViewModel
import io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer.TrimmerScreen
import io.github.patodesoxigenado.nexusfetch.ui.screens.trimmer.TrimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusNavHost(
    jobOrchestrator: JobOrchestrator,
    pendingDeepLink: DeepLinkPayload?,
    onDeepLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NexusDestination.DOWNLOADER.route
    val activeJobState by jobOrchestrator.activeState.collectAsStateWithLifecycle()
    var showMoreSheet by remember { mutableStateOf(false) }

    val downloaderVm: DownloaderViewModel = hiltViewModel()
    val multiVm: MultiDownloadViewModel = hiltViewModel()
    val compressorVm: CompressorViewModel = hiltViewModel()
    val trimmerVm: TrimmerViewModel = hiltViewModel()
    val converterVm: ConverterViewModel = hiltViewModel()
    val historyVm: HistoryViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settingsStatus by settingsVm.actionStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        settingsVm.checkAppUpdateOnLaunch()
    }

    LaunchedEffect(settingsStatus.installIntent) {
        settingsStatus.installIntent?.let { intent ->
            runCatching { context.startActivity(intent) }
            settingsVm.clearInstallIntent()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingDeepLink?.id) {
        val payload = pendingDeepLink ?: return@LaunchedEffect
        when (payload.targetRoute) {
            NexusDestination.DOWNLOADER.route -> payload.url?.let { downloaderVm.analyzeWithUrl(it) }
            NexusDestination.TRIMMER.route -> {
                if (!payload.url.isNullOrBlank()) {
                    trimmerVm.analyzeRemoteUrl(payload.url)
                } else if (payload.uris.isNotEmpty()) {
                    trimmerVm.loadLocalUri(payload.uris.first())
                }
            }
            NexusDestination.COMPRESSOR.route -> if (payload.uris.isNotEmpty()) {
                compressorVm.addUris(payload.uris)
            }
            NexusDestination.CONVERTER.route -> if (payload.uris.isNotEmpty()) {
                converterVm.addUris(payload.uris)
            }
        }
        navigateTo(payload.targetRoute)
        onDeepLinkConsumed()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NexusDestination.primaryTabs.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { navigateTo(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = {
                            Text(
                                text = dest.label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                val isMoreSelected = NexusDestination.moreDestinations.any { it.route == currentRoute }
                NavigationBarItem(
                    selected = isMoreSelected,
                    onClick = { showMoreSheet = true },
                    icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = "Mais") },
                    label = {
                        Text(
                            text = "Mais",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (settingsStatus.showTopUpdateBanner) {
                settingsStatus.availableUpdate?.let { update ->
                    AppUpdateBanner(
                        updateInfo = update,
                        isDownloading = settingsStatus.isBusy,
                        downloadProgress = settingsStatus.updateDownloadProgress,
                        errorMessage = settingsStatus.updateBannerError,
                        onDownloadAndInstall = settingsVm::downloadAndPrepareUpdate,
                        onDismiss = settingsVm::dismissTopUpdateBanner,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            JobProgressCard(
                state = activeJobState,
                onCancel = jobOrchestrator::cancelActiveJob,
                onDismiss = jobOrchestrator::clearCompletedBanner,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            NavHost(
                navController = navController,
                startDestination = NexusDestination.DOWNLOADER.route,
                modifier = Modifier.weight(1f),
            ) {
                composable(NexusDestination.DOWNLOADER.route) { DownloaderScreen(downloaderVm) }
                composable(NexusDestination.MULTI.route) { MultiDownloadScreen(multiVm) }
                composable(NexusDestination.COMPRESSOR.route) { CompressorScreen(compressorVm) }
                composable(NexusDestination.TRIMMER.route) { TrimmerScreen(trimmerVm) }
                composable(NexusDestination.CONVERTER.route) { ConverterScreen(converterVm) }
                composable(NexusDestination.HISTORY.route) { HistoryScreen(historyVm) }
                composable(NexusDestination.SETTINGS.route) { SettingsScreen(settingsVm) }
                composable(NexusDestination.HELP.route) { HelpScreen() }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Mais Ferramentas",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                NexusDestination.moreDestinations.forEach { dest ->
                    ListItem(
                        headlineContent = { Text(dest.label) },
                        leadingContent = { Icon(dest.icon, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showMoreSheet = false
                            navigateTo(dest.route)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
