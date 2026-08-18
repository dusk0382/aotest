package net.spin.ao3.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.spin.ao3.R
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    val store = container.store
    val prefs = store.prefs
    var mode by remember { mutableStateOf(AppThemeMode.from(prefs.appThemeMode)) }
    var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }
    var fontSize by remember { mutableIntStateOf(prefs.fontSizeSp) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var serif by remember { mutableStateOf(prefs.serif) }
    var lineHeight by remember { mutableFloatStateOf(prefs.lineHeight) }
    var margins by remember { mutableIntStateOf(prefs.margins) }
    var paged by remember { mutableStateOf(prefs.paged) }
    var commentName by remember { mutableStateOf(prefs.commentName) }
    var commentEmail by remember { mutableStateOf(prefs.commentEmail) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ---- Apariencia ----
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_reader_theme_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_colors), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                stringResource(R.string.settings_dynamic_colors_on)
                            } else {
                                stringResource(R.string.settings_dynamic_colors_off)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

            // ---- Lector (valores por defecto) ----
            SettingsSection(stringResource(R.string.settings_reader_defaults)) {
                Text(stringResource(R.string.settings_reading_mode), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_reading_theme), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyMedium)
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
                    "${fontSize} sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(14.dp))

                Text(stringResource(R.string.settings_line_height), style = MaterialTheme.typography.bodyMedium)
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
                Spacer(Modifier.height(14.dp))

                Text(stringResource(R.string.settings_margins), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(14.dp))

                Text(stringResource(R.string.settings_typography), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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

            // ---- Comentarios ----
            SettingsSection(stringResource(R.string.settings_comments)) {
                Text(
                    stringResource(R.string.settings_comments_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(8.dp))
                Text(
                    "Estos datos se usarán como valores predeterminados al comentar; el email no se muestra públicamente.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        prefs.commentName = commentName.trim()
                        prefs.commentEmail = commentEmail.trim()
                        store.savePrefs()
                        scope.launch { snackbar.showSnackbar("Datos de comentarios guardados") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Guardar datos")
                }
            }

            // ---- Copia de seguridad ----
            BackupSection(store = store, snackbar = snackbar)

            // ---- Acerca de ----
            SettingsSection(stringResource(R.string.settings_about)) {
                Text(
                    stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackupSection(store: Store, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmImport by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    // Resolved in composable context (stringResource is not callable inside
    // coroutines / click callbacks), then reused by the launchers + dialog.
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
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(store.exportBackup().toByteArray())
                    } != null
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
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
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

    SettingsSection(stringResource(R.string.settings_backup)) {
        Text(
            stringResource(R.string.settings_backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
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
                        val ok = json != null && store.importBackup(json)
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

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
