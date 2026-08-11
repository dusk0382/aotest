package net.spin.ao3.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.ui.theme.LocalSemanticColors
import net.spin.ao3.util.Line
import net.spin.ao3.util.LineKind
import net.spin.ao3.util.SearchMatch
import net.spin.ao3.util.Segment
import net.spin.ao3.util.escapeHtml
import net.spin.ao3.util.findInPages
import net.spin.ao3.util.htmlToLines

private const val BASE_URL = "https://archiveofourown.org"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    workId: Long,
    initialChapter: Int,
    onBack: () -> Unit,
    onOpenWork: () -> Unit = {},
) {
    val store = container.store
    val context = LocalContext.current

    var workTitle by remember { mutableStateOf("") }
    var workAuthor by remember { mutableStateOf("") }
    var chapters by remember { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(initialChapter.coerceAtLeast(0)) }
    var content by remember { mutableStateOf<String?>(null) }
    var chapterTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var theme by remember { mutableStateOf(store.prefs.theme) }
    var fontSize by remember { mutableIntStateOf(store.prefs.fontSizeSp) }
    var serif by remember { mutableStateOf(store.prefs.serif) }
    var lineHeight by remember { mutableFloatStateOf(store.prefs.lineHeight) }
    var margins by remember { mutableIntStateOf(store.prefs.margins) }
    /** Scroll continuo (WebView) frente a paginado (páginas deslizables). */
    var paged by remember { mutableStateOf(store.prefs.paged) }

    var retryTick by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    // Find-in-chapter search.
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var searchIndex by remember { mutableIntStateOf(-1) }
    // Scroll mode: WebView's native find reports the active/total matches.
    var findCount by remember { mutableIntStateOf(0) }
    var findActive by remember { mutableIntStateOf(0) }

    // Reading progress (per chapter, saved to Store).
    var currentRatio by remember { mutableFloatStateOf(0f) }
    var chapterProgress by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    // Tap on the text toggles the bars + full screen; scrolls and links are left alone.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(false) }
    val toggleChrome: () -> Unit = { chromeVisible = !chromeVisible }

    // The reader is immersive: the system bars stay hidden (a swipe reveals
    // them transiently). This also removes the status/navigation bar gaps
    // through which the story text used to peek behind the chrome bars.
    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window
        val view = window?.decorView
        if (window != null && view != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Short hint when the bars are first hidden.
    LaunchedEffect(chromeVisible) {
        if (!chromeVisible) {
            showHint = true
            delay(2600)
            showHint = false
        }
    }

    // Always restore the system bars when leaving the reader.
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val webView = remember { mutableStateOf<WebView?>(null) }
    var pendingScroll by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // Chapters whose NEXT chapter has already been prefetched (per reader session).
    var prefetched by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Paginated mode data (only parsed when active).
    val lines = remember(content, paged) {
        if (paged) htmlToLines(content ?: "") else emptyList()
    }
    // Pages are packed to the exact viewport inside PagedReaderBody's
    // BoxWithConstraints; this mirror lets progress/search/bottom-bar read
    // the current page count.
    var measuredPages by remember { mutableStateOf<List<List<Line>>>(emptyList()) }
    val pagerState = rememberPagerState(initialPage = 0) { measuredPages.size.coerceAtLeast(1) }

    fun currentHistory(): Store.HistoryEntry? =
        store.history().firstOrNull { it.id == workId }

    // 1) Load work metadata + chapter list
    LaunchedEffect(workId, retryTick) {
        if (chapters.isNotEmpty()) return@LaunchedEffect
        val downloaded = store.download(workId)
        if (downloaded != null) {
            workTitle = downloaded.title
            chapters = downloaded.chapters
        } else {
            try {
                val detail = container.client.getWork(workId)
                workTitle = detail.summary.title
                workAuthor = detail.summary.author
                chapters = detail.chapters
            } catch (e: Exception) {
                error = e.message ?: "No se pudo cargar la obra"
                loading = false
                return@LaunchedEffect
            }
        }
        val hist = currentHistory()
        currentIndex = currentIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        chapterProgress = hist?.chapterProgress.orEmpty()
        pendingScroll = if (hist != null && hist.chapterIndex == currentIndex) hist.scrollRatio else 0f
        currentRatio = if (hist != null && hist.chapterIndex == currentIndex) hist.scrollRatio else 0f
        loading = false
    }

    // 2) Load current chapter content (local download first, then network)
    LaunchedEffect(currentIndex, chapters, retryTick) {
        val chapter = chapters.getOrNull(currentIndex) ?: return@LaunchedEffect
        loading = true
        error = null
        if (chapter.content != null) {
            content = chapter.content
            chapterTitle = chapter.title
        } else {
            try {
                val fetched = container.client.getChapter(workId, chapter)
                content = fetched.content
                chapterTitle = fetched.title
            } catch (e: Exception) {
                content = null
                error = e.message ?: "No se pudo cargar el capítulo"
            }
        }
        loading = false
        // Prefetch the next chapter (best-effort, only after a successful load)
        // so advancing is instant: the call fills chapterCache + the on-disk
        // cache, so getChapter returns immediately from cache when the user
        // actually moves on.
        val nextIndex = currentIndex + 1
        if (error == null && nextIndex < chapters.size && nextIndex !in prefetched) {
            val next = chapters[nextIndex]
            if (next.content == null) {
                prefetched = prefetched + nextIndex
                scope.launch { runCatching { container.client.getChapter(workId, next) } }
            }
        }
    }

    // 2b) Paged mode: restore or reset the page whenever chapter content or the
    // (viewport-packed) page count changes.
    LaunchedEffect(paged, content, currentIndex, measuredPages.size) {
        if (!paged || measuredPages.isEmpty()) return@LaunchedEffect
        if (pendingScroll > 0f && measuredPages.size > 1) {
            val target = (pendingScroll * (measuredPages.size - 1)).roundToInt().coerceIn(0, measuredPages.size - 1)
            pagerState.scrollToPage(target)
            pendingScroll = 0f
        } else {
            pagerState.scrollToPage(0)
        }
    }

    fun saveProgressNow() {
        val t = workTitle
        if (t.isEmpty()) return
        val a = workAuthor
        val idx = currentIndex
        fun commit(ratio: Float) {
            currentRatio = ratio
            val base = currentHistory()?.chapterProgress.orEmpty().toMutableMap()
            base[idx] = ratio
            chapterProgress = base
            store.updateHistory(
                Store.HistoryEntry(
                    id = workId,
                    title = t,
                    author = a,
                    chapterIndex = idx,
                    scrollRatio = ratio,
                    chapterProgress = base,
                    at = System.currentTimeMillis(),
                ),
            )
        }
        if (paged) {
            commit(if (measuredPages.size > 1) pagerState.currentPage / (measuredPages.size - 1).toFloat() else 0f)
        } else {
            val wv = webView.value ?: return
            readScrollRatio(wv) { commit(it) }
        }
    }

    // Periodic progress save
    LaunchedEffect(workId) {
        while (true) {
            delay(3500)
            saveProgressNow()
        }
    }

    fun goToChapter(index: Int) {
        if (index !in chapters.indices || index == currentIndex) return
        saveProgressNow()
        pendingScroll = 0f
        currentRatio = 0f
        currentIndex = index
    }

    BackHandler { saveProgressNow(); onBack() }

    // 3) Render chapter into the WebView whenever content/css changes (scroll mode only).
    val css = remember(theme, fontSize, serif, lineHeight, margins) { readerCss(theme, fontSize, serif, lineHeight, margins) }
    val html = remember(content, workTitle, currentIndex, chapterTitle, css) {
        buildChapterHtml(workTitle, currentIndex, chapterTitle, content ?: "", css)
    }

    val onPageFinished = rememberUpdatedState {
        val wv = webView.value ?: return@rememberUpdatedState
        if (pendingScroll > 0f) {
            restoreScroll(wv, pendingScroll)
            pendingScroll = 0f
        }
    }

    val bgColor = readerBgColor(theme).toArgb()

    fun applyPrefs(block: () -> Unit) {
        if (paged) {
            pendingScroll = if (measuredPages.size > 1) pagerState.currentPage / (measuredPages.size - 1).toFloat() else 0f
            block()
        } else {
            val wv = webView.value
            if (wv != null && content != null) {
                wv.evaluateJavascript(
                    "(function(){var h=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;var y=window.scrollY||0;return String((h>vh)?y/(h-vh):0);})()",
                ) { r ->
                    pendingScroll = r?.toFloatOrNull() ?: 0f
                    block()
                }
            } else {
                block()
            }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchMatches = emptyList()
        searchIndex = -1
        findCount = 0
        findActive = 0
        webView.value?.evaluateJavascript(jsClearFind(), null)
    }

    // Recompute matches (paged) or ask the WebView to find (scroll) on query change.
    LaunchedEffect(searchQuery, paged, content) {
        if (paged && searchQuery.isNotBlank()) {
            searchMatches = findInPages(measuredPages, searchQuery)
            searchIndex = if (searchMatches.isEmpty()) -1 else 0
            if (searchMatches.isNotEmpty()) goToSearchMatch(
                paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState,
            )
        } else if (!paged) {
            val wv = webView.value
            if (wv != null) {
                // JS find (WebView's findAllAsync is unreliable with data: HTML).
                wv.evaluateJavascript(jsClearFind(), null)
                if (searchQuery.isNotBlank()) {
                    wv.evaluateJavascript(jsFindInPage(searchQuery), null)
                }
            }
            searchMatches = emptyList()
            searchIndex = -1
        }
    }

    // Scroll-mode: counter + navigation arrive asynchronously from the JS.
    DisposableEffect(webView) {
        onDispose { webView.value?.evaluateJavascript(jsClearFind(), null) }
    }

    val highlights = if (paged && searchQuery.isNotBlank()) {
        searchMatches
            .filter { it.page == pagerState.currentPage }
            .map { m ->
                val isCurrent = searchIndex in searchMatches.indices && searchMatches[searchIndex] == m
                (m.start until m.end) to if (isCurrent) 0.6f else 0.28f
            }
    } else {
        emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        // Body: paginated pages (viewport-packed) or the scroll WebView.
        if (paged && lines.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val viewportPages = remember(lines, maxWidth, maxHeight, fontSize, lineHeight, serif, margins, measurer) {
                    packPagesToViewport(lines, measurer, density, maxWidth, maxHeight, fontSize, lineHeight, serif, margins)
                }
                SideEffect { measuredPages = viewportPages }
                PagedReaderBody(
                    pages = viewportPages,
                    pagerState = pagerState,
                    highlights = highlights,
                    theme = theme,
                    fontSize = fontSize,
                    serif = serif,
                    lineHeight = lineHeight,
                    margins = margins,
                    toggleChrome = toggleChrome,
                )
            }
        } else {
            ReaderWebViewHost(
                webView = webView,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = bgColor,
                onPageFinished = { onPageFinished.value() },
                onToggleChrome = toggleChrome,
                onFindResult = { active, total ->
                    findActive = active
                    findCount = total
                },
                htmlToLoad = html,
                onJsFindResult = { count ->
                    findCount = count
                    findActive = 0
                },
            )
        }

        // Loading overlay
        if (loading) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando capítulo…", color = readerFgColor(theme))
                    }
                }
            }
        }

        // Error state
        if (error != null && content == null) {
            Surface(
                color = readerBgColor(theme),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error ?: "",
                            color = readerFgColor(theme),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            retryTick += 1
                            loading = true
                            error = null
                        }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }

        // Top chrome: title bar + (optional) search bar. Hidden with a tap on the text.
        if (chromeVisible) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.97f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { saveProgressNow(); onBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = readerFgColor(theme),
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { saveProgressNow(); onOpenWork() },
                        ) {
                            Text(
                                workTitle.ifBlank { "Cargando…" },
                                color = readerFgColor(theme),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (chapters.isNotEmpty()) "Capítulo ${currentIndex + 1} de ${chapters.size}" else " ",
                                    color = readerFgColor(theme).copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Abrir ficha de la obra",
                                    tint = readerFgColor(theme).copy(alpha = 0.45f),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) clearSearch()
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar en el capítulo",
                                tint = if (searchOpen) readerAccentColor(theme) else readerFgColor(theme),
                            )
                        }
                        IconButton(onClick = {
                            val target = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) {
                                Store.ReaderTheme.BLACK
                            } else {
                                Store.ReaderTheme.LIGHT
                            }
                            applyPrefs { theme = target }
                            store.prefs.theme = target
                            store.savePrefs()
                        }) {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = "Tema claro/oscuro",
                                tint = readerFgColor(theme),
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = readerFgColor(theme))
                        }
                    }
                    if (searchOpen) {
                        HorizontalDivider(color = readerFgColor(theme).copy(alpha = 0.12f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = readerFgColor(theme).copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Buscar en el capítulo…", color = readerFgColor(theme).copy(alpha = 0.45f))
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = readerFgColor(theme), fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = readerFgColor(theme),
                                    unfocusedTextColor = readerFgColor(theme),
                                    cursorColor = readerFgColor(theme),
                                    focusedBorderColor = readerFgColor(theme).copy(alpha = 0.4f),
                                    unfocusedBorderColor = readerFgColor(theme).copy(alpha = 0.25f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    searchIndex = nextSearchIndex(searchIndex, searchMatches.size)
                                    goToSearchMatch(
                                        paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState,
                                    )
                                }),
                                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                            )
                            val total = if (paged) searchMatches.size else findCount
                            val current = if (paged) searchIndex + 1 else findActive + 1
                            Text(
                                if (searchQuery.isBlank() || total <= 0) "0/0" else "$current/$total",
                                color = readerFgColor(theme).copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            IconButton(
                            onClick = { searchIndex = prevSearchIndex(searchIndex, searchMatches.size); goToSearchMatch(
                                paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState,
                            ) },
                            enabled = searchMatches.isNotEmpty(),
                        ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Anterior",
                                    tint = readerFgColor(theme),
                                )
                            }
                            IconButton(
                                onClick = { searchIndex = nextSearchIndex(searchIndex, searchMatches.size); goToSearchMatch(
                                    paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState,
                                ) },
                                enabled = searchMatches.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Siguiente",
                                    tint = readerFgColor(theme),
                                )
                            }
                            IconButton(onClick = { clearSearch(); searchOpen = false }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cerrar búsqueda", tint = readerFgColor(theme))
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar (chapter indicator + navigation + reading progress)
        if (chromeVisible) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.97f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Column {
                    LinearProgressIndicator(
                        progress = { currentRatio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = readerFgColor(theme).copy(alpha = 0.55f),
                        trackColor = readerFgColor(theme).copy(alpha = 0.12f),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { goToChapter(currentIndex - 1) }, enabled = currentIndex > 0) {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Anterior",
                                tint = if (currentIndex > 0) readerFgColor(theme) else readerFgColor(theme).copy(alpha = 0.3f),
                            )
                        }
                        Text(
                            "${currentIndex + 1} / ${chapters.size.coerceAtLeast(1)}" +
                                if (paged && measuredPages.size > 1) " · ${pagerState.currentPage + 1}/${measuredPages.size}" else "",
                            color = readerFgColor(theme),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showChapters = true }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { goToChapter(currentIndex + 1) },
                            enabled = currentIndex < chapters.size - 1,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Siguiente",
                                tint = if (currentIndex < chapters.size - 1) readerFgColor(theme) else readerFgColor(theme).copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }

        // Hint when the chrome is hidden
        if (!chromeVisible && showHint) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "Toca el texto para mostrar los controles",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    // ---- Settings sheet ----
    if (showSettings) {
        val sheetState = rememberModalBottomSheetState()
        var draftFont by remember { mutableIntStateOf(fontSize) }
        var draftLineHeight by remember { mutableFloatStateOf(lineHeight) }
        var draftMargins by remember { mutableIntStateOf(margins) }
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("Modo de lectura", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !paged,
                        onClick = {
                            applyPrefs { paged = false }
                            store.prefs.paged = false
                            store.savePrefs()
                        },
                        label = { Text("Scroll continuo") },
                    )
                    FilterChip(
                        selected = paged,
                        onClick = {
                            applyPrefs { paged = true }
                            store.prefs.paged = true
                            store.savePrefs()
                        },
                        label = { Text("Paginado") },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Tema de lectura", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Store.ReaderTheme.entries.forEach { option ->
                        FilterChip(
                            selected = theme == option,
                            onClick = { applyPrefs { theme = option } },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Vista previa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ReaderPreview(
                    theme = theme,
                    fontSize = draftFont,
                    lineHeight = draftLineHeight,
                    serif = serif,
                    margins = draftMargins,
                )
                Spacer(Modifier.height(20.dp))
                Text("Tamaño de letra", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = draftFont.toFloat(),
                        onValueChange = { draftFont = it.toInt() },
                        valueRange = 13f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "${draftFont} sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(20.dp))
                Text("Interlineado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1.2", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = draftLineHeight,
                        onValueChange = { draftLineHeight = it },
                        valueRange = 1.2f..2.4f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("2.4", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "%.2f".format(draftLineHeight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(20.dp))
                Text("Márgenes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Estrechos", 1 to "Normales", 2 to "Amplios").forEach { (value, label) ->
                        FilterChip(
                            selected = draftMargins == value,
                            onClick = { draftMargins = value },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Tipografía", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = serif, onClick = { applyPrefs { serif = true } }, label = { Text("Serif") })
                    FilterChip(selected = !serif, onClick = { applyPrefs { serif = false } }, label = { Text("Sans serif") })
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        store.prefs.fontSizeSp = draftFont
                        store.prefs.lineHeight = draftLineHeight
                        store.prefs.margins = draftMargins
                        store.prefs.theme = theme
                        store.prefs.serif = serif
                        store.prefs.paged = paged
                        store.savePrefs()
                        fontSize = draftFont
                        lineHeight = draftLineHeight
                        margins = draftMargins
                        showSettings = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Aplicar")
                }
            }
        }
    }

    // ---- Chapters sheet ----
    if (showChapters) {
        val sheetState = rememberModalBottomSheetState()
        val downloadedIds = remember(workId) { store.downloadedChapterIds(workId) }
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = sheetState,
        ) {
            Text(
                "Capítulos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(chapters) { index, chapter ->
                    val prog = chapterProgress[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showChapters = false
                                goToChapter(index)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                chapter.title.ifBlank { "Capítulo ${index + 1}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (prog != null && prog < 0.97f && prog > 0.01f) {
                                Spacer(Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier.fillMaxWidth(0.5f).height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                        if (downloadedIds.contains(index)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Descargado",
                                tint = LocalSemanticColors.current.success,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (prog != null && prog >= 0.97f) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Leído",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    DisposableEffect(Unit) {
        onDispose { saveProgressNow() }
    }
}

// ---- Find-in-chapter helpers (file-level so LaunchedEffects can call them) --

private fun goToSearchMatch(
    paged: Boolean,
    matches: List<SearchMatch>,
    index: Int,
    webView: MutableState<WebView?>,
    query: String,
    scope: kotlinx.coroutines.CoroutineScope,
    pagerState: PagerState,
) {
    val m = matches.getOrNull(index) ?: return
    if (paged) {
        scope.launch { pagerState.scrollToPage(m.page) }
    } else {
        val wv = webView.value
        if (wv != null && query.isNotBlank()) {
            wv.evaluateJavascript(jsGoToMatch(index), null)
        }
    }
}

private fun nextSearchIndex(index: Int, size: Int): Int {
    if (size <= 0) return -1
    return (index + 1) % size
}

private fun prevSearchIndex(index: Int, size: Int): Int {
    if (size <= 0) return -1
    return (index - 1 + size) % size
}

// ---- Paginated reader body --------------------------------------------------

@Composable
private fun PagedReaderBody(
    pages: List<List<Line>>,
    pagerState: PagerState,
    highlights: List<Pair<IntRange, Float>>,
    theme: Store.ReaderTheme,
    fontSize: Int,
    serif: Boolean,
    lineHeight: Float,
    margins: Int,
    toggleChrome: () -> Unit,
) {
    val (bgHex, fgHex, accentHex) = readerPalette(theme)
    val bg = Color(android.graphics.Color.parseColor(bgHex))
    val fg = Color(android.graphics.Color.parseColor(fgHex))
    val accent = Color(android.graphics.Color.parseColor(accentHex))
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val horizontalPad = when (margins) {
        0 -> 16.dp
        2 -> 34.dp
        else -> 24.dp
    }
    val scope = rememberCoroutineScope()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        Surface(
            color = bg,
            modifier = Modifier
                .fillMaxSize()
                // Manga-style tap zones: left third = previous page, right
                // third = next page, middle = toggle the chrome bars.
                .pointerInput(pagerState) {
                    detectTapGestures { offset ->
                        val w = size.width
                        val third = w / 3f
                        when {
                            offset.x < third -> if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            } else {
                                toggleChrome()
                            }
                            offset.x > third * 2 -> if (pagerState.currentPage < pages.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                toggleChrome()
                            }
                            else -> toggleChrome()
                        }
                    }
                },
        ) {
            // Pages are packed to exactly fit the viewport (see
            // packPagesToViewport), so normally nothing scrolls inside a page:
            // a swipe or a tap on a side lands on a complete, continuous page.
            // A rare oversized line (huge paragraph or <pre> block) still gets
            // a per-page scroll fallback so no text is unreachable.
            val pageScroll = remember { ScrollState(0) }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(horizontal = horizontalPad, vertical = 16.dp),
            ) {
                Text(
                    text = linesToAnnotated(pages[page], fontSize, fg, accent, highlights),
                    style = TextStyle(
                        color = fg,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeight).sp,
                        fontFamily = family,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "· ${page + 1} de ${pages.size} ·",
                    fontSize = (fontSize * 0.8f).sp,
                    color = fg.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Packs [lines] into pages that fit the measured viewport (px) without ever
 * splitting a paragraph. For every page we BINARY-SEARCH the largest number of
 * lines that fits, measuring the exact annotated string the page will render
 * (same style, same width) — so pages fill the viewport edge-to-edge instead
 * of leaving a few lines of unused space (which inflated the page count and
 * showed a visible gap at the bottom of most pages).
 *
 * An oversized single line (huge <pre>/heading) still gets its own page; the
 * per-page scroll fallback in [PagedReaderBody] keeps it reachable.
 */
private fun packPagesToViewport(
    lines: List<Line>,
    measurer: TextMeasurer,
    density: Density,
    maxWidth: Dp,
    maxHeight: Dp,
    fontSize: Int,
    lineHeight: Float,
    serif: Boolean,
    margins: Int,
): List<List<Line>> {
    if (lines.isEmpty()) return emptyList()
    with(density) {
        val horizontalPad = when (margins) {
            0 -> 16.dp
            2 -> 34.dp
            else -> 24.dp
        }.roundToPx()
        val verticalPad = 16.dp.roundToPx()
        val footerPx = (fontSize * 0.8f).sp.toPx().roundToInt() +
            8.dp.roundToPx() + 4.dp.roundToPx() + 6.dp.roundToPx()
        val widthPx = maxWidth.roundToPx() - 2 * horizontalPad
        val heightPx = maxHeight.roundToPx() - 2 * verticalPad - footerPx
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
        val style = TextStyle(
            color = Color.Black,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeight).sp,
            fontFamily = family,
        )
        fun fits(from: Int, to: Int): Boolean {
            // to exclusive; measures EXACTLY the string the page will render.
            val ann = linesToAnnotated(lines.subList(from, to), fontSize, Color.Black, Color.Black)
            return measurer.measure(
                ann,
                style = style,
                constraints = Constraints(maxWidth = widthPx),
            ).size.height <= heightPx
        }
        val pages = mutableListOf<List<Line>>()
        var i = 0
        while (i < lines.size) {
            var lo = i
            var hi = lines.size
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (fits(i, mid)) lo = mid else hi = mid - 1
            }
            val end = lo.coerceAtLeast(i + 1)
            pages += lines.subList(i, end)
            i = end
        }
        return pages
    }
}

/** Converts paginated [Line]s into a styled AnnotatedString using the reader palette. */
private fun linesToAnnotated(
    lines: List<Line>,
    fontSize: Int,
    fg: Color,
    accent: Color,
    highlights: List<Pair<IntRange, Float>> = emptyList(),
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    lines.forEachIndexed { i, line ->
        if (i > 0) builder.append("\n\n")
        when (line.kind) {
            LineKind.SEPARATOR -> builder.append("  ✦   ✦   ✦  ")
            LineKind.HEADING -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (fontSize + 2).sp))
                line.segments.forEach { builder.append(it.text) }
                builder.pop()
            }
            LineKind.QUOTE -> {
                builder.pushStyle(SpanStyle(color = fg.copy(alpha = 0.85f)))
                builder.append("“")
                line.segments.forEach { seg ->
                    val pushed = pushSegmentStyle(builder, seg, accent)
                    builder.append(seg.text)
                    repeat(pushed) { builder.pop() }
                }
                builder.append("”")
                builder.pop()
            }
            LineKind.LIST -> {
                builder.append("•  ")
                line.segments.forEach { seg ->
                    val pushed = pushSegmentStyle(builder, seg, accent)
                    builder.append(seg.text)
                    repeat(pushed) { builder.pop() }
                }
            }
            LineKind.CODE -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent))
                builder.append(line.segments.firstOrNull()?.text ?: "")
                builder.pop()
            }
            else -> line.segments.forEach { seg ->
                val pushed = pushSegmentStyle(builder, seg, accent)
                builder.append(seg.text)
                repeat(pushed) { builder.pop() }
            }
        }
    }
    val base = builder.toAnnotatedString()
    if (highlights.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        highlights.forEach { (range, alpha) ->
            addStyle(
                SpanStyle(background = accent.copy(alpha = alpha)),
                range.first.coerceIn(0, base.length),
                range.last.coerceIn(0, base.length),
            )
        }
    }
}

private fun pushSegmentStyle(builder: AnnotatedString.Builder, seg: Segment, accent: Color): Int {
    var pushed = 0
    if (seg.bold) {
        builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        pushed++
    }
    if (seg.italic) {
        builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        pushed++
    }
    if (seg.link) {
        builder.pushStyle(SpanStyle(color = accent))
        pushed++
    }
    return pushed
}

// ---- WebView host -----------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebViewHost(
    webView: MutableState<WebView?>,
    modifier: Modifier = Modifier,
    backgroundColor: Int,
    htmlToLoad: String,
    onPageFinished: () -> Unit,
    onToggleChrome: () -> Unit,
    onFindResult: (Int, Int) -> Unit,
    onJsFindResult: (Int) -> Unit,
) {
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentHtml by rememberUpdatedState(htmlToLoad)
    val currentOnFindResult by rememberUpdatedState(onFindResult)
    val currentOnJsFindResult by rememberUpdatedState(onJsFindResult)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(backgroundColor)
                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                    Handler(Looper.getMainLooper()).post { currentOnFindResult(activeMatchOrdinal, numberOfMatches) }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun toggleChrome() {
                            Handler(Looper.getMainLooper()).post { currentToggleChrome() }
                        }

                        @JavascriptInterface
                        fun onJsFindResult(count: Int) {
                            Handler(Looper.getMainLooper()).post { currentOnJsFindResult(count) }
                        }
                    },
                    "Reader",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        currentOnPageFinished()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                }
                webView.value = this
                loadDataWithBaseURL(BASE_URL, currentHtml, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            val current = webView.value
            if (current != wv) webView.value = wv
            wv.setBackgroundColor(backgroundColor)
            if (wv.url.isNullOrEmpty()) {
                wv.loadDataWithBaseURL(BASE_URL, currentHtml, "text/html", "UTF-8", null)
            }
        },
        onRelease = {
            webView.value = null
            it.destroy()
        },
    )

    // Reload when the html content changes
    LaunchedEffect(htmlToLoad) {
        val wv = webView.value ?: return@LaunchedEffect
        if (!wv.url.isNullOrEmpty()) {
            wv.loadDataWithBaseURL(BASE_URL, htmlToLoad, "text/html", "UTF-8", null)
        }
    }
}

// ---- JS find-in-page (works with data: HTML, unlike findAllAsync) -----------

/**
 * Highlights all occurrences of [query] and reports the count via
 * Reader.onJsFindResult. Uses DOM <mark> wrapping (not the CSS Custom
 * Highlight API) so it behaves identically across every WebView version.
 *
 * IMPORTANT: matches are grouped by text node and each node is wrapped from
 * the LAST match backwards, so the split offsets stay valid. The previous
 * forward loop (plus an accidental mark.appendChild) corrupted the chapter
 * text (letters lost/swapped) whenever a text node had two+ matches.
 */
private fun jsFindInPage(query: String): String {
    val q = query.replace("\\", "\\\\").replace("'", "\\'")
    return """
        (function(){
          try {
            var q = '$q';
            window.__find = [];
            if (!q) { if (window.Reader) window.Reader.onJsFindResult(0); return; }
            var root = document.querySelector('.content') || document.body;
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
            var lq = q.toLowerCase();
            var matches = [];
            while (walker.nextNode()) {
              var node = walker.currentNode;
              var lower = node.nodeValue.toLowerCase();
              var idx = lower.indexOf(lq);
              while (idx >= 0) {
                matches.push({node: node, start: idx, end: idx + lq.length});
                idx = lower.indexOf(lq, idx + lq.length);
              }
            }
            // Group matches per text node (a Map keyed by node IDENTITY — an
            // object literal would coerce every Text node to "[object Text]"
            // and collapse all matches onto one node, corrupting the text),
            // then wrap each node from the last match to the first so the
            // earlier split offsets stay valid.
            var byNode = new Map();
            for (var i = 0; i < matches.length; i++) {
              var m = matches[i];
              if (!byNode.has(m.node)) byNode.set(m.node, []);
              byNode.get(m.node).push(m);
            }
            byNode.forEach(function(list, node) {
              list.sort(function(a, b) { return b.start - a.start; });
              for (var j = 0; j < list.length; j++) {
                var mm = list[j];
                var mark = document.createElement('mark');
                mark.className = 'ao3findmark';
                var tail = node.splitText(mm.end);
                var mid = node.splitText(mm.start);
                node.parentNode.insertBefore(mark, tail);
                mark.appendChild(mid);
                mm.el = mark;
              }
            });
            window.__find = matches;
            if (window.Reader) window.Reader.onJsFindResult(matches.length);
          } catch (e) {
            if (window.Reader) window.Reader.onJsFindResult(0);
          }
        })();
    """
}

/** Scrolls to the [index]-th match (WebView adds ~100px of bottom chrome). */
private fun jsGoToMatch(index: Int): String = """
    (function(){
      try {
        if (!window.__find || index >= window.__find.length) return;
        var m = window.__find[$index];
        var el = m.el || m.node.parentNode;
        var rect = el.getBoundingClientRect();
        var y = window.scrollY + rect.top - 140;
        window.scrollTo(0, Math.max(0, y));
      } catch (e) {}
    })();
"""

/** Removes highlights and cached matches. */
private fun jsClearFind(): String = """
    (function(){
      try {
        window.__find = [];
        var marks = document.querySelectorAll('mark.ao3findmark');
        for (var i = 0; i < marks.length; i++) {
          var m = marks[i];
          m.parentNode.replaceChild(document.createTextNode(m.textContent), m);
        }
        if (window.CSS && CSS.highlights) { try { CSS.highlights.delete('ao3find'); } catch(e) {} }
      } catch (e) {}
    })();
"""

// ---- Scroll helpers ---------------------------------------------------------

private fun readScrollRatio(wv: WebView, cb: (Float) -> Unit) {
    wv.evaluateJavascript(
        "(function(){var h=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;var y=window.scrollY||0;return String((h>vh)?y/(h-vh):0);})()",
    ) { r -> cb(r?.toFloatOrNull() ?: 0f) }
}

private fun restoreScroll(wv: WebView, ratio: Float) {
    if (ratio <= 0f) return
    wv.evaluateJavascript(
        "(function(){var h=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;window.scrollTo(0, ${ratio}*(h-vh));})()",
        null,
    )
}

// ---- Reader theming ---------------------------------------------------------

private fun readerPalette(theme: Store.ReaderTheme): Triple<String, String, String> = when (theme) {
    Store.ReaderTheme.LIGHT -> Triple("#ffffff", "#1c1c1e", "#2F6B35")
    Store.ReaderTheme.SEPIA -> Triple("#f6edd9", "#433422", "#4F7A3A")
    Store.ReaderTheme.DARK -> Triple("#1b1b1f", "#d6d3d0", "#9AD8A6")
    Store.ReaderTheme.BLACK -> Triple("#000000", "#d4d4d4", "#8FCF9E")
}

private fun readerBgColor(theme: Store.ReaderTheme): Color = Color(android.graphics.Color.parseColor(readerPalette(theme).first))

private fun readerFgColor(theme: Store.ReaderTheme): Color = Color(android.graphics.Color.parseColor(readerPalette(theme).second))

private fun readerAccentColor(theme: Store.ReaderTheme): Color = Color(android.graphics.Color.parseColor(readerPalette(theme).third))

// ---- Live typography preview (settings sheet) --------------------------------

@Composable
private fun ReaderPreview(
    theme: Store.ReaderTheme,
    fontSize: Int,
    lineHeight: Float,
    serif: Boolean,
    margins: Int,
) {
    val (bgHex, fgHex, _) = readerPalette(theme)
    val bg = Color(android.graphics.Color.parseColor(bgHex))
    val fg = Color(android.graphics.Color.parseColor(fgHex))
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val horizontalPad = when (margins) {
        0 -> 12.dp
        2 -> 26.dp
        else -> 18.dp
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, fg.copy(alpha = 0.14f), MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(horizontal = horizontalPad, vertical = 14.dp)) {
            Text(
                "La carta llegó al amanecer",
                fontSize = (fontSize * 0.95f).sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hermione la leyó dos veces antes de guardarla. El viento movía las cortinas " +
                    "y, en la mesa, el té ya estaba frío. —¿Vas a responderle? —preguntó Ron, " +
                    "apoyando la barbilla en la mano. Ella sonrió sin apartar la vista de la página.",
                fontSize = (fontSize * 0.88f).sp,
                lineHeight = (fontSize * 0.88f * lineHeight).sp,
                fontFamily = family,
                color = fg,
            )
        }
    }
}

private fun marginPadding(margins: Int): Pair<String, String> = when (margins) {
    0 -> "12px 14px 48px" to "22px 26px 58px"
    2 -> "30px 36px 80px" to "44px 52px 96px"
    else -> "18px 22px 60px" to "28px 34px 72px"
}

private fun readerCss(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean, lineHeight: Float, margins: Int): String {
    val (bg, fg, accent) = readerPalette(theme)
    val subtle = if (theme == Store.ReaderTheme.SEPIA) "#7a6a50" else "#9a9a9a"
    val divider = if (theme == Store.ReaderTheme.LIGHT) "#e0e0e0" else "#3a3a3f"
    val family = if (serif) "Georgia, 'Times New Roman', serif" else "Roboto, 'Helvetica Neue', sans-serif"
    val colorScheme = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "light" else "dark"
    val (padMobile, padTablet) = marginPadding(margins)
    return """
        :root { color-scheme: $colorScheme; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body {
            background: $bg;
            color: $fg;
            font-family: $family;
            font-size: ${sizeSp}px;
            line-height: $lineHeight;
            overflow-wrap: break-word;
            padding: $padMobile;
        }
        .meta { text-align: center; margin-bottom: 26px; }
        .meta .work { font-size: 0.8em; color: $subtle; letter-spacing: 0.02em; }
        .meta .chap { font-size: 1.15em; font-weight: 600; margin-top: 4px; }
        .content h1, .content h2, .content h3 { line-height: 1.35; }
        .content p { margin: 0 0 1.1em; }
        .content blockquote {
            border-left: 3px solid $accent;
            margin: 1.1em 0;
            padding: 0.2em 1em;
            color: $subtle;
        }
        .content hr { border: none; border-top: 1px solid $divider; margin: 1.8em 0; }
        .content em, .content i { font-style: italic; }
        .content strong, .content b { font-weight: 700; }
        .content a { color: $accent; text-decoration: none; }
        .content a:hover { text-decoration: underline; }
        .content img { max-width: 100%; height: auto; border-radius: 6px; }
        mark.ao3findmark { background: $accent; color: #ffffff; border-radius: 2px; padding: 0 1px; }
        ::highlight(ao3find) { background-color: ${accent}66; }
        .content center { display: block; text-align: center; }
        .content .userstuff, .content div { line-height: inherit; }
        blockquote.notes { margin-left: 0; }
        pre { white-space: pre-wrap; }
        @media (min-width: 720px) { body { padding: $padTablet; } }
    """.trimIndent()
}

private fun buildChapterHtml(workTitle: String, chapterIndex: Int, chapterTitle: String, contentHtml: String, css: String): String {
    val titlePart = if (chapterTitle.isNotBlank()) ": ${escapeHtml(chapterTitle)}" else ""
    return """
        <!DOCTYPE html><html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>$css</style>
        <script>
        (function(){
          var moved = false;
          document.addEventListener('touchstart', function(){
            moved = false;
          }, true);
          document.addEventListener('touchmove', function(){ moved = true; }, true);
          document.addEventListener('touchend', function(e){
            if (moved) return;
            if (e.target && e.target.closest && e.target.closest('a,button,select,input,label')) return;
            if (window.Reader) window.Reader.toggleChrome();
          }, true);
        })();
        </script>
        </head><body>
        <div class="meta">
          <div class="work">${escapeHtml(workTitle)}</div>
          <div class="chap">Capítulo ${chapterIndex + 1}$titlePart</div>
        </div>
        <div class="content">$contentHtml</div>
        </body></html>
    """.trimIndent()
}
