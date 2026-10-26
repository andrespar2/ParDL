package com.andrespar2.pardl.core

import android.content.Context
import android.net.Uri
import com.andrespar2.pardl.core.music.ArtworkLoader
import com.andrespar2.pardl.core.music.AudioMetadataWriter
import com.andrespar2.pardl.core.music.HistoryStore
import com.andrespar2.pardl.core.music.TrackMetadata
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Flujo de análisis y descarga sobre yt-dlp.
 * Todo el estado se publica como StateFlow para sobrevivir a la rotación.
 */
object Downloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    @Volatile
    private var currentProcessId: String? = null

    /** Analiza un enlace y devuelve su información y resoluciones disponibles. */
    suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val check = Format.validateUrl(url)
        if (!check.valid) return@withContext Result.failure(IllegalArgumentException(check.reason))

        _analyzing.value = true
        val pid = "pardl-analyze-${System.currentTimeMillis()}"
        currentProcessId = pid
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-warnings")
            request.addOption("--no-playlist")
            request.addOption("--skip-download")

            val response = YoutubeDL.execute(request, pid)
            val json = extractJson(response.out)
                ?: return@withContext Result.failure(
                    IllegalStateException("No se pudo interpretar la respuesta del sitio.")
                )

            Result.success(parseInfo(json))
        } catch (e: Exception) {
            val (msg, _) = describeError(e.message ?: "")
            Result.failure(IllegalStateException(msg, e))
        } finally {
            _analyzing.value = false
            currentProcessId = null
        }
    }

    /** Comienza una descarga en segundo plano (no bloquea la interfaz). */
    fun start(
        context: Context,
        url: String,
        kind: DownloadKind,
        choice: QualityChoice,
        treeUriString: String?,
        titleHint: String,
        metadata: TrackMetadata? = null,
        embedCover: Boolean = false,
        embedLyrics: Boolean = false
    ) {
        if (_state.value is DownloadState.Downloading) return
        LegacyDownloadService.start(context)
        scope.launch {
            runDownload(
                context.applicationContext, url, kind, choice, treeUriString,
                titleHint, metadata, embedCover, embedLyrics
            )
        }
    }

    /** Cancela el proceso en curso (análisis o descarga). */
    fun cancel() {
        val pid = currentProcessId
        currentProcessId = null
        if (pid != null) {
            try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Exception) { }
        }
        _analyzing.value = false
    }

    /** Cancela solo el análisis y libera la interfaz aunque el proceso no responda. */
    fun abortAnalysis() {
        cancel()
        _analyzing.value = false
    }

    /** Vuelve al estado inicial. */
    fun reset() {
        if (_state.value !is DownloadState.Downloading) {
            _state.value = DownloadState.Idle
        }
    }

    private suspend fun runDownload(
        context: Context,
        url: String,
        kind: DownloadKind,
        choice: QualityChoice,
        treeUriString: String?,
        titleHint: String,
        metadata: TrackMetadata?,
        embedCover: Boolean,
        embedLyrics: Boolean
    ) = withContext(Dispatchers.IO) {
        if (treeUriString.isNullOrBlank()) {
            _state.value = DownloadState.Failure(
                "Selecciona primero una carpeta de descarga.", null
            )
            return@withContext
        }

        val jobDir = File(Storage.workingRoot(context), "job_${System.currentTimeMillis()}")
        if (!jobDir.exists()) jobDir.mkdirs()

        val pid = "pardl-dl-${System.currentTimeMillis()}"
        currentProcessId = pid
        _state.value = DownloadState.Downloading(0f, -1L, "", titleHint)

        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--no-overwrites")
            request.addOption("--retries", 10)
            request.addOption("--fragment-retries", 10)
            request.addOption("-P", jobDir.absolutePath)
            request.addOption("-o", "%(title)s [%(id)s].%(ext)s")

            if (kind == DownloadKind.VIDEO) {
                val sel = Format.videoFormatSelection(choice.height)
                request.addOption("-f", sel.format)
                request.addOption("-S", sel.sort)
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--embed-metadata")
            } else if (!choice.formatId.isNullOrBlank()) {
                // Se conserva el formato y códec originales: no se recodifica.
                request.addOption("-f", choice.formatId)
                request.addOption("--embed-metadata")
            } else {
                val kbps = if (choice.kbps > 0) choice.kbps else 192
                request.addOption("-f", "ba/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "${kbps}K")
                request.addOption("--embed-metadata")
            }

            YoutubeDL.execute(request, pid) { percent, eta, line ->
                _state.value = DownloadState.Downloading(
                    percent = if (percent.isNaN()) 0f else percent.coerceIn(0f, 100f),
                    etaSec = eta,
                    line = line.trim().take(160),
                    title = titleHint
                )
            }

            val produced = jobDir.listFiles()
                ?.filter { it.isFile && it.isCompletedDownload() }
                ?.maxByOrNull { it.length() }

            if (produced == null) {
                _state.value = DownloadState.Failure(
                    "La descarga terminó pero no se encontró el archivo.", null
                )
                jobDir.deleteRecursively()
                return@withContext
            }

            val sizeText = Format.formatFileSize(produced.length())
            var metadataReport: String? = null
            if (kind == DownloadKind.AUDIO && metadata != null) {
                val cover = if (embedCover) ArtworkLoader.fetch(metadata.artworkUrl) else null
                val report = AudioMetadataWriter.write(
                    file = produced,
                    metadata = metadata,
                    cover = cover,
                    embedCover = embedCover,
                    embedLyrics = embedLyrics,
                    embedMetadata = true
                )
                metadataReport = report.summary
            }
            val saved = Storage.copyIntoTreeSaved(context, Uri.parse(treeUriString), produced)
            val folder = Prefs.get(context).folderName
            val formatLabel = choice.ext?.uppercase()?.takeIf { it.isNotBlank() } ?: "MP3"
            HistoryStore.record(
                context,
                HistoryStore.Entry(
                    id = "legacy-${System.currentTimeMillis()}",
                    kind = if (kind == DownloadKind.VIDEO) "Video MP4" else "Audio $formatLabel",
                    title = titleHint,
                    quality = choice.label,
                    fileName = saved.name,
                    uri = saved.uri.toString(),
                    detail = listOfNotNull(sizeText, metadataReport).joinToString(" · "),
                    success = true
                )
            )

            _state.value = DownloadState.Success(
                fileName = saved.name,
                savedTo = folder,
                detail = listOfNotNull(sizeText, metadataReport).joinToString(" · "),
                savedUri = saved.uri.toString(),
                metadataReport = metadataReport
            )
            jobDir.deleteRecursively()
        } catch (c: YoutubeDL.CanceledException) {
            _state.value = DownloadState.Cancelled
            jobDir.deleteRecursively()
        } catch (e: Exception) {
            val (msg, detail) = describeError(e.message ?: "")
            _state.value = DownloadState.Failure(msg, detail)
            jobDir.deleteRecursively()
        } finally {
            currentProcessId = null
        }
    }

    private fun File.isCompletedDownload(): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return !n.endsWith(".part") && !n.endsWith(".ytdl") &&
            !n.endsWith(".frag") && !n.endsWith(".temp") && !n.startsWith(".")
    }

    private fun extractJson(out: String): JSONObject? {
        val text = out.trim()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInfo(obj: JSONObject): MediaInfo {
        val title = obj.optString("title").ifBlank { "Sin título" }
        val uploader = obj.optString("uploader")
            .ifBlank { obj.optString("channel") }
            .ifBlank { obj.optString("uploader_id") }
            .ifBlank { null }
        val duration = obj.optInt("duration", 0)

        val heights = sortedSetOf<Int>()
        var hasAudioOnly = false
        val audioFormats = mutableListOf<AudioFormatOption>()
        val formats = obj.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val h = f.optInt("height", 0)
                if (h > 0) heights.add(h)
                val v = f.optString("vcodec")
                val a = f.optString("acodec")
                if ((v.isEmpty() || v == "none") && a.isNotEmpty() && a != "none") {
                    hasAudioOnly = true
                    val formatId = f.optString("format_id")
                    val ext = f.optString("ext")
                    val abr = f.optDouble("abr", f.optDouble("tbr", 0.0)).toInt()
                    val size = f.optLong("filesize", 0L).takeIf { it > 0 }
                        ?: f.optLong("filesize_approx", 0L).takeIf { it > 0 }
                    val sampleRate = f.optInt("asr", 0).takeIf { it > 0 }
                    val channels = f.optInt("audio_channels", 0).takeIf { it > 0 }
                    if (formatId.isNotBlank() && ext.isNotBlank()) {
                        audioFormats += AudioFormatOption(
                            formatId = formatId,
                            ext = ext,
                            acodec = a,
                            abrKbps = abr.coerceAtLeast(0),
                            sampleRateHz = sampleRate,
                            channels = channels,
                            filesizeBytes = size,
                            note = f.optString("format_note").ifBlank { null }
                        )
                    }
                }
            }
        }

        return MediaInfo(
            title = title,
            uploader = uploader,
            durationSec = duration,
            heights = heights.toList(),
            hasAudio = hasAudioOnly || heights.isNotEmpty(),
            audioFormats = audioFormats
                .distinctBy { "${it.acodec}|${it.abrKbps}|${it.ext}|${it.channels ?: 0}" }
                .sortedByDescending { it.abrKbps },
            thumbnailUrl = obj.optString("thumbnail").ifBlank { null },
            webpageUrl = obj.optString("webpage_url").ifBlank { null }
        )
    }

    /** Traduce el error de yt-dlp a un mensaje en español comprensible. */
    private fun describeError(raw: String): Pair<String, String?> {
        val lower = raw.lowercase(Locale.ROOT)
        val detail = raw.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?.take(240)

        val message = when {
            lower.contains("unsupported url") || lower.contains("no suitable") ->
                "El sitio o el enlace no es compatible."
            lower.contains("private video") || lower.contains("this video is private") ->
                "El contenido es privado."
            lower.contains("sign in") || lower.contains("login") || lower.contains("cookies") ->
                "El contenido requiere iniciar sesión."
            lower.contains("video unavailable") || lower.contains("not available") ||
                lower.contains("removed") || lower.contains("404") ->
                "El contenido ya no está disponible."
            lower.contains("403") || lower.contains("forbidden") ->
                "El sitio bloqueó la petición. Inténtalo más tarde."
            lower.contains("timed out") || lower.contains("timeout") ->
                "La conexión tardó demasiado. Revisa tu red."
            lower.contains("unable to download") || lower.contains("network") ||
                lower.contains("connection") ->
                "Fallo de red durante la descarga."
            lower.contains("no space left") ->
                "No hay espacio suficiente en el dispositivo."
            lower.contains("ffmpeg") ->
                "Error al procesar el audio/video (FFmpeg)."
            else -> "No se pudo completar la descarga."
        }
        return message to detail
    }
}
