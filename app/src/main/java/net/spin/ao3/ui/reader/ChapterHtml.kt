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

// HTML del capítulo para el modo scroll (cabecera obra/capítulo + contenido + CSS).

internal fun buildChapterHtml(workTitle: String, chapterIndex: Int, chapterTitle: String, contentHtml: String, css: String): String {
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
