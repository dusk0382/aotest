package net.spin.ao3.ui.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.ui.theme.Ao3Theme

/**
 * Estado vacío Expressive: icono 24 dp (sin círculo), titular headlineSmall,
 * descripción corta y un único CTA relleno cuando hay acción. [compact] lo
 * renderiza en línea dentro de una sección; por defecto va centrado y acotado.
 */
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
    val body: @Composable () -> Unit = {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
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
