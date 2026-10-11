package com.andrespar2.pardl.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.AppInfo
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.EngineState
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.Storage
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.OutlineSoft
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.TextPrimary
import com.andrespar2.pardl.ui.theme.TextSecondary
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()
    val engineState by Engine.state.collectAsState()

    var folderLabel by remember { mutableStateOf(prefs.folderName) }
    var version by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateOk by remember { mutableStateOf(true) }

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
    }

    LaunchedEffect(Unit) {
        version = Engine.ytdlpVersion(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ParDLTopBar(title = "Carpeta y ajustes", onBack = onBack)

        Column(Modifier.padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(18.dp))

            SectionLabel("Carpeta de descarga")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, OutlineSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        folderLabel ?: "Sin elegir",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (folderLabel != null) TextPrimary else Warning
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Aquí se guardarán tus videos y audios.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = { folderLauncher.launch(null) }) {
                    Text(if (folderLabel == null) "Elegir" else "Cambiar", color = AccentCyan)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Motor de descargas (yt-dlp)")
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, OutlineSoft, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                InfoRow("Versión incluida", version ?: "—")
                InfoRow(
                    "Estado",
                    when (engineState) {
                        is EngineState.Ready -> "Listo"
                        is EngineState.Initializing -> "Iniciando…"
                        is EngineState.Failed -> "Error"
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Si un sitio deja de funcionar, actualiza el motor para obtener " +
                        "compatibilidad reciente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (updating) "Actualizando…" else "Actualizar yt-dlp",
                    enabled = !updating && engineState is EngineState.Ready,
                    onClick = {
                        updating = true
                        updateMsg = null
                        scope.launch {
                            Engine.updateYtdlp(context)
                                .onSuccess { updateMsg = it; updateOk = true }
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
                if (updateMsg != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        updateMsg!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (updateOk) Success else Danger
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Almacenamiento")
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .border(1.dp, OutlineSoft, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "Las descargas se realizan primero en el almacenamiento privado de la app " +
                        "y luego se copian a la carpeta que elijas. Así funciona de forma segura " +
                        "en Android 10 y versiones posteriores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "ParDL v${AppInfo.VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AccentCyan
    )
}
