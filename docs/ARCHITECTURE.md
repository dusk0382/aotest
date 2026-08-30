# Arquitectura — AO3 Lector (`net.spin.ao3`)

App Android nativa (Kotlin + Jetpack Compose + Material 3) para leer fanfic de
archiveofourown.org en tablet/móvil, sin login. Documento de referencia técnica:
qué hay, dónde está, y por qué. Para recetas de modificación véase
[MODIFYING.md](MODIFYING.md); para el lector en detalle,
[READER_SCREEN.md](READER_SCREEN.md).

Estado del código: ~14.500 LOC en `app/src/main`, ~1.800 LOC de tests JVM.

---

## 1. Arranque y DI (sin frameworks)

No hay ViewModels, ni Hilt/Dagger, ni Room/DataStore, ni reflexión: todo se
conecta a mano.

- `Ao3App` (Application): crea `AppContainer`, inicializa `AvatarImages`
  (caché de avatares en disco) y reanuda la cola de descargas pendiente
  (`DownloadQueueService.startIfPending`).
- `AppContainer` (`data/AppContainer.kt`): singletons `store` (Store),
  `client` (Ao3Client), `scrollStore`, `connectivity` (ConnectivityMonitor) y
  `translator` (Translator, cuyo `configProvider` lee `store.prefs` en cada
  llamada — cambiar proveedor/modelo en Ajustes surte efecto al vuelo).
  Registra `WebViewFetcher` con la Application (necesita trackear la Activity).
- `MainActivity``: 3 pestañas raíz (Inicio/Biblioteca/Ajustes) + un stack de
  pantallas completas. Ambos conviven en un `AnimatedContent`; la barra
  inferior se oculta cuando hay stack.
- `ui/AppNav.kt`: navegación propia (no androidx.navigation). `sealed class
  Route` (`Home`, `Search(filters, sort)`, `Detail(workId)`,
  `Reader(workId, chapterIndex)`, `Author(username)`) sobre un
  `mutableStateListOf`; serialización URL-safe por campo para
  `rememberSaveable` (rotación/muerte de proceso). Nota: las claves del stack
  son la ruta serializada — re-abrir la MISMA ruta reusa el estado guardado
  (por eso `currentIndex` del lector NO es `rememberSaveable`; ver
  [READER_SCREEN.md](READER_SCREEN.md)).

## 2. Red: `Ao3Client` + `WebViewFetcher`

El problema que define esta capa: **AO3 está detrás de Cloudflare, que
tarpit-ea clientes no-navegador por fingerprint TLS** (medido: OkHttp sufre
cuelgues de ~60 s y HTTP 525; Chrome pasa). Además AO3 tiene un age-gate para
obras adultas y sirve errores Cloudflare con HTTP 200.

Flujo de `fetch()`:

```
GET OkHttp rápido (HTTP/1.1 forzado, readTimeout 8 s, UA de Chrome real)
 ├─ 200 sin marcadores Cloudflare → devolver HTML
 ├─ challenge activo (_cf_chl_opt, challenges.cloudflare.com, turnstile)
 │    o error 403/429/5xx/timeout → WebViewFetcher.fetch(url)
 └─ WebView: instancia FRESCA de WebView a pantalla completa invisible,
      ejecuta el JS del challenge solo (si hay captcha, se muestra al
      usuario), y devuelve el DOM por un canal console.log → chunked.
      Timeout propio 120 s (challenge 25 s si no resuelve).
```

Reglas que NO se pueden romper (cada una fijó una regresión real):

- **Nunca** tratar `cdn-cgi/challenge-platform` como marcador de bloqueo:
  Cloudflare lo inyecta en TODAS las páginas legítimas (el bug del "spinner
  infinito" de v0.7.16-0.7.19).
- UA de navegador real (`BROWSER_UA`); el UA propio `AO3-Lector/...okhttp`
  recibe slow-down (14-16 s vs ~1 s).
- HTTP/1.1 forzado (HTTP/2 empeora el tarpit).
- **Todas** las peticiones se serializan con un Mutex + separación mínima
  (cortesía/anti-throttle). La única excepción sancionada es `getConcurrent`
  (perfil de autor + obras). No paralelizar casualmente.
- Age-gate: re-pedir con `?view_adult=true`; NUNCA detectar el gate buscando
  ese parámetro en el HTML (AO3 lo eco-a en links de login).
- Tags: construir rutas con `HttpUrl.Builder.addPathSegment` (espacios → %20;
  `+` de URLEncoder da 404). Nunca reusar un builder para otra URL.
- 429/`Retry-After` se honra (cap 30 s). Deadline global por petición: 45 s
  (POST 30 s) sobre todos los reintentos.
- CookieJar en memoria sincronizado: fusión por `(name, path)` con
  `ConcurrentHashMap.compute`; una respuesta sin cookies nunca limpia el jar
  (Cloudflare rota `__cf_bm` constantemente).

Cachés en capas: LRU en memoria → `DiskCache` en disco con TTL por instancia
→ `getStale()` como fallback offline. `refreshWork(id)` salta cachés a
demanda (pull-to-refresh del detalle).

`Ao3Client` recibe `Context?` opcional (los tests JVM lo omiten y caen a
OkHttp puro — el código testeable no toca clases de Android).

## 3. Parsing: `Ao3Parser`

Jsoup sin estado. Maneja **dos plantillas** de página de obra de AO3 (clásica
con capítulos inline vs moderna con índice `select#selected_id`) con
fallbacks en capas, además de búsqueda, comentarios (fragmento AJAX
`/comments/show_comments`), facets y perfiles de autor. Utilidades clave:
`htmlToPlainText` (walker que conserva párrafos), `sanitize` (HTML limpio para
el lector), `parseFeed` (Atom para "comprobar actualizaciones").

Los tests fijan el parser contra snapshots HTML reales en
`app/src/test/resources/ao3/` — refrescarlos (no borrarlos) si AO3 cambia de
plantilla.

## 4. Persistencia: `Store`

Dos archivos JSON en `filesDir`, escritura atómica con single-writer:

- `ao3_library.json` (pequeño): favoritos, historial (con progreso por
  capítulo), `prefs`, cola pendiente de descargas.
- `ao3_library_downloads.json` (MB): capítulos descargados con HTML completo.
  Carga perezosa (`ensureDownloadsLocked`) + calentamiento en IO al arrancar;
  el nombre deriva del root del Store (cada instancia/test tiene el suyo).

Invariantes (romperlas = corromper biblioteca):

- Snapshot coherente bajo `lock`; el WRITER serializa el estado más fresco en
  el hilo IO (sección `NonCancellable`, bajo `writeMutex`): la llamada solo
  marca dirty. Ver la carrera "writer cancelado entre token y escritura" en
  [HISTORY.md](HISTORY.md) (ronda 0.7.26).
- Debounce 300 ms (el progreso de lectura persiste cada 3,5 s); `savePrefs()`
  = `persist(immediate = true)`.
- Escritura atómica: temp + rename; JSON corrupto degrada a vacío, jamás
  crashea.
- `exportBackup`/`importBackup` (SAF) cubren TODO (favoritos + historial +
  descargas + prefs); `importBackup` fuerza la carga de descargas antes de
  persistir para no pisarlas con el placeholder vacío.

`Store.Prefs` concentra los ajustes (tema app, lector: fuente/interlineado/
márgenes/tema/serif/paginado/TTS, identidad de comentarios, traducción:
idioma/proveedor/baseURL/apiKey/modelo). Patrón para añadir campos en
[MODIFYING.md](MODIFYING.md).

## 5. Descargas: `DownloadQueueService`

Foreground service (`dataSync`) con cola FIFO persistida. `WorkDetailScreen`
encola con `enqueueIntent`; el servicio baja capítulo a capítulo vía
`getChapter` (saltando los ya descargados), guarda con
`store.addDownloadedChapter` y actualiza notificación + `QueueState`
(`MutableStateFlow` singleton observado por las pantallas con
`collectAsState()` — colectado en subcomposables para no recomponer la
pantalla entera por capítulo). Acciones Detener/Reanudar conservan los
capítulos restantes en `pending` (persistido); un fallo de red PAUSA (no
fabrica progreso). Al completar: notificación persistente + evento
`completedAt` que la UI consume una sola vez (`consumeCompletion`).

## 6. Traducción: `Translator`

Dos proveedores elegibles (Ajustes → Traducción y/o la hoja del lector):

- **Google** (por defecto, sin API key):
  1. `clients5.google.com/translate_a/t?client=dict-chrome-ex` (endpoint del
     plugin de Chrome; medido 200 sin cookies, conserva los `\n\n` entre
     bloques en UN string) — 3 intentos.
  2. Fallback: `translate.googleapis.com/translate_a/single?client=gtx` tras
     warm-up de cookies (visita a translate.google.com que recoge NID en el
     CookieJar) — 2 intentos.
  Ritmo anti-throttle: lotes ≤4.5k chars, pausa 900 ms + jitter, backoff 429
  paciente (Retry-After hasta 45 s, si no 2/6/15 s). El endpoint gtx SIN
  cookies responde 429 aunque el UA sea de navegador (medido 30/08/2026) —
  por eso clients5 es el principal.
- **IA OpenAI-compatible**: `POST {aiBaseUrl}/chat/completions` con
  `Authorization: Bearer`, prompt literario que conserva los bloques, sin
  `temperature` (algunos servidores la rechazan). Errores con el mensaje real
  de la API + pista (401→revisar key, 404→revisar Base URL/modelo). 4xx de
  config no reintentan.

Caché en disco por `clave#proveedor|modelo@host` — cambiar proveedor nunca
mezcla resultados. La traducción es por capítulo+idioma y vive para siempre
(`DiskCache` maxFiles 400).

## 7. UI

- Tema: `ui/theme/Theme.kt` — paleta "Cacao & Salvia" (acento terracota
  #B03A2E; cacao y salvia de secundarios) + **`dynamicColor` opt-in**
  (Material You): si el usuario lo activa, la paleta sale del wallpaper y el
  terracota no se ve. Spec completo en [UI.md](UI.md).
- Tokens semánticos vía `LocalSemanticColors` (success/favorite/warning/info)
  en vez de hexes sueltos. Superficies SÓLIDAS (prohibido
  `container.copy(alpha=…)` para fondos; la opacidad queda para texto y
  líneas). Tipografía editorial: serif del sistema en
  display/headline/titleLarge/titleMedium; sans en body/labels.
- Componentes en `ui/components/`: `TagChip` (5 variantes + escala sólida de
  ratings/warnings: `ratingSolidColors`/`warningSolidColors`/`TagChipSolid`),
  `WorkCard`, `BottomBar` (cápsula), `EmptyState`, `AutocompleteTagField`.
- Pantallas en `ui/screens/`: `HomeScreen` (buscador, chips de orden,
  fandoms, continuar leyendo), `SearchScreen` (resultados + filtros avanzados
  + orden), `WorkDetailScreen` (metadatos, kudos, comentarios, descargas,
  export), `ReaderScreen` (ver [READER_SCREEN.md](READER_SCREEN.md) — es el
  archivo más grande con diferencia), `LibraryScreen` (Favoritos/Historial/
  Descargas + export obra), `SettingsScreen` (apariencia, lector,
  comentarios, traducción, backup, acerca de), `AuthorScreen`.
- i18n parcial: `res/values/strings.xml` (es) + `values-en`. Muchos strings
  nuevos siguen hardcodeados en español — al tocar una pantalla, Preferir
  `stringResource`; aceptado mezclar mientras no se regrese.
- Export `.txt`: `util/ChapterExporter` — por capítulo y de obra completa
  (cabecera + índice + capítulos); MediaStore (API 30+), fallback legacy con
  `WRITE_EXTERNAL_STORAGE` (23-29, `requestLegacyExternalStorage`); el path
  legacy versiona `nombre (1).txt` en vez de pisar.
- Utilidades: `ReaderPaging` (paginado puro JVM), `ReaderTts`, `Format`,
  `HtmlText`, `AuthorUrl`, `AvatarImages`, `ChapterExporter`.

## 8. Tests

JUnit 4 puro JVM — sin Robolectric, sin mocks, sin coroutines-test
(`runBlocking` inline). El código testeado NO toca clases de Android
(por eso `Ao3Client` toma `Context?` y `Store` toma `File`). ~120 tests;
comando y cobertura en [TESTING.md](TESTING.md).
