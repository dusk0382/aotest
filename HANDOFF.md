# HANDOFF — AO3 Lector (`net.spin.ao3`)

> Documento de contexto para que otro agente (o humano) retome el proyecto sin
> tener que reconstruir la historia. Última actualización: 9 de agosto de 2026.

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

> **⚠️ CRÍTICO PARA AGENTES:** las herramientas de edición (`write_file`, `read_files`)
> de este entorno anclan a `/home/spin/Documentos/tachiyomi-legacy` (el proyecto "activo"
> de la sesión), **NO** a AO3. Para tocar archivos de AO3 usar **rutas absolutas vía
> terminal** (heredocs de bash/python con `<<'EOF'`), nunca `write_file`. Ya hubo un
> incidente que sobrescribió 9 archivos de tachiyomi-legacy (restaurado con git).

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
| Icons | solo `material-icons-core` | NO usar `material-icons-extended` (no está en las dependencias) |

- Kotlin de AGP 9: las `dependencies` del módulo van con `alias(libs.plugins...)` y `kotlin { compilerOptions { jvmTarget = ... } }`
- Release config existe (`isMinifyEnabled = true` + proguard) pero **nunca se ha probado**; el flujo usado es debug.

---

## 4. Estructura del proyecto (16 archivos Kotlin, ~840 LOC)

```
app/src/main/java/net/spin/ao3/
├── Ao3App.kt                 # Application; crea AppContainer
├── MainActivity.kt           # Activity; enableEdgeToEdge; AppRoot con AnimatedContent + nav
├── data/
│   ├── AppContainer.kt       # store + client (singleton de dependencias)
│   ├── Ao3Client.kt          # OkHttp: mutex serializa peticiones, 5 reintentos con backoff,
│   │                         #   detección de páginas de error de Cloudflare, sigue el
│   │                         #   age-gate view_adult, CookieJar en memoria
│   ├── Ao3Parser.kt          # Jsoup: parseSearchResults / parseWorkDetail / parseChapter +
│   │                         #   sanitize(). Soportan DOS plantillas de AO3 (ver §7)
│   ├── Store.kt              # JSON local (favorites, history, downloads, prefs) en filesDir
│   └── model/Models.kt       # WorkSummary, WorkDetail, ChapterInfo, SortOption
├── ui/
│   ├── AppNav.kt             # rutas selladas Home/Search/Detail/Reader + NavController
│   │                         #   (pila simple, rememberSaveable con serialización)
│   ├── components/           # TagChip (+colores por tipo de tag), WorkCard
│   ├── screens/
│   │   ├── HomeScreen.kt     # buscador, chips de tendencias, Continuar leyendo, Favoritos, Descargas
│   │   ├── SearchScreen.kt   # resultados + ordenación + paginación manual ("Cargar más")
│   │   ├── WorkDetailScreen.kt # metadatos, stats, tags, resumen, descargar, capítulos lazy
│   │   └── ReaderScreen.kt   # WebView + temas + tamaño letra + progreso + sheets
│   └── theme/Theme.kt        # Material 3, dark/light, dynamic color solo en Android 12+
└── util/Format.kt            # formatCount (1.2K/12K/1.2M), escapeHtml
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

1. **`Store.persist()` tiene una carrera teórica** (escribe `root.toString()` de forma
   asíncrona mientras `root` se muta en main thread; dos persist rápidos pueden
   escribir snapshots en desorden). Personal-use: aceptable. El historial además
   escribe el JSON completo cada 3,5 s mientras se lee.
2. **`InMemoryCookieJar` usa un `MutableMap` sin sincronizar** — seguro hoy porque el
   `Mutex gate` serializa todas las peticiones; frágil si se paraleliza `get()`.
3. **Worst case de carga**: 5 intentos × hasta 90 s de `callTimeout` = la UI puede
   quedarse en "Cargando…" varios minutos si AO3 está caído de verdad.
4. **`AppNav.serialize()` usa `\u0001`** como separador; una consulta con ese carácter
   rompería el round-trip (raro, trivial de arreglar con URLEncoder).
5. **Doble recarga del WebView** al cambiar contenido/tema (guard en `update` +
   `LaunchedEffect(htmlToLoad)`). Inofensivo pero derrochador.
6. **No hay tests unitarios** del parser (el componente más frágil).
7. **Release build sin probar** (minify + shrink activados en release).
8. La sección Descargas del Home no permite borrar descargas (solo desde el detalle).
9. `rememberModalBottomSheetState` deprecado (2 warnings) — migrar a
   `rememberBottomSheetState` cuando se toque.

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
