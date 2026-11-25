package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Target size presets calibrated for Discord and WhatsApp upload limits (exact parity with Desktop v1.4).
 */
enum class CompressionPreset(
    val key: String,
    val label: String,
    val limitMb: Double,
    val targetMb: Double,
) {
    DISCORD_10(
        key = "discord_10",
        label = "Discord Grátis (Limite 10 MB)",
        limitMb = 10.0,
        targetMb = 9.4,
    ),
    WHATSAPP_16(
        key = "whatsapp_16",
        label = "WhatsApp Vídeo (Limite 16 MB)",
        limitMb = 16.0,
        targetMb = 15.2,
    ),
    DISCORD_50(
        key = "discord_50",
        label = "Discord Nitro Basic (Limite 50 MB)",
        limitMb = 50.0,
        targetMb = 48.0,
    ),
    WHATSAPP_64(
        key = "whatsapp_64",
        label = "WhatsApp HD / Documento (Limite 64 MB)",
        limitMb = 64.0,
        targetMb = 61.5,
    );

    val tag: String
        get() = key.replace('_', '-')

    companion object {
        fun fromKey(key: String): CompressionPreset =
            entries.firstOrNull { it.key == key } ?: DISCORD_10
    }
}
