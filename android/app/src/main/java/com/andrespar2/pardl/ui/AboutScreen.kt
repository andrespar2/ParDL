package com.andrespar2.pardl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.AppInfo
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.OutlineSoft
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.TextPrimary
import com.andrespar2.pardl.ui.theme.TextSecondary

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ParDLTopBar(title = "Acerca de ParDL", onBack = onBack)

        Column(Modifier.padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(20.dp))

            Text(
                "ParDL v${AppInfo.VERSION}",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Descargador multimedia libre y de código abierto.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(22.dp))
            Card {
                Text("Desarrollado por ${AppInfo.AUTHOR}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                InfoRow("Instagram", "@${AppInfo.INSTAGRAM}")
                InfoRow("GitHub", AppInfo.GITHUB)
            }

            Spacer(Modifier.height(18.dp))
            Card {
                Text("Motor de descargas", color = AccentCyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(AppInfo.YTDLP_URL, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Text("Procesamiento de audio y vídeo", color = AccentCyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(AppInfo.FFMPEG_URL, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }

            Spacer(Modifier.height(18.dp))
            Card {
                Text("Licencia", color = AccentCyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "El código propio de ParDL se distribuye bajo licencia MIT. " +
                        "yt-dlp y FFmpeg conservan sus propias licencias.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(18.dp))
            Card {
                Text("Uso responsable", color = AccentCyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Usa ParDL únicamente con contenido propio o cuya descarga esté " +
                        "autorizada. Respeta los derechos de autor y las condiciones de cada sitio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, OutlineSoft, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        content()
    }
}
