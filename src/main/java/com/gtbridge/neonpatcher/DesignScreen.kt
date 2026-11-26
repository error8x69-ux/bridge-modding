package com.gtbridge.neonpatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DesignScreen(
    state: PatchState,
    choosePreset: (PalettePreset) -> Unit,
    changeColor: (ColorRole, String) -> Unit,
    continueToApk: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var editRole by rememberSaveable { mutableStateOf<ColorRole?>(null) }
    val editable = state.stage == null
    val palette = state.palette
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading("Design", "Gestalte deine Bridge-Kopie.")
        PalettePreview(palette)
        Panel {
            Text("Farbvorlagen", style = MaterialTheme.typography.titleMedium)
            PalettePreset.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { preset ->
                        PresetOption(preset, preset.palette == palette, editable, { choosePreset(preset) },
                            Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Panel {
            Text("Farben", style = MaterialTheme.typography.titleMedium)
            ColorRole.entries.forEachIndexed { index, role ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { editRole = role },
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Swatch(palette.value(role), 22)
                    Spacer(Modifier.width(10.dp))
                    Text(role.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Text(palette.value(role), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.size(16.dp))
                }
            }
        }
        if (continueToApk != null) {
            Button(continueToApk, modifier = Modifier.fillMaxWidth()) {
                Text("Weiter zum Cockpit", Modifier.padding(vertical = 6.dp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
    editRole?.let { role ->
        EditColorDialog(role, palette.value(role), { editRole = null }) { value ->
            changeColor(role, value)
            editRole = null
        }
    }
}

@Composable
private fun PalettePreview(palette: BridgePalette) {
    val activePreset = PalettePreset.entries.firstOrNull { it.palette == palette }
    val background = hexColor(palette.background)
    val text = hexColor(palette.foreground)
    val accent = hexColor(palette.accent)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Vorschau", style = MaterialTheme.typography.labelMedium, color = accent)
                Text(activePreset?.label ?: "Eigene Palette", style = MaterialTheme.typography.titleLarge, color = text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Swatch(palette.background, 10)
                    Swatch(palette.foreground, 10)
                    Swatch(palette.accent, 10)
                }
            }
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(100.dp)) {
                    val gap = 7.dp.toPx()
                    val ring = Size(size.width - 2 * gap, size.height - 2 * gap)
                    val start = Offset(gap, gap)
                    val style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(accent.copy(alpha = 0.2f), 146f, 248f, false, start, ring, style = style)
                    drawArc(accent, 146f, 156f, false, start, ring, style = style)
                }
                Text("Aa", color = text, fontSize = 36.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PresetOption(preset: PalettePreset, selected: Boolean, enabled: Boolean,
                         choose: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = choose,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(preset.label, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ColorRole.entries.forEach { Swatch(preset.palette.value(it), 12) }
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, size: Int) {
    Box(Modifier.size(size.dp).background(hexColor(hex), CircleShape)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape))
}

@Composable
private fun EditColorDialog(role: ColorRole, current: String, close: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable(role) { mutableStateOf(current) }
    val valid = normalizedHex(value)
    AlertDialog(
        onDismissRequest = close,
        title = { Text(role.title) },
        text = {
            OutlinedTextField(
                value, { if (it.length <= 8) value = it },
                label = { Text("Hexwert") },
                leadingIcon = { Swatch(valid ?: current, 18) },
                placeholder = { Text("#RRGGBB") },
                singleLine = true,
                isError = valid == null,
                supportingText = { Text("Sechs Hex-Ziffern, z. B. #FF0046") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { valid?.let(save) })
            )
        },
        confirmButton = { TextButton({ valid?.let(save) }, enabled = valid != null) { Text("Übernehmen") } },
        dismissButton = { TextButton(close) { Text("Abbrechen") } }
    )
}

private fun hexColor(hex: String) = Color(android.graphics.Color.parseColor(hex))
