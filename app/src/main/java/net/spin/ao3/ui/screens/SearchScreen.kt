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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.model.CATEGORY_OPTIONS
import net.spin.ao3.data.model.FacetKind
import net.spin.ao3.data.model.FilterFacets
import net.spin.ao3.data.model.FilterOption
import net.spin.ao3.data.model.LANGUAGE_OPTIONS
import net.spin.ao3.data.model.RATING_OPTIONS
import net.spin.ao3.data.model.SearchFilters
import net.spin.ao3.data.model.SortOption
import net.spin.ao3.data.model.WARNING_OPTIONS
import net.spin.ao3.data.model.WorkSummary
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.components.TagChip
import net.spin.ao3.ui.components.WorkCard
import net.spin.ao3.util.usernameFromAuthorUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    filters: SearchFilters,
    sort: SortOption,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
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
    // Sidebar facets (suggested tags with counts) of the current listing.
    var facets by remember { mutableStateOf<FilterFacets?>(null) }
    // Editable copy of the query shown in the search field below the bar.
    var queryDraft by remember(currentFilters.query) { mutableStateOf(currentFilters.query) }
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
                facets = result.facets
                page = 1
                noMorePages = false
            } catch (e: Exception) {
                error = e.message ?: "Error de red"
            } finally {
                loading = false
            }
        }
    }

    /** Applies the text in the search field (item: keeps the results + filters). */
    fun submitQuery() {
        val q = queryDraft.trim()
        if (q == currentFilters.query) {
            fetchFirst()
        } else {
            currentFilters = currentFilters.copy(query = q)
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
                    // The query itself IS the search field: tap it to keep
                    // typing (it stays fully editable), no extra input below.
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = queryDraft,
                        onValueChange = { queryDraft = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitQuery() }),
                        decorationBox = { inner ->
                            Box {
                                if (queryDraft.isBlank() && !focused) {
                                    Text(
                                        "Buscar…",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (queryDraft.isNotBlank()) {
                        IconButton(onClick = { queryDraft = ""; submitQuery() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Limpiar búsqueda",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    }
                },
                actions = {
                    // Filters: Tune icon + badge with the active-filter count.
                    Box {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Filtros",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    activeFilterCount.coerceAtMost(9).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    // Sort: SwapVert icon + dropdown with the current option checked.
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Ordenar: ${currentSort.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = if (option == currentSort) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f)) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            results.isEmpty() -> Box(Modifier.fillMaxSize()) {
                val hasFilters = activeFilterCount(currentFilters) > 0
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Sin resultados",
                    description = "No encontramos obras con esa combinación. Prueba a quitar filtros o a cambiar la búsqueda.",
                    actionLabel = if (hasFilters) "Limpiar filtros" else null,
                    onAction = {
                        currentFilters = SearchFilters(query = currentFilters.query, tag = currentFilters.tag)
                    },
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                            shape = MaterialTheme.shapes.small,
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
                    WorkCard(
                        work = work,
                        onTagClick = onOpenTag,
                        onAuthorClick = usernameFromAuthorUrl(work.authorUrl)?.let { name -> { onOpenAuthor(name) } },
                        onClick = { onOpenDetail(work.id) },
                    )
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
    }
    }

    if (showFilters) {
        FilterSheet(
            initial = currentFilters,
            facets = facets,
            container = container,
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
    if (f.fandomIds.isNotEmpty()) n++
    if (f.characterIds.isNotEmpty()) n++
    if (f.relationshipIds.isNotEmpty()) n++
    if (f.freeformIds.isNotEmpty()) n++
    if (f.excludeFandomIds.isNotEmpty()) n++
    if (f.excludeCharacterIds.isNotEmpty()) n++
    if (f.excludeRelationshipIds.isNotEmpty()) n++
    if (f.excludeFreeformIds.isNotEmpty()) n++
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
    facets: FilterFacets?,
    container: AppContainer,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var f by remember { mutableStateOf(initial) }

    // For a free-text query AO3's /works search page has NO sidebar, so we
    // resolve the query to a canonical tag and load THAT tag page's facets on
    // demand (characters, relationships, freeforms with result counts).
    var querySuggestions by remember { mutableStateOf<FilterFacets?>(null) }
    var suggestionsLoading by remember { mutableStateOf(false) }
    val suggestQuery = initial.tag ?: initial.query.takeIf { it.isNotBlank() }
    LaunchedEffect(suggestQuery) {
        querySuggestions = null
        if (suggestQuery == null) return@LaunchedEffect
        if (facets?.groups?.isNotEmpty() == true) return@LaunchedEffect
        suggestionsLoading = true
        querySuggestions = runCatching { container.client.searchFacets(suggestQuery) }.getOrNull()
        suggestionsLoading = false
    }
    val effectiveFacets = facets?.takeIf { it.groups.isNotEmpty() } ?: querySuggestions

    // Prefer the live sidebar facets (with result counts); fall back to the
    // static AO3 option tables when the listing has no filter sidebar.
    fun withCount(label: String, count: Int) = if (count > 0) "$label ($count)" else label
    val ratingItems: List<FilterOption> =
        effectiveFacets?.groups?.firstOrNull { it.kind == FacetKind.RATING }?.include
            ?.map { FilterOption(it.id.toInt(), withCount(it.label, it.count)) }
            ?: RATING_OPTIONS
    val warningItems: List<FilterOption> =
        effectiveFacets?.groups?.firstOrNull { it.kind == FacetKind.WARNING }?.include
            ?.map { FilterOption(it.id.toInt(), withCount(it.label, it.count)) }
            ?: WARNING_OPTIONS
    val categoryItems: List<FilterOption> =
        effectiveFacets?.groups?.firstOrNull { it.kind == FacetKind.CATEGORY }?.include
            ?.map { FilterOption(it.id.toInt(), withCount(it.label, it.count)) }
            ?: CATEGORY_OPTIONS
    val suggestionGroups = effectiveFacets?.groups
        ?.filter {
            it.kind == FacetKind.CHARACTER || it.kind == FacetKind.RELATIONSHIP ||
                it.kind == FacetKind.FREEFORM || it.kind == FacetKind.FANDOM
        }
        ?.filter { it.include.isNotEmpty() }
        ?: emptyList()

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
                "Los mismos filtros del sidebar de AO3. Puedes incluir y excluir a la vez.",
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

            if (suggestionsLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Cargando tags relacionados…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (suggestionGroups.isNotEmpty()) {
                Text("Sugerencias de tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tags relacionados con esta búsqueda (los mismos del sidebar de AO3). Toca + para incluir o − para excluir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                suggestionGroups.forEach { g ->
                    Text(
                        g.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.include.take(10).forEach { item ->
                            TagIncludeExcludeChip(
                                label = item.label,
                                count = item.count,
                                included = facetIncluded(f, g.kind, item.id),
                                excluded = facetExcluded(f, g.kind, item.id),
                                onInclude = { f = toggleFacet(f, g.kind, item.id, include = true) },
                                onExclude = { f = toggleFacet(f, g.kind, item.id, include = false) },
                            )
                        }
                    }
                    if (g.include.size > 10) {
                        Text(
                            "…y ${g.include.size - 10} más (búscalos con el campo de texto de abajo)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionLabel("Rating")
            IncExSection(
                items = ratingItems,
                included = { id -> f.rating == id },
                excluded = { id -> f.excludeRating == id },
                onToggleInclude = { id -> f = f.copy(rating = if (f.rating == id) null else id) },
                onToggleExclude = { id -> f = f.copy(excludeRating = if (f.excludeRating == id) null else id) },
            )
            Spacer(Modifier.height(10.dp))

            SectionLabel("Warnings")
            IncExSection(
                items = warningItems,
                included = { id -> id in f.warnings },
                excluded = { id -> id in f.excludeWarnings },
                onToggleInclude = { id -> f = f.copy(warnings = toggle(f.warnings, id)) },
                onToggleExclude = { id -> f = f.copy(excludeWarnings = toggle(f.excludeWarnings, id)) },
            )
            Spacer(Modifier.height(10.dp))

            SectionLabel("Categorías")
            IncExSection(
                items = categoryItems,
                included = { id -> id in f.categories },
                excluded = { id -> id in f.excludeCategories },
                onToggleInclude = { id -> f = f.copy(categories = toggle(f.categories, id)) },
                onToggleExclude = { id -> f = f.copy(excludeCategories = toggle(f.excludeCategories, id)) },
            )
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

/** Include row + exclude row for one option section (rating / warnings / categories). */
@Composable
private fun IncExSection(
    items: List<FilterOption>,
    included: (Int) -> Boolean,
    excluded: (Int) -> Boolean,
    onToggleInclude: (Int) -> Unit,
    onToggleExclude: (Int) -> Unit,
) {
    Text("Incluir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { opt ->
            FilterChip(
                selected = included(opt.id),
                onClick = { onToggleInclude(opt.id) },
                label = { Text(opt.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("Excluir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { opt ->
            FilterChip(
                selected = excluded(opt.id),
                onClick = { onToggleExclude(opt.id) },
                label = { Text(opt.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** A suggested tag with two explicit affordances: + include / − exclude. */
@Composable
private fun TagIncludeExcludeChip(
    label: String,
    count: Int,
    included: Boolean,
    excluded: Boolean,
    onInclude: () -> Unit,
    onExclude: () -> Unit,
) {
    val suffix = if (count > 0) " ($count)" else ""
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (included) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (included) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = onInclude,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Incluir", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    (if (included) "✓ " else "") + label + suffix,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (excluded) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (excluded) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = onExclude,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Excluir", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    (if (excluded) "✗ " else "") + label + suffix,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- Facet <-> SearchFilters helpers ---------------------------------------

private fun facetIncluded(f: SearchFilters, kind: FacetKind, id: Long): Boolean = when (kind) {
    FacetKind.FANDOM -> id in f.fandomIds
    FacetKind.CHARACTER -> id in f.characterIds
    FacetKind.RELATIONSHIP -> id in f.relationshipIds
    FacetKind.FREEFORM -> id in f.freeformIds
    else -> false
}

private fun facetExcluded(f: SearchFilters, kind: FacetKind, id: Long): Boolean = when (kind) {
    FacetKind.FANDOM -> id in f.excludeFandomIds
    FacetKind.CHARACTER -> id in f.excludeCharacterIds
    FacetKind.RELATIONSHIP -> id in f.excludeRelationshipIds
    FacetKind.FREEFORM -> id in f.excludeFreeformIds
    else -> false
}

private fun toggleFacet(f: SearchFilters, kind: FacetKind, id: Long, include: Boolean): SearchFilters = when (kind) {
    FacetKind.FANDOM -> if (include) f.copy(fandomIds = toggleL(f.fandomIds, id)) else f.copy(excludeFandomIds = toggleL(f.excludeFandomIds, id))
    FacetKind.CHARACTER -> if (include) f.copy(characterIds = toggleL(f.characterIds, id)) else f.copy(excludeCharacterIds = toggleL(f.excludeCharacterIds, id))
    FacetKind.RELATIONSHIP -> if (include) f.copy(relationshipIds = toggleL(f.relationshipIds, id)) else f.copy(excludeRelationshipIds = toggleL(f.excludeRelationshipIds, id))
    FacetKind.FREEFORM -> if (include) f.copy(freeformIds = toggleL(f.freeformIds, id)) else f.copy(excludeFreeformIds = toggleL(f.excludeFreeformIds, id))
    else -> f
}

private fun toggleL(set: Set<Long>, id: Long): Set<Long> = if (id in set) set - id else set + id

private fun toggle(set: Set<Int>, id: Int): Set<Int> =
    if (id in set) set - id else set + id
