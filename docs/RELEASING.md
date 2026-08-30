# Releases, versionado y firma

## 1. Versionado

- `versionName` (X.Y.Z) y `versionCode` (entero que SIEMPRE crece) en
  `app/build.gradle.kts`.
- El workflow de CI tiene un guard: un tag `vX.Y.Z` DEBE coincidir con
  `versionName` o el build falla a propósito. El número se consume por commit
  aunque no se publique — ante duda, subir.

## 2. Pipeline de GitHub Actions (`.github/workflows/build.yml`)

Dispara en push a `main`/PR/`workflow_dispatch` y en tags `v*`:

- Corre `testDebugUnitTest` + `assembleDebug` + `assembleRelease` (JDK
  Temurin 17, `gradle/actions/setup-gradle@v4`).
- Publica artefactos en CADA corrida: `ao3-reader-debug` (~22 MB, firma debug
  del runner), `ao3-reader-release` (~2 MB, R8, firmado con los secrets) y
  `ao3-reader-mapping` (R8 mapping — sin él un crash de release es
  indescifrable).
- Con tag `v*`: publica además un GitHub Release con el APK firmado.

Secrets del repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
(`ao3`), `KEY_PASSWORD`. Configurarlos ANTES de la primera corrida (GitHub
resuelve secrets al inicio del job).

## 3. Firma (histórico delicado)

- El keystore original se **filtró en el historial público** de git y fue
  rotado. NUNCA firmar con claves recuperadas de git history (APKs viejos
  requieren la key vieja = reinstalación con pérdida de datos).
- Backup de la clave vigente (huella SHA-256 `ab7ba84e…`, alias `ao3`):
  `/home/spin/Documentos/ao3-keys/` (`ao3-release.jks` + `keystore_pass.txt`),
  FUERA del repo. Sin ella no hay upgrade directo de releases.
- Firma local de un release (para instalar sobre una instalación release):
  ```bash
  export KEYSTORE_BASE64=$(base64 -w0 /home/spin/Documentos/ao3-keys/ao3-release.jks)
  export KEYSTORE_PASSWORD=$(cat /home/spin/Documentos/ao3-keys/keystore_pass.txt)
  export KEY_ALIAS=ao3 KEY_PASSWORD=$KEYSTORE_PASSWORD
  ./gradlew :app:assembleRelease
  ```

## 4. Matriz de firma vs instalación (por qué falla `adb install`)

| Instalado en el teléfono | Update desde... | ¿Funciona? |
|---|---|---|
| CI debug (firma del runner) | CI debug posterior | Solo si el runner mantiene el mismo debug.keystore (HA cambiado entre imágenes) |
| CI debug | CI release / debug local | NO (firma) → `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| CI release | release firmado con el keystore real | SÍ |
| debug local (clave `~/.android/debug.keystore`) | debug local | SÍ |
| Cualquiera | versionCode menor | NO (`INSTALL_FAILED_VERSION_DOWNGRADE`) |

Si no hay camino de update: preservar datos con `run-as` y reinstalar
(receta completa en [TESTING.md](TESTING.md) §5). La app también trae
Ajustes → Copia de seguridad (JSON completo via SAF) como camino manual.

## 5. Checklist de release

1. Suite verde + `assembleDebug` + prueba real en dispositivo de lo tocado.
2. Port al repo (`/home/spin/Documentos/AO3`) y diff de seguridad
   (`diff -rq app/src`) — ver [MODIFYING.md](MODIFYING.md) §final.
3. Bump `versionName`/`versionCode` en el repo.
4. Commit `vX.Y.Z: ...` + push `main` (artefactos de corrida).
5. Opcional: `git tag -a vX.Y.Z -m ...` + `git push origin vX.Y.Z` → GitHub
   Release público firmado.
6. Actualizar `docs/HISTORY.md` con la ronda (qué cambió y por qué — es la
   crónica que evita re-aprender lecciones).
