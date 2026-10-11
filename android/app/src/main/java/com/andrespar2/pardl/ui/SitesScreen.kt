package com.andrespar2.pardl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.Format
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.OutlineSoft
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.TextDim
import com.andrespar2.pardl.ui.theme.TextPrimary
import com.andrespar2.pardl.ui.theme.TextSecondary
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun SitesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var extractors by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize()
    ) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                ParDLTopBar(title = "Sitios compatibles", onBack = onBack)
                Spacer(Modifier.height(18.dp))
                Text(
                    "ParDL usa el motor yt-dlp, compatible con cientos de sitios web.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(18.dp))

                Format.compatibleSiteExamples().forEach { ex ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface1)
                            .border(1.dp, OutlineSoft, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            ex.category,
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentCyan
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ex.sites,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "La compatibilidad puede cambiar con el tiempo. Úsalo solo con contenido " +
                        "propio o cuya descarga esté autorizada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning
                )
                Spacer(Modifier.height(18.dp))

                if (extractors == null) {
                    PrimaryButton(
                        text = if (loading) "Cargando…" else "Ver lista completa",
                        enabled = !loading,
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                Engine.listExtractors(context)
                                    .onSuccess { extractors = it }
                                    .onFailure { error = "No se pudo obtener la lista de sitios." }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "${extractors!!.size} extractores disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Buscar sitio…", color = TextDim) },
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = OutlineSoft,
                            cursorColor = AccentCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Surface1,
                            unfocusedContainerColor = Surface1
                        )
                    )
                }

                if (loading) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentCyan
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Consultando el motor…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Danger)
                }

                Spacer(Modifier.height(14.dp))
            }
        }

        val list = extractors
        if (list != null) {
            val filtered = if (query.isBlank()) list else list.filter {
                it.contains(query, ignoreCase = true)
            }
            items(filtered) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 5.dp)
                )
            }
        }

        item { Spacer(Modifier.height(34.dp)) }
    }
}
