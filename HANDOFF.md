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
  través de **Cronet** (la pila Chromium de Chrome). Cloudflare le da el mismo
  trato que a un navegador real (fingerprint TLS idéntico).
- Dependencia **`org.chromium.net:cronet-embedded:141.7340.3`** (catálogo +
  build.gradle). **No** `play-services-cronet` (trae `cronet-api` + `cronet-shared`,
  ambos con namespace `org.chromium.net`). Ojo: **cronet-embedded 141 también
  publica 5 AARs transitivos** (`httpengine-native-provider`, `cronet-common`,
  `cronet-api`, `cronet-shared`) **todos con el mismo namespace
  `org.chromium.net`** — y AGP 9 falla el manifest merger por defecto.
- **Fix documentado por JetBrains** (skill `kotlin-tooling-agp9-migration`):
  AGP 9 cambió el default de `android.uniquePackageNames` a `true` (en AGP 8
  era warning; en 9 es error duro). Se desactiva en `gradle.properties` con
  `android.uniquePackageNames=false` — el paquete duplicado es inofensivo en
  runtime (se funde en un solo APK).
- `abiFilters` = `arm64-v8a` + `armeabi-v7a` (cronet trae ~14MB de .so por
  ABI; los emuladores x86 no se usan en CI — solo se compila).
- `Ao3Client` recibe `context: Context?` opcional (los tests JVM lo omiten y
  caen al OkHttp normal); `AppContainer` pasa `applicationContext`.

### Detalles de implementación
- Engine construido con `CronetEngine.Builder(context).enableBrotli(true)`
  (la API 141 ya no tiene `CronetProvider.getInstalledProvider` ni
  `engine.getExecutor()` — se usa un `ExecutorService` propio). Si falla,
  `engine == null` → fallback a OkHttp puro.
- **OJO — application interceptor, NO network interceptor**: OkHttp 5 exige que
  los network interceptors llamen `chain.proceed()` exactamente una vez (si
  devuelves tu propia Response sin proceed, lanza
  `IllegalStateException: "network interceptor … must call proceed() exactly once"`,
  visto en producción). `addInterceptor` (application) sí puede cortocircuitar.
  Consecuencia: el `BridgeInterceptor` de OkHttp corre DESPUÉS, así que el
  bridge maneja el `CookieJar` manualmente (`loadForRequest` antes del request
  + `saveFromResponse` con los Set-Cookie finales y de redirects).
- **Headers filtrados** al reenviar a Cronet: `Accept-Encoding` (Cronet negocia
  y descomprime él mismo — evita doble descompresión), `Host`, `Connection`,
  `Content-Length`, `Transfer-Encoding`, `Cookie` (se agrega desde el jar).
- **Cookies de redirecciones**: Cronet internamente sigue redirects pero tira
  los `Set-Cookie` intermedios (clave en el login) → se capturan y se guardan
  en el jar. Los lookups de headers de `UrlResponseInfo` son case-insensitive
  (HTTP/1.1 pasa el casing del server).
- Upload de POST (login) vía `UploadDataProvider`; timeout global de 75s en el
  latch + cancel.

### Resultado
- **Cronet NO funcionó en el teléfono** (la búsqueda seguía colgada) y fue
  **descartado en v0.7.11** a favor de la estrategia WebView de CO3 (ver
  sección siguiente). El código de esta sección quedó como historia; el
  `CronetBridge.kt` fue eliminado.

---

## Ronda 0.7.11 — Búsqueda arreglada: fallback WebView (estrategia CO3)

### Qué
- **`WebViewFetcher.kt`** (nuevo): capa de red de respaldo que descarga páginas
  de AO3 a través del **WebView del sistema** (el mismo Chromium que Chrome)
  en lugar de OkHttp. Copia la estrategia probada de **CO3**
  (https://github.com/tbvns/CO3), un cliente AO3 que sí funciona.
- **Por qué**: Cloudflare tarpit al fingerprint TLS de OkHttp (medido:
  requests a AO3 que se cuelgan ~60s o mueren con HTTP 525, mientras
  curl/OpenSSL y Chrome pasan). El WebView ES Chromium → AO3 lo trata como
  navegador, y al ejecutar JS **resuelve los challenges de Cloudflare solos**
  (si hay captcha, se muestra al usuario para que lo resuelva).
- **`Ao3Client.fetch()`** reescrito:
  1. OkHttp con **readTimeout de 8s** (falla rápido ante el tarpit);
  2. si el error es de Cloudflare (códigos 403/525/418/520/522/503, timeout
     o página `_cf_chl_opt`/`challenge-platform` con HTTP 200) → **WebView**;
  3. reintentos posteriores usan OkHttp normal (páginas legítimamente lentas).
- **Cronet eliminado por completo**: `CronetBridge.kt` borrado, dependencia
  `cronet-embedded` y `abiFilters` fuera del build → el APK release vuelve de
  ~13,5 MB a ~2,2 MB.
- `WebViewFetcher` se registra en `AppContainer` vía
  `registerActivityLifecycleCallbacks` (rastrea la Activity actual); el WebView
  vive en un overlay 1x1 transparente sobre el decor view (no bloquea toques)
  y se hace visible solo mientras un captcha de Cloudflare requiere al usuario.

### Cómo funciona (flujo)
```
fetch(url)
  └─ OkHttp rápido (8s)
       ├─ OK y sin challenge → devuelve body
       ├─ challenge CF (_cf_chl_opt) → WebView.fetch(url)
       └─ error CF (403/525/418/520/522/503) o timeout → WebView.fetch(url)
             └─ WebView carga la URL (Chromium real)
                  ├─ JS detector: challenge? → muestra captcha al usuario
                  └─ success → extrae document.documentElement.outerHTML
```

### Notas de implementación
- El WebView usa su **User-Agent por defecto** (el de Chrome real) — no
  personalizar.
- `@JavascriptInterface` del puente queda cubierto por la regla proguard
  existente (`-keepclassmembers … @android.webkit.JavascriptInterface`).
- Los POST (login/kudos/comentarios) NO pasan por WebView (igual que CO3:
  durante CF mode esas funciones degradan).
- Timeout del fetch WebView: 90s (el captcha visible no tiene timeout — el
  usuario resuelve a su ritmo).

### Pendiente
- **Validar en el teléfono (release v0.7.11)**: la búsqueda debe completar,
  ahora sí, a través del WebView cuando OkHttp se cuelgue. Si aún fallara,
  logcat: buscar `WebViewFetcher` en acción y DNS/TCP de la app.

## Ronda 0.7.12 → 0.7.16 — El puente JS→Kotlin (canal console.log)

### Qué
- El canal `@JavascriptInterface` NO llegaba en release: R8 borraba la
  anotación y el WebView no exponía ningún método al JS → `onPageFinished`
  disparaba pero el HTML nunca volvía. Verificado en el dex del APK:
  `grep -c JavascriptInterface` = 0. Reglas proguard específicas
  (`-keepclasseswithmembers … @android.webkit.JavascriptInterface`) lo
  restauraron.
- Siguiente cambio: **canal `console.log`** (el equivalente Android-nativo del
  `window.ReactNativeWebView.postMessage` de CO3) — inmune a R8, sin
  anotaciones. Overlay del WebView a **pantalla completa INVISIBLE** (un
  WebView 1x1 puede negarse a ejecutar JS en MIUI).
- `getInternal()` reestructurado: el WebView corre con **timeout propio de
  120s FUERA del deadline global de 45s** (antes el challenge mataba el fetch
  justo cuando el WebView completaba).

### El bug que quedaba (v0.7.16, reportado por el usuario)
- El WebView SÍ devolvía el HTML completo (verificado en logcat:
  `HTML completo: 110000 chars` de la búsqueda) pero la UI se quedaba en
  spinner infinito.
- **Causa raíz**: `isCfChallenge()` buscaba `cdn-cgi/challenge-platform`
  como marcador de challenge — pero Cloudflare **inyecta ese script pasivo
  (`jsd/main.js`) en TODAS las páginas legítimas de AO3** (verificado:
  presente 1 vez en el HTML real de búsqueda descargado con urllib). El
  HTML bueno del WebView se descartaba como "challenge" → caía al retry
  loop de OkHttp (tarpit) → spinner infinito.
- **Fix (v0.7.17)**: `isCfChallenge()` ahora solo matchea challenges
  ACTIVOS: `_cf_chl_opt`, `challenges.cloudflare.com` y `turnstile`. El
  `DETECT_JS` del WebView usa el mismo criterio (añadido el iframe de
  `challenges.cloudflare.com`).
- Lección: nunca usar `challenge-platform`/`cdn-cgi/challenge-platform`
  como señal de bloqueo — es el detector pasivo que Cloudflare sirve en
  cada página.

## Ronda 0.7.18 — WebViewFetcher endurecido (spinner infinito definitivo)

### Qué pasaba (v0.7.17, reproducido de forma autónoma en el dispositivo)
- El WebView SÍ devolvía el HTML de la búsqueda (`HTML completo: 103733
  chars`) y el parseo ocurría (GC a los ~20s), pero la corrutina de búsqueda
  nunca terminaba: **spinner infinito durante 27+ minutos** y el hilo
  principal bloqueado (MIUI mostraba su overlay `retrievingView`, su versión
  del ANR).

### Causas raíz identificadas (estáticas + dumpsys)
1. **Timeout dependiente del hilo principal**: `withTimeoutOrNull` sobre
   `Dispatchers.Main` no puede dispararse si Main está ocupado → el fetch
   del WebView (y el gate global que lo envuelve) se quedaba bloqueado
   para SIEMPRE, colgando todas las peticiones posteriores.
2. **WebView compartido entre requests**: el WebView long-lived seguía
   ejecutando el JS de la página anterior; inyecciones retrasadas de
   `DETECT_JS` podían contaminar el `chunkBuffer` del request siguiente y
   el overlay podía quedar visible indefinidamente.
3. **Modo challenge sin salida**: si el challenge de Cloudflare no se
   auto-resolvía, el deferred nunca se completaba (solo lo cerraba el
   timeout… que a su vez dependía de Main).

### Fixes aplicados
1. **WebView FRESCO por fetch**: se crea, carga, y se destruye (`teardown`:
   removeView del overlay) al terminar. Cero estado compartido entre
   requests, cero inyecciones viejas.
2. **Timeout independiente de Main**: `fetch()` hace el `await` con
   `withContext(Dispatchers.Default)`, así el timeout dispara aunque Main
   esté ocupado. Además `fetch()` NUNCA lanza: devuelve null.
3. **Challenge acotado a 25s**: si el challenge no se auto-resuelve en
   25s, el fetch falla limpio (`settle(null)`) en vez de bloquear la app.
4. **Overlay siempre retirado**: `teardown()` lo quita del decorView tras
   cada fetch (visible o no).
5. **Logs de diagnóstico** en `WebViewFetcher` (fetch/enqueue/processNext/
   settle/challenge/chunks) y en `Ao3Client.getInternal` (OkHttp rápido
   OK/falló, WebView OK X chars, cae al retry loop) y `searchFresh` (HTML
   X chars, parseó N obras) y `SearchScreen.fetchFirst` (OK/falló en Xms).

### Estado
- v0.7.18 pusheado a GitHub; la CI compila el release firmado.

## Ronda 0.7.19 — Causa raíz REAL del spinner: scrollToItem colgado

### Qué pasó (v0.7.18 instalado, reproducido con logs nuevos)
- La búsqueda COMPLETÓ la red: `searchFresh: HTML 96567 chars` (cache de
  disco) → `parseó 20 obras` en ~2s. Pero la UI seguía con spinner y la
  corrutina nunca llegaba al `finally` (ni un solo log de SearchScreen).

### Causa raíz (verificada en el dex + lógica de Compose)
- En `fetchFirst()` el único punto suspendible entre el retorno de
  `search()` y el log final es `listState.scrollToItem(0)`. Y se llama
  CON `loading = true` todavía: la rama `loading ->` muestra el spinner y
  el `LazyColumn` NO está compuesto. `scrollToItem` sobre un
  `LazyListState` desacoplado **se suspende para siempre** (espera una
  confirmación de layout que nunca llega) → `finally { loading = false }`
  jamás se ejecuta → spinner infinito. Es el clásico pitfall de Compose.
- Por eso antes “funcionaba”: el reset de scroll (`scrollToItem(0)`) se
  añadió en una ronda reciente, coincidiendo con la rotura de la búsqueda.

### Fix
- `fetchFirst()`: primero `loading = false` (en `finally`), y el scroll se
  hace DESPUÉS con `withTimeoutOrNull(3s)` como red de seguridad — un
  scroll colgado ya no puede bloquear la corrutina.
- Lo mismo en el `LaunchedEffect(Unit)` de restauración de scroll
  (también acotado a 3s).

### Estado — ✅ VERIFICADO EN EL DISPOSITIVO (18:20, 17 ago 2026)
- v0.7.19 instalado (release firmado de CI) y **la búsqueda funciona de
  punta a punta**:
  - Búsqueda en vivo "sherlock": `OkHttp rápido OK (106302 chars)` →
    `parseó 20 obras` → `fetchFirst OK: 20 obras en 2369ms` →
    `fetchFirst terminó tras 2370ms`. La UI muestra los resultados
    (cards con título/autor/tags/stats).
  - Búsqueda desde caché "naruto": instantánea, 20 obras.
  - El camino OkHttp rápido funcionó en vivo (Cloudflare no tarpiteó en
    esa sesión); el fallback WebView (WebView fresco por fetch, timeout
    independiente de Main, challenge acotado a 25s) queda listo para
    cuando Cloudflare bloquee OkHttp.
- Pendiente menor: validar manualmente "Cargar más" (paginación) — los
  taps ciegos caen en la zona de gestos del sistema (el botón está al
  borde inferior), no es un bug del producto.

## Ronda v0.7.20 — rediseño UX del lector

### Objetivo
Reducir la carga visual del lector y garantizar que el texto nunca quede oculto
por las barras de controles.

### Cambios
- El área de lectura reserva dinámicamente la altura real del top bar y del
  bottom bar mediante medición Compose; esto se aplica tanto al WebView como al
  modo paginado.
- El paginado mide ahora el viewport útil, no la pantalla completa detrás de
  los overlays. Buscar, traducir y mostrar/ocultar controles ya no deberían
  tapar texto ni alterar el área de lectura de forma inesperada.
- El top bar deja visibles el contexto de la obra, búsqueda y menú. TTS y
  ajustes pasan al menú progresivo junto con traducción y tema.
- El indicador inferior muestra capítulo, página cuando corresponde y
  porcentaje aproximado de lectura, además de la barra visual.
- Los ajustes del lector usan un borrador coherente: todos los cambios se
  confirman con **Aplicar** o se descartan con **Cancelar**.
- Se eliminó el `WebviewFetcher.kt` antiguo que había quedado junto al nuevo
  `WebViewFetcher.kt` tras la migración a v0.7.19.

### Verificación
- `compileDebugKotlin` OK.
- 75 tests JVM no-live OK.
- `assembleDebug` OK.
- APK debug: `dist/ao3-reader-v0.7.20-debug.apk` (22 MB).
- No se generó release.

## Ronda v0.7.21 — revisión UI/UX integral

### Objetivo
Mejorar la jerarquía de acciones, reducir ruido visual y hacer más comprensibles
los estados offline, carga, error y eliminación sin cambiar la arquitectura de red.

### Cambios
- Inicio: aviso offline visible, búsqueda con accesibilidad explícita y acción de
  quitar de "Continuar leyendo" con Snackbar + **Deshacer**.
- Biblioteca: confirmación antes de borrar todo el historial o una descarga completa,
  y resumen visible de obras/capítulos disponibles sin conexión.
- Búsqueda: skeleton de resultados en vez de un spinner vacío, mensajes de error
  orientados al usuario y resumen de filtros activos dentro de la hoja avanzada.
- Tarjetas de obras: máximo de tres tags visibles antes de "+N más" para facilitar
  el escaneo en pantallas pequeñas.
- Detalle: "Leer/Continuar" pasa a ser la acción primaria a ancho completo; guardar
  sin conexión queda como acción secundaria. Las acciones de cada capítulo se
  agrupan en un menú contextual para evitar una fila saturada.
- Ajustes: los datos de comentarios ahora tienen botón de guardado explícito y una
  explicación de privacidad.
- Lector: el estado de TTS se muestra en la barra inferior con acción directa para
  detenerlo; se conserva el rediseño de insets y controles de v0.7.20.
- La búsqueda y el inicio comunican cuando no hay conexión y qué tipo de datos
  pueden mostrar desde caché.

### Verificación
- `compileDebugKotlin` OK.
- `testDebugUnitTest` OK: 76 tests, 0 fallos.
- Se generó únicamente APK debug v0.7.21; no se generó release.

## Ronda v0.7.22 — descargas, navegación offline, TTS y búsqueda del lector

### Descargas
- La cola foreground ahora avanza `done` únicamente después de guardar un capítulo
  correctamente; un timeout o falta de red pausa la cola en el capítulo fallido y no
  fabrica progreso.
- Se añadieron acciones **Detener** y **Reanudar** para la descarga completa desde
  Detalle y Biblioteca. La cola conserva los capítulos restantes en `pending`.
- Las descargas individuales de capítulos pueden cancelarse directamente desde la
  fila del capítulo en Detalle.
- El evento de "Descarga completada" se consume una sola vez en la UI; la notificación
  final sigue siendo la confirmación persistente del sistema.
- Si solo hay una descarga parcial, Detalle ofrece "Guardar restantes" en lugar de
  bloquearse en el estado "Descargado".

### Lector y capítulos
- El lector obtiene la lista completa de capítulos de la obra y superpone los cuerpos
  descargados localmente. Descargar 1–3 ya no convierte una obra de 30 capítulos en
  `Cap. 1/3`; el capítulo 4 puede abrirse y cargarse online.
- Offline conserva el fallback limitado a los capítulos realmente guardados.
- TTS lee la versión traducida cuando está activa, permite pausar/reanudar el fragmento
  actual sin reiniciar todo el capítulo y expone resaltado de la palabra/frase actual
  cuando el motor Android proporciona rangos de voz.
- La búsqueda del WebView se reescribió sin offsets mutables: soporta consultas de
  varias letras y múltiples coincidencias sin corromper el contenido.

### Verificación
- `compileDebugKotlin` OK.
- `testDebugUnitTest` OK.
- Versión debug de trabajo: `0.7.22` (versionCode 55).
- No se generó release.

## Ronda v0.7.22b — resaltado TTS, restauración de progreso, búsqueda y paginado

### Resaltado TTS estable
- El resaltado dejaba de saltar: ahora solo hace scroll cuando la palabra leída
  sale del viewport (margen superior/inferior), y el desplazamiento es instantáneo
  (`behavior auto`) en vez de animado. Antes `scrollIntoView({behavior:'smooth'})`
  se disparaba por cada palabra y el viewport rebotaba arriba/abajo constantemente.

### Restauración de progreso al volver a un capítulo
- Al navegar a un capítulo, `goToChapter` ahora restaura el % guardado de ESE
  capítulo (antes lo ponía a 0) y la restauración espera a que el layout del
  WebView se asiente (postDelayed 80ms) para que `scrollHeight` sea el real.

### Búsqueda del lector (scroll) reescrita
- `jsFindInPage` ahora borra los `mark` anteriores al inicio (búsquedas repetidas
  ya no encuentran coincidencias fantasma sobre un DOM ya marcado) y reconstruye
  cada nodo de texto una sola vez con un fragmento de texto+marks, sin `splitText`
  (que corrompía el texto con 2+ coincidencias en un mismo nodo).
- La búsqueda se ejecuta como una única llamada atómica y con debounce de 150ms
  para no re-buscar en cada tecla y evitar carreras entre llamadas async.

### Modo paginado no regresa a scroll
- El cuerpo del lector ahora decide por `paged`: si está activo, SIEMPRE muestra
  el pager (con overlay de carga mientras pagina), nunca cae al WebView aunque
  `lines` sea null o vacío.

### Verificación
- `compileDebugKotlin` OK · `testDebugUnitTest` OK (76 tests, 0 fallos).
- APK debug v0.7.22 (versionCode 55): `dist/ao3-reader-v0.7.22-debug.apk`.
- No se generó release.

## Ronda v0.7.22c — fixes de raíz tras revisión del código (TTS, %, búsqueda, paginado)

Revisión a fondo del código de la ronda anterior: los 4 síntomas persistían porque
los fixes eran parciales. Esta ronda corrige las CAUSAS RAÍZ.

### 1. Resaltado TTS saltaba arriba/abajo — CAUSA RAÍZ
- Antes `jsHighlightTtsWord` resaltaba la PRIMERA ocurrencia de la palabra en todo
  el documento: con palabras comunes ("de", "y", "el"…) saltaba al inicio del
  capítulo y volvía. Además envolvía cada palabra en un `<mark>`, moviendo el
  layout en cada callback.
- Ahora usa la CSS Custom Highlight API (un overlay de Ranges: el DOM no cambia,
  el layout no se mueve) y prefiere la ocurrencia en/después de la última posición
  leída (TTS lee en orden), con fallback a la ocurrencia más cercana al centro del
  viewport. El scroll solo ocurre si la palabra sale de la banda visible.

### 2. Progreso guardado pero al volver al capítulo caía a 0% — CAUSA RAÍZ
- El lector cargaba 3 documentos: placeholder → página intermedia con solo el
  título → capítulo real. El `onPageFinished` de la página intermedia consumía
  `pendingScroll` cuando `content != null` (obras descargadas), restauraba sobre un
  documento vacío, y el capítulo real ya no restauraba.
- Ahora `onPageFinished` solo notifica al lector cuando el documento cargado es el
  CONTENIDO actual (`loadedContentKey == currentContentKey`): las cargas
  placeholder/intermedias ya no pueden consumir la restauración.

### 3. Búsqueda del lector (2 letras máx) — CAUSA RAÍZ
- Modificar el DOM con `<mark>` corrompía los offsets al repetir búsquedas.
- Ahora los matches son Ranges pintados con la CSS Custom Highlight API (el texto
  del documento queda intacto → imposible corromper offsets para cualquier
  longitud de consulta), con fallback a `<mark>` idempotente si el motor no
  soporta la API. Añadido resaltado del match activo (`ao3findcurrent`).

### 4. Modo paginado volvía a scroll — CAUSA RAÍZ
- El switch a paginado dependía de un callback JS asíncrono (`applyPrefs`):
  si el callback se perdía/retrasaba, `paged` nunca se activaba y el lector se
  quedaba en scroll. Ahora `applyPrefs` aplica el cambio INMEDIATAMENTE y captura
  la posición por separado (best-effort).
- `htmlToLines` colapsaba capítulos envueltos en `<div>`/`<section>` en una sola
  línea gigante. Ahora recorre contenedores de forma recursiva para paginar
  párrafo a párrafo.
- El parseo de líneas tiene try/catch: un capítulo malformado ya no deja el lector
  en blanco para siempre.

### Verificación
- `compileDebugKotlin` OK · `testDebugUnitTest` OK (76 tests, 0 fallos) ·
  `assembleDebug` OK.
- APK debug v0.7.22 (versionCode 55): `dist/ao3-reader-v0.7.22-debug.apk`
  (SHA-256 `25136ed5d2d4a4a3666e90c49188ca91138664ef2ce6c98a002333a86d5427d0`).
- No se generó release.

## Ronda v0.7.23 — Autocompletado de tags en los filtros (endpoint nativo de AO3)

### Qué
Autocompletado de tags en el sheet de filtros usando el **endpoint nativo de AO3**
(`GET /autocomplete/{type}?term=...`), que devuelve JSON plano
`[{"id": "<nombre canónico>", "name": "<nombre canónico>"}]` — sin HTML que
parsear, sin login, rápido (~1s). El `id` es EL nombre canónico, así que una
sugerencia elegida **no necesita** `resolveCanonicalTag` (a diferencia de un
nombre escrito a mano).

Es una **fusión** de dos implementaciones (la de esta ronda y una variante
"0.7.22d" que llegó después): se tomó el componente reutilizable y los helpers
de coma de la variante, y se conservaron los campos por categoría + el fallback
WebView de esta ronda.

### Componentes
- `ui/components/AutocompleteTagField.kt` (nuevo, del 7z): campo reutilizable
  con debounce de 300ms explícito + auto-cancel de respuestas obsoletas
  (LaunchedEffect keyed en el término), spinner "Buscando tags…", dropdown de
  sugerencias mientras tiene foco, dedupe case-insensitive contra chips,
  free-form fallback (Enter commitea lo escrito aunque AO3 no sugiera nada),
  icono de limpiar, y dos modos:
  - `singleSelect` = true: el campo guarda el valor entero (para "Fandom a
    explorar").
  - `singleSelect` = false: chips de tags commiteados + el campo edita solo el
    segmento activo de un string separado por comas.
  - Params extra `accent`/`onAccent` para colorear los chips por categoría.
- Helpers puros de coma (mismo archivo, testeados): `committedTagSegments`,
  `activeTagSegment`, `appendCommittedTag`, `replaceActiveSegment`,
  `removeCommittedTag`.

### Cambios
- `Models.kt`:
  - `TagSuggestion(id, name)` + `AutocompleteType` enum (FANDOM, CHARACTER,
    RELATIONSHIP, FREEFORM, TAG) en el modelo (el enum ya NO vive en
    Ao3Client).
  - `SearchFilters` con 4 campos nuevos de **nombres canónicos** por categoría
    — `fandomNames`, `characterNames`, `relationshipNames`, `freeformNames`
    (`List<String>`). Incluidos en `hasFilters` y en `serialize()/parse()`
    (índices 27–30, listas unidas con `\u0003` — los nombres contienen comas,
    barras y `&`, así que el separador de listas es distinto del `\u0002` de
    campos). `\u0003` también pasa por `urlEncode`.
- `Ao3Client.kt`:
  - `suspend fun autocomplete(type, term): List<TagSuggestion>`: mínimo 2
    letras; GET `/autocomplete/{type}?term=` **por el pipeline `get()` completo**
    (gate de cortesía + retries + fallback WebView de Cloudflare) + **caché en
    disco propia** (`autocompleteDisk`, TTL 7 días, 250 archivos — los nombres
    de tags cambian rara vez). Devuelve `[]` en cualquier fallo (el sheet
    degrada a texto libre, nunca errorea).
  - `addCommon` envía `work_search[fandom_names]`, `[character_names]`,
    `[relationship_names]`, `[freeform_names]` (coma-joined = AND) **y**
    `cleanTagList` en `other_tag_names`/`excluded_tag_names` (el picker deja
    una coma final tras los chips; AO3 la vería como segmento vacío).
    **Verificado en vivo**: `/works?work_search[fandom_names]=Naruto (Anime &
    Manga)` → 20 obras; funciona también en `/works/search` (texto libre) — a
    diferencia de los `*_ids[]` del sidebar, que solo aplican en una tag page.
- `Ao3Parser.kt`: `parseAutocomplete(json): List<TagSuggestion>` (id+name, el
  id cae al name si viene vacío; nombres en blanco se descartan; shapes
  malformados → `[]`).
- `SearchScreen.kt` (FilterSheet):
  - **"Fandom / tag a explorar"** ahora es un `AutocompleteTagField`
    singleSelect con `AutocompleteType.FANDOM` (escribir sugiere fandoms
    canónicos; seleccionar rellena el campo; texto libre sigue funcionando).
  - **Sección Avanzado**: 4 pickers de inclusión por categoría (Fandoms,
    Personajes, Relaciones, Tags adicionales) vía `CategoryTagField` (wrapper
    que adapta el modelo `List<String>` al picker de coma), cada uno con su
    color semántico (`accent` + texto blanco). Y **1 campo de exclusión**
    ("Excluir tags") con `AutocompleteType.TAG` (busca en todos los tipos) que
    alimenta `excludeTags` → `work_search[excluded_tag_names]` con los helpers
    de coma.

### Tests (95 total, +12 desde v0.7.22)
- `AutocompleteTagHelpersTest` (nuevo, 7 tests): lógica de chips de coma
  (commit/remove/replace/round-trip).
- `AutocompleteJsonTest` (nuevo, 5 tests): parsing JSON del endpoint
  (id≠name, blanks, malformados).
- `Ao3AutocompleteTest` (+1, 6 tests): snapshots **reales** del endpoint
  (fandom/character/relationship/freeform en
  `app/src/test/resources/ao3/autocomplete_*.json`).
- `SearchFiltersSerializationTest` +2: round-trip de nombres canónicos y
  `hasFilters` con cada campo nuevo.

### Pendiente
- Validar en el dispositivo (release firmado): escribir "naruto" en un campo
  del sheet → sugerencias canónicas → tocar → chip → "Aplicar filtros" → la
  búsqueda llega con `work_search[fandom_names]` y filtra de verdad.
