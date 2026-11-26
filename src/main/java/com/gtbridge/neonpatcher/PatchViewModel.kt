package com.gtbridge.neonpatcher

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.gtbridge.OriginalBridgeUpdates
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ColorRole(val title: String) {
    Background("Hintergrund"),
    Foreground("Schrift und Symbole"),
    Accent("Akzent")
}

internal data class BridgePalette(
    val background: String,
    val foreground: String,
    val accent: String,
    val original: Boolean = false
) {
    fun value(role: ColorRole) = when (role) {
        ColorRole.Background -> background
        ColorRole.Foreground -> foreground
        ColorRole.Accent -> accent
    }

    fun with(role: ColorRole, hex: String) = when (role) {
        ColorRole.Background -> copy(background = hex, original = false)
        ColorRole.Foreground -> copy(foreground = hex, original = false)
        ColorRole.Accent -> copy(accent = hex, original = false)
    }

    val colors: IntArray?
        get() = if (original) null else ColorRole.entries.map { Color.parseColor(value(it)) }.toIntArray()
}

internal enum class PalettePreset(val label: String, val palette: BridgePalette) {
    Standard("Standard", BridgePalette("#0B1218", "#EDF2F6", "#EE405E", original = true)),
    OledPink("OLED Pink", BridgePalette("#000000", "#FF0046", "#FF0046")),
    OledGt2("OLED GT2", BridgePalette("#000000", "#39FF14", "#39FF14")),
    Ice("Eisblau", BridgePalette("#08111C", "#E3F7FF", "#55C8FF")),
    Violet("Violett", BridgePalette("#140D1D", "#E7C8FF", "#FF91E0")),
    Amber("Amber", BridgePalette("#171109", "#FFF1D4", "#FFAD45"))
}

internal fun normalizedHex(input: String): String? =
    input.trim().removePrefix("#").uppercase(Locale.ROOT)
        .takeIf { it.matches(Regex("[0-9A-F]{6}")) }?.let { "#$it" }

internal enum class MessageType { Info, Success, Error }
internal data class PatchMessage(val text: String, val type: MessageType)

internal data class CloneUpdateRequest(val source: BridgeSource, val profile: BridgeProfile)

internal data class PatchState(
    val palette: BridgePalette,
    val display: BridgeDisplayOptions = BridgeDisplayOptions(),
    val blocked: Set<OnlineOption> = emptySet(),
    val source: BridgeSource = BridgeSource.Installed,
    val original: BridgeVersion? = null,
    val installed: BridgeVersion? = null,
    val output: BridgeVersion? = null,
    val report: PatchReport? = null,
    val pendingImport: BridgeProfile? = null,
    val pendingLegacyUpdate: CloneUpdateRequest? = null,
    val installCloneRequest: Boolean = false,
    val cloneUpdate: OriginalBridgeUpdates.Release? = null,
    val cloneUpdateChecked: Boolean = false,
    val cloneUpdateMessage: PatchMessage? = null,
    val update: BridgeRelease? = null,
    val preparedUpdate: BridgeVersion? = null,
    val updateChecked: Boolean = false,
    val stage: PatchStage? = null,
    val message: PatchMessage? = null,
    val updateMessage: PatchMessage? = null
) {
    val sourceAvailable: Boolean get() = source is BridgeSource.Document || original != null
    val originalNewer: Boolean get() = original != null && installed != null && original.code > installed.code
    val profile: BridgeProfile get() = BridgeProfile(palette, display, blocked)
}

internal class PatchViewModel(application: Application) : AndroidViewModel(application) {
    val repository = BridgeRepository(application)
    private val updates = BridgeUpdates(application)
    private val preferences = application.getSharedPreferences("palette", Application.MODE_PRIVATE)
    private val model = MutableStateFlow(PatchState(loadPalette(), BridgeDisplayOptions(
        hideStatusBar = preferences.getBoolean("hideStatusBar", false),
        compactNavigation = preferences.getBoolean("compactNavigation", false),
        hideReadingsPopup = preferences.getBoolean("hideReadingsPopup", false),
        cockpitText = CockpitText.entries.firstOrNull { it.name == preferences.getString("cockpitText", null) }
            ?: CockpitText.Standard,
        hiddenReadings = preferences.getInt("hiddenReadings",
            if (preferences.getBoolean("hideMotorReadings", false)) CockpitReading.DRIVE.bit else 0),
        escFirst = preferences.getBoolean("escFirst", false),
        holdSettings = preferences.getBoolean("holdSettings", false),
        customOrder = preferences.getBoolean("customOrder", false),
        readingOrder = preferences.getString("readingOrder", null)?.let { encoded ->
            runCatching { parseReadingOrder(encoded) }.getOrNull()
        } ?: defaultReadingOrder,
        restrictedTripleTap = preferences.getBoolean("restrictedTripleTap", false),
        keepPageOnResize = preferences.getBoolean("keepCockpitOnResize", false),
        hideCockpitTileBackground = preferences.getBoolean("hideCockpitTileBackground", false),
        cockpitTitle = preferences.getString("cockpitTitle", null)?.takeIf(::validCockpitTitle).orEmpty()
    ), OnlineOption.entries.filterTo(mutableSetOf()) { preferences.getBoolean("block${it.name}", false) }))
    val state = model.asStateFlow()

    fun checkForUpdate() {
        launchWork(PatchStage.CheckUpdate) {
            val release = withContext(Dispatchers.IO) { updates.latest() }
            model.update { state ->
                state.copy(update = release, updateChecked = true,
                    preparedUpdate = state.preparedUpdate?.takeIf { it.name == release?.version })
            }
        }
    }

    fun downloadUpdate() {
        val release = model.value.update ?: return
        launchWork(PatchStage.DownloadUpdate) {
            val version = withContext(Dispatchers.IO) { updates.download(release) }
            model.update { it.copy(preparedUpdate = version) }
        }
    }

    fun checkForCloneUpdate() {
        launchWork(PatchStage.CheckOriginalUpdate, cloneUpdate = true) {
            val (installed, release) = withContext(Dispatchers.IO) {
                repository.installedVersion(BridgeRepository.CLONE) to OriginalBridgeUpdates.latest()
            }
            model.update { state ->
                state.copy(installed = installed, cloneUpdateChecked = true,
                    cloneUpdate = release?.takeIf { installed != null && it.code > installed.code })
            }
        }
    }

    fun installPublishedCloneUpdate() {
        val snapshot = model.value
        val release = snapshot.cloneUpdate ?: return
        buildCloneUpdate(CloneUpdateRequest(BridgeSource.Published(release), snapshot.profile), false)
    }

    fun choosePreset(preset: PalettePreset) = changePalette(preset.palette)

    fun changeColor(role: ColorRole, hex: String) =
        changePalette(model.value.palette.with(role, hex))

    fun changeDisplay(options: BridgeDisplayOptions) = applyProfile(model.value.profile.copy(display = options))

    fun changeOnline(option: OnlineOption, blocked: Boolean) {
        val profile = model.value.profile
        applyProfile(profile.copy(blocked = if (blocked) profile.blocked + option else profile.blocked - option))
    }

    fun blockAnalyticsAndRanking() {
        val profile = model.value.profile
        applyProfile(profile.copy(blocked = profile.blocked + privateOnlineOptions))
    }

    private fun changePalette(palette: BridgePalette) = applyProfile(model.value.profile.copy(palette = palette))

    private fun applyProfile(profile: BridgeProfile) {
        if (model.value.stage != null) return
        val (palette, display, blocked) = profile
        preferences.edit().apply {
            ColorRole.entries.forEachIndexed { index, role -> putString("color$index", palette.value(role)) }
            putBoolean("originalColors", palette.original)
            putBoolean("hideStatusBar", display.hideStatusBar)
            putBoolean("compactNavigation", display.compactNavigation)
            putBoolean("hideReadingsPopup", display.hideReadingsPopup)
            putString("cockpitText", display.cockpitText.name)
            putInt("hiddenReadings", display.hiddenReadings)
            putBoolean("hideMotorReadings", !display.shows(CockpitReading.DRIVE))
            putBoolean("escFirst", display.escFirst)
            putBoolean("holdSettings", display.holdSettings)
            putBoolean("customOrder", display.customOrder)
            putBoolean("hideCockpitTileBackground", display.hideCockpitTileBackground)
            putString("cockpitTitle", display.cockpitTitle)
            putString("readingOrder", display.readingOrder.joinToString(",") { it.name })
            putBoolean("restrictedTripleTap", display.restrictedTripleTap)
            putBoolean("keepCockpitOnResize", display.keepPageOnResize)
            OnlineOption.entries.forEach { putBoolean("block${it.name}", it in blocked) }
        }.apply()
        model.update { it.copy(palette = palette, display = display, blocked = blocked) }
    }

    private fun loadPalette(): BridgePalette {
        if (preferences.getBoolean("originalColors", false)) return PalettePreset.Standard.palette
        val saved = ColorRole.entries.fold(PalettePreset.OledPink.palette) { palette, role ->
            val oldValue = preferences.getString("color${role.ordinal}", null)
            oldValue?.let(::normalizedHex)?.let { palette.with(role, it) } ?: palette
        }
        val gt2 = PalettePreset.OledGt2.palette
        val pink = PalettePreset.OledPink.palette
        if (saved == pink.with(ColorRole.Accent, "#00FFFF")) {
            preferences.edit().putString("color${ColorRole.Accent.ordinal}", pink.accent).apply()
            return pink
        }
        if (saved == gt2.with(ColorRole.Accent, "#FF0046")) {
            preferences.edit().putString("color${ColorRole.Accent.ordinal}", gt2.accent).apply()
            return gt2
        }
        return saved
    }

    fun refresh() {
        viewModelScope.launch {
            val (original, installed, output) = withContext(Dispatchers.IO) {
                Triple(repository.installedVersion(BridgeNeonBuild.ORIGINAL),
                    repository.installedVersion(BridgeRepository.CLONE), repository.readyVersion())
            }
            model.update {
                it.copy(original = original, installed = installed,
                    cloneUpdate = it.cloneUpdate?.takeIf { release -> installed != null && release.code > installed.code },
                    output = if (it.stage == null) output else it.output)
            }
        }
    }

    fun selectInstalled() {
        model.update { if (it.stage == null) it.copy(source = BridgeSource.Installed) else it }
    }

    fun selectDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) { repository.document(uri) }
                model.update { if (it.stage == null) it.copy(source = document) else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                report(failure)
            }
        }
    }

    fun patch() {
        val snapshot = model.value
        if (snapshot.stage != null || !snapshot.sourceAvailable) return
        launchWork(PatchStage.Validate) {
            val report = withContext(Dispatchers.IO) {
                repository.patch(snapshot.source, snapshot.profile) { stage ->
                    model.update { it.copy(stage = stage) }
                }
            }
            model.update { it.copy(output = report.version, report = report,
                message = PatchMessage("APK erstellt und signiert.", MessageType.Success)) }
        }
    }

    fun updateClone() {
        val snapshot = model.value
        if (snapshot.installed == null || !snapshot.sourceAvailable) return
        buildCloneUpdate(CloneUpdateRequest(snapshot.source, snapshot.profile), false)
    }

    fun acceptLegacyUpdate() {
        val request = model.value.pendingLegacyUpdate ?: return
        dismissLegacyUpdate()
        buildCloneUpdate(request, true)
    }

    fun dismissLegacyUpdate() = model.update { it.copy(pendingLegacyUpdate = null) }

    fun consumeCloneInstallRequest() = model.update { it.copy(installCloneRequest = false) }

    private fun buildCloneUpdate(request: CloneUpdateRequest, useCurrentProfile: Boolean) {
        launchWork(PatchStage.Validate, cloneUpdate = request.source is BridgeSource.Published) {
            val report = try {
                withContext(Dispatchers.IO) {
                    repository.updateClone(request.source, request.profile.takeIf { useCurrentProfile }) { stage ->
                        model.update { it.copy(stage = stage) }
                    }
                }
            } catch (_: MissingAppliedProfile) {
                model.update { it.copy(pendingLegacyUpdate = request) }
                return@launchWork
            }
            model.update { it.copy(output = report.version, report = report, installCloneRequest = true) }
        }
    }

    fun importProfile(uri: Uri) {
        if (model.value.stage != null) return
        launchWork(PatchStage.ImportProfile) {
            val profile = withContext(Dispatchers.IO) { repository.readProfile(uri) }
            model.update { it.copy(pendingImport = profile) }
        }
    }

    fun acceptImport() {
        if (model.value.stage != null) return
        val profile = model.value.pendingImport ?: return
        applyProfile(profile)
        dismissImport()
        notify("Patch-Einstellungen geladen.", MessageType.Success)
    }

    fun dismissImport() = model.update { it.copy(pendingImport = null) }

    fun exportProfile(uri: Uri) {
        val profile = model.value.profile
        launchWork(PatchStage.ExportProfile) {
            withContext(Dispatchers.IO) { repository.writeProfile(uri, profile) }
            notify("Patch-Einstellungen gespeichert.", MessageType.Success)
        }
    }

    fun export(uri: Uri) {
        if (model.value.output == null) return
        launchWork(PatchStage.Export) {
            withContext(Dispatchers.IO) { repository.export(uri) }
            notify("APK gespeichert.", MessageType.Success)
        }
    }

    fun notify(text: String, type: MessageType = MessageType.Info, update: Boolean = false) {
        val message = PatchMessage(text, type)
        model.update { if (update) it.copy(updateMessage = message) else it.copy(message = message) }
    }

    private fun launchWork(stage: PatchStage, cloneUpdate: Boolean = false, action: suspend () -> Unit) {
        if (model.value.stage != null) return
        val update = stage == PatchStage.CheckUpdate || stage == PatchStage.DownloadUpdate
        model.update { state ->
            when {
                cloneUpdate -> state.copy(stage = stage, cloneUpdateMessage = null, message = null)
                update -> state.copy(stage = stage, updateMessage = null)
                else -> state.copy(stage = stage, message = null)
            }
        }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                report(failure, update, cloneUpdate)
            } catch (failure: LinkageError) {
                report(failure, update, cloneUpdate)
            } finally {
                model.update { it.copy(stage = null) }
            }
        }
    }

    private fun report(failure: Throwable, update: Boolean = false, cloneUpdate: Boolean = false) {
        Log.e("BridgeModding", "Aktion fehlgeschlagen", failure)
        val message = failure.localizedMessage ?: "Die Aktion ist fehlgeschlagen."
        if (cloneUpdate) {
            model.update { it.copy(cloneUpdateMessage = PatchMessage(message, MessageType.Error)) }
        } else {
            notify(message, MessageType.Error, update)
        }
    }
}
