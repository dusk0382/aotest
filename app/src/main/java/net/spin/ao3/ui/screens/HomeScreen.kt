package net.spin.ao3.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onSearch: (String, SortOption) -> Unit,
    onBrowseTag: (String) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenReader: (Long, Int) -> Unit,
) {
    val store = container.store
    val history = remember { store.history() }
    val saved = remember { store.savedWorks() }
    val downloads = remember { store.downloads() }
    var query by rememberSaveable { mutableStateOf("") }

    val chipColor = MaterialTheme.colorScheme.primary
    val fandomChipColor = MaterialTheme.colorScheme.secondary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AO3 Lector", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Busca por título, fandom, autor, tag…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) onSearch(query.trim(), SortOption.BEST_MATCH) },
                ),
            )
            Spacer(Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip("⭐ Tendencias", chipColor, tinted = true, onClick = { onSearch("", SortOption.KUDOS) })
                TagChip("🔥 Lo nuevo", chipColor, tinted = true, onClick = { onSearch("", SortOption.UPDATED) })
                TagChip("📖 Más leídas", chipColor, tinted = true, onClick = { onSearch("", SortOption.HITS) })
                TagChip("✍️ Más largas", chipColor, tinted = true, onClick = { onSearch("", SortOption.WORDS) })
            }
            Spacer(Modifier.height(22.dp))

            SectionTitle("Explorar fandoms")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip("Naruto", fandomChipColor, onClick = { onBrowseTag("Naruto") })
                TagChip("Harry Potter", fandomChipColor, onClick = { onBrowseTag("Harry Potter") })
                TagChip("Marvel", fandomChipColor, onClick = { onBrowseTag("Marvel") })
                TagChip("DCU", fandomChipColor, onClick = { onBrowseTag("DCU") })
                TagChip("My Hero Academia", fandomChipColor, onClick = { onBrowseTag("My Hero Academia") })
                TagChip("One Piece", fandomChipColor, onClick = { onBrowseTag("One Piece") })
                TagChip("Sherlock", fandomChipColor, onClick = { onBrowseTag("Sherlock (TV)") })
            }
            Spacer(Modifier.height(22.dp))

            SectionTitle("Continuar leyendo")
            if (history.isEmpty()) {
                EmptyHint("Todavía no has leído nada.\nBusca un fandom o explora las tendencias.")
            } else {
                history.forEach { entry ->
                    val read = entry.chapterProgress.count { it.value >= 0.97f }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onOpenReader(entry.id, entry.chapterIndex) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    "${entry.author} · Cap. ${entry.chapterIndex + 1}" +
                                        (if (read > 0) " · $read leídos" else "") +
                                        (if (entry.scrollRatio > 0.02f) " · ${(entry.scrollRatio * 100).toInt()}%" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("Favoritos")
            if (saved.isEmpty()) {
                EmptyHint("Marca obras con la estrella ⭐ para tenerlas aquí.")
            } else {
                saved.forEach { sw ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onOpenDetail(sw.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFF9A825),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sw.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    "${sw.author} · ${sw.chapters} ${if (sw.chapters == 1) "cap" else "caps"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("Descargas")
            if (downloads.isEmpty()) {
                EmptyHint("Descarga obras para leerlas sin conexión.")
            } else {
                downloads.forEach { dl ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onOpenReader(dl.id, 0) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dl.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    "${dl.chapters.size} ${if (dl.chapters.size == 1) "capítulo" else "capítulos"} · disponible sin conexión",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "AO3 Lector · app de uso personal\nEl contenido pertenece a sus autores en archiveofourown.org",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
