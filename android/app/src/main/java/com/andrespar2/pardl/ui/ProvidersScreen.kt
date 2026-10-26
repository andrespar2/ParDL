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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andrespar2.pardl.core.Prefs
import com.andrespar2.pardl.core.music.MusicProviders
import com.andrespar2.pardl.core.music.MusicRepository
import com.andrespar2.pardl.core.music.ProviderInfo
import com.andrespar2.pardl.core.music.ProviderStatus
import com.andrespar2.pardl.core.music.ProviderTestResult
import com.andrespar2.pardl.core.music.RegistryAudit
import com.andrespar2.pardl.ui.theme.AccentBlue
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun ProvidersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass = LocalGlass.current
    val prefs = remember { Prefs.get(context) }
    var refresh by remember { mutableStateOf(0) }
    var preferred by remember { mutableStateOf(prefs.musicPreferredProvider) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Map<String, ProviderTestResult>>(emptyMap()) }
    var showAudit by remember { mutableStateOf(false) }

    val providers = remember(refresh, preferred) { MusicRepository.providers(context) }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(
            title = "Proveedores",
            subtitle = "Estado real · sin simulaciones",
            onBack = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            GlassCard(strong = true, padding = 14.dp) {
                Text(
                    "Proveedor preferido",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary
                )
                Text(
                    "Se intenta primero al buscar, si está disponible. Cambiarlo no " +
                        "convierte en funcional una integración que no lo sea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("tidal", "internet-archive", "deezer", "apple-music").forEach { id ->
                        val name = MusicProviders.byId(id)?.info?.name ?: id
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (preferred == id) glass.accent else glass.glassFill
                                )
                                .border(
                                    0.8.dp,
                                    if (preferred == id) glass.accent else glass.glassBorder,
                                    CircleShape
                                )
                                .clickable {
                                    preferred = id
                                    prefs.musicPreferredProvider = id
                                    refresh++
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (preferred == id) Color(0xFF04121E) else glass.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (preferred == "tidal") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "TIDAL aparece como preferencia inicial, pero no tiene conexión " +
                            "funcional para descargar. ParDL continuará con proveedores autorizados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Integraciones de ParDL")
            providers.forEachIndexed { index, provider ->
                ProviderCard(
                    provider = provider,
                    preferred = provider.id == preferred,
                    testResult = results[provider.id],
                    testing = testingId == provider.id,
                    canMoveUp = index > 0,
                    canMoveDown = index < providers.lastIndex,
                    onTest = {
                        testingId = provider.id
                        scope.launch {
                            val result = MusicRepository.testProvider(context, provider.id)
                            results = results + (provider.id to result)
                            testingId = null
                            refresh++
                        }
                    },
                    onPrefer = {
                        preferred = provider.id
                        prefs.musicPreferredProvider = provider.id
                        refresh++
                    },
                    onToggle = { enabled ->
                        prefs.musicDisabledProviders = if (enabled) {
                            prefs.musicDisabledProviders - provider.id
                        } else {
                            prefs.musicDisabledProviders + provider.id
                        }
                        refresh++
                    },
                    onMove = { direction ->
                        val order = prefs.providerOrder().toMutableList()
                        val from = order.indexOf(provider.id)
                        val to = from + direction
                        if (from >= 0 && to in order.indices) {
                            val item = order.removeAt(from)
                            order.add(to, item)
                            prefs.musicProviderOrder = order.joinToString(",")
                            refresh++
                        }
                    }
                )
                Spacer(Modifier.height(9.dp))
            }

            Spacer(Modifier.height(8.dp))
            GlassCard(
                padding = 14.dp,
                onClick = { showAudit = !showAudit }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Verified,
                        null,
                        tint = AccentCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Auditoría de registry.json",
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.textPrimary
                        )
                        Text(
                            "12 paquetes .sflx · hashes SHA-256 verificados · código no ejecutado",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textSecondary
                        )
                    }
                    Text(
                        if (showAudit) "Ocultar" else "Ver",
                        style = MaterialTheme.typography.labelMedium,
                        color = glass.accent
                    )
                }
            }

            if (showAudit) {
                Spacer(Modifier.height(9.dp))
                RegistryAudit.entries.forEach { entry ->
                    AuditCard(entry)
                    Spacer(Modifier.height(7.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(padding = 14.dp) {
                Text(
                    "Seguridad",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.textPrimary
                )
                Text(
                    "ParDL no ejecuta JavaScript remoto, no guarda tokens y no elude DRM, " +
                        "cuentas ni controles de acceso. Las descargas usan HTTPS y validan " +
                        "el formato real del archivo antes de guardarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderInfo,
    preferred: Boolean,
    testResult: ProviderTestResult?,
    testing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTest: () -> Unit,
    onPrefer: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit
) {
    val glass = LocalGlass.current
    val enabled = provider.status != ProviderStatus.DISABLED
    val color = when (provider.status) {
        ProviderStatus.AVAILABLE -> Success
        ProviderStatus.NEEDS_AUTH, ProviderStatus.NEEDS_CONFIG,
        ProviderStatus.TEMPORARILY_UNAVAILABLE -> Warning
        ProviderStatus.ERROR, ProviderStatus.INCOMPATIBLE -> Danger
        ProviderStatus.DISABLED -> glass.textSecondary
    }
    GlassCard(padding = 14.dp, strong = preferred) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                provider.name,
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (preferred) {
                Icon(Icons.Default.Star, "Preferido", tint = glass.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            StatusChip(provider.status.label, color)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            provider.description,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            provider.statusDetail,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        if (provider.kinds.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                provider.kinds.joinToString(" · ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary
            )
        }
        if (provider.formats.isNotEmpty()) {
            Text(
                "Formatos: ${provider.formats.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = glass.textSecondary
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            provider.legalNote,
            style = MaterialTheme.typography.labelSmall,
            color = glass.textSecondary
        )

        if (testResult != null) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (testResult.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    null,
                    tint = if (testResult.ok) Success else Danger,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    testResult.message + (testResult.latencyMs?.let { " · ${it} ms" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (testResult.ok) Success else Danger
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmallProviderAction(
                text = if (testing) "Probando…" else "Probar",
                icon = Icons.Default.PlayArrow,
                enabled = !testing,
                onClick = onTest
            )
            if (provider.canDownload) {
                SmallProviderAction(
                    text = if (preferred) "Preferido" else "Preferir",
                    icon = if (preferred) Icons.Default.Star else Icons.Default.StarBorder,
                    enabled = !preferred,
                    onClick = onPrefer
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowUpward,
                "Subir",
                tint = if (canMoveUp) glass.textSecondary else glass.glassBorder,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canMoveUp) { onMove(-1) }
            )
            Icon(
                Icons.Default.ArrowDownward,
                "Bajar",
                tint = if (canMoveDown) glass.textSecondary else glass.glassBorder,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canMoveDown) { onMove(1) }
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = glass.accent,
                    uncheckedThumbColor = glass.textSecondary,
                    uncheckedTrackColor = glass.glassStrong
                )
            )
        }
    }
}

@Composable
private fun SmallProviderAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .glassModifier(14.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) glass.accent else glass.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) glass.textPrimary else glass.textSecondary
        )
    }
}

@Composable
private fun AuditCard(entry: RegistryAudit.Entry) {
    val glass = LocalGlass.current
    val color = when (entry.status) {
        ProviderStatus.AVAILABLE -> Success
        ProviderStatus.NEEDS_AUTH, ProviderStatus.NEEDS_CONFIG -> Warning
        else -> Danger
    }
    GlassCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (entry.hashVerified) Icons.Default.Verified else Icons.Default.ErrorOutline,
                null,
                tint = if (entry.hashVerified) Success else Danger,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${entry.name} ${entry.version}",
                style = MaterialTheme.typography.titleMedium,
                color = glass.textPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusChip(entry.status.label, color)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${entry.category} · SHA-256 ${if (entry.hashVerified) "verificado" else "no coincide"} · ${entry.sha256.take(12)}…",
            style = MaterialTheme.typography.labelSmall,
            color = glass.textSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.decision,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
        Text(
            entry.reason,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary
        )
    }
}
