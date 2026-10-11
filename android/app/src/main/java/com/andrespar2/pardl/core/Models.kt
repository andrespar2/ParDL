package com.andrespar2.pardl.core

/** Metadatos de la aplicación. */
object AppInfo {
    const val VERSION = "0.1.0"
    const val AUTHOR = "andrespar2"
    const val INSTAGRAM = "andres.par2"
    const val GITHUB = "andrespar2"
    const val YTDLP_URL = "https://github.com/yt-dlp/yt-dlp"
    const val FFMPEG_URL = "https://ffmpeg.org"
}

/** Tipo de descarga. */
enum class DownloadKind { VIDEO, AUDIO }

/** Información extraída de un enlace. */
data class MediaInfo(
    val title: String,
    val uploader: String?,
    val durationSec: Int,
    val heights: List<Int>,
    val hasAudio: Boolean
)

/** Una opción de calidad (resolución de video o kbps de audio). */
data class QualityChoice(
    val key: String,
    val label: String,
    val height: Int = 0,
    val kbps: Int = 0
)

/** Estado del motor de descargas. */
sealed interface EngineState {
    object Initializing : EngineState
    object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

/** Estado del flujo de descarga, observable desde la interfaz. */
sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(
        val percent: Float,
        val etaSec: Long,
        val line: String,
        val title: String
    ) : DownloadState

    data class Success(
        val fileName: String,
        val savedTo: String?,
        val detail: String?
    ) : DownloadState

    data class Failure(val message: String, val detail: String?) : DownloadState

    object Cancelled : DownloadState
}
