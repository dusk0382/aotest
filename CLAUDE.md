# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Ao3Reader (`net.spin.ao3`) — single-module Android app (Kotlin + Jetpack Compose + Material 3), a personal-use reader client for archiveofourown.org with no login. UI copy, code comments, and docs are written in **Spanish**; follow suit in files you touch. `HANDOFF.md` (Spanish) is the authoritative deep-dive: architecture decisions with rationale, per-version changelog, and on-device testing recipes — consult it before non-obvious changes.

## Commands

```bash
./gradlew :app:testDebugUnitTest                # the green gate — must pass before finishing work
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.data.Ao3ParserTest"
./gradlew :app:testDebugUnitTest --tests "net.spin.ao3.data.Ao3ParserTest.blurb parses summary and typed tags"
./gradlew :app:assembleDebug                    # daily build flow (~22 MB APK)
./gradlew :app:assembleRelease                  # R8-minified; unsigned locally (signing needs CI env secrets)
```

- Backtick test names contain spaces — quote the entire `--tests` pattern.
- `SearchLiveTest` is ungated and makes real network calls to AO3. It catches everything and only prints (never fails), but can add minutes; when iterating, prefer targeted `--tests` filters.
- Releases are tag-driven: bump `versionCode`/`versionName` in `app/build.gradle.kts`, push a `vX.Y.Z` tag, and `.github/workflows/build.yml` builds/tests/signs/publishes the GitHub Release. Daily work is debug-only; signed releases come from CI secrets (`KEYSTORE_BASE64`, …).
- Lint is deliberately non-blocking (`abortOnError = false`) and is not a gate.
- Local builds run on JDK 21 with the SDK at `/opt/android-sdk` (see `local.properties`); CI pins Temurin 17. Gradle configuration cache and parallel are on in `gradle.properties`.

## Architecture

No ViewModels, no DI framework, no Room/DataStore, and no reflection-based serialization anywhere (`org.json` only). Everything is wired by hand:

- **DI**: `Ao3App` creates `AppContainer` (`data/AppContainer.kt`) holding the `store`/`client`/`connectivity`/`translator` singletons. `AppRoot()` in `MainActivity.kt` pulls it from the application context and passes `container` as a plain parameter into every screen composable. `DownloadQueueService` reaches it via `(application as Ao3App).container`.
- **Navigation** (`ui/AppNav.kt`): custom, not androidx.navigation. A `sealed class Route` (`Home`, `Search`, `Detail`, `Reader`, `Author`) is pushed onto a `mutableStateListOf` back stack, persisted with `rememberSaveable`; serialization joins fields with `"\u0001"` and URL-encodes free text (malformed input degrades silently). Three tabs (HOME/LIBRARY/SETTINGS) vs. pushed screens are switched by one `AnimatedContent` in MainActivity. At call sites routes are typed objects — serialization only matters for rotation/process-death restore.
- **Network** (`data/Ao3Client.kt` — the heart): every GET tries a fast OkHttp request first (HTTP/1.1 only — HTTP/2 gets tarpitted), and on Cloudflare markers/errors falls back to `WebViewFetcher.fetch()`, which loads the URL in a fresh, invisible full-screen WebView overlay and streams the DOM back over a `console.log` → `WebChromeClient.onConsoleMessage` channel — all under a global 45 s deadline (30 s for POSTs) with retries/backoff and `Retry-After` handling. All requests serialize behind one Mutex with a minimum spacing out of politeness to AO3; that mutex is load-bearing — don't parallelize casually (the sanctioned exception is `getConcurrent` for author profile + works pages). Caching is tiered: in-memory LRUs → several TTL'd on-disk `DiskCache` instances → `getStale()` fallback on error so offline reading works. POSTs (kudos/comments) never go through the WebView. This whole layer exists because Cloudflare tarpits OkHttp's TLS fingerprint while Chrome passes; the rationale is documented in the source comments.
- **Parsing** (`data/Ao3Parser.kt`): stateless Jsoup object handling both known AO3 work-page templates (classic inline chapters vs. modern `select#selected_id` index) with layered fallbacks.
- **Persistence** (`data/Store.kt`): two hand-rolled JSON files in filesDir — small `ao3_library.json` (favorites, history, kudoed ids, pending download jobs, reader prefs) and a separate large downloads file holding full chapter HTML, split so the reader's ~3.5 s progress writes don't rewrite megabytes. Monitor-lock + debounced (300 ms) single-writer atomic writes (temp + rename); corrupt input degrades to empty, never crashes. The constructor takes a root `File` so JVM tests can use temp dirs.
- **Reader** (`ui/screens/ReaderScreen.kt`, largest file): scroll mode renders locally-generated HTML in a WebView; theme/font changes restyle live via CSS custom properties + `applyReaderPrefs()` JS instead of reloading. Paged mode uses the pure-JVM functions in `util/ReaderPaging.kt` (unit-tested) packed against the measured viewport in a `HorizontalPager`. Scroll ratio crosses a JS bridge named `"Reader"` and progress is saved **synchronously** on exit/back. TTS (`util/ReaderTts.kt`) auto-advances chapters; translations cache per chapter+language indefinitely.

## Hard rules (each one fixed a real regression — do not undo)

- **Never treat `cdn-cgi/challenge-platform` as a Cloudflare-block marker.** CF injects that passive script into every legitimate page; matching it discarded good HTML and caused the infamous infinite spinner. Active-challenge markers are `_cf_chl_opt`, `challenges.cloudflare.com`, `turnstile`.
- **Age gate**: re-request gated pages with `?view_adult=true`. Never detect gating by searching for that param in page HTML — AO3 echoes it inside login `return_to` links everywhere.
- **Tag URLs**: build path segments with `HttpUrl.Builder.addPathSegment` (spaces become `%20`). `URLEncoder`'s `+` gives 404s (`/tags/Harry+Potter/works`). Never reuse one builder for a second URL — it mutates in place.
- **R8 strips `@JavascriptInterface` methods in release** even with the common keep rule (R8 full mode, AGP 9). Keep the `-keepclasseswithmembers … JavascriptInterface` rules in `app/proguard-rules.pro`; the console.log channel is the R8-immune bridge.
- **minSdk 23 is mandatory** (the test tablet runs Android 6.0.1) together with core-library desugaring — dropping either crashes on API 23 with `NoClassDefFoundError`. `material-icons-extended` must stay.
- **`listState.scrollToItem` on a not-yet-composed LazyColumn suspends forever** (once caused a 27-minute spinner). Established pattern: set `loading = false` first, then wrap any programmatic scroll in `withTimeoutOrNull(3_000)`.
- When a screen shows "No se pudo interpretar", inspect the raw response body first — it is almost always Cloudflare or the age gate, not an HTML/parser problem.
- Don't customize `WebViewFetcher`'s User-Agent (stock Chrome UA is correct) and don't shrink its overlay to 1×1 (MIUI refuses to run JS there — keep it invisible-but-fullscreen).
- Don't reintroduce a paged-mode measurement cache keyed by `lines.hashCode()` (it produced half-empty pages and was removed).
- The original release keystore leaked in public git history and was rotated. Never sign with a keystore recovered from git history.

## Tests

JUnit 4 only, plain JVM — no Robolectric, no mocking libs, no coroutines-test; suspend calls use inline `runBlocking`. Tested code stays free of Android framework classes so it runs on the JVM (`Ao3Client` takes `Context?` for exactly this reason). Conventions to match: backtick sentence-style method names (language follows the subject — parser/store tests in English, reader/translator tests in Spanish), static-imported `org.junit.Assert.*` with diagnostic messages interpolating actual values, class-level KDoc explaining the regression being locked down. Parser behavior is pinned against real AO3 HTML snapshots in `app/src/test/resources/ao3/` — refresh those fixtures (don't delete them) when AO3 changes its template.

## Conventions

- Dependency versions in `gradle/libs.versions.toml` are frozen ("versiones exactas — no cambiar sin motivo"). AGP 9 bundles Kotlin — there is no separate `kotlin-android` plugin, only `org.jetbrains.kotlin.plugin.compose`.
- UI design language: single terracota accent (#B03A2E), warm-gray surfaces (never pure white), semantic colors accessed via `LocalSemanticColors` tokens instead of scattered hex literals, neutral TagChips carrying a colored dot per AO3 category, max 3 tags shown then "+N más". `UI.txt` records the full design-system spec.
