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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.R
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.ui.theme.Ao3Theme
import net.spin.ao3.util.formatCount

/**
 * Tarjeta de obra: catálogo editorial con escalera de jerarquía de cuatro
 * niveles — título Fraunces oscuro; enlaces en `primary` (autor y tags, una
 * sola voz de enlace); metadatos grises callados; y la valoración como píldora
 * compacta, el ÚNICO relleno de color de la tarjeta (ancla de seguridad). La
 * tarjeta vive un paso por encima del fondo (`surfaceContainerLow`) para que
 * cada resultado se lea como un objeto.
 */
@Composable
fun WorkCard(
    work: WorkSummary,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
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
        color = colors.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = work.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = work.author,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Único relleno de la tarjeta: la valoración, compacta.
                work.rating?.let {
                    val (bg, fg) = ratingSolidColors(work.ratingKey)
                    TagChipSolid(it, bg, fg, compact = true)
                }
            }

            // Metadatos: fandom dominante + estado, texto callado.
            val meta = buildString {
                work.fandoms.firstOrNull()?.let { append(it) }
                if (work.isCompleted) {
                    if (isNotEmpty()) append(" · ")
                    append(strCompleted)
                }
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Línea editorial de enlaces: dos tags (primero las relaciones, que
            // es lo que la gente busca) y «+N más» para abrir la hoja completa.
            // Sin contenedores ni puntos: la misma voz de enlace que el autor.
            val tagLinks = remember(work) {
                val rels = work.relationships
                val extras = work.otherTags.filterNot { it in work.relationships || it in work.characters }
                when {
                    rels.isNotEmpty() -> rels.take(2)
                    extras.isNotEmpty() -> extras.take(2)
                    else -> work.characters.take(2)
                }
            }
            val hiddenCount = tagGroups.sumOf { it.tags.size }
            if (tagLinks.isNotEmpty() || hiddenCount > 0) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tagLinks.forEachIndexed { i, tag ->
                        if (i > 0) {
                            Text("·", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = onTagClick?.let { cb -> Modifier.clickable { cb(tag) } } ?: Modifier,
                        )
                    }
                    if (hiddenCount > tagLinks.size) {
                        if (tagLinks.isNotEmpty()) {
                            Text("·", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text(
                            text = "+${hiddenCount - tagLinks.size} más",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.clickable { showAllTags = true },
                        )
                    }
                }
            }

            if (work.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = work.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            // Estadísticas: una sola línea tabular, sin iconos.
            val chapters = if (work.chapterTotal == null) {
                "${work.chapterCount}+ cap."
            } else {
                "${work.chapterCount}/${work.chapterTotal} cap."
            }
            Text(
                text = listOf(
                    "${formatCount(work.words)} palabras",
                    chapters,
                    "${formatCount(work.kudos)} kudos",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            SearchBarDefaults.InputField(
                state = filter,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                colors = standaloneSearchFieldColors(),
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

@Preview(showBackground = true, widthDp = 320, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkCardLongDarkScaledPreview() {
    Ao3Theme {
        WorkCard(
            work = WorkSummary(
                id = 2,
                title = "Un título deliberadamente muy largo para comprobar el salto a tres líneas y el recorte en pantalla estrecha con fuente al 200 por ciento",
                author = "Una autora con un nombre también bastante largo",
                authorUrl = null,
                fandoms = listOf("Harry Potter", "Marvel", "Star Wars"),
                rating = "Explicit",
                ratingKey = "explicit",
                warnings = listOf("Graphic Depictions Of Violence", "Major Character Death"),
                categories = listOf("F/M", "Gen"),
                otherTags = listOf("Angst", "Hurt/Comfort", "Slow Burn", "Post-Canon", "Alternative Universe"),
                relationships = listOf("Draco Malfoy/Hermione Granger"),
                characters = listOf("Hermione Granger", "Draco Malfoy", "Severus Snape"),
                summary = "Resumen extenso que ocupa varias líneas para verificar el límite de cuatro líneas y la elipsis en el modo oscuro con escala de fuente doble.",
                words = 123_456,
                chapterCount = 27,
                chapterTotal = 40,
                hits = 123_456,
                kudos = 9_876,
                comments = 1_234,
                bookmarks = 567,
                published = "2020-01-01",
                updated = "2026-11-01",
                url = "",
            ),
            onClick = {},
            onTagClick = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}
