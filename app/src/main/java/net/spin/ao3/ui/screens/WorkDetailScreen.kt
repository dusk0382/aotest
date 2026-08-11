package net.spin.ao3.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.DownloadQueueService
import net.spin.ao3.data.model.Ao3Comment
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.data.model.WorkDetail
import net.spin.ao3.ui.components.AdditionalColor
import net.spin.ao3.ui.components.CategoryColor
import net.spin.ao3.ui.components.CharacterColor
import net.spin.ao3.ui.components.FandomColor
import net.spin.ao3.ui.components.RelationshipColor
import net.spin.ao3.ui.components.TagChip
import net.spin.ao3.ui.components.TagChipVariant
import net.spin.ao3.ui.components.WarningColor
import net.spin.ao3.ui.components.ratingColor
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.AvatarImages
import net.spin.ao3.util.ChapterExporter
import net.spin.ao3.util.formatCount
import net.spin.ao3.util.htmlToAnnotated
import net.spin.ao3.util.usernameFromAuthorUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkDetailScreen(
    container: AppContainer,
    workId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
) {
    val store = container.store
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(store.isSaved(workId)) }
    var downloadedIds by remember { mutableStateOf(store.downloadedChapterIds(workId)) }
    var downloadingChapter by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var removeChapterIndex by remember { mutableStateOf<Int?>(null) }
    var descExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var kudoed by rememberSaveable { mutableStateOf(store.isKudoed(workId)) }
    var kudosSending by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val queueState by DownloadQueueService.state.collectAsState()
    var lastQueueCompletion by remember { mutableLongStateOf(0L) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* proceed either way */ }
    val history = remember(workId) { store.history().firstOrNull { it.id == workId } }
    val chapterProgress = remember(workId) { history?.chapterProgress.orEmpty() }

    // ---- Comments state ----
    var commentChapterIndex by rememberSaveable { mutableIntStateOf(history?.chapterIndex ?: 0) }
    var comments by remember { mutableStateOf<List<Ao3Comment>?>(null) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf<String?>(null) }
    var commentReloadTick by remember { mutableIntStateOf(0) }
    var showCommentForm by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Long?>(null) }
    var commentName by remember { mutableStateOf(store.prefs.commentName) }
    var commentEmail by remember { mutableStateOf(store.prefs.commentEmail) }
    var commentBody by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    val exporter = ChapterExporter.rememberChapterExporter { msg ->
        scope.launch {
            if (msg.startsWith("OK:")) {
                snackbar.showSnackbar("Guardado en Descargas: ${msg.removePrefix("OK:")}")
            } else {
                snackbar.showSnackbar(msg.removePrefix("ERR:"))
            }
        }
    }

    LaunchedEffect(workId) {
        try {
            detail = container.client.getWork(workId)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo cargar la obra"
        } finally {
            loading = false
        }
    }

    fun refreshDownloads() {
        downloadedIds = store.downloadedChapterIds(workId)
    }

    LaunchedEffect(queueState.completedAt) {
        if (queueState.completedAt > 0 && queueState.completedAt != lastQueueCompletion) {
            lastQueueCompletion = queueState.completedAt
            refreshDownloads()
            snackbar.showSnackbar("Descarga completada: ${queueState.total} ${if (queueState.total == 1) "capítulo" else "capítulos"}")
        }
    }

    fun toggleSaved() {
        val d = detail ?: return
        if (saved) store.removeSaved(d.summary.id) else store.saveWork(d.summary)
        saved = !saved
    }

    fun downloadChapter(ch: ChapterInfo) {
        val d = detail ?: return
        if (ch.index in downloadingChapter) return
        downloadingChapter = downloadingChapter + ch.index
        scope.launch {
            try {
                val ready = if (ch.content != null) ch else container.client.getChapter(d.summary.id, ch)
                store.addDownloadedChapter(d.summary.id, d.summary.title, ready)
                refreshDownloads()
                snackbar.showSnackbar("Capítulo ${ch.index + 1} descargado (sin conexión)")
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo descargar: ${e.message}")
            } finally {
                downloadingChapter = downloadingChapter - ch.index
            }
        }
    }

    fun exportChapter(ch: ChapterInfo) {
        val d = detail ?: return
        scope.launch {
            try {
                val ready = if (ch.content != null) ch else container.client.getChapter(d.summary.id, ch)
                exporter(d.summary.title, ch.index, ch.title, ready.content)
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo exportar: ${e.message}")
            }
        }
    }

    fun enqueueAll() {
        val d = detail ?: return
        if (queueState.active && queueState.workId == d.summary.id) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ContextCompat.startForegroundService(
            context,
            DownloadQueueService.enqueueIntent(context, d.summary.id, d.summary.title, d.chapters),
        )
        scope.launch { snackbar.showSnackbar("Descarga en cola…") }
    }

    fun sendKudos() {
        val d = detail ?: return
        if (kudosSending || kudoed) return
        kudosSending = true
        scope.launch {
            try {
                val msg = container.client.postKudos(d.summary.id)
                if (msg == null || msg.startsWith("Ya habías")) {
                    kudoed = true
                    store.markKudoed(d.summary.id)
                    snackbar.showSnackbar(msg ?: "¡Gracias por el kudo! ❤")
                } else {
                    snackbar.showSnackbar(msg)
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo enviar el kudo: ${e.message}")
            } finally {
                kudosSending = false
            }
        }
    }

    fun openBookmarkPage() {
        val d = detail ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://archiveofourown.org/bookmarks/new?work_id=${d.summary.id}")),
        )
    }

    fun deleteDownload() {
        store.removeDownload(workId)
        refreshDownloads()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Detalle de la obra", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { toggleSaved() }) {
                        Icon(
                            if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (saved) semantic.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
            else -> {
                val chapters = d.chapters

                // Load comments for the selected chapter.
                LaunchedEffect(commentChapterIndex, commentReloadTick, workId) {
                    val ch = chapters.getOrNull(commentChapterIndex) ?: return@LaunchedEffect
                    val cid = ch.chapterId ?: run {
                        comments = null
                        return@LaunchedEffect
                    }
                    commentsLoading = true
                    commentsError = null
                    try {
                        comments = container.client.getComments(cid)
                    } catch (e: Exception) {
                        commentsError = e.message ?: "No se pudieron cargar los comentarios"
                    } finally {
                        commentsLoading = false
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(d.summary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val authorUser = usernameFromAuthorUrl(d.summary.authorUrl)
                            Text(
                                d.summary.author,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = if (authorUser != null) {
                                    Modifier.clickable { onOpenAuthor(authorUser) }
                                } else {
                                    Modifier
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                d.summary.rating?.let { TagChip(it, ratingColor(d.summary.ratingKey), variant = TagChipVariant.TINTED) }
                                if (d.summary.isCompleted) TagChip("Completada", semantic.success, variant = TagChipVariant.TINTED)
                                d.summary.warnings.take(2).forEach { TagChip(it, WarningColor, variant = TagChipVariant.TINTED) }
                                d.summary.categories.forEach { TagChip(it, CategoryColor, variant = TagChipVariant.OUTLINED) }
                            }
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = MaterialTheme.shapes.medium,
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
                                    if (d.summary.comments > 0) "${d.summary.comments} comentarios" else null,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Kudos (invitado anónimo) + marcar en AO3 (navegador)
                    item {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { sendKudos() },
                                enabled = !kudosSending && !kudoed,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (kudosSending) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        if (kudoed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (kudoed) "Kudo enviado" else "Dar kudos")
                            }
                            OutlinedButton(onClick = { openBookmarkPage() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Marcar en AO3")
                            }
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
                                    Text("Descargado (${downloadedIds.size})")
                                }
                            } else {
                                val queuedHere = queueState.active && queueState.workId == d.summary.id
                                Button(
                                    onClick = { enqueueAll() },
                                    enabled = !queuedHere,
                                    modifier = Modifier.weight(0.8f),
                                ) {
                                    if (queuedHere) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Text(" ${queueState.done}/${queueState.total}")
                                    } else {
                                        Text("Descargar todo")
                                    }
                                }
                            }
                        }
                        if (queueState.active && queueState.workId == d.summary.id) {
                            LinearProgressIndicator(
                                progress = { if (queueState.total > 0) queueState.done / queueState.total.toFloat() else 0f },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                        item { TagSection("Relaciones", d.relationships, RelationshipColor, TagChipVariant.OUTLINED, onOpenTag) }
                    }
                    if (d.characters.isNotEmpty()) {
                        item { TagSection("Personajes", d.characters, CharacterColor, TagChipVariant.FILLED_TERTIARY, onOpenTag) }
                    }
                    if (d.additionalTags.isNotEmpty()) {
                        item { TagSection("Etiquetas adicionales", d.additionalTags, AdditionalColor, TagChipVariant.OUTLINED, onOpenTag) }
                    }
                    if (d.summary.fandoms.isNotEmpty()) {
                        item { TagSection("Fandoms", d.summary.fandoms, FandomColor, TagChipVariant.FILLED_SECONDARY, onOpenTag) }
                    }

                    item {
                        Section("Capítulos (${d.chapters.size})") {
                                Text(
                                    "El botón \"TXT\" exporta el capítulo a la carpeta Descargas. El icono de flecha lo guarda para leer sin conexión (y se marca con ✓).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    items(chapters, key = { it.index }) { ch ->
                        ChapterRow(
                            index = ch.index,
                            chapter = ch,
                            downloaded = downloadedIds.contains(ch.index),
                            downloading = ch.index in downloadingChapter,
                            progress = chapterProgress[ch.index],
                            onClick = { onOpenChapter(ch.index) },
                            onDownload = { downloadChapter(ch) },
                            onExport = { exportChapter(ch) },
                            onRemoveDownload = { removeChapterIndex = ch.index },
                        )
                    }

                    // ---- Comments ----
                    item {
                        Section("Comentarios (${d.summary.comments})") {
                            CommentSection(
                                chapters = chapters,
                                selectedIndex = commentChapterIndex,
                                onSelectChapter = { commentChapterIndex = it },
                                comments = comments,
                                loading = commentsLoading,
                                error = commentsError,
                                onRetry = { commentReloadTick++ },
                                onOpenForm = {
                                    replyTo = null
                                    showCommentForm = true
                                },
                                onReply = { id ->
                                    replyTo = id
                                    showCommentForm = true
                                },
                                onOpenAuthor = onOpenAuthor,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Remove single chapter download dialog ----
    removeChapterIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { removeChapterIndex = null },
            title = { Text("Quitar capítulo de las descargas") },
            text = { Text("El capítulo ${index + 1} se eliminará de la descarga local.") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeDownloadedChapter(workId, index)
                    refreshDownloads()
                    removeChapterIndex = null
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { removeChapterIndex = null }) { Text("Cancelar") }
            },
        )
    }

    // ---- Comment form ----
    if (showCommentForm) {
        val d = detail
        AlertDialog(
            onDismissRequest = { showCommentForm = false },
            title = {
                Text(if (replyTo == null) "Deja un comentario" else "Responder")
            },
            text = {
                Column {
                    Text(
                        "Como invitado (nombre + email; el email no se publica). ${replyTo?.let { "Respondiendo a un comentario." } ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = commentName,
                        onValueChange = { commentName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commentEmail,
                        onValueChange = { commentEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commentBody,
                        onValueChange = { commentBody = it },
                        label = { Text("Comentario") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !posting && commentName.isNotBlank() && commentEmail.isNotBlank() && commentBody.isNotBlank(),
                    onClick = {
                        val dd = d ?: return@TextButton
                        val ch = dd.chapters.getOrNull(commentChapterIndex)
                        posting = true
                        scope.launch {
                            try {
                                store.prefs.commentName = commentName.trim()
                                store.prefs.commentEmail = commentEmail.trim()
                                store.savePrefs()
                                val err = container.client.postComment(
                                    workId = workId,
                                    chapterId = ch?.chapterId,
                                    name = commentName.trim(),
                                    email = commentEmail.trim(),
                                    content = commentBody.trim(),
                                    replyTo = replyTo,
                                )
                                if (err == null) {
                                    showCommentForm = false
                                    commentBody = ""
                                    commentReloadTick++
                                    snackbar.showSnackbar("Comentario enviado")
                                } else {
                                    snackbar.showSnackbar(err)
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar("No se pudo enviar: ${e.message}")
                            } finally {
                                posting = false
                            }
                        }
                    },
                ) {
                    if (posting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Enviar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentForm = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CommentSection(
    chapters: List<ChapterInfo>,
    selectedIndex: Int,
    onSelectChapter: (Int) -> Unit,
    comments: List<Ao3Comment>?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenForm: () -> Unit,
    onReply: (Long) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
) {
    if (chapters.size > 1) {
        var open by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Capítulo: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { open = true }) {
                Text("${selectedIndex + 1}${chapters.getOrNull(selectedIndex)?.title?.let { " · $it" }?.take(40) ?: ""}")
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                chapters.forEachIndexed { i, ch ->
                    DropdownMenuItem(
                        text = { Text("${i + 1}. ${ch.title.ifBlank { "Sin título" }}") },
                        onClick = { onSelectChapter(i); open = false },
                    )
                }
            }
        }
    }

    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Cargando comentarios…", style = MaterialTheme.typography.bodySmall)
        }
        error != null -> Column {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
        comments == null -> Text(
            "Los comentarios de este capítulo no están disponibles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comments.isEmpty() -> Text(
            "Todavía no hay comentarios en este capítulo. ¡Anímate a dejar el primero!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            comments.forEach { comment ->
                CommentItem(comment, onReply, onOpenAuthor)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenForm, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Deja un comentario")
    }
}

@Composable
private fun CommentItem(comment: Ao3Comment, onReply: (Long) -> Unit, onOpenAuthor: (String) -> Unit = {}) {
    val commentUser = usernameFromAuthorUrl(comment.authorUrl)
    val isReply = comment.depth > 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (comment.depth * 18).dp, top = 4.dp, bottom = 4.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isReply) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var avatar by remember(comment.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(comment.avatarUrl) {
                        avatar = comment.avatarUrl?.let { AvatarImages.load(it) }
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isReply) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = avatar
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Avatar de ${comment.author}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                comment.author.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isReply) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                comment.author,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (commentUser != null) {
                                    Modifier.clickable { onOpenAuthor(commentUser) }
                                } else {
                                    Modifier
                                },
                            )
                            if (commentUser == null) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        "Invitado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        if (comment.date.isNotBlank()) {
                            Text(
                                comment.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isReply) {
                        Text(
                            "↳",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = htmlToAnnotated(
                        html = comment.html,
                        baseColor = MaterialTheme.colorScheme.onSurface,
                        linkColor = MaterialTheme.colorScheme.primary,
                        plainText = comment.text,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = { onReply(comment.id) },
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Responder", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
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
private fun TagSection(
    title: String,
    tags: List<String>,
    color: androidx.compose.ui.graphics.Color,
    variant: TagChipVariant,
    onOpenTag: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Section(title) {
        val visible = if (expanded) tags else tags.take(20)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach {
                TagChip(it, color, variant = variant, onClick = { onOpenTag(it) })
            }
            if (!expanded && tags.size > 20) {
                TagChip(
                    "+${tags.size - 20} más",
                    color = color,
                    variant = variant,
                    onClick = { expanded = true },
                )
            }
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
    downloading: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onExport: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val semantic = LocalSemanticColors.current
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
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
        if (progress != null && progress >= 0.97f) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Leído",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        // Export to .txt: clearly labeled chip, distinct from the offline download.
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onExport,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("TXT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(4.dp))
        // Offline download toggle (icon button; turns green + ✓ once saved).
        if (downloading) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = if (downloaded) onRemoveDownload else onDownload) {
                Icon(
                    if (downloaded) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = if (downloaded) "Quitar de descargas" else "Guardar para leer sin conexión",
                    tint = if (downloaded) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
