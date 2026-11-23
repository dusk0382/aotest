package net.spin.ao3.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spin.ao3.BuildConfig
import net.spin.ao3.R
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.ui.reader.readerColors
import net.spin.ao3.ui.theme.AppThemeMode
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.components.LocalBottomBarClearance
import net.spin.ao3.ui.components.PrimaryCtaButton
import net.spin.ao3.ui.components.SecondaryOutlinedButton

/** Las categorías del índice de Ajustes. Cada una abre su propia pantalla. */
private enum class SettingsPage(@get:StringRes val titleRes: Int) {
    APPEARANCE(R.string.settings_appearance),
    READER(R.string.settings_reader_defaults),
    COMMENTS(R.string.settings_comments),
    TRANSLATION(R.string.settings_translation),
    BACKUP(R.string.settings_backup),
}

/**
 * Ajustes como índice de categorías: cada fila resume el estado actual y abre
 * la pantalla con esos controles. Antes había 17 controles apilados en una sola
 * lista, donde nada destacaba y había que recorrer media pantalla para cambiar
 * el tamaño del lector.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onSubpageChange: (Boolean) -> Unit = {},
) {
    val store = container.store
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Atrás vuelve al índice, no al Inicio (el BackHandler de MainActivity
    // quedaría por debajo de este mientras haya una categoría abierta).
    BackHandler(enabled = page != null) { page = null }

    // Reportar si hay una subpágina abierta para que MainActivity esconda la
    // píldora de navegación (navega con estado interno, no empuja rutas).
    // En two-pane la página convive con el índice: ahí la píldora puede quedarse.
    val config = LocalConfiguration.current
    DisposableEffect(page, config) {
        onSubpageChange(page != null && config.screenWidthDp < 840)
        onDispose { onSubpageChange(false) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val twoPane = maxWidth >= 840.dp && maxHeight >= 480.dp
            val showBack = !twoPane
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        HubList(onOpen = { page = it })
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (page == null) {
                            EmptyState(
                                icon = Icons.Default.Settings,
                                title = "Elige una categoría",
                                description = "Selecciona un ajuste a la izquierda para abrirlo.",
                            )
                        } else {
                            SettingsPageContent(
                                page = page!!, store = store, container = container,
                                snackbar = snackbar, showBack = false,
                                onBack = { page = null },
                                onThemeModeChanged = onThemeModeChanged,
                                onDynamicColorChanged = onDynamicColorChanged,
                            )
                        }
                    }
                }
            } else {
                if (page == null) {
                    SettingsHub(onOpen = { page = it })
                } else {
                    SettingsPageContent(
                        page = page!!, store = store, container = container,
                        snackbar = snackbar, showBack = true,
                        onBack = { page = null },
                        onThemeModeChanged = onThemeModeChanged,
                        onDynamicColorChanged = onDynamicColorChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    store: Store,
    container: AppContainer,
    snackbar: SnackbarHostState,
    showBack: Boolean,
    onBack: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    when (page) {
        SettingsPage.APPEARANCE -> AppearancePage(store, onBack, onThemeModeChanged, onDynamicColorChanged, showBack)
        SettingsPage.READER -> ReaderPage(store, onBack, showBack)
        SettingsPage.COMMENTS -> CommentsPage(store, snackbar, onBack, showBack)
        SettingsPage.TRANSLATION -> TranslationPage(container, snackbar, onBack, showBack)
        SettingsPage.BACKUP -> BackupPage(store, snackbar, onBack, showBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHub(onOpen: (SettingsPage) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_settings),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HubList(onOpen = onOpen)
        }
    }
}

@Composable
private fun HubList(onOpen: (SettingsPage) -> Unit) {
    data class Entry(
        @StringRes val titleRes: Int,
        @StringRes val descRes: Int,
        val icon: ImageVector,
        val target: SettingsPage,
    )
    val entries = listOf(
        Entry(R.string.settings_appearance, R.string.settings_appearance_desc, Icons.Default.Palette, SettingsPage.APPEARANCE),
        Entry(R.string.settings_reader_defaults, R.string.settings_reader_defaults_desc, Icons.AutoMirrored.Filled.MenuBook, SettingsPage.READER),
        Entry(R.string.settings_comments, R.string.settings_comments_desc, Icons.Default.Comment, SettingsPage.COMMENTS),
        Entry(R.string.settings_translation, R.string.settings_translation_desc, Icons.Default.Translate, SettingsPage.TRANSLATION),
        Entry(R.string.settings_backup, R.string.settings_backup_desc, Icons.Default.SettingsBackupRestore, SettingsPage.BACKUP),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // Despeje para la píldora flotante: el índice pasa por detrás.
            .padding(bottom = LocalBottomBarClearance.current),
    ) {
        entries.forEachIndexed { index, entry ->
            SegmentedListItem(
                onClick = { onOpen(entry.target) },
                shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                leadingContent = {
                    TonalIconCircle(entry.icon, circle = 40.dp, glyph = 22.dp)
                },
                supportingContent = { Text(stringResource(entry.descRes)) },
                content = {
                    Text(
                        stringResource(entry.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            if (index < entries.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
        }
    }
}

/** Marco común de una categoría: barra compacta con «volver» y contenido desplazable. */
@Composable
private fun navIcon(showBack: Boolean, onBack: () -> Unit): @Composable () -> Unit = {
    if (showBack) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    showBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = navIcon(showBack, onBack),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

// ---- Bloques de composición del nuevo diseño -------------------------------

/** Círculo tonal con un icono: la firma del patrón «Segmented settings». */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun TonalIconCircle(icon: ImageVector, circle: Dp = 36.dp, glyph: Dp = 20.dp) {
    // Sello de marca: la misma forma Cookie9Sided que los estados vacíos —
    // un sistema de sellos recurrente, no círculos genéricos.
    Box(
        modifier = Modifier
            .size(circle)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(glyph),
        )
    }
}

/**
 * Grupo de opciones dentro de una tarjeta: cabecera opcional + superficie
 * sólida `surfaceContainerLow` con esquina `medium` (12 dp, token de tarjeta).
 */
@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * Fila de ajuste: círculo tonal + título + descripción opcional + control a la
 * derecha. Con `onClick` toda la fila es pulsable (filas de acción).
 */
@Composable
private fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TonalIconCircle(icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Divisor con sangría alineado tras el círculo del icono (16 + 36 + 12 = 64 dp). */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Control segmentado a ancho completo: cada opción pesa lo mismo, el grupo
 * ocupa toda la tarjeta. Es el patrón favorito para opciones mutuamente
 * excluyentes de 2 a 4 valores.
 */
@Composable
private fun <T> SegmentedOptions(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option.first,
                onClick = { onSelect(option.first) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.weight(1f),
                label = { Text(option.second, maxLines = 1) },
            )
        }
    }
}

/** Fila de deslizador dentro de tarjeta: título + valor actual en primario. */
@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onCommit,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppearancePage(
    store: Store,
    onBack: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    showBack: Boolean = true,
) {
    val prefs = store.prefs
    var mode by remember { mutableStateOf(AppThemeMode.from(prefs.appThemeMode)) }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }

    SettingsPageScaffold(R.string.settings_appearance, onBack, showBack) {
        SettingsSection(title = stringResource(R.string.settings_app_theme)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedOptions(
                    options = AppThemeMode.entries.map { it to it.label },
                    selected = mode,
                    onSelect = { option ->
                        mode = option
                        prefs.appThemeMode = option.name
                        store.savePrefs()
                        onThemeModeChanged(option)
                    },
                )
            }
        }
        SettingsSection {
            val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SettingsRow(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_dynamic_colors),
                subtitle = when {
                    canDynamic -> stringResource(
                        if (dynamicColor) R.string.settings_on else R.string.settings_off,
                    )
                    else -> stringResource(R.string.settings_dynamic_colors_off)
                },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        enabled = canDynamic,
                        onCheckedChange = {
                            dynamicColor = it
                            prefs.dynamicColor = it
                            store.savePrefs()
                            onDynamicColorChanged(it)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReaderPage(store: Store, onBack: () -> Unit, showBack: Boolean = true) {
    val prefs = store.prefs
    var fontSize by remember { mutableIntStateOf(prefs.fontSizeSp) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var serif by remember { mutableStateOf(prefs.serif) }
    var lineHeight by remember { mutableFloatStateOf(prefs.lineHeight) }
    var margins by remember { mutableIntStateOf(prefs.margins) }
    var paged by remember { mutableStateOf(prefs.paged) }

    SettingsPageScaffold(R.string.settings_reader_defaults, onBack, showBack) {
        SettingsSection(title = stringResource(R.string.settings_reading_mode)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedOptions(
                    options = listOf(
                        false to stringResource(R.string.settings_scroll),
                        true to stringResource(R.string.settings_paged),
                    ),
                    selected = paged,
                    onSelect = { option ->
                        paged = option
                        prefs.paged = option
                        store.savePrefs()
                    },
                )
            }
        }

        // Muestras reales: cada tesela pinta su propio fondo y texto, así la
        // elección se ve antes de abrir el lector.
        SettingsSection(title = stringResource(R.string.settings_reading_theme)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Store.ReaderTheme.entries.forEach { option ->
                    val (bg, fg, _) = readerColors.getValue(option)
                    val tileShape = MaterialTheme.shapes.small
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(tileShape)
                            .background(bg)
                            .then(
                                if (theme == option) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, tileShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                theme = option
                                prefs.theme = option
                                store.savePrefs()
                            }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Aa",
                            style = MaterialTheme.typography.titleLarge,
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_typography)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedOptions(
                    options = listOf(
                        true to stringResource(R.string.settings_serif),
                        false to stringResource(R.string.settings_sans),
                    ),
                    selected = serif,
                    onSelect = { option ->
                        serif = option
                        prefs.serif = option
                        store.savePrefs()
                    },
                )
            }
            RowDivider()
            SliderRow(
                title = stringResource(R.string.settings_font_size),
                valueLabel = "$fontSize sp",
                value = fontSize.toFloat(),
                valueRange = 13f..28f,
                onChange = { fontSize = it.toInt() },
                onCommit = {
                    prefs.fontSizeSp = fontSize
                    store.savePrefs()
                },
            )
            RowDivider()
            SliderRow(
                title = stringResource(R.string.settings_line_height),
                valueLabel = "%.2f".format(lineHeight),
                value = lineHeight,
                valueRange = 1.2f..2.4f,
                onChange = { lineHeight = it },
                onCommit = {
                    prefs.lineHeight = lineHeight
                    store.savePrefs()
                },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_margins)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedOptions(
                    options = listOf(
                        0 to stringResource(R.string.settings_margins_narrow),
                        1 to stringResource(R.string.settings_margins_normal),
                        2 to stringResource(R.string.settings_margins_wide),
                    ),
                    selected = margins,
                    onSelect = { option ->
                        margins = option
                        prefs.margins = option
                        store.savePrefs()
                    },
                )
            }
        }
    }
}

@Composable
private fun CommentsPage(store: Store, snackbar: SnackbarHostState, onBack: () -> Unit, showBack: Boolean = true) {
    val prefs = store.prefs
    val scope = rememberCoroutineScope()
    var commentName by remember { mutableStateOf(prefs.commentName) }
    var commentEmail by remember { mutableStateOf(prefs.commentEmail) }

    SettingsPageScaffold(R.string.settings_comments, onBack, showBack) {
        SettingsSection {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = commentName,
                    onValueChange = { commentName = it },
                    label = { Text(stringResource(R.string.settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commentEmail,
                    onValueChange = { commentEmail = it },
                    label = { Text(stringResource(R.string.settings_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PrimaryCtaButton(
            onClick = {
                prefs.commentName = commentName.trim()
                prefs.commentEmail = commentEmail.trim()
                store.savePrefs()
                scope.launch { snackbar.showSnackbar("Datos guardados") }
            },
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TranslationPage(container: AppContainer, snackbar: SnackbarHostState, onBack: () -> Unit, showBack: Boolean = true) {
    val store = container.store
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(if (store.prefs.translationProvider == "ai") "ai" else "google") }
    var baseUrl by remember { mutableStateOf(store.prefs.aiBaseUrl) }
    val apiKeyState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState(store.prefs.aiApiKey) }
    val apiKey by remember { derivedStateOf { apiKeyState.text.toString() } }
    var model by remember { mutableStateOf(store.prefs.aiModel) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    // Guardar y probar comparten camino: el traductor lee las prefs al llamar,
    // así que probar sin guardar antes ejercitaba la configuración vieja.
    fun saveNow(): String {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val mdl = model.trim()
        if (provider == "ai" && (url.isBlank() || key.isBlank() || mdl.isBlank())) {
            return "Faltan Base URL, API key o modelo"
        }
        store.prefs.translationProvider = provider
        store.prefs.aiBaseUrl = url
        store.prefs.aiApiKey = key
        store.prefs.aiModel = mdl
        store.savePrefs()
        return ""
    }

    SettingsPageScaffold(R.string.settings_translation, onBack, showBack) {
        SettingsSection(title = stringResource(R.string.settings_translation)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SegmentedOptions(
                    options = listOf(
                        "google" to "Google",
                        "ai" to "IA (OpenAI)",
                    ),
                    selected = provider,
                    onSelect = { provider = it },
                )
            }
        }
        if (provider == "ai") {
            SettingsSection {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedSecureTextField(
                        state = apiKeyState,
                        label = { Text("API key") },
                        textObfuscationMode = if (showKey) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showKey) "Ocultar API key" else "Mostrar API key",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo") },
                        placeholder = { Text("gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryOutlinedButton(
                onClick = {
                    val invalid = saveNow()
                    if (invalid.isNotBlank()) {
                        scope.launch { snackbar.showSnackbar(invalid) }
                        return@SecondaryOutlinedButton
                    }
                    if (testing) return@SecondaryOutlinedButton
                    testing = true
                    scope.launch {
                        val msg = try {
                            val out = container.translator.translateChapterHtml(
                                "settings_test_${System.currentTimeMillis()}",
                                "<p>Hello, this is a small translation test.</p>",
                                store.prefs.translationLang.ifBlank { "es" },
                            )
                            "Conexión OK · traducción: “${out.take(60)}”"
                        } catch (e: Exception) {
                            "Falló la prueba: ${e.message}"
                        }
                        snackbar.showSnackbar(msg)
                        testing = false
                    }
                },
                enabled = !testing,
                modifier = Modifier.weight(1f),
            ) {
                if (testing) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(R.string.settings_test), maxLines = 1)
                }
            }
            PrimaryCtaButton(
                onClick = {
                    val invalid = saveNow()
                    scope.launch {
                        snackbar.showSnackbar(if (invalid.isNotBlank()) invalid else "Ajustes guardados")
                    }
                },
                enabled = provider != "ai" || (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_save), maxLines = 1)
            }
        }
    }
}

@Composable
private fun BackupPage(store: Store, snackbar: SnackbarHostState, onBack: () -> Unit, showBack: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    // Resueltos aquí: stringResource no es invocable dentro de callbacks.
    val savedMsg = stringResource(R.string.settings_backup_saved)
    val failedMsg = stringResource(R.string.settings_backup_failed)
    val unreadableMsg = stringResource(R.string.settings_backup_unreadable)
    val restoredMsg = stringResource(R.string.settings_backup_restored)
    val invalidMsg = stringResource(R.string.settings_backup_invalid)
    val restoreTitle = stringResource(R.string.settings_restore_title)
    val restoreBody = stringResource(R.string.settings_restore_body)
    val restoreLabel = stringResource(R.string.settings_restore)
    val cancelLabel = stringResource(R.string.settings_cancel)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // Serializar la biblioteca completa (incluye descargas, MB) es
                // trabajo de IO: en main provocaba jank/ANR con biblioteca grande.
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(store.exportBackup().toByteArray())
                        } != null
                    }
                }.getOrDefault(false)
                snackbar.showSnackbar(if (ok) savedMsg else failedMsg)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    }
                }.getOrNull()
                if (json.isNullOrBlank()) {
                    snackbar.showSnackbar(unreadableMsg)
                } else {
                    pendingImportJson = json
                    confirmImport = true
                }
            }
        }
    }

    SettingsPageScaffold(R.string.settings_backup, onBack, showBack) {
        SettingsSection {
            SettingsRow(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.settings_export),
                onClick = { exportLauncher.launch("ao3-lector-copia-${System.currentTimeMillis()}.json") },
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Default.Upload,
                title = stringResource(R.string.settings_import),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false; pendingImportJson = null },
            title = { Text(restoreTitle) },
            text = { Text(restoreBody) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    val json = pendingImportJson
                    pendingImportJson = null
                    scope.launch {
                        // Parsear el JSON y reescribir ambos ficheros también es IO.
                        val ok = json != null && withContext(Dispatchers.IO) { store.importBackup(json) }
                        snackbar.showSnackbar(if (ok) restoredMsg else invalidMsg)
                    }
                }) { Text(restoreLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false; pendingImportJson = null }) {
                    Text(cancelLabel)
                }
            },
        )
    }
}
