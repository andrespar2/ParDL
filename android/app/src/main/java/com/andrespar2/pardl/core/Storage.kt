package com.andrespar2.pardl.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Almacenamiento: carpeta de trabajo privada de la app y copia del resultado
 * a la carpeta elegida por el usuario mediante el Storage Access Framework.
 */
object Storage {

    data class SavedFile(val name: String, val uri: Uri)

    /** Carpeta real de trabajo (no requiere permisos) donde descarga yt-dlp. */
    fun workingRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "pardl-work")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Conserva el permiso sobre la carpeta elegida para futuras sesiones. */
    fun takePersistable(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Algunos proveedores no permiten permiso persistente; se ignora.
        }
    }

    /** Nombre visible de la carpeta representada por el árbol SAF. */
    fun folderDisplayName(context: Context, uri: Uri): String? =
        try {
            DocumentFile.fromTreeUri(context, uri)?.name
        } catch (_: Exception) {
            null
        }

    /**
     * Copia [source] dentro del árbol [treeUri] y devuelve el nombre final.
     */
    fun copyIntoTree(context: Context, treeUri: Uri, source: File): String =
        copyIntoTreeSaved(context, treeUri, source).name

    /**
     * Copia [source] dentro del árbol [treeUri], creando las subcarpetas
     * indicadas (por ejemplo artista/álbum) y devolviendo también la URI final.
     */
    fun copyIntoTreeSaved(
        context: Context,
        treeUri: Uri,
        source: File,
        relativeDirs: List<String> = emptyList()
    ): SavedFile {
        var tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("No se pudo acceder a la carpeta seleccionada. Vuelve a elegirla.")

        for (raw in relativeDirs) {
            val name = sanitizeSegment(raw)
            if (name.isBlank()) continue
            tree = tree.findFile(name)?.takeIf { it.isDirectory }
                ?: tree.createDirectory(name)
                ?: tree
        }

        val mime = mimeFor(source.name)
        var doc = tree.createFile(mime, source.name)
        if (doc == null) {
            val stamp = System.currentTimeMillis()
            val ext = source.extension
            val alt = if (ext.isNotEmpty()) {
                "${source.nameWithoutExtension}_$stamp.$ext"
            } else {
                "${source.name}_$stamp"
            }
            doc = tree.createFile(mime, alt)
        }
        if (doc == null) {
            throw IOException("No se pudo crear el archivo en la carpeta destino.")
        }

        context.contentResolver.openOutputStream(doc.uri, "w")?.use { out ->
            source.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
        } ?: throw IOException("No se pudo escribir en la carpeta destino.")

        return SavedFile(doc.name ?: source.name, doc.uri)
    }

    fun sanitizeSegment(raw: String): String =
        raw.trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .take(80)

    private fun mimeFor(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "opus", "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "aiff", "aif" -> "audio/aiff"
            else -> "application/octet-stream"
        }
    }
}
