package net.spin.ao3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.ui.theme.Ao3Theme
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.formatCount

/**
 * Work card redesigned for dense scanning (M3):
 *  - Title top-left (16sp SemiBold) with the rating capsule pinned top-right.
 *  - Author prefixed by a small pen icon in [secondary].
 *  - Tags in one scrolling row, capped at 3 visible + a "+N más" chip that
 *    opens a bottom sheet with every tag grouped by category.
 *  - A stats bar (surfaceContainerLow) with icons for words / chapters /
 *    views / kudos, so cards stay compact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkCard(
    work: WorkSummary,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val colorScheme = MaterialTheme.colorScheme
    var showAllTags by remember { mutableStateOf(false) }

    val tagGroups = buildList {
        if (work.fandoms.isNotEmpty()) add(TagGroup("Fandoms", work.fandoms, FandomColor, TagChipVariant.FILLED_SECONDARY))
        if (work.characters.isNotEmpty()) add(TagGroup("Personajes", work.characters, CharacterColor, TagChipVariant.FILLED_TERTIARY))
        if (work.relationships.isNotEmpty()) add(TagGroup("Relaciones", work.relationships, RelationshipColor, TagChipVariant.OUTLINED))
        if (work.otherTags.isNotEmpty()) add(TagGroup("Etiquetas", work.otherTags, AdditionalColor, TagChipVariant.OUTLINED))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // ---- Header: title + author (left), rating capsule (top-right) ----
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = work.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = work.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (onAuthorClick != null) {
                                Modifier.clickable(onClick = onAuthorClick)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                work.rating?.let {
                    TagChip(it, ratingColor(work.ratingKey), variant = TagChipVariant.TINTED)
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- Tags: scrolling row, 3 visible + "+N más" overflow ----
            val chips = buildList {
                if (work.isCompleted) add(Triple("Completada", semantic.success, TagChipVariant.TINTED))
                work.fandoms.forEach { add(Triple(it, FandomColor, TagChipVariant.FILLED_SECONDARY)) }
                work.characters.forEach { add(Triple(it, CharacterColor, TagChipVariant.FILLED_TERTIARY)) }
                work.relationships.forEach { add(Triple(it, RelationshipColor, TagChipVariant.OUTLINED)) }
            }
            if (chips.isNotEmpty()) {
                // The "+N más" overflow chip must stay visible without scrolling, so
                // only the first 3 chips live inside the scrollable strip.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chips.take(3).forEach { (text, color, variant) ->
                            TagChip(
                                text = text,
                                color = color,
                                variant = variant,
                                onClick = onTagClick?.let { cb -> { cb(text) } },
                            )
                        }
                    }
                    if (chips.size > 3) {
                        TagChip(
                            text = "+${chips.size - 3} más",
                            color = colorScheme.secondary,
                            variant = TagChipVariant.TINTED,
                            onClick = { showAllTags = true },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (work.summary.isNotBlank()) {
                Text(
                    text = work.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
            }

            // ---- Stats bar with icons ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colorScheme.surfaceContainerLow)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(Icons.Filled.Description, "${formatCount(work.words)}", "palabras", Modifier.weight(1f))
                CellDivider()
                StatCell(
                    Icons.Filled.Layers,
                    if (work.chapterTotal == null) "${work.chapterCount}+" else "${work.chapterCount}/${work.chapterTotal}",
                    "caps",
                    Modifier.weight(1f),
                )
                CellDivider()
                StatCell(Icons.Filled.Visibility, "${formatCount(work.hits)}", "visitas", Modifier.weight(1f))
                CellDivider()
                StatCell(Icons.Filled.Favorite, "${formatCount(work.kudos)}", "kudos", Modifier.weight(1f))
            }
            work.updated?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Actualizado: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }

    if (showAllTags) {
        AllTagsSheet(
            groups = tagGroups,
            onTagClick = { tag ->
                showAllTags = false
                onTagClick?.invoke(tag)
            },
            onDismiss = { showAllTags = false },
        )
    }
}

private data class TagGroup(
    val name: String,
    val tags: List<String>,
    val color: androidx.compose.ui.graphics.Color,
    val variant: TagChipVariant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllTagsSheet(
    groups: List<TagGroup>,
    onTagClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Todos los tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Toca un tag para explorar más obras de ese tag.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            groups.forEach { group ->
                Spacer(Modifier.height(14.dp))
                Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.tags.forEach { tag ->
                        TagChip(
                            text = tag,
                            color = group.color,
                            variant = group.variant,
                            onClick = { onTagClick(tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.height(2.dp))
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

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun WorkCardPreview() {
    Ao3Theme {
        WorkCard(
            work = WorkSummary(
                id = 1,
                title = "La carta que nunca se envió",
                author = "Vichan",
                authorUrl = null,
                fandoms = listOf("Harry Potter"),
                rating = "Teen And Up Audiences",
                ratingKey = "teen-and-up-audiences",
                warnings = listOf("No Archive Warnings Apply"),
                categories = listOf("M/M"),
                otherTags = listOf("Fluff", "Post-Canon", "Slow Burn"),
                relationships = listOf("Draco Malfoy/Hermione Granger"),
                characters = listOf("Hermione Granger", "Draco Malfoy"),
                summary = "Una carta encuentra su camino años después de la guerra, y con ella una verdad que cambia todo.",
                words = 12_453,
                chapterCount = 3,
                chapterTotal = 12,
                hits = 2_301,
                kudos = 210,
                comments = 34,
                bookmarks = 8,
                published = "2024-01-01",
                updated = "2026-08-01",
                url = "",
            ),
            onClick = {},
            onTagClick = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
