# AO3 Lector (`net.spin.ao3`)

App Android nativa (Kotlin + Jetpack Compose + Material 3) para leer fanfic de
[archiveofourown.org](https://archiveofourown.org) cómodamente en móvil/tablet,
sin cuenta ni login. Uso personal; el contenido pertenece a sus autores.

## Funciones

- Búsqueda en AO3 con orden y filtros avanzados (tags, ratings, palabras,
  fechas, idioma, completadas/crossovers) y navegación por chips de tag.
- Detalle de obra: metadatos, kudos anónimos, comentarios (leer y responder
  como invitado), "comprobar actualizaciones" (Atom feed).
- Lector con dos modos: **scroll** (WebView) y **paginado** (páginas que
  llenan la pantalla partiendo párrafos por línea visual), 4 temas, fuente/
  interlineado/márgenes/serif, TTS con auto-avance, búsqueda en capítulo,
  **traducción por capítulo** (Google sin API key o cualquier API
  OpenAI-compatible configurable).
- Biblioteca local: favoritos, historial con progreso por capítulo,
  **descargas offline reales**, export .txt (por capítulo u obra completa).
- Cola de descargas en segundo plano con notificación y reanudación.
- Copia de seguridad/restauración de toda la biblioteca (JSON).

## Build

```bash
./gradlew :app:testDebugUnitTest    # tests (la puerta verde)
./gradlew :app:assembleDebug        # APK debug en app/build/outputs/apk/debug/
```

Requisitos: JDK 21 local (CI usa 17), SDK Android en `local.properties`,
minSdk 23. Versiones congeladas en `gradle/libs.versions.toml`.

Instalación en dispositivo: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Documentación

| Doc | Qué contiene |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Reglas duras del proyecto, comandos, convenciones (entrada para agentes) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitectura completa: red/Cloudflare, parsing, persistencia, colas, traducción, UI |
| [docs/READER_SCREEN.md](docs/READER_SCREEN.md) | Guía dedicada de `ReaderScreen.kt` (el archivo crítico) |
| [docs/MODIFYING.md](docs/MODIFYING.md) | Recetas: cómo añadir/cambiar cada tipo de cosa sin romper nada |
| [docs/TESTING.md](docs/TESTING.md) | Suite JVM, pruebas en dispositivo, depuración, síntomas clásicos |
| [docs/RELEASING.md](docs/RELEASING.md) | Versionado, CI, firma, instalación sin perder datos |
| [docs/UI.md](docs/UI.md) | Spec del sistema de diseño ("Terracota & Serif" + fundamentos) |
| [docs/HISTORY.md](docs/HISTORY.md) | Crónica completa de rondas y lecciones (v0.1 → presente) |

## Repo y releases

GitHub: `dusk0382/aotest` (rama `main`). Cada push compila y publica
artefactos; los tags `vX.Y.Z` publican Releases firmados. Detalles y el
checklist completo en [docs/RELEASING.md](docs/RELEASING.md).

## Estado

La última ronda pública es **v0.7.28**: paginado completamente re-hecho
(aplicación inmediata del modo, páginas que llenan la pantalla partiendo
párrafos por línea visual, controles como overlay sin resize, posición exacta
por fila visual, respeto al notch con el color del tema), traducción Google
reparada (endpoint clients5 tras el bloqueo 429 del gtx) + proveedor IA
OpenAI-compatible, export .txt de obra completa, y una pasada de rendimiento
(composición, persistencia y cola de descargas fuera del hilo principal).
