package net.spin.ao3.ui.screens

import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ---- Apariencia ----
            SettingsSection("Apariencia") {
                Text("Tema de la app", style = MaterialTheme.typography.bodyMedium)
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
                    "El lector tiene sus propios temas (incluido AMOLED) que puedes cambiar mientras lees.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Colores dinámicos", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "Usa los colores de tu fondo de pantalla (Material You)."
                            } else {
                                "Tu dispositivo no soporta colores dinámicos (requiere Android 12+)."
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
            SettingsSection("Lector (por defecto)") {
                Text("Modo de lectura", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !paged,
                        onClick = {
                            paged = false
                            prefs.paged = false
                            store.savePrefs()
                        },
                        label = { Text("Scroll continuo") },
                    )
                    FilterChip(
                        selected = paged,
                        onClick = {
                            paged = true
                            prefs.paged = true
                            store.savePrefs()
                        },
                        label = { Text("Paginado") },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Tema de lectura", style = MaterialTheme.typography.bodyMedium)
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
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("Tamaño de letra", style = MaterialTheme.typography.bodyMedium)
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

                Text("Interlineado", style = MaterialTheme.typography.bodyMedium)
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

                Text("Márgenes", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Estrechos", 1 to "Normales", 2 to "Amplios").forEach { (value, label) ->
                        FilterChip(
                            selected = margins == value,
                            onClick = {
                                margins = value
                                prefs.margins = value
                                store.savePrefs()
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                Text("Tipografía", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = serif,
                        onClick = {
                            serif = true
                            prefs.serif = true
                            store.savePrefs()
                        },
                        label = { Text("Serif") },
                    )
                    FilterChip(
                        selected = !serif,
                        onClick = {
                            serif = false
                            prefs.serif = false
                            store.savePrefs()
                        },
                        label = { Text("Sans serif") },
                    )
                }
            }

            // ---- Comentarios ----
            SettingsSection("Comentarios (invitado)") {
                Text(
                    "AO3 permite comentar sin cuenta usando un nombre y un email (no se publica). Se guardan aquí para rellenar el formulario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = commentName,
                    onValueChange = { commentName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = commentEmail,
                    onValueChange = { commentEmail = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Acerca de ----
            SettingsSection("Acerca de") {
                Text(
                    "AO3 Lector · v0.6.3\nApp de uso personal. Todo el contenido pertenece a sus autores y se sirve desde archiveofourown.org.\nSé respetuoso con el sitio: las descargas y comentarios se hacen como un lector normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
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
