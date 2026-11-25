package io.github.patodesoxigenado.nexusfetch.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.patodesoxigenado.nexusfetch.domain.model.AppSettings
import io.github.patodesoxigenado.nexusfetch.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_settings")

/**
 * Privacy-by-design user preferences backed by transactional Jetpack DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val WARN_ON_CELLULAR = booleanPreferencesKey("warn_on_cellular")
        val DEFAULT_QUALITY_HEIGHT = intPreferencesKey("default_quality_height")
        val YTDLP_VERSION = stringPreferencesKey("ytdlp_version")
        val LAST_YTDLP_UPDATE_MS = longPreferencesKey("last_ytdlp_update_ms")
        val LAST_APP_UPDATE_CHECK_MS = longPreferencesKey("last_app_update_check_ms")
        val DISMISSED_UPDATE_VERSION_TAG = stringPreferencesKey("dismissed_update_version_tag")
    }

    val settings: Flow<AppSettings> = context.nexusDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            val savedHeight = prefs[Keys.DEFAULT_QUALITY_HEIGHT] ?: 1080
            AppSettings(
                themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
                wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
                warnOnCellular = prefs[Keys.WARN_ON_CELLULAR] ?: true,
                defaultVideoQualityHeight = if (savedHeight <= 0) null else savedHeight,
                ytDlpVersion = prefs[Keys.YTDLP_VERSION] ?: "Empacotada (2025.11.12)",
                lastYtDlpUpdateEpochMs = prefs[Keys.LAST_YTDLP_UPDATE_MS] ?: 0L,
                lastAppUpdateCheckEpochMs = prefs[Keys.LAST_APP_UPDATE_CHECK_MS] ?: 0L,
                dismissedUpdateVersionTag = prefs[Keys.DISMISSED_UPDATE_VERSION_TAG].orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.nexusDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.nexusDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.nexusDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setWarnOnCellular(enabled: Boolean) {
        context.nexusDataStore.edit { it[Keys.WARN_ON_CELLULAR] = enabled }
    }

    suspend fun setDefaultVideoQualityHeight(height: Int?) {
        context.nexusDataStore.edit { it[Keys.DEFAULT_QUALITY_HEIGHT] = height ?: 0 }
    }

    suspend fun recordYtDlpVersion(version: String) {
        context.nexusDataStore.edit {
            it[Keys.YTDLP_VERSION] = version
            it[Keys.LAST_YTDLP_UPDATE_MS] = System.currentTimeMillis()
        }
    }

    suspend fun recordAppUpdateCheck(epochMs: Long) {
        context.nexusDataStore.edit {
            it[Keys.LAST_APP_UPDATE_CHECK_MS] = epochMs
        }
    }

    suspend fun setDismissedUpdateVersionTag(versionTag: String) {
        context.nexusDataStore.edit {
            it[Keys.DISMISSED_UPDATE_VERSION_TAG] = versionTag
        }
    }
}
