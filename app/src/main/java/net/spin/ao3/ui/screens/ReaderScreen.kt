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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.util.Log
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
import androidx.compose.ui.graphics.lerp
import net.spin.ao3.ui.components.AdaptiveSheet
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
import net.spin.ao3.ui.reader.ReaderProgressBar
import net.spin.ao3.ui.reader.ReaderProgressLabel
import net.spin.ao3.ui.reader.goToSearchMatch
import net.spin.ao3.ui.reader.nextSearchIndex
import net.spin.ao3.ui.reader.prevSearchIndex
import net.spin.ao3.ui.reader.parseJsRatio
import net.spin.ao3.ui.reader.restoreScroll
import net.spin.ao3.ui.reader.jsFindInPage
import net.spin.ao3.ui.reader.jsGoToMatch
import net.spin.ao3.ui.reader.jsClearFind
import net.spin.ao3.ui.reader.jsHighlightTtsWord
import net.spin.ao3.ui.reader.PagedReaderBody
import net.spin.ao3.ui.reader.packPagesToViewport
import net.spin.ao3.ui.reader.ReaderWebViewHost
import net.spin.ao3.ui.reader.readerBgColor
import net.spin.ao3.ui.reader.readerFgColor
import net.spin.ao3.ui.reader.readerAccentColor
import net.spin.ao3.ui.reader.readerCss
import net.spin.ao3.ui.reader.readerPrefsJson
import net.spin.ao3.ui.reader.buildChapterHtml
import net.spin.ao3.ui.reader.ReaderPreview
import net.spin.ao3.util.sliceSegments

private const val BASE_URL = "https://archiveofourown.org"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // Deliberately NOT rememberSaveable: nav slots are keyed by serialized
    // route, so a saveable index would resurrect the last-read chapter when
    // the same Route.Reader(workId, chapter) is pushed again (the explicit
    // chapter tap would be ignored). Cost: rotating mid-read snaps back to the
    // entry chapter — acceptable until slots get per-push identity.
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

    // Chapter translation (Google web endpoint or an OpenAI-compatible API,
    // selectable in the picker below and configurable in Ajustes → Traducción).
    var translationLang by remember { mutableStateOf(store.prefs.translationLang) }
    var translationProvider by remember { mutableStateOf(store.prefs.translationProvider) }
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
    var ttsWord by remember { mutableStateOf("") }
    // The done-handler is set below (after goToChapter is defined — local
    // functions aren't forward-referenced), so the TTS engine can stay
    // independent of the navigation code.
    val currentTtsDone = remember { mutableStateOf<() -> Unit>({}) }
    val tts = rememberReaderTts(
        onUtteranceDone = { currentTtsDone.value() },
        onWordRange = { word -> ttsWord = word },
    )
    fun startTts() {
        resumeTtsOnChapterLoad = false
        tts.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            return
        }
        // Read the visible version. Previously this always used [content], so
        // TTS kept speaking the original after the user enabled translation.
        val readableHtml = if (translationOn) translatedHtml ?: content else content
        scope.launch {
            val text = withContext(Dispatchers.Default) {
                Ao3Parser.htmlToPlainText(readableHtml ?: return@withContext "")
            }
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
    // Chrome is a pure OVERLAY: showing/hiding the bars never resizes or
    // re-packs the reading area (no reload feel, no page drift). They may
    // cover the first/last lines while visible — hiding them is one tap away.
    val density = LocalDensity.current
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
        // Reads currentIndex: after any recreation it points at the chapter the
        // reader is about to render, so the restored ratio belongs to the same
        // chapter being rendered.
        mutableFloatStateOf(
            store.history().firstOrNull { it.id == workId && it.chapterIndex == currentIndex }
                ?.scrollRatio ?: 0f,
        )
    }
    // Chapter the pending ratio belongs to. Without the label, a slow chapter
    // change let an intermediate render consume the restore target of the
    // chapter the user was LEAVING (documented v0.7.23: the position landed on
    // page 0 after slow network jumps).
    var pendingScrollChapter by remember(workId) { mutableIntStateOf(currentIndex) }
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
        } catch (e: Exception) {
            // A malformed chapter must not leave the reader stuck on a blank
            // page forever: fall back to an empty pager (no crash, no hang).
            lines = emptyList()
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

    // 1) Load the complete work metadata, then overlay downloaded chapter
    // bodies. A download is a cache of content, not a replacement for the
    // work's chapter index: opening chapter 4 after downloading 1–3 must still
    // show 4/30 and fetch it online when available.
    LaunchedEffect(workId, retryTick) {
        if (chapters.isNotEmpty()) return@LaunchedEffect
        val downloaded = store.download(workId)
        try {
            val detail = container.client.getWork(workId)
            workTitle = detail.summary.title
            workAuthor = detail.summary.author
            val localByIndex = downloaded?.chapters.orEmpty().associateBy { it.index }
            chapters = detail.chapters.map { remote ->
                localByIndex[remote.index]?.let { local -> remote.copy(content = local.content) } ?: remote
            }
        } catch (e: Exception) {
            if (downloaded != null) {
                // Offline fallback can expose only the chapters that really
                // exist locally; online navigation above always restores the
                // complete chapter list.
                workTitle = downloaded.title
                chapters = downloaded.chapters.sortedBy { it.index }
            } else {
                error = if (!online) {
                    "Sin conexión. Esta obra no está descargada."
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
                    "Sin conexión. Este capítulo no está descargado."
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

    // 2b) Paged mode: position across re-packs, EXACTLY.
    // The chapter is a flat list of VISUAL ROWS; each page starts at a global
    // row index ([rowStarts]). Toggling the controls (or any viewport change
    // that keeps the WIDTH) slices identical rows — only the page GROUPING
    // changes — so restoring by global row index is pixel-exact: no ±1 page
    // drift like the old ratio rounding (6/28 → 5/23 → 6/28 on every toggle).
    var lastChapterKey by remember { mutableIntStateOf(-1) }
    var lastContentKey by remember { mutableStateOf<String?>(null) }
    var rowStarts by remember { mutableStateOf<List<Int>>(emptyList()) }

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
        // Restore the target chapter's own saved position (chapter navigation
        // must not reset the user to the top of the next chapter).
        val saved = currentHistory()?.chapterProgress?.get(index) ?: 0f
        pendingScroll = saved
        pendingScrollChapter = index
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
    // Identity of the loaded content WITHOUT building a chapter-sized string
    // on every recomposition (scroll updates used to re-compose this scope per
    // frame, string-building the whole chapter each time). The hash only
    // recomputes when the content itself changes (chapter/translation).
    val contentId = remember(displayContent) { displayContent?.hashCode() ?: 0 }
    val contentKey = "$workId|$currentIndex|$chapterTitle|$translationOn|$contentId"
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
        if (pendingScroll > 0f && pendingScrollChapter == currentIndex && content != null) {
            val ratio = pendingScroll
            pendingScroll = 0f
            // Layout may still be settling right after onPageFinished; a short
            // delay makes the restore land on the true scrollHeight instead of
            // a half-measured one (which clamped the position to 0).
            Handler(Looper.getMainLooper()).postDelayed({ restoreScroll(wv, ratio) }, 80L)
        }
    }

    val bgColor = readerBgColor(theme).toArgb()

    fun applyPrefs(block: () -> Unit) {
        if (paged) {
            pendingScroll = if (measuredPages.size > 1) pagerState.currentPage / (measuredPages.size - 1).toFloat() else 0f
            pendingScrollChapter = currentIndex
            block()
        } else {
            val wv = webView.value
            if (wv != null && content != null) {
                // Capture the current ratio for the post-change restore, but
                // apply the change IMMEDIATELY: the paged switch must not wait
                // on an async JS callback (it can be delayed or lost, leaving
                // the reader stuck in scroll mode).
                wv.evaluateJavascript(
                    "(function(){var h=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;var y=window.scrollY||0;return String((h>vh)?y/(h-vh):0);})()",
                ) { r ->
                    pendingScroll = parseJsRatio(r)
                    pendingScrollChapter = currentIndex
                }
            }
            block()
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
    // Debounced: re-searching on every keystroke raced the async JS and left
    // the DOM half-marked, which made multi-letter queries appear to break.
    LaunchedEffect(searchQuery, paged, displayContent) {
        if (searchQuery.isBlank()) {
            searchMatches = emptyList()
            searchIndex = -1
            if (!paged) webView.value?.evaluateJavascript(jsClearFind(), null)
            return@LaunchedEffect
        }
        delay(150)
        if (paged) {
            searchMatches = findInPages(measuredPages, searchQuery)
            searchIndex = if (searchMatches.isEmpty()) -1 else 0
            if (searchMatches.isNotEmpty()) goToSearchMatch(
                paged, searchMatches, searchIndex, webView, searchQuery, scope, pagerState,
            )
        } else {
            val wv = webView.value
            if (wv != null && content != null) {
                // Single atomic call: clearing + finding in one evaluateJavascript
                // is critical. Two separate calls race (both are async), and an
                // interleaved clear could wipe the highlights mid-search.
                wv.evaluateJavascript(jsFindInPage(searchQuery), null)
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
        // Body: paginated pages (viewport-packed) or the scroll WebView. The
        // measured chrome insets are part of the layout, not an approximation
        // inside the HTML/pager, so both modes use the same readable viewport.
        Box(
            Modifier
                .fillMaxSize()
                // Todo el lienzo (incluidas las franjas del notch) del color
                // del tema del lector: sin costuras grises.
                .background(readerBgColor(theme))
                // El notch/punch-hole (display cutout) nunca pisa palabras: en
                // inmersivo el lienzo se extiende por detrás de él.
                .windowInsetsPadding(WindowInsets.displayCutout)

        ) {
        if (!paged) {
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
                ttsWord = ttsWord,
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
        } else if (lines == null) {
            // Paginating off the main thread — the loading overlay below covers this.
            Box(Modifier.fillMaxSize())
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val pageLines = lines!!
                // Packing runs OFF the main thread: the binary search measures
                // with TextMeasurer and on a long chapter used to freeze the UI
                // for seconds-to-minutes, so activating paged mode looked like
                // "nothing happens". While packing, the loading overlay covers
                // the reader. First background, fallback to main thread if the
                // measurer refuses to run off it (correct result either way).
                LaunchedEffect(pageLines, maxWidth, maxHeight, fontSize, lineHeight, serif, margins, measurer) {
                    // Re-pack SOLO de viewport (misma obra+contenido): mantiene
                    // las páginas actuales visibles y sin overlay; el swap es
                    // silencioso al terminar.
                    val viewportOnly = lastChapterKey == currentIndex &&
                        lastContentKey == displayContent && measuredPages.isNotEmpty()
                    // Global index of the CURRENT page's first row. Same width
                    // ⇒ identical rows ⇒ this index survives any re-pack exactly.
                    val savedRow = if (viewportOnly) rowStarts.getOrNull(pagerState.currentPage) ?: 0 else 0
                    if (!viewportOnly) paginating = true
                    val t0 = System.currentTimeMillis()
                    var packed = try {
                        withContext(Dispatchers.Default) {
                            packPagesToViewport(pageLines, measurer, density, maxWidth, maxHeight, fontSize, lineHeight, serif, margins)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ReaderPaged", "pack en background falló, reintenta en main", e)
                        null
                    }
                    if (packed == null) {
                        packed = try {
                            packPagesToViewport(pageLines, measurer, density, maxWidth, maxHeight, fontSize, lineHeight, serif, margins)
                        } catch (e: Exception) {
                            Log.w("ReaderPaged", "pack falló", e)
                            emptyList()
                        }
                    }
                    Log.d("ReaderPaged", "pack: ${packed.size} páginas en ${System.currentTimeMillis() - t0} ms")
                    val starts = buildList {
                        var n = 0
                        for (pg in packed) {
                            add(n)
                            n += pg.size
                        }
                    }
                    measuredPages = packed
                    rowStarts = starts
                    lastChapterKey = currentIndex
                    lastContentKey = displayContent
                    if (viewportOnly) {
                        // EXACT: the page that now contains the same global row.
                        val target = starts.indexOfLast { it <= savedRow }.coerceAtLeast(0)
                        pagerState.scrollToPage(target)
                        // La restauración por fila sustituye a la basada en
                        // ratio que applyPrefs dejó pendiente.
                        pendingScroll = 0f
                    } else if (pendingScroll > 0f && pendingScrollChapter == currentIndex && packed.size > 1) {
                        pagerState.scrollToPage(
                            (pendingScroll * (packed.size - 1)).roundToInt().coerceIn(0, packed.size - 1),
                        )
                        pendingScroll = 0f
                    } else {
                        pagerState.scrollToPage(0)
                    }
                    paginating = false
                }
                PagedReaderBody(
                    pages = measuredPages,
                    pagerState = pagerState,
                    highlights = highlights,
                    ttsWord = ttsWord,
                    theme = theme,
                    fontSize = fontSize,
                    serif = serif,
                    lineHeight = lineHeight,
                    margins = margins,
                    toggleChrome = toggleChrome,
                )
            }
        }
        }

        // Loading overlay (chapter fetch OR off-main pagination)
        if (loading || paginating) {
            Surface(
                color = readerBgColor(theme),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
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
                color = readerBgColor(theme),
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
                                trackColor = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.15f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$done / $total",
                                color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.7f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            LoadingIndicator(color = readerAccentColor(theme))
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
                color = readerBgColor(theme),
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
                        color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.55f),
                    )
                }
            }
        }

        // Top chrome: title bar + (optional) search bar. Hidden with a tap on the text.
        if (chromeVisible) {
            Surface(
                color = readerBgColor(theme),
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
                                    color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Abrir ficha de la obra",
                                    tint = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.45f),
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
                        // Overflow menu keeps the reading surface focused: only
                        // search stays visible; secondary actions live here.
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = readerFgColor(theme),
                                )
                            }
                            DropdownMenuPopup(
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
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                tts.speaking -> "Pausar lectura en voz alta"
                                                tts.paused -> "Reanudar lectura en voz alta"
                                                else -> "Leer en voz alta"
                                            },
                                            color = if (tts.speaking || tts.paused) readerAccentColor(theme) else readerFgColor(theme),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            when {
                                                tts.speaking -> Icons.Default.Pause
                                                tts.paused -> Icons.Default.PlayArrow
                                                else -> Icons.Default.VolumeUp
                                            },
                                            contentDescription = null,
                                            tint = if (tts.speaking || tts.paused) readerAccentColor(theme) else readerFgColor(theme),
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        when {
                                            tts.speaking -> tts.pause()
                                            tts.paused -> tts.resume()
                                            else -> startTts()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ajustes del lector", color = readerFgColor(theme)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = readerFgColor(theme))
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showSettings = true
                                    },
                                )
                            }
                        }
                    }
                    if (translatedHtml != null && !searchOpen) {
                        HorizontalDivider(color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.12f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Ver:",
                                style = MaterialTheme.typography.labelMedium,
                                color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.6f),
                            )
                            FilterChip(
                                selected = !translationOn,
                                onClick = { translationOn = false },
                                label = { Text("Original") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = readerFgColor(theme),
                                    selectedContainerColor = androidx.compose.ui.graphics.lerp(readerBgColor(theme), readerAccentColor(theme), 0.32f),
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
                                    selectedContainerColor = androidx.compose.ui.graphics.lerp(readerBgColor(theme), readerAccentColor(theme), 0.32f),
                                    selectedLabelColor = readerFgColor(theme),
                                ),
                            )
                        }
                    }
                    if (searchOpen) {
                        HorizontalDivider(color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.12f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Buscar en el capítulo…", color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.45f))
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = readerFgColor(theme), fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = readerFgColor(theme),
                                    unfocusedTextColor = readerFgColor(theme),
                                    cursorColor = readerFgColor(theme),
                                    focusedBorderColor = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.4f),
                                    unfocusedBorderColor = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.25f),
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
                                color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.7f),
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
                color = readerBgColor(theme),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column {
                    if (tts.speaking || tts.paused) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = readerAccentColor(theme),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (tts.paused) "Lectura en voz alta en pausa" else "Leyendo en voz alta",
                                style = MaterialTheme.typography.labelSmall,
                                color = readerFgColor(theme),
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    if (tts.speaking) tts.pause() else tts.resume()
                                }) {
                                    Text(if (tts.speaking) "Pausar" else "Reanudar", color = readerAccentColor(theme))
                                }
                                TextButton(onClick = { tts.stop() }) {
                                    Text("Detener", color = readerAccentColor(theme))
                                }
                            }
                        }
                    }
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
                                tint = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.6f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Sin conexión · copia local",
                                style = MaterialTheme.typography.labelSmall,
                                color = readerMuted(readerBgColor(theme), readerFgColor(theme), 0.6f),
                            )
                        }
                    }
                    // Both composables read the ratio/page INSIDE their own
                    // scope: a scroll-driven update (one JS event per frame)
                    // recomposes only the bar + label, not the whole reader.
                    ReaderProgressBar(
                        ratio = { currentRatio },
                        paged = paged,
                        pageCount = measuredPages.size,
                        currentPage = pagerState.currentPage,
                        color = readerAccentColor(theme),
                        trackColor = readerFgColor(theme),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { goToChapter(currentIndex - 1) }, enabled = currentIndex > 0) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Anterior",
                                tint = if (currentIndex > 0) readerFgColor(theme) else readerMuted(readerBgColor(theme), readerFgColor(theme), 0.3f),
                            )
                        }
                        ReaderProgressLabel(
                            chapter = currentIndex + 1,
                            chapters = chapters.size.coerceAtLeast(1),
                            ratio = { currentRatio },
                            paged = paged,
                            pageCount = measuredPages.size,
                            currentPage = pagerState.currentPage,
                            color = readerFgColor(theme),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showChapters = true }
                                .padding(vertical = 8.dp),
                        )
                        IconButton(
                            onClick = { goToChapter(currentIndex + 1) },
                            enabled = currentIndex < chapters.size - 1,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Siguiente",
                                tint = if (currentIndex < chapters.size - 1) readerFgColor(theme) else readerMuted(readerBgColor(theme), readerFgColor(theme), 0.3f),
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
        var draftTheme by remember { mutableStateOf(theme) }
        var draftSerif by remember { mutableStateOf(serif) }
        var draftPaged by remember { mutableStateOf(paged) }
        var draftTtsRate by remember { mutableFloatStateOf(store.prefs.ttsRate) }
        AdaptiveSheet(onDismissRequest = { showSettings = false }) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("Modo de lectura", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // MODE SWITCHES APPLY IMMEDIATELY: this sheet is long and
                    // "Aplicar" sits below the fold, so a draft-only toggle made
                    // tapping "Paginado" look completely dead (the #1 reported
                    // bug — the engine was fine all along). The rest of the
                    // drafts (font/theme…) still wait for Aplicar/Cancelar; a
                    // later Cancelar does not undo a mode already switched.
                    FilterChip(
                        selected = !draftPaged,
                        onClick = {
                            draftPaged = false
                            if (paged) {
                                applyPrefs { paged = false }
                                store.prefs.paged = false
                                store.savePrefs()
                            }
                        },
                        label = { Text("Scroll continuo") },
                    )
                    FilterChip(
                        selected = draftPaged,
                        onClick = {
                            draftPaged = true
                            if (!paged) {
                                applyPrefs { paged = true }
                                store.prefs.paged = true
                                store.savePrefs()
                            }
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
                            selected = draftTheme == option,
                            onClick = { draftTheme = option },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Vista previa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ReaderPreview(
                    theme = draftTheme,
                    fontSize = draftFont,
                    lineHeight = draftLineHeight,
                    serif = draftSerif,
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
                    FilterChip(selected = draftSerif, onClick = { draftSerif = true }, label = { Text("Serif") })
                    FilterChip(selected = !draftSerif, onClick = { draftSerif = false }, label = { Text("Sans serif") })
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { showSettings = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            applyPrefs {
                                theme = draftTheme
                                serif = draftSerif
                                paged = draftPaged
                            }
                            store.prefs.fontSizeSp = draftFont
                            store.prefs.lineHeight = draftLineHeight
                            store.prefs.margins = draftMargins
                            store.prefs.theme = draftTheme
                            store.prefs.serif = draftSerif
                            store.prefs.paged = draftPaged
                            store.prefs.ttsRate = draftTtsRate
                            store.savePrefs()
                            fontSize = draftFont
                            lineHeight = draftLineHeight
                            margins = draftMargins
                            showSettings = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Aplicar")
                    }
                }
            }
        }
    }

    // ---- Chapters sheet ----
    if (showChapters) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val downloadedIds = remember(workId) { store.downloadedChapterIds(workId) }
        AdaptiveSheet(onDismissRequest = { showChapters = false }) {
            Text(
                "Capítulos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(chapters, key = { _, ch -> ch.index }) { index, chapter ->
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
        AdaptiveSheet(onDismissRequest = { showLangPicker = false }) {
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
                Spacer(Modifier.height(12.dp))
                // Provider: Google (web endpoint) or any OpenAI-compatible API.
                Text("Proveedor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = translationProvider != "ai",
                        onClick = {
                            translationProvider = "google"
                            store.prefs.translationProvider = "google"
                            store.savePrefs()
                        },
                        label = { Text("Google") },
                    )
                    FilterChip(
                        selected = translationProvider == "ai",
                        onClick = {
                            translationProvider = "ai"
                            store.prefs.translationProvider = "ai"
                            store.savePrefs()
                        },
                        label = { Text("IA (OpenAI)") },
                    )
                }
                if (translationProvider == "ai" &&
                    (store.prefs.aiBaseUrl.isBlank() || store.prefs.aiApiKey.isBlank() || store.prefs.aiModel.isBlank())
                ) {
                    Text(
                        "Configura la API en Ajustes → Traducción.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Idioma", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
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


/** Color sólido del lector derivado por mezcla opaca (sin transparencias). */
private fun readerMuted(bg: Color, fg: Color, factor: Float): Color = lerp(bg, fg, factor)
