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
 * AO3 Lector palette — warm editorial look with a single brand accent
 * (terracotta red). Fixed on purpose: dynamic color made the app change
 * palette per device and feel chaotic.
 *
 * Neutrals are warm paper tones (never pure white / pure black) so long
 * reading sessions are comfortable.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFB03A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3B0904),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF63602B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9E5A4),
    onTertiaryContainer = Color(0xFF1E1C00),
    background = Color(0xFFFBF7F3),
    onBackground = Color(0xFF221A17),
    surface = Color(0xFFFBF7F3),
    onSurface = Color(0xFF221A17),
    surfaceVariant = Color(0xFFF1E2DB),
    onSurfaceVariant = Color(0xFF52443F),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A7),
    onPrimary = Color(0xFF5F150B),
    primaryContainer = Color(0xFF7F2D22),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFCDC98D),
    onTertiary = Color(0xFF343106),
    tertiaryContainer = Color(0xFF4B4819),
    onTertiaryContainer = Color(0xFFE9E5A4),
    background = Color(0xFF16110F),
    onBackground = Color(0xFFF0DED7),
    surface = Color(0xFF16110F),
    onSurface = Color(0xFFF0DED7),
    surfaceVariant = Color(0xFF52443F),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08C85),
    outlineVariant = Color(0xFF53443F),
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
    favorite = Color(0xFFD32F2F),
    warning = Color(0xFFE65100),
    info = Color(0xFF1E6FD9),
)

private val DarkSemantic = SemanticColors(
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF00391B),
    favorite = Color(0xFFEF9A9A),
    warning = Color(0xFFFFB74D),
    info = Color(0xFF90CAF9),
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
