package net.spin.ao3.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spin.ao3.BuildConfig
import net.spin.ao3.R
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.ui.theme.AppThemeMode

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
@Composable
fun SettingsScreen(
    container: AppContainer,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    val store = container.store
    val prefs = store.prefs
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Atrás vuelve al índice, no al Inicio (el BackHandler de MainActivity
    // quedaría por debajo de este mientras haya una categoría abierta).
    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        when (page) {
            null -> SettingsHub(
                prefs = prefs,
                onOpen = { page = it },
            )
            SettingsPage.APPEARANCE -> AppearancePage(
                store = store,
                onBack = { page = null },
                onThemeModeChanged = onThemeModeChanged,
                onDynamicColorChanged = onDynamicColorChanged,
            )
            SettingsPage.READER -> ReaderPage(store = store, onBack = { page = null })
            SettingsPage.COMMENTS -> CommentsPage(store = store, snackbar = snackbar, onBack = { page = null })
            SettingsPage.TRANSLATION -> TranslationPage(container = container, snackbar = snackbar, onBack = { page = null })
            SettingsPage.BACKUP -> BackupPage(store = store, snackbar = snackbar, onBack = { page = null })
        }
    }
}

@Composable
private fun SettingsHub(
    prefs: Store.Prefs,
    onOpen: (SettingsPage) -> Unit,
) {
    val themeLabel = AppThemeMode.from(prefs.appThemeMode).label
    // En Android sin Material You el resumen se queda en el tema: repetir aquí
    // el requisito de la versión alargaba la fila a dos líneas.
    val appearanceSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicLabel = stringResource(if (prefs.dynamicColor) R.string.settings_on else R.string.settings_off)
        "$themeLabel · ${stringResource(R.string.settings_dynamic_colors)}: $dynamicLabel"
    } else {
        themeLabel
    }
    val readerThemeLabel = stringResource(prefs.theme.labelRes)
    val modeLabel = stringResource(if (prefs.paged) R.string.settings_paged else R.string.settings_scroll)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HubRow(
            title = stringResource(R.string.settings_appearance),
            summary = appearanceSummary,
            onClick = { onOpen(SettingsPage.APPEARANCE) },
        )
        HubRow(
            title = stringResource(R.string.settings_reader_defaults),
            summary = "$modeLabel · ${prefs.fontSizeSp} sp · $readerThemeLabel",
            onClick = { onOpen(SettingsPage.READER) },
        )
        HubRow(
            title = stringResource(R.string.settings_comments),
            summary = prefs.commentName.ifBlank { stringResource(R.string.settings_not_set) },
            onClick = { onOpen(SettingsPage.COMMENTS) },
        )
        HubRow(
            title = stringResource(R.string.settings_translation),
            summary = if (prefs.translationProvider == "ai") "IA (OpenAI)" else "Google",
            onClick = { onOpen(SettingsPage.TRANSLATION) },
        )
        HubRow(
            title = stringResource(R.string.settings_backup),
            summary = null,
            onClick = { onOpen(SettingsPage.BACKUP) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fila del índice: título, valor actual y toda la superficie como toque. */
@Composable
private fun HubRow(title: String, summary: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Marco común de una categoría: barra con «volver» y contenido desplazable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsSection(title: String? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(
    store: Store,
    onBack: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    val prefs = store.prefs
    var mode by remember { mutableStateOf(AppThemeMode.from(prefs.appThemeMode)) }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }

    SettingsPageScaffold(R.string.settings_appearance, onBack) {
        SettingsSection(stringResource(R.string.settings_app_theme)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = {
                            mode = option
                            prefs.appThemeMode = option.name
                            store.savePrefs()
                            onThemeModeChanged(option)
                        },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        SettingsSection(stringResource(R.string.settings_dynamic_colors)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stringResource(if (dynamicColor) R.string.settings_on else R.string.settings_off)
                    } else {
                        stringResource(R.string.settings_dynamic_colors_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = {
                        dynamicColor = it
                        prefs.dynamicColor = it
                        store.savePrefs()
                        onDynamicColorChanged(it)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderPage(store: Store, onBack: () -> Unit) {
    val prefs = store.prefs
    var fontSize by remember { mutableIntStateOf(prefs.fontSizeSp) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var serif by remember { mutableStateOf(prefs.serif) }
    var lineHeight by remember { mutableFloatStateOf(prefs.lineHeight) }
    var margins by remember { mutableIntStateOf(prefs.margins) }
    var paged by remember { mutableStateOf(prefs.paged) }

    SettingsPageScaffold(R.string.settings_reader_defaults, onBack) {
        SettingsSection(stringResource(R.string.settings_reading_mode)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !paged,
                    onClick = {
                        paged = false
                        prefs.paged = false
                        store.savePrefs()
                    },
                    label = { Text(stringResource(R.string.settings_scroll)) },
                )
                FilterChip(
                    selected = paged,
                    onClick = {
                        paged = true
                        prefs.paged = true
                        store.savePrefs()
                    },
                    label = { Text(stringResource(R.string.settings_paged)) },
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_reading_theme)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Store.ReaderTheme.entries.forEach { option ->
                    FilterChip(
                        selected = theme == option,
                        onClick = {
                            theme = option
                            prefs.theme = option
                            store.savePrefs()
                        },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_font_size)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { fontSize = it.toInt() },
                    onValueChangeFinished = {
                        prefs.fontSizeSp = fontSize
                        store.savePrefs()
                    },
                    valueRange = 13f..28f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("A", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "$fontSize sp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SettingsSection(stringResource(R.string.settings_line_height)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1.2", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = lineHeight,
                    onValueChange = { lineHeight = it },
                    onValueChangeFinished = {
                        prefs.lineHeight = lineHeight
                        store.savePrefs()
                    },
                    valueRange = 1.2f..2.4f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("2.4", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "%.2f".format(lineHeight),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SettingsSection(stringResource(R.string.settings_margins)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to R.string.settings_margins_narrow,
                    1 to R.string.settings_margins_normal,
                    2 to R.string.settings_margins_wide,
                ).forEach { (value, labelRes) ->
                    FilterChip(
                        selected = margins == value,
                        onClick = {
                            margins = value
                            prefs.margins = value
                            store.savePrefs()
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_typography)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = serif,
                    onClick = {
                        serif = true
                        prefs.serif = true
                        store.savePrefs()
                    },
                    label = { Text(stringResource(R.string.settings_serif)) },
                )
                FilterChip(
                    selected = !serif,
                    onClick = {
                        serif = false
                        prefs.serif = false
                        store.savePrefs()
                    },
                    label = { Text(stringResource(R.string.settings_sans)) },
                )
            }
        }
    }
}

@Composable
private fun CommentsPage(store: Store, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val prefs = store.prefs
    val scope = rememberCoroutineScope()
    var commentName by remember { mutableStateOf(prefs.commentName) }
    var commentEmail by remember { mutableStateOf(prefs.commentEmail) }

    SettingsPageScaffold(R.string.settings_comments, onBack) {
        OutlinedTextField(
            value = commentName,
            onValueChange = { commentName = it },
            label = { Text(stringResource(R.string.settings_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = commentEmail,
            onValueChange = { commentEmail = it },
            label = { Text(stringResource(R.string.settings_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                prefs.commentName = commentName.trim()
                prefs.commentEmail = commentEmail.trim()
                store.savePrefs()
                scope.launch { snackbar.showSnackbar("Datos guardados") }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationPage(container: AppContainer, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val store = container.store
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(if (store.prefs.translationProvider == "ai") "ai" else "google") }
    var baseUrl by remember { mutableStateOf(store.prefs.aiBaseUrl) }
    var apiKey by remember { mutableStateOf(store.prefs.aiApiKey) }
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

    SettingsPageScaffold(R.string.settings_translation, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = provider != "ai",
                onClick = { provider = "google" },
                label = { Text("Google") },
            )
            FilterChip(
                selected = provider == "ai",
                onClick = { provider = "ai" },
                label = { Text("IA (OpenAI)") },
            )
        }
        if (provider == "ai") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo") },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val invalid = saveNow()
                    if (invalid.isNotBlank()) {
                        scope.launch { snackbar.showSnackbar(invalid) }
                        return@OutlinedButton
                    }
                    if (testing) return@OutlinedButton
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_test), maxLines = 1)
                }
            }
            Button(
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
private fun BackupPage(store: Store, snackbar: SnackbarHostState, onBack: () -> Unit) {
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

    SettingsPageScaffold(R.string.settings_backup, onBack) {
        OutlinedButton(
            onClick = { exportLauncher.launch("ao3-lector-copia-${System.currentTimeMillis()}.json") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_export))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_import))
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
