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

// Cuerpo del lector paginado: pager + zonas de toque + indicadores de progreso.

// ---- Reader bottom-bar progress (isolated so scroll updates don't recompose the screen)

@Composable
internal fun ReaderProgressBar(
    ratio: () -> Float,
    paged: Boolean,
    pageCount: Int,
    currentPage: Int,
    color: Color,
    trackColor: Color,
) {
    val visible = if (paged && pageCount > 1) currentPage / (pageCount - 1).toFloat() else ratio()
    LinearProgressIndicator(
        progress = { visible.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = color,
        trackColor = trackColor,
    )
}

@Composable
internal fun ReaderProgressLabel(
    chapter: Int,
    chapters: Int,
    ratio: () -> Float,
    paged: Boolean,
    pageCount: Int,
    currentPage: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val visible = if (paged && pageCount > 1) currentPage / (pageCount - 1).toFloat() else ratio()
    Text(
        "Cap. $chapter/$chapters · ${(visible * 100).roundToInt()} %" +
            if (paged && pageCount > 1) " · Pág. ${currentPage + 1}/$pageCount" else "",
        color = color,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier,
        textAlign = TextAlign.Center,
    )
}


// ---- Paginated reader body --------------------------------------------------

@Composable
internal fun PagedReaderBody(
    pages: List<List<Line>>,
    pagerState: PagerState,
    highlights: List<Pair<IntRange, Float>>,
    ttsWord: String,
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
    // Degenerate pack (empty chapter, zero-height viewport): the pager would
    // coerce its count to 1 and index pages[0] on an empty list (crash). Render
    // nothing instead; the reader chrome stays usable.
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize())
        return
    }
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
                        // Tap zones 25/50/25: ONLY the side quarters turn the
                        // page; the wide middle band toggles the chrome bars.
                        // (Thirds made "middle" taps land on a page flip.)
                        val w = size.width
                        if (offset.x < w / 4f) {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            } else {
                                toggleChrome()
                            }
                        } else if (offset.x > w * 3f / 4f) {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                toggleChrome()
                            }
                        } else {
                            toggleChrome()
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
                    text = linesToAnnotated(pages[page], fontSize, fg, accent, readerBgColor(theme), highlights, ttsWord),
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
                    color = readerMuted(readerBgColor(theme), fg, 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

