# Modificar el proyecto — recetas

Patrones concretos para cambiar cosas sin romperlas. Si el cambio toca la
red/Cloudflare o el lector, leer antes [ARCHITECTURE.md](ARCHITECTURE.md) §2 y
[READER_SCREEN.md](READER_SCREEN.md).

## Reglas transversales

1. **Idioma**: UI, comentarios y docs en español (convención del proyecto).
2. **Tests**: `./gradlew :app:testDebugUnitTest` es la puerta verde. El código
   testeado NO importa clases de Android (`android.*`, Widgets) — por eso
   `Ao3Client` toma `Context?` y `Store` toma `File`.
3. **Sin frameworks**: nada de añadir DI/Room/retrofit; los patrones a mano
   del proyecto son deliberados (mínimo APK, mínimo magia).
4. **Versiones congeladas**: `gradle/libs.versions.toml` no se toca sin
   motivo fuerte (documentado en el commit).
5. minSdk **23** + desugaring obligatorios (el dispositivo histórico era
   Android 6); `material-icons-extended` se queda.
6. Commit por bloque, mensajes `vX.Y.Z: descripción` en el repo de GitHub.
7. Antes de dar por terminado algo: suite verde + `assembleDebug` + prueba
   real en dispositivo si tocó UI/red (ver [TESTING.md](TESTING.md)).

## Añadir un ajuste (Store.Prefs + Settings)

Patrón de 4 pasos en `data/Store.kt`:

1. Campo en `class Prefs` (con default).
2. Lectura en `applyPrefsFrom` (`optString/optBoolean/...` con default).
3. Escritura en `persist()` (`put(...)` dentro del JSONObject "prefs").
4. UI en `SettingsScreen` (sección `SettingsSection`): control que escribe
   `prefs.X = valor` + `store.savePrefs()` (persistencia inmediata) y
   snackbar de confirmación si aplica.

Los prefs viajan gratis en el backup (`exportBackup` serializa `root`
completo). Si el ajuste lo consume un singleton construido en `AppContainer`,
exponerlo como `configProvider` (ver `Translator`) para que lea prefs en cada
uso sin reconstruir nada.

## Añadir una sección de Ajustes

Copiar el patrón de la sección Comentarios o Traducción en
`SettingsScreen.kt`: `SettingsSection(título) { ... }` + estado local
`remember` + guardado explícito con botón + snackbar. Strings largos en
`res/values/strings.xml` (y su gemelo en `values-en` si existe la clave).

## Añadir tema del lector / cambiar paleta

- Tema de LECTOR: `Store.ReaderTheme` (enum + `labelRes`) → `readerPalette`
  (hex bg/fg/accent) → `readerColors` se recalcula solo → strings del enum.
  Recordar `readerCss` (el modo scroll pinta con CSS, no con Compose).
- Paleta de la APP: `ui/theme/Theme.kt` (light/dark). Ojo: si el usuario tiene
  **colores dinámicos** (Material You) activados, la paleta fija no se ve —
  no confundir con "el build está viejo".
- Cambios de identidad visual: hacerlos en un commit separado (revert
  limpio), actualizar `docs/UI.md`, y NO usar tints translúcidos para fondos
  (superficies sólidas; opacidad solo para texto/líneas).

## Añadir idioma o proveedor de traducción

- Idioma: entrada en `TranslationLang` (ReaderScreen) — código BCP-47.
- Proveedor: enum `Translator.Config.Provider` + rama en
  `translateChapterHtml` + parser tolerante (`parseXxx`, internal + test con
  las formas reales del JSON) + UI de configuración si necesita credenciales
  (patrón Traducción en SettingsScreen con "Probar conexión").

## Tocar la red (Ao3Client)

Leer las reglas duras de [ARCHITECTURE.md](ARCHITECTURE.md) §2 primero. En
resumen: respetar el Mutex (nada de paralelizar), el UA de navegador, HTTP/1.1,
el manejo 429/Retry-After y los marcadores de challenge (nunca
`challenge-platform`). Cualquier endpoint nuevo: probar el JSON real con curl
ANTES de escribir el parser, y escribir el parser tolerante a formas
alternativas con tests.

## Añadir pantalla / ruta

1. Entrada en `sealed class Route` (`ui/AppNav.kt`) con campos serializables
   (la serialización URL-safe es automática vía los helpers de Route; probar
   round-trip si añades campos de texto libre — ver `RouteSerializationTest`).
2. `nav.push(Route.X(...))` desde donde corresponda.
3. Rama en el `when` de `MainActivity` (dentro del stack) con la Screen nueva.
4. La barra inferior se oculta sola cuando el stack crece; BackHandler ya
   cubre el pop.

## Añadir strings

`res/values/strings.xml` (español, base) + `values-en/strings.xml` si la clave
ya existía en inglés. Los strings nuevos de pantallas viejas pueden estar
hardcodeados en español — al tocar una pantalla, preferir `stringResource`.

## Tocar el lector

Ver [READER_SCREEN.md](READER_SCREEN.md) §8 (invariantes) y §10 (recetas).
Resumen: no medir en composición, no cachear por hash, mantener
`fits()`/`pagePlainText`/`linesToAnnotated` sincronizados, respetar los
flags de fila, y probar en dispositivo real (el lector es la zona más frágil
del proyecto: cada "arreglo rápido" sin prueba en hardware costó una
regresión).

## Revertir cambios

Cada bloque de trabajo vive en su propio commit (ver `git log`):
`git revert <sha>` revierte limpio. La identidad visual completa es un commit
("UI: ..."), igual que rendimiento y cada feature.

## Actualizar el repo de GitHub (dusk0382/aotest)

Este repo **es** el proyecto: una sola copia, un solo historial, remote
`dusk0382/aotest`. Ya no hay copia de trabajo paralela que portear — las que
existieron se eliminaron el 2026-10-06 (ver la nota de cabecera de
[HISTORY.md](HISTORY.md)).

El flujo canónico de esta máquina:

1. Trabajar directamente en `/home/spin/Documentos/AO3` (aquí).
2. Bump de `versionCode`/`versionName` (ver [RELEASING.md](RELEASING.md)).
3. `./gradlew :app:testDebugUnitTest :app:assembleDebug` — la puerta verde.
4. Commit (`vX.Y.Z: ...`) + push a `main` → la CI compila y publica los
   artefactos `ao3-reader-debug` / `ao3-reader-release` / `ao3-reader-mapping`.
5. Tag `vX.Y.Z` SOLO si se quiere Release público (el CI exige
   tag == versionName).

Nunca vuelvas a crear una segunda copia para trabajar: con dos historiales
divergen las versiones (`versionCode` se quedó atrás en la copia) y aparece
trabajo que nunca se porta. Si necesitas una versión vieja del código, usa
`git stash`, una rama o un tag — no un directorio clonado.
