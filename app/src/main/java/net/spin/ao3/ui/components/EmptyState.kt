package net.spin.ao3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.ui.theme.Ao3Theme

/**
 * Friendly empty state: an icon inside a soft circle, a title, a description and
 * an optional call-to-action. [compact] renders it as an inline card for sections
 * inside scrolling lists (e.g. "Continuar leyendo"); the default renders centered.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (compact) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(icon)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 36.dp),
            ) {
                IconCircle(icon, large = true)
                Spacer(Modifier.height(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun IconCircle(icon: ImageVector, large: Boolean = false) {
    val colorScheme = MaterialTheme.colorScheme
    val circle = if (large) 72.dp else 44.dp
    val iconSize = if (large) 30.dp else 20.dp
    Box(
        modifier = Modifier
            .size(circle)
            .background(colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun EmptyStateCompactPreview() {
    Ao3Theme {
        Column(Modifier.padding(12.dp)) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "Sin favoritos todavía",
                description = "Marca obras con la estrella en su detalle para tenerlas aquí, siempre a mano.",
                actionLabel = "Explorar tendencias",
                onAction = {},
                compact = true,
            )
        }
    }
}
