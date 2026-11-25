package io.github.patodesoxigenado.nexusfetch

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.patodesoxigenado.nexusfetch.data.local.datastore.SettingsRepository
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.ui.screens.share.QuickShareBottomSheet
import io.github.patodesoxigenado.nexusfetch.ui.screens.share.QuickShareViewModel
import io.github.patodesoxigenado.nexusfetch.ui.theme.NexusFetchTheme
import javax.inject.Inject

/**
 * Translucent floating Share Sheet activity triggered via `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
 * from YouTube, Instagram, Twitter/X, Gallery, or File Managers.
 */
@AndroidEntryPoint
class ShareSheetActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    private val viewModel: QuickShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseIncomingIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            NexusFetchTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                QuickShareBottomSheet(
                    state = state,
                    onSelectQuality = viewModel::selectQuality,
                    onQuickDownload = { mode ->
                        if (viewModel.dispatchQuickDownload(mode)) {
                            Toast.makeText(this, "Download iniciado em segundo plano!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                    onOpenInApp = { route, url, uris ->
                        launchMainApp(route, url, uris)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun parseIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    viewModel.handleSharedText(text)
                    return
                }
                val streamUri = readParcelableUri(intent, Intent.EXTRA_STREAM)
                viewModel.handleSharedUris(listOfNotNull(streamUri))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = readParcelableUriList(intent, Intent.EXTRA_STREAM)
                viewModel.handleSharedUris(uris)
            }
            else -> finish()
        }
    }

    private fun launchMainApp(route: String, url: String?, uris: List<Uri>) {
        val target = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TARGET_ROUTE, route)
            if (!url.isNullOrBlank()) putExtra(MainActivity.EXTRA_URL, url)
            if (uris.isNotEmpty()) {
                putStringArrayListExtra(MainActivity.EXTRA_URIS, ArrayList(uris.map { it.toString() }))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clip = ClipData.newUri(contentResolver, "shared_media", uris.first())
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                clipData = clip
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(target)
    }

    @Suppress("DEPRECATION")
    private fun readParcelableUri(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }

    @Suppress("DEPRECATION")
    private fun readParcelableUriList(intent: Intent, key: String): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
        }
}
