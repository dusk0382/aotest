package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** CTA principal relleno, 56 dp de altura con formas Expressive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PrimaryCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(56.dp),
        modifier = modifier.height(56.dp),
        enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
        content = content,
    )
}

/** Acción secundaria tonal, 40 dp (cargar más, limpiar, pausar…). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SecondaryTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(40.dp),
        modifier = modifier.height(40.dp),
        enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(40.dp),
        content = content,
    )
}

/** Acción secundaria outlined, 40 dp (importar, abrir perfil…). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SecondaryOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(40.dp),
        modifier = modifier.height(40.dp),
        enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(40.dp),
        content = content,
    )
}
