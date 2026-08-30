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

// Búsqueda dentro del capítulo (modo scroll): JS para el WebView y navegación
// de coincidencias del modo paginado; helpers de ratio y scroll.

// ---- Find-in-chapter helpers (file-level so LaunchedEffects can call them) --
internal fun goToSearchMatch(
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

internal fun nextSearchIndex(index: Int, size: Int): Int {
    if (size <= 0) return -1
    return (index + 1) % size
}

internal fun prevSearchIndex(index: Int, size: Int): Int {
    if (size <= 0) return -1
    return (index - 1 + size) % size
}


// ---- JS find-in-page (works with data: HTML, unlike findAllAsync) -----------

/**
 * Highlights every occurrence of [query] in the chapter and reports the count.
 *
 * Works for ANY query length and any number of matches: it never touches the
 * document text. Matches become Range objects painted via the CSS Custom
 * Highlight API (an overlay — the DOM stays byte-identical, so re-searching
 * can never corrupt offsets or lose text, which the old <mark>-wrapping
 * approach did on repeated/overlapping searches). Engines without the API
 * fall back to idempotent <mark> wrapping.
 */
internal fun jsFindInPage(query: String): String {
    return """
        (function(){
          try {
            var q = ${jsStringLiteral(query)};
            window.__find = [];
            // Remove any marks left by the fallback path on previous searches.
            var stale = document.querySelectorAll('mark.ao3findmark');
            for (var i = 0; i < stale.length; i++) {
              var m = stale[i];
              if (m.parentNode) m.parentNode.replaceChild(document.createTextNode(m.textContent), m);
            }
            try { if (window.CSS && CSS.highlights) { CSS.highlights.delete('ao3find'); CSS.highlights.delete('ao3findcurrent'); } } catch (e) {}
            if (!q) { if (window.Reader) window.Reader.onJsFindResult(0); return; }
            var root = document.querySelector('.content') || document.body;
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
            var nodes = [];
            while (walker.nextNode()) nodes.push(walker.currentNode);
            var lq = q.toLocaleLowerCase();
            var matches = [];
            var ranges = [];
            for (var n = 0; n < nodes.length; n++) {
              var node = nodes[n];
              var value = node.nodeValue || '';
              var lower = value.toLocaleLowerCase();
              var idx = lower.indexOf(lq);
              while (idx >= 0) {
                var range = document.createRange();
                range.setStart(node, idx);
                range.setEnd(node, idx + q.length);
                ranges.push(range);
                matches.push({range: range});
                idx = lower.indexOf(lq, idx + Math.max(1, lq.length));
              }
            }
            window.__find = matches;
            if (window.CSS && CSS.highlights && ranges.length) {
              var hl = new Highlight();
              for (var i = 0; i < ranges.length; i++) hl.add(ranges[i]);
              CSS.highlights.set('ao3find', hl);
            } else if (ranges.length) {
              // Fallback: wrap each node once (atomic, idempotent).
              for (var n = 0; n < nodes.length; n++) {
                var node = nodes[n];
                var value = node.nodeValue || '';
                var lower = value.toLocaleLowerCase();
                var positions = [];
                var idx = lower.indexOf(lq);
                while (idx >= 0) {
                  positions.push(idx);
                  idx = lower.indexOf(lq, idx + Math.max(1, lq.length));
                }
                if (positions.length === 0 || !node.parentNode) continue;
                var fragment = document.createDocumentFragment();
                var cursor = 0;
                for (var p = 0; p < positions.length; p++) {
                  var start = positions[p];
                  fragment.appendChild(document.createTextNode(value.slice(cursor, start)));
                  var mark = document.createElement('mark');
                  mark.className = 'ao3findmark';
                  mark.textContent = value.slice(start, start + q.length);
                  fragment.appendChild(mark);
                  cursor = start + q.length;
                }
                fragment.appendChild(document.createTextNode(value.slice(cursor)));
                node.parentNode.replaceChild(fragment, node);
              }
            }
            if (window.Reader) window.Reader.onJsFindResult(matches.length);
          } catch (e) {
            window.__find = [];
            if (window.Reader) window.Reader.onJsFindResult(0);
          }
        })();
    """.trimIndent()
}

internal fun jsStringLiteral(value: String): String =
    "'" + value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029") + "'"

/** Scrolls to the [index]-th match (WebView adds ~100px of bottom chrome). */
internal fun jsGoToMatch(index: Int): String = """
    (function(){
      try {
        if (!window.__find || $index < 0 || $index >= window.__find.length) return;
        var m = window.__find[$index];
        var el = m.el || m.range;
        if (!el) return;
        var rect = el.getBoundingClientRect();
        var y = window.scrollY + rect.top - 140;
        window.scrollTo(0, Math.max(0, y));
        // Emphasize the active match (CSS Custom Highlight API only).
        if (m.range && window.CSS && CSS.highlights) {
          try {
            CSS.highlights.delete('ao3findcurrent');
            CSS.highlights.set('ao3findcurrent', new Highlight(m.range));
          } catch (e) {}
        }
      } catch (e) {}
    })();
"""

/** Removes highlights and cached matches. */
internal fun jsClearFind(): String = """
    (function(){
      try {
        window.__find = [];
        var marks = document.querySelectorAll('mark.ao3findmark');
        for (var i = 0; i < marks.length; i++) {
          var m = marks[i];
          if (m.parentNode) m.parentNode.replaceChild(document.createTextNode(m.textContent), m);
        }
        if (window.CSS && CSS.highlights) {
          try { CSS.highlights.delete('ao3find'); CSS.highlights.delete('ao3findcurrent'); } catch(e) {}
        }
      } catch (e) {}
    })();
"""

/**
 * Highlights the TTS phrase currently being spoken WITHOUT modifying the DOM.
 *
 * The old version highlighted the FIRST occurrence of the word in the whole
 * document: for common words ("de", "y", "the"…) it jumped to the top of the
 * chapter, then back — the visible "bouncing" bug. It also wrapped every word
 * in a <mark>, which shifted the layout on every callback.
 *
 * This version:
 *  - uses the CSS Custom Highlight API (a Range overlay: zero DOM changes), so
 *    the layout and scroll position never move on their own;
 *  - prefers the occurrence at/after the last spoken position (TTS reads in
 *    document order), and falls back to the occurrence nearest the viewport
 *    centre when the tracked position is stale (restart, manual jump);
 *  - scrolls only when the word leaves the visible band, and jumps instantly
 *    (behavior auto) instead of animating.
 */
internal fun jsHighlightTtsWord(word: String): String = """
    (function(){
      try {
        var old = document.querySelectorAll('mark.ao3ttsmark');
        for (var i = 0; i < old.length; i++) {
          if (old[i].parentNode) old[i].parentNode.replaceChild(document.createTextNode(old[i].textContent), old[i]);
        }
        try { if (window.CSS && CSS.highlights) CSS.highlights.delete('ao3tts'); } catch (e) {}
        var q = ${jsStringLiteral(word)};
        if (!q) { window.__ttsLast = null; return; }
        var root = document.querySelector('.content') || document.body;
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
        var nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        var lq = q.toLocaleLowerCase();
        // Long phrases can collapse whitespace between text nodes; fall back to
        // the first word when the exact phrase has no match.
        var first = (q.split(/\s+/)[0] || q);
        var lfirst = first.toLocaleLowerCase();
        var candidates = [];
        for (var n = 0; n < nodes.length; n++) {
          var node = nodes[n];
          var value = node.nodeValue || '';
          var lower = value.toLocaleLowerCase();
          var idx = lower.indexOf(lq);
          while (idx >= 0) {
            candidates.push({node: node, ni: n, start: idx, end: idx + q.length});
            idx = lower.indexOf(lq, idx + Math.max(1, lq.length));
          }
          if (lfirst !== lq) {
            var idx2 = lower.indexOf(lfirst);
            while (idx2 >= 0) {
              candidates.push({node: node, ni: n, start: idx2, end: idx2 + first.length});
              idx2 = lower.indexOf(lfirst, idx2 + Math.max(1, lfirst.length));
            }
          }
        }
        if (candidates.length === 0) return;
        candidates.sort(function(a, b) { return a.ni - b.ni || a.start - b.start; });
        var best = null;
        var last = window.__ttsLast;
        if (last && last.node) {
          var li = -1;
          for (var i = 0; i < nodes.length; i++) { if (nodes[i] === last.node) { li = i; break; } }
          for (var c = 0; c < candidates.length; c++) {
            var cand = candidates[c];
            if (li >= 0) {
              if (cand.ni > li) { best = cand; break; }
              if (cand.ni === li && cand.start >= last.offset) { best = cand; break; }
            } else {
              best = cand; break;
            }
          }
        } else {
          best = candidates[0];
        }
        // Stale tracked position: pick the occurrence nearest the viewport
        // centre (the one the user is actually looking at).
        if (!best) {
          var center = window.scrollY + window.innerHeight / 2;
          var bestDist = Infinity;
          for (var c = 0; c < candidates.length; c++) {
            var cand = candidates[c];
            var r = document.createRange();
            r.setStart(cand.node, cand.start);
            r.setEnd(cand.node, cand.end);
            var rect = r.getBoundingClientRect();
            var dist = Math.abs((rect.top + rect.height / 2) - center);
            if (dist < bestDist) { bestDist = dist; best = cand; }
          }
        }
        var range = document.createRange();
        range.setStart(best.node, best.start);
        range.setEnd(best.node, best.end);
        if (window.CSS && CSS.highlights) {
          CSS.highlights.set('ao3tts', new Highlight(range));
        } else {
          // Fallback for engines without the CSS Custom Highlight API.
          var mark = document.createElement('mark');
          mark.className = 'ao3ttsmark';
          mark.textContent = range.toString();
          range.deleteContents();
          range.insertNode(mark);
        }
        window.__ttsLast = {node: best.node, offset: best.end};
        var rect = range.getBoundingClientRect();
        var vh = window.innerHeight;
        // Keep a margin at the top and bottom: only scroll when the word is
        // actually outside the visible band, so normal reading flow is untouched.
        if (rect.top < 96 || rect.bottom > vh - 80) {
          var y = window.scrollY + rect.top - vh / 3;
          window.scrollTo(0, Math.max(0, y));
        }
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

internal fun restoreScroll(wv: WebView, ratio: Float) {
    if (ratio <= 0f) return
    wv.evaluateJavascript(
        "(function(){var h=document.documentElement.scrollHeight||document.body.scrollHeight;var vh=window.innerHeight;window.scrollTo(0, ${ratio}*(h-vh));})()",
        null,
    )
}

