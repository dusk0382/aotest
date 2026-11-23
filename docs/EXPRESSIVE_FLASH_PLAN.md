# AO3 Lector — plan de implementación para modelo de código

## Encargo y estado inicial

Implementar un rediseño completo de todas las pantallas Compose con **Material 3 Expressive**, identidad **azul tinta + hielo**, titulares Fraunces y comportamiento existente intacto. El usuario ha descartado el previewer como gate: **no ejecutar `android-design-preview`, no esperar otro board y no presentar `design/editorial-redesign.explore.kt` como evidencia visual aprobada**. Verificar el resultado en el dispositivo real.

Trabajo ya realizado, no repetir:

- Repo: `/home/spin/Documentos/AO3`; rama `redesign/editorial-20261121-234543`.
- Backup completo y checksums OK: `/home/spin/Documentos/ao3-backups/20261121-234529-pre-editorial/`. Contiene `repository.bundle`, `worktree.patch`, configuración local, archivos no tracked, keystore y `SHA256SUMS`.
- Skill oficial local sin parches `980ff4d45565fea5d77de7cc4a945e7fdd248e08`: `.agents/skills/android-design/` y `.agents/skills/android-design-explore/`, enlaces en `.claude/skills/` y entradas en `skills-lock.json`. No modificar `/home/spin/.agents/skills/android-design`.
- `design/editorial-redesign.explore.kt` es un borrador no aprobado; no se usa como fuente ejecutable ni se reintenta su previewer.
- No se ha modificado código de la app. Conservar cualquier cambio nuevo del usuario; no resetear, stashear ni borrar archivos ajenos.

Stack actual que debe mantenerse: Kotlin 2.4.0, AGP 9.2.1, `compileSdk 37`, `minSdk 23`, BOM `androidx.compose:compose-bom-alpha:2026.06.01`, Compose `1.12.0-beta02`, Material3 `1.5.0-alpha23`. Ya se comprobó que este Material3 contiene `MaterialExpressiveTheme`, `MotionScheme`, barras flexibles, `ShortNavigationBar`, `WideNavigationRail`, `AppBarWithSearch`, `TextFieldState`, `OutlinedSecureTextField`, `LoadingIndicator`, `DropdownMenuPopup`, `DropdownMenuGroup`, `LinearWavyProgressIndicator`, listas expresivas y `MaterialShapes`. **No actualizar BOM, Kotlin, AGP ni SDK.** `SelectableDropdownMenuItem` no existe en alpha23: usar `DropdownMenuItem` + check/radio.

## Sistema visual cerrado

### Paleta: no copiar los colores obsoletos de docs/UI.md

Fuente principal: MaterialKolor 5.0.1, `PaletteStyle.TonalSpot`, `ColorSpec.SpecVersion.SPEC_2025`, contraste del sistema. Material You permanece opt-in y `dynamicColor=false` por defecto.

| Rol | Decisión exacta |
|---|---|
| Primary | Generar desde semilla `#243B6B`. |
| Secondary | `ice.primary`, `ice.onPrimary`, `ice.primaryContainer`, `ice.onPrimaryContainer`, con `ice` generado desde `#4B8296`. |
| Tertiary | `ice.secondary`, `ice.onSecondary`, `ice.secondaryContainer`, `ice.onSecondaryContainer`. No reintroducir ámbar como acento de marca. |
| Surface/background claro | `#F4F6F8`, con `onSurface/onBackground` del esquema base generado. |
| Surface/background oscuro | `#111820`, con `onSurface/onBackground` del esquema base generado. |
| Otros roles | Esquema base generado; pares `onX/X` correctos, superficies sólidas, nada de `copy(alpha=…)` para fondos. |
| Semánticos generados | `success=#237A57`, `favorite=#B83B5E`, `warning=#9B6400`, `info=#315DA8`; asignar `primary/onPrimary` resultantes a los campos existentes `success/onSuccess/favorite/warning/info` sin cambiar la firma de `SemanticColors`. |
| Categorías | Sustituir sólo los valores de `FandomColor` a `#315DA8`, `RatingColor` a `#9B6400`, `WarningColor` a `#B3263C`, `CategoryColor` a `#243B6B`, `RelationshipColor` a `#B83B5E`, `CharacterColor` a `#237A57`, `AdditionalColor` a `#65717B`; conservar nombres y usos. |

Si Material You está activo y API ≥31, usar `dynamicLightColorScheme`/`dynamicDarkColorScheme` sin los overrides de marca. Los colores semánticos siguen generándose con sus semillas y contraste del sistema.

### Ratings y warnings: pares nuevos, no los de la spec anterior

Crear `ui/components/TagPalette.kt`, JVM puro, sin imports Android/Compose. No existe un helper puro equivalente. Firmas exactas:

```kotlin
internal fun ratingTextHex(key: String?, light: Boolean): Long
internal fun ratingSolidHex(key: String?, light: Boolean): Pair<Long, Long>
internal fun warningSolidHex(light: Boolean): Pair<Long, Long>
```

Usar longs ARGB `0xFF……` y estas parejas container/texto; en oscuro invertirlas. El contraste de cada par ya fue calculado:

| Clave | Claro: container / texto | Ratio |
|---|---|---:|
| `general-audiences` | `#C2E7FF` / `#001E30` | 13.16:1 |
| `teen-and-up-audiences` | `#B9F6CA` / `#00210A` | 13.99:1 |
| `mature` | `#FFDEA3` / `#2A1800` | 13.20:1 |
| `explicit` | `#FFDAD6` / `#410002` | 13.26:1 |
| `not-rated` | `#E1E3E6` / `#1A1C1E` | 13.29:1 |
| warning/unknown | `#FFD7DC` / `#4A0711` | 12.04:1 |

`ratingTextHex` usa estos colores de texto claro/oscuro: general `#005B85/#9BD3F0`, teen `#146B45/#8CD9AC`, mature `#855200/#F0C77C`, explicit `#9E2838/#FF9FAA`, not-rated y fallback `#52616C/#BCC7D0`. Con la mezcla opaca `lerp(surface, accent, 0.18f)` y las superficies elegidas, todos superan 4.5:1 (mínimo observado 4.59:1).

En `TagChip.kt`, conservar las firmas públicas `ratingColor`, `ratingSolidColors`, `warningSolidColors`, `TagChipSolid`; cada wrapper obtiene `light = MaterialTheme.colorScheme.surface.luminance() > 0.5f`, llama al helper puro y convierte cada Long con `Color(value)`. Mantener los significados G azul/T verde/M ámbar/E rojo/NR gris y las funciones de categoría; no reutilizar los pares anteriores.

### Tipografía, formas y énfasis

- Google Sans Flex (`gs_regular/medium/semibold/bold`) para Title Medium/Small, Body y Label. Fraunces solamente en los seis Display, seis Headline y `titleLarge`; sus variantes `*Emphasized` usan Fraunces Bold. Inicializar la escala con `Typography(fontFamily = AppSans)` y aplicar los nuevos estilos sobre ella, manteniendo los tamaños M3.
- Crear `AppEditorial = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold), Font(R.font.fraunces_bold, FontWeight.Bold))`. Usar TTF estáticos, nunca `FontVariation` runtime en minSdk 23.
- Shapes: `extraSmall=4.dp`, `small=8.dp`, `medium=12.dp`, `large=16.dp`, `largeIncreased=20.dp`, `extraLarge=28.dp`, `extraLargeIncreased=32.dp`, `extraExtraLarge=48.dp`. Hero de 32 dp; tarjetas de datos 12/20; formas nativas en botones/chips/listas.
- CTA principal relleno 56 dp mediante `ButtonDefaults.shapesFor(56.dp)` y su `contentPaddingFor`; acciones secundarias tonal/outlined 40 dp. No XL en utilidades. Una sola zona saturada por pantalla: el hero o un grupo de descarga activa. Títulos, contenido y progreso no se animan decorativamente.
- `MaterialExpressiveTheme(colorScheme=…, motionScheme=MotionScheme.expressive(), typography=Ao3Typography, shapes=Ao3Shapes)`. Toda animación personalizada usa `MaterialTheme.motionScheme.fast/default/slow SpatialSpec/EffectsSpec`; no `tween()`. Tabs: crossfade corto; push/pop: espacial horizontal; bottom sheets sin fade lateral.
- Márgenes 16 dp compacto, 24 dp desde 600 dp. Texto corrido y formularios 40–60 caracteres/línea, formularios máximo 720 dp, contenido de lectura máximo 720 dp. No estirar botones/campos para llenar una tablet.
- Headings con `Modifier.semantics { heading() }`; fila toggle/selectable completa de ≥48 dp con `Role.Switch`/`Role.Tab`; Switch/Checkbox/RadioButton hijos con callback null. Sin chevrons de navegación ni títulos centrados por costumbre. Etiquetas españolas en `values/strings.xml` y `values-en/strings.xml` cuando ya exista traducción.
- Compacto `<600 dp` o altura `<480 dp`: un panel + `ShortNavigationBar`; 600–839 dp con altura suficiente: rail colapsado; ≥840 dp: rail + feeds de 2 columnas + Ajustes índice/detalle + diálogos; ≥1200 dp: rail expandido + feeds de 3 columnas. Espacio adicional no significa texto más ancho.

## Implementación ordenada y gates por bloque

### 1. Tema, dependencia y fuentes

1. Añadir al catálogo `materialKolor = "5.0.1"` y `material-kolor = { module = "com.materialkolor:material-kolor", version.ref = "materialKolor" }`; añadir `implementation(libs.material.kolor)` a `app/build.gradle.kts`. No añadir `material-kolor-ktx` ni librerías adaptive/navigation.
2. En `Theme.kt`, borrar las paletas baseline privadas `LightColors`, `DarkColors`, `LightSemantic`, `DarkSemantic`, conservando las firmas `AppThemeMode`, `SemanticColors`, `LocalSemanticColors`, `Ao3Theme`. Añadir:
   ```kotlin
   @Composable
   private fun rememberSeedScheme(seed: Color, dark: Boolean, contrast: Double): ColorScheme =
       rememberDynamicColorScheme(
           seedColor = seed, isDark = dark, style = PaletteStyle.TonalSpot,
           specVersion = ColorSpec.SpecVersion.SPEC_2025, contrastLevel = contrast,
       )
   ```
   Usarla para base, ice y los cuatro semánticos, cacheada por Compose. `rememberSystemContrast(): Double` devuelve `UiModeManager.contrast.toDouble()` en API 34+ con fallback de servicio nulo a `0.0`; versiones anteriores `0.0`. Mantener `key(themeMode,dynamicColor)` y recreate de MainActivity.
3. Generar los dos TTF Fraunces desde el upstream fijado, en un venv temporal y sin instalación global:
   ```bash
   FONT_SRC=https://raw.githubusercontent.com/charliesbot/android-design/980ff4d45565fea5d77de7cc4a945e7fdd248e08/app/features/preview/desktop/src/main/resources/fonts
   TMP=$(mktemp -d)
   curl -fsSL "$FONT_SRC/fraunces.ttf" -o "$TMP/fraunces.ttf"
   python3 -m venv "$TMP/venv"
   "$TMP/venv/bin/pip" install fonttools
   "$TMP/venv/bin/fonttools" varLib.instancer "$TMP/fraunces.ttf" wght=600 opsz=32 SOFT=100 WONK=1 --update-name-table -o app/src/main/res/font/fraunces_semibold.ttf
   "$TMP/venv/bin/fonttools" varLib.instancer "$TMP/fraunces.ttf" wght=700 opsz=32 SOFT=100 WONK=1 --update-name-table -o app/src/main/res/font/fraunces_bold.ttf
   curl -fsSL "$FONT_SRC/licenses/fraunces-OFL.txt" -o app/src/main/assets/license_fraunces.txt
   ```
   Poner `splash_background` e `ic_launcher_background` a `#111820`, conservando el icono de libro ámbar.
4. Ejecutar `./gradlew :app:dependencyInsight --dependency org.jetbrains.compose.material3:material3 --configuration debugRuntimeClasspath` y el equivalente `androidx.compose.material3:material3`; revisar también `org.jetbrains.compose.foundation/runtime/ui` en `debugRuntimeClasspath`. Si aparece una segunda implementación Compose en vez de alinearse a `androidx.compose.material3:material3:1.5.0-alpha23`, eliminar MaterialKolor y usar esquemas estáticos exportados de Material Theme Builder con las mismas semillas, Light/Dark × contraste 0/0.5/1. No subir versiones ni conservar dos Compose.
5. Gate: `./gradlew :app:compileDebugKotlin`.

### 2. Shell adaptativo y componentes comunes

1. En `MainActivity.AppRoot`, conservar `nav`, `tab`, `screenStates`, `NavTarget`, `slotKey`, `BackHandler` y llamadas a todas las screens. Envolver el Scaffold en `BoxWithConstraints`: `useRail = showTabs && maxWidth >= 600.dp && maxHeight >= 480.dp`, `railExpanded = maxWidth >= 1200.dp`. Rail en Row al inicio sólo si `useRail`; bottom sólo si `showTabs && !useRail`; nunca ambos. Sustituir CubicBezier/tween por MotionScheme.
2. `CapsuleBottomBar` conserva firma pero usa `ShortNavigationBar` + `ShortNavigationBarItem(iconPosition=NavigationItemIconPosition.Top)`. Añadir en `BottomBar.kt`:
   ```kotlin
   @Composable
   fun <T : BottomBarDestination> PrimaryNavigationRail(
       items: List<T>, selected: T, onSelect: (T) -> Unit,
       railExpanded: Boolean, modifier: Modifier = Modifier,
   )
   ```
   Usa `WideNavigationRail(state=rememberWideNavigationRailState())` y `WideNavigationRailItem(railExpanded=railExpanded,…)`, mismos tres `AppTab`.
3. Crear `ui/components/AdaptiveSheet.kt`:
   ```kotlin
   @Composable
   fun AdaptiveSheet(
       onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
       content: @Composable ColumnScope.() -> Unit,
   )
   ```
   `BoxWithConstraints`: si `maxWidth < 840.dp || maxHeight < 480.dp`, ModalBottomSheet expandida con drag handle; si no, Dialog `usePlatformDefaultWidth=false`, Surface extraLarge, `fillMaxWidth(0.9f).widthIn(max=720.dp).fillMaxHeight(0.92f)`, Column con el scroll del caller. Usarla en las cinco hojas existentes: AllTags, FilterSheet y las tres de Reader. No cambiar flags, drafts ni callbacks.
4. `WorkCard`: conservar firma, memoización y callbacks. Sustituir Card tonal por fila Surface sólida, título `titleLarge` hasta 3 líneas, autor clicable `onSurfaceVariant`, rating/warning sólidos, tags neutros/borde, resumen Body Medium 4 líneas y una sola fila de stats tabulares sin tarjeta interna ni divisores de celda. `+N más` abre la misma AdaptiveSheet. Para TagChip clickable usar FilterChip con `FilterChipDefaults.shapes()` y target 48 dp; tags no clickables pueden quedar en Surface sólida, sin wash alpha.
5. `EmptyState`, sin cambiar firma: quitar círculo grande, icono 24 dp, `headlineSmall`, descripción ≤60 caracteres, FilledButton 56 sólo con acción. `compact=true` inline; default centrado ≤560 dp. Reusarlo en errores ad hoc conservando mensaje real y retry. Sustituir spinners iniciales/cargar más por `LoadingIndicator`; `ContainedLoadingIndicator` sólo para espera corta contenida. Progreso determinado de descargas/exportaciones puede ser `LinearWavyProgressIndicator(progress={done/total})`; progreso de lectura queda lineal y calmado.
6. Actualizar los cuatro previews existentes con datos largos y un caso dark/fontScale=2. No añadir `testTag("ad:primary")` a producción: el proyecto no tiene UI tests que lo consuman.
7. Gate: `./gradlew :app:compileDebugKotlin`.

### 3. Inicio y Biblioteca

**Inicio (`HomeScreen`)**: conservar `history`, `query`, `homeScroll`, `online`, snackbar/Deshacer y firmas. Orden: display `AO3 Lector` + `Tu biblioteca personal`, buscador, offline compacto, sección Continuar. Primer HistoryEntry usa:
```kotlin
@Composable private fun ContinueReadingHero(
    entry: Store.HistoryEntry, onOpen: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit, modifier: Modifier = Modifier,
)
```
Hero `primaryContainer/onPrimaryContainer`, porcentaje `displaySmallEmphasized`, título `headlineSmall`, autor/capítulo y `LinearWavyProgressIndicator` para `(chapterProgress[chapterIndex] ?: scrollRatio).coerceIn(0f,1f)`. Filled 56 `Continuar capítulo N` llama `onOpenReader(id,chapterIndex)`; quitar sigue usando IconButton secundario y mismo Undo. Resto: `ContinueReadingRow` plano, sin círculos/chevrons, título hasta 2 líneas. Sin historial no hay hero vacío y búsqueda queda como acción principal.

Usar SIEMPRE una sola LazyColumn con `homeScroll`, nunca otra LazyColumn vertical anidada. ≥840: item cabecera Row con título+buscador `weight(3f)` y hero `weight(2f)`; resto en filas de dos con key primer ID/Spacer. Compacto: un item por entrada y sólo elementos visibles.

**Biblioteca (`LibraryScreen`)**: `MediumFlexibleTopAppBar` con título y recuento del tab. `LibraryTabSelector` pasa a Row scrollable de ToggleButton con `connectedLeading/Middle/TrailingButtonShapes`, mismos índices y estados. `LibraryRowCard` conserva firma/trailing slot: fila plana, título/subtítulo hasta 2 líneas, progreso y acciones 48 dp. Downloads expande capítulos con `AnimatedVisibility`; cola activa es el único grupo `secondaryContainer`, pausa/final vuelve a surface y conserva progreso real. No tocar Queue, Store, exporters ni claves de estado.

≥840/≥1200: obras en filas de 2/3 celdas dentro de los LazyColumn existentes, `rowCount=(list.size+columns-1)/columns`, key primer ID, `weight(1f)` y Spacer. Los capítulos expandidos permanecen dentro de su obra. Mantener `refreshTick`, `SaveableStateProvider` y LazyListState. `Leer sin conexión`/`Explorar obras` puede ser CTA 56 sólo cuando la lista correspondiente está vacía o existe capítulo real que abrir; no crear CTA sin destino.

Gate: compileDebugKotlin y comprobar en código que las ramas compacta/ancha comparten datos/callbacks, no listas duplicadas.

### 4. Búsqueda, filtros, autor y ficha

**SearchScreen**: conservar filtros/sort actuales, `searchKey`, ScrollStore, `listState`, página, restorePending, dedupe, `withTimeoutOrNull(3_000)`, `fetchFirst/fetchMore`. Reemplazar campo casero por `AppBarWithSearch` + `SearchBarDefaults.InputField(textFieldState=…, searchBarState=…, onSearch=…)`, back/filtros/ordenar como únicas acciones. Resultados con recuento real y ActiveFilterSummary; rows de 1/2/3 WorkCard por breakpoint conservando LazyListState y claves. Cargar más tonal 40, LoadingIndicator, EmptyState real. El contenido/título de cada obra es la acción dominante; no llenar toda la pantalla de botones rellenos.

**FilterSheet**: conservar `initial`, facets, draft, onApply/reset/canónicos. Orden: texto/fandom/tag; include/exclude; rating/warnings/categories/language; avanzados; switches; palabras/fechas. CollapsibleSection con heading+AnimatedVisibility. Include usa secondaryContainer; exclude errorContainer/onErrorContainer sin cambiar signo/tag. FlowRow con gaps 8 dp (no FlexBox de Compose 1.13). Aplicar Filled 56 fijo al final; Limpiar tonal 40; a 200% se apilan. ≥840 usa Dialog de AdaptiveSheet.

**Autor**: avatar en `MaterialShapes.Cookie9Sided` o inicial `primaryContainer/onPrimaryContainer`, displayName Headline Medium, username/pseuds/bio ≤60 caracteres, stats sin círculos. No extraer color de la imagen. `Abrir perfil en AO3` outlined. Obras usa la cuadrícula común y conserva cargas independientes perfil/Works, prefetch página 2, `restoredPage`, `listState`, `retryTick` y errores separados.

**Ficha (`WorkDetailScreen`)**: `MediumFlexibleTopAppBar` con `detail?.summary?.title ?: "Obra"`, up y favorito como única acción esencial; eliminar título duplicado del body. Mantener autor/tag links, rating/warnings, offline, pull-refresh, `listState`, descargas y comments. CTA Filled 56 `Leer capítulo 1` sólo si `chapters.isNotEmpty()` y llama `onOpenChapter(chapters.first().index)`; sin capítulos, disabled + texto explicativo. No inventar reanudación ni cambiar índice guardado. Descargar/exportar/kudos/bookmark/checkUpdates mantienen callbacks existentes como acciones secundarias. Summary expand/collapse con AnimatedVisibility; tags con heading; ChapterRow y DownloadActionsRow conservan índice/estado/callbacks.

Contenido ≤960 dp, forms/metadatos ≤720 dp; ≥840 cabecera/acciones en dos columnas, botones sin estirar. Los cinco AlertDialog existentes (borrar historial, borrar descarga, restaurar backup, borrar capítulo, comentario/respuesta) conservan flujo. Destrucción/restauración Filled error/onError; publicar Filled primary/onPrimary; cancelar TextButton dentro del grupo de acciones del diálogo.

Gate: `./gradlew :app:compileDebugKotlin`.

### 5. Ajustes: hub y cinco páginas

1. `SettingsHub`: MediumFlexibleTopAppBar `Ajustes` + `Lectura, datos y personalización`; cinco `SegmentedListItem` con `ListItemDefaults.segmentedShapes(index,count)` y `SegmentedGap`, sin chevrons ni cinco tarjetas saturadas.
2. `SettingsScreen`: compacto mantiene hub↔página y BackHandler actual. ≥840 con altura ≥480: Row, hub fijo 320 dp + outlineVariant + página; `page==null` muestra EmptyState `Elige una categoría`. `SettingsPageScaffold` recibe `showBack:Boolean=true`; las cinco llamadas pasan `showBack=!twoPane`. Misma `page` en todos los tamaños.
3. `SettingsSection` pasa de Surface por sección a Column con heading y gaps 12/24. Sólo credenciales IA y aviso de backup llevan container outline. Todos los formularios máximo 720 dp.
4. Apariencia: Sistema/Claro/Oscuro ToggleButtons conectados, guardado existente; Material You en fila completa 48 dp `toggleable(role=Role.Switch)`, Switch hijo null, default false y recreate intactos.
5. Ajustes del Lector: añadir arriba el top-level existente `net.spin.ao3.ui.reader.ReaderPreview(theme,fontSize,lineHeight,serif,margins)` enlazado al estado real. No cambiar guardados ni preferencias. Los cuatro temas muestran previews con `readerPalette()` y permanecen exactamente: LIGHT `#ffffff/#1c1c1e/#2F6B35`; SEPIA `#f6edd9/#433422/#4F7A3A`; DARK `#1b1b1f/#d6d3d0/#9AD8A6`; BLACK `#000000/#d4d4d4/#8FCF9E`. Serif/Sans del lector es independiente de Fraunces.
6. Comentarios: guest name/email separados, Filled 56 `Guardar datos`, mismas prefs y snackbar. Traducciones: provider ToggleButtons, AnimatedVisibility para Base URL/API key/model, API key `OutlinedSecureTextField`, test con LoadingIndicator y la misma corrutina/validación. Backup: Exportar Filled 56, Importar outlined con advertencia, mismo SAF/JSON/confirmación destructiva.
7. Gate: compileDebugKotlin; cambiar tema/dynamic en dispositivo no debe dejar colores del esquema anterior.

### 6. Reader: sólo chrome, nunca el motor

Leer `docs/READER_SCREEN.md` §8 antes de editar. No tocar `htmlToLines`, `blockToRows`, `packPagesToViewport`, `fits`, `linesToAnnotated`, `pagePlainText`, ReaderFind, ReaderWebViewHost, contentKey, pendingScroll, joins ni guardado/progreso. Mantener root `background(readerBgColor(theme))` + `windowInsetsPadding(WindowInsets.displayCutout)` y chrome overlay puro: barras no reservan espacio ni reempaquetan. Contenido WebView/paginado puede quedar centrado con ancho máximo 720 dp, medido por el viewport actual; no cambiar CSS/pipeline para lograrlo.

- Top: Surface opaca del reader theme, up/obra/search/more 48 dp, `AnimatedVisibility(chromeVisible)` con MotionScheme y sin espacio reservado. Autor/capítulo abre ficha; search mantiene clearSearch; overflow conserva Traducir/Tema/TTS/Ajustes.
- Reemplazar alpha de fondos/textos secundarios/disabled por colores sólidos `lerp(readerBgColor(theme), readerFgColor(theme), factor)`. Hint usa inverseSurface/inverseOnSurface. No alterar los hex de readerPalette ni readerCss.
- Buscar conserva `findActive/findCount/searchIndex`, JavaScript y paginación; InputField en segunda franja, 0/0 correcto, prev/next disabled donde toque. TTS conserva pause/resume/stop/startTts/auto-avance: Pausar/Reanudar tonal, Detener estándar, sin acciones flotantes.
- Bottom conserva ReaderProgressBar/Label, prev/next y chapter picker. Offline/Original/Traducción conservan estado. Loading/error/traducción/Cancelar usan colores reader y callbacks actuales.
- Tres hojas usan AdaptiveSheet. Ajustes: modo aplica inmediato, demás draft+Aplicar/Cancelar, ReaderPreview y slider TTS actuales. Capítulos: current/read/downloaded/progress y goToChapter. Traducción: provider/idioma/aviso IA sin config. Aplicar accesible al final con scroll; cancelar overlay no cancela descarga/traducción implícitamente.

Gate obligatorio:
```bash
./gradlew :app:testDebugUnitTest \
  --tests "net.spin.ao3.util.ReaderPagingTest" \
  --tests "net.spin.ao3.util.ReaderSearchTest" \
  --tests "net.spin.ao3.ui.screens.ReaderRatioTest"
./gradlew :app:compileDebugKotlin
```

### 7. Inputs y menús: cutover completo

Imports Foundation necesarios: `androidx.compose.foundation.text.input.rememberTextFieldState`, `TextFieldLineLimits`, `TextObfuscationMode`, `snapshotFlow` desde runtime.

1. `AutocompleteTagField` conserva firma `value:String/onValueChange` y API de chips/sugerencias. Internamente `TextFieldState(value)`; `LaunchedEffect(value)` usa `setTextAndPlaceCursorAtEnd(value)` sólo si difiere, y `snapshotFlow { state.text.toString() }` llama `onValueChange` sólo si difiere. Mantener debounce 300 ms, cancelación y selección. Sugerencias en Surface 12 dp con SegmentedListItem/48 dp y LoadingIndicator pequeño. No duplicar requests.
2. Home/All Tags: SearchBar colapsado sin pane de sugerencias inexistente. Search: AppBarWithSearch. Reader: InputField dentro de su chrome, no fullscreen search. Todos usan TextFieldState + SearchBarDefaults.InputField. `submitQuery`, trim/sort, ScrollStore y JavaScript reciben los mismos strings.
3. CommentsPage, TranslationPage, comentario/respuesta en WorkDetail, wordsFrom/To, fechas y campos avanzados de FilterSheet usan `rememberSaveable(saver=TextFieldState.Saver) { TextFieldState(initialValue) }`. Leer `.text.toString()` sólo en validar/guardar/aplicar. Números conservan `KeyboardType.Number`, single-line `TextFieldLineLimits.SingleLine`, comentario multilinea y validaciones actuales. API key usa `OutlinedSecureTextField`; icono actual alterna `TextObfuscationMode.Visible/Hidden`, nunca PasswordVisualTransformation ni logs.
4. Sustituir los seis `DropdownMenu`: orden Search, idioma filtros, selector capítulo comentarios, acciones por capítulo, exportación completa y overflow Reader. Usar `DropdownMenuPopup` + `DropdownMenuGroup` + `MenuDefaults` shapes. Opción actual con `DropdownMenuItem` + check/radio, no `SelectableDropdownMenuItem`. Sin wrappers de compatibilidad ni ambos menús.
5. Antes de modificar símbolos exportados/firmas, usar LSP references. Las firmas previstas aquí conservan APIs salvo nuevas privadas/locales explícitas; no dejar aliases ni imports viejos muertos.
6. Gate final de código:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```

## Prueba nueva de contraste

Crear `app/src/test/java/net/spin/ao3/ui/components/TagPaletteTest.kt` (JUnit4 JVM, sin Android/mocks). Helpers privados del test para luminancia sRGB y ratio. Para cada una de las seis parejas sólidas y ambos `light`/`dark`, exigir `contrastRatio(container,text) >= 4.5`; para `ratingTextHex`, mezclar opacamente surface `#F4F6F8`/`#111820` con 18% del acento y exigir ratio ≥4.5 para las cinco claves y fallback en ambos modos. Incluir claves desconocidas para asegurar warning/fallback, no comprobar sólo que el código existe. Debe detectar una futura combinación ilegible, no repetir literales sin evaluar su efecto visible.

Ejecutar:
```bash
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.ui.components.TagPaletteTest"
```

## Verificación real obligatoria

Dispositivo conocido: M2006C3LG, Android 10, serie `DAWSAICMVKAQGQS8`, 720×1600 px, densidad 320, `font_scale 1.0`. Antes de instalar: `adb devices`, `adb shell pm path net.spin.ao3`, `wm size`, `wm density` y font_scale. Si el paquete ahora existe, hacer un nuevo tar `run-as net.spin.ao3` de `files shared_prefs` en una ruta nueva con umask 077. Si `run-as` falla, detenerse y exportar desde Ajustes → Copia de seguridad; no desinstalar ni perder datos.

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug`; luego `adb -s DAWSAICMVKAQGQS8 install -r app/build/outputs/apk/debug/app-debug.apk` y `adb shell am start -n net.spin.ao3/.MainActivity`.
2. Usar `uiautomator dump` para localizar controles; nunca taps a ciegas. Guardar y leer PNG reales en `.design-review/editorial/`, nombres pantalla/estado/viewport. No afirmar calidad visual sólo por código.
3. Con Search `Harry Potter`, elegir una obra General si está disponible (si no, otra no explícita) y usar la app real para abrir Tags/Autor/Ficha, marcar favorito, abrir capítulo/volver, generar Continue/History y descargar UN capítulo. No modificar parser/red/Cloudflare para obtener capturas. Si AO3 no responde, capturar el estado de error real y marcar estados no verificados explícitamente.
4. Capturar y revisar:
   - Home light/dark, hero, lista, vacío y offline.
   - Library Favorites/History/Downloads, cola activa/final, expansiones, diálogos y vacío.
   - Search resultados, edición, filtros include+exclude/rangos, sort, loading/paginación y error real.
   - Detail título/tags/resumen/leer/descargas/capítulos/comentario/menús/borrado.
   - Author avatar/inicial, bio/stats/obras/cargar más.
   - Settings hub y las cinco páginas; provider condicional, API key oculta/visible, export/import y confirmación.
   - Reader scroll y paginado, chrome visible/oculto, búsqueda N/M, capítulos, cuatro temas en preview, ajustes, traducción sin afirmar llamada real, TTS si inicializa, offline/error real.
5. Cada captura: una acción principal clara, nada blanco-sobre-blanco, heading semántico, filas toggles ≥48 dp, labels sin recortes, acciones secundarias localizables y paleta azul/petróleo sin el violeta anterior. Back conserva query/scroll/stack.
6. Texto 200%: `adb -s DAWSAICMVKAQGQS8 shell settings put system font_scale 2.0`; probar Home, tabs Library, Settings backup/traducción, Detail, FilterSheet, ReaderSettings y comentario. Botones se reordenan/apilan y el scroll llega a la última acción. En cleanup garantizado restaurar `font_scale 1.0`.
7. Compact height: `wm size 1600x720`, densidad 320 = 800×360 dp, un panel y ShortNavigationBar. Expanded: `1920x2560`, densidad 320 = 960×1280 dp, rail, feeds 2 columnas, Settings dos paneles, diálogos acotados. Large: `2560x1600` = 1280×800 dp, rail expandido/feeds amplios sin líneas infinitas. Restaurar los valores originales de tamaño/densidad en un bloque finally; `wm size reset`/`wm density reset` sólo si eran los defaults originales.
8. Máximo dos rondas de corrección visual: corregir el mayor desajuste y recapturar. Registrar cualquier estado no alcanzable como `calidad visual no verificada`, sin sustituirlo por mocks ni capturas del board fallido.
9. Final: `git diff --check` y `./gradlew :app:assembleRelease` para R8/recursos. Sin bump de versión, release, publicación ni firma nueva. Documentar la especificación resultante y la crónica como exige el repo; eliminar herramientas temporales de fuentes, no el backup ni las skills oficiales.

## Anclajes prioritarios

- `app/src/main/java/net/spin/ao3/ui/theme/Theme.kt` — reemplazo completo de baseline/tokens y `MaterialExpressiveTheme`.
- `app/src/main/java/net/spin/ao3/MainActivity.kt:66-244` — shell/nav/AnimatedContent y `SaveableStateProvider`; no reescribir navegación.
- `app/src/main/java/net/spin/ao3/ui/screens/ReaderScreen.kt:950-1735` — chrome/hojas; invariants en `docs/READER_SCREEN.md` §8.
- `app/src/main/java/net/spin/ao3/ui/screens/SearchScreen.kt:133-604,667-1037` — query, filtros, scroll restoration y autocomplete.
- `app/src/main/java/net/spin/ao3/ui/components/TagChip.kt` — categorías y wrappers de ratings; extraer pares puros a `TagPalette.kt` sin cambiar firmas públicas.
