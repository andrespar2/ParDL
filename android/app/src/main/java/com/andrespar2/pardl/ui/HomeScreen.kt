package com.andrespar2.pardl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrespar2.pardl.core.AppInfo
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.EngineState
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onSites: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    val engineState by Engine.state.collectAsState()

    LaunchedEffect(Unit) { Engine.ensureStarted(context) }

    val ready = engineState is EngineState.Ready

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(30.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ParDLBanner()
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = "ig: ${AppInfo.INSTAGRAM}  |  GitHub: ${AppInfo.GITHUB}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "ParDL v${AppInfo.VERSION}  ·  Descargador multimedia",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        AccentRule()
        Spacer(Modifier.height(20.dp))

        EngineBadge(engineState) { Engine.retry(context) }

        Spacer(Modifier.height(14.dp))

        MenuCard(
            title = "Descargar video (MP4)",
            subtitle = "Elige la resolución disponible en el enlace",
            enabled = ready,
            onClick = onVideo
        )
        Spacer(Modifier.height(10.dp))
        MenuCard(
            title = "Descargar audio (MP3)",
            subtitle = "Extrae el audio con la calidad que prefieras",
            enabled = ready,
            onClick = onAudio
        )
        Spacer(Modifier.height(10.dp))
        MenuCard(
            title = "Sitios compatibles",
            subtitle = "Consulta qué webs puedes usar",
            accent = false,
            onClick = onSites
        )
        Spacer(Modifier.height(10.dp))
        MenuCard(
            title = "Carpeta y ajustes",
            subtitle = "Elige dónde guardar tus descargas",
            accent = false,
            onClick = onSettings
        )
        Spacer(Modifier.height(10.dp))
        MenuCard(
            title = "Acerca de ParDL",
            subtitle = "Versión, autor y licencias",
            accent = false,
            onClick = onAbout
        )

        Spacer(Modifier.height(26.dp))
        Text(
            text = "Compatible con cientos de sitios web mediante yt-dlp.\n" +
                "Úsalo solo con contenido propio o cuya descarga esté autorizada.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextDim,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(34.dp))
    }
}

@Composable
private fun EngineBadge(state: EngineState, onRetry: () -> Unit) {
    when (state) {
        is EngineState.Initializing -> Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = AccentCyan
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Preparando el motor de descargas…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        is EngineState.Ready -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", color = Success, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.size(8.dp))
            Text(
                "Motor listo",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        is EngineState.Failed -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1)
                .padding(14.dp)
        ) {
            Text(
                "No se pudo iniciar el motor",
                style = MaterialTheme.typography.titleMedium,
                color = Danger
            )
            Spacer(Modifier.height(4.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            TextButton(onClick = onRetry) {
                Text("Reintentar", color = AccentCyan)
            }
        }
    }
}
