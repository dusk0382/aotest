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
import androidx.compose.ui.text.font.FontFamily
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
 * AO3 Lector palette — Material 3 baseline (los valores canónicos de la
 * especificación de Material, no una paleta inventada).
 *
 * Motivo del cambio (v0.7.32): la paleta anterior ("Terracota & Serif") llevaba
 * un primary terracota #B03A2E y un secondary cacao #7C5825 con ratio de
 * contraste 1.06 entre sí — indistinguibles, marrón sobre marrón. Los colores
 * actuales son la familia estándar de Google: primary violeta #6750A4 para
 * acciones y selección, surfaces neutros que nunca llegan a negro puro, y
 * superficies SÓLIDAS (sin `copy(alpha=…)`) para que las sesiones largas de
 * lectura no produzcan halación.
 *
 * Todos los pares on-* / *-container superan WCAG AA (el mínimo es 5.48:1 en
 * dark onSurfaceVariant); la escala sólida de ratings/warnings va de 9.6:1 a
 * 11.2:1. Verificado con el linter de `@google/design.md`.
 *
 * El serif del sistema en display/headline/title se conserva: es la identidad
 * editorial y no cuesta peso en el APK.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF131318),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * Semantic colors used across the app (success = downloaded/read, favorite,
 * etc.) so screens stop hardcoding one-off hex values. `info` is a token, not
 * the brand accent — the brand accent is `primary` (see LightColors).
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val favorite: Color,
    val warning: Color,
    val info: Color,
)

private val LightSemantic = SemanticColors(
    success = Color(0xFF146C2E),
    onSuccess = Color(0xFFFFFFFF),
    favorite = Color(0xFFB3261E),
    warning = Color(0xFF8B5000),
    info = Color(0xFF1565C0),
)

private val DarkSemantic = SemanticColors(
    success = Color(0xFF6DD58C),
    onSuccess = Color(0xFF00391B),
    favorite = Color(0xFFFFB4AB),
    warning = Color(0xFFFFB86B),
    info = Color(0xFFA8C7FA),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/**
 * MD3 type scale for AO3 Lector. Sizes follow the spec (display 36, headline
 * 24-28, title 14-22, body 12-16, label 11-14) so screens never need raw
 * `fontSize` literals.
 *
 * Editorial identity: display/headline/titleLarge/titleMedium use the system
 * SERIF — the biggest identity lever with zero APK weight (the serif ships on
 * every Android since API 21). Body/labels stay sans for density and
 * legibility: work titles read like a book cover; metadata stays a UI.
 */
private val TitleSerif = FontFamily.Serif

val Ao3Typography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, fontFamily = TitleSerif),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = TitleSerif),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFamily = TitleSerif),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = TitleSerif),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = TitleSerif),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * MD3 shape tokens: chips = small (8dp), cards/surfaces = medium (12dp), large
 * sheets = large (16dp), dialogs/bottom sheets = extraLarge (28dp). Components
 * reference these instead of magic radii.
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
