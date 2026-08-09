package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    footer: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = work.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = work.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            val chips = buildList {
                work.rating?.let { add(it to ratingColor(work.ratingKey)) }
                work.warnings.take(1).forEach { add(it to WarningColor) }
                work.categories.take(2).forEach { add(it to CategoryColor) }
                work.fandoms.take(2).forEach { add(it to FandomColor) }
                work.relationships.take(2).forEach { add(it to RelationshipColor) }
                work.characters.take(3).forEach { add(it to CharacterColor) }
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { (text, color) -> TagChip(text, color) }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (work.summary.isNotBlank()) {
                Text(
                    text = work.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatText("${formatCount(work.words)} palabras")
                StatText(
                    if (work.chapterTotal == null) "${work.chapterCount}+ caps"
                    else "${work.chapterCount}/${work.chapterTotal} caps",
                )
                StatText("${formatCount(work.hits)} visitas")
                StatText("${formatCount(work.kudos)} kudos")
            }
            work.updated?.let {
                Spacer(Modifier.height(6.dp))
                StatText("Actualizado: $it")
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun StatText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
