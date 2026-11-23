# Previewer setup

Read this when `android-design-preview` cannot be found (neither on `PATH` nor bundled in the skill's `scripts/` folder) or exits `3` (missing Java or server prerequisites). Tell the user these steps, then wait. Never edit the user's harness config unasked.

## Prerequisites

- **macOS or Linux** (Windows is unsupported)
- **Java 17+** (system `java` on `PATH`, `JAVA_HOME`, or Android Studio's bundled JBR)

On first run, if `~/.cache/android-design-preview/<version>/android-design-preview.jar` is missing, the bootstrap script downloads the previewer for your OS and Java CPU from GitHub Releases and verifies its SHA-256 checksum automatically.

## Where `android-design-preview` comes from

The bootstrap script ships inside this skill at `scripts/android-design-preview`, so every install route brings it and agents run it from there. Claude Code also puts it on `PATH` as a bare command through the plugin's `bin/` folder.

If the skill folder is missing `scripts/android-design-preview`, the skill was copied without it: reinstall it from https://github.com/charliesbot/android-design. To type the bare command in your own shell, run `install` once to link it into `~/.local/bin` (which must be on your `PATH`):

```sh
<skill-dir>/scripts/android-design-preview install
```

## Codex sandbox settings (only if the command fails in Codex)

Codex runs shell commands inside its workspace-write sandbox, which blocks loopback HTTP and cache writes by default. If `android-design-preview` fails in Codex with a connection or permission error, tell the user to add these settings to `~/.codex/config.toml` (noting that `network_access = true` opens outbound network access to all Codex shell commands, not only this tool):

```toml
[sandbox_workspace_write]
network_access = true
writable_roots = ["~/.cache/android-design-preview"]
```

## Local development JAR override

To test a locally built JAR (`cd app && ./gradlew :app:workerJar`), set `ANDROID_DESIGN_PREVIEW_JAR` to `<path-to-repo>/app/app/build/libs/android-design-preview.jar`.
