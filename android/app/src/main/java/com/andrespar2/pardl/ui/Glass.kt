package com.andrespar2.pardl.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.andrespar2.pardl.ui.theme.AccentCyan
import com.andrespar2.pardl.ui.theme.Danger
import com.andrespar2.pardl.ui.theme.LocalGlass
import com.andrespar2.pardl.ui.theme.Success
import com.andrespar2.pardl.ui.theme.Warning
import com.andrespar2.pardl.ui.theme.darken

// ---------------------------------------------------------------------------
// Superficies de cristal
// ---------------------------------------------------------------------------

@Composable
fun Modifier.glassModifier(
    cornerRadius: Dp = 24.dp,
    strong: Boolean = false,
    bordered: Boolean = true,
    shape: Shape = RoundedCornerShape(cornerRadius)
): Modifier {
    val glass = LocalGlass.current
    val fill = if (strong) glass.glassStrong else glass.glassFill
    var result = this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(fill, fill.copy(alpha = fill.alpha * 0.55f))
            )
        )
    if (bordered) {
        result = result.border(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    glass.glassBorder,
                    glass.glassBorder.copy(alpha = glass.glassBorder.alpha * 0.22f)
                )
            ),
            shape
        )
    }
    return result
}

@Composable
fun AmbientBackground(
    artworkUrl: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(glass.backgroundStart, glass.backgroundEnd, glass.backgroundStart)
                )
            )
    ) {
        if (!glass.amoled) {
            Box(
                Modifier
                    .size(430.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                glass.accentSoft.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .size(360.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                glass.accent.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        if (!artworkUrl.isNullOrBlank() && !glass.amoled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.30f)
                    .blur(glass.blur)
                    .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                glass.backgroundStart.copy(alpha = 0.45f),
                                glass.backgroundStart.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var modifierWithClick = modifier
        .fillMaxWidth()
        .glassModifier(24.dp, strong = strong)
    if (onClick != null) modifierWithClick = modifierWithClick.clickable(onClick = onClick)
    Column(
        modifier = modifierWithClick.padding(padding),
        content = content
    )
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onClick = onBack)
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 42.dp,
    onClick: () -> Unit
) {
    val glass = LocalGlass.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(glass.glassFill)
            .border(0.8.dp, glass.glassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint ?: glass.textPrimary, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val glass = LocalGlass.current
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.height(46.dp),
            enabled = enabled,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = glass.accent,
                contentColor = if (glass.isDark) Color(0xFF04121E) else Color.White,
                disabledContainerColor = glass.accent.copy(alpha = 0.32f)
            )
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Row(
            modifier = modifier
                .height(46.dp)
                .glassModifier(23.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, tint = glass.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) glass.textPrimary else glass.textSecondary
            )
        }
    }
}

@Composable
fun CoverArt(
    artworkUrl: String?,
    size: Dp,
    cornerRadius: Dp = 18.dp,
    circular: Boolean = false
) {
    val glass = LocalGlass.current
    val shape = if (circular) CircleShape else RoundedCornerShape(cornerRadius)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(glass.accentSoft.copy(alpha = 0.35f), darken(glass.backgroundEnd, 0.8f))
                )
            )
            .border(0.8.dp, glass.glassBorder.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            null,
            tint = glass.textPrimary.copy(alpha = 0.45f),
            modifier = Modifier.size(size * 0.34f)
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(0.8.dp, color.copy(alpha = 0.32f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val glass = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = glass.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = glass.accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(glass.glassFill)
                .border(0.8.dp, glass.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = glass.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = glass.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = glass.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PillButton(actionLabel, onAction)
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Search,
    enabled: Boolean = true
) {
    val glass = LocalGlass.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, color = glass.textSecondary, style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = leadingIcon?.let {
            { Icon(it, null, tint = glass.textSecondary, modifier = Modifier.size(20.dp)) }
        },
        singleLine = singleLine,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = glass.accent,
            unfocusedBorderColor = glass.glassBorder,
            focusedContainerColor = glass.glassFill,
            unfocusedContainerColor = glass.glassFill,
            disabledContainerColor = glass.glassFill,
            cursorColor = glass.accent,
            focusedTextColor = glass.textPrimary,
            unfocusedTextColor = glass.textPrimary,
            disabledTextColor = glass.textSecondary,
            focusedPlaceholderColor = glass.textSecondary,
            unfocusedPlaceholderColor = glass.textSecondary
        )
    )
}

@Composable
fun GlassSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) glass.textPrimary else glass.textSecondary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = glass.accent,
                uncheckedThumbColor = glass.textSecondary,
                uncheckedTrackColor = glass.glassStrong
            )
        )
    }
}

@Composable
fun GlassProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp
) {
    val glass = LocalGlass.current
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape),
        color = glass.accent,
        trackColor = glass.glassStrong
    )
}

@Composable
fun LoadingRow(text: String) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = glass.accent
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
    }
}

fun statusColor(status: String): Color = when (status) {
    "AVAILABLE", "COMPLETED" -> Success
    "NEEDS_AUTH", "NEEDS_CONFIG", "PAUSED", "INTERRUPTED" -> Warning
    "ERROR", "FAILED", "CANCELLED", "INCOMPATIBLE" -> Danger
    else -> AccentCyan
}

@Composable
fun MetaLine(label: String, value: String?, status: String? = null) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = glass.textSecondary,
            modifier = Modifier.width(118.dp)
        )
        Text(
            value ?: "No disponible",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) glass.textSecondary else glass.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (status != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (status == "Verificado") Success else glass.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
