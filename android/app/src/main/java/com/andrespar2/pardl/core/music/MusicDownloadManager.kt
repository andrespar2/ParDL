package com.andrespar2.pardl.core.music

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.andrespar2.pardl.core.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Cola persistente de descargas musicales.
 *
 * La cola vive en disco y el servicio en primer plano la procesa. La interfaz
 * solo observa y envía acciones; cerrar la Activity no detiene la descarga.
 */
object MusicDownloadManager {

    const val ACTION_START = "com.andrespar2.pardl.music.action.START"
    const val ACTION_PAUSE = "com.andrespar2.pardl.music.action.PAUSE"
    const val ACTION_RESUME = "com.andrespar2.pardl.music.action.RESUME"
    const val ACTION_CANCEL = "com.andrespar2.pardl.music.action.CANCEL"
    const val ACTION_RETRY = "com.andrespar2.pardl.music.action.RETRY"
    const val EXTRA_JOB_ID = "job_id"
    const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

    private val _jobs = MutableStateFlow<List<MusicDownloadJob>>(emptyList())
    val jobs: StateFlow<List<MusicDownloadJob>> = _jobs.asStateFlow()

    private val lock = Any()
    private var initialized = false
    private lateinit var appContext: Context

    private val pauseRequests = mutableSetOf<String>()
    private val cancelRequests = mutableSetOf<String>()

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
            _jobs.value = read(context)
        }
    }

    fun enqueue(context: Context, drafts: List<DownloadDraft>): List<String> {
        initialize(context)
        val now = System.currentTimeMillis()
        val newJobs = drafts.mapIndexed { index, draft ->
            MusicDownloadJob(
                id = "music-$now-$index",
                track = draft.track,
                quality = draft.quality,
                metadata = draft.metadata,
                state = JobState.QUEUED,
                totalBytes = draft.quality.expectedSizeBytes ?: 0,
                embedCover = draft.embedCover,
                embedLyrics = draft.embedLyrics,
                organizeByAlbum = draft.organizeByAlbum
            )
        }
        synchronized(lock) {
            _jobs.value = _jobs.value + newJobs
            write(context, _jobs.value)
        }
        if (newJobs.isNotEmpty()) {
            startService(context, ACTION_START)
        }
        return newJobs.map { it.id }
    }

    fun get(id: String): MusicDownloadJob? = _jobs.value.firstOrNull { it.id == id }

    fun update(job: MusicDownloadJob) {
        synchronized(lock) {
            _jobs.value = _jobs.value.map { if (it.id == job.id) job else it }
            if (initialized) write(appContext, _jobs.value)
        }
    }

    fun patch(id: String, transform: (MusicDownloadJob) -> MusicDownloadJob): MusicDownloadJob? {
        var result: MusicDownloadJob? = null
        synchronized(lock) {
            _jobs.value = _jobs.value.map { job ->
                if (job.id == id) {
                    transform(job).copy(updatedAt = System.currentTimeMillis())
                        .also { result = it }
                } else job
            }
            if (initialized) write(appContext, _jobs.value)
        }
        return result
    }

    fun setState(
        id: String,
        state: JobState,
        error: String? = null,
        detail: String? = null
    ) {
        patch(id) {
            it.copy(
                state = state,
                error = error,
                detail = detail ?: it.detail,
                speedBytesPerSec = if (state.terminal || state == JobState.PAUSED) 0 else it.speedBytesPerSec,
                etaSec = if (state.terminal || state == JobState.PAUSED) -1 else it.etaSec
            )
        }
    }

    fun updateProgress(
        id: String,
        bytes: Long,
        total: Long,
        speed: Long,
        eta: Long
    ) {
        patch(id) {
            it.copy(
                state = JobState.DOWNLOADING,
                bytesDownloaded = bytes.coerceAtLeast(0),
                totalBytes = if (total > 0) total else it.totalBytes,
                speedBytesPerSec = speed.coerceAtLeast(0),
                etaSec = eta
            )
        }
    }

    fun pause(context: Context, id: String) {
        pauseRequests += id
        setState(id, JobState.PAUSED, detail = "Pausado por el usuario")
        startService(context, ACTION_PAUSE, id)
    }

    fun resume(context: Context, id: String) {
        pauseRequests -= id
        cancelRequests -= id
        patch(id) {
            it.copy(state = JobState.RESUMING, error = null, detail = "Reanudando")
        }
        startService(context, ACTION_RESUME, id)
    }

    fun cancel(context: Context, id: String) {
        cancelRequests += id
        pauseRequests -= id
        setState(id, JobState.CANCELLED, detail = "Cancelado por el usuario")
        startService(context, ACTION_CANCEL, id)
    }

    fun retry(context: Context, id: String) {
        pauseRequests -= id
        cancelRequests -= id
        patch(id) {
            it.copy(
                state = JobState.QUEUED,
                bytesDownloaded = 0,
                totalBytes = it.quality.expectedSizeBytes ?: 0,
                speedBytesPerSec = 0,
                etaSec = -1,
                error = null,
                detail = "Reintentando",
                retryCount = it.retryCount + 1
            )
        }
        startService(context, ACTION_RETRY, id)
    }

    fun remove(context: Context, id: String) {
        synchronized(lock) {
            _jobs.value = _jobs.value.filterNot { it.id == id }
            write(context, _jobs.value)
        }
    }

    fun isPauseRequested(id: String): Boolean = pauseRequests.contains(id)
    fun isCancelRequested(id: String): Boolean = cancelRequests.contains(id)
    fun clearCancel(id: String) { cancelRequests.remove(id) }

    fun activeCount(): Int = _jobs.value.count { it.isActive && it.state != JobState.PAUSED }

    fun nextQueued(): List<MusicDownloadJob> = _jobs.value.filter { it.state == JobState.QUEUED }

    fun startService(context: Context, action: String, id: String? = null) {
        try {
            val intent = Intent(context, MusicDownloadService::class.java).apply {
                this.action = action
                id?.let { putExtra(EXTRA_JOB_ID, it) }
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // Si Android bloquea el arranque en segundo plano, la cola queda
            // persistida y se reanudará al abrir la app de nuevo.
        }
    }

    fun reload(context: Context) {
        synchronized(lock) {
            _jobs.value = read(context)
        }
    }

    /**
     * Reanuda trabajos pendientes al abrir la app. Android no garantiza que un
     * servicio vuelva tras un cierre forzado, así que la cola persistida es la
     * fuente de verdad y se retoma cuando el usuario vuelve a ParDL.
     */
    fun resumePending(context: Context) {
        initialize(context)
        val prefs = Prefs.get(context)
        if (!prefs.musicBackground) return
        val pending = _jobs.value.filter {
            it.state == JobState.QUEUED || it.state == JobState.INTERRUPTED
        }
        if (pending.isEmpty()) return
        synchronized(lock) {
            _jobs.value = _jobs.value.map {
                if (it.state == JobState.INTERRUPTED) {
                    it.copy(state = JobState.QUEUED, error = null, detail = "Reanudando")
                } else it
            }
            write(context, _jobs.value)
        }
        startService(context, ACTION_START)
    }

    private fun file(context: Context): File = File(context.filesDir, "pardl_music_queue.json")

    private fun read(context: Context): List<MusicDownloadJob> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val array = JSONArray(f.readText())
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val job = runCatching { jobFromJson(o) }.getOrNull() ?: return@mapNotNull null
                if (job.state in setOf(
                        JobState.SEARCHING, JobState.CONSULTING,
                        JobState.OBTAINING_QUALITIES, JobState.PREPARING,
                        JobState.DOWNLOADING, JobState.RESUMING,
                        JobState.PROCESSING, JobState.TAGGING, JobState.VERIFYING
                    )
                ) {
                    job.copy(
                        state = JobState.INTERRUPTED,
                        speedBytesPerSec = 0,
                        etaSec = -1,
                        error = "La aplicación se cerró durante la descarga. Puedes reanudarla."
                    )
                } else {
                    job
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, jobs: List<MusicDownloadJob>) {
        try {
            val array = JSONArray()
            jobs.takeLast(200).forEach { array.put(it.toJson()) }
            file(context).writeText(array.toString())
        } catch (_: Exception) {
            // La cola no debe romper la descarga por un fallo de escritura.
        }
    }
}
