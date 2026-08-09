package net.spin.ao3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.util.formatCount

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkCard(
    work: WorkSummary,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = work.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))

            // Chips: rating + completion are tinted; the rest carry a category dot.
            val chips = buildList {
                work.rating?.let { add(Triple(it, ratingColor(work.ratingKey), true)) }
                if (work.isCompleted) add(Triple("Completada", Color(0xFF2E7D32), true))
                work.fandoms.take(2).forEach { add(Triple(it, FandomColor, false)) }
                work.characters.take(3).forEach { add(Triple(it, CharacterColor, false)) }
                work.relationships.take(1).forEach { add(Triple(it, RelationshipColor, false)) }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { (text, color, tinted) ->
                        TagChip(
                            text = text,
                            color = color,
                            tinted = tinted,
                            onClick = onTagClick?.let { cb -> { cb(text) } },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (work.summary.isNotBlank()) {
                Text(
                    text = work.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell("${formatCount(work.words)}", "palabras", Modifier.weight(1f))
                CellDivider()
                StatCell(
                    if (work.chapterTotal == null) "${work.chapterCount}+" else "${work.chapterCount}/${work.chapterTotal}",
                    "caps",
                    Modifier.weight(1f),
                )
                CellDivider()
                StatCell("${formatCount(work.hits)}", "visitas", Modifier.weight(1f))
                CellDivider()
                StatCell("${formatCount(work.kudos)}", "kudos", Modifier.weight(1f))
            }
            work.updated?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Actualizado: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CellDivider() {
    HorizontalDivider(
        modifier = Modifier.width(1.dp).height(30.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
