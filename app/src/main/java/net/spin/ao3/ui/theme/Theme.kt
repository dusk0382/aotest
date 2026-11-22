package net.spin.ao3.ui.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spin.ao3.R

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

// La identidad azul tinta + hielo se genera en `ColorSchemes.kt` a partir de:
//   semilla de marca #243B6B, hielo #4B8296,
//   semánticos success #237A57 / favorite #B83B5E / warning #9B6400 / info #315DA8
// con TonalSpot + SPEC_2025 y contraste 0 / 0.5 / 1 (light y dark).
private const val LightSurfaceHex = 0xFFF4F6F8
private const val DarkSurfaceHex = 0xFF111820

/** Contraste del sistema (API 34+); en versiones anteriores, neutro 0.0. */
@Composable
private fun rememberSystemContrast(): Double {
    val context = LocalContext.current
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(UiModeManager::class.java)
            manager?.contrast?.toDouble() ?: 0.0
        } else {
            0.0
        }
    }
}

/** El contraste continuo del sistema se ajusta al cubo exportado más cercano. */
private fun contrastBucket(contrast: Double): Int = when {
    contrast < 0.25 -> 0
    contrast < 0.75 -> 1
    else -> 2
}

private fun brandScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> BrandDarkC0
    dark && bucket == 1 -> BrandDarkC05
    dark -> BrandDarkC1
    bucket == 0 -> BrandLightC0
    bucket == 1 -> BrandLightC05
    else -> BrandLightC1
}

private fun iceScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> IceDarkC0
    dark && bucket == 1 -> IceDarkC05
    dark -> IceDarkC1
    bucket == 0 -> IceLightC0
    bucket == 1 -> IceLightC05
    else -> IceLightC1
}

private fun successScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> SuccessDarkC0
    dark && bucket == 1 -> SuccessDarkC05
    dark -> SuccessDarkC1
    bucket == 0 -> SuccessLightC0
    bucket == 1 -> SuccessLightC05
    else -> SuccessLightC1
}

private fun favoriteScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> FavoriteDarkC0
    dark && bucket == 1 -> FavoriteDarkC05
    dark -> FavoriteDarkC1
    bucket == 0 -> FavoriteLightC0
    bucket == 1 -> FavoriteLightC05
    else -> FavoriteLightC1
}

private fun warningScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> WarningDarkC0
    dark && bucket == 1 -> WarningDarkC05
    dark -> WarningDarkC1
    bucket == 0 -> WarningLightC0
    bucket == 1 -> WarningLightC05
    else -> WarningLightC1
}

private fun infoScheme(dark: Boolean, bucket: Int): ColorScheme = when {
    dark && bucket == 0 -> InfoDarkC0
    dark && bucket == 1 -> InfoDarkC05
    dark -> InfoDarkC1
    bucket == 0 -> InfoLightC0
    bucket == 1 -> InfoLightC05
    else -> InfoLightC1
}

/**
 * Semantic colors used across the app (success = downloaded/read, favorite,
 * etc.). Se generan con sus propias semillas; `info` es un token, no el acento
 * de marca (que es `primary`). La firma se conserva: los campos sin `on*` se
 * rellenan con el `primary` del esquema generado de cada semilla.
 */
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val favorite: Color,
    val warning: Color,
    val info: Color,
)

private val DefaultSemantic = SemanticColors(
    success = Color(0xFF237A57),
    onSuccess = Color(0xFFFFFFFF),
    favorite = Color(0xFFB83B5E),
    warning = Color(0xFF9B6400),
    info = Color(0xFF315DA8),
)

val LocalSemanticColors = staticCompositionLocalOf { DefaultSemantic }

/**
 * Google Sans Flex para la escala utilitaria (Title Medium/Small, Body y Label).
 * El lector NO usa esta escala: tiene la suya en ReaderTheme.
 */
private val AppSans = FontFamily(
    Font(R.font.gs_regular, FontWeight.Normal),
    Font(R.font.gs_medium, FontWeight.Medium),
    Font(R.font.gs_semibold, FontWeight.SemiBold),
    Font(R.font.gs_bold, FontWeight.Bold),
)

/** Fraunces estático (TTF instanciado en build time, sin FontVariation en runtime). */
private val AppEditorial = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
)

private fun editorial(size: TextUnit, lineHeight: TextUnit, emphasized: Boolean = false) = TextStyle(
    fontSize = size,
    lineHeight = lineHeight,
    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
    fontFamily = AppEditorial,
)

/**
 * MD3 Expressive type scale. Las medidas M3 se conservan; cambia la familia:
 * Fraunces en los seis Display, seis Headline y titleLarge (y sus variantes
 * *Emphasized en Bold); Google Sans Flex en el resto de la escala.
 */
val Ao3Typography = Typography(fontFamily = AppSans).copy(
    displayLarge = editorial(57.sp, 64.sp),
    displayLargeEmphasized = editorial(57.sp, 64.sp, emphasized = true),
    displayMedium = editorial(45.sp, 52.sp),
    displayMediumEmphasized = editorial(45.sp, 52.sp, emphasized = true),
    displaySmall = editorial(36.sp, 44.sp),
    displaySmallEmphasized = editorial(36.sp, 44.sp, emphasized = true),
    headlineLarge = editorial(32.sp, 40.sp),
    headlineLargeEmphasized = editorial(32.sp, 40.sp, emphasized = true),
    headlineMedium = editorial(28.sp, 36.sp),
    headlineMediumEmphasized = editorial(28.sp, 36.sp, emphasized = true),
    headlineSmall = editorial(24.sp, 32.sp),
    headlineSmallEmphasized = editorial(24.sp, 32.sp, emphasized = true),
    titleLarge = editorial(22.sp, 28.sp),
    titleLargeEmphasized = editorial(22.sp, 28.sp, emphasized = true),
)

/**
 * MD3 shape tokens con los radios Expressive: chips 8dp, tarjetas 12/20dp,
 * hero 32dp, diálogos/hojas 28dp y superiores.
 */
val Ao3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val bucket = contrastBucket(rememberSystemContrast())
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val brand = brandScheme(dark, bucket)
    val ice = iceScheme(dark, bucket)

    val colorScheme = if (useDynamic) {
        // Material You: esquema del sistema SIN overrides de marca.
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        brand.copy(
            // Secondary = ice.primary family, Tertiary = ice.secondary family.
            secondary = ice.primary,
            onSecondary = ice.onPrimary,
            secondaryContainer = ice.primaryContainer,
            onSecondaryContainer = ice.onPrimaryContainer,
            tertiary = ice.secondary,
            onTertiary = ice.onSecondary,
            tertiaryContainer = ice.secondaryContainer,
            onTertiaryContainer = ice.onSecondaryContainer,
            // Superficies sólidas de la identidad; on-* del esquema base.
            surface = Color(if (dark) DarkSurfaceHex else LightSurfaceHex),
            background = Color(if (dark) DarkSurfaceHex else LightSurfaceHex),
            onSurface = brand.onSurface,
            onSurfaceVariant = brand.onSurfaceVariant,
            onBackground = brand.onBackground,
        )
    }

    val success = successScheme(dark, bucket)
    val favorite = favoriteScheme(dark, bucket)
    val warning = warningScheme(dark, bucket)
    val info = infoScheme(dark, bucket)
    val semantic = SemanticColors(
        success = success.primary,
        onSuccess = success.onPrimary,
        favorite = favorite.primary,
        warning = warning.primary,
        info = info.primary,
    )

    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = Ao3Typography,
            shapes = Ao3Shapes,
            content = {
                // Los textos que no fijan color heredan LocalContentColor. Fijarlo
                // aquí evita que queden con un tono claro sobre superficies claras
                // después de recomponer el árbol (jugaba con el crossfade de tabs).
                CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
                    content()
                }
            },
        )
    }
}
