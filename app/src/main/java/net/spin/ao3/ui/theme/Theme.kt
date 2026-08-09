package net.spin.ao3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AO3 Lector palette — warm editorial look with a single brand accent
 * (terracotta red). Fixed on purpose: dynamic color made the app change
 * palette per device and feel chaotic.
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
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A17),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A17),
    surfaceVariant = Color(0xFFF5DED7),
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
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFF0DED7),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFF0DED7),
    surfaceVariant = Color(0xFF53443F),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08C85),
    outlineVariant = Color(0xFF53443F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun Ao3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
