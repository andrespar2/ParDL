package com.andrespar2.pardl.ui.music

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.music.AudioFormat
import com.andrespar2.pardl.core.music.AudioQuality
import com.andrespar2.pardl.core.music.DownloadDraft
import com.andrespar2.pardl.core.music.MusicDownloadManager
import com.andrespar2.pardl.core.music.MusicQuery
import com.andrespar2.pardl.core.music.MusicRepository
import com.andrespar2.pardl.core.music.QualityRanker
import com.andrespar2.pardl.core.music.SearchMode
import com.andrespar2.pardl.core.music.TrackCandidate
import com.andrespar2.pardl.core.music.TrackMetadata
import com.andrespar2.pardl.core.music.UrlRouter
import com.andrespar2.pardl.ui.CoverArt
import com.andrespar2.pardl.ui.EmptyState
import com.andrespar2.pardl.ui.GlassCard
import com.andrespar2.pardl.ui.GlassIconButton
import com.andrespar2.pardl.ui.GlassProgress
import com.andrespar2.pardl.ui.GlassSwitchRow
import com.andrespar2.pardl.ui.GlassTextField
import com.andrespar2.pardl.ui.GlassTopBar
import com.andrespar2.pardl.ui.MetaLine
import com.andrespar2.pardl.ui.PillButton
import com.andrespar2.pardl.ui.SectionHeader
import com.andrespar2.pardl.ui.StatusChip
import com.andrespar2.pardl.ui.glassModifier
import com.andrespar2.pardl.ui.theme.AccentBlue
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class Step { SEARCH, QUALITIES, PREVIEW, BATCH }

@Composable
fun MusicScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onLegacy: (String, DownloadKind) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass = LocalGlass.current
    val prefs = remember { Prefs.get(context) }

    var step by remember { mutableStateOf(Step.SEARCH) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.SONG) }
    var searching by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<MusicRepository.SearchOutcome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    var selectedTrack by remember { mutableStateOf<TrackCandidate?>(null) }
    var qualities by remember { mutableStateOf<List<AudioQuality>>(emptyList()) }
    var qualityMessage by remember { mutableStateOf("") }
    var loadingQualities by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf<AudioQuality?>(null) }
    var albumTracks by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var loadingAlbum by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var loadingAlternatives by remember { mutableStateOf(false) }
    var showAlternatives by remember { mutableStateOf(false) }

    var draft by remember { mutableStateOf<DownloadDraft?>(null) }
    var loadingDraft by remember { mutableStateOf(false) }

    var batchPlan by remember { mutableStateOf<MusicRepository.BatchPlan?>(null) }
    var preparingBatch by remember { mutableStateOf(false) }
    var confirmBatch by remember { mutableStateOf(false) }

    fun extentText(text: String): String {
        val found = Regex("""https?://\S+""").find(text)
        return found?.value?.trim() ?: text.trim()
    }

    fun performSearch() {
        val raw = query.trim()
        val text = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw.filterNot { it.isWhitespace() }
        } else {
            raw
        }
        query = UrlRouter.canonical(text)
        if (text.isBlank()) {
            error = "Escribe una canción, artista, álbum o enlace."
            return
        }
        val effectiveMode = if (text.startsWith("http://") || text.startsWith("https://")) {
            SearchMode.LINK
        } else {
            mode
        }
        searching = true
        error = null
        outcome = null
        selectedIds = emptySet()
        step = Step.SEARCH
        scope.launch {
            val result = withTimeoutOrNull(70_000) {
                MusicRepository.search(context, MusicQuery(text, effectiveMode))
            }
            if (result == null) {
                searching = false
                error = "La búsqueda tardó demasiado. Comprueba la conexión y vuelve a intentarlo."
            } else {
                outcome = result
                searching = false
                if (result.tracks.isEmpty() && result.legacyUrl == null) {
                    error = result.errors.firstOrNull()
                        ?: result.notice
                        ?: "No se encontraron resultados."
                }
            }
        }
    }

    fun pasteAndSearch() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            error = "El portapapeles está vacío."
            return
        }
        query = extentText(text)
        mode = if (query.startsWith("http")) SearchMode.LINK else mode
        performSearch()
    }

    fun openQualities(track: TrackCandidate) {
        selectedTrack = track
        selectedQuality = null
        qualities = emptyList()
        qualityMessage = ""
        albumTracks = null
        alternatives = null
        showAlternatives = false
        step = Step.QUALITIES
        loadingQualities = true
        scope.launch {
            val bundle = withTimeoutOrNull(35_000) {
                MusicRepository.qualities(context, track)
            }
            if (bundle == null) {
                qualities = emptyList()
                qualityMessage = "La consulta de calidades tardó demasiado."
                error = "Tiempo de espera agotado. Prueba otro proveedor o enlace."
                loadingQualities = false
                return@launch
            }
            qualities = bundle.qualities
            qualityMessage = bundle.message
            error = bundle.error
            selectedQuality = QualityRanker.recommended(bundle.qualities, prefs.qualityPolicy())
                ?: bundle.qualities.firstOrNull { !it.previewOnly }
                ?: bundle.qualities.firstOrNull()
            loadingQualities = false
        }
    }

    fun openPreview(quality: AudioQuality) {
        val track = selectedTrack ?: return
        selectedQuality = quality
        loadingDraft = true
        step = Step.PREVIEW
        draft = null
        scope.launch {
            draft = withTimeoutOrNull(50_000) {
                MusicRepository.prepare(context, track, quality)
            }
            loadingDraft = false
            if (draft == null) error = "La preparación de metadatos tardó demasiado."
        }
    }

    fun loadAlbum(track: TrackCandidate) {
        loadingAlbum = true
        scope.launch {
            val list = withTimeoutOrNull(35_000) {
                MusicRepository.albumTracks(context, track)
            } ?: emptyList()
            loadingAlbum = false
            if (list.isEmpty()) {
                error = "El proveedor no devolvió las pistas del álbum."
            } else {
                outcome = (outcome ?: MusicRepository.SearchOutcome()).copy(
                    tracks = list,
                    notice = "Álbum cargado con ${list.size} pistas. Selecciona las que quieras descargar."
                )
                selectedIds = list.filter { it.downloadable }.map { it.id }.toSet()
                step = Step.SEARCH
            }
        }
    }

    fun findAlternatives() {
        val track = selectedTrack ?: return
        loadingAlternatives = true
        showAlternatives = false
        scope.launch {
            val result = withTimeoutOrNull(50_000) {
                MusicRepository.findAlternatives(context, track)
            }
            if (result == null) {
                loadingAlternatives = false
                showAlternatives = false
                error = "La búsqueda de otras fuentes tardó demasiado."
                return@launch
            }
            alternatives = result.tracks
            loadingAlternatives = false
            showAlternatives = true
            if (result.tracks.isEmpty()) {
                error = result.notice ?: "No se encontraron otras fuentes."
            }
        }
    }

    fun startBatch() {
        val tracks = outcome?.tracks?.filter { it.id in selectedIds && it.downloadable }
            ?: emptyList()
        if (tracks.isEmpty()) {
            error = "Selecciona al menos una canción descargable."
            return
        }
        step = Step.BATCH
        preparingBatch = true
        batchPlan = null
        scope.launch {
            batchPlan = withTimeoutOrNull(120_000) {
                MusicRepository.prepareBatch(context, tracks)
            }
            preparingBatch = false
            if (batchPlan == null) {
                error = "La preparación de la descarga múltiple tardó demasiado."
            }
        }
    }

    fun confirmBatchDownload() {
        val plan = batchPlan ?: return
        val items = plan.items.filter { it.selected != null }
        if (items.isEmpty()) {
            error = "No hay pistas con una calidad seleccionada."
            return
        }
        confirmBatch = true
        scope.launch {
            val drafts = withTimeoutOrNull(180_000) {
                items.mapNotNull { item ->
                    val quality = item.selected ?: return@mapNotNull null
                    MusicRepository.prepare(context, item.track, quality)?.copy(
                        embedCover = prefs.musicEmbedCover,
                        embedLyrics = prefs.musicEmbedLyrics,
                        organizeByAlbum = prefs.musicOrganize
                    )
                }
            } ?: emptyList()
            if (drafts.isEmpty()) {
                error = "No se pudo preparar ninguna descarga o la operación tardó demasiado."
                confirmBatch = false
                return@launch
            }
            MusicDownloadManager.enqueue(context, drafts)
            confirmBatch = false
            onOpenDownloads()
        }
    }

    BackHandler(enabled = step != Step.SEARCH) {
        step = Step.SEARCH
        error = null
    }

    Column(Modifier.fillMaxSize()) {
        val title = when (step) {
            Step.SEARCH -> "Música — Alta calidad"
            Step.QUALITIES -> "Elegir calidad"
            Step.PREVIEW -> "Revisar metadatos"
            Step.BATCH -> "Descarga múltiple"
        }
        val subtitle = when (step) {
            Step.SEARCH -> "FLAC primero · proveedores legales"
            Step.QUALITIES -> selectedTrack?.title
            Step.PREVIEW -> selectedQuality?.shortLabel
            Step.BATCH -> batchPlan?.let { "${it.readyCount} pistas listas" }
        }
        GlassTopBar(
            title = title,
            subtitle = subtitle,
            onBack = {
                if (step == Step.SEARCH) onBack() else {
                    step = Step.SEARCH
                    error = null
                }
            }
        )

        when (step) {
            Step.SEARCH -> SearchStep(
                query = query,
                onQueryChange = { query = it },
                mode = mode,
                onModeChange = { mode = it },
                searching = searching,
                outcome = outcome,
                error = error,
                selectedIds = selectedIds,
                onToggleSelection = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onSelectAll = {
                    val ids = outcome?.tracks?.filter { it.downloadable }?.map { it.id }?.toSet()
                        ?: emptySet()
                    selectedIds = if (selectedIds.size == ids.size) emptySet() else ids
                },
                onSearch = ::performSearch,
                onPaste = ::pasteAndSearch,
                onOpenTrack = ::openQualities,
                onStartBatch = ::startBatch,
                onLegacy = { url -> onLegacy(url, DownloadKind.AUDIO) }
            )

            Step.QUALITIES -> QualitiesStep(
                track = selectedTrack,
                qualities = qualities,
                message = qualityMessage,
                loading = loadingQualities,
                selected = selectedQuality,
                onSelect = { selectedQuality = it },
                albumLoading = loadingAlbum,
                onLoadAlbum = { selectedTrack?.let(::loadAlbum) },
                alternativesLoading = loadingAlternatives,
                onFindAlternatives = ::findAlternatives,
                onContinue = { selectedQuality?.let(::openPreview) },
                onRetry = { selectedTrack?.let(::openQualities) }
            )

            Step.PREVIEW -> PreviewStep(
                draft = draft,
                loading = loadingDraft,
                onConfirm = { edited ->
                    MusicDownloadManager.enqueue(context, listOf(edited))
                    onOpenDownloads()
                }
            )

            Step.BATCH -> BatchStep(
                plan = batchPlan,
                loading = preparingBatch,
                confirming = confirmBatch,
                onConfirm = ::confirmBatchDownload,
                onQualityChange = { track, quality ->
                    val plan = batchPlan ?: return@BatchStep
                    batchPlan = plan.copy(
                        items = plan.items.map {
                            if (it.track.id == track.id) it.copy(selected = quality) else it
                        }
                    )
                },
                onBack = { step = Step.SEARCH }
            )
        }

        if (showAlternatives && alternatives != null) {
            AlternativesDialog(
                track = selectedTrack,
                alternatives = alternatives.orEmpty(),
                onDismiss = { showAlternatives = false },
                onSelect = { track ->
                    showAlternatives = false
                    openQualities(track)
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Búsqueda
// ---------------------------------------------------------------------------

@Composable
private fun SearchStep(
    query: String,
    onQueryChange: (String) -> Unit,
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    searching: Boolean,
    outcome: MusicRepository.SearchOutcome?,
    error: String?,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSearch: () -> Unit,
    onPaste: () -> Unit,
    onOpenTrack: (TrackCandidate) -> Unit,
    onStartBatch: () -> Unit,
    onLegacy: (String) -> Unit
) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        GlassCard(padding = 13.dp) {
            GlassTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = when (mode) {
                    SearchMode.ISRC -> "ISRC, por ejemplo USRC17607839"
                    SearchMode.LINK -> "Pega un enlace de Spotify, Apple Music, Deezer…"
                    else -> "Canción, artista, álbum o enlace"
                },
                leadingIcon = Icons.Default.Search,
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(
                    text = if (searching) "Buscando…" else "Buscar",
                    onClick = onSearch,
                    enabled = !searching,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(Icons.Default.ContentPaste, "Pegar enlace", onClick = onPaste)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchMode.entries.forEach { item ->
                ModeChip(
                    label = item.label,
                    selected = mode == item,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onModeChange(item)
                        if (item == SearchMode.LINK) onPaste()
                    }
                )
            }
        }

        if (searching) {
            LoadingBlock("Consultando proveedores…")
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            NoticeCard(
                icon = Icons.Default.ErrorOutline,
                title = "No se pudo completar",
                message = it,
                color = Danger
            )
        }

        outcome?.let { result ->
            result.notice?.let {
                Spacer(Modifier.height(10.dp))
                NoticeCard(
                    icon = Icons.Default.Verified,
                    title = if (result.identification != null) "Identificación" else "Aviso",
                    message = it,
                    color = AccentCyan
                )
            }
            result.identification?.let { identified ->
                Spacer(Modifier.height(10.dp))
                IdentificationCard(identified)
            }
            result.legacyUrl?.let { url ->
                Spacer(Modifier.height(10.dp))
                GlassCard(padding = 14.dp) {
                    Text(
                        "Este enlace se descarga con el motor yt-dlp existente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.textPrimary
                    )
                    Text(
                        "Se abrirá la pantalla clásica de audio para elegir la calidad real disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton("Abrir descarga clásica", { onLegacy(url) })
                }
            }

            if (result.tracks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        "${result.tracks.size} resultados",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (selectedIds.isEmpty()) "Seleccionar todo" else "Quitar selección",
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onSelectAll)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                result.tracks.forEach { track ->
                    ResultRow(
                        track = track,
                        checked = track.id in selectedIds,
                        onToggle = { onToggleSelection(track.id) },
                        onClick = { onOpenTrack(track) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (selectedIds.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = "Preparar ${selectedIds.size} descargas",
                        onClick = onStartBatch,
                        icon = Icons.Default.Download,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (result.errors.isNotEmpty() && result.tracks.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                GlassCard(padding = 13.dp) {
                    Text(
                        "Detalle de proveedores",
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.textSecondary
                    )
                    result.errors.take(4).forEach {
                        Text(
                            "· $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textSecondary
                        )
                    }
                }
            }
        }

        if (outcome == null && !searching && error == null) {
            EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "Busca música en alta calidad",
                message = "ParDL consulta varios proveedores, prioriza FLAC y te muestra " +
                    "las alternativas reales antes de descargar."
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) glass.accent else glass.glassFill)
            .border(0.8.dp, if (selected) glass.accent else glass.glassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(0xFF04121E) else glass.textSecondary
        )
    }
}

@Composable
private fun ResultRow(
    track: TrackCandidate,
    checked: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassModifier(18.dp)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(track.artworkUrl, 56.dp, 14.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    track.artist,
                    track.album,
                    TrackCandidate.formatDuration(track.durationSec).ifBlank { null }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusChip(track.providerName, AccentCyan)
                if (!track.downloadable) StatusChip("Solo identificación", Warning)
            }
        }
        if (track.downloadable) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = glass.accent,
                    uncheckedColor = glass.textSecondary
                )
            )
        } else {
            Icon(
                Icons.Default.Link,
                null,
                tint = glass.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun IdentificationCard(track: TrackCandidate) {
    val glass = LocalGlass.current
    GlassCard(strong = true, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(track.artworkUrl, 46.dp, 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Identificado: ${track.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.artist} · ${track.providerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    color: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassModifier(16.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = LocalGlass.current.textSecondary
            )
        }
    }
}

@Composable
private fun LoadingBlock(text: String) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = glass.accent
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
    }
}

// ---------------------------------------------------------------------------
// Calidades
// ---------------------------------------------------------------------------

@Composable
private fun QualitiesStep(
    track: TrackCandidate?,
    qualities: List<AudioQuality>,
    message: String,
    loading: Boolean,
    selected: AudioQuality?,
    onSelect: (AudioQuality) -> Unit,
    albumLoading: Boolean,
    onLoadAlbum: () -> Unit,
    alternativesLoading: Boolean,
    onFindAlternatives: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        track?.let {
            GlassCard(strong = true, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(it.artworkUrl, 84.dp, 18.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            it.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = glass.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(it.artist, style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
                        Text(
                            listOfNotNull(it.album, TrackCandidate.formatDuration(it.durationSec).ifBlank { null })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (loading) {
            LoadingBlock("Consultando calidades reales…")
        } else {
            if (message.isNotBlank()) {
                NoticeCard(
                    icon = if (QualityRanker.hasFlac(qualities)) Icons.Default.Verified else Icons.Default.WarningAmber,
                    title = if (QualityRanker.hasFlac(qualities)) "FLAC disponible" else "Atención",
                    message = message,
                    color = if (QualityRanker.hasFlac(qualities)) Success else Warning
                )
            }
            Spacer(Modifier.height(10.dp))
            if (qualities.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.GraphicEq,
                    title = "Sin calidades",
                    message = "El proveedor no declaró ninguna calidad descargable para esta pista.",
                    actionLabel = "Volver a consultar",
                    onAction = onRetry
                )
            } else {
                val recommended = QualityRanker.recommended(qualities)
                qualities.forEachIndexed { index, quality ->
                    QualityOption(
                        quality = quality,
                        selected = selected?.id == quality.id,
                        recommended = recommended?.id == quality.id,
                        onClick = { onSelect(quality) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(6.dp))
                PillButton(
                    text = "Revisar metadatos",
                    onClick = onContinue,
                    enabled = selected != null,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = if (alternativesLoading) "Buscando otras fuentes…" else "Buscar en otros proveedores",
                    onClick = onFindAlternatives,
                    primary = false,
                    enabled = !alternativesLoading && track != null,
                    icon = Icons.Default.CloudDownload,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PillButton(
                    text = if (albumLoading) "Cargando álbum…" else "Ver álbum completo",
                    onClick = onLoadAlbum,
                    primary = false,
                    enabled = !albumLoading && track != null,
                    icon = Icons.Default.Album,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun QualityOption(
    quality: AudioQuality,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassModifier(18.dp, strong = selected)
            .border(
                1.dp,
                if (selected) glass.accent else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (selected) glass.accent else glass.textSecondary,
            modifier = Modifier.size(21.dp).padding(top = 1.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quality.shortLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary
                )
                if (recommended) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip("Recomendado", Success)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(quality.providerName)
                    append(" · ").append(quality.sizeLabel)
                    if (quality.channels > 1) append(" · ${quality.channels} canales")
                },
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusChip(
                    if (quality.lossless) "Sin pérdidas" else "Con pérdida",
                    if (quality.lossless) Success else Warning
                )
                StatusChip(
                    if (quality.verified) "Verificado" else "Estimado",
                    if (quality.verified) AccentCyan else glass.textSecondary
                )
                if (quality.spatial) StatusChip("Espacial", AccentBlue)
                if (quality.previewOnly) StatusChip("Vista previa", Warning)
            }
            quality.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Vista previa de metadatos
// ---------------------------------------------------------------------------

@Composable
private fun PreviewStep(
    draft: DownloadDraft?,
    loading: Boolean,
    onConfirm: (DownloadDraft) -> Unit
) {
    val glass = LocalGlass.current
    if (loading || draft == null) {
        Column(Modifier.fillMaxSize()) {
            LoadingBlock("Preparando metadatos, portada y letras…")
        }
        return
    }

    val metadata = draft.metadata
    var title by remember(draft) { mutableStateOf(metadata.title.value.orEmpty()) }
    var artist by remember(draft) { mutableStateOf(metadata.artist.value.orEmpty()) }
    var album by remember(draft) { mutableStateOf(metadata.album.value.orEmpty()) }
    var albumArtist by remember(draft) { mutableStateOf(metadata.albumArtist.value.orEmpty()) }
    var genre by remember(draft) { mutableStateOf(metadata.genre.value.orEmpty()) }
    var year by remember(draft) { mutableStateOf(metadata.year.value.orEmpty()) }
    var trackNumber by remember(draft) { mutableStateOf(metadata.trackNumber.value.orEmpty()) }
    var discNumber by remember(draft) { mutableStateOf(metadata.discNumber.value.orEmpty()) }
    var embedCover by remember(draft) { mutableStateOf(draft.embedCover) }
    var embedLyrics by remember(draft) { mutableStateOf(draft.embedLyrics) }
    var organize by remember(draft) { mutableStateOf(draft.organizeByAlbum) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        GlassCard(strong = true, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(metadata.artworkUrl, 108.dp, 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        metadata.title.value ?: draft.track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        metadata.artist.value ?: draft.track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusChip(draft.quality.shortLabel, AccentCyan)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        draft.quality.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Información musical")
        GlassCard(padding = 14.dp) {
            MetaLine("Título", metadata.title.value, metadata.title.status.label)
            MetaLine("Artista", metadata.artist.value, metadata.artist.status.label)
            MetaLine("Artistas invitados", null, null)
            MetaLine("Álbum", metadata.album.value, metadata.album.status.label)
            MetaLine("Artista del álbum", metadata.albumArtist.value, metadata.albumArtist.status.label)
            MetaLine("Año", metadata.year.value, metadata.year.status.label)
            MetaLine("Fecha", metadata.releaseDate.value, metadata.releaseDate.status.label)
            MetaLine("Género", metadata.genre.value, metadata.genre.status.label)
            MetaLine("Compositor", metadata.composer.value, metadata.composer.status.label)
            MetaLine("Productor", metadata.producer.value, metadata.producer.status.label)
            MetaLine("Sello", metadata.label.value, metadata.label.status.label)
            MetaLine("Pista", metadata.trackNumber.value, metadata.trackNumber.status.label)
            MetaLine("Disco", metadata.discNumber.value, metadata.discNumber.status.label)
            MetaLine("Duración", TrackCandidate.formatDuration(metadata.durationSec).ifBlank { null })
            MetaLine("ISRC", metadata.isrc.value, metadata.isrc.status.label)
            MetaLine("Créditos", metadata.credits.value, metadata.credits.status.label)
            MetaLine("Derechos", metadata.copyright.value, metadata.copyright.status.label)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Información del archivo")
        GlassCard(padding = 14.dp) {
            MetaLine("Formato", draft.quality.format.label)
            MetaLine("Códec", draft.quality.format.label)
            MetaLine("Contenedor", ".${draft.quality.format.extension}")
            MetaLine("Calidad", draft.quality.resolutionLabel)
            MetaLine("Bitrate", draft.quality.bitrateKbps?.let { "$it kbps" })
            MetaLine("Frecuencia", draft.quality.sampleRateHz?.let { AudioQuality.formatSampleRate(it) })
            MetaLine("Profundidad", draft.quality.bitDepth?.let { "$it bit" })
            MetaLine("Canales", draft.quality.channels.toString())
            MetaLine("Tamaño", draft.quality.sizeLabel)
            MetaLine("Proveedor", draft.quality.providerName)
            MetaLine(
                "Tipo",
                when {
                    draft.quality.spatial -> "Audio espacial"
                    draft.quality.lossless -> "Sin pérdidas"
                    else -> "Con pérdida"
                }
            )
            MetaLine("Disponibilidad", if (draft.quality.available) "Disponible" else "No disponible")
            MetaLine("Datos técnicos", draft.quality.verifiedLabel)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Editar antes de descargar")
        GlassCard(padding = 14.dp) {
            EditableField("Título", title) { title = it }
            EditableField("Artista", artist) { artist = it }
            EditableField("Álbum", album) { album = it }
            EditableField("Artista del álbum", albumArtist) { albumArtist = it }
            EditableField("Género", genre) { genre = it }
            EditableField("Año", year, KeyboardType.Number) { year = it }
            EditableField("Número de pista", trackNumber, KeyboardType.Number) { trackNumber = it }
            EditableField("Número de disco", discNumber, KeyboardType.Number) { discNumber = it }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Portada")
        GlassCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(metadata.artworkUrl, 86.dp, 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            metadata.artworkWidth != null && metadata.artworkHeight != null ->
                                "${metadata.artworkWidth}×${metadata.artworkHeight}"
                            metadata.artworkUrl != null -> "Portada disponible"
                            else -> "Sin portada"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = glass.textPrimary
                    )
                    Text(
                        metadata.artworkMime ?: "Formato desconocido",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            GlassSwitchRow(
                title = "Incrustar portada",
                subtitle = "Se escribirá en el archivo final",
                checked = embedCover,
                onCheckedChange = { embedCover = it },
                enabled = metadata.artworkUrl != null
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Letras")
        GlassCard(padding = 14.dp) {
            val lyrics = metadata.lyrics
            if (lyrics == null || !lyrics.available) {
                Text(
                    "No hay letras disponibles en fuentes compatibles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textSecondary
                )
            } else {
                Text(
                    "Fuente: ${lyrics.source ?: "—"} · " +
                        if (lyrics.syncedAvailable) "sincronizadas" else "sin sincronizar",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    (lyrics.plain ?: lyrics.synced.orEmpty())
                        .lines().take(10).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textPrimary,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
                lyrics.licenseNote?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.textSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                GlassSwitchRow(
                    title = "Incrustar letras",
                    subtitle = "Solo si tienes derecho a usarlas",
                    checked = embedLyrics,
                    onCheckedChange = { embedLyrics = it }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 14.dp) {
            GlassSwitchRow(
                title = "Organizar por artista y álbum",
                subtitle = "Crea subcarpetas en la carpeta elegida",
                checked = organize,
                onCheckedChange = { organize = it }
            )
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(padding = 14.dp) {
            Text(
                "Resumen de etiquetas",
                style = MaterialTheme.typography.labelLarge,
                color = glass.textPrimary
            )
            Spacer(Modifier.height(5.dp))
            val willWrite = buildList {
                if (title.isNotBlank()) add("Título")
                if (artist.isNotBlank()) add("Artista")
                if (album.isNotBlank()) add("Álbum")
                if (albumArtist.isNotBlank()) add("Artista del álbum")
                if (genre.isNotBlank()) add("Género")
                if (year.isNotBlank()) add("Año")
                if (trackNumber.isNotBlank()) add("Pista")
                if (discNumber.isNotBlank()) add("Disco")
                if (metadata.isrc.isPresent) add("ISRC")
                if (metadata.composer.isPresent) add("Compositor")
                if (embedCover && metadata.artworkUrl != null) add("Portada")
                if (embedLyrics && metadata.lyrics?.available == true) add("Letras")
            }
            Text(
                willWrite.joinToString(", ").ifBlank { "Ninguna etiqueta" },
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary
            )
            Spacer(Modifier.height(6.dp))
            val missing = buildList {
                if (metadata.composer.value == null) add("compositor")
                if (metadata.producer.value == null) add("productor")
                if (metadata.label.value == null) add("sello")
                if (metadata.isrc.value == null) add("ISRC")
                if (metadata.lyrics?.available != true) add("letras")
            }
            if (missing.isNotEmpty()) {
                Text(
                    "No disponible: ${missing.joinToString(", ")}. No se inventarán valores.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning
                )
            }
            Spacer(Modifier.height(12.dp))
            val edited = draft.copy(
                metadata = metadata.editableCopy(
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    discNumber = discNumber
                ),
                embedCover = embedCover,
                embedLyrics = embedLyrics,
                organizeByAlbum = organize
            )
            PillButton(
                text = "Confirmar y descargar",
                onClick = { onConfirm(edited) },
                enabled = title.isNotBlank() &&
                    artist.isNotBlank() &&
                    draft.quality.available,
                icon = Icons.Default.CloudDownload,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalGlass.current.textSecondary
        )
        Spacer(Modifier.height(4.dp))
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            keyboardType = keyboardType,
            imeAction = androidx.compose.ui.text.input.ImeAction.Next
        )
    }
}

// ---------------------------------------------------------------------------
// Descarga múltiple
// ---------------------------------------------------------------------------

@Composable
private fun BatchStep(
    plan: MusicRepository.BatchPlan?,
    loading: Boolean,
    confirming: Boolean,
    onConfirm: () -> Unit,
    onQualityChange: (TrackCandidate, AudioQuality) -> Unit,
    onBack: () -> Unit
) {
    val glass = LocalGlass.current
    var changing by remember { mutableStateOf<MusicRepository.BatchItem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        if (loading || plan == null) {
            LoadingBlock("Consultando calidades para cada pista…")
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        GlassCard(strong = true, padding = 14.dp) {
            Text(
                "Resumen de la descarga",
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${plan.readyCount} de ${plan.items.size} pistas listas · " +
                    "tamaño estimado ${AudioQuality.formatBytes(plan.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.textSecondary
            )
            if (plan.missingFlac > 0) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "FLAC no disponible en ${plan.missingFlac} pistas. " +
                        "Revisa las alternativas antes de confirmar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        plan.items.forEach { item ->
            BatchRow(
                item = item,
                onClick = { changing = item }
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = "Volver",
                onClick = onBack,
                primary = false,
                modifier = Modifier.weight(1f)
            )
            PillButton(
                text = if (confirming) "Preparando…" else "Confirmar ${plan.readyCount}",
                onClick = onConfirm,
                enabled = !confirming && plan.readyCount > 0,
                icon = Icons.Default.Download,
                modifier = Modifier.weight(1.4f)
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    changing?.let { item ->
        QualityDialog(
            item = item,
            onDismiss = { changing = null },
            onSelect = { quality ->
                onQualityChange(item.track, quality)
                changing = null
            }
        )
    }
}

@Composable
private fun BatchRow(
    item: MusicRepository.BatchItem,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassModifier(16.dp)
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(item.track.artworkUrl, 46.dp, 12.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
                maxLines = 1
            )
            Text(
                item.selected?.shortLabel ?: "Sin calidad disponible",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.selected == null) Danger else AccentCyan
            )
            item.warning?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "Cambiar",
            style = MaterialTheme.typography.labelMedium,
            color = glass.accent,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AlternativesDialog(
    track: TrackCandidate?,
    alternatives: List<TrackCandidate>,
    onDismiss: () -> Unit,
    onSelect: (TrackCandidate) -> Unit
) {
    val glass = LocalGlass.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassModifier(24.dp, strong = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Otras fuentes",
                style = MaterialTheme.typography.titleLarge,
                color = glass.textPrimary
            )
            Text(
                track?.let { "Para «${it.title}» de ${it.artist}" } ?: "Misma canción en otros proveedores",
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            if (alternatives.isEmpty()) {
                Text(
                    "No se encontraron otras fuentes compatibles. No se inventará una descarga.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning
                )
            } else {
                alternatives.take(12).forEach { alternative ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glassModifier(16.dp)
                            .clickable { onSelect(alternative) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(alternative.artworkUrl, 46.dp, 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                alternative.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                alternative.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.textSecondary,
                                maxLines = 1
                            )
                            Text(
                                listOfNotNull(
                                    alternative.providerName,
                                    alternative.album,
                                    TrackCandidate.formatDuration(alternative.durationSec)
                                        .ifBlank { null }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "Elegir",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.accent
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            PillButton("Cerrar", onDismiss, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QualityDialog(
    item: MusicRepository.BatchItem,
    onDismiss: () -> Unit,
    onSelect: (AudioQuality) -> Unit
) {
    val glass = LocalGlass.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassModifier(24.dp, strong = true)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.titleLarge,
                color = glass.textPrimary
            )
            Text(
                "Elige la calidad para esta pista",
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            if (item.qualities.isEmpty()) {
                Text(
                    "No hay calidades disponibles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Danger
                )
            } else {
                item.qualities.forEach { quality ->
                    QualityOption(
                        quality = quality,
                        selected = item.selected?.id == quality.id,
                        recommended = QualityRanker.recommended(item.qualities)?.id == quality.id,
                        onClick = { onSelect(quality) }
                    )
                    Spacer(Modifier.height(7.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            PillButton(
                "Cerrar",
                onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
