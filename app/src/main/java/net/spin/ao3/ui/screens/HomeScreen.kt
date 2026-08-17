package net.spin.ao3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.spin.ao3.R
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.components.TagChip
import net.spin.ao3.ui.components.TagChipVariant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onSearch: (String, SortOption) -> Unit,
    onBrowseTag: (String) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenReader: (Long, Int) -> Unit,
) {
    val store = container.store
    var history by remember { mutableStateOf(store.history()) }
    var query by rememberSaveable { mutableStateOf("") }
    // Scroll position survives tab switches (AnimatedContent disposes the screen).
    val homeScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(homeScroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) onSearch(query.trim(), SortOption.BEST_MATCH) },
                ),
            )
            Spacer(Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickChip(Icons.Filled.TrendingUp, stringResource(R.string.home_trends)) { onSearch("", SortOption.KUDOS) }
                QuickChip(Icons.Filled.NewReleases, stringResource(R.string.home_new)) { onSearch("", SortOption.UPDATED) }
                QuickChip(Icons.Filled.Visibility, stringResource(R.string.home_most_read)) { onSearch("", SortOption.HITS) }
                QuickChip(Icons.Filled.FormatListNumbered, stringResource(R.string.home_longest)) { onSearch("", SortOption.WORDS) }
            }
            Spacer(Modifier.height(22.dp))

            SectionTitle(stringResource(R.string.home_explore_fandoms))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Naruto", "Harry Potter", "Marvel", "DCU",
                    "My Hero Academia", "One Piece", "Sherlock (TV)",
                ).forEach { fandom ->
                    TagChip(
                        fandom,
                        MaterialTheme.colorScheme.secondary,
                        variant = TagChipVariant.FILLED_SECONDARY,
                        onClick = { onBrowseTag(fandom) },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            SectionTitle(stringResource(R.string.home_continue))
            if (history.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.home_nothing),
                    description = stringResource(R.string.home_empty_desc),
                    actionLabel = stringResource(R.string.home_explore_trends),
                    onAction = { onSearch("", SortOption.KUDOS) },
                    compact = true,
                )
            } else {
                // AO3 es un archivo de texto: las obras no tienen portadas, así
                // que "Continuar leyendo" usa tarjetas de texto limpias (título,
                // autor, capítulo, tiempo, progreso) en una lista vertical.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { entry ->
                        ContinueReadingRow(
                            entry = entry,
                            onOpen = { id, ch -> onOpenReader(id, ch) },
                            onRemove = { id ->
                                store.removeHistory(id)
                                history = store.history()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.home_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContinueReadingRow(
    entry: Store.HistoryEntry,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val progress = (entry.chapterProgress[entry.chapterIndex] ?: entry.scrollRatio).coerceIn(0f, 1f)
    val readCount = entry.chapterProgress.count { it.value >= 0.97f }
    Card(
        onClick = { onOpen(entry.id, entry.chapterIndex) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        entry.author,
                        stringResource(R.string.home_chapter_cap, entry.chapterIndex + 1),
                        relativeTime(entry.at),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    buildString {
                        append("${(progress * 100).toInt()}% del capítulo")
                        if (readCount > 0) append(" · $readCount cap. leídos")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Remove from "Continuar leyendo" (un libro abierto, no un icono de video).
            IconButton(onClick = { onRemove(entry.id) }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_remove_continue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** "hace 5 min", "hace 2 h", "hace 3 d"… */
private fun relativeTime(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> "hace $minutes min"
        minutes < 60 * 24 -> "hace ${minutes / 60} h"
        minutes < 60 * 24 * 7 -> "hace ${minutes / (60 * 24)} d"
        else -> "hace ${minutes / (60 * 24 * 7)} sem"
    }
}

@Composable
private fun QuickChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}
