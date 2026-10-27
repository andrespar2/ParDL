package com.andrespar2.pardl.core

/** Metadatos de la aplicación. */
object AppInfo {
    const val VERSION = "0.3.2"
    const val AUTHOR = "andrespar2"
    const val INSTAGRAM = "andres.par2"
    const val GITHUB = "andrespar2"
    const val YTDLP_URL = "https://github.com/yt-dlp/yt-dlp"
    const val FFMPEG_URL = "https://ffmpeg.org"
}

/** Tipo de descarga. */
enum class DownloadKind { VIDEO, AUDIO }

/** Formato de audio real declarado por yt-dlp (sin inventar bitrates). */
data class AudioFormatOption(
    val formatId: String,
    val ext: String,
    val acodec: String,
    val abrKbps: Int,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val filesizeBytes: Long? = null,
    val note: String? = null
)

/** Información extraída de un enlace. */
data class MediaInfo(
    val title: String,
    val uploader: String?,
    val durationSec: Int,
    val heights: List<Int>,
    val hasAudio: Boolean,
    val audioFormats: List<AudioFormatOption> = emptyList(),
    val thumbnailUrl: String? = null,
    val webpageUrl: String? = null
)

/** Una opción de calidad (resolución de video o audio real/estimado). */
data class QualityChoice(
    val key: String,
    val label: String,
    val height: Int = 0,
    val kbps: Int = 0,
    val formatId: String? = null,
    val ext: String? = null,
    val codec: String? = null,
    val note: String? = null
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
        val detail: String?,
        val savedUri: String? = null,
        val metadataReport: String? = null
    ) : DownloadState

    data class Failure(val message: String, val detail: String?) : DownloadState

    object Cancelled : DownloadState
}
