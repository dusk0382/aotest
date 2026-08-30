package net.spin.ao3.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spin.ao3.data.Ao3Parser
import net.spin.ao3.data.model.ChapterInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports chapters — or a WHOLE work — as plain-text .txt files into the
 * device's Downloads folder, so the user can read them anywhere.
 *
 * Strategy (verified on a Redmi/Android 10 where MediaStore.Downloads rejects
 * inserts with "Ignoring mutation"):
 *  - API 30+: MediaStore.Downloads (no permission needed).
 *  - API 23-29: request WRITE_EXTERNAL_STORAGE once, then write directly to
 *    the public Downloads directory (requestLegacyExternalStorage is set).
 *
 * Usage:
 *   val exporter = rememberChapterExporter { msg -> snackbar(msg) }
 *   exporter(workTitle, index, chapterTitle, contentHtml)
 *   val workExporter = rememberWorkExporter { msg -> snackbar(msg) }
 *   workExporter(title, author, fandoms, url, chapters, onProgress)
 *
 * [onResult] receives "OK:<filename>" or "ERR:<message>".
 */
object ChapterExporter {

    /** An export deferred until the storage permission (API 23-29) is granted. */
    private data class PendingExport(
        val workTitle: String,
        val chapterIndex: Int,
        val chapterTitle: String,
        val text: String,
    )

    /** Whole-work export deferred until the storage permission is granted. */
    private data class PendingWorkExport(
        val workTitle: String,
        val author: String,
        val fandoms: List<String>,
        val workUrl: String,
        val chapters: List<ChapterInfo>,
    )

    @Composable
    fun rememberChapterExporter(onResult: (String) -> Unit): (String, Int, String, String?) -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // The export parameters are parked here while the permission dialog is
        // open, so the actual write runs (and only reports success) AFTER the
        // user grants access — never before.
        var pending by remember { mutableStateOf<PendingExport?>(null) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val p = pending ?: return@rememberLauncherForActivityResult
            pending = null
            if (!granted) {
                onResult("ERR:Permiso de almacenamiento denegado")
                return@rememberLauncherForActivityResult
            }
            scope.launch { doExport(context, p, onResult) }
        }
        return remember(context) {
            { workTitle: String, chapterIndex: Int, chapterTitle: String, contentHtml: String? ->
                scope.launch {
                    val text = runCatching { Ao3Parser.htmlToPlainText(contentHtml) }.getOrNull()
                    if (text.isNullOrBlank()) {
                        onResult("ERR:El capítulo no tiene contenido para exportar")
                        return@launch
                    }
                    // API 23-29 needs WRITE_EXTERNAL_STORAGE for the legacy path;
                    // API 30+ uses MediaStore with no permission.
                    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        pending = PendingExport(workTitle, chapterIndex, chapterTitle, text)
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        doExport(context, PendingExport(workTitle, chapterIndex, chapterTitle, text), onResult)
                    }
                }
            }
        }
    }

    /**
     * Exports a WHOLE work as one .txt (header + index + every chapter). The
     * HTML→text conversion runs off the main thread with [onProgress]
     * (done, total) so the caller can show a progress UI for long works.
     */
    @Composable
    fun rememberWorkExporter(onResult: (String) -> Unit): (
        workTitle: String,
        author: String,
        fandoms: List<String>,
        workUrl: String,
        chapters: List<ChapterInfo>,
        onProgress: (Int, Int) -> Unit,
    ) -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var pending by remember { mutableStateOf<PendingWorkExport?>(null) }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val p = pending ?: return@rememberLauncherForActivityResult
            pending = null
            if (!granted) {
                onResult("ERR:Permiso de almacenamiento denegado")
                return@rememberLauncherForActivityResult
            }
            scope.launch { doWorkExport(context, p, onResult) }
        }
        return remember(context) {
            { workTitle: String, author: String, fandoms: List<String>, workUrl: String,
              chapters: List<ChapterInfo>, onProgress: (Int, Int) -> Unit ->
                scope.launch {
                    val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                    val p = PendingWorkExport(workTitle, author, fandoms, workUrl, chapters)
                    if (needsPermission) {
                        pending = p
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        doWorkExport(context, p, onResult, onProgress)
                    }
                }
            }
        }
    }

    private suspend fun doExport(context: Context, p: PendingExport, onResult: (String) -> Unit) {
        runCatching {
            exportChapter(context, p.workTitle, p.chapterIndex, p.chapterTitle, p.text)
        }.onSuccess { name -> onResult("OK:$name") }
            .onFailure { e -> onResult("ERR:${e.message ?: "Error al exportar"}") }
    }

    private suspend fun doWorkExport(
        context: Context,
        p: PendingWorkExport,
        onResult: (String) -> Unit,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        runCatching {
            val content = withContext(Dispatchers.Default) {
                buildWorkContent(p, onProgress)
            }
            onProgress(p.chapters.size, p.chapters.size)
            writeOut(context, workFileName(p.workTitle), content)
        }.onSuccess { name -> onResult("OK:$name") }
            .onFailure { e -> onResult("ERR:${e.message ?: "Error al exportar la obra"}") }
    }

    private suspend fun exportChapter(
        context: Context,
        workTitle: String,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
    ): String {
        val content = buildContent(workTitle, chapterIndex, chapterTitle, text)
        return writeOut(context, fileName(workTitle, chapterIndex), content)
    }

    /** Single write path: MediaStore (30+), MediaStore-with-legacy-fallback (29), legacy (23-28). */
    private fun writeOut(context: Context, name: String, content: String): String {
        // API 30+: MediaStore only (no permission needed).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return writeMediaStore(context, name, content)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")
        }
        // API 29: MediaStore usually works; some ROMs (MIUI) reject it, fall back
        // to the legacy direct write (permission already ensured by the caller).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStore(context, name, content)?.let { return it }
        }
        // API 23-28 (and the API 29 fallback): direct file write.
        return writeLegacy(context, name, content)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "Obra" }

    private fun fileName(workTitle: String, chapterIndex: Int): String =
        "Capitulo_${chapterIndex + 1}_${sanitizeFileName(workTitle).take(40)}.txt"

    private fun workFileName(workTitle: String): String =
        "${sanitizeFileName(workTitle).take(50)}.txt"

    private fun buildContent(workTitle: String, chapterIndex: Int, chapterTitle: String, text: String): String {
        val title = chapterTitle.ifBlank { "Capítulo ${chapterIndex + 1}" }
        return buildString {
            appendLine(workTitle)
            appendLine(title)
            appendLine("—".repeat(40))
            appendLine()
            append(text.trim())
            appendLine()
            appendLine()
            appendLine("—".repeat(40))
            append("Exportado con AO3 Lector · archiveofourown.org")
        }
    }

    /** Header + index + every chapter of the work, as plain text. */
    private fun buildWorkContent(
        p: PendingWorkExport,
        onProgress: (Int, Int) -> Unit,
    ): String = buildString {
        appendLine(p.workTitle)
        if (p.author.isNotBlank()) appendLine("por ${p.author}")
        appendLine("—".repeat(40))
        if (p.fandoms.isNotEmpty()) appendLine("Fandoms: ${p.fandoms.joinToString(", ")}")
        if (p.workUrl.isNotBlank()) appendLine(p.workUrl)
        appendLine(
            "Exportado el ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())} " +
                "con AO3 Lector · archiveofourown.org",
        )
        appendLine()
        val ordered = p.chapters.sortedBy { it.index }
        if (ordered.size > 1) {
            appendLine("ÍNDICE")
            ordered.forEachIndexed { i, ch ->
                appendLine("  ${i + 1}. ${ch.title.ifBlank { "Capítulo ${i + 1}" }}")
            }
            appendLine()
        }
        ordered.forEachIndexed { i, ch ->
            appendLine("═".repeat(40))
            appendLine("CAPÍTULO ${i + 1}${if (ordered.size > 1) " de ${ordered.size}" else ""}")
            appendLine(ch.title.ifBlank { "Capítulo ${i + 1}" })
            appendLine()
            val text = runCatching { Ao3Parser.htmlToPlainText(ch.content) }.getOrNull().orEmpty().trim()
            if (text.isBlank()) {
                appendLine("(este capítulo no tiene contenido guardado)")
            } else {
                appendLine(text)
            }
            appendLine()
            onProgress(i + 1, ordered.size)
        }
        appendLine("—".repeat(40))
        append("Fin de la obra · Exportado con AO3 Lector")
    }

    /** Writes through MediaStore so the file appears in the Downloads app. Returns null on rejection. */
    private fun writeMediaStore(context: Context, name: String, content: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray())
        } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return name
    }

    /** Writes directly to the public Downloads directory (API 23-29 with permission). */
    private fun writeLegacy(context: Context, name: String, content: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("No hay almacenamiento externo disponible")
        if (!dir.exists()) dir.mkdirs()
        // No silent overwrites: MediaStore versiones "name (1)" solo, aquí lo
        // replicamos a mano.
        var target = File(dir, name)
        if (target.exists()) {
            val base = name.removeSuffix(".txt")
            var n = 1
            while (File(dir, "$base ($n).txt").exists()) n++
            target = File(dir, "$base ($n).txt")
        }
        target.writeText(content)
        return target.name
    }
}
