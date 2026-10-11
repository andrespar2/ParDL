package com.andrespar2.pardl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrespar2.pardl.ui.theme.AccentBlue
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.AccentTeal
import com.andrespar2.pardl.ui.theme.OutlineSoft
import com.andrespar2.pardl.ui.theme.Surface1
import com.andrespar2.pardl.ui.theme.TextPrimary
import com.andrespar2.pardl.ui.theme.TextSecondary

/** Banner "PARDL" en pixel-art con degradado cian. */
@Composable
fun ParDLBanner(modifier: Modifier = Modifier) {
    val grid = remember { Banner.grid("PARDL") }
    val rows = grid.size
    val cols = grid.firstOrNull()?.size ?: 1

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(cols.toFloat() / rows)
    ) {
        val cell = size.height / rows
        val startX = (size.width - cols * cell) / 2f
        val brush = Brush.horizontalGradient(
            colors = listOf(AccentCyan, AccentTeal, AccentBlue, AccentBlue),
            startX = 0f,
            endX = size.width
        )
        grid.forEachIndexed { r, row ->
            row.forEachIndexed { c, on ->
                if (on) {
                    val inset = cell * 0.08f
                    drawRect(
                        brush = brush,
                        topLeft = Offset(startX + c * cell + inset, r * cell + inset),
                        size = Size(cell - 2 * inset, cell - 2 * inset)
                    )
                }
            }
        }
    }
}

/** Regla horizontal sutil con degradado. */
@Composable
fun AccentRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(AccentCyan.copy(alpha = 0.9f), AccentBlue.copy(alpha = 0.15f), AccentCyan.copy(alpha = 0.0f))
                )
            )
    )
}

/** Barra superior con título y botón atrás opcional. */
@Composable
fun ParDLTopBar(title: String, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text(
                    text = "‹",
                    fontSize = 34.sp,
                    color = AccentCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp)
                )
            } else {
                Spacer(Modifier.width(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(12.dp))
        AccentRule()
    }
}

/** Tarjeta de menú pulsable, elegante y minimalista. */
@Composable
fun MenuCard(
    title: String,
    subtitle: String? = null,
    accent: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, OutlineSoft, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) TextPrimary else TextSecondary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        Text(
            text = "›",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = if (enabled && accent) AccentCyan else TextSecondary
        )
    }
}

/** Fila de información etiqueta/valor. */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
