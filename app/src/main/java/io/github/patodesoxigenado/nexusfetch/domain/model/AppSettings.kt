package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Theme preference supported by NexusFetch Material 3 UI.
 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Escuro");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Persisted user preferences backed by Jetpack DataStore.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val wifiOnly: Boolean = false,
    val warnOnCellular: Boolean = true,
    val defaultVideoQualityHeight: Int? = 1080,
    val ytDlpVersion: String = "Não verificada",
    val lastYtDlpUpdateEpochMs: Long = 0L,
    val lastAppUpdateCheckEpochMs: Long = 0L,
    val dismissedUpdateVersionTag: String = "",
)
