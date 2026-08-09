package net.spin.ao3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.model.CATEGORY_OPTIONS
import net.spin.ao3.data.model.LANGUAGE_OPTIONS
import net.spin.ao3.data.model.RATING_OPTIONS
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.data.model.WARNING_OPTIONS
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.ui.components.TagChip
import net.spin.ao3.ui.components.WorkCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    filters: SearchFilters,
    sort: SortOption,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    var currentFilters by remember { mutableStateOf(filters) }
    var currentSort by remember { mutableStateOf(sort) }
    var results by remember { mutableStateOf<List<WorkSummary>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var noMorePages by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtersNotApplied by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchFirst() {
        loading = true
        error = null
        filtersNotApplied = false
        scope.launch {
            try {
                val result = container.client.search(currentFilters, 1, currentSort)
                results = result.works
                filtersNotApplied = !result.filtersApplied
                page = 1
                noMorePages = false
            } catch (e: Exception) {
                error = e.message ?: "Error de red"
            } finally {
                loading = false
            }
        }
    }

    fun fetchMore() {
        if (loadingMore || loading) return
        loadingMore = true
        scope.launch {
            try {
                val more = container.client.search(currentFilters, page + 1, currentSort).works
                if (more.isEmpty()) {
                    error = null
                    noMorePages = true
                } else {
                    results = results + more
                    page += 1
                }
            } catch (e: Exception) {
                error = e.message ?: "Error de red"
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(currentFilters, currentSort) { fetchFirst() }

    val title = when {
        currentFilters.query.isNotBlank() -> "«${currentFilters.query}»"
        currentFilters.tag != null -> currentFilters.tag!!
        else -> "Explorar"
    }
    val activeFilterCount = activeFilterCount(currentFilters)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    Box {
                        TextButton(onClick = { showFilters = true }) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (activeFilterCount > 0) "Filtros ($activeFilterCount)" else "Filtros")
                        }
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text(currentSort.label)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        currentSort = option
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && results.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { fetchFirst() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                }
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Sin resultados. Prueba a quitar filtros.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ActiveFilterSummary(currentFilters, currentSort)
                }
                if (filtersNotApplied) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                "Los filtros no se aplicaron: la búsqueda no coincide con un fandom/tag de AO3. " +
                                    "Elige un fandom en \"Explorar fandoms\" o en el campo \"Fandom / tag a explorar\" para filtrar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                items(results, key = { it.id }) { work ->
                    WorkCard(work = work, onTagClick = onOpenTag, onClick = { onOpenDetail(work.id) })
                }
                item {
                    if (error != null) {
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    if (loadingMore) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    } else if (noMorePages) {
                        Text(
                            "No hay más resultados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Button(
                            onClick = { fetchMore() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cargar más")
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            initial = currentFilters,
            onDismiss = { showFilters = false },
            onApply = { newFilters ->
                showFilters = false
                currentFilters = newFilters
            },
        )
    }
}

private fun activeFilterCount(f: SearchFilters): Int {
    var n = 0
    if (f.tag != null) n++
    if (f.includeTags.isNotBlank()) n++
    if (f.excludeTags.isNotBlank()) n++
    if (f.rating != null) n++
    n += f.warnings.size
    n += f.categories.size
    if (f.excludeRating != null) n++
    n += f.excludeWarnings.size
    n += f.excludeCategories.size
    if (f.completeOnly) n++
    if (f.crossoverOnly) n++
    if (f.excludeCrossover) n++
    if (f.partOfSeries) n++
    if (f.language != null) n++
    if (f.wordsFrom.isNotBlank()) n++
    if (f.wordsTo.isNotBlank()) n++
    if (f.dateFrom.isNotBlank()) n++
    if (f.dateTo.isNotBlank()) n++
    return n
}

@Composable
private fun ActiveFilterSummary(f: SearchFilters, sort: SortOption) {
    val parts = mutableListOf<String>()
    f.tag?.let { parts.add(it) }
    f.rating?.let { r -> RATING_OPTIONS.firstOrNull { it.id == r }?.let { parts.add("Rating: ${it.label}") } }
    f.warnings.forEach { w -> WARNING_OPTIONS.firstOrNull { it.id == w }?.let { parts.add("⚠ ${it.label}") } }
    f.categories.forEach { c -> CATEGORY_OPTIONS.firstOrNull { it.id == c }?.let { parts.add(it.label) } }
    if (f.completeOnly) parts.add("Completadas")
    if (f.crossoverOnly) parts.add("Solo crossover")
    if (f.excludeCrossover) parts.add("Sin crossovers")
    if (f.partOfSeries) parts.add("Parte de serie")
    f.language?.let { l -> LANGUAGE_OPTIONS.firstOrNull { it.code == l }?.let { parts.add("Idioma: ${it.label}") } }
    if (f.wordsFrom.isNotBlank() || f.wordsTo.isNotBlank()) parts.add("${f.wordsFrom}-${f.wordsTo} palabras")
    if (f.dateFrom.isNotBlank() || f.dateTo.isNotBlank()) parts.add("${f.dateFrom} → ${f.dateTo}")
    if (f.includeTags.isNotBlank()) parts.add("Incluir: ${f.includeTags}")
    if (f.excludeTags.isNotBlank()) parts.add("Excluir: ${f.excludeTags}")
    f.excludeRating?.let { r -> RATING_OPTIONS.firstOrNull { it.id == r }?.let { parts.add("Sin: ${it.label}") } }
    f.excludeWarnings.forEach { w -> WARNING_OPTIONS.firstOrNull { it.id == w }?.let { parts.add("Sin ⚠: ${it.label}") } }
    f.excludeCategories.forEach { c -> CATEGORY_OPTIONS.firstOrNull { it.id == c }?.let { parts.add("Sin: ${it.label}") } }
    if (parts.isEmpty()) {
        Text("Ordenadas por ${sort.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { TagChip(it, MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    initial: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var f by remember { mutableStateOf(initial) }
    // false = Incluir, true = Excluir (edits the exclude_* sets)
    var excludeMode by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Filtros de búsqueda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Los mismos filtros del sidebar de AO3.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = f.tag ?: "",
                onValueChange = { f = f.copy(tag = it.ifBlank { null }) },
                label = { Text("Fandom / tag a explorar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !excludeMode,
                    onClick = { excludeMode = false },
                    label = { Text("Incluir") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = excludeMode,
                    onClick = { excludeMode = true },
                    label = { Text("Excluir") },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (excludeMode) "Eliges lo que se EXCLUYE de los resultados" else "Eliges lo que se INCLUYE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionLabel("Rating")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RATING_OPTIONS.forEach { opt ->
                    val selected = if (excludeMode) f.excludeRating == opt.id else f.rating == opt.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            f = if (excludeMode) {
                                f.copy(excludeRating = if (selected) null else opt.id)
                            } else {
                                f.copy(rating = if (selected) null else opt.id)
                            }
                        },
                        label = { Text(opt.label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Warnings")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WARNING_OPTIONS.forEach { opt ->
                    val sel = if (excludeMode) f.excludeWarnings else f.warnings
                    FilterChip(
                        selected = opt.id in sel,
                        onClick = {
                            f = if (excludeMode) {
                                f.copy(excludeWarnings = toggle(sel, opt.id))
                            } else {
                                f.copy(warnings = toggle(sel, opt.id))
                            }
                        },
                        label = { Text(opt.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Categorías")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CATEGORY_OPTIONS.forEach { opt ->
                    val sel = if (excludeMode) f.excludeCategories else f.categories
                    FilterChip(
                        selected = opt.id in sel,
                        onClick = {
                            f = if (excludeMode) {
                                f.copy(excludeCategories = toggle(sel, opt.id))
                            } else {
                                f.copy(categories = toggle(sel, opt.id))
                            }
                        },
                        label = { Text(opt.label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Idioma")
            var langOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { langOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        f.language?.let { l -> LANGUAGE_OPTIONS.firstOrNull { it.code == l }?.label ?: l } ?: "Cualquier idioma",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = langOpen, onDismissRequest = { langOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Cualquier idioma") },
                        onClick = { f = f.copy(language = null); langOpen = false },
                    )
                    LANGUAGE_OPTIONS.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = { f = f.copy(language = opt.code); langOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Tags (separados por coma; se requieren TODOS)")
            OutlinedTextField(
                value = f.includeTags,
                onValueChange = { f = f.copy(includeTags = it) },
                label = { Text("Incluir tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = f.excludeTags,
                onValueChange = { f = f.copy(excludeTags = it) },
                label = { Text("Excluir tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Solo completadas", style = MaterialTheme.typography.bodyMedium)
                    Text("Oculta las obras sin terminar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.completeOnly, onCheckedChange = { f = f.copy(completeOnly = it) })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Solo crossovers", style = MaterialTheme.typography.bodyMedium)
                    Text("Muestra solo obras con más de un fandom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.crossoverOnly, onCheckedChange = {
                    f = f.copy(crossoverOnly = it, excludeCrossover = if (it) false else f.excludeCrossover)
                })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Excluir crossovers", style = MaterialTheme.typography.bodyMedium)
                    Text("Oculta las obras con más de un fandom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.excludeCrossover, onCheckedChange = {
                    f = f.copy(excludeCrossover = it, crossoverOnly = if (it) false else f.crossoverOnly)
                })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Parte de una serie", style = MaterialTheme.typography.bodyMedium)
                    Text("Solo obras que pertenecen a una serie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.partOfSeries, onCheckedChange = { f = f.copy(partOfSeries = it) })
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Palabras")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = f.wordsFrom,
                    onValueChange = { f = f.copy(wordsFrom = it.filter { c -> c.isDigit() }) },
                    label = { Text("Mín.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.wordsTo,
                    onValueChange = { f = f.copy(wordsTo = it.filter { c -> c.isDigit() }) },
                    label = { Text("Máx.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))

            SectionLabel("Fechas (AAAA-MM-DD)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = f.dateFrom,
                    onValueChange = { f = f.copy(dateFrom = it) },
                    label = { Text("Desde") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.dateTo,
                    onValueChange = { f = f.copy(dateTo = it) },
                    label = { Text("Hasta") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { onApply(SearchFilters(query = f.query)) }) {
                    Text("Limpiar")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(f) }) {
                    Text("Aplicar filtros")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

private fun toggle(set: Set<Int>, id: Int): Set<Int> =
    if (id in set) set - id else set + id
