package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Cifra + etiqueta de las estadísticas de una obra. Compartida por la ficha
 * y las tarjetas de resultado: una sola voz numérica en toda la app.
 */
@Composable
fun StatBig(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Bloque de estadísticas de la obra (palabras · capítulos · visitas · kudos):
 * la misma pieza que la ficha muestra bajo los tags, reutilizable en las
 * tarjetas de búsqueda para que «el medio de la tarjeta» hable el mismo
 * idioma visual que el detalle.
 */
@Composable
fun WorkStatsBlock(
    words: String,
    chapters: String,
    hits: String,
    kudos: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatBig(words, "palabras", Modifier.weight(1f))
            StatBig(chapters, "capítulos", Modifier.weight(1f))
            StatBig(hits, "visitas", Modifier.weight(1f))
            StatBig(kudos, "kudos", Modifier.weight(1f))
        }
    }
}
