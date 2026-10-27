package com.andrespar2.pardl.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andrespar2.pardl.core.AppInfo
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.EngineState
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.Storage
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Appearance
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs.get(context) }
    val glass = LocalGlass.current
    val engineState by Engine.state.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    var folderLabel by remember { mutableStateOf(prefs.folderName) }
    var lyricsEnabled by remember { mutableStateOf(prefs.musicEmbedLyrics) }
    // Fuerza una recomposición cuando cambia cualquier preferencia.
    val refreshState = refresh
    LaunchedEffect(refreshState) { }
    var version by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateOk by remember { mutableStateOf(true) }

    fun touched() { refresh++ }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Storage.takePersistable(context, uri)
            prefs.treeUri = uri.toString()
            val name = Storage.folderDisplayName(context, uri) ?: uri.lastPathSegment ?: "Carpeta"
            prefs.folderName = name
            folderLabel = name
            touched()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { touched() }

    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        version = Engine.ytdlpVersion(context)
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Carpeta y ajustes",
            subtitle = "Carpeta, apariencia, metadatos y motor",
            onBack = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            SectionLabel("Carpeta de descarga")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Default.Folder, "Carpeta", onClick = { folderLauncher.launch(null) })
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            folderLabel ?: "Sin elegir",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (folderLabel != null) glass.textPrimary else Warning,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Videos, audios y música se guardan aquí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textSecondary
                        )
                    }
                    PillButton(
                        if (folderLabel == null) "Elegir" else "Cambiar",
                        { folderLauncher.launch(null) },
                        primary = false
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Apariencia")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Palette,
                        null,
                        tint = glass.accent,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Tema",
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.textPrimary
                        )
                        Text(
                            "Liquid Glass · ParSound",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val modes = listOf(
                    "system" to "Sistema",
                    "dark" to "Oscuro",
                    "light" to "Claro",
                    "amoled" to "AMOLED"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { (value, label) ->
                        val selected = prefs.themeMode == value
                        ModePill(
                            label = label,
                            selected = selected,
                            modifier = Modifier.weight(1f)
                        ) {
                            Appearance.setMode(context, value)
                            touched()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Intensidad del desenfoque: ${(prefs.glassBlur * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.textPrimary
                )
                Slider(
                    value = prefs.glassBlur,
                    onValueChange = {
                        Appearance.setBlur(context, it)
                        touched()
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = glass.accent,
                        activeTrackColor = glass.accent,
                        inactiveTrackColor = glass.glassStrong
                    )
                )
                GlassSwitchRow(
                    title = "Animaciones",
                    subtitle = "Transiciones suaves; desactívalas para ahorrar recursos",
                    checked = prefs.animationsEnabled,
                    onCheckedChange = {
                        Appearance.setAnimations(context, it)
                        touched()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Metadatos, portadas y letras")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                GlassSwitchRow(
                    "Escribir metadatos",
                    "Título, artista, álbum, año, género, pista, disco, ISRC…",
                    prefs.musicEmbedMetadata
                ) { prefs.musicEmbedMetadata = it; touched() }
                GlassSwitchRow(
                    "Incrustar portada",
                    "Se descarga y se escribe en el archivo cuando el formato lo permite",
                    prefs.musicEmbedCover
                ) { prefs.musicEmbedCover = it; touched() }
                GlassSwitchRow(
                    "Incrustar letras",
                    "Solo si su origen y licencia lo permiten; LRCLIB es la fuente",
                    lyricsEnabled
                ) {
                    lyricsEnabled = it
                    prefs.musicEmbedLyrics = it
                    touched()
                }
                GlassSwitchRow(
                    "Organizar por artista y álbum",
                    "Crea subcarpetas dentro de la carpeta elegida",
                    prefs.musicOrganize
                ) { prefs.musicOrganize = it; touched() }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Notificaciones")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                Text(
                    "Las descargas muestran progreso y controles en una notificación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
                if (needsNotificationPermission) {
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        "Permitir notificaciones",
                        { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        icon = Icons.Default.Notifications,
                        primary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Motor de descargas (yt-dlp)")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                InfoRow("Versión incluida", version ?: "—")
                InfoRow(
                    "Estado",
                    when (engineState) {
                        is EngineState.Ready -> "Listo"
                        is EngineState.Initializing -> "Iniciando…"
                        is EngineState.Failed -> "Error"
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "El motor puede actualizarse solo al iniciar. También puedes forzar " +
                        "la comprobación manualmente si un sitio deja de funcionar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
                GlassSwitchRow(
                    "Actualizar yt-dlp automáticamente",
                    "Comprueba la última versión al iniciar la app",
                    prefs.autoUpdateYtdlp
                ) {
                    prefs.autoUpdateYtdlp = it
                    if (it) prefs.ytdlpAutoUpdated = false
                    touched()
                }
                Spacer(Modifier.height(9.dp))
                PillButton(
                    text = if (updating) "Actualizando…" else "Actualizar yt-dlp",
                    enabled = !updating && engineState is EngineState.Ready,
                    icon = Icons.Default.Refresh,
                    onClick = {
                        updating = true
                        updateMsg = null
                        scope.launch {
                            Engine.updateYtdlp(context)
                                .onSuccess {
                                    updateMsg = it
                                    updateOk = true
                                    prefs.ytdlpAutoUpdated = true
                                }
                                .onFailure {
                                    updateMsg = "No se pudo actualizar: ${it.message ?: "error"}"
                                    updateOk = false
                                }
                            version = Engine.ytdlpVersion(context)
                            updating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                updateMsg?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateOk) Success else Danger
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Almacenamiento")
            Spacer(Modifier.height(6.dp))
            GlassCard(padding = 14.dp) {
                Text(
                    "Las descargas se realizan primero en el almacenamiento privado de la app " +
                        "y luego se copian a la carpeta que elijas mediante Storage Access " +
                        "Framework. Así funciona de forma segura en Android 10 y posteriores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "ParDL v${AppInfo.VERSION} · ${AppInfo.AUTHOR}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    androidx.compose.foundation.layout.Box(
        modifier
            .glassModifier(18.dp, strong = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) glass.accent else glass.textSecondary
        )
    }
}
