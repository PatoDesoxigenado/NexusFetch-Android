package io.github.patodesoxigenado.nexusfetch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.patodesoxigenado.nexusfetch.domain.model.ThemeMode

private val LightExtra = NexusExtraColors(
    converterAccent = ConverterVioletLight,
    converterBg = ConverterBgLight,
    trimmerAccent = TrimmerAmberLight,
    success = SuccessGreenLight,
    warning = TrimmerAmberLight,
    info = InfoBlueLight,
    cardSubtle = CardSubtleLight,
    inputSurface = InputLight,
    border = BorderLight,
)

private val DarkExtra = NexusExtraColors(
    converterAccent = ConverterVioletDark,
    converterBg = ConverterBgDark,
    trimmerAccent = TrimmerAmberDark,
    success = SuccessGreenDark,
    warning = TrimmerAmberDark,
    info = InfoBlueDark,
    cardSubtle = CardSubtleDark,
    inputSurface = InputDark,
    border = BorderDark,
)

val LocalNexusColors = staticCompositionLocalOf { DarkExtra }

private val NexusLightColorScheme = lightColorScheme(
    primary = NexusRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = ConverterVioletLight,
    onSecondary = Color.White,
    tertiary = TrimmerAmberLight,
    onTertiary = Color.White,
    background = BgLight,
    onBackground = TextMainLight,
    surface = CardLight,
    onSurface = TextMainLight,
    surfaceVariant = InputLight,
    onSurfaceVariant = TextMutedLight,
    outline = BorderLight,
    outlineVariant = BorderSubtleLight,
    error = DangerRedLight,
    onError = Color.White,
)

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C050A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = ConverterVioletDark,
    onSecondary = Color.White,
    tertiary = TrimmerAmberDark,
    onTertiary = Color.Black,
    background = BgDark,
    onBackground = TextMainDark,
    surface = CardDark,
    onSurface = TextMainDark,
    surfaceVariant = CardSubtleDark,
    onSurfaceVariant = TextMutedDark,
    outline = BorderDark,
    outlineVariant = BorderSubtleDark,
    error = DangerRedDark,
    onError = Color.White,
)

@Composable
fun NexusFetchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> NexusDarkColorScheme
        else -> NexusLightColorScheme
    }

    val extraColors = if (isDark) DarkExtra else LightExtra

    CompositionLocalProvider(LocalNexusColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            content = content,
        )
    }
}
