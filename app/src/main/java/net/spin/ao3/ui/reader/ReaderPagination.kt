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
import androidx.compose.ui.graphics.lerp
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

// Pipeline de render del modo paginado: bloques → filas visuales → páginas
// que llenan el viewport (ver docs/READER_SCREEN.md §3).

/**
 * Splits a block into its VISUAL ROWS measured at the page width, so pages
 * can end mid-paragraph and fill the viewport edge-to-edge (the old whole-
 * paragraph units left half a page blank whenever the next long paragraph
 * didn't fit). Row boundaries come from the real text layout, and the sliced
 * segments keep their bold/italic/link styles. First row keeps paragraphStart
 * (decorations/join), last keeps paragraphEnd.
 */
internal fun blockToRows(line: Line, measurer: TextMeasurer, style: TextStyle, widthPx: Int): List<Line> {
    if (line.kind == LineKind.SEPARATOR) return listOf(line)
    val text = line.segments.joinToString("") { it.text }
    if (text.isBlank()) return listOf(line)
    val layout = measurer.measure(
        AnnotatedString(text),
        style = style,
        constraints = Constraints(maxWidth = widthPx),
    )
    if (layout.lineCount <= 1) return listOf(line)
    val rows = mutableListOf<Line>()
    for (r in 0 until layout.lineCount) {
        val s = layout.getLineStart(r)
        val e = layout.getLineEnd(r, visibleEnd = true)
        if (e <= s) continue
        rows += Line(
            segments = sliceSegments(line.segments, s, e),
            kind = line.kind,
            paragraphStart = r == 0,
            paragraphEnd = r == layout.lineCount - 1,
        )
    }
    return rows.ifEmpty { listOf(line) }
}

/**
 * Packs [lines] into pages that fill the measured viewport (px) exactly.
 * Blocks are first sliced into VISUAL ROWS (see [blockToRows]), then for every
 * page a BINARY-SEARCH finds the largest number of rows that fits, measuring
 * the exact annotated string the page will render (same style, same width) —
 * a page ends at the last line that fits and the paragraph continues on the
 * next page, so no page is left half-empty at a paragraph boundary.
 *
 * An oversized single row (huge <pre>/heading) still gets its own page; the
 * per-page scroll fallback in [PagedReaderBody] keeps it reachable.
 */
internal fun packPagesToViewport(
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
        // Slice every block into its visual rows FIRST: a page may now end
        // mid-paragraph, so pages fill the viewport to the last line instead
        // of leaving half a page blank when the next long paragraph didn't fit.
        val rows = lines.flatMap { blockToRows(it, measurer, style, widthPx) }
        if (rows.isEmpty()) return emptyList()
        fun fits(from: Int, to: Int): Boolean {
            // to exclusive; measures EXACTLY the string the page will render.
            val ann = linesToAnnotated(rows.subList(from, to), fontSize, Color.Black, Color.Black, Color.Black)
            return measurer.measure(
                ann,
                style = style,
                constraints = Constraints(maxWidth = widthPx),
            ).size.height <= heightPx
        }
        // Hard ceiling on rows per page: the paragraph style fixes the line
        // height, so every row occupies at least one row of lineHeightPx and
        // no page can hold more than heightPx/lineHeightPx of them (the ×2
        // slack covers oversized heading rows). Capping the binary search keeps
        // every fits() call at ~one page of text — searching the full remaining
        // chapter measured half of it per call and made packing crawl on long
        // chapters. Results are identical: the true fit is always under the cap.
        val capLines = 2 * (heightPx / ((fontSize * lineHeight).sp.toPx())).toInt() + 8
        val pages = mutableListOf<List<Line>>()
        var i = 0
        while (i < rows.size) {
            var lo = i
            var hi = minOf(rows.size, i + capLines)
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (fits(i, mid)) lo = mid else hi = mid - 1
            }
            val end = lo.coerceAtLeast(i + 1)
            pages += rows.subList(i, end)
            i = end
        }
        return pages
    }
}

/** Converts paginated [Line]s into a styled AnnotatedString using the reader palette. */
internal fun linesToAnnotated(
    lines: List<Line>,
    fontSize: Int,
    fg: Color,
    accent: Color,
    bg: Color,
    highlights: List<Pair<IntRange, Float>> = emptyList(),
    ttsWord: String = "",
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    lines.forEachIndexed { i, line ->
        // A row that starts its block joins with a blank line (paragraph
        // break); a CONTINUATION row (the same paragraph sliced across pages)
        // joins with a single newline so the text reads continuously.
        if (i > 0) builder.append(if (line.paragraphStart) "\n\n" else "\n")
        when (line.kind) {
            LineKind.SEPARATOR -> builder.append("  ✦   ✦   ✦  ")
            LineKind.HEADING -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (fontSize + 2).sp))
                line.segments.forEach { builder.append(it.text) }
                builder.pop()
            }
            LineKind.QUOTE -> {
                builder.pushStyle(SpanStyle(color = lerp(bg, fg, 0.85f)))
                if (line.paragraphStart) builder.append("“")
                line.segments.forEach { seg ->
                    val pushed = pushSegmentStyle(builder, seg, accent)
                    builder.append(seg.text)
                    repeat(pushed) { builder.pop() }
                }
                if (line.paragraphEnd) builder.append("”")
                builder.pop()
            }
            LineKind.LIST -> {
                if (line.paragraphStart) builder.append("•  ")
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
                SpanStyle(background = lerp(bg, accent, alpha)),
                range.first.coerceIn(0, base.length),
                range.last.coerceIn(0, base.length),
            )
        }
        if (ttsWord.isNotBlank()) {
            val start = base.text.indexOf(ttsWord, ignoreCase = true)
            if (start >= 0) {
                addStyle(
                    SpanStyle(background = lerp(bg, accent, 0.55f), fontWeight = FontWeight.Bold),
                    start,
                    (start + ttsWord.length).coerceAtMost(base.length),
                )
            }
        }
    }
}

internal fun pushSegmentStyle(builder: AnnotatedString.Builder, seg: Segment, accent: Color): Int {
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

