package net.spin.ao3.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spin.ao3.data.Ao3Parser
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.data.model.ChapterInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.spin.ao3.util.rememberReaderTts
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
    val online by container.connectivity.online.collectAsState()

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
    // Overflow menu (Translate / DarkMode) so the top bar isn't a wall of icons.
    var showMoreMenu by remember { mutableStateOf(false) }

    // Chapter translation (unofficial gtx endpoint, per-chapter on demand).
    var translationLang by remember { mutableStateOf(store.prefs.translationLang) }
    var translatedHtml by remember { mutableStateOf<String?>(null) }
    var translationOn by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var translateProgress by remember { mutableStateOf(0 to 0) }
    var translateError by remember { mutableStateOf<String?>(null) }
    var showLangPicker by remember { mutableStateOf(false) }
    var translateJob by remember { mutableStateOf<Job?>(null) }

    // Find-in-chapter search.
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var searchIndex by remember { mutableIntStateOf(-1) }
    // Scroll mode: WebView's native find reports the active/total matches.
    var findCount by remember { mutableIntStateOf(0) }
    var findActive by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // ---- Text-to-speech (read the chapter aloud) ----
    // Auto-advance: when TTS finishes a chapter and a next one exists, load it
    // and keep reading. The flag makes the chapter-load effect re-start TTS
    // once the new chapter's content is ready (it is async).
    var resumeTtsOnChapterLoad by remember { mutableStateOf(false) }
    // The done-handler is set below (after goToChapter is defined — local
    // functions aren't forward-referenced), so the TTS engine can stay
    // independent of the navigation code.
    val currentTtsDone = remember { mutableStateOf<() -> Unit>({}) }
    val tts = rememberReaderTts { currentTtsDone.value() }
    fun startTts() {
        resumeTtsOnChapterLoad = false
        tts.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            return
        }
        // Parse off the main thread: Jsoup over a long chapter is expensive and
        // would freeze the UI right when the user taps play.
        scope.launch {
            val text = withContext(Dispatchers.Default) { Ao3Parser.htmlToPlainText(content ?: return@withContext "") }
            if (text.isBlank()) return@launch
            tts.speak(text, store.prefs.ttsRate)
        }
    }

    // Stop reading when the app goes to the background (call, another app…).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, tts) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) tts.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    // Restore target captured SYNCHRONOUSLY at composition (before any network
    // effect finishes). The previous version set it inside the metadata effect,
    // which raced the chapter render: onPageFinished fired with pendingScroll
    // still 0, so the scroll never restored even though the % was saved right.
    var pendingScroll by remember(workId) {
        mutableFloatStateOf(
            store.history().firstOrNull { it.id == workId && it.chapterIndex == initialChapter }
                ?.scrollRatio ?: 0f,
        )
    }
    // Chapters whose NEXT chapter has already been prefetched (per reader session).
    var prefetched by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Paginated mode data (only parsed when active). The chapter body is
    // either the original or the cached translation (per chapter + language).
    val displayContent = if (translationOn) translatedHtml ?: content else content
    // Lines are parsed OFF the main thread: Jsoup over a long chapter is
    // expensive on low-end CPUs, and doing it in composition froze the reader
    // on every chapter/settings change. [lines] is null while paginating.
    var lines by remember(displayContent, paged) { mutableStateOf<List<Line>?>(null) }
    var paginating by remember { mutableStateOf(false) }
    LaunchedEffect(displayContent, paged) {
        if (!paged) {
            lines = emptyList()
            paginating = false
            return@LaunchedEffect
        }
        paginating = true
        try {
            lines = withContext(Dispatchers.Default) { htmlToLines(displayContent ?: "") }
        } finally {
            paginating = false
        }
    }
    // Pages are packed to the exact viewport inside PagedReaderBody's
    // BoxWithConstraints; this mirror lets progress/search/bottom-bar read
    // the current page count.
    var measuredPages by remember { mutableStateOf<List<List<Line>>>(emptyList()) }
    val pagerState = rememberPagerState(initialPage = 0) { measuredPages.size.coerceAtLeast(1) }

    fun currentHistory(): Store.HistoryEntry? = store.historyEntry(workId)

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
                error = if (!online) {
                    "Sin conexión y esta obra no está descargada. Descargala desde su detalle para leerla sin conexión."
                } else {
                    e.message ?: "No se pudo cargar la obra"
                }
                loading = false
                return@LaunchedEffect
            }
        }
        val hist = currentHistory()
        currentIndex = currentIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        chapterProgress = hist?.chapterProgress.orEmpty()
        // pendingScroll was captured synchronously at composition, so the
        // restore never depends on this network-driven effect finishing in time.
        currentRatio = if (hist != null && hist.chapterIndex == currentIndex) hist.scrollRatio else 0f
        loading = false
    }

    // 2) Load current chapter content (local download first, then network)
    LaunchedEffect(currentIndex, chapters, retryTick) {
        val chapter = chapters.getOrNull(currentIndex) ?: return@LaunchedEffect
        // Manual navigation stops the reader aloud; auto-advance keeps it going
        // (the flag is set by TTS's onDone and cleared after the re-start).
        if (!resumeTtsOnChapterLoad) tts.stop()
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
                error = if (!online) {
                    "Sin conexión y este capítulo no está descargado. Descargá la obra (o este capítulo) para leerlo sin conexión."
                } else {
                    e.message ?: "No se pudo cargar el capítulo"
                }
            }
        }
        // New chapter: reset the translation display (cached translations stay
        // on disk keyed per chapter + language and are reused on return) and
        // cancel any in-flight translation of the previous chapter.
        translateJob?.cancel()
        translatedHtml = null
        translationOn = false
        translateError = null
        // Auto-advance: once the new chapter's content is ready, keep reading.
        if (resumeTtsOnChapterLoad && content != null) {
            resumeTtsOnChapterLoad = false
            startTts()
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

    // 2b) Paged mode: restore / reset / preserve the reading position.
    //  - pendingScroll (chapter change, settings apply) wins;
    //  - a PURE repack (only the page count changed, e.g. rotation) keeps the
    //    relative position instead of snapping back to page 0;
    //  - a chapter/content change resets to the top.
    var lastPageSize by remember { mutableIntStateOf(0) }
    var lastChapterKey by remember { mutableIntStateOf(-1) }
    var lastContentKey by remember { mutableStateOf<String?>(null) }
    // True reading ratio captured in the pack SideEffect BEFORE the pager
    // clamps currentPage to a (smaller) new page count — reading it later in
    // the effect below would yield a clamped value (shrink case) and land on
    // the wrong page.
    var preservedRatio by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paged, displayContent, currentIndex, measuredPages.size) {
        if (!paged || measuredPages.isEmpty()) return@LaunchedEffect
        val newSize = measuredPages.size
        when {
            pendingScroll > 0f && newSize > 1 -> {
                pagerState.scrollToPage((pendingScroll * (newSize - 1)).roundToInt().coerceIn(0, newSize - 1))
                pendingScroll = 0f
            }
            // Same chapter and content: only the page count changed (viewport).
            lastChapterKey == currentIndex && lastContentKey == displayContent && lastPageSize > 1 -> {
                pagerState.scrollToPage((preservedRatio * (newSize - 1)).roundToInt().coerceIn(0, (newSize - 1).coerceAtLeast(0)))
            }
            else -> pagerState.scrollToPage(0)
        }
        lastPageSize = newSize
        lastChapterKey = currentIndex
        lastContentKey = displayContent
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
            // currentRatio is kept fresh by the JS scroll listener (onScrollRatio)
            // on every scroll, so saving is SYNCHRONOUS — it never depends on an
            // async evaluateJavascript round-trip that could fail or race when the
            // reader is being disposed (the old code lost the last chapter and the
            // within-chapter % on quick exits).
            commit(currentRatio)
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

    // Wire the TTS auto-advance (needs goToChapter, defined just above).
    currentTtsDone.value = {
        if (currentIndex < chapters.lastIndex) {
            resumeTtsOnChapterLoad = true
            goToChapter(currentIndex + 1)
        }
    }

    /** Translates the CURRENT chapter into [translationLang] (cached per chapter). */
    fun translateChapter() {
        val c = content ?: return
        if (translating) return
        val lang = translationLang
        // Capture the chapter at launch: the job survives chapter navigation
        // (rememberCoroutineScope), so a stale result must not be applied to a
        // chapter the user already left.
        val chapterAtLaunch = currentIndex
        val key = "w${workId}_c${chapterAtLaunch}_$lang"
        translating = true
        translateError = null
        translateProgress = 0 to 0
        translateJob = scope.launch {
            try {
                val result = container.translator.translateChapterHtml(key, c, lang) { done, total ->
                    translateProgress = done to total
                }
                if (currentIndex == chapterAtLaunch) {
                    translatedHtml = result
                    translationOn = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancel (button or chapter switch) must stay silent.
                throw e
            } catch (e: Exception) {
                translateError = e.message ?: "No se pudo traducir el capítulo"
            } finally {
                translating = false
                translateJob = null
            }
        }
    }

    BackHandler { saveProgressNow(); onBack() }

    // 3) Render chapter into the WebView (scroll mode only). The full HTML is
    // keyed ONLY on the actual content (chapter/translation), NOT on theme/size:
    // style changes go through applyReaderPrefs() via JS without reloading.
    val css = remember(theme, fontSize, serif, lineHeight, margins) { readerCss(theme, fontSize, serif, lineHeight, margins) }
    val contentKey = "${displayContent ?: ""}|$workTitle|$currentIndex|$chapterTitle"
    val html = remember(contentKey, css) {
        buildChapterHtml(workTitle, currentIndex, chapterTitle, displayContent ?: "", css)
    }
    val prefsJson = remember(theme, fontSize, serif, lineHeight, margins) {
        readerPrefsJson(theme, fontSize, serif, lineHeight, margins)
    }

    val onPageFinished = rememberUpdatedState {
        val wv = webView.value ?: return@rememberUpdatedState
        // Only restore once the REAL chapter html rendered: the WebView is
        // first loaded with placeholder html (content == null) and its
        // onPageFinished would otherwise consume pendingScroll on an empty page.
        if (pendingScroll > 0f && content != null) {
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
                    pendingScroll = parseJsRatio(r)
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
    LaunchedEffect(searchQuery, paged, displayContent) {
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
        if (paged && lines == null) {
            // Paginating off the main thread — the overlay below covers this.
            Box(Modifier.fillMaxSize())
        } else if (paged && lines!!.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val pageLines = lines!!
                val viewportPages = remember(pageLines, maxWidth, maxHeight, fontSize, lineHeight, serif, margins, measurer) {
                    packPagesToViewport(pageLines, measurer, density, maxWidth, maxHeight, fontSize, lineHeight, serif, margins)
                }
                SideEffect {
                    // Capture the position BEFORE the pager sees the new page
                    // count (it clamps currentPage, losing the true ratio when
                    // the viewport shrinks). Only when nothing else changed.
                    if (lastPageSize > 1 &&
                        lastChapterKey == currentIndex &&
                        lastContentKey == displayContent
                    ) {
                        preservedRatio = pagerState.currentPage / (lastPageSize - 1).toFloat()
                    }
                    measuredPages = viewportPages
                }
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
                contentKey = contentKey,
                prefsJson = prefsJson,
                onJsFindResult = { count ->
                    findCount = count
                    findActive = 0
                },
                onScrollRatio = { r -> currentRatio = r },
                onOpenLink = { url ->
                    // A link inside the chapter opens in the system browser
                    // instead of being silently swallowed by the reader.
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }

        // Loading overlay (chapter fetch OR off-main pagination)
        if (loading || paginating) {
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

        // Translation progress overlay / error
        if (translating) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.92f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Traduciendo capítulo…",
                            color = readerFgColor(theme),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(14.dp))
                        val done = translateProgress.first
                        val total = translateProgress.second
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth(0.7f).height(4.dp),
                                color = readerAccentColor(theme),
                                trackColor = readerFgColor(theme).copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$done / $total",
                                color = readerFgColor(theme).copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            androidx.compose.material3.CircularProgressIndicator(color = readerAccentColor(theme))
                        }
                        if (translateJob != null) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Cancelar",
                                color = readerAccentColor(theme),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clickable { translateJob?.cancel() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        } else if (translateError != null) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.medium,
                onClick = { translateError = null },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        translateError ?: "",
                        color = readerFgColor(theme),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Toca para cerrar",
                        style = MaterialTheme.typography.labelSmall,
                        color = readerFgColor(theme).copy(alpha = 0.55f),
                    )
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
                        // Overflow menu: Translate + DarkMode live here so the bar
                        // stays at 4 actions (Search, TTS, Settings, ⋮) instead of 6.
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = readerFgColor(theme),
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Traducir capítulo",
                                            color = if (translationOn || translating) readerAccentColor(theme) else readerFgColor(theme),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Translate,
                                            contentDescription = null,
                                            tint = if (translationOn || translating) readerAccentColor(theme) else readerFgColor(theme),
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showLangPicker = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "Tema oscuro" else "Tema claro",
                                            color = readerFgColor(theme),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DarkMode,
                                            contentDescription = null,
                                            tint = readerFgColor(theme),
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        val target = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) {
                                            Store.ReaderTheme.BLACK
                                        } else {
                                            Store.ReaderTheme.LIGHT
                                        }
                                        applyPrefs { theme = target }
                                        store.prefs.theme = target
                                        store.savePrefs()
                                    },
                                )
                            }
                        }
                        IconButton(onClick = {
                            when {
                                tts.speaking -> tts.stop()
                                tts.paused -> tts.resume()
                                else -> startTts()
                            }
                        }) {
                            Icon(
                                if (tts.speaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (tts.speaking) "Detener lectura en voz alta" else "Leer capítulo en voz alta",
                                tint = if (tts.speaking) readerAccentColor(theme) else readerFgColor(theme),
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = readerFgColor(theme))
                        }
                    }
                    if (translatedHtml != null && !searchOpen) {
                        HorizontalDivider(color = readerFgColor(theme).copy(alpha = 0.12f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Ver:",
                                style = MaterialTheme.typography.labelMedium,
                                color = readerFgColor(theme).copy(alpha = 0.6f),
                            )
                            FilterChip(
                                selected = !translationOn,
                                onClick = { translationOn = false },
                                label = { Text("Original") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = readerFgColor(theme),
                                    selectedContainerColor = readerAccentColor(theme).copy(alpha = 0.25f),
                                    selectedLabelColor = readerFgColor(theme),
                                ),
                            )
                            FilterChip(
                                selected = translationOn,
                                onClick = { translationOn = true },
                                label = { Text("Traducción") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = readerFgColor(theme),
                                    selectedContainerColor = readerAccentColor(theme).copy(alpha = 0.25f),
                                    selectedLabelColor = readerFgColor(theme),
                                ),
                            )
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
                                    if (paged) {
                                        searchIndex = nextSearchIndex(searchIndex, searchMatches.size)
                                        goToSearchMatch(paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState)
                                    } else {
                                        findActive = nextSearchIndex(findActive, findCount)
                                        goToSearchMatch(paged, emptyList(), findActive, webView, searchQuery, scope, pagerState)
                                    }
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
                                onClick = {
                                    if (paged) {
                                        searchIndex = prevSearchIndex(searchIndex, searchMatches.size)
                                        goToSearchMatch(paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState)
                                    } else {
                                        findActive = prevSearchIndex(findActive, findCount)
                                        goToSearchMatch(paged, emptyList(), findActive, webView, searchQuery, scope, pagerState)
                                    }
                                },
                                enabled = if (paged) searchMatches.isNotEmpty() else findCount > 0,
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Anterior",
                                    tint = readerFgColor(theme),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (paged) {
                                        searchIndex = nextSearchIndex(searchIndex, searchMatches.size)
                                        goToSearchMatch(paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState)
                                    } else {
                                        findActive = nextSearchIndex(findActive, findCount)
                                        goToSearchMatch(paged, emptyList(), findActive, webView, searchQuery, scope, pagerState)
                                    }
                                },
                                enabled = if (paged) searchMatches.isNotEmpty() else findCount > 0,
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
                    // Offline indicator: the chapter on screen came from a
                    // local download or stale cache, not the live site.
                    if (!online) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = readerFgColor(theme).copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Sin conexión · copia local",
                                style = MaterialTheme.typography.labelSmall,
                                color = readerFgColor(theme).copy(alpha = 0.6f),
                            )
                        }
                    }
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
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
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
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        var draftFont by remember { mutableIntStateOf(fontSize) }
        var draftLineHeight by remember { mutableFloatStateOf(lineHeight) }
        var draftMargins by remember { mutableIntStateOf(margins) }
        var draftTtsRate by remember { mutableFloatStateOf(store.prefs.ttsRate) }
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
                            label = { Text(stringResource(option.labelRes)) },
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
                Spacer(Modifier.height(20.dp))
                Text("Lectura en voz alta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("0.5×", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = draftTtsRate,
                        onValueChange = { draftTtsRate = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("2×", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "%.1f×".format(draftTtsRate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        store.prefs.fontSizeSp = draftFont
                        store.prefs.lineHeight = draftLineHeight
                        store.prefs.margins = draftMargins
                        store.prefs.theme = theme
                        store.prefs.serif = serif
                        store.prefs.paged = paged
                        store.prefs.ttsRate = draftTtsRate
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
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
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

    // ---- Language picker sheet ----
    if (showLangPicker) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        ModalBottomSheet(
            onDismissRequest = { showLangPicker = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Traducir capítulo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    "Se traduce solo este capítulo y se guarda en el dispositivo: " +
                        "no se vuelve a traducir por segunda vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TranslationLang.entries.forEach { option ->
                    val selected = translationLang == option.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                translationLang = option.code
                                store.prefs.translationLang = option.code
                                store.savePrefs()
                                showLangPicker = false
                                translateChapter()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { saveProgressNow() }
    }
}

/** Target languages for the chapter translator (BCP-47 codes for gtx). */
private enum class TranslationLang(val code: String, val label: String) {
    ES("es", "Español"),
    EN("en", "English"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    PT("pt", "Português"),
    IT("it", "Italiano"),
    RU("ru", "Русский"),
    JA("ja", "日本語"),
    ZH("zh-CN", "中文"),
    KO("ko", "한국어"),
    AR("ar", "العربية"),
    HI("hi", "हिन्दी"),
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
    if (paged) {
        val m = matches.getOrNull(index) ?: return
        scope.launch { pagerState.scrollToPage(m.page) }
    } else {
        // Scroll mode: the JS keeps the match list (window.__find) and scrolls
        // to the [index]-th one; the current index is tracked via findActive.
        val wv = webView.value
        if (wv != null && query.isNotBlank() && index >= 0) {
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
    contentKey: String,
    prefsJson: String,
    onPageFinished: () -> Unit,
    onToggleChrome: () -> Unit,
    onFindResult: (Int, Int) -> Unit,
    onJsFindResult: (Int) -> Unit,
    onScrollRatio: (Float) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentHtml by rememberUpdatedState(htmlToLoad)
    val currentContentKey by rememberUpdatedState(contentKey)
    val currentPrefsJson by rememberUpdatedState(prefsJson)
    val currentOnFindResult by rememberUpdatedState(onFindResult)
    val currentOnJsFindResult by rememberUpdatedState(onJsFindResult)
    val currentOnScrollRatio by rememberUpdatedState(onScrollRatio)
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    // Tracks the exact content already loaded into the WebView, so the initial
    // composition (and any recomposition with the same content) never triggers
    // a redundant reload — the old code reloaded twice on first open. Keyed on
    // the CONTENT (chapter/translation), not the styling: theme/size changes
    // are applied live via applyReaderPrefs() without touching the document.
    var loadedContentKey by remember { mutableStateOf<String?>(null) }
    // Re-apply styling after every page load; a prefs change that raced with a
    // reload would otherwise be lost on the freshly-loaded document.
    fun applyPrefs(wv: WebView) {
        wv.evaluateJavascript("if (window.applyReaderPrefs) applyReaderPrefs($currentPrefsJson);", null)
    }
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

                        @JavascriptInterface
                        fun onScrollRatio(ratio: Double) {
                            Handler(Looper.getMainLooper()).post { currentOnScrollRatio(ratio.toFloat().coerceIn(0f, 1f)) }
                        }
                    },
                    "Reader",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        applyPrefs(this@apply)
                        currentOnPageFinished()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // The reader must never navigate away from the chapter;
                        // hand any real http(s) link to the system browser
                        // instead of silently swallowing it.
                        val url = request?.url?.toString()
                        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                            Handler(Looper.getMainLooper()).post { currentOnOpenLink(url) }
                        }
                        return true
                    }
                }
                webView.value = this
                loadedContentKey = currentContentKey
                loadDataWithBaseURL(BASE_URL, currentHtml, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            val current = webView.value
            if (current != wv) webView.value = wv
            wv.setBackgroundColor(backgroundColor)
            // Safety net: if the WebView somehow lost its content (url cleared),
            // restore it. Guarded by loadedContentKey so we never double-load.
            if (wv.url.isNullOrEmpty() && loadedContentKey != currentContentKey) {
                loadedContentKey = currentContentKey
                wv.loadDataWithBaseURL(BASE_URL, currentHtml, "text/html", "UTF-8", null)
            }
        },
        onRelease = {
            webView.value = null
            it.destroy()
        },
    )

    // Reload ONLY when the content actually changed (new chapter / translation).
    LaunchedEffect(contentKey) {
        val wv = webView.value ?: return@LaunchedEffect
        if (loadedContentKey != contentKey) {
            loadedContentKey = contentKey
            wv.loadDataWithBaseURL(BASE_URL, htmlToLoad, "text/html", "UTF-8", null)
        }
    }

    // Theme/size/margins changes restyle the loaded document via CSS variables
    // (applyReaderPrefs) — no reload, no re-layout of the whole chapter.
    LaunchedEffect(prefsJson) {
        val wv = webView.value ?: return@LaunchedEffect
        applyPrefs(wv)
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

/**
 * WebView.evaluateJavascript returns the JS result JSON-encoded, so a String
 * comes back quoted ("0.4321"). The old `r?.toFloatOrNull()` always failed on
 * the quotes and progress was saved as 0 (broken reader restore + 0% bars).
 */
internal fun parseJsRatio(r: String?): Float =
    r?.trim('"')?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f

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

/**
 * Reader stylesheet. All themeable values live as CSS custom properties on
 * :root, so [applyReaderPrefs] can restyle an already-loaded chapter via JS
 * WITHOUT reloading the WebView (a full reload re-lays-out the whole document,
 * which is the dominant cost when changing theme/size on low-end devices).
 */
private fun readerCss(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean, lineHeight: Float, margins: Int): String {
    val (bg, fg, accent) = readerPalette(theme)
    val subtle = if (theme == Store.ReaderTheme.SEPIA) "#7a6a50" else "#9a9a9a"
    val divider = if (theme == Store.ReaderTheme.LIGHT) "#e0e0e0" else "#3a3a3f"
    val family = if (serif) "Georgia, 'Times New Roman', serif" else "Roboto, 'Helvetica Neue', sans-serif"
    val colorScheme = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "light" else "dark"
    val (padMobile, padTablet) = marginPadding(margins)
    return """
        :root {
            color-scheme: $colorScheme;
            --bg: $bg;
            --fg: $fg;
            --accent: $accent;
            --accent-soft: ${accent}66;
            --subtle: $subtle;
            --divider: $divider;
            --font-family: $family;
            --font-size: ${sizeSp}px;
            --line-height: $lineHeight;
            --pad-mobile: $padMobile;
            --pad-tablet: $padTablet;
        }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body {
            background: var(--bg);
            color: var(--fg);
            font-family: var(--font-family);
            font-size: var(--font-size);
            line-height: var(--line-height);
            overflow-wrap: break-word;
            padding: var(--pad-mobile);
        }
        .meta { text-align: center; margin-bottom: 26px; }
        .meta .work { font-size: 0.8em; color: var(--subtle); letter-spacing: 0.02em; }
        .meta .chap { font-size: 1.15em; font-weight: 600; margin-top: 4px; }
        .content h1, .content h2, .content h3 { line-height: 1.35; }
        .content p { margin: 0 0 1.1em; }
        .content blockquote {
            border-left: 3px solid var(--accent);
            margin: 1.1em 0;
            padding: 0.2em 1em;
            color: var(--subtle);
        }
        .content hr { border: none; border-top: 1px solid var(--divider); margin: 1.8em 0; }
        .content em, .content i { font-style: italic; }
        .content strong, .content b { font-weight: 700; }
        .content a { color: var(--accent); text-decoration: none; }
        .content a:hover { text-decoration: underline; }
        .content img { max-width: 100%; height: auto; border-radius: 6px; }
        mark.ao3findmark { background: var(--accent); color: #ffffff; border-radius: 2px; padding: 0 1px; }
        ::highlight(ao3find) { background-color: var(--accent-soft); }
        .content center { display: block; text-align: center; }
        .content .userstuff, .content div { line-height: inherit; }
        blockquote.notes { margin-left: 0; }
        pre { white-space: pre-wrap; }
        @media (min-width: 720px) { body { padding: var(--pad-tablet); } }
    """.trimIndent()
}

/**
 * Serializes the current reader preferences as a JSON object consumed by the
 * applyReaderPrefs() JS helper, letting the WebView restyle live without a
 * reload. Mirrors the defaults baked into [readerCss].
 */
private fun readerPrefsJson(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean, lineHeight: Float, margins: Int): String {
    val (bg, fg, accent) = readerPalette(theme)
    val subtle = if (theme == Store.ReaderTheme.SEPIA) "#7a6a50" else "#9a9a9a"
    val divider = if (theme == Store.ReaderTheme.LIGHT) "#e0e0e0" else "#3a3a3f"
    val family = if (serif) "Georgia, 'Times New Roman', serif" else "Roboto, 'Helvetica Neue', sans-serif"
    val colorScheme = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "light" else "dark"
    val (padMobile, padTablet) = marginPadding(margins)
    return "{\"bg\":\"$bg\",\"fg\":\"$fg\",\"accent\":\"$accent\",\"accentSoft\":\"${accent}66\",\"subtle\":\"$subtle\",\"divider\":\"$divider\",\"fontFamily\":\"$family\",\"fontSize\":$sizeSp,\"lineHeight\":$lineHeight,\"padMobile\":\"$padMobile\",\"padTablet\":\"$padTablet\",\"colorScheme\":\"$colorScheme\"}"
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
        // Reports the reading position (0..1) on every scroll so progress can be
        // saved reliably WITHOUT an async evaluateJavascript round-trip at exit
        // (which raced chapter changes and lost the last chapter/percentage).
        (function(){
          var last = -1;
          function report(){
            var h = document.documentElement.scrollHeight || document.body.scrollHeight;
            var vh = window.innerHeight;
            var y = window.scrollY || 0;
            var r = (h > vh) ? y / (h - vh) : 0;
            if (r < 0) r = 0; if (r > 1) r = 1;
            if (Math.abs(r - last) > 0.001) {
              last = r;
              if (window.Reader && window.Reader.onScrollRatio) window.Reader.onScrollRatio(r);
            }
          }
          document.addEventListener('scroll', report, true);
          window.addEventListener('resize', report, true);
          // Report the initial position once the document is laid out.
          if (document.readyState === 'complete') report();
          else document.addEventListener('DOMContentLoaded', report);
        })();
        function applyReaderPrefs(p){
          var s = document.documentElement.style;
          s.setProperty('--bg', p.bg);
          s.setProperty('--fg', p.fg);
          s.setProperty('--accent', p.accent);
          s.setProperty('--accent-soft', p.accentSoft);
          s.setProperty('--subtle', p.subtle);
          s.setProperty('--divider', p.divider);
          s.setProperty('--font-family', p.fontFamily);
          s.setProperty('--font-size', p.fontSize + 'px');
          s.setProperty('--line-height', p.lineHeight);
          s.setProperty('--pad-mobile', p.padMobile);
          s.setProperty('--pad-tablet', p.padTablet);
          s.setProperty('color-scheme', p.colorScheme);
        }
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
