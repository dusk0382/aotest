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

// Paleta, CSS y vista previa del lector (4 temas + AMOLED).

// ---- Reader theming ---------------------------------------------------------

internal fun readerPalette(theme: Store.ReaderTheme): Triple<String, String, String> = when (theme) {
    Store.ReaderTheme.LIGHT -> Triple("#ffffff", "#1c1c1e", "#2F6B35")
    Store.ReaderTheme.SEPIA -> Triple("#f6edd9", "#433422", "#4F7A3A")
    Store.ReaderTheme.DARK -> Triple("#1b1b1f", "#d6d3d0", "#9AD8A6")
    Store.ReaderTheme.BLACK -> Triple("#000000", "#d4d4d4", "#8FCF9E")
}

// Precomputed once: parsing the hex per call ran dozens of times per
// recomposed frame of the reader chrome (bar, menus, overlays). The hex
// Strings stay for the WebView CSS, which is rebuilt only on theme change.
// Pure-JVM parse (no android.graphics): this file's top-level state must stay
// initializable in JVM unit tests (ReaderRatioTest).
internal fun parseReaderColor(hex: String): Color =
    Color(0xFF000000L or hex.removePrefix("#").toLong(16))

internal val readerColors: Map<Store.ReaderTheme, Triple<Color, Color, Color>> =
    Store.ReaderTheme.entries.associateWith { t ->
        val (bg, fg, accent) = readerPalette(t)
        Triple(parseReaderColor(bg), parseReaderColor(fg), parseReaderColor(accent))
    }

internal fun readerBgColor(theme: Store.ReaderTheme): Color = readerColors.getValue(theme).first

internal fun readerFgColor(theme: Store.ReaderTheme): Color = readerColors.getValue(theme).second

internal fun readerAccentColor(theme: Store.ReaderTheme): Color = readerColors.getValue(theme).third

// ---- Live typography preview (settings sheet) --------------------------------

@Composable
internal fun ReaderPreview(
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

internal fun marginPadding(margins: Int): Pair<String, String> = when (margins) {
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
internal fun readerCss(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean, lineHeight: Float, margins: Int): String {
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
        ::highlight(ao3findcurrent) { background-color: var(--accent); color: #ffffff; }
        ::highlight(ao3tts) { background-color: var(--accent); color: #ffffff; }
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
internal fun readerPrefsJson(theme: Store.ReaderTheme, sizeSp: Int, serif: Boolean, lineHeight: Float, margins: Int): String {
    val (bg, fg, accent) = readerPalette(theme)
    val subtle = if (theme == Store.ReaderTheme.SEPIA) "#7a6a50" else "#9a9a9a"
    val divider = if (theme == Store.ReaderTheme.LIGHT) "#e0e0e0" else "#3a3a3f"
    val family = if (serif) "Georgia, 'Times New Roman', serif" else "Roboto, 'Helvetica Neue', sans-serif"
    val colorScheme = if (theme == Store.ReaderTheme.LIGHT || theme == Store.ReaderTheme.SEPIA) "light" else "dark"
    val (padMobile, padTablet) = marginPadding(margins)
    return "{\"bg\":\"$bg\",\"fg\":\"$fg\",\"accent\":\"$accent\",\"accentSoft\":\"${accent}66\",\"subtle\":\"$subtle\",\"divider\":\"$divider\",\"fontFamily\":\"$family\",\"fontSize\":$sizeSp,\"lineHeight\":$lineHeight,\"padMobile\":\"$padMobile\",\"padTablet\":\"$padTablet\",\"colorScheme\":\"$colorScheme\"}"
}

