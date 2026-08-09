package net.spin.ao3.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.AppContainer
import kotlinx.coroutines.launch
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.ChapterExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenDetail: (Long) -> Unit,
    onOpenReader: (Long, Int) -> Unit,
) {
    val store = container.store
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val favorites = remember(refreshTick) { store.savedWorks() }
    val history = remember(refreshTick) { store.history() }
    val downloads = remember(refreshTick) { store.downloads() }
    val semantic = LocalSemanticColors.current

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
            androidx.compose.material3.TopAppBar(
                title = { Text("Biblioteca", fontWeight = FontWeight.SemiBold) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favoritos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Historial") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Descargas") })
            }
            when (tab) {
                0 -> FavoritesTab(
                    favorites = favorites,
                    onOpen = onOpenDetail,
                    onRemove = { id ->
                        store.removeSaved(id)
                        refreshTick++
                        scope.launch { snackbar.showSnackbar("Quitado de favoritos") }
                    },
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
                )
                else -> DownloadsTab(
                    downloads = downloads,
                    onOpenDetail = onOpenDetail,
                    onOpenChapter = onOpenReader,
                    onDeleteChapter = { id, index ->
                        store.removeDownloadedChapter(id, index)
                        refreshTick++
                        scope.launch { snackbar.showSnackbar("Capítulo eliminado de la descarga") }
                    },
                    onDeleteDownload = { id ->
                        store.removeDownload(id)
                        refreshTick++
                        scope.launch { snackbar.showSnackbar("Descarga eliminada") }
                    },
                    exporter = exporter,
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<net.spin.ao3.data.Store.SavedWork>,
    onOpen: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val semantic = LocalSemanticColors.current
    if (favorites.isEmpty()) {
        EmptyHint("Marca obras con la estrella ⭐ en su detalle para tenerlas aquí.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites, key = { it.id }) { sw ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(sw.id) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = semantic.favorite, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sw.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${sw.author} · ${sw.chapters} ${if (sw.chapters == 1) "cap" else "caps"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(sw.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar de favoritos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<net.spin.ao3.data.Store.HistoryEntry>,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyHint("Las obras que leas aparecerán aquí con tu progreso.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearAll) {
                    Text("Borrar historial")
                }
            }
        }
        items(history, key = { it.id }) { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.id, entry.chapterIndex) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${entry.author} · Cap. ${entry.chapterIndex + 1}" +
                                (if (entry.scrollRatio > 0.02f) " · ${(entry.scrollRatio * 100).toInt()}%" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(entry.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar del historial", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
) {
    val semantic = LocalSemanticColors.current
    var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }

    if (downloads.isEmpty()) {
        EmptyHint("Descarga obras o capítulos individuales desde su detalle para leerlos sin conexión.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(downloads, key = { it.id }) { dl ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(dl.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = semantic.success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dl.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${dl.chapters.size} ${if (dl.chapters.size == 1) "capítulo" else "capítulos"} · sin conexión",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { expanded = if (dl.id in expanded) expanded - dl.id else expanded + dl.id }) {
                            Icon(
                                if (dl.id in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (dl.id in expanded) "Contraer" else "Expandir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteDownload(dl.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar descarga", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                contentDescription = "Descargado",
                tint = LocalSemanticColors.current.success,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "Cap. ${chapter.index + 1} · ${chapter.title.ifBlank { "Sin título" }}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.FileDownload, contentDescription = "Exportar .txt", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar capítulo", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
