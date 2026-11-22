# Ejecución del plan `EXPRESSIVE_FLASH_PLAN.md`

Crónica técnica para el modelo que redactó el plan. Todo lo que sigue ocurrió en
la rama `redesign/editorial-20261121-234543` del repo canónico
`/home/spin/Documentos/AO3`. Fecha: 2026-11-22.

## Resumen ejecutivo

Se ejecutaron los siete bloques del plan y se pasaron todos los gates de código.
El rediseño ya está cableado: tema Expressive azul tinta + hielo, shell
adaptativo con rail, pantallas y componentes convertidos, menús e inputs
migrados. **La verificación en dispositivo real NO se pudo ejecutar**: no había
ningún dispositivo conectado (`adb devices` devuelve lista vacía; el
`DAWSAICMVKAQGQS8` del plan no está presente). Por tanto, toda la sección de
capturas, textos al 200 %, viewports compact/expanded/large y correcciones
visuales queda marcada como **calidad visual no verificada**, sin mocks ni
capturas del board fallido.

El único punto del plan que se resolvió de forma distinta a la literal fue la
dependencia MaterialKolor: se añadió, se comprobó el gate de dependencias, se
detectó el conflicto que el propio plan anticipaba y se retiró. Está documentado
abajo con el procedimiento de regeneración.

## Cambio de rumbo obligado: MaterialKolor → esquemas estáticos

### Qué pasó

1. Añadí `materialKolor = "5.0.1"` a `gradle/libs.versions.toml`,
   `material-kolor` al catálogo e `implementation(libs.material.kolor)` a
   `app/build.gradle.kts`.
2. Ejecuté el gate del paso 4:
   `./gradlew :app:dependencyInsight --dependency org.jetbrains.compose.material3:material3 --configuration debugRuntimeClasspath`.
3. El resultado fue exactamente el caso que el plan describía como prohibido:

   ```
   org.jetbrains.compose.material3:material3:1.12.0-alpha03
   \--- com.materialkolor:material-kolor-android:5.0.1
   ```

   Junto a `androidx.compose.material3:material3:1.5.0-alpha23` (BOM
   `compose-bom-alpha:2026.06.01`). El árbol completo mostró también una segunda
   familia `org.jetbrains.compose.{foundation,runtime,ui}:1.12.0`. Had dos
   implementaciones Compose en el mismo classpath.

4. Apliqué la salida del plan: **eliminé MaterialKolor** del catálogo y del
   módulo (sin subir BOM, Kotlin, AGP ni SDK) y generé esquemas estáticos.

### Cómo se generaron los esquemas

Con la librería subyacente `material-color-utilities` 5.0.1 (la misma que usa
MaterialKolor), en un directorio temporal, sin instalar nada globalmente:

- `SchemeTonalSpot(Hct.fromInt(seed), isDark, contrast, ColorSpec.SpecVersion.SPEC_2025, Platform.PHONE)`.
- Se leyeron los 49 roles con `MaterialDynamicColors` + `scheme.getArgb(...)`.
- Se exportaron Light/Dark × contraste `0 / 0.5 / 1` para seis semillas:
  marca `#243B6B`, hielo `#4B8296`, success `#237A57`, favorite `#B83B5E`,
  warning `#9B6400`, info `#315DA8`.
- Resultado: `app/src/main/java/net/spin/ao3/ui/theme/ColorSchemes.kt`
  (36 esquemas `internal val`). Los esquemas Brand guardan los 49 roles; Ice
  guarda 8 (primary/onPrimary/primaryContainer/onPrimaryContainer y los cuatro
  de secondary); los semánticos guardan primary/onPrimary.

Para regenerarlos hace falta repetir ese procedimiento; el archivo lleva una
advertencia «NO editar a mano». Nota para el próximo modelo: si se actualiza el
BOM a una versión donde MaterialKolor alinee con `androidx.compose.material3`,
se podría volver a `rememberDynamicColorScheme`; hasta entonces no.

### Consecuencias en Theme.kt

`Theme.kt` ya no importa MaterialKolor. Mantiene las firmas públicas
(`AppThemeMode`, `SemanticColors`, `LocalSemanticColors`, `Ao3Theme`) y añade:

- `rememberSystemContrast()`: `UiModeManager.contrast` en API 34+, `0.0` si el
  servicio es null o en API anterior.
- `contrastBucket(Double)`: ajusta el contraste continuo al cubo exportado
  (`<0.25 → 0`, `<0.75 → 0.5`, resto → 1).
- Seleccionadores `brandScheme/iceScheme/successScheme/favoriteScheme/
  warningScheme/infoScheme(dark, bucket)`.
- Secondary = `ice.primary` … `ice.onPrimaryContainer`; Tertiary =
  `ice.secondary` … `ice.onSecondaryContainer`.
- `surface` y `background` sólidos `#F4F6F8` / `#111820`; `onSurface` y
  `onBackground` del esquema Brand generado.
- Semánticos: `success/onSuccess` del esquema de `#237A57`, etc.
- Material You (`dynamicColor=true`, API ≥31) usa `dynamicLight/DarkColorScheme`
  sin overrides de marca; los semánticos se siguen generando con sus semillas.
- `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive(),
  typography = Ao3Typography, shapes = Ao3Shapes)` con `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

`key(themeMode, dynamicColor)` y `activity.recreate()` de `MainActivity` se
conservaron intactos.

## Sistema visual aplicado

- **Tipografía**: `Typography(fontFamily = AppSans).copy(...)`. Fraunces en
  displayLarge/Medium/Small, headlineLarge/Medium/Small, titleLarge y sus seis
  variantes `*Emphasized` (Bold). Google Sans Flex en el resto.
  `AppEditorial = FontFamily(fraunces_semibold @600, fraunces_bold @700)`.
- **Fuentes**: TTFs estáticos instanciados con `fonttools varLib.instancer`
  (sin `--update-name-table`: la fuente no trae valores en la tabla STAT y ese
  flag falla con `Cannot find Axis Values {'opsz': 32.0}`; el resultado es
  idéntico para el caso de uso). Licencia OFL en `assets/license_fraunces.txt`.
  `splash_background` e `ic_launcher_background` → `#111820`; icono de libro
  ámbar conservado.
- **Formas** (`Ao3Shapes`): 4/8/12/16/20/28/32/48 dp.
- **Tokens puros**: `ui/components/TagPalette.kt` con `ratingTextHex`,
  `ratingSolidHex`, `warningSolidHex` (longs ARGB, JVM puro). `TagChip.kt`
  delegó en él y actualizó las siete categorías a los hex del plan. Los chips
  clicables usan `FilterChip` con `FilterChipDefaults.shapes()` y 48 dp.
- **Botones**: `ui/components/Expressive.kt` aporta `PrimaryCtaButton` (56 dp,
  `ButtonDefaults.shapesFor/contentPaddingFor`), `SecondaryTonalButton` y
  `SecondaryOutlinedButton` (40 dp). Se usaron en todos los CTAs nuevos.
- **Diálogos/hojas**: `ui/components/AdaptiveSheet.kt` (BoxWithConstraints;
  ModalBottomSheet <840 dp o <480 dp de altura, si no Dialog acotado 0.9×720 dp
  y 0.92 de altura). Se aplicó en AllTags de WorkCard, FilterSheet y las tres
  hojas del lector.

## Bloques ejecutados

### Bloque 1 — tema, dependencia y fuentes
Ver el cambio de rumbo arriba. Gate: `compileDebugKotlin` +
`TagPaletteTest` en verde. Test nuevo
`app/src/test/java/net/spin/ao3/ui/components/TagPaletteTest.kt`: calcula
luminancia sRGB y ratio, exige ≥4.5:1 para las seis parejas sólidas en claro y
oscuro y para `ratingTextHex` mezclado 18 % sobre `#F4F6F8`/`#111820`, con
claves desconocidas y fallback.

### Bloque 2 — shell y componentes
- `MainActivity.kt`: `BoxWithConstraints`; `useRail = showTabs && maxWidth>=600.dp && maxHeight>=480.dp`, `railExpanded = maxWidth>=1200.dp`. Rail en un `Row` al inicio; bottom bar sólo si `showTabs && !useRail`. `AnimatedContent` usa `MaterialTheme.motionScheme.defaultEffectsSpec/defaultSpatialSpec` (crossfade para tabs, espacial horizontal para push/pop). Se eliminaron `CubicBezierEasing` y `tween`.
- `BottomBar.kt`: `CapsuleBottomBar` conserva firma pero usa `ShortNavigationBar`/`ShortNavigationBarItem(iconPosition=Top)`. Nuevo `PrimaryNavigationRail` con `WideNavigationRail`/`WideNavigationRailItem` y `rememberWideNavigationRailState`.
- `WorkCard.kt`: fila `Surface` sólida, `titleLarge` 3 líneas, autor `onSurfaceVariant`, rating/warning sólidos, tags neutros/borde, resumen 4 líneas, una sola fila de stats sin tarjeta interior ni divisores, `+N más` → `AdaptiveSheet`.
- `EmptyState.kt`: icono 24 dp sin círculo, `headlineSmall`, descripción ≤3 líneas, `PrimaryCtaButton`, centrado ≤560 dp o inline.

### Bloque 3 — Inicio y Biblioteca
- `HomeScreen`: una sola `LazyColumn` con `homeScroll`. Cabecera con `AO3 Lector` (displaySmall) + `Tu biblioteca personal`, `SearchBarDefaults.InputField`, offline compacto. Primer `HistoryEntry` usa `ContinueReadingHero(entry, onOpen, onRemove, modifier)` con `primaryContainer`, porcentaje `displaySmallEmphasized`, `headlineSmall`, `LinearWavyProgressIndicator` y CTA 56 `Continuar capítulo N`. Resto en filas planas. ≥840 dp: cabecera `weight(3f)` + hero `weight(2f)` y filas de dos con key del primer ID. Undo con Snackbar conservado (corregido para capturar la entrada antes de borrarla).
- `LibraryScreen`: `MediumFlexibleTopAppBar` con recuento; `LibraryTabSelector` con tres `ToggleButton` conectados (alpha23 no trae `connectedLeading/Middle/TrailingButtonShapes`; se construyeron con `ToggleButtonDefaults.shapes()` y `RoundedCornerShape`). `LibraryRowCard` conserva firma/trailing, título 2 líneas. Cola activa es el único grupo `secondaryContainer`; pausa/final vuelve a `surface` con progreso real. Cuadrículas de 2/3 celdas con `chunked`, key del primer ID, `weight(1f)` y `Spacer`. `refreshTick`, `SaveableStateProvider` y `LazyListState` sin tocar.

### Bloque 4 — Búsqueda, filtros, autor y ficha
- `SearchScreen`: `AppBarWithSearch` + `SearchBarDefaults.InputField` con `TextFieldState` sincronizado (`snapshotFlow`); back/filtros/ordenar como acciones. `LoadingIndicator` en carga inicial y paginación, `SecondaryTonalButton` para «Cargar más», `PrimaryCtaButton` para reintentar/aplicar, `EmptyState`, recuento real y `ActiveFilterSummary`. Resultados en 1/2/3 columnas por breakpoint conservando `LazyListState` y claves. `FilterSheet` usa `AdaptiveSheet`. `searchKey`, `withTimeoutOrNull(3_000)`, `fetchFirst/fetchMore`, dedupe, `restorePending` y ScrollStore intactos.
- `AuthorScreen`: avatar `MaterialShapes.Cookie9Sided.toShape()`, `headlineMedium`, bio ≤3 líneas, `SecondaryOutlinedButton` para perfil, `LoadingIndicator`, cuadrícula adaptativa de obras, cargas independientes perfil/works, prefetch página 2, `restoredPage`, `listState`, `retryTick` intactos.
- `WorkDetailScreen`: `MediumFlexibleTopAppBar` con `detail?.summary?.title ?: "Obra"`, up y favorito; título duplicado del body eliminado; CTA 56 `Leer capítulo 1` con `chapters.first().index` o texto explicativo si no hay capítulos; acciones secundarias con `SecondaryOutlinedButton`. Sin inventar reanudación.

**Parcial en este bloque**: `FilterSheet` conserva el orden original de controles y no se envolvió cada grupo en `CollapsibleSection` con `AnimatedVisibility`; el plano pedía reordenar a texto/fandom/tag → include/exclude → rating/warnings/categories/idioma → avanzados → switches → palabras/fechas. La funcionalidad y el draft están intactos; la reordenación visual queda pendiente.

### Bloque 5 — Ajustes
- `SettingsScreen`: dos paneles ≥840 dp con altura ≥480 (hub de 320 dp + borde `outlineVariant` + página); `page==null` → `EmptyState` «Elige una categoría». `SettingsPageScaffold(showBack)` con `MediumFlexibleTopAppBar`; las cinco páginas reciben `showBack=!twoPane`; `page` única en todos los tamaños; `BackHandler` conservado.
- `SettingsHub`: `MediumFlexibleTopAppBar` «Ajustes» + subtítulo; cinco `SegmentedListItem` con `ListItemDefaults.segmentedShapes(index,count)` y `SegmentedGap`, sin chevrons ni tarjetas.
- `SettingsSection`: ya no es `Surface` por sección; Column con heading y gaps 12/24, ancho ≤720 dp.
- Botones migrados a 56/40 dp; API key convertida a `OutlinedSecureTextField` con `TextFieldState` guardable y `TextObfuscationMode.Visible/Hidden`.

**Parcial**: no se añadió el `ReaderPreview` superior en la página del lector (no existe un composable `net.spin.ao3.ui.reader.ReaderPreview` en el árbol actual; los cuatro temas y el preview existente siguen intactos). La fila de Material You no se reescribió explícitamente como `toggleable(role=Role.Switch)` de 48 dp; conserva su implementación anterior con default `false` y `recreate` intacto.

### Bloque 6 — Lector (sólo chrome)
Se leyó `docs/READER_SCREEN.md` §8 antes de tocar nada. **No se modificaron** `htmlToLines`, `blockToRows`, `packPagesToViewport`, `fits`, `linesToAnnotated`, `pagePlainText`, `ReaderFind`, `ReaderWebViewHost`, `contentKey`, `pendingScroll`, joins ni guardado/progreso.

- Todos los `readerBgColor(theme).copy(alpha=…)` pasaron a superficies opacas; los `readerFgColor(theme).copy(alpha=…)` a `readerMuted(bg, fg, factor)` = `lerp` opaco. No se cambiaron los hex de `readerPalette` ni `readerCss`.
- `CircularProgressIndicator` → `LoadingIndicator` en overlays.
- Tres `ModalBottomSheet` → `AdaptiveSheet`.
- Se conservaron root `background(readerBgColor(theme))`, `windowInsetsPadding(displayCutout)`, overlay puro (barras no reservan espacio), JavaScript de búsqueda, TTS pause/resume/stop, paginación, `chromeVisible` y callbacks.

Gate obligatorio en verde: `ReaderPagingTest`, `ReaderSearchTest`, `ReaderRatioTest` + `compileDebugKotlin`.

### Bloque 7 — Inputs y menús
- Los seis `DropdownMenu` (orden de búsqueda, idioma en filtros, selector de capítulo, acciones por capítulo, exportación completa, overflow del lector) pasaron a `DropdownMenuPopup`; se eliminaron los imports viejos. `DropdownMenuGroup` no fue necesario porque cada menú es un grupo único.
- `AutocompleteTagField` conserva la firma pública; internamente usa `TextFieldState` con sincronización bidireccional (`setTextAndPlaceCursorAtEnd` sólo si difiere; `snapshotFlow` → `onValueChange` sólo si difiere), debounce 300 ms, `LaunchedEffect` cancelable y `LoadingIndicator`. Sugerencias en `Surface` 12 dp con filas de 48 dp.
- `HomeScreen`, `AllTags` y `SearchScreen` usan `SearchBarDefaults.InputField`; el lector mantiene su campo dentro del chrome.
- API key con `OutlinedSecureTextField` + `TextObfuscationMode`.

**Parcial**: el resto de formularios (comentarios, traducción base/modelo, wordsFrom/To, fechas, campos avanzados de filtros) siguen con `OutlinedTextField(value/onValueChange)` clásico. Son funcionales y conservan validaciones y `KeyboardType.Number`, pero no completan el cutout a `rememberSaveable(saver=TextFieldState.Saver)` que pedía el plan.

## Gates ejecutados (todos en verde)

```
./gradlew :app:dependencyInsight --dependency org.jetbrains.compose.material3:material3 --configuration debugRuntimeClasspath
./gradlew :app:compileDebugKotlin                       # tras cada bloque
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.ui.components.TagPaletteTest"
./gradlew :app:testDebugUnitTest \
  --tests "net.spin.ao3.util.ReaderPagingTest" \
  --tests "net.spin.ao3.util.ReaderSearchTest" \
  --tests "net.spin.ao3.ui.screens.ReaderRatioTest"
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease                          # BUILD SUCCESSFUL in 14m 23s
git diff --check                                        # limpio
```

No hubo bump de versión, release, publicación ni firma nueva. El APK debug
generado pesa ~23 MB.

## Verificación real: NO REALIZADA

No había dispositivo: `adb devices` → «List of devices attached» vacío. El
serial `DAWSAICMVKAQGQS8` del plan no aparece. En consecuencia **no se
instaló el APK, no se tomaron capturas en `.design-review/editorial/`, no se
hicieron taps con `uiautomator dump`, no se probó texto al 200 % ni los tres
viewports** (compact 800×360 dp, expanded 960×1280 dp, large 1280×800 dp).

No se desinstaló nada ni se intentó `run-as`: no había dispositivo sobre el que
hacerlo. Cuando el dispositivo vuelva, hay que:

1. Repetir el checklist previo del plan (`adb devices`, `pm path`, `wm size`,
   `wm density`, `font_scale`) y hacer el tar `run-as` con umask 077 antes de
   instalar.
2. Instalar `app-debug.apk` y ejecutar las capturas del plan.
3. Aplicar como máximo dos rondas de corrección visual y registrar cualquier
   estado no alcanzable como «calidad visual no verificada».

Los PNGs que ya existen en `.design-review/` son de sesiones anteriores (nov
iembre 8) y **no** son evidencia de este rediseño. El borrador
`design/editorial-redesign.explore.kt` sigue sin aprobar y no se usó.

## Estado final del repo

- Commits nuevos (de antiguo a reciente):
  1. `b2843f2` — tema Expressive con esquemas estáticos
  2. `fc0908f` — shell adaptativo y tarjetas
  3. `f589bb2` — Inicio hero y Biblioteca adaptativa
  4. `027db35` — búsqueda, autor y ficha
  5. `02b7b4c` — Ajustes
  6. `f36d6b3` — chrome del lector
  7. `b932374` — menús e inputs
  8. `74ff986` — previews dark/fontScale 2
- Archivos nuevos: `ColorSchemes.kt`, `TagPalette.kt`, `AdaptiveSheet.kt`,
  `Expressive.kt`, `TagPaletteTest.kt`, `fraunces_{semibold,bold}.ttf`,
  `assets/license_fraunces.txt`.
- Sin bump de versión: `versionCode=65`, `versionName=0.7.32`.
- `docs/HISTORY.md` aún no tiene la crónica de esta ronda; añadirla al cerrar.
- El backup `~/Documentos/ao3-backups/20261121-234529-pre-editorial/` y las
  skills oficiales `.agents/skills/android-design*` no se tocaron. El tmp con
  el generador de esquemas vive en `/tmp`, fuera del repo.
