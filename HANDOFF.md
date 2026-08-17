# HANDOFF — AO3 Lector (`net.spin.ao3`)

> Documento de contexto para que otro agente (o humano) retome el proyecto sin
> tener que reconstruir la historia. Última actualización: 17 de agosto de 2026.

---

## 1. Qué es

App Android **nativa (Kotlin + Jetpack Compose + Material 3)** para leer fanfic de
**archiveofourown.org (AO3)** de forma cómoda en el móvil/tablet, con:

- Búsqueda en AO3 (por texto libre) con ordenación (kudos, hits, palabras, actualizadas…)
- Pantalla de detalle de obra: rating, warnings, categorías, stats, tags con colores, resumen, capítulos
- Lector con WebView: 4 temas (claro/sepia/oscuro/negro), tamaño de letra, serif/sans, progreso guardado, lista de capítulos
- Biblioteca local: historial de lectura (con % de scroll), favoritos, **descargas para leer sin conexión**
- **Modo offline real probado**: obras descargadas se leen con la red apagada

Personal-use, sin cuenta ni login (AO3 es público). El contenido pertenece a sus autores.

---

## 2. Ubicación y comandos

| Concepto | Valor |
|---|---|
| Directorio del proyecto | `/home/spin/Documentos/AO3` |
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` (~21 MB) |
| Build | `cd /home/spin/Documentos/AO3 && ./gradlew :app:assembleDebug` (~10-30 s en frío tras caché) |
| Instalar en dispositivo | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| Lanzar | `adb shell am start -n net.spin.ao3/.MainActivity` |
| Reiniciar limpio + lanzar | `adb shell am force-stop net.spin.ao3 && adb shell am start -n net.spin.ao3/.MainActivity` |

> **Nota sobre el entorno:** la advertencia antigua de que `write_file`/`read_files`
> anclaban a otro proyecto (tachiyomi-legacy) era específica de la sesión original y
> **ya no aplica**; el proyecto se edita directamente con las herramientas normales.

---

## 3. Stack técnico (versiones exactas — no cambiar sin motivo)

Reutiliza la caché de Gradle de tachiyomi-legacy en esta misma máquina (por eso el
primer build fue rápido). Versiones en `gradle/libs.versions.toml`:

| Componente | Versión | Notas |
|---|---|---|
| Gradle wrapper | 9.6.1 | copiado del proyecto tachiyomi-legacy |
| AGP | 9.2.1 | `com.android.application` |
| Kotlin | 2.4.0 | **integrado en AGP 9** (NO existe plugin `kotlin-android` separado); solo se usa `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2026.06.01 | `androidx.compose:compose-bom-alpha` |
| compileSdk / targetSdk / minSdk | 37 / 36 / **23** | minSdk 23 es obligatorio: el dispositivo de prueba es Android 6.0.1 |
| JVM target | 17 | `sourceCompatibility/targetCompatibility = VERSION_17` + `jvmTarget = JVM_17` |
| core library desugaring | `desugar_jdk_libs:2.1.5` | **IMPORTANTE**: `isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring(...)`. Sin esto la app crashea en Android 6 (`NoClassDefFoundError ExternalSyntheticLambda`, java.util.function no existe en API 23) |
| Jsoup | 1.22.2 | parser HTML |
| OkHttp | 5.4.0 | cliente HTTP |
| Coroutines | 1.11.0 | `kotlinx-coroutines-android` |
| Icons | `material-icons-core` + `material-icons-extended` | `extended` SÍ se usa (Description, Layers, TrendingUp, MenuBook, DarkMode, Translate… no están en core) |

- Kotlin de AGP 9: las `dependencies` del módulo van con `alias(libs.plugins...)` y `kotlin { compilerOptions { jvmTarget = ... } }`
- Release config existe (`isMinifyEnabled = true` + proguard) pero **nunca se ha probado**; el flujo usado es debug.

---

## 4. Estructura del proyecto (39 archivos Kotlin, ~10.100 LOC en main)

```
app/src/main/java/net/spin/ao3/
├── Ao3App.kt                 # Application; crea AppContainer; AvatarImages.init; reanuda cola
├── MainActivity.kt           # Activity; enableEdgeToEdge; AppRoot: 3 tabs + stack full-screen
├── data/
│   ├── AppContainer.kt       # store + client + connectivity + translator (singleton)
│   ├── Ao3Client.kt          # OkHttp: mutex serializa peticiones, reintentos+backoff,
│   │                         #   Cloudflare/age-gate, 429+Retry-After, LRU + DiskCache,
│   │                         #   refreshWork() (salta cachés), caches de tags acotadas
│   ├── Ao3Parser.kt          # Jsoup: search/detail/chapter/comments/facets/author, 2 plantillas
│   ├── Store.kt              # JSON local; thread-safe; escritura atómica + single-writer
│   ├── DiskCache.kt          # caché HTML en disco (TTL por instancia, getStale para offline)
│   ├── ConnectivityMonitor.kt# NetworkCallback -> StateFlow (banner offline)
│   ├── DownloadQueueService.kt # cola FIFO foreground con notificación + reanudable
│   ├── Translator.kt         # traducción de capítulos (endpoint gtx) con caché en disco
│   └── model/Models.kt       # WorkSummary, WorkDetail, ChapterInfo, SearchFilters, etc.
├── ui/
│   ├── AppNav.kt             # Route sellada + NavController (serialización URL-safe)
│   ├── components/           # BottomBar, WorkCard, TagChip, EmptyState
│   ├── screens/
│   │   ├── HomeScreen.kt     # buscador, chips, Continuar leyendo
│   │   ├── SearchScreen.kt   # resultados + filtros + ordenación + paginación
│   │   ├── WorkDetailScreen.kt # metadatos, kudos, comentarios, descargas, pull-to-refresh
│   │   ├── ReaderScreen.kt   # WebView scroll + paginado, temas, traducción, buscar, links
│   │   ├── LibraryScreen.kt  # Favoritos / Historial / Descargas
│   │   ├── SettingsScreen.kt # tema app + defaults lector + identidad comentarios
│   │   └── AuthorScreen.kt   # perfil de autor + obras paginadas
│   └── theme/Theme.kt        # Material 3 "Cacao & Salvia", dynamic color opt-in
└── util/                     # Format, HtmlText, ReaderPaging, ChapterExporter,
                              #   AuthorUrl, AvatarImages (caché en disco)
```

Recursos: `res/values` (strings/colors/themes), `res/values-night/themes.xml`,
icono launcher vectorial en `res/mipmap/ic_launcher.xml` (API 23-25) y
`res/mipmap-anydpi-v26/ic_launcher.xml`.

### Modelos clave

- `WorkSummary`: id, título, autor, fandoms, rating+ratingKey, warnings, categories, summary, words, chapterCount/chapterTotal, hits, kudos, comments, bookmarks, published, updated, url. `isCompleted` = chapterCount >= chapterTotal.
- `ChapterInfo(index, title, url?, content?)` — content es HTML **sanitizado**; `null` = no cargado.
- `WorkDetail(summary, descriptionHtml, notesHtml, relationships, characters, additionalTags, chapters)`.

---

## 5. Estado actual — TODO VERIFICADO EN EL DISPOSITIVO (9 ago 2026)

Flujo completo probado en el tablet real:

1. ✅ App instala y arranca sin crash en **ALCATEL POP 7 LTE, Android 6.0.1 (API 23), ARMv7**
2. ✅ Búsqueda "naruto" → resultados parseados con rating, categoría, fandom, palabras, caps, visitas, kudos, fecha
3. ✅ Detalle de obra: stats (palabras/capítulos/visitas/kudos), fechas, relaciones, personajes, fandoms, warnings, **Capítulos (N)** con lista navegable
4. ✅ Lector: abre capítulo, renderiza contenido real en el WebView (verificado por píxeles y por accesibilidad)
5. ✅ Historial: al leer se guarda; el detalle cambia a "Continuar · Cap. X" y el Home muestra "Continuar leyendo" con la obra
6. ✅ Descarga: botón "Descargar" → "Descargado", contenido persistido en `ao3_library.json` (verificado)
7. ✅ **LECTURA OFFLINE PROBADA**: con el dispositivo en modo avión (`adb shell settings put global airplane_mode_on 1` + broadcast), se abrió la obra descargada desde la sección "Descargas" del Home y el capítulo se renderizó completo desde caché
8. ✅ Manejo de errores: página de error con "Reintentar" cuando AO3 falla (no crashea)

Build actual: `BUILD SUCCESSFUL`, solo 2 warnings de deprecación
(`rememberModalBottomSheetState`, inofensivos).

---

## 6. Arquitectura y decisiones clave

- **Navegación propia** (sin navigation-compose): `Route` sellada + `NavController` con pila, `rememberSaveable` para sobrevivir rotación. `AnimatedContent` en MainActivity.
- **Datos**: `Ao3Client` serializa TODAS las peticiones con un `Mutex` + `delay(400ms)` mínimo por cortesía al sitio. 5 reintentos con backoff `1200ms * intento`.
- **Store**: un único JSON `filesDir/ao3_library.json` (`saved`, `history`, `downloads`, `prefs`). Escritura asíncrona. `prefs` = fontSizeSp (18), theme (DARK), serif (true).
- **Lector**: `AndroidView(WebView)` con `loadDataWithBaseURL(BASE_URL, html, ...)`. El HTML se construye localmente: cabecera (obra + capítulo) + contenido sanitizado + CSS del tema. `evaluateJavascript` para capturar/restaurar el ratio de scroll. El progreso se guarda cada 3,5 s y al salir.
- **Descarga offline**: el lector comprueba `store.download(workId)` ANTES de tocar la red; si existe, usa capítulos con `content` incrustado. La sección "Descargas" del Home lista `store.downloads()` y abre el lector directo.
- **ratingKey** se usa para colorear el rating (general-audiences=verde, teen=ámbar, mature=naranja, explicit=rojo, not-rated=gris).

---

## 7. Peculiaridades de AO3 (¡críticas!)

AO3 está detrás de Cloudflare y cambia plantillas. El cliente/parser ya maneja esto,
pero **cualquier cambio debe preservar estas protecciones**:

1. **Errores Cloudflare con HTTP 200**: AO3 sirve a veces páginas de error
   (520/521/522/525) o de challenge "Just a moment" con **status 200**. `Ao3Client`
   detecta marcadores (`cf-error-details`, `SSL handshake failed`, `challenge-form`,
   `challenge-running`, `cf-chl`, `jschl`, `error code: 5XX`) y los trata como error
   reintentable. Sin esto el parser daba "No se pudo interpretar la obra".
2. **Age-gate "Adult Content Warning"**: para obras con contenido adulto, AO3 muestra
   un intersticial con `<a href="/works/{id}?view_adult=true">Yes, Continue</a>`
   a visitantes sin la cookie `view_adult`. Se sirve con HTTP 200 y el `<title>` de la
   obra (¡por eso el parser fallaba sin título!). `get()` lo detecta y **re-pide la URL
   con `?view_adult=true`** como haría un navegador. `CookieJar` en memoria guarda
   cookies de CF y de AO3.
3. **Dos plantillas de página de obra**: la clásica (`div#chapters > div.chapter[id]`
   con todas los capítulos inline, tags en `div.tags.commas`) y la nueva (tags dentro
   de `dl.work.meta.group`, stats en un `dl.stats` anidado, `div#chapters` con el
   contenido directo en `div.userstuff` sin wrapper). `Ao3Parser` maneja ambas:
   - `parseWorkDetail` recolecta pares `dt→dd` de todos los `dl` candidatos, salta el
     contenedor "Stats:", y clasifica por etiqueta.
   - `parseChapters(doc, workId)`: 1) índice `ol.chapter li a[href*='/chapters/']`;
     2) template clásico (chapters inline con contenido); 3) template nuevo
     (un solo capítulo con contenido).
4. **Inestabilidad de red**: AO3/CF falla intermitentemente (525, timeouts).
   Reintentos + backoff son esenciales. Las pruebas en dispositivo a veces necesitan
   2-3 intentos; el botón "Reintentar" de cada pantalla re-lanza la carga.
5. **UA de la app**: `AO3-Lector/0.1 (personal reader app; okhttp)`. Funciona.

### Estructuras HTML confirmadas (última verificación 9 ago 2026)

- Blurb (resultado de búsqueda): `ol.work.index.group > li.work.blurb[id=work_NNN]`,
  `h4.heading a[href*='/works/']` (título), `a[rel=author]`, `h5.fandoms a.tag`,
  `ul.required-tags span.rating/warning/category` (el título en `attr title`),
  `dd.words/chapters/hits/kudos/comments/bookmarks`, `p.datetime`.
- Detalle: `h2.title.heading`, `a[rel=author]`, `div.summary.module div.userstuff`,
  `div.notes.module div.userstuff`, `dl.work.meta.group` (con `dl.stats` anidado).
- Capítulo: `div#chapters` → `div.chapter[id] div.userstuff` (clásica) o
  `div#chapters div.userstuff` directo (nueva).

---

## 8. Dispositivo de prueba

| | |
|---|---|
| Modelo | ALCATEL POP 7 LTE (TLS11?) |
| Android | 6.0.1 (API 23), ARMv7 |
| Conectividad | USB (adb) + WiFi (router MiRouter, IP 192.168.31.x) |
| Particularidades | `svc wifi disable` NO funciona (no corta la red); el modo avión SÍ (`settings put global airplane_mode_on 1` + broadcast `android.intent.action.AIRPLANE_MODE`). Para volver: `airplane_mode_on 0` + `svc wifi enable`. |
| uiautomator | funcional; el contenido del WebView SÍ aparece como nodos de texto en el dump |

**Prueba manual del flujo completo** (el dispositivo es lento; dar tiempo de sobra):

```bash
# Home: el buscador está aprox. en (300, 124) — verificar con uiautomator dump antes
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > /tmp/ui.xml
adb shell input tap 300 124 && adb shell input text 'naruto' && adb shell input keyevent 66
sleep 22   # resultados (AO3 tarda)
adb shell input tap 150 132 && sleep 30   # primer resultado → detalle (puede tardar con reintentos)
# en el detalle: botón Leer (aprox. 183,360) / Descargar (aprox. 460,320) — verificar bounds
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > /tmp/ui.xml
# ver texto del capítulo en el dump (WebView expone accesibilidad)
```

Para descargas verificadas offline: leer `ao3_library.json` con
`adb shell run-as net.spin.ao3 cat files/ao3_library.json`
(`adb pull` NO puede leer /data/data en este Android 6; usar `run-as`).

---

## 9. Problemas conocidos y limitaciones

1. ~~**`Store.persist()` carrera teórica**~~ — **ARREGLADO en v0.7.5**: snapshot
   capturado en el caller thread dentro de un lock, single-writer "last-wins"
   (nunca fuera de orden) y escritura atómica (temp + rename, no corrompe el JSON
   si se corta la corriente). El historial sigue reescribiendo el JSON completo
   cada 3,5 s (debounced a 300 ms); si la biblioteca crece mucho, separar el
   progreso del contenido descargado seguiría siendo una mejora.
2. ~~**`InMemoryCookieJar` sin sincronizar**~~ — **ARREGLADO en v0.7.5**: ahora es un
   `ConcurrentHashMap` (seguro aunque `getConcurrent` dispare dos peticiones en
   paralelo).
3. **Worst case de carga**: 7 intentos × hasta 90 s de `callTimeout` = la UI puede
   quedarse en "Cargando…" varios minutos si AO3 está caído de verdad. (Ahora se
   respeta `Retry-After` en 429, cap a 30 s.)
4. ~~**`AppNav.serialize()` con `\u0001`**~~ — **ARREGLADO en v0.7.5**: `Route` y
   `SearchFilters` URL-encoden cada campo; cualquier consulta hace round-trip.
5. **Doble recarga del WebView** al cambiar contenido/tema (guard en `update` +
   `LaunchedEffect(htmlToLoad)`). Inofensivo pero derrochador.
6. ~~**No hay tests unitarios del parser**~~ — **YA NO APLICA**: hay ~60 tests JVM
   (parser, comentarios, autores, DiskCache, Translator, paginación, búsqueda,
   ratio, **Store**, serialización) con snapshots HTML reales.
7. **Release build**: probado en CI (debug + release firmado + tests). La v0.7.4
   trae APK firmado verificado; el flujo diario sigue siendo debug.
8. La sección Descargas del Home no permite borrar descargas (solo desde el detalle).
9. ~~`rememberModalBottomSheetState` deprecado~~ — **ARREGLADO en v0.7.5**: migrado a
   `rememberBottomSheetState(initialValue = SheetValue.Hidden)` (WorkCard, SearchScreen,
   ReaderScreen).

---

## 10. Próximos pasos sugeridos

1. **Tests unitarios del parser** con muestras HTML de ambas plantillas (fijar
   `/tmp/w3.html`, `/tmp/w2.html`, `/tmp/dbg_work.html` como fixtures — AO3 cambia
   plantillas con frecuencia).
2. **Pantalla de Biblioteca** (lista de descargas con borrar, favoritos editables,
   historial con porcentaje) o al menos botón de borrar en la sección Descargas.
3. **Sincronizar historial/marcas con el servidor** (p.ej. guardar en el teléfono vía
   SimpleLogin/otro — fuera de alcance actual).
4. **Mejorar el lector**: notas del autor antes/después del capítulo, modo
   "solo contenido", pre-carga del siguiente capítulo.
5. Probar y pulir el **build release** (firma, R8, icono, versionName).
6. Considerar `androidx.navigation` si la pila crece (hoy la navegación propia basta).

---

## 11. Lecciones de la sesión (para no repetir errores)

- **Escribir SIEMPRE los archivos de AO3 con rutas absolutas vía terminal** (heredoc
  bash o python). `write_file`/`read_files` apuntan a tachiyomi-legacy.
- Verificar con `grep -n` los patches con backslashes: Kotlin no permite `\d` literal
  (usar `\\d`); las comillas anidadas rompen el parseo (usar comillas simples dentro).
- El `pidof` en este Android 6 imprime salidas raras; usar `ps | grep` en su lugar.
- `adb pull` de /data/data falla en Android 6 sin root → usar `run-as`.
- Cuando una pantalla falla "No se pudo interpretar", antes de tocar el parser,
  **verificar QUÉ página devolvió el servidor** (guardar el body y mirarlo) — casi
  siempre es Cloudflare o el age-gate, no el HTML.
- El dispositivo real es la única fuente de verdad para WebView (Android 6 es un
  WebKit antiguo; `color-scheme`, `overflow-wrap` etc. se ignoran sin romper).

---

## 12. Sistema de búsqueda y filtros (AO3) — cómo funciona de verdad

Investigado y verificado en vivo (ago 2026). AO3 tiene **dos superficies de búsqueda**
con comportamientos distintos:

### `/works/search` (búsqueda por texto libre)
- Respeta: `work_search[query]`, `work_search[sort_column]`, `work_search[complete]`,
  `work_search[date_from/date_to]`, `work_search[words_from/words_to]`, `work_search[crossover]`.
- **IGNORA** `include_work_search[]` y `exclude_work_search[]` (filtros de tags/ratings
  nunca se aplican aquí, da igual el formato).
- Endpoint correcto para la búsqueda por texto; `/works` con esos parámetros NO filtra
  (devuelve el listado por defecto de actualizadas — el bug original de la app).

### `/works` (páginas de tag + filtros del sidebar)
- Respeta los filtros del sidebar (`include_work_search[rating_ids][]`,
  `include_work_search[archive_warning_ids][]`, `include_work_search[category_ids][]`,
  `exclude_work_search[...][]`, `work_search[other_tag_names]`, `work_search[excluded_tag_names]`,
  `work_search[complete]`, `work_search[crossover]`, palabras, fechas, sort)
  **SOLO si hay `tag_id` canónico presente** (el input oculto `#tag_id` de la página de tag).
- IGNORA `work_search[query]` (la query no filtra dentro del tag).
- `tag_id` debe ser el **nombre canónico** del tag (ej: "Naruto (Anime *a* Manga)"),
  no el simplificado ("Naruto" o "One Piece" fallan / dan 525).

### Cómo lo resuelve la app (`Ao3Client.search`)
1. Si hay `filters.tag` o (query + filtros activos) → `resolveCanonicalTag(name)`:
   fetchea `/tags/{name}/works` y extrae el valor del input oculto `tag_id`.
   El resultado se cachea en `canonicalTagCache` (Map por nombre) para no re-fetchear
   en cada página de "Cargar más".
2. Si el tag canónico se resuelve → `buildTagFilterUrl`: `/works?tag_id=<canonical>&filtros&sort`.
3. Si no → `buildSearchUrl`: `/works/search?work_search[query]&...` (filtros se ignoran ahí).

`search()` devuelve `SearchResult(works, filtersApplied)`. Cuando los filtros no
pudieron aplicarse (la query no es un tag resolvible), `SearchScreen` muestra un
aviso "Los filtros no se aplicaron..." en errorContainer.`

### IDs de la taxonomía (ratings/warnings/categorías)
- Ratings: 9=Not Rated, 10=General, 11=Teen, 12=Mature, 13=Explicit.
- Warnings: 14=Creator Chose Not To Use, 16=No Archive Warnings, 17=Graphic Violence,
  18=Major Death, 19=Rape/Non-Con, 20=Underage.
- Categorías: 21=Gen, 22=F/M, 23=M/M, 24=Other, 116=F/F, 2246=Multi.
- Nombres de sort correctos (cambiaron): `kudos_count`, `revised_at`, `created_at`,
  `hits`, `comments_count`, `bookmarks_count`, `word_count`, `title_to_sort_on`, `authors_to_sort_on`.

### Pantallas
- `HomeScreen`: chips de sort reales (Tendencias=kudos, Lo nuevo=updated, etc.) +
  sección "Explorar fandoms" (chips → `SearchFilters(tag=...)`).
- `SearchScreen`: botón Filtros abre un sheet con Incluir/Excluir (ratings, warnings,
  categorías), tags a incluir/excluir, solo completadas, solo crossovers, rango de
  palabras, fechas, y reseteo. Dropdown de ordenación con las 10 opciones.
- `Models.kt`: `SearchFilters` serializable (navegación por estado) + `SortOption`.

### Notas de UI/UX
- `ModalBottomSheet` se cierra con un swipe hacia abajo que empiece dentro de la
  zona de drag; para scrollear el sheet usa swipes cortos en la parte baja.
- El sheet de filtros es largo; "Aplicar filtros" queda debajo del fold.
---

## 13. Ronda de fixes (ago 2026): búsqueda real, capítulos, lector fullscreen + CI

### Bugs encontrados y corregidos (verificados en vivo + tests JVM)
1. **Chips de fandom de Home daban error**: `resolveCanonicalTag` usaba
   `URLEncoder.encode()` que convierte espacios en `+` — `+` solo vale en query
   strings, en la *ruta* de la URL AO3 devuelve 404 (`/tags/Harry+Potter/works`
   → 404). Ahora se construye con `HttpUrl.Builder.addPathSegment` (`%20`).
   Verificado: chips Naruto/Harry Potter/Marvel/DCU/MHA/One Piece resuelven y
   muestran resultados.
2. **Sin descripción en los resultados**: el resumen del blurb está en
   `<blockquote class="userstuff summary">` (no `div.userstuff.summary`).
   Mismo bug en el detalle (`div.summary.module div.userstuff` → ahora
   `div.summary.module .userstuff`).
3. **Solo se veían un par de tags en resultados**: los tags del blurb viven en
   `<ul class="tags commas"><li class="relationships|characters|freeforms">`.
   `WorkSummary` ahora tiene `relationships` y `characters`, y `WorkCard` los
   muestra como chips (además de fandoms/categorías/warnings).
4. **Solo se accedía al capítulo 1**: el template nuevo de AO3 indexa los
   capítulos en `<select id="selected_id">` con `<option value="<chapterId>">N. Title</option>`
   (ya NO hay `<ol class="chapter">`). `parseChapters` ahora lee el select y
   construye la URL `/works/{id}/chapters/{chapterId}`. Verificado: obra de 29
   capítulos → los 29 listados y el capítulo 2 carga (la página de capítulo usa
   `div#chapters div.chapter[id] > div.userstuff` como contenido, saltándose
   notes/preface/afterword).
5. **Conteo de capítulos roto**: en el detalle la stat mostraba solo el número
   actual; ahora muestra `29/29` (y `N+` si es incompleta).
6. **Lector**: ahora el indicador de capítulo/barras **se ocultan con un toque**
   en el texto (JS `touchend` + `JavascriptInterface` → `chromeVisible`), lo que
   además activa **pantalla completa** (esconde las system bars vía
   `WindowInsetsControllerCompat`). El scroll NO alterna las barras (flag
   `moved` en touchmove) y los links se excluyen (`closest('a,...')`). Al salir
   se restauran las barras. Se muestra un hint "Toca el texto para mostrar los
   controles" unos segundos.

### Tests unitarios (JVM, se ejecutan en CI)
- `app/src/test/java/net/spin/ao3/data/Ao3ParserTest.kt` + snapshots de HTML
  real en `app/src/test/resources/ao3/` (`blurb.html`, `work.html`, `chap.html`).
- Cubren: resumen+tags del blurb, los 36 capítulos del select, contenido del
  capítulo sin notes, y `sanitize`.
- `./gradlew :app:testDebugUnitTest` (4/4 verdes).

### CI / GitHub Actions
- Repo: `github.com/dusk0382/aotest` (rama `main`).
- `.github/workflows/build.yml`: dispara en push a main/master + PR +
  workflow_dispatch. Corre `testDebugUnitTest` y `assembleDebug` con JDK 17
  (Temurin) y `gradle/actions/setup-gradle@v4`, y sube el APK como artefacto
  (`ao3-reader-debug`).
- El repo local `/home/spin/Documentos/AO3` está conectado a `origin`.

### Notas del dispositivo de prueba actual
- Redmi Note 9 (M2006C3LG), Android 10, MIUI.
- `uiautomator dump` da "null root node" (MIUI bloquea el bridge de accesibilidad
  sin "USB debugging (Security settings)"). Para verificar la UI se usa
  `screencap` + OCR (`rapidocr_onnxruntime` en `/tmp/ocrvenv`) y taps por
  coordenadas. La pantalla se duerme rápido: despertar con KEYCODE_POWER y
  verificar `mWakefulness=Awake` antes de screencap.
- Se detectó `com.bur.odaru.voicetouchlock` (overlay) robando el foco; si vuelve
  a pasar: `adb shell am force-stop com.bur.odaru.voicetouchlock`.
---

## 14. Ronda v0.2.0: release firmado en CI, filtros, chips, progreso, Android 16 y rediseño UI

### Rediseño de UI (tema cohesivo, sin arcoíris)
- `ui/theme/Theme.kt`: tema **fijo** con paleta unificada (ya NO usa dynamic color).
  Un solo color primario (burdeos AO3 `#990000`) y neutros. Fondo y superficies
  en grises cálidos, no blanco puro.
- `ui/components/TagChip.kt`: chip **neutral** (un solo tono de superficie) con un
  **punto de color** por categoría: fandom/marrón, personaje/azul, relación/rosa,
  adicional/verde, warning/gris-rojo. Así los tags no compiten entre sí con colores
  distintos.
- `ui/components/WorkCard.kt`: tarjeta limpia: título + autor, fila de rating/estado,
  chips de tags (fandom, personajes, relación), descripción, fila de stats
  (palabras · caps · visitas · kudos) y fecha de actualización.
- `ui/screens/HomeScreen.kt`: sin arcoíris; pestañas (Tendencias/Lo nuevo/Más
  leídas/Más largas), chips "Explorar fandoms" y sección "Continuar leyendo".

### Filtros de búsqueda nuevos (`SearchScreen.kt` + `Ao3Client.kt` + `Models.kt`)
- **Idioma**: select con códigos ISO (en, es, fr, de, pt, it, ru, ja, zh, ko, etc.)
  → `work_search[language_id]`.
- **Excluir crossovers**: checkbox → `work_search[crossover]=F`.
- **Parte de serie**: checkbox (solo obras que son parte de una serie) →
  `work_search[complete]=F` + `work_search[series_id]` no vacío.
- Filtros enviados como query params en la búsqueda de `/tags/<tag>/works` y la
  búsqueda general.

### Navegación por chips
- Cualquier chip de tag (fandom, personaje, relación, adicional) en resultados y en
  el detalle es clickeable → abre la exploración de ese tag (`onOpenTag` en
  `AppNav.kt`/`MainActivity.kt`).

### Marcador de progreso de lectura
- `data/Store.kt`: `progress: MutableMap<String, Float>` (workId → fracción leída
  del capítulo actual) persistido en SharedPreferences (JSON).
- `ReaderScreen.kt`: guarda progreso periódicamente (scroll del contenido ÷ alto
  total) y al salir.
- `WorkDetailScreen.kt`: sheet de capítulos con **barra de progreso** por capítulo
  leído; el capítulo en curso se marca. La Home muestra "Continuar leyendo" con el
  último capítulo.
- Indicador "Cap. X de Y" en el lector (el mismo que se oculta en fullscreen).

### Android 16
- `targetSdk = 36`, `compileSdk = 37` (SDK 36/API 36 instalado localmente).
- Predictive back: `android:enableOnBackInvokedCallback="true"` en el manifest.

### Release firmado en CI (solo en GitHub)
- `app/build.gradle.kts`: `signingConfigs.release` decodifica `KEYSTORE_BASE64` del
  entorno, escribe el `.jks` en `$buildDir/outputs/keystore/`, y aplica
  `storePassword`/`keyAlias`/`keyPassword` desde env. El buildType release usa ese
  signingConfig **solo si** `KEYSTORE_BASE64` está presente (localmente queda
  unsigned para evitar fallar sin secrets).
- `.github/workflows/build.yml`: el job `build` pasa las 4 secrets como env al paso
  `assembleRelease`, sube `ao3-reader-debug` y `ao3-reader-release`. El job
  `release` (solo en tags `v*`) publica un GitHub Release con el APK firmado
  (`softprops/action-gh-release`).
- **Secrets del repo** (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

### ⚠️ ROTACIÓN DE CLAVE DE FIRMA (importante para el futuro)
- El keystore original se commiteó por error (`.gitignore` tenía `*.keystore` pero
  no `*.jks`; el commit `bd0e396` "Ignore keystore dir" incluyó
  `keystore/ao3-release.jks`). **Ese key quedó expuesto en el historial público.**
- Se rotó: commit `abbfa2f` lo elimina del repo, y un keystore NUEVO con password
  aleatoria (31 chars) se generó y subió a las secrets (22:44). El APK del release
  v0.2.0 está firmado con la key nueva (cert SHA256 `ab7ba84e...` verificado con
  apksigner contra el keystore local).
- **Copia de seguridad del keystore nuevo**: `/home/spin/Documentos/ao3-keys/`
  (`ao3-release.jks` + `keystore_pass.txt`), FUERA del repo. No perderla: sin ella
  no se puede firmar la siguiente versión (mismo APK → upgrade directo).
- **Importante**: NO se debe usar nunca el keystore del historial de git; si se
  necesita rebuildear una versión anterior firmada con la key vieja, no es posible
  con la key nueva (firma distinta → el usuario tendría que desinstalar).

### Estado verificado (2026-08-09)
- CI: debug + release firmado + tests, todo verde (runs 31337021943, 31337247172,
  31337458658). El primer run salió unsigned porque las secrets se crearon ~40s
  DESPUÉS de arrancar el run (GitHub resuelve secrets al inicio del job) → siempre
  configurar secrets antes del primer run.
- GitHub Release **v0.2.0** publicado con `app-release.apk` firmado (~1.96 MB,
  minificado R8) + artefactos en la pestaña Actions.
- Dispositivo: el APK release firmado se instaló limpio y arranca; búsqueda con el
  nuevo card (tags, descripción, stats) y Home rediseñada verificadas por OCR.
---

## 15. Ronda v0.3.0: restructura con bottom nav, descargas selectivas + export .txt, comentarios, ajustes avanzados del lector y rediseño UI

### Restructura de navegación (barra inferior)
- `MainActivity.kt`: ahora hay 3 pestañas raíz con `NavigationBar` — **Inicio** (búsqueda + tendencias + continuar leyendo), **Biblioteca** y **Ajustes**. El stack (`AppNav`) se usa solo para Search/Detail/Reader (pantallas completas, la barra se oculta cuando `nav.stack.size > 1`). `BackHandler`: si hay stack → pop; si no y no estás en Inicio → vuelve a Inicio.
- `HomeScreen.kt` ya NO tiene secciones Favoritos/Descargas (movidas a Biblioteca).
- `LibraryScreen.kt` (nuevo): tabs **Favoritos / Historial / Descargas**. En Descargas cada obra se expande y cada capítulo tiene: abrir, **exportar .txt**, eliminar. Historial tiene "Borrar historial".
- `SettingsScreen.kt` (nuevo): **tema de la app** (Sistema/Claro/Oscuro → `AppThemeMode` en `Theme.kt`), **defaults del lector** (tema, tamaño, interlineado, márgenes, tipografía) e **identidad para comentarios** (nombre + email, guardados en prefs).

### Tema / UI (investigación de UI/UX aplicada)
- `Theme.kt`: `AppThemeMode` (SISTEMA/CLARO/OSCURO), `SemanticColors` (success/favorite/warning/info) expuesto por `LocalSemanticColors` para eliminar hexes sueltos (estrella dorada, checks verdes, etc. ahora vienen del token), y `Ao3Typography` con jerarquía ajustada (line-heights 1.4-1.5, títulos SemiBold).
- Se mantiene el acento único terracota (`#B03A2E`), fondos cálidos sin blanco puro, y los chips neutros con punto de color por categoría (fandom/personaje/relación).

### Descargas selectivas + export .txt
- `Store.kt`: `addDownloadedChapter(workId, title, chapter)` (fusiona con la descarga existente), `removeDownloadedChapter(workId, index)` (borra el registro si era el último). `ChapterInfo` ahora persiste `chapterId`.
- `WorkDetailScreen.kt`: cada fila de capítulo tiene **icono de descarga** (descarga SOLO ese capítulo; al estar descargado muestra check verde y tocarlo pregunta "¿Quitar de descargas?") e **icono de exportar .txt**. Botón principal "Descargar todo" (con %); si ya hay descargas muestra "Descargado (X)" y borra todo.
- `util/ChapterExporter.kt`: exporta `Capitulo_<N>_<Título>.txt` (título sanitizado, hasta 40 chars) con cabecera + texto plano + pie. **Estrategia**: API 30+ → `MediaStore.Downloads`; API 23-29 → pide `WRITE_EXTERNAL_STORAGE` y escribe directo a `Download/` (`requestLegacyExternalStorage="true"` en el manifest, permiso con `maxSdkVersion="29"`).
- ⚠️ **MIUI/Android 10**: `MediaStore.Downloads` rechaza los inserts con `MediaProvider: Ignoring mutation` (¡pero el archivo sí se crea!). Por eso en API 29 se intenta MediaStore y si devuelve null se cae al modo legacy con permiso. En el Redmi de prueba el export funciona (vía MediaStore y vía legacy).
- El contenido para exportar usa `Ao3Parser.htmlToPlainText` (walker de nodos que conserva párrafos y evita espacios antes de puntuación).

### Comentarios de AO3 (ver + publicar como invitado)
- **Importante (cambio de AO3 2023)**: `/works/{id}/comments` redirige a login, y la página de obra carga los comentarios por **AJAX** por capítulo: `/comments/show_comments?chapter_id=<id>` devuelve un fragmento JS cuyo HTML está en `$j("#comments_placeholder").append("...")`.
- `Ao3Parser.parseComments(js)`: extrae el HTML del `append("...")` (desescapa `\"` `\/` `\n`), parsea `ol.thread > li.comment.group` recursivo (`h4.heading.byline` → autor/fecha, `blockquote.userstuff` → contenido, hijos en `ol.comment.children`), devuelve lista plana con `depth`.
- `Ao3Client.getComments(chapterId)` y `postComment(workId, chapterId, name, email, content, replyTo)`: el POST de invitado usa el `authenticity_token` de la página + `comment[name]`, `comment[email]`, `comment[comment_content]` (los campos del formulario guest real de AO3). Devuelve el mensaje de error de AO3 si lo rechaza, o null en éxito.
- `WorkDetailScreen.kt`: sección **"Comentarios (N)"** al final del detalle con **selector de capítulo** (solo funciona si el capítulo tiene `chapterId`, es decir, template moderno con `select#selected_id`; en obras single-chapter antiguas muestra "no disponibles"), hilos con autor/fecha/contenido, botón **Responder** por comentario y formulario "Deja un comentario" (nombre/email precargados de Ajustes).
- Verificado en dispositivo con Evitative (29 caps, 9120 comentarios): hilos reales con fecha "Tue 11 Feb 2020 06:26PM UTC" y Responder + IDs. **No se probó publicar un comentario real** (para no ensuciar obras ajenas); el envío quedó implementado y listo para probar con datos propios.

### Lector: ajustes avanzados + AMOLED
- El tema **"Negro" ahora se llama "AMOLED"** (mismo `#000000` — ya era AMOLED de facto).
- Nuevos ajustes en el panel del lector y en Ajustes: **Interlineado** (1.2–2.4, default 1.75) y **Márgenes** (Estrechos/Normales/Amplios), además de tamaño de letra y Serif/Sans existentes. Se persisten en `Store.Prefs` (lineHeight, margins).

### Estado verificado (2026-08-09, Redmi/Android 10 + OCR)
- Tests 7/7 (Ao3ParserTest 4 + Ao3CommentsTest 3, con snapshot real `comments.js`).
- En dispositivo: barra inferior Inicio/Biblioteca/Ajustes ✓, Biblioteca con 3 tabs ✓, Ajustes completo ✓, descarga selectiva del capítulo 1 ✓ (aparece en Descargas "1 capítulo · sin conexión"), export `Capitulo_1_Oh, Naruto, Naruto.txt` con contenido correcto ✓, comentarios de Evitative cargados ✓, panel del lector con Interlineado/Márgenes/AMOLED ✓.
- Nota de pruebas: el teclado de MIUI tapa la barra inferior (cerrar con BACK antes de tocar la barra). Los iconos de las filas se localizan por píxeles (el OCR no ve iconos).

### Release publicado
- **v0.3.0** (versionCode 3): GitHub Release con `app-release.apk` firmado (2.05 MB, key rotada `ab7ba84e...`).
  Creado con `git tag v0.3.0` + push (el job `release` del workflow publica el APK automáticamente).
## 16. v0.4.0 — Lector paginado, cola de descargas + notificación, kudos y marcadores

### Lector paginado (activable, no sustituye al scroll)
- Nuevo pref `Store.Prefs.paged` (persistido). Toggle "Scroll continuo / Paginado" en el panel de
  ajustes del lector y en Ajustes → Lector (por defecto).
- `util/ReaderPaging.kt` (funciones puras JVM, probadas): `htmlToLines(html)` convierte el HTML del
  capítulo en bloques (`Line`/`Segment`) conservando párrafos, encabezados, citas, listas, código,
  `hr` y énfasis (b/i/u/a); `packPages(lines, targetChars)` los empaqueta en páginas **sin partir
  párrafos** (presupuesto ≈ 2300 chars, escala con el tamaño de letra).
- `ReaderScreen`: en modo paginado se muestra un `HorizontalPager` (beyondViewportPageCount=1) con
  una página por elemento; cada página es un `Text` con `AnnotatedString` (estilos inline) usando la
  paleta del tema del lector. El swipe horizontal cambia de página; el vertical desplaza dentro de la
  página. Cabecera (obra + capítulo) solo en la página 1; pie "· X de Y ·".
- Progreso: el ratio de lectura se guarda igual que el scroll (`scrollRatio` 0..1) — en paginado se
  deriva de `currentPage/(pages-1)` y se restaura al abrir/cambiar de capítulo. La barra inferior
  muestra "cap X de Y · pág P de Z". Tap en el texto sigue alternando chrome/full-screen.
- Tests: `ReaderPagingTest` (5 tests): párrafos/estilos/separadores, párrafos vacíos, presupuesto sin
  partir líneas, página única, entrada vacía.

### Cola de descargas + notificación
- `data/DownloadQueueService.kt` (nuevo): foreground service con cola FIFO. `WorkDetailScreen` →
  "Descargar todo" enqueuea el trabajo con `enqueueIntent(context, workId, title, chapters)`
  (capítulos serializados a JSON en los extras del Intent). El servicio descarga capítulo a capítulo
  vía `Ao3Client.getChapter` (politely: serializado con delays), guarda con
  `store.addDownloadedChapter` y actualiza la notificación de progreso ("Capítulo X de Y", barra).
  Al terminar: notificación "Descarga completada" que **persiste** (`stopForeground(STOP_FOREGROUND_DETACH)`
  + `stopSelf()` — ¡importante: sin el stopSelf el servicio queda en foreground para siempre!).
- Estado en vivo: `companion object state: MutableStateFlow<QueueState>` (active/workId/title/done/
  total/completedAt) que las pantallas observan con `collectAsState()`.
- `WorkDetailScreen`: el botón "Descargar todo" muestra "X/Y" + barra mientras corre (y pide
  `POST_NOTIFICATIONS` en API 33+). Al completarse, snackbar "Descarga completada: N capítulos".
- `LibraryScreen`: banner con progreso en la pestaña Descargas mientras hay cola activa, y **refresh
  en vivo** de la lista (observa `queueState` — sin esto la pestaña no muestra los capítulos nuevos
  hasta re-entrar).
- Manifest: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` +
  `<service android:name=".data.DownloadQueueService" android:foregroundServiceType="dataSync">`.

### Kudos (invitado anónimo)
- AO3 no pide nombre/email para kudos: `POST /kudos` con `authenticity_token`,
  `kudo[commentable_id]`, `kudo[commentable_type]=Work`, `commit=Kudos ♥`. Verificado: el formulario
  real solo tiene esos campos; un POST a un work inexistente devuelve 404 (path de error).
- `Ao3Client.postKudos(workId)`: GET del work page (token + detección de "You have already left
  kudos"), POST, y parseo del resultado (éxito → null; ya dado → mensaje; errores → mensaje).
- UI: botón "Dar kudos" en el detalle (corazón), deshabilitado tras el envío ("Kudo enviado") y
  mientras procesa. Verificado en dispositivo con obra real (el kudo anónimo queda registrado).

### Marcadores (decisión del usuario: sin login)
- Los marcadores de AO3 **exigen cuenta** (`/bookmarks/new?work_id=X` → 302 a login para invitados).
  El usuario eligió NO implementar login: el botón "Marcar en AO3" abre la página de marcador en el
  navegador del sistema (`Intent.ACTION_VIEW` a `/bookmarks/new?work_id=X`). Verificado (abre el
  resolver de apps).
- Si algún día se quiere login: el formulario de AO3 es `POST /users/login` con `authenticity_token`,
  `user[login]`, `user[password]`, `user[remember_me]` (verificado); los marcadores se crean con
  `POST /bookmarks` (autenticado, necesita extraer los campos del form).

### Extras
- Bug fix: los "Lector (por defecto)" de Ajustes **no persistían** (cambiaban estado local sin
  guardar). Ahora cada control persiste al cambiar.
- Versión 0.4.0 (versionCode 4). Tests JVM 12/12 (3 comentarios + 4 parser + 5 paginación).

### Verificación en dispositivo (MIUI, Android 10)
- Paginado: lector → ajustes → "Paginado": "1/29 · 1/16", swipe horizontal → "2/16". ✓
- Cola: "Descargar todo" en obra con 4/29 capítulos publicados → notificación "Capítulo 2 de 4" →
  "Descarga completada" persistente → servicio detenido (dumpsys sin ServiceRecord) → Biblioteca →
  Descargas se actualiza sola ("4 capítulos · sin conexión"). ✓
- Kudos: botón → "Kudo enviado" (kudo real en obra ajena, como invitado). ✓
- Marcar: abre el navegador. ✓
- Nota: el work page de obras con "N/M" capítulos lista solo los N publicados — la cola descarga
  exactamente esos N (no es un bug).

### v0.4.1 (hotfix tras revisión)
- Servicio de cola: `onDestroy` resetea el estado si muere a mitad (evita banner/botón "activo" colgados);
  cola sincronizada (race entre hilo principal y corutina IO); notificación final cuenta éxitos
  ("N de M capítulos guardados" si hubo fallos); icono de notificación drawable vectorial (no mipmap).

---

## 17. Ronda v0.7.5: robustez de persistencia y red, pull-to-refresh y deuda técnica

### Robustez
1. **Store thread-safe + escritura atómica** (`data/Store.kt`): todos los métodos públicos
   sincronizan en un `lock` (main + servicio de descargas mutan el mismo JSON). El snapshot se
   captura en el caller thread (nunca un torn read) y un **single-writer "last-wins"** garantiza
   que el último persist es el que queda en disco (antes dos persists rápidos podían quedar en
   desorden). La escritura es **atómica**: temp file + rename, así un crash a mitad no corrompe
   `ao3_library.json`. `Store` ahora acepta un `File` (constructor `Context` se mantiene), lo que
   lo hace testeable en JVM.
2. **CookieJar thread-safe** (`Ao3Client.kt`): `InMemoryCookieJar` usa `ConcurrentHashMap`
   (seguro aunque `getConcurrent` dispare dos peticiones en paralelo).
3. **Cachés de tags acotadas** (`tagPages` 40, `canonicalTagCache` 200, `facetsCache` 50): nueva
   `BoundedMap` (LRU por acceso) en lugar de `ConcurrentHashMap` sin límite.
4. **429/Retry-After** (`Ao3Client.kt`): `fetch`/`post` detectan HTTP 429, leen `Retry-After` y
   esperan (cap 30 s) en vez de reintentar a ciegas.
5. **Serialización URL-safe** (`AppNav.kt` + `Models.kt`): `Route.serialize()` y
   `SearchFilters.serialize()` URL-encoden cada campo; una consulta con `\u0001`/`\u0002` (o
   cualquier carácter) ya no rompe el round-trip al rotar/navegar. Helpers `urlEncode`/`urlDecode`.

### Funcionalidad
6. **Pull-to-refresh en el detalle** (`WorkDetailScreen.kt`): `PullToRefreshBox` + nuevo
   `Ao3Client.refreshWork(id)` que salta cachés en memoria y disco para traer datos frescos
   (p.ej. capítulos nuevos) a demanda. La caché se mantiene para el acceso rápido a obras
   (re-seleccionar no re-parsea todo).
7. **Links del capítulo abren en el navegador** (`ReaderScreen.kt`): el WebView ya no traga los
   enlaces del texto; `shouldOverrideUrlLoading` los entrega al sistema con `ACTION_VIEW`.
8. **Buscar en el lector (modo scroll) arreglado** (`ReaderScreen.kt`): antes las flechas
   Anterior/Siguiente estaban muertas y el contador no avanzaba; ahora `findActive`/`findCount`
   guían la navegación por coincidencias vía `jsGoToMatch`.
9. **Export .txt espera al permiso** (`util/ChapterExporter.kt`): en API 23-29, si falta
   `WRITE_EXTERNAL_STORAGE`, se pide y la exportación real (y su confirmación) corre DESPUÉS de
   concederlo; si se deniega, avisa. Antes reportaba "Guardado" sin haber escrito nada.
10. **Avatares con caché en disco** (`util/AvatarImages.kt` + `Ao3App.kt`): `AvatarImages.init()`
    desde `Ao3App.onCreate`; bytes crudos por URL en `cacheDir/avatars` para no re-descargar en
    arranques en frío.

### Limpieza
11. **`rememberModalBottomSheetState` migrado** a `rememberBottomSheetState(initialValue =
    SheetValue.Hidden)` (WorkCard, SearchScreen, ReaderScreen) — se eliminan los 2 warnings de
    deprecación.
12. **Dependencia duplicada quitada** (`app/build.gradle.kts`): el `ui-tooling-preview`
    hardcodeado duplicaba el del catálogo. `material-icons-extended` SE MANTIENE (es necesario).

### Tests (JVM, ~60 verdes)
- **`StoreTest`** (nuevo, 8 tests): favoritos, historial (orden + progreso), descargas (merge +
  borrado), prefs, cola pendiente, round-trip a disco entre instancias y JSON corrupto.
- **`SearchFiltersSerializationTest`** (nuevo, 4 tests): round-trip con caracteres hostiles
  (`\u0001`, `\u0002`, comillas, `&`, `+`, `%`, acentos, emoji) y estabilidad de cache key.
- **`RouteSerializationTest`** (nuevo, 5 tests): round-trip de Search/Detail/Reader/Author con
  consultas y usernames hostiles.

> ✅ **Compilado y verificado el 17/08/2026**: SDK Android instalado en `/opt/android-sdk`
> (platform 37.0, build-tools 37.0.0, JDK 21), `local.properties` apunta a él. **67/67 tests
> JVM verdes** y `assembleDebug` + `assembleRelease` OK (R8). APK release firmado con la
> clave de debug (instalable): `releases/ao3-reader-v0.7.5.apk`.
> Para un release firmado con la clave real, alimentar `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`
> (como hace el CI) en el build.

---

## 18. Ronda v0.7.6: TTS, backup, feed de actualizaciones, escalado de Store y localización

Compilado y verificado el 17/08/2026. **75/75 tests JVM verdes** (se añadieron StoreTest +
Ao3FeedTest). APK release firmado con clave de debug: `releases/ao3-reader-v0.7.6.apk`.

### Funcionalidad
1. **Lectura en voz alta (TTS)** (`util/ReaderTts.kt` + `ReaderScreen.kt`): botón play/stop en la
   barra superior del lector, velocidad 0.5×–2× en el panel de ajustes (`prefs.ttsRate`),
   **auto-avance al siguiente capítulo** al terminar, y pausa al pasar la app a segundo plano.
2. **Copia de seguridad de la biblioteca** (`SettingsScreen.kt` + `Store.exportBackup`/
   `importBackup`): exporta/importa favoritos + historial + descargas + ajustes a un JSON
   (SAF), con diálogo de confirmación antes de restaurar (no reversible).
3. **Comprobar actualizaciones** (`WorkDetailScreen.kt` + `Ao3Client.getWorkFeed`): consulta el
   **Atom feed** de la obra (sin tocar el HTML) y avisa si hay capítulos nuevos o si estás al día,
   con la fecha de la última actualización.
4. **Timeout global de carga** (`Ao3Client`): cada petición lógica tiene deadline de 45 s
   (POST 30 s) sobre TODOS los reintentos + backoff, y el detalle de obra tiene botón **Cancelar**
   (carga en Job cancelable). El worst-case ya no es "varios minutos de spinner".

### Robustez / rendimiento
5. **Store con dos archivos** (`ao3_library.json` pequeño + `ao3_*_downloads.json` grande):
   el progreso de lectura (cada 3,5 s) ya no reescribe los MB de capítulos descargados. El
   archivo de descargas se deriva del nombre del raíz (cada instancia/test tiene el suyo).
   Migración automática de bibliotecas antiguas de un solo archivo.
6. **Carrera del single-writer arreglada**: un persist cancelado podía consumir el snapshot y
   dejar el archivo sin escribir. Ahora se lee el snapshot en el momento de escribir y solo se
   limpia si sigue siendo el más nuevo (last-wins garantizado).
7. **Doble recarga del WebView eliminada** (`ReaderScreen`): se guarda el último HTML cargado y
   solo se recarga cuando cambia de verdad (antes recargaba dos veces al abrir).

### Localización (parcial, coherente)
8. `res/values/strings.xml` + `res/values-en/strings.xml` con ~70 strings: pestañas inferiores,
   temas del lector, **Ajustes completo**, **Inicio**, **Biblioteca** y **WorkCard**.
   `BottomBarDestination`/`AppTab`/`ReaderTheme` ahora usan `@StringRes labelRes`. El lector,
   la búsqueda y el detalle siguen en español (siguiente paso).

### Tests
- `StoreTest` +6: descargas en su propio archivo, migración legacy, backup round-trip,
  import rechaza basura, persistencia de `ttsRate`.
- `Ao3FeedTest` +4: parsing del Atom feed (conteo de capítulos, fecha, malformados).

> La versión pasó de 0.7.5 → **0.7.6** (versionCode 39). APK **debug** verificado en
> `app/build/outputs/apk/debug/app-debug.apk` (22 MB, instalable). No se generó release
> (decidido en sesión); para la firma real, alimentar las env vars del keystore.

## Ronda 0.7.6b — Optimización de rendimiento (gama baja)

Enfocado en código más eficiente (sin recortar funciones ni animaciones).

### Lector
- **Paginación fuera del hilo principal**: `htmlToLines` (Jsoup) se ejecuta en
  `Dispatchers.Default` con overlay "Cargando capítulo…"; antes bloqueaba la UI
  al abrir cada capítulo en CPUs lentas (A53).
- **Caché de `packPagesToViewport`**: la medición de texto (búsqueda binaria con
  `TextMeasurer`) se memoiza por `(hash de líneas, tamaño, interlineado, serif,
  márgenes, ancho, alto)`. Rotar o recomponer ya no re-mide todo el capítulo.
- **WebView sin recargar al cambiar tema/tamaño**: el CSS usa variables CSS
  (`--bg`, `--fg`, `--font-size`…) y un helper JS `applyReaderPrefs()` los aplica
  en vivo. Antes cada toque en "A"/tema reconstruía y re-layouteaba el capítulo
  entero (~1 s de congelamiento en gama baja). Ahora solo recarga cuando cambia
  el contenido (capítulo/traducción).

### Memoria / listas
- **Avatares con downscale**: se decodifican con `inSampleSize` (máx. 256 px);
  antes un avatar 4K ocupaba ~33 MB en RAM. Se muestran a ≤84 dp.
- **Home lazy**: "Continuar leyendo" ahora vive en un `LazyColumn` (toda la
  pantalla es lazy); antes componía cada fila de historial al abrir.
- **WorkCard memoiza tags**: los grupos de tags y chips se calculan con
  `remember(work)`; antes se recomputaban en cada recomposición al hacer scroll.

### Verificación
- `compileDebugKotlin` OK, **75/75 tests JVM verdes**.
- APK debug v0.7.6 (versionCode 39) en `dist/ao3-reader-v0.7.6-debug.apk`
  (22 MB, instalable). Sin release.

## Ronda 0.7.7 — Fix de bugs reportados en 0.7.6

### 1. Progreso de lectura (barra % + capítulo off-by-one)
- **Causa raíz**: el guardado al salir dependía de un `readScrollRatio` asíncrono
  (evaluateJavascript) que podía fallar/races cuando el WebView se destruía → se
  perdía el % del capítulo y, al avanzar y salir rápido, quedaba el capítulo
  anterior (off-by-one, agravado por la precarga del siguiente).
- **Fix**: un scroll listener JS (`onScrollRatio`) reporta el ratio 0..1 en cada
  scroll al estado `currentRatio`; `saveProgressNow()` ahora guarda SÍNCRONO con
  ese valor (sin round-trip al WebView), así el guardado al salir siempre aterriza
  en el capítulo correcto con su % real. También arregla el orden de "Continuar
  leyendo" (el `at` se actualiza de forma fiable → la obra leída sube arriba).

### 2. Search bar del lector
- `android:windowSoftInputMode="adjustResize"` en el manifest: en modo inmersivo
  el teclado podía tapar/desplazar la barra de búsqueda (adjustPan la empujaba
  fuera). Ahora la ventana redimensiona y la búsqueda queda usable.
- El botón de búsqueda además ahora es alcanzable (ver top bar).

### 3. Top bar del lector saturada
- Rediseño: **Volver | Título | Buscar | TTS | Ajustes | ⋮** (Traducir + Tema
  claro/oscuro viven en el menú desplegable). Pasa de 6 botones a 4.

### 4. TTS no funcionaba
- **Causa**: los capítulos largos (>~4k chars) fallan/truncan en varios motores
  TTS → parecía muerto. Ahora el texto se divide en trozos ≤4000 chars (por
  límites de palabra) y se leen en secuencia.
- El parseo de texto (Jsoup) se movió fuera del hilo principal y se muestra un
  Toast si el dispositivo no tiene motor de voz.

### 5. Modo paginado (páginas medio vacías)
- Se eliminó la caché de paginación (key por `lines.hashCode()`), la única
  adición nueva a la paginación y sospechosa de devolver páginas incorrectas.
  El algoritmo de medición queda idéntico al de la versión que funcionaba.

### 6. Orden de "Continuar leyendo"
- Resuelto por el fix del guardado síncrono (el `at` se actualiza al salir), la
  obra seleccionada vuelve a subir al primer puesto.

### 7. Búsquedas
- Hygiene de corrutinas en `Ao3Client.getInternal`/`post`: ya no se traga la
  `CancellationException` (incl. el timeout del deadline) como error retryable,
  lo que podía cancelar corrutinas del caller y lanzar errores espurios.
- El round-trip de `SearchFilters.serialize/parse` ya estaba cubierto por tests
  y es correcto. Si la búsqueda principal sigue fallando, revisar AO3/Cloudflare
  (red).

### Verificación
- `compileDebugKotlin` OK, **75/75 tests JVM verdes**.
- APK **debug v0.7.7** (versionCode 40) en `dist/ao3-reader-v0.7.7-debug.apk`
  (22 MB, instalable). Sin release.

## Ronda 0.7.8/0.7.9 — Búsqueda rota: diagnóstico Cloudflare + UA

### Síntoma
- Búsqueda desde Inicio quedaba en **spinner infinito**; el navegador del
  teléfono iba bien pero la app no. No era regresión: v0.7.3/v0.7.4 funcionaban
  antes y dejaron de hacerlo → el entorno (AO3/Cloudflare) cambió.

### Diagnóstico (medido)
- **Cloudflare tarpit por fingerprint de OkHttp**: con el UA de la app (o uno
  de Chrome), ~1 de cada 5 requests se cuelga ~60s o muere con **HTTP 525**,
  mientras curl/OpenSSL y Chrome real pasan. El tarpit es a nivel **TLS
  fingerprint (JA3/JA4)**, no de headers ni protocolo.
- El UA propio `AO3-Lector/0.1…` además recibía slow-down (14-16s vs ~1s).
- Test de diagnóstico en CI (`SearchLiveTest`): 5 queries con HTTP/1.1 → 5/5
  OK pero lentas; con HTTP/2 → 1 de 5 falla con 525 tras 58s de stall.

### Fixes
- **v0.7.8**: UA de la app → UA real de Chrome (`BROWSER_UA`). No bastó (el
  tarpit es por fingerprint TLS, no UA).
- **v0.7.9**: forzar **HTTP/1.1** en OkHttp (`.protocols(listOf(Protocol.HTTP_1_1))`)
  — 5/5 completan pero sigue lento y el tarpit es probabilístico (~20-40%).

### Verificación
- CI verde en ambos; release firmado v0.7.8 (code 41) y v0.7.9 (code 42).

## Ronda 0.7.10 — Cronet: fingerprint de Chrome real

### Qué
- Nuevo `CronetBridge.kt`: interceptor OkHttp de red que ejecuta la petición a
  través de **Cronet** (la pila Chromium de Chrome vía Play Services). Cloudflare
  le da el mismo trato que a un navegador real (fingerprint TLS idéntico).
- Dependencia `play-services-cronet:18.1.1` (catálogo de versiones + build.gradle).
- `Ao3Client` recibe `context: Context?` opcional (los tests JVM lo omiten y
  caen al OkHttp normal); `AppContainer` pasa `applicationContext`.

### Detalles de implementación
- El engine se construye una vez con `CronetProvider.getInstalledProvider()`;
  si no hay provider (sin GMS), `engine == null` → fallback a OkHttp puro.
- **Headers filtrados** al reenviar a Cronet: `Accept-Encoding` (Cronet negocia
  y descomprime él mismo — evita doble descompresión), `Host`, `Connection`,
  `Content-Length`, `Transfer-Encoding` (Cronet los maneja / los rechaza).
- **Cookies de redirecciones**: Cronet internamente sigue redirects pero tira
  los `Set-Cookie` intermedios (clave en el login) → se capturan y re-inyectan
  en la respuesta final para que el `CookieJar` de OkHttp los vea.
- Upload de POST (login) vía `UploadDataProvider`; timeout global de 75s en el
  latch + cancel.

### Pendiente
- Validar en el teléfono real (release): la búsqueda debe completar rápido y
  sin spinner infinito. Si aún fallara, revisar logcat + `SearchLiveTest` en CI.
