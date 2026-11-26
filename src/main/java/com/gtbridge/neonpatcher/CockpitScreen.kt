package com.gtbridge.neonpatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CockpitScreen(
    state: PatchState,
    changeDisplay: (BridgeDisplayOptions) -> Unit,
    continueToApk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsScroll = rememberScrollState()
    val previewScroll = rememberScrollState()
    BoxWithConstraints(modifier.padding(horizontal = 16.dp)) {
        val settings = @Composable {
            CockpitControls(state.display, state.stage == null, changeDisplay, continueToApk)
        }
        if (maxWidth >= 680.dp) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).verticalScroll(previewScroll)) {
                    DashboardPreview(state.palette, state.display)
                }
                Column(Modifier.weight(1f).verticalScroll(settingsScroll)) { settings() }
            }
        } else if (maxHeight >= 590.dp) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardPreview(state.palette, state.display)
                Column(Modifier.weight(1f).verticalScroll(settingsScroll)) { settings() }
            }
        } else {
            Column(Modifier.verticalScroll(settingsScroll).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardPreview(state.palette, state.display)
                settings()
            }
        }
    }
}

@Composable
private fun CockpitControls(
    display: BridgeDisplayOptions,
    enabled: Boolean,
    change: (BridgeDisplayOptions) -> Unit,
    continueToApk: () -> Unit
) {
    Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Text("Reihenfolge", style = MaterialTheme.typography.titleMedium)
            DisplaySwitch("Zusatzwerte frei anordnen", display.customOrder, enabled) {
                change(display.copy(customOrder = it))
            }
            if (display.customOrder) {
                ReadingOrderEditor(display, enabled, change)
            }
        }
        Panel {
            Text("Sichtbare Messwerte", style = MaterialTheme.typography.titleMedium)
            CockpitReading.entries.forEach { reading ->
                val insideDrive = reading.isDriveReading()
                val available = !insideDrive || display.shows(CockpitReading.DRIVE)
                if (reading == CockpitReading.DRIVE || reading == CockpitReading.TRIP) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(Modifier.padding(start = if (insideDrive) 12.dp else 0.dp)) {
                    DisplaySwitch(reading.label, available && display.shows(reading), enabled && available) {
                        change(display.show(reading, it))
                    }
                }
            }
        }
        Panel {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = { change(display.copy(hideStatusBar = true, compactNavigation = true)) },
                enabled = enabled && (!display.hideStatusBar || !display.compactNavigation)
            ) {
                Text("Statusleiste ausblenden und Navigation verkleinern")
            }
            Text("Cockpit-Schrift", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CockpitText.entries.forEach { scale ->
                    FilterChip(selected = scale == display.cockpitText,
                        onClick = { change(display.copy(cockpitText = scale)) },
                        label = { Text("${scale.percent} %") }, enabled = enabled)
                }
            }
            if (!display.customOrder) {
                DisplaySwitch("ESC-Temperatur links vom Motor", display.escFirst,
                    enabled && display.shows(CockpitReading.DRIVE)) {
                    change(display.copy(escFirst = it))
                }
            }
            DisplaySwitch("Android-Statusleiste ausblenden", display.hideStatusBar, enabled) {
                change(display.copy(hideStatusBar = it))
            }
            DisplaySwitch("Untere Navigation auf halbe Höhe", display.compactNavigation, enabled) {
                change(display.copy(compactNavigation = it))
            }
        }
        Panel {
            Text("Bedienung", style = MaterialTheme.typography.titleMedium)
            DisplaySwitch("Popup „Messwerte & Quellen“ beim Tippen ausblenden",
                display.hideReadingsPopup || display.holdSettings, enabled && !display.holdSettings) {
                change(display.copy(hideReadingsPopup = it))
            }
            if (display.hideReadingsPopup || display.holdSettings) {
                DisplaySwitch("3× Tippen aufs Dashboard lädt das gedrosselte Profil",
                    display.restrictedTripleTap, enabled) {
                    change(display.copy(restrictedTripleTap = it))
                }
            }
            DisplaySwitch("Einstellungssuche bei langem Druck aufs Cockpit öffnen", display.holdSettings, enabled) {
                change(display.copy(holdSettings = it))
            }
            if (display.holdSettings) {
                Text("Lange auf die Instrumentenfläche drücken.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(continueToApk, modifier = Modifier.fillMaxWidth()) { Text("Weiter zur APK") }
    }
}

@Composable
private fun ReadingOrderEditor(
    display: BridgeDisplayOptions,
    enabled: Boolean,
    change: (BridgeDisplayOptions) -> Unit
) {
    val visible = display.visibleOrderedReadings
    fun move(from: Int, to: Int) {
        val order = display.readingOrder.toMutableList()
        val fromPosition = order.indexOf(visible[from])
        val toPosition = order.indexOf(visible[to])
        order[fromPosition] = visible[to]
        order[toPosition] = visible[from]
        change(display.copy(readingOrder = order))
    }
    visible.forEachIndexed { index, reading ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(reading.tileLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton({ move(index, index - 1) }, enabled = enabled && index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "${reading.tileLabel} nach oben")
            }
            IconButton({ move(index, index + 1) }, enabled = enabled && index < visible.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "${reading.tileLabel} nach unten")
            }
        }
    }
    if (visible.isEmpty()) {
        Text("Aktiviere die gewünschten Anzeigen unter „Sichtbare Messwerte“.",
            style = MaterialTheme.typography.bodySmall)
    }
    if (display.readingOrder != defaultReadingOrder) {
        TextButton({ change(display.copy(readingOrder = defaultReadingOrder)) }, enabled = enabled) {
            Text("Reihenfolge zurücksetzen")
        }
    }
}

@Composable
private fun DashboardPreview(palette: BridgePalette, display: BridgeDisplayOptions) {
    val background = Color(android.graphics.Color.parseColor(palette.background))
    val text = Color(android.graphics.Color.parseColor(palette.foreground))
    val accent = Color(android.graphics.Color.parseColor(palette.accent))
    val track = lerp(background, accent, 0.30f)
    val scale = display.cockpitText.percent / 100f
    val show = display::shows
    val motor = CockpitReading.MOTOR_TEMP
    val esc = CockpitReading.ESC_TEMP
    val left = if (display.escFirst) esc else motor
    val right = if (display.escFirst) motor else esc
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cockpit-Vorschau", style = MaterialTheme.typography.titleMedium)
            Text("Beispielwerte", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large, color = background, contentColor = text
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (!display.hideStatusBar) {
                    Text("9:41  ·  Android", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        fontSize = 9.sp, color = text)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Akku 82 %", color = text, fontWeight = FontWeight.SemiBold, fontSize = (12 * scale).sp)
                    Box(Modifier.weight(1f).height(3.dp).background(track)) {
                        Spacer(Modifier.fillMaxWidth(0.82f).height(3.dp).background(accent))
                    }
                    Box(Modifier.height(22.dp).widthIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                        if (!display.customOrder && show(CockpitReading.VOLTAGE)) {
                            Text(CockpitReading.VOLTAGE.example, fontSize = (11 * scale).sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxWidth(0.38f).height(60.dp)) {
                        drawArc(track, 184f, 168f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(accent, 184f, 54f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("23", fontWeight = FontWeight.Bold, fontSize = (30 * scale).sp,
                            lineHeight = (32 * scale).sp)
                        Text("km/h", color = accent, fontSize = (10 * scale).sp)
                    }
                }
                if (display.customOrder) {
                    OrderedPreview(display.visibleOrderedReadings, scale, lerp(background, accent, 0.27f), accent)
                } else {
                    PreviewValue("Leistung", "0,83 kW", show(CockpitReading.POWER), scale, Modifier.fillMaxWidth())
                    Box(Modifier.height(21.dp), contentAlignment = Alignment.Center) {
                        if (show(CockpitReading.CURRENT)) {
                            Text("Akkustrom  ·  10 A", fontSize = (11 * scale).sp)
                        }
                    }
                    val drive = show(CockpitReading.DRIVE)
                    Box(Modifier.height(21.dp), contentAlignment = Alignment.Center) {
                        if (drive) Text("ANTRIEB", color = accent, fontSize = (10 * scale).sp)
                    }
                    Box(Modifier.fillMaxWidth(0.60f).height(23.dp), contentAlignment = Alignment.TopCenter) {
                        if (drive && show(CockpitReading.PHASE)) {
                            Text("Phase  ·  14 A", color = text, fontSize = (11 * scale).sp)
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(track)) {
                                Spacer(Modifier.fillMaxWidth(0.30f).height(2.dp).background(accent))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreviewValue(temperatureName(left), temperatureSample(left),
                            drive && show(left), scale, Modifier.weight(1f))
                        PreviewValue(temperatureName(right), temperatureSample(right),
                            drive && show(right), scale, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PreviewValue("Trip", "3,2 km", show(CockpitReading.TRIP), scale, Modifier.weight(1f))
                        PreviewValue("Ø Verbrauch", "24 Wh/km", show(CockpitReading.CONSUMPTION), scale, Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = track)
                Row(Modifier.fillMaxWidth().height(if (display.compactNavigation) 18.dp else 30.dp),
                    horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    listOf("Cockpit", "Fahren", "Akku", "Fahrzeug", "Service").forEachIndexed { index, item ->
                        Text(item, color = if (index == 0) accent else text, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderedPreview(order: List<CockpitReading>, scale: Float, fill: Color, accent: Color) {
    Column(Modifier.fillMaxWidth().height(146.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        order.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { reading ->
                    Column(Modifier.weight(1f).height(34.dp)
                        .background(fill, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center) {
                        Text(reading.tileLabel, fontSize = (9 * scale).sp, lineHeight = (10 * scale).sp)
                        Text(reading.example, fontSize = (12 * scale).sp, lineHeight = (13 * scale).sp,
                            fontWeight = FontWeight.SemiBold)
                        if (reading == CockpitReading.PHASE) {
                            Spacer(Modifier.fillMaxWidth(0.30f).height(2.dp).background(accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewValue(label: String, value: String, visible: Boolean, scale: Float, modifier: Modifier) {
    Box(modifier.height(34.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp)
                Text(value, fontSize = (12 * scale).sp, lineHeight = (14 * scale).sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun temperatureName(reading: CockpitReading) = if (reading == CockpitReading.ESC_TEMP) "ESC" else "Motor"
private fun temperatureSample(reading: CockpitReading) = if (reading == CockpitReading.ESC_TEMP) "31 °C" else "28 °C"
