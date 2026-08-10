package net.spin.ao3.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * AO3 Lector palette — "Cacao & Salvia" (M3 HCT tokens).
 *
 * A calm blue acts as the primary accent for actions/selection; warm cocoa
 * brown is the secondary accent (fandom tags, author names); sage green is
 * the tertiary accent (character tags, reading progress). Surfaces are warm
 * paper neutrals (H40/C04) that never reach pure white/black in normal mode
 * — long reading sessions stay comfortable and free of halation/blooming.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A60A5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF7C5825),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF1B6C3B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA7F4BC),
    onTertiaryContainer = Color(0xFF00210B),
    background = Color(0xFFFDFCF7),
    onBackground = Color(0xFF1A1B18),
    surface = Color(0xFFFDFCF7),
    onSurface = Color(0xFF1A1B18),
    surfaceVariant = Color(0xFFE5E2DA),
    onSurfaceVariant = Color(0xFF48473E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F4EC),
    surfaceContainer = Color(0xFFF1EEE6),
    surfaceContainerHigh = Color(0xFFEBE8E0),
    surfaceContainerHighest = Color(0xFFE5E2DA),
    outline = Color(0xFF79776E),
    outlineVariant = Color(0xFFC8C6BC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004783),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFE8C08C),
    onSecondary = Color(0xFF3E2B00),
    secondaryContainer = Color(0xFF5F4110),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFF82D7A0),
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005227),
    onTertiaryContainer = Color(0xFFA7F4BC),
    background = Color(0xFF1A1B18),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF1A1B18),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF41403B),
    onSurfaceVariant = Color(0xFFC8C6BC),
    surfaceContainerLowest = Color(0xFF121310),
    surfaceContainerLow = Color(0xFF20201C),
    surfaceContainer = Color(0xFF2B2A26),
    surfaceContainerHigh = Color(0xFF363530),
    surfaceContainerHighest = Color(0xFF41403B),
    outline = Color(0xFF929085),
    outlineVariant = Color(0xFF48473E),
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
    info = Color(0xFF1A60A5),
)

private val DarkSemantic = SemanticColors(
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF00391B),
    favorite = Color(0xFFEF9A9A),
    warning = Color(0xFFFFB74D),
    info = Color(0xFFA2C9FF),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/**
 * MD3 type scale for AO3 Lector. Sizes follow the spec (display 36, headline
 * 24-28, title 14-22, body 12-16, label 11-14) so screens never need raw
 * `fontSize` literals.
 */
val Ao3Typography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * MD3 shape tokens (Cacao & Salvia): chips = small (8dp), cards/surfaces =
 * medium (12dp), large sheets = large (16dp), dialogs/bottom sheets =
 * extraLarge (28dp). Components reference these instead of magic radii.
 */
val Ao3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun Ao3Theme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    /** Material You: wallpaper-derived colors on Android 12+ (opt-in). */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalSemanticColors provides (if (dark) DarkSemantic else LightSemantic),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Ao3Typography,
            shapes = Ao3Shapes,
            content = content,
        )
    }
}
