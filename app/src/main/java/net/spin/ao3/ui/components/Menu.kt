package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Menú desplegable de la app: el popup de M3 envuelto en su grupo contenedor.
 *
 * `DropdownMenuPopup` a pelo es solo la ventana — sin Surface no dibuja
 * fondo ni sombra y los ítems «flotan» sobre el contenido (regresión real).
 * Aquí se envuelve en un `DropdownMenuGroup` único, que aporta el contenedor
 * sólido (groupStandardContainerColor), la forma de menú y la sombra.
 *
 * Para menús con varias secciones usa `DropdownMenuPopup` + varios
 * `DropdownMenuGroup` (ver el menú «Más opciones» del lector).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Ao3MenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
            content = content,
        )
    }
}
