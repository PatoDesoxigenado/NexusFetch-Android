package io.github.patodesoxigenado.nexusfetch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.service.JobOrchestrator
import io.github.patodesoxigenado.nexusfetch.ui.navigation.DeepLinkPayload
import io.github.patodesoxigenado.nexusfetch.ui.navigation.NexusDestination
import io.github.patodesoxigenado.nexusfetch.ui.navigation.NexusNavHost
import io.github.patodesoxigenado.nexusfetch.ui.theme.NexusFetchTheme
import javax.inject.Inject

/**
 * Primary Material 3 host activity supporting Edge-to-Edge and deep-link routing from ShareSheetActivity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var jobOrchestrator: JobOrchestrator

    private var pendingDeepLink by mutableStateOf<DeepLinkPayload?>(null)

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* User choice respected gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleDeepLinkIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            NexusFetchTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                NexusNavHost(
                    jobOrchestrator = jobOrchestrator,
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkConsumed = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_TARGET_ROUTE) ?: return
        val url = intent.getStringExtra(EXTRA_URL)
        val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        val uris = uriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

        pendingDeepLink = DeepLinkPayload(
            targetRoute = NexusDestination.fromRoute(route).route,
            url = url,
            uris = uris,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_ROUTE = "extra_target_route"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_URIS = "extra_uris"
    }
}
