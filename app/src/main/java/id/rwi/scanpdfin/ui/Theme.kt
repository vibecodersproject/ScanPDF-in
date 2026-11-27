package id.rwi.scanpdfin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import id.rwi.scanpdfin.data.ThemeMode

// Palet biru-hijau (teal) buatan sendiri
private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF2EC),
    onPrimaryContainer = Color(0xFF05302C),
    secondary = Color(0xFF2E6B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5EFEA),
    onSecondaryContainer = Color(0xFF0D2E2A),
    tertiary = Color(0xFF3F6E9A),
    tertiaryContainer = Color(0xFFD6E6F7),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF151D1C),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFDDE7E4),
    onSurfaceVariant = Color(0xFF3E4A48),
    surfaceContainer = Color(0xFFEAF2F0),
    surfaceContainerHigh = Color(0xFFE2ECEA),
    surfaceContainerHighest = Color(0xFFDBE6E3),
    outline = Color(0xFF6E7B78),
    outlineVariant = Color(0xFFC1CCC9),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD4C6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0B5B54),
    onPrimaryContainer = Color(0xFFBCEFE7),
    secondary = Color(0xFFA7D5CC),
    onSecondary = Color(0xFF123B35),
    secondaryContainer = Color(0xFF2A4F49),
    onSecondaryContainer = Color(0xFFC6F1E8),
    tertiary = Color(0xFFA8C8EC),
    tertiaryContainer = Color(0xFF26486C),
    background = Color(0xFF101514),
    onBackground = Color(0xFFE1E6E4),
    surface = Color(0xFF101514),
    onSurface = Color(0xFFE1E6E4),
    surfaceVariant = Color(0xFF3E4A48),
    onSurfaceVariant = Color(0xFFBDC9C6),
    surfaceContainer = Color(0xFF1B2220),
    surfaceContainerHigh = Color(0xFF252D2B),
    surfaceContainerHighest = Color(0xFF303836),
    outline = Color(0xFF879390),
    outlineVariant = Color(0xFF3E4A48),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SISTEM -> isSystemInDarkTheme()
        ThemeMode.TERANG -> false
        ThemeMode.GELAP -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
