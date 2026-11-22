package net.spin.ao3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.R
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.ui.theme.Ao3Theme
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.formatCount

/**
 * Tarjeta de obra: una fila Surface sólida, título editorial dominante, autor
 * clicable, rating/advertencia sólidos, tags neutros, resumen corto y una sola
 * fila de estadísticas tabulares (sin tarjeta interior ni divisores).
 */
@Composable
fun WorkCard(
    work: WorkSummary,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    val colors = MaterialTheme.colorScheme
    var showAllTags by remember { mutableStateOf(false) }

    val strFandoms = stringResource(R.string.wc_fandoms)
    val strCharacters = stringResource(R.string.wc_characters)
    val strRelationships = stringResource(R.string.wc_relationships)
    val strTags = stringResource(R.string.wc_tags)
    val strWarnings = stringResource(R.string.wc_warnings)
    val strCompleted = stringResource(R.string.wc_completed)
    val tagGroups = remember(work) {
        buildList {
            if (work.fandoms.isNotEmpty()) add(TagGroup(strFandoms, work.fandoms, FandomColor, TagChipVariant.NEUTRAL))
            if (work.characters.isNotEmpty()) add(TagGroup(strCharacters, work.characters, CharacterColor, TagChipVariant.NEUTRAL))
            if (work.relationships.isNotEmpty()) add(TagGroup(strRelationships, work.relationships, RelationshipColor, TagChipVariant.OUTLINED))
            val freeforms = work.otherTags.filterNot { it in work.relationships || it in work.characters }
            if (freeforms.isNotEmpty()) add(TagGroup(strTags, freeforms, AdditionalColor, TagChipVariant.OUTLINED))
            if (work.warnings.isNotEmpty()) add(TagGroup(strWarnings, work.warnings, WarningColor, TagChipVariant.NEUTRAL))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = work.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = work.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    work.rating?.let {
                        val (bg, fg) = ratingSolidColors(work.ratingKey)
                        TagChipSolid(it, bg, fg)
                    }
                    work.warnings.firstOrNull()?.let {
                        Spacer(Modifier.height(4.dp))
                        val (bg, fg) = warningSolidColors()
                        TagChipSolid(it, bg, fg)
                    }
                }
            }

            val chips = remember(work, onTagClick) {
                buildList {
                    if (work.isCompleted) add(Triple(strCompleted, semantic.success, TagChipVariant.NEUTRAL))
                    work.fandoms.take(2).forEach { add(Triple(it, FandomColor, TagChipVariant.NEUTRAL)) }
                    work.characters.take(2).forEach { add(Triple(it, CharacterColor, TagChipVariant.NEUTRAL)) }
                    work.relationships.forEach { add(Triple(it, RelationshipColor, TagChipVariant.OUTLINED)) }
                }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    chips.forEach { (text, color, variant) ->
                        TagChip(
                            text = text,
                            color = color,
                            variant = variant,
                            onClick = onTagClick?.let { cb -> { cb(text) } },
                        )
                    }
                    val hidden = tagGroups.sumOf { it.tags.size }
                    if (hidden > chips.size) {
                        TagChip(
                            text = "+${hidden - chips.size} más",
                            color = colors.onSurfaceVariant,
                            variant = TagChipVariant.OUTLINED,
                            onClick = { showAllTags = true },
                        )
                    }
                }
            }

            if (work.summary.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = work.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatText(Icons.Filled.Description, "${formatCount(work.words)} palabras")
                StatText(
                    Icons.Filled.Layers,
                    if (work.chapterTotal == null) "${work.chapterCount}+ cap." else "${work.chapterCount}/${work.chapterTotal} cap.",
                )
                StatText(Icons.Filled.Visibility, formatCount(work.hits))
                StatText(Icons.Filled.Favorite, formatCount(work.kudos))
            }
            work.updated?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.wc_updated, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AllTagsSheet(
    groups: List<TagGroup>,
    onTagClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val filter = rememberTextFieldState()
    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                stringResource(R.string.wc_all_tags),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            SearchBarDefaults.InputField(
                state = filter,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.wc_filter_tags)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val q = filter.text.toString().trim().lowercase()
            groups.forEach { group ->
                val visible = if (q.isEmpty()) group.tags else group.tags.filter { it.lowercase().contains(q) }
                if (visible.isEmpty()) return@forEach
                Spacer(Modifier.height(18.dp))
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visible.forEach { tag ->
                        TagChip(
                            text = tag,
                            color = group.color,
                            variant = group.variant,
                            onClick = { onTagClick(tag) },
                        )
                    }
                }
            }
            if (q.isNotEmpty() && groups.none { g -> g.tags.any { it.lowercase().contains(q) } }) {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.wc_no_matches, filter.text.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = androidx.compose.ui.Modifier.size(16.dp),
        )
        Spacer(androidx.compose.ui.Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
