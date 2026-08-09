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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.data.model.ChapterInfo
import net.spin.ao3.util.escapeHtml

private const val BASE_URL = "https://archiveofourown.org"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    workId: Long,
    initialChapter: Int,
    onBack: () -> Unit,
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

    var retryTick by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    // Tap on the text toggles the bars + full screen; scrolls and links are left alone.
    var chromeVisible by remember { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(false) }
    val toggleChrome: () -> Unit = { chromeVisible = !chromeVisible }

    // Full screen: hide the system bars together with the app chrome.
    LaunchedEffect(chromeVisible) {
        val window = (context as? Activity)?.window
        val view = window?.decorView
        if (window != null && view != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (chromeVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
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

    fun currentHistory(): Store.HistoryEntry? =
        store.history().firstOrNull { it.id == workId }

    // 1) Load work metadata + chapter list (skip when already loaded so a
    //    chapter retry does not re-hit the network for metadata)
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
        pendingScroll = if (hist != null && hist.chapterIndex == currentIndex) hist.scrollRatio else 0f
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
    }

    fun saveProgressNow() {
        val wv = webView.value ?: return
        val t = workTitle
        if (t.isEmpty()) return
        val a = workAuthor
        val idx = currentIndex
        readScrollRatio(wv) { ratio ->
            store.updateHistory(
                Store.HistoryEntry(id = workId, title = t, author = a, chapterIndex = idx, scrollRatio = ratio, at = System.currentTimeMillis()),
            )
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
        currentIndex = index
    }

    BackHandler { saveProgressNow(); onBack() }

    // 3) Render chapter into the WebView whenever content/css changes
    val css = remember(theme, fontSize, serif) { readerCss(theme, fontSize, serif) }
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

    Box(Modifier.fillMaxSize()) {
        // WebView (background color avoids white flash)
        ReaderWebViewHost(
            webView = webView,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = bgColor,
            onPageFinished = { onPageFinished.value() },
            onToggleChrome = toggleChrome,
            htmlToLoad = html,
        )

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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

        // Top bar (hidden with a tap on the text)
        if (chromeVisible) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.97f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            workTitle.ifBlank { "Cargando…" },
                            color = readerFgColor(theme),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (chapters.isNotEmpty()) "Capítulo ${currentIndex + 1} de ${chapters.size}" else " ",
                            color = readerFgColor(theme).copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = readerFgColor(theme))
                    }
                }
            }
        }

        // Bottom bar (chapter indicator + navigation)
        if (chromeVisible) {
            Surface(
                color = readerBgColor(theme).copy(alpha = 0.97f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
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
                        "${currentIndex + 1} / ${chapters.size.coerceAtLeast(1)}",
                        color = readerFgColor(theme),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showChapters = true }
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

        // Hint when the chrome is hidden
        if (!chromeVisible && showHint) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
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
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
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
                Text("Tamaño de letra", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                var draftSize by remember { mutableIntStateOf(fontSize) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = draftSize.toFloat(),
                        onValueChange = { draftSize = it.toInt() },
                        valueRange = 13f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", fontSize = 22.sp)
                }
                Text(
                    "${draftSize} sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
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
                        store.prefs.fontSizeSp = draftSize
                        store.prefs.theme = theme
                        store.prefs.serif = serif
                        store.savePrefs()
                        fontSize = draftSize
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showChapters = false
                                goToChapter(index)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            chapter.title.ifBlank { "Capítulo ${index + 1}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (downloadedIds.contains(index)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Descargado",
                                tint = Color(0xFF2E7D32),
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
) {
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentHtml by rememberUpdatedState(htmlToLoad)
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
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun toggleChrome() {
                            Handler(Looper.getMainLooper()).post { currentToggleChrome() }
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
    Store.ReaderTheme.LIGHT -> Triple("#ffffff", "#1c1c1e", "#8a5a00")
    Store.ReaderTheme.SEPIA -> Triple("#f6edd9", "#433422", "#8a5a00")
    Store.ReaderTheme.DARK -> Triple("#1b1b1f", "#d6d3d0", "#e0b06a")
    Store.ReaderTheme.BLACK -> Triple("#000000", "#a8a8a8", "#b98a4a")
}

private fun readerBgColor(theme: Store.ReaderTheme): Color = Color(android.graphics.Color.parseColor(readerPalette(theme).first))

private fun readerFgColor(theme: Store.ReaderTheme): Color = Color(android.graphics.Color.parseColor(readerPalette(theme).second))

private fun readerCss(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean): String {
    val (bg, fg, accent) = readerPalette(theme)
    val subtle = if (theme == Store.ReaderTheme.SEPIA) "#7a6a50" else "#9a9a9a"
    val divider = if (theme == Store.ReaderTheme.LIGHT) "#e0e0e0" else "#3a3a3f"
    val family = if (serif) "Georgia, 'Times New Roman', serif" else "Roboto, 'Helvetica Neue', sans-serif"
    val colorScheme = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "light" else "dark"
    return """
        :root { color-scheme: $colorScheme; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; }
        body {
            background: $bg;
            color: $fg;
            font-family: $family;
            font-size: ${sizeSp}px;
            line-height: 1.75;
            overflow-wrap: break-word;
            padding: 18px 22px 60px;
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
        .content center { display: block; text-align: center; }
        .content .userstuff, .content div { line-height: inherit; }
        blockquote.notes { margin-left: 0; }
        pre { white-space: pre-wrap; }
        @media (min-width: 720px) { body { padding: 28px 48px 70px; } }
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
          var sx = 0, sy = 0, moved = false;
          document.addEventListener('touchstart', function(e){
            var t = e.touches[0];
            if (t) { sx = t.clientX; sy = t.clientY; }
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
