package net.spin.ao3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.spin.ao3.R
import net.spin.ao3.ui.components.LocalBottomBarClearance
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.components.PrimaryCtaButton
import net.spin.ao3.ui.components.standaloneSearchFieldColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onSearch: (String, SortOption) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenReader: (Long, Int) -> Unit,
) {
    val store = container.store
    val online by container.connectivity.online.collectAsState()
    var history by remember { mutableStateOf(store.history()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val homeScroll = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    fun removeHistoryEntry(id: Long) {
        val removed = history.firstOrNull { it.id == id } ?: return
        store.removeHistory(id)
        history = store.history()
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "Quitado de Continuar leyendo",
                actionLabel = "Deshacer",
            )
            if (result == SnackbarResult.ActionPerformed) {
                store.updateHistory(removed)
                history = store.history()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        BoxWithConstraints {
            val wide = maxWidth >= 840.dp
            LazyColumn(
                state = homeScroll,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Despeje para la píldora flotante: el contenido pasa por detrás.
                contentPadding = PaddingValues(bottom = LocalBottomBarClearance.current + 16.dp),
            ) {
                item(key = "header") {
                    HomeHeader(
                        wide = wide,
                        onSearch = { q ->
                            if (q.isNotBlank()) onSearch(q.trim(), SortOption.BEST_MATCH)
                        },
                        online = online,
                    )
                }
                if (history.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = stringResource(R.string.home_empty_title),
                            description = stringResource(R.string.home_empty_desc),
                        )
                    }
                }
                if (history.isNotEmpty()) {
                    if (wide) {
                        item(key = "hero") {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.weight(2f)) {
                                    ContinueReadingHero(
                                        entry = history.first(),
                                        onOpen = onOpenReader,
                                        onRemove = ::removeHistoryEntry,
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "hero") {
                            ContinueReadingHero(
                                entry = history.first(),
                                onOpen = onOpenReader,
                                onRemove = ::removeHistoryEntry,
                            )
                        }
                    }
                }
                if (history.size > 1) {
                    item(key = "section") {
                        Text(
                            stringResource(R.string.home_continue),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    val rest = history.drop(1)
                    if (wide) {
                        items(rest.chunked(2), key = { it.first().id }) { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (entry in pair) {
                                    Box(Modifier.weight(1f)) {
                                        ContinueReadingRow(
                                            entry = entry,
                                            onOpen = onOpenReader,
                                            onRemove = ::removeHistoryEntry,
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(rest, key = { it.id }) { entry ->
                            ContinueReadingRow(
                                entry = entry,
                                onOpen = onOpenReader,
                                onRemove = ::removeHistoryEntry,
                            )
                        }
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeHeader(wide: Boolean, online: Boolean, onSearch: (String) -> Unit) {
    val query = rememberTextFieldState()
    // Portada editorial: el nombre de la app a tamaño display (Fraunces), no
    // una etiqueta de barra. Aquí arranca la app: es su momento de mayor voz.
    val title = @Composable {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
    val search = @Composable {
        SearchBarDefaults.InputField(
            state = query,
            onSearch = onSearch,
            expanded = false,
            onExpandedChange = {},
            colors = standaloneSearchFieldColors(),
            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(3f)) {
                title()
                Spacer(Modifier.height(12.dp))
                search()
            }
            Column(Modifier.weight(2f)) {
                if (!online) OfflineBadge()
            }
        }
    } else {
        Column {
            title()
            Spacer(Modifier.height(16.dp))
            if (!online) {
                OfflineBadge()
                Spacer(Modifier.height(12.dp))
            }
            search()
        }
    }
}

@Composable
private fun OfflineBadge() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sin conexión", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContinueReadingHero(
    entry: Store.HistoryEntry,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = (entry.chapterProgress[entry.chapterIndex] ?: entry.scrollRatio).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        // Esquinas expresivas asimétricas: tres grandes y una cuadrada — la
        // tensión redondo/cuadrado marca el héroe de la pantalla.
        shape = RoundedCornerShape(
            topStart = 32.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 4.dp,
        ),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    // Ráfaga Expressive: el porcentaje vive dentro de una forma
                    // de la librería MaterialShapes (tinta sólida + cifra clara).
                    // La forma abraza al texto (padding, no tamaño fijo): con
                    // fuente al 200% la ráfaga crece con él en vez de recortarlo.
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .clip(MaterialShapes.Sunny.toShape())
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                        ) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                IconButton(onClick = { onRemove(entry.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_remove_continue))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(entry.author, stringResource(R.string.home_chapter_cap, entry.chapterIndex + 1)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            LinearWavyProgressIndicator(progress = { progress })
            Spacer(Modifier.height(20.dp))
            PrimaryCtaButton(onClick = { onOpen(entry.id, entry.chapterIndex) }) {
                Text(stringResource(R.string.home_continue_chapter, entry.chapterIndex + 1))
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun ContinueReadingRow(
    entry: Store.HistoryEntry,
    onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = (entry.chapterProgress[entry.chapterIndex] ?: entry.scrollRatio).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        onClick = { onOpen(entry.id, entry.chapterIndex) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { onRemove(entry.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_remove_continue))
                }
            }
            Text(
                listOfNotNull(entry.author, stringResource(R.string.home_chapter_cap, entry.chapterIndex + 1)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            LinearWavyProgressIndicator(progress = { progress })
        }
    }
}
