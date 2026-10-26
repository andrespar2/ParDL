package com.andrespar2.pardl.core.music

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Historial común para descargas MP3/MP4 y para el módulo de música. */
object HistoryStore {

    data class Entry(
        val id: String,
        val kind: String,
        val title: String,
        val artist: String? = null,
        val provider: String? = null,
        val quality: String? = null,
        val fileName: String? = null,
        val uri: String? = null,
        val detail: String? = null,
        val success: Boolean = true,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val lock = Any()
    private var loaded = false

    fun initialize(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loaded = true
            _entries.value = read(context)
        }
    }

    fun record(context: Context, entry: Entry) {
        initialize(context)
        synchronized(lock) {
            val updated = (listOf(entry) + _entries.value)
                .distinctBy { it.id }
                .take(300)
            _entries.value = updated
            write(context, updated)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            _entries.value = emptyList()
            write(context, emptyList())
        }
    }

    private fun file(context: Context): File = File(context.filesDir, "pardl_history.json")

    private fun read(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    id = o.optString("id"),
                    kind = o.optString("kind"),
                    title = o.optString("title"),
                    artist = o.optString("artist").ifBlank { null },
                    provider = o.optString("provider").ifBlank { null },
                    quality = o.optString("quality").ifBlank { null },
                    fileName = o.optString("fileName").ifBlank { null },
                    uri = o.optString("uri").ifBlank { null },
                    detail = o.optString("detail").ifBlank { null },
                    success = o.optBoolean("success", true),
                    timestamp = o.optLong("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<Entry>) {
        try {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(JSONObject().apply {
                    put("id", e.id)
                    put("kind", e.kind)
                    put("title", e.title)
                    put("artist", e.artist)
                    put("provider", e.provider)
                    put("quality", e.quality)
                    put("fileName", e.fileName)
                    put("uri", e.uri)
                    put("detail", e.detail)
                    put("success", e.success)
                    put("timestamp", e.timestamp)
                })
            }
            file(context).writeText(array.toString())
        } catch (_: Exception) {
            // El historial no debe interrumpir una descarga.
        }
    }
}
