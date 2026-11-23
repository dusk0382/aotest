package net.spin.ao3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sello Expressive: un icono dentro de una forma de la librería
 * MaterialShapes (Cookie9Sided, Heart, Pill...). Es el sistema de sellos de
 * la app: Ajustes, Biblioteca y estados vacíos comparten la misma pieza, así
 * que una forma y un rol tonal significan lo mismo en toda la interfaz.
 *
 * El sello siempre representa algo del dominio (favorito = corazón,
 * historial = reloj, descarga = píldora con flecha): nunca blob decorativo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeStamp(
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    stampSize: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(stampSize)
            .clip(shape.toShape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Formas del sello por estado, para reutilizar en las llamadas. */
object StampShapes {
    // Favorito usa Cookie9Sided como el resto: el corazón de MaterialShapes
    // con una estrella dentro se leyó confuso en el dispositivo (doble
    // símbolo de cariño) y el usuario lo rechazó.
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val Favorito: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val Historial: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val Descarga: RoundedPolygon get() = MaterialShapes.Pill
}
