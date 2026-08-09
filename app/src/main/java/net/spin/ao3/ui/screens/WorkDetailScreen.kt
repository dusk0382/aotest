package net.spin.ao3.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.ui.components.AdditionalColor
import net.spin.ao3.ui.components.CategoryColor
import net.spin.ao3.ui.components.CharacterColor
import net.spin.ao3.ui.components.FandomColor
import net.spin.ao3.ui.components.RelationshipColor
import net.spin.ao3.ui.components.TagChip
import net.spin.ao3.ui.components.WarningColor
import net.spin.ao3.ui.components.ratingColor
import net.spin.ao3.util.formatCount

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkDetailScreen(
    container: AppContainer,
    workId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val store = container.store
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.isSaved(workId)) }
    var downloadedIds by remember { mutableStateOf(store.downloadedChapterIds(workId)) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var descExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val history = remember(workId) { store.history().firstOrNull { it.id == workId } }
    val chapterProgress = remember(workId) { history?.chapterProgress.orEmpty() }

    LaunchedEffect(workId) {
        try {
            detail = container.client.getWork(workId)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo cargar la obra"
        } finally {
            loading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { downloadJob?.cancel() }
    }

    fun toggleSaved() {
        val d = detail ?: return
        if (saved) store.removeSaved(d.summary.id) else store.saveWork(d.summary)
        saved = !saved
    }

    fun startDownload() {
        val d = detail ?: return
        if (downloading) return
        downloading = true
        downloadError = null
        downloadProgress = 0f
        downloadJob = scope.launch {
            try {
                val fetched = d.chapters.mapIndexed { i, ch ->
                    val ready = if (ch.content != null) ch else container.client.getChapter(d.summary.id, ch)
                    downloadProgress = (i + 1) / d.chapters.size.toFloat()
                    ready
                }
                store.saveDownload(d.summary.id, d.summary.title, fetched)
                downloadedIds = fetched.map { it.index }.toSet()
            } catch (e: Exception) {
                downloadError = "Descarga incompleta: ${e.message}"
            } finally {
                downloading = false
            }
        }
    }

    fun deleteDownload() {
        store.removeDownload(workId)
        downloadedIds = emptySet()
        downloadError = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Detalle de la obra", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { toggleSaved() }) {
                        Icon(
                            if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (saved) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val d = detail
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        loading = true; error = null
                        scope.launch {
                            try { detail = container.client.getWork(workId) } catch (e: Exception) { error = e.message }
                            loading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Reintentar")
                    }
                }
            }
            d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Obra no encontrada")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(d.summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            d.summary.author,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.summary.rating?.let { TagChip(it, ratingColor(d.summary.ratingKey), tinted = true) }
                            if (d.summary.isCompleted) TagChip("Completada", Color(0xFF2E7D32), tinted = true)
                            d.summary.warnings.take(2).forEach { TagChip(it, WarningColor) }
                            d.summary.categories.forEach { TagChip(it, CategoryColor) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatBig("${formatCount(d.summary.words)}", "palabras", Modifier.weight(1f))
                                StatBig(
                                    if (d.summary.chapterTotal != null) {
                                        "${d.summary.chapterCount}/${d.summary.chapterTotal}"
                                    } else {
                                        "${d.summary.chapterCount}+"
                                    },
                                    "capítulos",
                                    Modifier.weight(1f),
                                )
                                StatBig("${formatCount(d.summary.hits)}", "visitas", Modifier.weight(1f))
                                StatBig("${formatCount(d.summary.kudos)}", "kudos", Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(
                                d.summary.published?.let { "Publicada: $it" },
                                d.summary.updated?.let { "Actualizada: $it" },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { onOpenChapter(history?.chapterIndex ?: 0) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                if (history != null) "Continuar · Cap. ${history.chapterIndex + 1}"
                                else "Leer desde el principio",
                            )
                        }
                        if (downloadedIds.isNotEmpty()) {
                            OutlinedButton(onClick = { deleteDownload() }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Descargado")
                            }
                        } else {
                            Button(
                                onClick = { startDownload() },
                                enabled = !downloading,
                                modifier = Modifier.weight(0.8f),
                            ) {
                                if (downloading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                }
                                Text(if (downloading) " ${(downloadProgress * 100).toInt()}%" else "Descargar")
                            }
                        }
                    }
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    downloadError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                item {
                    Section("Resumen") {
                        val summary = d.summary.summary
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descExpanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (summary.length > 200) {
                            TextButton(onClick = { descExpanded = !descExpanded }) {
                                Text(if (descExpanded) "Ver menos" else "Ver más")
                            }
                        }
                    }
                }

                if (d.relationships.isNotEmpty()) {
                    item { TagSection("Relaciones", d.relationships, RelationshipColor, onOpenTag) }
                }
                if (d.characters.isNotEmpty()) {
                    item { TagSection("Personajes", d.characters, CharacterColor, onOpenTag) }
                }
                if (d.additionalTags.isNotEmpty()) {
                    item { TagSection("Etiquetas adicionales", d.additionalTags, AdditionalColor, onOpenTag) }
                }
                if (d.summary.fandoms.isNotEmpty()) {
                    item { TagSection("Fandoms", d.summary.fandoms, FandomColor, onOpenTag) }
                }

                item {
                    Section("Capítulos (${d.chapters.size})") { }
                }
                items(d.chapters, key = { it.index }) { ch ->
                    ChapterRow(
                        index = ch.index,
                        chapter = ch,
                        downloaded = downloadedIds.contains(ch.index),
                        progress = chapterProgress[ch.index],
                        onClick = { onOpenChapter(ch.index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(title: String, tags: List<String>, color: Color, onOpenTag: (String) -> Unit) {
    Section(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.take(24).forEach { TagChip(it, color, onClick = { onOpenTag(it) }) }
        }
    }
}

@Composable
private fun StatBig(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChapterRow(
    index: Int,
    chapter: ChapterInfo,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (downloaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title.ifBlank { "Capítulo ${index + 1}" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null && progress < 0.97f && progress > 0.01f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.55f).height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
        if (downloaded) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Descargado",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
        if (progress != null && progress >= 0.97f) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Leído",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
