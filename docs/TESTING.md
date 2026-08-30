# Testing y depuración en dispositivo

## 1. Suite JVM (la puerta verde)

```bash
./gradlew :app:testDebugUnitTest                       # todo
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.data.StoreTest"
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.data.Ao3ParserTest.blurb parses summary and typed tags"
./gradlew :app:assembleDebug                           # APK debug (~22 MB)
./gradlew :app:assembleRelease                         # R8; sin firma local salvo env del keystore
```

- Los nombres con backticks llevan espacios: citar el patrón `--tests`
  completo.
- `SearchLiveTest` hace llamadas REALES a AO3 (solo imprime, nunca falla,
  añade minutos) — excluirlo con `--tests` en la iteración diaria.
- Convenciones: JUnit 4 plano, `runBlocking` inline, nombres oración con
  backticks (parser/store en inglés, lector/traductor en español), mensajes
  con los valores reales interpolados, KDoc de clase explicando la regresión
  que fija. Parser anclado a snapshots reales en `app/src/test/resources/ao3/`
  (refrescar, no borrar).

Mapa rápido de suites: `Ao3ParserTest` (+comments/feed/author/thread),
`StoreTest` (persistencia, migración, backup), `TranslatorTest` (gtx,
clients5, OpenAI), `CookieJarMergeTest`, `DiskCacheTest`, `ReaderPagingTest`
(filas visuales/slices/joins), `ReaderRatioTest`, `Route/SearchFilters`
(serialización), `AutocompleteTagHelpersTest`.

Si un test falla de forma intermitente: sospechar carreras de
persistencia/corrutinas ANTES de reintentar (hubo una real: writer cancelado
entre token y escritura, documentada en [HISTORY.md](HISTORY.md)).

## 2. Dispositivo real (obligatorio para UI/red)

La única fuente de verdad para WebView, insets, notch y rendimiento. La
referencia histórica era una tablet Alcatel Android 6 y un Redmi/MIUI; hoy el
banco de pruebas es un **Samsung Galaxy A55** (Android 14) por USB.

```bash
adb devices                                   # ver conexión
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n net.spin.ao3/.MainActivity
adb exec-out screencap -p > /tmp/x.png        # captura (leerla con la tool Read)
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > /tmp/ui.xml
adb shell input tap X Y ; adb shell input text 'query'; adb shell input keyevent 66
adb shell input keyevent KEYCODE_WAKEUP ; adb shell input keyevent 82  # despertar/desbloquear
```

Trucos aprendidos: el screen apagado/lockscreen se confunde con "la app está
en blanco"; MIUI bloqueaba uiautomator (usar screencap+OCR); el teclado de
MIUI tapaba la barra inferior (BACK antes de tocar); los taps ciegos por
coordenadas se localizan con el dump (los iconos no aparecen en OCR).

## 3. Depurar el lector / traducción / red (tags de logcat)

```bash
adb logcat -c && adb logcat -v time > /tmp/log.txt   # captura amplia en background
grep -E "ReaderPaged|TranslatorGoogle|WebViewFetcher|Ao3Client|FATAL" /tmp/log.txt
```

- `ReaderPaged`: "pack: N páginas en X ms" — si al activar paginado NO
  aparece, el estado `paged` nunca llegó a true (mirar la hoja de ajustes y
  el botón Aplicar, no el motor).
- `TranslatorGoogle`: intentos por chunk y motivo del fallo (429/403/parse).
- `WebViewFetcher`: fetch/enqueue/challenge/chunks del fallback Cloudflare.
- `AndroidRuntime` FATAL: crash real con stack.

## 4. Síntomas clásicos y su primera hipótesis

| Síntoma | Primera sospecha |
|---|---|
| "No se pudo interpretar" / spinner | Cloudflare o age-gate, NO el parser: guardar el body y mirarlo |
| Búsqueda cuelga ~60 s / HTTP 525 | tarpit TLS de Cloudflare a OkHttp → debe entrar el fallback WebView |
| Traducción HTTP 429 | endpoint gtx sin cookies → verificar que clients5 es el principal |
| Páginas medio vacías | alguien tocó `fits()`/`linesToAnnotated`/`pagePlainText` desincronizados |
| "El paginado no hace nada" | el chip no aplica (verificar el fix de aplicación inmediata) o el pack no corre (log `ReaderPaged`) |
| Texto bajo el notch | falta el `windowInsetsPadding(WindowInsets.displayCutout)` del contenedor raíz del lector |
| UI "se ve azul/vieja" | **Material You activado** (Ajustes → colores dinámicos), no un build viejo |
| ANR al abrir ajustes con biblioteca grande | algo volvió a leer/serializar en main (Store ya carga perezoso; no regresar) |

## 5. Firma e instalación (el baile de datos)

El teléfono puede tener instalada una build de CI (debug = firma efímera del
runner, release = keystore real). Una firma distinta NO admite update:

1. Identificar qué hay: `adb shell dumpsys package net.spin.ao3 | grep -E "versionName|DEBUGGABLE"`.
2. La app es debuggeable → **datos preservables con run-as**:
   ```bash
   adb shell run-as net.spin.ao3 cat files/ao3_library.json > /tmp/lib.json
   adb uninstall net.spin.ao3
   adb install <apk-nuevo>
   adb shell am start -n net.spin.ao3/.MainActivity ; sleep 3
   adb shell am force-stop net.spin.ao3          # para que cree files/
   adb push /tmp/lib.json /data/local/tmp/lib.json
   adb shell "run-as net.spin.ao3 mkdir -p files"
   adb shell "run-as net.spin.ao3 cp /data/local/tmp/lib.json files/ao3_library.json"
   adb shell "run-as net.spin.ao3 chmod 600 files/ao3_library.json"
   adb shell rm -f /data/local/tmp/lib.json
   adb shell am start -n net.spin.ao3/.MainActivity
   ```
   `ao3_library.json` contiene favoritos + historial + progreso + prefs (todo
   menos descargas pesadas, que vivirían en `files/ao3_library_downloads.json`
   — respaldar igual si existe).
3. Alternativa sin perder firma: instalar el artefacto de CI de la MISMA
   variedad (debug↔debug, release↔release) — ver [RELEASING.md](RELEASING.md).

## 6. Descargar builds de GitHub sin `gh` CLI

```bash
TOKEN=$(python3 -c "...")   # token en ~/.git-credentials
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/dusk0382/aotest/actions/runs?per_page=1"        # listar
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/dusk0382/aotest/actions/runs/<id>/artifacts"    # artefactos
curl -sL -H "Authorization: Bearer $TOKEN" -o artifact.zip \
  "https://api.github.com/repos/dusk0382/aotest/actions/artifacts/<artifact_id>/zip"
```
(Sin token la API de artefactos responde 401 aunque el repo sea público.)
