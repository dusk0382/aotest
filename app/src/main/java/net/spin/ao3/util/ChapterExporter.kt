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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import net.spin.ao3.data.Ao3Parser
import java.io.File

/**
 * Exports a chapter as a plain-text .txt file into the device's Downloads
 * folder, so the user can read it anywhere.
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
 *
 * [onResult] receives "OK:<filename>" or "ERR:<message>".
 */
object ChapterExporter {

    @Composable
    fun rememberChapterExporter(onResult: (String) -> Unit): (String, Int, String, String?) -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {}
        return remember(context) {
            { workTitle: String, chapterIndex: Int, chapterTitle: String, contentHtml: String? ->
                scope.launch {
                    runCatching {
                        val text = Ao3Parser.htmlToPlainText(contentHtml)
                        if (text.isBlank()) throw IllegalStateException("El capítulo no tiene contenido para exportar")
                        exportChapter(context, permissionLauncher::launch, workTitle, chapterIndex, chapterTitle, text)
                    }.onSuccess { name -> onResult("OK:$name") }
                        .onFailure { e -> onResult("ERR:${e.message ?: "Error al exportar"}") }
                }
            }
        }
    }

    private fun exportChapter(
        context: Context,
        requestPermission: (String) -> Unit,
        workTitle: String,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
    ): String {
        // API 30+: MediaStore only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return writeMediaStore(context, workTitle, chapterIndex, chapterTitle, text)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas")
        }
        // API 29: MediaStore usually works; some ROMs (MIUI) reject it, fall back
        // to the legacy direct write with the storage permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStore(context, workTitle, chapterIndex, chapterTitle, text)?.let { return it }
        }
        // API 23-28 (and the API 29 fallback): direct file write.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return fileName(workTitle, chapterIndex)
        }
        return writeLegacy(context, workTitle, chapterIndex, chapterTitle, text)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "Obra" }

    private fun fileName(workTitle: String, chapterIndex: Int): String =
        "Capitulo_${chapterIndex + 1}_${sanitizeFileName(workTitle).take(40)}.txt"

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

    /** Writes through MediaStore so the file appears in the Downloads app. Returns null on rejection. */
    private fun writeMediaStore(
        context: Context,
        workTitle: String,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
    ): String? {
        val name = fileName(workTitle, chapterIndex)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            out.write(buildContent(workTitle, chapterIndex, chapterTitle, text).toByteArray())
        } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return name
    }

    /** Writes directly to the public Downloads directory (API 23-29 with permission). */
    private fun writeLegacy(
        context: Context,
        workTitle: String,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
    ): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("No hay almacenamiento externo disponible")
        if (!dir.exists()) dir.mkdirs()
        val name = fileName(workTitle, chapterIndex)
        val target = File(dir, name)
        target.writeText(buildContent(workTitle, chapterIndex, chapterTitle, text))
        return name
    }
}
