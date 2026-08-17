package net.spin.ao3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.spin.ao3.R
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.DownloadQueueService
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.ChapterExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenDetail: (Long) -> Unit,
    onOpenReader: (Long, Int) -> Unit,
    onExplore: () -> Unit,
) {
    val store = container.store
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    // Keeps each tab's scroll position alive when switching tabs (plain `when`
    // disposes the old tab, so rememberSaveable alone would lose it).
    val tabStates = rememberSaveableStateHolder()
    val queueState by DownloadQueueService.state.collectAsState()
    var lastQueueCompletion by remember { mutableLongStateOf(0L) }

    // Live-refresh the Downloads tab while the queue writes chapters, and once
    // when a queued download finishes.
    LaunchedEffect(queueState.completedAt) {
        if (queueState.completedAt > 0 && queueState.completedAt != lastQueueCompletion) {
            lastQueueCompletion = queueState.completedAt
            refreshTick++
        }
    }

    val favorites = remember(refreshTick) { store.savedWorks() }
    val history = remember(refreshTick) { store.history() }
    val downloads = remember(refreshTick, queueState) { store.downloads() }

    // Resolved in composable context (stringResource can't run inside coroutines).
    val removedFavMsg = stringResource(R.string.library_removed_fav)
    val chapterRemovedMsg = stringResource(R.string.library_chapter_removed)
    val downloadRemovedMsg = stringResource(R.string.library_download_removed)

    val exporter = ChapterExporter.rememberChapterExporter { msg ->
        scope.launch {
            if (msg.startsWith("OK:")) {
                snackbar.showSnackbar("Guardado: ${msg.removePrefix("OK:")}")
            } else {
                snackbar.showSnackbar(msg.removePrefix("ERR:"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LibraryTabSelector(selected = tab, onSelect = { tab = it })
            tabStates.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> FavoritesTab(
                        favorites = favorites,
                        onOpen = onOpenDetail,
                        onRemove = { id ->
                            store.removeSaved(id)
                            refreshTick++
                            scope.launch { snackbar.showSnackbar(removedFavMsg) }
                        },
                        onExplore = onExplore,
                    )
                    1 -> HistoryTab(
                        history = history,
                        onOpen = onOpenReader,
                        onRemove = { id ->
                            store.removeHistory(id)
                            refreshTick++
                        },
                        onClearAll = {
                            store.clearHistory()
                            refreshTick++
                        },
                        onExplore = onExplore,
                    )
                    else -> DownloadsTab(
                        downloads = downloads,
                        onOpenDetail = onOpenDetail,
                        onOpenChapter = onOpenReader,
                        onDeleteChapter = { id, index ->
                            store.removeDownloadedChapter(id, index)
                            refreshTick++
                            scope.launch { snackbar.showSnackbar(chapterRemovedMsg) }
                        },
                        onDeleteDownload = { id ->
                            store.removeDownload(id)
                            refreshTick++
                            scope.launch { snackbar.showSnackbar(downloadRemovedMsg) }
                        },
                        exporter = exporter,
                        onExplore = onExplore,
                    )
                }
            }
        }
    }
}

/** Segmented pill selector (mismo lenguaje que la barra inferior de la app). */
@Composable
private fun LibraryTabSelector(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f)) {
                LibraryTabPill(stringResource(R.string.library_favorites), Icons.Filled.Star, selected == 0) { onSelect(0) }
            }
            Box(Modifier.weight(1f)) {
                LibraryTabPill(stringResource(R.string.library_history), Icons.Filled.History, selected == 1) { onSelect(1) }
            }
            Box(Modifier.weight(1f)) {
                LibraryTabPill(stringResource(R.string.library_downloads), Icons.Filled.Download, selected == 2) { onSelect(2) }
            }
        }
    }
}

@Composable
private fun LibraryTabPill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tabBg",
    )
    val fg by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "tabFg",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Fila tipo card con icono en círculo tonal a la izquierda, título + subtítulo
 * (y opcionalmente una barra de progreso) y acciones al final.
 */
@Composable
private fun LibraryRowCard(
    icon: ImageVector,
    iconTint: Color,
    iconContainer: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    progress: Float? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
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
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<net.spin.ao3.data.Store.SavedWork>,
    onOpen: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onExplore: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    if (favorites.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Star,
            title = stringResource(R.string.library_no_favs),
            description = stringResource(R.string.library_favs_empty_desc),
            actionLabel = stringResource(R.string.home_explore_trends),
            onAction = onExplore,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites, key = { it.id }) { sw ->
            LibraryRowCard(
                icon = Icons.Default.Star,
                iconTint = semantic.favorite,
                iconContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                title = sw.title,
                subtitle = "${sw.author} · ${sw.chapters} ${if (sw.chapters == 1) "cap" else "caps"}",
                onClick = { onOpen(sw.id) },
                trailing = {
                    IconButton(onClick = { onRemove(sw.id) }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.library_remove_fav),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<net.spin.ao3.data.Store.HistoryEntry>,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
    onExplore: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.library_history_empty),
            description = stringResource(R.string.library_history_empty_desc),
            actionLabel = stringResource(R.string.home_explore_trends),
            onAction = onExplore,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.library_clear_history))
                }
            }
        }
        items(history, key = { it.id }) { entry ->
            val progress = (entry.chapterProgress[entry.chapterIndex] ?: entry.scrollRatio).coerceIn(0f, 1f)
            val finished = progress >= 0.97f
            LibraryRowCard(
                icon = Icons.Default.History,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconContainer = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                title = entry.title,
                subtitle = buildString {
                    append("${entry.author} · Cap. ${entry.chapterIndex + 1}")
                    if (progress > 0.02f) append(" · ${(progress * 100).toInt()}%")
                    if (finished) append(" · leído")
                },
                progress = if (progress > 0.02f) progress else null,
                onClick = { onOpen(entry.id, entry.chapterIndex) },
                trailing = {
                    IconButton(onClick = { onRemove(entry.id) }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.library_remove_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<net.spin.ao3.data.Store.Download>,
    onOpenDetail: (Long) -> Unit,
    onOpenChapter: (Long, Int) -> Unit,
    onDeleteChapter: (Long, Int) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    exporter: (String, Int, String, String?) -> Unit,
    onExplore: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val queueState by DownloadQueueService.state.collectAsState()

    if (downloads.isEmpty() && !queueState.active) {
        EmptyState(
            icon = Icons.Default.Download,
            title = stringResource(R.string.library_nothing_downloaded),
            description = stringResource(R.string.library_downloads_empty_desc),
            actionLabel = stringResource(R.string.home_explore_trends),
            onAction = onExplore,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (queueState.active) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.library_downloading, queueState.workTitle),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.library_queue_chapter, queueState.done, queueState.total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { if (queueState.total > 0) queueState.done / queueState.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        )
                    }
                }
            }
        }
        items(downloads, key = { it.id }) { dl ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    LibraryRowCard(
                        icon = Icons.Default.Download,
                        iconTint = semantic.success,
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        title = dl.title,
                        subtitle = "${dl.chapters.size} ${if (dl.chapters.size == 1) "capítulo" else "capítulos"} · sin conexión",
                        onClick = { onOpenDetail(dl.id) },
                        trailing = {
                            IconButton(onClick = { expanded = if (dl.id in expanded) expanded - dl.id else expanded + dl.id }) {
                                Icon(
                                    if (dl.id in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(
                                        if (dl.id in expanded) R.string.library_collapse else R.string.library_expand,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteDownload(dl.id) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.library_delete_download),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    AnimatedVisibility(visible = dl.id in expanded) {
                        Column {
                            dl.chapters.sortedBy { it.index }.forEach { ch ->
                                ChapterDownloadRow(
                                    chapter = ch,
                                    onOpen = { onOpenChapter(dl.id, ch.index) },
                                    onExport = { exporter(dl.title, ch.index, ch.title, ch.content) },
                                    onDelete = { onDeleteChapter(dl.id, ch.index) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterDownloadRow(
    chapter: ChapterInfo,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.library_downloaded),
                tint = LocalSemanticColors.current.success,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.library_chapter_row, chapter.index + 1, chapter.title.ifBlank { "Sin título" }),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExport) {
            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.library_export_txt), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.library_remove_chapter), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
