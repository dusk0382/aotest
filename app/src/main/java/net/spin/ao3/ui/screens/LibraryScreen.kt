package net.spin.ao3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
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
import androidx.compose.ui.res.stringResource
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spin.ao3.R
import net.spin.ao3.ui.components.LocalBottomBarClearance
import net.spin.ao3.ui.components.ShapeStamp
import net.spin.ao3.ui.components.StampShapes
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.DownloadQueueService
import net.spin.ao3.data.Store
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
    val tabStates = rememberSaveableStateHolder()
    var lastQueueCompletion by remember { mutableLongStateOf(0L) }

    QueueEventsEffect(
        lastConsumed = lastQueueCompletion,
        onCompleted = { completedAt, total ->
            lastQueueCompletion = completedAt
            refreshTick++
            if (DownloadQueueService.consumeCompletion(completedAt)) {
                scope.launch {
                    snackbar.showSnackbar("Descarga completada: $total ${if (total == 1) "capítulo" else "capítulos"}")
                }
            }
        },
        onQueueSettled = { refreshTick++ },
    )

    val favorites = remember(refreshTick) { store.savedWorks() }
    val history = remember(refreshTick) { store.history() }
    var downloads by remember { mutableStateOf<List<Store.Download>>(emptyList()) }
    var downloadsLoading by remember { mutableStateOf(true) }
    LaunchedEffect(refreshTick) {
        downloads = withContext(Dispatchers.IO) { store.downloads() }
        downloadsLoading = false
    }

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
    val workExporter = ChapterExporter.rememberWorkExporter { msg ->
        scope.launch {
            if (msg.startsWith("OK:")) {
                snackbar.showSnackbar("Obra exportada: ${msg.removePrefix("OK:")}")
            } else {
                snackbar.showSnackbar(msg.removePrefix("ERR:"))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_library),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
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
                        loading = downloadsLoading,
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
                        workExporter = workExporter,
                        onExplore = onExplore,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryTabSelector(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        Pair(stringResource(R.string.library_favorites), Icons.Filled.Star),
        Pair(stringResource(R.string.library_history), Icons.Filled.History),
        Pair(stringResource(R.string.library_downloads), Icons.Filled.Download),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val shapes = connectedToggleShapes(index, tabs.size)
            ToggleButton(
                checked = selected == index,
                onCheckedChange = { if (it) onSelect(index) },
                shapes = shapes,
                // Padding propio más estrecho que el default: con el icono y la
                // etiqueta dentro de un tercio de pantalla, "Descargas" no cabe
                // con el padding estándar y se recorta.
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Tab },
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** Fila plana con título/subtítulo a 2 líneas, progreso y acciones de 48 dp. */
@Composable
private fun LibraryRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconContainer: androidx.compose.ui.graphics.Color,
    cardColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    stamp: RoundedPolygon = StampShapes.Historial,
    progress: Float? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = cardColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sello del estado (favorito/historial/descarga): misma pieza que
            // en Ajustes y los vacíos, con su rol tonal propio.
            ShapeStamp(
                icon = icon,
                shape = stamp,
                container = iconContainer,
                content = iconTint,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    Spacer(Modifier.height(10.dp))
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

/** Filas de [columns] celdas dentro del LazyColumn existente. */
private fun <T> LazyListScope.gridItems(
    values: List<T>,
    columns: Int,
    key: (T) -> Long,
    content: @Composable (T) -> Unit,
) {
    val rows = (values.size + columns - 1) / columns
    val chunked = remember(values, columns) { values.chunked(columns) }
    items(chunked, key = { key(it.first()) }) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { value ->
                Box(Modifier.weight(1f)) { content(value) }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Formas conectadas para el grupo de ToggleButtons (alpha23 no trae helper). */
@Composable
private fun connectedToggleShapes(index: Int, count: Int): ToggleButtonShapes {
    val shape = when {
        count == 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp,
        )
        else -> RoundedCornerShape(4.dp)
    }
    return ToggleButtonDefaults.shapes(shape, shape, shape)
}

@Composable
private fun FavoritesTab(
    favorites: List<Store.SavedWork>,
    onOpen: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onExplore: () -> Unit,
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    if (favorites.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Star,
            title = stringResource(R.string.library_no_favs),
            actionLabel = stringResource(R.string.library_explore),
            onAction = onExplore,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = when { maxWidth >= 1200.dp -> 3; maxWidth >= 840.dp -> 2; else -> 1 }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarClearance.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(favorites, columns, key = { it.id }) { sw ->
            LibraryRowCard(
                icon = Icons.Default.Star,
                iconTint = MaterialTheme.colorScheme.onPrimary,
                iconContainer = MaterialTheme.colorScheme.primary,
                cardColor = MaterialTheme.colorScheme.primaryContainer,
                stamp = StampShapes.Favorito,
                title = sw.title,
                subtitle = "${sw.author} · ${sw.chapters} ${if (sw.chapters == 1) "cap" else "caps"}",
                onClick = { onOpen(sw.id) },
                trailing = {
                    IconButton(onClick = { onRemove(sw.id) }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.library_remove_fav),
                        )
                    }
                },
            )
        }
    }
    }
}

@Composable
private fun HistoryTab(
    history: List<Store.HistoryEntry>,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
    onExplore: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.library_history_empty),
            actionLabel = stringResource(R.string.library_explore),
            onAction = onExplore,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = when { maxWidth >= 1200.dp -> 3; maxWidth >= 840.dp -> 2; else -> 1 }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarClearance.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text(stringResource(R.string.library_clear_history))
                }
            }
        }
        gridItems(history, columns, key = { it.id }) { entry ->
            val progress = (entry.chapterProgress[entry.chapterIndex] ?: entry.scrollRatio).coerceIn(0f, 1f)
            val finished = progress >= 0.97f
            LibraryRowCard(
                icon = Icons.Default.History,
                iconTint = MaterialTheme.colorScheme.onSecondary,
                iconContainer = MaterialTheme.colorScheme.secondary,
                cardColor = MaterialTheme.colorScheme.secondaryContainer,
                stamp = StampShapes.Historial,
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
                        )
                    }
                },
            )
        }
    }
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Borrar historial") },
            text = { Text("Se vacía el historial. Favoritos y descargas no se tocan.") },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false; onClearAll() }) { Text("Borrar historial") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsTab(
    downloads: List<Store.Download>,
    loading: Boolean,
    onOpenDetail: (Long) -> Unit,
    onOpenChapter: (Long, Int) -> Unit,
    onDeleteChapter: (Long, Int) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    exporter: (String, Int, String, String?) -> Unit,
    workExporter: (String, String, List<String>, String, List<ChapterInfo>, (Int, Int) -> Unit) -> Unit,
    onExplore: () -> Unit,
) {
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val queueState by DownloadQueueService.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (!loading && downloads.isEmpty() && !queueState.active && !queueState.paused) {
        EmptyState(
            icon = Icons.Default.Download,
            title = stringResource(R.string.library_nothing_downloaded),
            actionLabel = stringResource(R.string.library_explore),
            onAction = onExplore,
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = when { maxWidth >= 1200.dp -> 3; maxWidth >= 840.dp -> 2; else -> 1 }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarClearance.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (downloads.isNotEmpty()) {
            item {
                Text(
                    "${downloads.size} ${if (downloads.size == 1) "obra" else "obras"} · ${downloads.sumOf { it.chapters.size }} capítulos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (queueState.active || queueState.paused) {
            item {
                val active = queueState.active
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (queueState.paused) "Descarga pausada: ${queueState.workTitle}" else stringResource(R.string.library_downloading, queueState.workTitle),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearWavyProgressIndicator(
                            progress = { if (queueState.total > 0) queueState.done / queueState.total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.library_queue_chapter, queueState.done, queueState.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    if (queueState.paused) DownloadQueueService.resume(context)
                                    else DownloadQueueService.cancel(context, queueState.workId)
                                },
                            ) {
                                Text(if (queueState.paused) "Reanudar" else "Detener")
                            }
                        }
                    }
                }
            }
        }
        gridItems(downloads, columns, key = { it.id }) { dl ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    LibraryRowCard(
                        icon = Icons.Default.Download,
                        iconTint = MaterialTheme.colorScheme.onTertiary,
                        iconContainer = MaterialTheme.colorScheme.tertiary,
                        cardColor = MaterialTheme.colorScheme.tertiaryContainer,
                        stamp = StampShapes.Descarga,
                        title = dl.title,
                        subtitle = "${dl.chapters.size} ${if (dl.chapters.size == 1) "capítulo" else "capítulos"}",
                        onClick = { onOpenDetail(dl.id) },
                        trailing = {
                            IconButton(onClick = { expanded = if (dl.id in expanded) expanded - dl.id else expanded + dl.id }) {
                                Icon(
                                    if (dl.id in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(
                                        if (dl.id in expanded) R.string.library_collapse else R.string.library_expand,
                                    ),
                                )
                            }
                            IconButton(onClick = { pendingDelete = dl.id }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.library_delete_download),
                                )
                            }
                        },
                    )
                    AnimatedVisibility(visible = dl.id in expanded) {
                        Column {
                            TextButton(
                                onClick = {
                                    workExporter(
                                        dl.title,
                                        "",
                                        emptyList(),
                                        "https://archiveofourown.org/works/${dl.id}",
                                        dl.chapters.sortedBy { it.index },
                                    ) { _, _ -> }
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Exportar obra completa (.txt)")
                            }
                            dl.chapters.sortedBy { it.index }.forEach { ch ->
                                ChapterDownloadRow(
                                    chapter = ch,
                                    onOpen = { onOpenChapter(dl.id, ch.index) },
                                    onExport = { exporter(dl.title, ch.index, ch.title, ch.content) },
                                    onDelete = { onDeleteChapter(dl.id, ch.index) },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    }
    pendingDelete?.let { id ->
        val download = downloads.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar descarga") },
            text = {
                Text("Se eliminarán ${download?.chapters?.size ?: 0} capítulos guardados de «${download?.title ?: "esta obra"}».")
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteDownload(id) }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            },
        )
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = stringResource(R.string.library_downloaded),
            tint = LocalSemanticColors.current.success,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.library_chapter_row, chapter.index + 1, chapter.title.ifBlank { "Sin título" }),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExport) {
            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.library_export_txt))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.library_remove_chapter))
        }
    }
}

/**
 * Queue events for the library (completion + pause/resume), collected in a
 * tiny scope: queue ticks no longer recompose the whole library Scaffold.
 */
@Composable
private fun QueueEventsEffect(
    lastConsumed: Long,
    onCompleted: (completedAt: Long, total: Int) -> Unit,
    onQueueSettled: () -> Unit,
) {
    val state by DownloadQueueService.state.collectAsState()
    LaunchedEffect(state.completedAt, state.paused) {
        if (state.completedAt > 0 && state.completedAt != lastConsumed) {
            onCompleted(state.completedAt, state.total)
        } else if (state.paused) {
            onQueueSettled()
        }
    }
}
