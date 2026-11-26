package com.gtbridge.neonpatcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class PatcherActivity : ComponentActivity() {
    private val model: PatchViewModel by viewModels()
    private var updating = false
    private val chooseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::selectDocument)
    }
    private val saveFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BridgeRepository.APK)
    ) { uri ->
        uri?.let(model::export)
    }
    private val openProfile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importProfile)
    }
    private val saveProfile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(model::exportProfile)
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) {
            installApk(updating)
        } else {
            model.notify("Erlaube die Installation für ${getString(R.string.app_name)}.", MessageType.Error, updating)
        }
    }
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val label = if (updating) getString(R.string.app_name) else BridgeNeonBuild.CLONE_LABEL
            model.notify("$label wurde installiert.", MessageType.Success, updating)
        } else {
            model.notify("Android hat die Installation nicht bestätigt.", update = updating)
        }
        model.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updating = savedInstanceState?.getBoolean("updating", false) ?: false
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.BLACK),
            SystemBarStyle.dark(android.graphics.Color.BLACK))
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            BridgeTheme {
                BridgeApp(state, model::choosePreset, model::changeColor,
                    ApkActions(
                        chooseInstalled = model::selectInstalled,
                        chooseDocument = { chooseFile.launch(arrayOf("*/*")) },
                        changeDisplay = model::changeDisplay,
                        changeOnline = model::changeOnline,
                        blockAnalyticsAndRanking = model::blockAnalyticsAndRanking,
                        patch = model::patch,
                        install = { installApk(false) },
                        checkForUpdate = model::checkForUpdate,
                        downloadUpdate = model::downloadUpdate,
                        installUpdate = { installApk(true) },
                        open = ::openClone,
                        export = { saveFile.launch(BridgeRepository.CLONE_FILE) },
                        importProfile = { openProfile.launch(arrayOf("*/*")) },
                        exportProfile = { saveProfile.launch("Bridge-Modding-Einstellungen.json") },
                        acceptImport = model::acceptImport,
                        dismissImport = model::dismissImport
                    ))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("updating", updating)
        super.onSaveInstanceState(outState)
    }

    private fun installApk(update: Boolean) {
        updating = update
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                model.notify("Erlaube die Installation in den Android-Einstellungen.", update = update)
                permissions.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
            } else {
                if (!update) model.notify("Bestätige die Installation von ${BridgeNeonBuild.CLONE_LABEL}.")
                installer.launch(model.repository.installIntent(update))
            }
        } catch (_: ActivityNotFoundException) {
            model.notify("Android konnte den Installer nicht öffnen. Speichere die APK und öffne sie in Dateien.",
                MessageType.Error, update)
        } catch (_: SecurityException) {
            model.notify("Android sperrt die Installation. Speichere die APK und öffne sie in Dateien.",
                MessageType.Error, update)
        }
    }

    private fun openClone() {
        val launch = packageManager.getLaunchIntentForPackage(BridgeRepository.CLONE)
        if (launch != null) startActivity(launch) else model.refresh()
    }
}
