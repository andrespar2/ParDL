package com.andrespar2.pardl.ui

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.core.DownloadState
import com.andrespar2.pardl.core.Downloader
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.EngineState
import com.andrespar2.pardl.core.Format
import com.andrespar2.pardl.core.MediaInfo
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.QualityChoice
import com.andrespar2.pardl.core.Storage
import com.andrespar2.pardl.core.music.MusicRepository
import com.andrespar2.pardl.core.music.TrackMetadata
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.OutlineSoft
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.Surface2
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.TextPrimary
import com.andrespar2.pardl.ui.theme.TextSecondary
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    kind: DownloadKind,
    navKey: Int,
    initialUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val downloadState by Downloader.state.collectAsState()
    val analyzing by Downloader.analyzing.collectAsState()
    val engineState by Engine.state.collectAsState()

    var url by rememberSaveable(navKey) { mutableStateOf(initialUrl.orEmpty()) }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedKey by rememberSaveable(navKey) {
        mutableStateOf(if (kind == DownloadKind.VIDEO) "7" else Format.defaultAudioChoice().key)
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf(false) }
    var pickGeneration by remember { mutableStateOf(0) }
    var folderLabel by remember { mutableStateOf(prefs.folderName) }
    var metadataProvider by remember { mutableStateOf("musicbrainz") }
    var metadataPreview by remember { mutableStateOf<MusicRepository.MetadataPreview?>(null) }
    var metadataLoading by remember { mutableStateOf(false) }
    var metadataError by remember { mutableStateOf<String?>(null) }
    var bindMetadata by remember { mutableStateOf(false) }
    var embedCover by remember { mutableStateOf(true) }

    val engineReady = engineState is EngineState.Ready

    // Al entrar en la pantalla se descarta cualquier resultado anterior
    // (éxito/error/cancelación) salvo que haya una descarga en curso, para no
    // mostrar el estado de la descarga previa.
    LaunchedEffect(navKey) {
        if (Downloader.state.value !is DownloadState.Downloading) {
            Downloader.reset()
        }
        delay(400)
        focusManager.clearFocus()
    }

    LaunchedEffect(info) {
        if (info != null) {
            delay(200)
            focusManager.clearFocus()
        }
    }

    fun currentChoices(): List<QualityChoice> =
        if (kind == DownloadKind.VIDEO) {
            Format.videoQualityChoices()
        } else {
            val realFormats = info?.audioFormats.orEmpty()
            if (realFormats.isNotEmpty()) {
                Format.audioChoicesFromFormats(realFormats)
            } else {
                Format.audioQualityChoices()
            }
        }

    fun selectedChoice(): QualityChoice =
        currentChoices().firstOrNull { it.key == selectedKey } ?: currentChoices().first()

    // Lanza la descarga (requiere que ya haya carpeta elegida).
    fun actuallyStart() {
        val media = info ?: return
        val tree = prefs.treeUri ?: return
        val targetUrl = media.webpageUrl ?: Format.cleanUrl(url)
        Downloader.start(
            context = context,
            url = targetUrl,
            kind = kind,
            choice = selectedChoice(),
            treeUriString = tree,
            titleHint = media.title,
            metadata = if (kind == DownloadKind.AUDIO && bindMetadata) metadataPreview?.metadata else null,
            embedCover = kind == DownloadKind.AUDIO && embedCover,
            embedLyrics = false
        )
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Storage.takePersistable(context, uri)
            prefs.treeUri = uri.toString()
            val name = Storage.folderDisplayName(context, uri) ?: uri.lastPathSegment ?: "Carpeta"
            prefs.folderName = name
            folderLabel = name
        }
        // Señala que terminó la elección (haya o no carpeta).
        pickGeneration++
    }

    fun requestDownload() {
        if (prefs.treeUri.isNullOrBlank()) {
            pendingDownload = true
            folderLauncher.launch(null)
        } else {
            actuallyStart()
        }
    }

    // Tras elegir carpeta, arranca la descarga si venía pendiente de ello.
    LaunchedEffect(pickGeneration) {
        if (pickGeneration > 0 && pendingDownload) {
            pendingDownload = false
            actuallyStart()
        }
    }

    fun analyze() {
        val cleaned = Format.cleanUrl(url)
        url = cleaned
        val check = Format.validateUrl(cleaned)
        if (!check.valid) {
            errorMsg = check.reason
            return
        }
        errorMsg = null
        info = null
        Downloader.reset()
        scope.launch {
            Downloader.analyze(cleaned)
                .onSuccess { media ->
                    info = media
                    url = media.webpageUrl ?: cleaned
                    metadataPreview = null
                    metadataError = null
                    bindMetadata = false
                    focusManager.clearFocus()
                    selectedKey = if (kind == DownloadKind.VIDEO) {
                        "7"
                    } else {
                        media.audioFormats.firstOrNull()
                            ?.let { "fmt:${it.formatId}" }
                            ?: Format.defaultAudioChoice().key
                    }
                }
                .onFailure { errorMsg = it.message ?: "No se pudo analizar el enlace." }
        }
    }

    fun findMetadata() {
        val media = info ?: return
        metadataLoading = true
        metadataError = null
        metadataPreview = null
        bindMetadata = false
        scope.launch {
            MusicRepository.findMetadata(
                context = context,
                title = media.title,
                artist = media.uploader.orEmpty(),
                providerId = metadataProvider
            ).onSuccess {
                metadataPreview = it
                bindMetadata = true
            }.onFailure {
                metadataError = it.message ?: "No se encontraron metadatos."
            }
            metadataLoading = false
        }
    }

    val title = if (kind == DownloadKind.VIDEO) "Descargar video (MP4)" else "Descargar audio (MP3)"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ParDLTopBar(title = title, onBack = onBack)

        Column(Modifier.padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(18.dp))

            when (val st = downloadState) {
                is DownloadState.Downloading -> DownloadingView(
                    state = st,
                    onCancel = { Downloader.cancel() }
                )

                is DownloadState.Success -> SuccessView(
                    state = st,
                    onAgain = { Downloader.reset() },
                    onHome = onBack
                )

                is DownloadState.Failure -> FailureView(
                    state = st,
                    onRetry = { Downloader.reset() },
                    onHome = onBack
                )

                is DownloadState.Cancelled -> CancelledView(
                    onRetry = { Downloader.reset() }
                )

                DownloadState.Idle -> FormView(
                    kind = kind,
                    url = url,
                    onUrlChange = { url = it; errorMsg = null },
                    onPaste = {
                        val clip = (context.getSystemService(ClipboardManager::class.java))?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            url = Format.cleanUrl(clip.getItemAt(0).coerceToText(context).toString())
                            errorMsg = null
                        }
                    },
                    analyzing = analyzing,
                    engineReady = engineReady,
                    info = info,
                    errorMsg = errorMsg,
                    choices = currentChoices(),
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it },
                    folderLabel = folderLabel,
                    onChangeFolder = { folderLauncher.launch(null) },
                    onAnalyze = { analyze() },
                    onDownload = { requestDownload() },
                    metadataProvider = metadataProvider,
                    onMetadataProviderChange = {
                        metadataProvider = it
                        metadataPreview = null
                        metadataError = null
                        bindMetadata = false
                    },
                    metadataPreview = metadataPreview,
                    metadataLoading = metadataLoading,
                    metadataError = metadataError,
                    bindMetadata = bindMetadata,
                    onBindMetadataChange = { bindMetadata = it },
                    onFindMetadata = { findMetadata() },
                    embedCover = embedCover,
                    onEmbedCoverChange = { embedCover = it }
                )
            }

            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun FormView(
    kind: DownloadKind,
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    analyzing: Boolean,
    engineReady: Boolean,
    info: MediaInfo?,
    errorMsg: String?,
    choices: List<QualityChoice>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    folderLabel: String?,
    onChangeFolder: () -> Unit,
    onAnalyze: () -> Unit,
    onDownload: () -> Unit,
    metadataProvider: String,
    onMetadataProviderChange: (String) -> Unit,
    metadataPreview: MusicRepository.MetadataPreview?,
    metadataLoading: Boolean,
    metadataError: String?,
    bindMetadata: Boolean,
    onBindMetadataChange: (Boolean) -> Unit,
    onFindMetadata: () -> Unit,
    embedCover: Boolean,
    onEmbedCoverChange: (Boolean) -> Unit
) {
    val isOriginalAudio = kind == DownloadKind.AUDIO && choices.any { it.formatId != null }
    Text(
        "Pega el enlace del contenido",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = info == null,
        placeholder = { Text("https://…", color = TextDim) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = OutlineSoft,
            cursorColor = AccentCyan,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = Surface1,
            unfocusedContainerColor = Surface1
        ),
        trailingIcon = {
            if (info == null) {
                TextButton(onClick = onPaste) {
                    Text("Pegar", color = AccentCyan)
                }
            }
        }
    )

    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(
            text = if (analyzing) "Analizando…" else "Analizar",
            enabled = !analyzing && url.isNotBlank() && engineReady,
            onClick = onAnalyze,
            modifier = Modifier.weight(1f)
        )
    }

    if (!engineReady) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Esperando a que el motor esté listo…",
            style = MaterialTheme.typography.bodyMedium,
            color = Warning
        )
    }

    if (errorMsg != null) {
        Spacer(Modifier.height(14.dp))
        NoticeCard(
            text = errorMsg,
            color = Danger,
            title = "No se pudo analizar el enlace"
        )
    }

    if (info != null) {
        Spacer(Modifier.height(18.dp))
        MediaCard(info)
        Spacer(Modifier.height(18.dp))

        Text(
            if (kind == DownloadKind.VIDEO) "Resolución" else "Calidad de audio disponible",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        if (kind == DownloadKind.AUDIO) {
            Text(
                if (isOriginalAudio) {
                    "Formatos reales del enlace. Se descarga el original sin recodificar; " +
                        "los metadatos completos dependen del contenedor."
                } else {
                    "El motor no declaró formatos de audio; se usará la extracción MP3 indicada."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
        }

        choices.forEach { choice ->
            val available = if (kind == DownloadKind.VIDEO && choice.height > 0) {
                info.heights.isEmpty() || info.heights.contains(choice.height)
            } else true
            val note = when {
                kind != DownloadKind.VIDEO -> choice.note
                choice.height == 0 -> info.heights.maxOrNull()?.let { "hasta ${it}p" }
                !available -> "no disponible"
                else -> null
            }
            QualityRow(
                label = choice.label,
                note = note,
                selected = choice.key == selectedKey,
                enabled = available,
                onClick = { onSelect(choice.key) }
            )
            Spacer(Modifier.height(8.dp))
        }

        if (kind == DownloadKind.AUDIO) {
            Spacer(Modifier.height(8.dp))
            MetadataBindingSection(
                provider = metadataProvider,
                onProviderChange = onMetadataProviderChange,
                preview = metadataPreview,
                loading = metadataLoading,
                error = metadataError,
                bind = bindMetadata,
                onBindChange = onBindMetadataChange,
                onFind = onFindMetadata,
                embedCover = embedCover,
                onEmbedCoverChange = onEmbedCoverChange
            )
        }

        Spacer(Modifier.height(14.dp))
        FolderRow(folderLabel, onChangeFolder)
        Spacer(Modifier.height(18.dp))

        PrimaryButton(
            text = when {
                kind == DownloadKind.VIDEO -> "Descargar en MP4"
                isOriginalAudio -> "Descargar audio completo"
                else -> "Extraer audio en MP3"
            },
            enabled = true,
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MetadataBindingSection(
    provider: String,
    onProviderChange: (String) -> Unit,
    preview: MusicRepository.MetadataPreview?,
    loading: Boolean,
    error: String?,
    bind: Boolean,
    onBindChange: (Boolean) -> Unit,
    onFind: () -> Unit,
    embedCover: Boolean,
    onEmbedCoverChange: (Boolean) -> Unit
) {
    val providers = listOf(
        "musicbrainz" to "MusicBrainz",
        "apple-music" to "Apple Music",
        "deezer" to "Deezer",
        "internet-archive" to "Internet Archive"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, OutlineSoft, RoundedCornerShape(16.dp))
            .padding(15.dp)
    ) {
        Text(
            "Metadatos de otro proveedor",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "El audio mantiene la calidad elegida. Puedes vincular título, artista, álbum, " +
                "año, portada y créditos desde una fuente distinta.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        providers.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                row.forEach { (id, label) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (provider == id) AccentCyan.copy(alpha = 0.18f) else Surface2)
                            .border(
                                1.dp,
                                if (provider == id) AccentCyan else OutlineSoft,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onProviderChange(id) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (provider == id) AccentCyan else TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }

        if (loading) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AccentCyan
                )
                Spacer(Modifier.size(8.dp))
                Text("Buscando coincidencia…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(5.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = Danger)
        }

        if (preview != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(preview.metadata.artworkUrl, 56.dp, 13.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preview.metadata.title.value ?: "Sin título",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            preview.metadata.artist.value,
                            preview.metadata.album.value,
                            preview.metadata.year.value
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(preview.providerName)
                            if (preview.confidence > 0) append(" · coincidencia ").append(preview.confidence).append('%')
                            else append(" · búsqueda directa")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            GlassSwitchRow(
                title = "Vincular estos metadatos",
                subtitle = "Se escribirán al finalizar la descarga",
                checked = bind,
                onCheckedChange = onBindChange
            )
            GlassSwitchRow(
                title = "Incrustar portada",
                subtitle = if (preview.metadata.artworkUrl == null) "Esta fuente no aportó portada" else "Se descargará y se escribirá",
                checked = embedCover && preview.metadata.artworkUrl != null,
                enabled = preview.metadata.artworkUrl != null,
                onCheckedChange = onEmbedCoverChange
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Se escribirán: " + buildList {
                    preview.metadata.title.value?.let { add("título") }
                    preview.metadata.artist.value?.let { add("artista") }
                    preview.metadata.album.value?.let { add("álbum") }
                    preview.metadata.year.value?.let { add("año") }
                    preview.metadata.trackNumber.value?.let { add("pista") }
                    preview.metadata.genre.value?.let { add("género") }
                    preview.metadata.isrc.value?.let { add("ISRC") }
                    preview.metadata.composer.value?.let { add("compositor") }
                    if (embedCover && preview.metadata.artworkUrl != null) add("portada")
                }.ifEmpty { listOf("solo etiquetas básicas") }.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        } else if (!loading) {
            Spacer(Modifier.height(6.dp))
            PrimaryButton(
                text = "Buscar coincidencia",
                enabled = true,
                onClick = onFind,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(7.dp))
        Text(
            "Metadatos completos: MP3, M4A, FLAC y WAV. En WebM/Opus se aplican las " +
                "etiquetas básicas del motor y se conserva el audio original.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}

@Composable
private fun MediaCard(info: MediaInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, OutlineSoft, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            info.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!info.uploader.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(info.uploader, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        val durations = Format.formatDuration(info.durationSec)
        if (durations.isNotEmpty()) {
            Text("Duración: $durations", style = MaterialTheme.typography.bodyMedium, color = TextDim)
        }
        if (info.heights.isNotEmpty()) {
            Text(
                "Resoluciones: " + info.heights.joinToString(", ") { "${it}p" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim
            )
        }
        if (info.audioFormats.isNotEmpty()) {
            Text(
                "Audio: " + info.audioFormats.take(4).joinToString(", ") {
                    buildString {
                        append(it.acodec.ifBlank { it.ext }.uppercase())
                        if (it.abrKbps > 0) append(" ").append(it.abrKbps).append(" kbps")
                        append(" .").append(it.ext)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    note: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Surface2 else Surface1)
            .border(
                width = 1.dp,
                color = if (selected) AccentCyan else OutlineSoft,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "◉" else "○",
            color = if (!enabled) TextDim else if (selected) AccentCyan else TextSecondary,
            fontSize = 16.sp
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) TextPrimary else TextDim,
            modifier = Modifier.weight(1f)
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) TextSecondary else TextDim
            )
        }
    }
}

@Composable
private fun FolderRow(folderLabel: String?, onChangeFolder: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Surface1)
            .border(1.dp, OutlineSoft, RoundedCornerShape(13.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Carpeta de descarga", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                folderLabel ?: "Sin elegir",
                style = MaterialTheme.typography.bodyLarge,
                color = if (folderLabel != null) TextPrimary else Warning
            )
        }
        TextButton(onClick = onChangeFolder) {
            Text(if (folderLabel == null) "Elegir" else "Cambiar", color = AccentCyan)
        }
    }
}

@Composable
private fun DownloadingView(state: DownloadState.Downloading, onCancel: () -> Unit) {
    Text(
        "Descargando…",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary
    )
    Spacer(Modifier.height(6.dp))
    Text(
        state.title,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(20.dp))

    val hasPercent = state.percent > 0f
    Text(
        text = if (hasPercent) "${state.percent.toInt()} %" else "Procesando…",
        style = MaterialTheme.typography.displaySmall,
        color = AccentCyan
    )
    Spacer(Modifier.height(14.dp))

    LinearProgressIndicator(
        progress = { (state.percent / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = AccentCyan,
        trackColor = Surface2
    )

    if (state.etaSec >= 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Tiempo restante estimado: ${Format.formatEta(state.etaSec)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }

    if (state.line.isNotBlank()) {
        Spacer(Modifier.height(14.dp))
        Text(
            state.line,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(Modifier.height(24.dp))
    GhostButton(text = "Cancelar descarga", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(
        "Mantén la pantalla encendida. La descarga continúa aunque cambies de app, " +
            "pero puede pausarse si el sistema libera recursos.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextDim
    )
}

@Composable
private fun SuccessView(
    state: DownloadState.Success,
    onAgain: () -> Unit,
    onHome: () -> Unit
) {
    NoticeCard(
        title = "¡Descarga completada!",
        text = state.fileName + (state.detail?.let { "\n$it" } ?: ""),
        color = Success
    )
    Spacer(Modifier.height(10.dp))
    if (!state.savedTo.isNullOrBlank()) {
        Text(
            "Guardado en: ${state.savedTo}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
    if (!state.metadataReport.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            state.metadataReport,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
    Spacer(Modifier.height(22.dp))
    PrimaryButton(
        text = "Descargar otro",
        onClick = onAgain,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    GhostButton(text = "Volver al inicio", onClick = onHome, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun FailureView(
    state: DownloadState.Failure,
    onRetry: () -> Unit,
    onHome: () -> Unit
) {
    NoticeCard(title = "No se pudo completar", text = state.message, color = Danger)
    if (!state.detail.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            state.detail,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            fontFamily = FontFamily.Monospace
        )
    }
    Spacer(Modifier.height(22.dp))
    PrimaryButton(text = "Reintentar", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    GhostButton(text = "Volver al inicio", onClick = onHome, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CancelledView(onRetry: () -> Unit) {
    NoticeCard(title = "Descarga cancelada", text = "Puedes intentarlo de nuevo.", color = Warning)
    Spacer(Modifier.height(22.dp))
    PrimaryButton(text = "Volver a intentar", onClick = onRetry, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NoticeCard(title: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentCyan,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            disabledContainerColor = Surface2,
            disabledContentColor = TextDim
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Surface1,
            contentColor = TextPrimary,
            disabledContainerColor = Surface2,
            disabledContentColor = TextDim
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
