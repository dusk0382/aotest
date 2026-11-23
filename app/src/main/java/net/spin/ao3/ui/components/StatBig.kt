package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Cifra + etiqueta de las estadísticas de una obra. Compartida por la ficha
 * y las tarjetas de resultado: una sola voz numérica en toda la app.
 */
@Composable
fun StatBig(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}

/**
 * Bloque de estadísticas de la obra (palabras · capítulos · visitas · kudos):
 * la misma pieza que la ficha muestra bajo los tags. [dense] lo compacta para
 * las tarjetas de búsqueda (menos aire, cifras en titleSmall).
 */
@Composable
fun WorkStatsBlock(
    words: String,
    chapters: String,
    hits: String,
    kudos: String,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    // En la ficha (no dense) el bloque lleva color propio: los números clave
    // son el segundo acento de la pantalla (secondaryContainer). En las
    // tarjetas de búsqueda se queda en superficie para no competir.
    val container = if (dense) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.secondaryContainer
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = if (dense) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dense) {
                StatBigDense(words, "palabras", Modifier.weight(1f))
                StatBigDense(chapters, "capítulos", Modifier.weight(1f))
                StatBigDense(hits, "visitas", Modifier.weight(1f))
                StatBigDense(kudos, "kudos", Modifier.weight(1f))
            } else {
                val labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                StatBig(words, "palabras", Modifier.weight(1f), labelColor)
                StatBig(chapters, "capítulos", Modifier.weight(1f), labelColor)
                StatBig(hits, "visitas", Modifier.weight(1f), labelColor)
                StatBig(kudos, "kudos", Modifier.weight(1f), labelColor)
            }
        }
    }
}

@Composable
private fun StatBigDense(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
