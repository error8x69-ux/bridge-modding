package com.gtbridge.neonpatcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private enum class Section(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Design("Design", Icons.Default.Edit),
    Cockpit("Cockpit", Icons.Default.Star),
    Apk("APK", Icons.Default.Build)
}

@Composable
internal fun BridgeApp(
    state: PatchState,
    choosePreset: (PalettePreset) -> Unit,
    changeColor: (ColorRole, String) -> Unit,
    actions: ApkActions,
    openCloneUpdates: Boolean,
    consumeCloneUpdateNavigation: () -> Unit
) {
    state.pendingImport?.let {
        ProfileImportDialog(it, state.stage == null, actions.acceptImport, actions.dismissImport)
    }
    if (state.pendingLegacyUpdate != null) {
        AlertDialog(
            onDismissRequest = actions.dismissLegacyUpdate,
            title = { Text("Aktuelle Mod-Auswahl übernehmen?") },
            text = { Text("Diese ältere Kopie enthält kein gespeichertes Mod-Profil. Für das Update kannst du die aktuelle Auswahl aus Bridge Modding übernehmen.") },
            confirmButton = { TextButton(actions.acceptLegacyUpdate, enabled = state.stage == null) {
                Text("Aktuelle Auswahl übernehmen")
            } },
            dismissButton = { TextButton(actions.dismissLegacyUpdate) { Text("Abbrechen") } }
        )
    }
    var section by rememberSaveable { mutableStateOf(Section.Design) }
    LaunchedEffect(openCloneUpdates) {
        if (openCloneUpdates) {
            section = Section.Apk
            consumeCloneUpdateNavigation()
        }
    }
    val screenState = rememberSaveableStateHolder()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        BackHandler(section != Section.Design) { section = Section.Design }
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Section.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = section == destination,
                                onClick = { section = destination },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { insets ->
            Box(
                Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 22.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.patcher_icon), contentDescription = null,
                            modifier = Modifier.size(30.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                    if (wide) {
                        TabRow(selectedTabIndex = section.ordinal) {
                            Section.entries.forEach { destination ->
                                Tab(selected = section == destination, onClick = { section = destination },
                                    text = { Text(destination.label) })
                            }
                        }
                    }
                    screenState.SaveableStateProvider(section.name) { when (section) {
                        Section.Design -> DesignScreen(state, choosePreset, changeColor,
                            { section = Section.Cockpit }, Modifier.fillMaxSize())
                        Section.Cockpit -> CockpitScreen(state, actions.changeDisplay,
                            { section = Section.Apk }, Modifier.fillMaxSize())
                        Section.Apk -> ApkScreen(state, actions, Modifier.fillMaxSize())
                    } }
                }
            }
        }
    }
}
