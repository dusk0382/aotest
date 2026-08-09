package net.spin.ao3.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.Store
import net.spin.ao3.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onThemeModeChanged: (AppThemeMode) -> Unit,
) {
    val store = container.store
    val prefs = store.prefs
    var mode by remember { mutableStateOf(AppThemeMode.from(prefs.appThemeMode)) }
    var fontSize by remember { mutableIntStateOf(prefs.fontSizeSp) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var serif by remember { mutableStateOf(prefs.serif) }
    var lineHeight by remember { mutableFloatStateOf(prefs.lineHeight) }
    var margins by remember { mutableIntStateOf(prefs.margins) }
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
            }

            // ---- Lector (valores por defecto) ----
            SettingsSection("Lector (por defecto)") {
                Text("Tema de lectura", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Store.ReaderTheme.entries.forEach { option ->
                        FilterChip(
                            selected = theme == option,
                            onClick = { theme = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("Tamaño de letra", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt() },
                        valueRange = 13f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", fontSize = 22.sp)
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
                            onClick = { margins = value },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                Text("Tipografía", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = serif, onClick = { serif = true }, label = { Text("Serif") })
                    FilterChip(selected = !serif, onClick = { serif = false }, label = { Text("Sans serif") })
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
                    "AO3 Lector · v0.3.0\nApp de uso personal. Todo el contenido pertenece a sus autores y se sirve desde archiveofourown.org.\nSé respetuoso con el sitio: las descargas y comentarios se hacen como un lector normal.",
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
