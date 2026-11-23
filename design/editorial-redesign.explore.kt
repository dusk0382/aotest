@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.charlies.androiddesign.preview.kit.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
val preview = designBoard(
    name = "AO3 Lector — Biblioteca editorial",
    seed = 0xFF243B6BL,
    variant = PaletteVariant.TonalSpot,
    brandFont = BundledFont.Fraunces,
) {
    direction("Azul tinta + hielo") {
        screen("inicio") { EditorialFrame(0) { HomeBoard() } }
        screen("biblioteca") { EditorialFrame(1) { LibraryBoard() } }
        screen("buscar") { EditorialFrame(0, false) { SearchBoard() } }
        screen("ficha") { EditorialFrame(0, false) { DetailBoard() } }
        screen("autor") { EditorialFrame(0, false) { AuthorBoard() } }
        listDetail(
            id = "ajustes",
            detailId = "ajuste-editor",
            list = { open -> SettingsHubBoard(open) },
            detail = { AppearanceBoard() },
            placeholder = { ChoiceBoard("Abrir apariencia") },
        )
        screen("lector") { EditorialFrame(0, false) { ReaderBoard() } }

        overlay("filtros", "buscar") { FilterBoard() }
        overlay("todos-tags", "ficha") { AllTagsBoard() }
        overlay("lector-ajustes", "lector") { ReaderSettingsBoard() }
        overlay("capitulos", "lector") { ChaptersBoard() }
        overlay("traduccion", "lector") { TranslationBoard() }
        overlay("comentario", "ficha") { CommentBoard() }
        overlay("borrar-descarga", "biblioteca") { DeleteBoard() }
        overlay("credenciales", "ajustes") { CredentialsBoard() }
        overlay("backup", "ajustes") { BackupBoard() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorialFrame(selected: Int, navigation: Boolean = true, content: @Composable () -> Unit) {
    var current by remember { mutableIntStateOf(selected) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = navigation && maxWidth >= 600.dp && maxHeight >= 480.dp
        val wide = maxWidth >= 1200.dp
        Row(Modifier.fillMaxSize()) {
            if (rail) {
                WideNavigationRail(state = rememberWideNavigationRailState()) {
                    listOf("home" to "Inicio", "library_books" to "Biblioteca", "settings" to "Ajustes")
                        .forEachIndexed { index, item ->
                            WideNavigationRailItem(
                                railExpanded = wide,
                                selected = current == index,
                                onClick = { current = index },
                                icon = { SymbolIcon(item.first, item.second) },
                                label = { Text(item.second) },
                            )
                        }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (navigation && !rail) {
                        ShortNavigationBar {
                            listOf("home" to "Inicio", "library_books" to "Biblioteca", "settings" to "Ajustes")
                                .forEachIndexed { index, item ->
                                    ShortNavigationBarItem(
                                        selected = current == index,
                                        onClick = { current = index },
                                        icon = { SymbolIcon(item.first, item.second) },
                                        label = { Text(item.second) },
                                        iconPosition = NavigationItemIconPosition.Top,
                                    )
                                }
                        }
                    }
                },
            ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(title: String, subtitle: String? = null, back: Boolean = false) {
    MediumFlexibleTopAppBar(
        title = { Text(title) },
        subtitle = subtitle?.let { { Text(it) } },
        navigationIcon = { if (back) IconButton(onClick = {}) { SymbolIcon("arrow_back", "Volver") } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Primary(label: String) {
    Button(
        onClick = {}, shapes = ButtonDefaults.shapesFor(56.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
        modifier = Modifier.testTag("ad:primary"),
    ) { Text(label, maxLines = 1) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(value: String = "Harry Potter") {
    val state = rememberTextFieldState(value)
    val search = rememberSearchBarState()
    SearchBar(state = search, inputField = {
        SearchBarDefaults.InputField(
            textFieldState = state, searchBarState = search, onSearch = {},
            placeholder = { Text("Título, fandom, autor o tag") },
            leadingIcon = { SymbolIcon("search", "Buscar") },
        )
    })
}

@Composable
private fun WorkRow(title: String, author: String, meta: String, modifier: Modifier = Modifier) {
    ListItem(
        onClick = {}, modifier = modifier,
        supportingContent = { Text("$author\n$meta") },
        trailingContent = { Text("General", style = MaterialTheme.typography.labelMedium) },
    ) { Text(title, style = MaterialTheme.typography.titleLarge) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBoard() {
    BoxWithConstraints(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        val expanded = maxWidth >= 840.dp
        Row(Modifier.fillMaxWidth().padding(if (expanded) 24.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("AO3 Lector", style = MaterialTheme.typography.displaySmall)
                Text("Tu biblioteca personal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchField("Buscar una historia")
                if (!expanded) ContinueHero()
                Heading("Continuar leyendo")
                if (expanded) {
                    WorkRow("Las cartas que dejamos atrás", "MinervaWrites", "Capítulo 8 · hace 2 h")
                    WorkRow("Un atlas de pequeñas estrellas", "NorthPole", "Terminado · ayer")
                } else {
                    WorkRow("Un atlas de pequeñas estrellas", "NorthPole", "Terminado · ayer")
                }
            }
            if (expanded) Column(Modifier.weight(2f)) { ContinueHero() }
        }
    }
}

@Composable
private fun ContinueHero() {
    Surface(shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("42%", style = MaterialTheme.typography.displaySmallEmphasized)
            Text("El jardín de las horas imposibles", style = MaterialTheme.typography.headlineSmall)
            Text("Capítulo 5 de 12 · Elena M. Torres", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearWavyProgressIndicator(progress = { 0.42f }, modifier = Modifier.fillMaxWidth())
            Primary("Continuar capítulo 5")
        }
    }
}

@Composable
private fun LibraryBoard() {
    var tab by remember { mutableIntStateOf(2) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Biblioteca", "Tus obras y capítulos descargados")
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf("Favoritos", "Historial", "Descargas").forEachIndexed { index, label ->
                ToggleButton(
                    checked = tab == index, onCheckedChange = { tab = index },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                ) { Text(label) }
            }
        }
        Heading("Descargas")
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.largeIncreased) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Descargando El jardín…", style = MaterialTheme.typography.titleMedium)
                Text("7 de 12 capítulos")
                LinearWavyProgressIndicator(progress = { 7f / 12f }, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(onClick = {}, shapes = ButtonDefaults.shapesFor(40.dp)) { Text("Pausar") }
            }
        }
        Heading("Disponibles sin conexión")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            listOf("Las cartas que dejamos atrás", "Un atlas de pequeñas estrellas").forEachIndexed { index, title ->
                SegmentedListItem(
                    onClick = {}, shapes = ListItemDefaults.segmentedShapes(index, 2),
                    supportingContent = { Text("${index + 3} capítulos descargados") },
                ) { Text(title) }
            }
        }
        Primary("Leer sin conexión")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBoard() {
    val state = rememberSearchBarState()
    val text = rememberTextFieldState("Harry Potter")
    var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppBarWithSearch(state = state, inputField = {
            SearchBarDefaults.InputField(
                textFieldState = text, searchBarState = state, onSearch = {},
                placeholder = { Text("Buscar obras") }, leadingIcon = { SymbolIcon("search", null) },
            )
        })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("128 obras", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = {}) { SymbolIcon("tune", "Filtros, 2 activos") }
            IconButton(onClick = {}) { SymbolIcon("sort", "Ordenar") }
        }
        Heading("Resultados")
        BoxWithConstraints {
            val columns = if (maxWidth >= 1200.dp) 3 else if (maxWidth >= 840.dp) 2 else 1
            val titles = listOf("El jardín de las horas imposibles", "Las cartas que dejamos atrás", "Un atlas de pequeñas estrellas", "Donde termina el invierno")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                titles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { WorkRow(it, "Autor invitado", "42 000 palabras · 12 capítulos", Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        AnimatedVisibility(loading) { LoadingIndicator(Modifier.size(48.dp)) }
        Primary(if (loading) "Cargando…" else "Cargar más")
        LaunchedEffect(loading) { if (loading) { kotlinx.coroutines.delay(300); loading = false } }
    }
}

@Composable
private fun DetailBoard() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("El jardín de las horas imposibles", "Elena M. Torres", true)
        Text("General · Sin advertencias · Completada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Primary("Leer capítulo 1")
        BoxWithConstraints {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { SummaryBoard() }
                    Column(Modifier.width(320.dp)) { DownloadBoard() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { SummaryBoard(); DownloadBoard() }
            }
        }
    }
}

@Composable
private fun SummaryBoard() {
    Heading("Una historia sobre volver a empezar")
    Text("En una ciudad donde los relojes se detienen al amanecer, Elena encuentra correspondencia dirigida a alguien que todavía no ha nacido.", style = MaterialTheme.typography.bodyLarge)
    Text("Harry Potter · Hermione Granger · Viaje en el tiempo", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Heading("Capítulos")
    repeat(3) { ListItem(onClick = {}, supportingContent = { Text("${(it + 4) * 700} palabras") }) { Text("${it + 1}. El primer reloj") } }
}

@Composable
private fun DownloadBoard() {
    Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Tu copia")
            Text("3 de 12 capítulos disponibles")
            LinearWavyProgressIndicator(progress = { 0.25f }, modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = {}, shapes = ButtonDefaults.shapesFor(40.dp)) { Text("Descargar todo") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorBoard() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text("E", style = MaterialTheme.typography.displaySmall) }
            }
            Column {
                Text("Elena M. Torres", style = MaterialTheme.typography.headlineMedium)
                Text("@elenawrites · 24 obras · desde 2018")
            }
        }
        Text("Escribo sobre memoria, jardines y personas que encuentran su camino. Actualizaciones sin calendario fijo.")
        OutlinedButton(onClick = {}, shapes = ButtonDefaults.shapesFor(40.dp)) { Text("Abrir perfil en AO3") }
        Heading("Obras de Elena")
        WorkRow("El jardín de las horas imposibles", "Elena M. Torres", "12 capítulos · Completada")
        Primary("Explorar sus obras")
    }
}

@Composable
private fun ReaderBoard() {
    Column(Modifier.fillMaxSize()) {
        Header("El jardín de las horas imposibles", "Capítulo 5 de 12", true)
        Column(
            Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("El primer reloj", style = MaterialTheme.typography.headlineMedium)
            Text("La carta llegó al amanecer. Hermione la leyó dos veces antes de guardarla. El viento movía las cortinas y, en la mesa, el té ya estaba frío.", style = MaterialTheme.typography.bodyLarge, )
            Text("—¿Vas a responderle? —preguntó Ron. Ella sonrió sin apartar la vista de la página. Al otro lado de la ventana, el jardín empezaba a cambiar con una luz que no pertenecía a aquella mañana.", style = MaterialTheme.typography.bodyLarge, )
        }
        Primary("Continuar lectura")
    }
}

@Composable
private fun ChoiceBoard(label: String) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Elige una categoría", style = MaterialTheme.typography.headlineSmall)
        Text("Lectura, datos y personalización")
        Primary(label)
    }
}

@Composable
private fun SettingsHubBoard(open: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Ajustes", "Lectura, datos y personalización")
        val entries = listOf("Apariencia" to "palette", "Ajustes del lector" to "menu_book", "Comentarios" to "chat", "Traducciones" to "translate", "Copia de seguridad" to "cloud_upload")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            entries.forEachIndexed { index, entry ->
                SegmentedListItem(
                    onClick = open, shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                    leadingContent = { SymbolIcon(entry.second, null) },
                ) { Text(entry.first) }
            }
        }
        Button(onClick = open, shapes = ButtonDefaults.shapesFor(56.dp), modifier = Modifier.testTag("ad:primary")) { Text("Abrir apariencia") }
    }
}

@Composable
private fun AppearanceBoard() {
    var mode by remember { mutableIntStateOf(0) }
    var dynamic by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Apariencia", "Tema de la interfaz")
        Heading("Modo")
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf("Sistema", "Claro", "Oscuro").forEachIndexed { index, label ->
                ToggleButton(
                    checked = mode == index, onCheckedChange = { mode = index },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                ) { Text(label) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = dynamic, role = Role.Switch, onValueChange = { dynamic = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Colores dinámicos", Modifier.weight(1f))
            Switch(checked = dynamic, onCheckedChange = null)
        }
        Text("Desactivado por defecto. La identidad azul tinta se mantiene salvo que actives Material You.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Primary("Guardar apariencia")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBoard() {
    val fandom = rememberTextFieldState("Harry Potter")
    val words = rememberTextFieldState("40000")
    var include by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Filtros", "Afina esta búsqueda")
        Heading("Fandom o tag")
        OutlinedTextField(state = fandom, label = { Text("Fandom") }, lineLimits = TextFieldLineLimits.SingleLine)
        Heading("Tags sugeridos")
        FilterChip(selected = include, onClick = { include = !include }, label = { Text("Time Travel") }, leadingIcon = { SymbolIcon(if (include) "add" else "close", null) })
        FilterChip(selected = false, onClick = {}, label = { Text("Excluir: Major Character Death") })
        Heading("Extensión mínima")
        OutlinedTextField(state = words, label = { Text("Palabras") }, lineLimits = TextFieldLineLimits.SingleLine)
        Primary("Aplicar filtros")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllTagsBoard() {
    val query = rememberTextFieldState()
    val search = rememberSearchBarState()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Todos los tags")
        SearchBar(state = search, inputField = {
            SearchBarDefaults.InputField(textFieldState = query, searchBarState = search, onSearch = {}, placeholder = { Text("Filtrar tags") })
        })
        Heading("Personajes")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = {}, label = { Text("Hermione Granger") }, modifier = Modifier.testTag("ad:primary"))
            FilterChip(selected = false, onClick = {}, label = { Text("Ron Weasley") })
        }
        Heading("Adicionales")
        FilterChip(selected = false, onClick = {}, label = { Text("Viaje en el tiempo") })
    }
}

@Composable
private fun ReaderSettingsBoard() {
    var theme by remember { mutableIntStateOf(0) }
    val font = rememberSliderState(value = 0.5f)
    val fontSize = 12f + font.value * 20f
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Ajustes del lector", "Vista previa en directo")
        Surface(color = if (theme >= 2) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerLow, contentColor = if (theme >= 2) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.medium) {
            Text("La carta llegó al amanecer. Hermione la leyó dos veces antes de guardarla.", modifier = Modifier.padding(20.dp), fontSize = fontSize.sp)
        }
        Heading("Tema de lectura")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Claro", "Sepia", "Oscuro", "AMOLED").forEachIndexed { index, label -> FilterChip(selected = theme == index, onClick = { theme = index }, label = { Text(label) }) }
        }
        Heading("Tamaño: ${fontSize.toInt()} sp")
        Slider(state = font, onValueChange = { font.value = it })
        Primary("Aplicar ajustes")
    }
}

@Composable
private fun ChaptersBoard() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Capítulos", "El jardín de las horas imposibles")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            repeat(5) { index ->
                SegmentedListItem(
                    onClick = {}, shapes = ListItemDefaults.segmentedShapes(index, 5),
                    supportingContent = { Text(if (index < 4) "Leído" else "42% · descargado") },
                ) { Text("${index + 1}. ${if (index == 0) "El primer reloj" else "La hora prestada"}") }
            }
        }
        Primary("Reanudar capítulo 5")
    }
}

@Composable
private fun TranslationBoard() {
    var provider by remember { mutableIntStateOf(0) }
    var lang by remember { mutableIntStateOf(0) }
    val languages = listOf("Español", "English", "Português", "Français", "Deutsch", "Italiano", "Català", "Galego", "Euskara", "Русский", "中文", "日本語")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Traducir capítulo", "El contenido original no cambia")
        Heading("Proveedor")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = provider == 0, onClick = { provider = 0 }, label = { Text("Google") })
            FilterChip(selected = provider == 1, onClick = { provider = 1 }, label = { Text("IA") })
        }
        Heading("Idioma")
        languages.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { label -> FilterChip(selected = languages.indexOf(label) == lang, onClick = { lang = languages.indexOf(label) }, label = { Text(label) }) } }
        }
        Primary("Traducir al español")
    }
}

@Composable
private fun CommentBoard() {
    val name = rememberTextFieldState("Invitado")
    val body = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Nuevo comentario") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state = name, label = { Text("Nombre") }, lineLimits = TextFieldLineLimits.SingleLine)
            OutlinedTextField(state = body, label = { Text("Comentario") }, modifier = Modifier.height(160.dp))
        } },
        confirmButton = { Primary("Publicar comentario") },
        dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
    )
}

@Composable
private fun DeleteBoard() {
    AlertDialog(
        onDismissRequest = {}, title = { Text("Borrar descarga") },
        text = { Text("Se eliminarán los capítulos disponibles sin conexión. La obra seguirá en favoritos.") },
        confirmButton = { Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), shapes = ButtonDefaults.shapesFor(56.dp), modifier = Modifier.testTag("ad:primary")) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } },
    )
}

@Composable
private fun CredentialsBoard() {
    val baseUrl = rememberTextFieldState("https://api.ejemplo.invalid/v1")
    val key = rememberTextFieldState()
    val model = rememberTextFieldState("modelo-editorial")
    var reveal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Traducciones", "Proveedor compatible con OpenAI")
        OutlinedTextField(state = baseUrl, label = { Text("Base URL") }, lineLimits = TextFieldLineLimits.SingleLine)
        OutlinedSecureTextField(
            state = key, label = { Text("API key") },
            textObfuscationMode = if (reveal) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            trailingIcon = { IconButton(onClick = { reveal = !reveal }) { SymbolIcon(if (reveal) "visibility_off" else "visibility", "Mostrar API key") } },
        )
        OutlinedTextField(state = model, label = { Text("Modelo") }, lineLimits = TextFieldLineLimits.SingleLine)
        Primary("Probar conexión")
    }
}

@Composable
private fun BackupBoard() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header("Copia de seguridad", "Biblioteca, historial y preferencias")
        Text("Exporta un JSON con tus datos. La importación reemplaza el contenido actual y siempre pide confirmación.")
        Primary("Exportar biblioteca")
        OutlinedButton(onClick = {}, shapes = ButtonDefaults.shapesFor(40.dp)) { Text("Importar biblioteca") }
    }
}

