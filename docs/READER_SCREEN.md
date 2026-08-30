# El lector — guía (`ReaderScreen.kt` + `ui/reader/`)

El lector vive en `ui/screens/ReaderScreen.kt` (~1.750 líneas: el composable
principal con estado y efectos) y en el paquete `ui/reader/` (piezas extraídas,
228-465 líneas cada una). Es la zona más frágil del proyecto: concentra el modo
scroll (WebView), el modo paginado (Compose), TTS, la UI de traducción, la
búsqueda en capítulo, la hoja de ajustes y el chrome inmersivo. Esta guía mapea
el conjunto y explica cómo modificarlo sin romperlo.

**Antes de tocar**: leer la sección 8 (Invariantes) y ejecutar los tests
(`./gradlew :app:testDebugUnitTest`). El lector acumula varias regresiones
históricas documentadas — la sección 9 explica las que están blindadas con
código y por qué.

---

## 1. Mapa de archivos

| Archivo | Contenido |
|---|---|
| `ui/screens/ReaderScreen.kt` | El composable principal: TODO el estado del lector, efectos de carga (metadatos + capítulo), `goToChapter`/`saveProgressNow`, ramas scroll/paginado, chrome (barras, overlays de carga/error/traducción, hint), hoja de ajustes y hoja de traducción, enum `TranslationLang` |
| `ui/reader/ReaderTheme.kt` | Paleta de los 4 temas (`readerPalette`, `readerColors` precalculada), `readerCss`, `readerPrefsJson`, `ReaderPreview` (vista previa de la hoja de ajustes) |
| `ui/reader/ChapterHtml.kt` | `buildChapterHtml`: HTML del capítulo para el modo scroll |
| `ui/reader/ReaderWebViewHost.kt` | El WebView del modo scroll: carga, `applyReaderPrefs` en vivo, ratio por JS, búsqueda nativa, links al navegador |
| `ui/reader/ReaderPagination.kt` | Pipeline de paginación: `blockToRows`, `packPagesToViewport`, `linesToAnnotated`, `pushSegmentStyle` |
| `ui/reader/PagedReaderBody.kt` | `HorizontalPager` + zonas de toque 25/50/25 + `ReaderProgressBar`/`ReaderProgressLabel` (usados también por la barra inferior) |
| `ui/reader/ReaderFind.kt` | Búsqueda en capítulo: `jsFindInPage/jsGoToMatch/jsClearFind/jsHighlightTtsWord`, `parseJsRatio`, `restoreScroll`, navegación de coincidencias del modo paginado |

Dentro del composable principal (por orden aproximado): estado (~158-230),
progreso y chrome inmersivo, datos paginados (`displayContent`, `lines`,
`measuredPages`, `rowStarts`, `pagerState`), efecto de metadatos, efecto de
capítulo, `goToChapter`/`saveProgressNow`, HTML/JS del modo scroll, cuerpo
(ramas scroll/paginado), overlays, hoja de ajustes, hoja de traducción.

## 2. Flujo de datos

```
workId ──(efecto 1)──> chapters: List<ChapterInfo> (índice completo de la obra,
                         cuerpos descargados superpuestos)
currentIndex ──(efecto 2)──> content: String? (HTML sanitizado del capítulo)
translatedHtml (por capítulo+idioma, caché en disco)
displayContent = translatedHtml si translationOn, si no content
   ├── modo scroll: html = buildChapterHtml(...) → WebView (contentKey decide recarga)
   └── modo paginado: htmlToLines(displayContent) → filas visuales → páginas
```

- `contentKey = "$workId|$currentIndex|$chapterTitle|$translationOn|$contentId"`
  con `contentId = displayContent.hashCode()`. NUNCA volver a concatenar el
  capítulo completo aquí (se recomponía por frame de scroll).
- El capítulo cambia: `goToChapter` guarda progreso del saliente, etiqueta
  `pendingScroll` con el capítulo destino y cancela la traducción en vuelo.

## 3. Pipeline del modo paginado

```
displayContent
  → htmlToLines()            [util/ReaderPaging.kt, JVM puro, Dispatchers.Default]
     bloques: Line(segments, kind, paragraphStart, paragraphEnd)
  → blockToRows()            [ReaderScreen; mide CADA bloque con TextMeasurer al
     ancho de página y lo corta en FILAS VISUALES exactas (sliceSegments conserva
     estilos). Un párrafo puede empezar en una página y terminar en la otra]
  → packPagesToViewport()    [búsqueda binaria de filas por página; fits() mide
     EXACTAMENTE el AnnotatedString que la página renderizará; techo capLines =
     2*(heightPx/lineHeightPx)+8 para que cada medición cueste ~una página]
  → measuredPages + rowStarts   (páginas y su índice global de fila inicial)
  → PagedReaderBody          [HorizontalPager; columna con scroll de respaldo
     para filas gigantes; pie "· N de M ·"]
```

Reglas del pipeline:

1. **`fits()` mide exactamente lo que se renderiza** (mismos joins, estilos y
   ancho). Cualquier cambio en `linesToAnnotated` debe reflejarse en
   `pagePlainText` (offsets de búsqueda) y viceversa.
2. **Los joins dependen de los flags**: fila que inicia bloque → `"\n\n"`;
   fila de continuación → `"\n"`; comillas `“ ”` y viñeta `•` solo en las
   filas `paragraphStart`/`paragraphEnd` correspondientes. Los tres
   consumidores (`linesToAnnotated`, `pagePlainText`, `findInPages` vía
   `pagePlainText`) comparten la lógica — cambiarla en uno y no en otro
   desalinea los offsets de búsqueda.
3. **Posición entre re-packs**: `rowStarts` guarda el índice GLOBAL de la
   primera fila de cada página. Si el ANCHO no cambió, las filas son
   idénticas y el índice sobrevive a cualquier re-pack: el pack restaura la
   página que contiene esa fila (`starts.indexOfLast { it <= savedRow }`).
   Esto sustituyó al restore por ratio, que corría ±1 página al alternar
   controles (regresión v0.7.28-arreglada).
4. **Chrome = overlay puro**: mostrar/ocultar barras NO cambia el layout del
   capítulo (sin insets reservados, sin re-pack, sin flash). Las barras pueden
   tapar la primera/última línea mientras son visibles — decisión explícita
   del usuario.
5. **Notch**: el contenedor raíz pinta `.background(readerBgColor(theme))` y
   aplica `.windowInsetsPadding(WindowInsets.displayCutout)` — la franja del
   punch-hole queda del color del tema y el texto nunca queda debajo.
6. El pack corre en `Dispatchers.Default` con fallback a main si el
   TextMeasurer falla fuera del hilo principal (log `ReaderPaged`). Re-packs
   del MISMO contenido (viewportOnly) no muestran el overlay de carga: las
   páginas viejas quedan visibles hasta el swap silencioso.
7. NO reintroducir una caché de medición keyed por `lines.hashCode()` —
   produjo páginas medio vacías y se eliminó (regla dura de CLAUDE.md).

## 4. Modo scroll (WebView)

- `loadDataWithBaseURL` con el HTML construido localmente (cabecera obra +
  capítulo + contenido + CSS del tema).
- Cambios de tema/fuente/márgenes NO recargan: van por JS
  (`applyReaderPrefs`) con variables CSS. Solo `contentKey` dispara recarga.
- Scroll: listener JS reporta el ratio 0..1 por evento (`onScrollRatio`);
  el progreso se guarda SÍNCRONAMENTE con ese valor (nunca con un round-trip
  tardío al WebView — regresión v0.7.7).
- Búsqueda: `jsFindInPage` marca con la CSS Custom Highlight API (Ranges; el
  DOM no se muta — las offsets nunca se corrompen), fallback `<mark>`
  idempotente. `jsGoToMatch` navega (`$index` interpolado también en el
  guard — regresión v0.7.23).
- Links del capítulo abren en el navegador del sistema
  (`shouldOverrideUrlLoading` → ACTION_VIEW).

## 5. Chrome y zonas de toque (paginado)

- Tap 25% izquierdo → página anterior; 25% derecho → siguiente; banda central
  (50%) → `toggleChrome` (barras + pantalla completa vía
  `WindowInsetsController`). En los extremos, el toque de zona cae al toggle.
- La barra superior: Volver (guarda progreso) | título/obra (abre ficha) |
  búsqueda | ⋮ (traducir, TTS, ajustes, tema). La inferior: anterior |
  "Cap. X/Y · % · Pág. N/M" (abre lista de capítulos) | siguiente; con TTS
  activo muestra estado + Detener.
- Hint "Toca el texto para mostrar los controles" al ocultarlas.

## 6. Traducción

- Menú ⋮ → hoja "Traducir capítulo": chips de proveedor (Google / IA) +
  idioma (`TranslationLang`). El chip de IA avisa si falta configuración.
- `translateChapter()` usa `container.translator` (lee prefs en cada llamada);
  progreso (lotes done/total) en overlay con Cancelar; el resultado aplica
  solo si sigues en el mismo capítulo; caché en disco (claves por capítulo +
  idioma + proveedor/modelo) — volver a un capítulo traducido es instantáneo.
- Con traducción activa: chips Original/Traducción conmutan `translationOn`
  sin re-traducir; TTS lee la versión visible; el modo paginado re-empaqueta
  con el texto traducido.

## 7. TTS

`util/ReaderTts`: trocea el texto ≤4000 chars, velocidad persistida
(0.5×–2×), auto-avance al siguiente capítulo (`resumeTtsOnChapterLoad`),
pausa al pasar a segundo plano, resaltado de palabra con la CSS Custom
Highlight API (scroll solo si la palabra sale del viewport).

## 8. Invariantes (romper cualquiera = regresión conocida)

1. `currentIndex` NO es `rememberSaveable` (las claves de nav son la ruta
   serializada; un índice saveable resucitaría el último capítulo leído al
   re-abrir la misma ruta).
2. `pendingScroll` lleva `pendingScrollChapter`; los consumidores (WebView
   `onPageFinished` y el pack) solo lo consumen si coincide con el capítulo
   actual (bug v0.7.23: un render intermedio consumía el restore del capítulo
   saliente y caías en página 0).
3. El guardado al salir es SÍNCRONO con `currentRatio` (que el listener JS
   mantiene fresco) — nunca dependas de un `evaluateJavascript` tardío.
4. `fits()`/`pagePlainText`/`linesToAnnotated` comparten joins y decoraciones.
5. `PagedReaderBody` hace guard de `pages.isEmpty()` (el pager coacciona el
   conteo a 1 y `pages[0]` sobre lista vacía crashea).
6. La medición del pack corre FUERA de composición (congelaba la UI; se veía
   como "el paginado no hace nada") — no volver a medir en composición.
7. `contentId` (hash) y no el HTML completo dentro de `contentKey`.
8. Los colores del lector salen de `readerColors` (precalculados, parseo
   puro JVM — `android.graphics` en un inicializador top-level rompe los
   tests JVM de este archivo).

## 9. Regresiones históricas (para contexto)

- **"El paginado no hace nada"** (v0.7.26-28): dos causas encadenadas —
  (a) el pack medía media obra por llamada en el hilo principal (congelaba);
  (b) el chip de modo de la hoja solo actualizaba el borrador y el modo no se
  aplicaba hasta "Aplicar" (bajo el fold). Arreglado: pack en background +
  techo de búsqueda + chips con aplicación inmediata.
- **Páginas medio vacías**: los bloques no se partían; ahora se parten en
  filas visuales (§3).
- **Salto de página ±1 al alternar controles**: el resize del viewport +
  restore por ratio; resuelto con restauración por fila exacta y re-pack
  silencioso.
- **Spinner infinito del WebView** (v0.7.16-19): marcador de challenge
  incorrecto; ver [ARCHITECTURE.md](ARCHITECTURE.md) §2.

## 10. Recetas rápidas

- **Añadir tema del lector**: `Store.ReaderTheme` (enum + labelRes) →
  `readerPalette` (hex bg/fg/accent) → `readerColors` se recalcula solo →
  strings. Probar el CSS del modo scroll (`readerCss`).
- **Cambiar densidad de página**: `verticalPad`/`footerPx`/`heightPx` en
  `packPagesToViewport` — siempre con `fits()` midiendo el render exacto.
- **Cambiar zonas de toque**: `PagedReaderBody.pointerInput` (fracciones del
  ancho).
- **Añadir idioma de traducción**: `TranslationLang` (código BCP-47 +
  etiqueta). Google/IA lo consumen igual.
- **Añadir ajuste del lector**: ver patrón de 4 pasos en
  [MODIFYING.md](MODIFYING.md) y el flujo borrador/Aplicar de la hoja (los
  chips de MODO son la excepción: aplican al instante).
