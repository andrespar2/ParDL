package com.andrespar2.pardl.ui

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrespar2.pardl.core.AppInfo
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.EngineState
import com.andrespar2.pardl.ui.theme.AccentBlue
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Warning

@Composable
fun HomeScreen(
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onMusic: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onProviders: () -> Unit,
    onBrowser: () -> Unit,
    onSites: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    val engineState by Engine.state.collectAsState()
    val glass = LocalGlass.current

    LaunchedEffect(Unit) { Engine.ensureStarted(context) }
    val ready = engineState is EngineState.Ready

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Música y multimedia",
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.textPrimary
                )
                Text(
                    "v${AppInfo.VERSION} · Liquid Glass",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
            GlassIconButton(
                Icons.Default.Settings,
                "Ajustes",
                onClick = onSettings
            )
        }

        Spacer(Modifier.height(18.dp))

        GlassCard(strong = true, padding = 20.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ParDLBanner()
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Descarga video, audio y música en alta calidad",
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "FLAC primero · metadatos · descargas en segundo plano",
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            EngineStatus(engineState) { Engine.retry(context) }
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Descargar")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(
                icon = Icons.Default.Movie,
                title = "Video MP4",
                subtitle = "Hasta 4K",
                accent = AccentBlue,
                modifier = Modifier.weight(1f),
                onClick = onVideo
            )
            ActionCard(
                icon = Icons.Default.MusicNote,
                title = "Audio",
                subtitle = "MP3 o formato original",
                accent = AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = onAudio
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(
                icon = Icons.Default.Album,
                title = "Música",
                subtitle = "Buscar y descargar",
                accent = glass.accent,
                highlight = true,
                modifier = Modifier.weight(1f),
                onClick = onMusic
            )
            ActionCard(
                icon = Icons.Default.Download,
                title = "Descargas",
                subtitle = "Cola y progreso",
                accent = Success,
                modifier = Modifier.weight(1f),
                onClick = onDownloads
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Biblioteca")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(
                icon = Icons.Default.History,
                title = "Historial",
                subtitle = "Archivos guardados",
                accent = Warning,
                modifier = Modifier.weight(1f),
                onClick = onHistory
            )
            ActionCard(
                icon = Icons.Default.Cloud,
                title = "Proveedores",
                subtitle = "Estado y orden",
                accent = AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = onProviders
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Más")
        MenuCard(
            title = "Navegador web",
            subtitle = "YouTube, SoundCloud, Bandcamp… y descarga el audio completo",
            accent = true,
            onClick = onBrowser
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = "Sitios compatibles",
            subtitle = "Qué webs puedes usar con el motor yt-dlp",
            accent = false,
            onClick = onSites
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = "Carpeta y ajustes",
            subtitle = "Almacenamiento, calidad, metadatos y apariencia",
            accent = false,
            onClick = onSettings
        )
        Spacer(Modifier.height(8.dp))
        MenuCard(
            title = "Acerca de ParDL",
            subtitle = "Versión, autor, límites legales y licencias",
            accent = false,
            onClick = onAbout
        )

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glassModifier(16.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Verified,
                null,
                tint = Success,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "Usa solo contenido propio o cuya descarga esté autorizada. " +
                    "ParDL no elude DRM ni cuentas de pago.",
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
                lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "ig: ${AppInfo.INSTAGRAM}  ·  GitHub: ${AppInfo.GITHUB}",
            style = MaterialTheme.typography.labelSmall,
            color = glass.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .height(124.dp)
            .glassModifier(22.dp, strong = highlight)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f))
                    )
                )
                .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                maxLines = 1
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = glass.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EngineStatus(state: EngineState, onRetry: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(glass.glassFill)
            .border(0.8.dp, glass.glassBorder, CircleShape)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            is EngineState.Initializing -> {
                CircularProgressIndicator(
                    Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = glass.accent
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Preparando motor yt-dlp…",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
            is EngineState.Ready -> {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(Success)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Motor listo · yt-dlp + FFmpeg",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
            is EngineState.Failed -> {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(Danger)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Refresh,
                    "Reintentar",
                    tint = glass.accent,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRetry)
                )
            }
        }
    }
}
