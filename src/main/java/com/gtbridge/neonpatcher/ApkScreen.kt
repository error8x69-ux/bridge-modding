package com.gtbridge.neonpatcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class ApkActions(
    val chooseInstalled: () -> Unit,
    val chooseDocument: () -> Unit,
    val changeDisplay: (BridgeDisplayOptions) -> Unit,
    val changeOnline: (OnlineOption, Boolean) -> Unit,
    val blockAnalyticsAndRanking: () -> Unit,
    val patch: (Boolean) -> Unit,
    val install: () -> Unit,
    val checkForUpdate: () -> Unit,
    val downloadUpdate: () -> Unit,
    val installUpdate: () -> Unit,
    val open: () -> Unit,
    val export: () -> Unit,
    val importProfile: () -> Unit,
    val exportProfile: () -> Unit,
    val acceptImport: () -> Unit,
    val dismissImport: () -> Unit
)

@Composable
internal fun ApkScreen(state: PatchState, actions: ApkActions, modifier: Modifier = Modifier) {
    val idle = state.stage == null
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeading("APK erstellen", "Wähle deine Original-Bridge als Quelle.")
        Panel {
            Text("Bridge Modding", style = MaterialTheme.typography.titleMedium)
            VersionRow("Installierte Version", "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}")
            state.update?.let { release -> VersionRow("Update verfügbar", release.version) }
            if (state.updateChecked && state.update == null) {
                Text("Du hast die aktuelle Version.", style = MaterialTheme.typography.bodySmall)
            }
            when {
                state.preparedUpdate != null -> FilledTonalButton(actions.installUpdate, enabled = idle,
                    modifier = Modifier.fillMaxWidth()) { Text("Bridge Modding aktualisieren") }
                state.update != null -> FilledTonalButton(actions.downloadUpdate, enabled = idle,
                    modifier = Modifier.fillMaxWidth()) { Text("Update herunterladen") }
                else -> OutlinedButton(actions.checkForUpdate, enabled = idle,
                    modifier = Modifier.fillMaxWidth()) { Text("Auf GitHub nach Updates suchen") }
            }
            state.stage?.takeIf { it == PatchStage.CheckUpdate || it == PatchStage.DownloadUpdate }?.let {
                Text(it.title, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.updateMessage?.let { StatusMessage(it) }
        }
        Panel {
            Text("Original", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(state.source == BridgeSource.Installed, actions.chooseInstalled, enabled = idle)
                Column(Modifier.weight(1f)) {
                    Text("Installierte GT VESC Bridge", style = MaterialTheme.typography.bodyLarge)
                    Text(state.original?.label ?: "Auf diesem Gerät nicht gefunden",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val document = state.source as? BridgeSource.Document
            if (document != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(true, onClick = null)
                    Text(document.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(actions.chooseDocument, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text(if (document == null) "Original-APK auswählen" else "Andere APK auswählen")
            }
        }
        if (state.originalNewer) {
            FilledTonalButton({ actions.patch(true) }, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text("Neueres Original mit diesen Einstellungen patchen")
            }
        }
        Panel {
            Text("Online-Funktionen in der Kopie", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = actions.blockAnalyticsAndRanking,
                enabled = idle && !state.blocked.containsAll(privateOnlineOptions)) {
                Text("Analyseversand und GPS-Uploads blockieren")
            }
            OnlineOption.entries.forEach { option ->
                DisplaySwitch(option.label, option in state.blocked, idle) { actions.changeOnline(option, it) }
            }
        }
        Panel {
            Text("Patch-Einstellungen sichern", style = MaterialTheme.typography.titleMedium)
            Text("Farben, Cockpit und Online-Optionen als JSON-Datei.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(actions.importProfile, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text("Einstellungen aus Datei laden")
            }
            OutlinedButton(actions.exportProfile, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                Text("Einstellungen als Datei sichern")
            }
        }
        Panel {
            Text("Für diese APK", style = MaterialTheme.typography.titleMedium)
            ProfileOverview(state.profile, compact = true)
        }
        Button({ actions.patch(false) }, enabled = idle && state.sourceAvailable, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Build, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("APK erstellen", Modifier.padding(vertical = 6.dp))
        }
        state.stage?.takeUnless { it == PatchStage.CheckUpdate || it == PatchStage.DownloadUpdate }?.let { stage ->
            Panel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stage.title, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        state.message?.let { StatusMessage(it) }
        if (state.output != null || state.installed != null) {
            Panel {
                Text(BridgeNeonBuild.CLONE_LABEL, style = MaterialTheme.typography.titleMedium)
                state.output?.let {
                    VersionRow("APK", it.label)
                }
                state.report?.let { report ->
                    report.changes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (report.colorInstructions > 0) {
                        VersionRow("Geänderte Farbwerte im App-Code", report.colorInstructions.toString())
                    }
                }
                state.installed?.let {
                    VersionRow("Installiert", it.label)
                }
                if (state.installed != null) {
                    FilledTonalButton(actions.open, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                        Text("${BridgeNeonBuild.CLONE_LABEL} öffnen")
                    }
                }
                if (state.output != null) {
                    OutlinedButton(actions.install, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                        Text("Kopie installieren")
                    }
                    OutlinedButton(actions.export, enabled = idle, modifier = Modifier.fillMaxWidth()) {
                        Text("APK speichern")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileImportDialog(profile: BridgeProfile, enabled: Boolean, accept: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Patch-Einstellungen übernehmen?") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Diese Datei ersetzt deine aktuellen Patch-Einstellungen.")
                ProfileOverview(profile, compact = false)
            }
        },
        confirmButton = { TextButton(accept, enabled = enabled) { Text("Übernehmen") } },
        dismissButton = { TextButton(dismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ProfileOverview(profile: BridgeProfile, compact: Boolean) {
    val cockpit = profile.cockpitSummary
    val online = profile.blocked.map { it.label }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionRow("Farben", profile.paletteLabel)
            VersionRow("Cockpit", if (cockpit.isEmpty()) "Original" else "${cockpit.size} Anpassungen")
            VersionRow("Online", if (online.isEmpty()) "Original" else "${online.size} Sperren")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewGroup("Farben", listOf(profile.paletteLabel))
            OverviewGroup("Cockpit", cockpit.ifEmpty { listOf("Original") })
            OverviewGroup("Online", online.ifEmpty { listOf("Original") })
        }
    }
}

@Composable
private fun OverviewGroup(label: String, entries: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        entries.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun DisplaySwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun VersionRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusMessage(message: PatchMessage) {
    val colors = MaterialTheme.colorScheme
    val error = message.type == MessageType.Error
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (error) colors.errorContainer else colors.primaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onPrimaryContainer
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp)) {
                Icon(if (message.type == MessageType.Success) Icons.Default.Check else Icons.Default.Info,
                    contentDescription = null)
            }
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
