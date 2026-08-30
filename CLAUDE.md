# CLAUDE.md

Instrucciones para agentes (Claude Code / ZCode) trabajando en este repo.
Índice de la documentación completa en [README.md](README.md).

## Proyecto

AO3 Lector (`net.spin.ao3`) — app Android de un solo módulo (Kotlin +
Jetpack Compose + Material 3), lector personal de archiveofourown.org sin
login. UI, comentarios de código y docs en **español**. La referencia técnica
profunda es `docs/ARCHITECTURE.md`; el lector tiene guía propia en
`docs/READER_SCREEN.md`. Recetas de modificación en `docs/MODIFYING.md`.

## Comandos

```bash
./gradlew :app:testDebugUnitTest                # la puerta verde — debe pasar antes de terminar
./gradlew :app:assembleDebug                    # build diario (~22 MB)
./gradlew :app:assembleRelease                  # R8; sin firma local salvo env del keystore
```

- Nombres de test con backticks llevan espacios: citar el patrón `--tests`
  completo.
- `SearchLiveTest` toca la red real (imprime, nunca falla, añade minutos).
- Releases por tag en GitHub (`dusk0382/aotest`): el CI exige tag ==
  versionName. Detalles en `docs/RELEASING.md`.
- Lint no bloquea (`abortOnError = false`). JDK 21 local, SDK en
  `local.properties` (NO commitear).

## Arquitectura (resumen)

Sin ViewModels, sin DI, sin Room/DataStore, sin reflexión: todo cableado a
mano vía `AppContainer`. Navegación propia (`AppNav`, `sealed class Route`).
Red: OkHttp (HTTP/1.1, UA de navegador) con fallback a WebView del sistema
para esquivar el tarpit TLS de Cloudflare — todo serializado con un Mutex.
Persistencia: dos JSON (`ao3_library.json` + `ao3_library_downloads.json`)
con single-writer y escritura atómica. Traducción: Google (clients5 +
fallback gtx con cookies) o API OpenAI-compatible. Lector: WebView (scroll) y
paginado Compose con filas visuales medidas. Detalles en `docs/`.

## Reglas duras (cada una fijó una regresión real — no deshacer)

1. **Nunca** tratar `cdn-cgi/challenge-platform` como marcador de bloqueo de
   Cloudflare: está en TODAS las páginas legítimas (causó el infame spinner
   infinito). Marcadores válidos: `_cf_chl_opt`, `challenges.cloudflare.com`,
   `turnstile`.
2. **Age gate**: re-pedir con `?view_adult=true`; nunca detectarlo buscando
   ese parámetro en el HTML.
3. **Tags**: `HttpUrl.Builder.addPathSegment` (espacios → `%20`; el `+` de
   URLEncoder da 404). Nunca reusar un builder.
4. **R8 borra `@JavascriptInterface`** en release incluso con la regla
   común: mantener las reglas específicas de `proguard-rules.pro`; el canal
   console.log es inmune a R8.
5. **minSdk 23 + desugaring** obligatorios; `material-icons-extended` se queda.
6. `listState.scrollToItem` sobre una LazyColumn no compuesta SE SUSPENDE para
   siempre: poner `loading = false` primero y acotar con
   `withTimeoutOrNull(3_000)`.
7. Ante "No se pudo interpretar": mirar el body crudo antes de tocar el
   parser (casi siempre Cloudflare o age-gate).
8. No personalizar el User-Agent de `WebViewFetcher` ni reducir su overlay a
   1×1 (MIUI no ejecuta JS ahí).
9. NO reintroducir caché de paginación keyed por `lines.hashCode()`
   (páginas medio vacías).
10. Nunca firmar con keystrokes del historial de git (la clave original se
    filtró y se rotó; backup vigente en `~/Documentos/ao3-keys/`).
11. El Mutex de red de `Ao3Client` es load-bearing: no paralelizar peticiones
    casualmente.
12. El keystore de CI-debug del runner puede cambiar entre imágenes: ante
    `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, preservar datos con `run-as`
    (receta en `docs/TESTING.md` §5).
13. En la UI, superficies SÓLIDAS (sin `copy(alpha=…)` para fondos) y tokens
    (`LocalSemanticColors`) — spec en `docs/UI.md`. Ojo: si el usuario tiene
    colores dinámicos (Material You) activados, la paleta fija no se ve.

## Tests

JUnit 4 plano JVM (sin Robolectric/mocks/coroutines-test; `runBlocking`
inline). El código testeado no importa clases de Android. Snapshots HTML de
AO3 en `app/src/test/resources/ao3/` (refrescar, no borrar). Detalles y
depuración en dispositivo: `docs/TESTING.md`.

## Convenciones

- UI, comentarios y docs en español.
- Versiones en `gradle/libs.versions.toml` congeladas (AGP 9 integra Kotlin:
  no existe plugin `kotlin-android` separado).
- Un commit por bloque de trabajo; los cambios de identidad visual en commit
  propio (revert fácil).
- Documentación: `README.md` (entrada) + `docs/` (arquitectura, lector,
  recetas, testing, releases, UI, historial). Al cerrar una ronda, añadir la
  crónica a `docs/HISTORY.md`.
