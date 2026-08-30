package net.spin.ao3.ui.reader


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
import net.spin.ao3.util.sliceSegments
internal const val BASE_URL = "https://archiveofourown.org"

// WebView del modo scroll: carga el HTML del capítulo, aplica prefs en vivo
// por JS y reporta ratio de scroll/resultados de búsqueda.

// ---- WebView host -----------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ReaderWebViewHost(
    webView: MutableState<WebView?>,
    modifier: Modifier = Modifier,
    backgroundColor: Int,
    htmlToLoad: String,
    contentKey: String,
    prefsJson: String,
    onPageFinished: () -> Unit,
    onToggleChrome: () -> Unit,
    onFindResult: (Int, Int) -> Unit,
    ttsWord: String,
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
    val currentTtsWord by rememberUpdatedState(ttsWord)
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
                        // Only signal finished when the document that just loaded
                        // IS the current content. The reader loads a placeholder
                        // and (before the chapter arrives) an intermediate
                        // title-only page; their onPageFinished must NOT consume
                        // the pending scroll restore, or the user lands at 0%
                        // instead of the saved position.
                        if (loadedContentKey == currentContentKey) currentOnPageFinished()
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

    LaunchedEffect(ttsWord) {
        val wv = webView.value ?: return@LaunchedEffect
        wv.evaluateJavascript(jsHighlightTtsWord(currentTtsWord), null)
    }
}

