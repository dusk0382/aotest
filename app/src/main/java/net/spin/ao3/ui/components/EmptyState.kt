package net.spin.ao3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.ui.theme.Ao3Theme

/**
 * Estado vacío Expressive: icono en sello MaterialShapes (compact lo usa
 * pequeño y sin sello), titular y descripción corta, y un único CTA relleno
 * cuando hay acción. [compact] lo renderiza en línea dentro de una sección.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val iconStamp: @Composable () -> Unit = if (compact) {
        {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    } else {
        // Sello Expressive: icono dentro de una forma de la librería
        // MaterialShapes (contenedor tonal, par de roles onSecondaryContainer).
        // Entra con escala + fade del motionScheme: el sello «se planta».
        {
            val appearSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            val appear = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(Unit) {
                appear.animateTo(1f, appearSpec)
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        val eased = appear.value
                        scaleX = 0.7f + 0.3f * eased
                        scaleY = 0.7f + 0.3f * eased
                        alpha = eased
                    }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
    val body: @Composable () -> Unit = {
        iconStamp()
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            textAlign = if (compact) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = if (compact) TextAlign.Start else TextAlign.Center,
                maxLines = 3,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryCtaButton(onClick = onAction) { Text(actionLabel) }
        }
    }
    if (compact) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp)) { body() }
        }
    } else {
        androidx.compose.foundation.layout.Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp),
            ) { body() }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun EmptyStateCompactPreview() {
    Ao3Theme {
        Column(Modifier.padding(12.dp)) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "Sin favoritos",
                description = "Guarda obras para leerlas después.",
                actionLabel = "Explorar tendencias",
                onAction = {},
                compact = true,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun EmptyStatePreview() {
    Ao3Theme {
        EmptyState(
            icon = Icons.Filled.Star,
            title = "Sin favoritos",
            description = "Guarda obras para leerlas después.",
            actionLabel = "Explorar tendencias",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStateDarkScaledPreview() {
    Ao3Theme {
        EmptyState(
            icon = Icons.Filled.Star,
            title = "Sin favoritos en esta biblioteca tan vacía",
            description = "Guarda obras para leerlas después cuando tengas conexión.",
            actionLabel = "Explorar tendencias",
            onAction = {},
        )
    }
}
