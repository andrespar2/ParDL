package com.andrespar2.pardl.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
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

/**
 * Motor de descargas. Inicializa yt-dlp (Python embebido) y FFmpeg, y expone
 * su estado de preparación a la interfaz.
 */
object Engine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    private var started = false

    /** Inicia la inicialización una sola vez (idempotente). */
    fun ensureStarted(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        _state.value = EngineState.Initializing
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(
                    t.message ?: "No se pudo iniciar el motor de descargas."
                )
            }
        }
    }

    /** Reintenta la inicialización tras un fallo. */
    fun retry(context: Context) {
        started = false
        ensureStarted(context)
    }

    /** Versión de yt-dlp incluida (o null si no se pudo obtener). */
    suspend fun ytdlpVersion(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest("https://example.com")
            request.addOption("--version")
            val response = YoutubeDL.execute(request)
            response.out.trim().lines().firstOrNull { it.isNotBlank() }?.trim()
        } catch (_: Exception) {
            null
        }
    }

    /** Lista completa de extractores compatibles (motor yt-dlp). */
    suspend fun listExtractors(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest("https://example.com")
            request.addOption("--list-extractors")
            val response = YoutubeDL.execute(request)
            val lines = response.out.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            Result.success(lines)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Actualiza el binario de yt-dlp desde GitHub (canal estable). */
    suspend fun updateYtdlp(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val text = when (status) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp se actualizó correctamente."
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp ya está en la última versión."
                else -> "Actualización finalizada."
            }
            Result.success(text)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
