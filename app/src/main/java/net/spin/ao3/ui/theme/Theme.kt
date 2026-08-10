package net.spin.ao3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * App theme mode (Settings -> Apariencia). The reader has its own per-work
 * themes (light/sepia/dark/AMOLED) on top of this.
 */
enum class AppThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro");

    companion object {
        fun from(name: String?): AppThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * AO3 Lector palette — warm paper neutrals + a calm forest-green accent.
 *
 * Chosen after design research: green sits in the middle of the visible
 * spectrum (easiest on the eyes for long reading sessions) and evokes a quiet
 * library. Neutrals stay warm paper tones (never pure white / pure black) so
 * long reading sessions are comfortable. The old terracotta red was replaced
 * because it felt harsh and clashed with the warm paper.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5128),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8E8B4),
    onPrimaryContainer = Color(0xFF003914),
    secondary = Color(0xFF6E583A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7DEBC),
    onSecondaryContainer = Color(0xFF251A07),
    tertiary = Color(0xFF5E6300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E98D),
    onTertiaryContainer = Color(0xFF1B1D00),
    background = Color(0xFFFBF7F3),
    onBackground = Color(0xFF1F1B18),
    surface = Color(0xFFF6F1EC),
    onSurface = Color(0xFF1F1B18),
    surfaceVariant = Color(0xFFEDE3DA),
    onSurfaceVariant = Color(0xFF52443C),
    surfaceContainer = Color(0xFFEDE6DF),
    surfaceContainerHigh = Color(0xFFE7DED6),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C4BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD8A6),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF1D5427),
    onPrimaryContainer = Color(0xFFB4F0BE),
    secondary = Color(0xFFDDC09D),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF55432B),
    onSecondaryContainer = Color(0xFFF7DEBC),
    tertiary = Color(0xFFC6CC74),
    onTertiary = Color(0xFF303300),
    tertiaryContainer = Color(0xFF464B00),
    onTertiaryContainer = Color(0xFFE3E98D),
    background = Color(0xFF16110F),
    onBackground = Color(0xFFEFE6E0),
    surface = Color(0xFF1D1714),
    onSurface = Color(0xFFEFE6E0),
    surfaceVariant = Color(0xFF4A4138),
    onSurfaceVariant = Color(0xFFD8C4BA),
    surfaceContainer = Color(0xFF241E1C),
    surfaceContainerHigh = Color(0xFF2F2826),
    outline = Color(0xFFA08C85),
    outlineVariant = Color(0xFF53443C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Semantic colors used across the app (success = downloaded/read, favorite,
 * etc.) so screens stop hardcoding one-off hex values.
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val favorite: Color,
    val warning: Color,
    val info: Color,
)

private val LightSemantic = SemanticColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    favorite = Color(0xFFB3261E),
    warning = Color(0xFFE65100),
    info = Color(0xFF2F6B35),
)

private val DarkSemantic = SemanticColors(
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF00391B),
    favorite = Color(0xFFEF9A9A),
    warning = Color(0xFFFFB74D),
    info = Color(0xFF9AD8A6),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

val Ao3Typography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun Ao3Theme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    CompositionLocalProvider(
        LocalSemanticColors provides (if (dark) DarkSemantic else LightSemantic),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Ao3Typography,
            content = content,
        )
    }
}
