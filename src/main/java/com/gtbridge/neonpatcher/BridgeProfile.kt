package com.gtbridge.neonpatcher

import org.json.JSONObject

internal enum class CockpitText(val percent: Int) {
    Small(90), Standard(100), Large(110)
}

internal enum class OnlineOption(val label: String) {
    Updates("Update-Prüfung blockieren"),
    Analytics("Analyseversand blockieren"),
    Community("Community-Zugriff blockieren"),
    Ranking("GPS-Bestwerte nicht ans Leaderboard senden")
}

internal val privateOnlineOptions = setOf(OnlineOption.Analytics, OnlineOption.Ranking)

internal const val maxCockpitTitleLength = 32

internal fun validCockpitTitle(text: String): Boolean =
    text.length <= maxCockpitTitleLength &&
        text.none { it.isISOControl() || it.category == CharCategory.LINE_SEPARATOR ||
            it.category == CharCategory.PARAGRAPH_SEPARATOR }

internal data class BridgeProfile(
    val palette: BridgePalette,
    val display: BridgeDisplayOptions = BridgeDisplayOptions(),
    val blocked: Set<OnlineOption> = emptySet()
) {
    val onlineMask: Int get() = blocked.sumOf { 1 shl it.ordinal }

    val paletteLabel: String get() = PalettePreset.entries.firstOrNull { it.palette == palette }?.label
        ?: "${palette.background} / ${palette.foreground} / ${palette.accent}"

    val cockpitSummary: List<String> get() = buildList {
        if (display.hideStatusBar) add("Android-Statusleiste ausgeblendet")
        if (display.compactNavigation) add("Kompakte Navigation")
        if (display.hideReadingsPopup || display.holdSettings) add("Cockpit-Tippschutz für Messwerte & Quellen")
        if (display.cockpitText != CockpitText.Standard) add("Cockpit-Schrift · ${display.cockpitText.percent} %")
        val hidden = CockpitReading.entries.filterNot(display::shows)
        if (hidden.isNotEmpty()) add("Im Cockpit ausgeblendet · " + hidden.joinToString { it.label })
        if (display.escFirst && !display.customOrder) add("ESC-Temperatur links vom Motor")
        if (display.customOrder) add("Cockpit-Raster · " + display.visibleOrderedReadings
            .joinToString { it.tileLabel }.ifEmpty { "keine Zusatzwerte" })
        if (display.customOrder && display.hideCockpitTileBackground) add("Kästchenhintergrund ausgeblendet")
        if (display.cockpitTitle.isNotBlank()) add("Cockpit-Schriftzug · ${display.cockpitTitle.trim()}")
        if (display.holdSettings) add("Einstellungen per langem Druck öffnen")
        if (display.keepPageOnResize) add("Aktuelle Seite beim Bildschirmwechsel behalten")
        if (display.tripleTapEnabled) add("Dreifachtipp lädt das gedrosselte Profil")
    }

    val summary: List<String> get() = buildList {
        add("Palette · $paletteLabel")
        addAll(cockpitSummary)
        if (display.showUpdateChangelog) add("Changelogs der Original-App anzeigen")
        addAll(blocked.map { it.label })
    }

    fun encode(): String = JSONObject()
        .put("format", 1)
        .put("background", palette.background)
        .put("foreground", palette.foreground)
        .put("accent", palette.accent)
        .put("original", palette.original)
        .put("hideStatusBar", display.hideStatusBar)
        .put("compactNavigation", display.compactNavigation)
        .put("hideReadingsPopup", display.hideReadingsPopup)
        .put("cockpitText", display.cockpitText.name)
        .put("hideMotorReadings", !display.shows(CockpitReading.DRIVE))
        .put("hiddenReadings", display.hiddenReadings)
        .put("escFirst", display.escFirst)
        .put("holdSettings", display.holdSettings)
        .put("keepCockpitOnResize", display.keepPageOnResize)
        .put("customOrder", display.customOrder)
        .put("hideCockpitTileBackground", display.hideCockpitTileBackground)
        .put("cockpitTitle", display.cockpitTitle)
        .put("showUpdateChangelog", display.showUpdateChangelog)
        .put("readingOrder", display.readingOrder.joinToString(",") { it.name })
        .put("restrictedTripleTap", display.restrictedTripleTap)
        .put("onlineMask", onlineMask)
        .toString(2)

    companion object {
        fun decode(content: String): BridgeProfile {
            val json = JSONObject(content)
            require(json.get("format") == 1)
            fun color(key: String) = requireNotNull(normalizedHex(json.getString(key)))
            fun flag(key: String) = json.get(key) as Boolean
            val mask = json.get("onlineMask") as Int
            require(mask in 0 until (1 shl OnlineOption.entries.size))
            val hidden = when {
                json.has("hiddenReadings") -> json.get("hiddenReadings") as Int
                json.has("hideMotorReadings") && flag("hideMotorReadings") -> CockpitReading.DRIVE.bit
                else -> 0
            }
            require(hidden and CockpitReading.allowedMask().inv() == 0)
            val title = if (json.has("cockpitTitle")) json.get("cockpitTitle") as String else ""
            require(validCockpitTitle(title))
            return BridgeProfile(
                BridgePalette(color("background"), color("foreground"), color("accent"), flag("original")),
                BridgeDisplayOptions(flag("hideStatusBar"), flag("compactNavigation"), flag("hideReadingsPopup"),
                    CockpitText.valueOf(json.optString("cockpitText", CockpitText.Standard.name)),
                    hidden, json.has("escFirst") && flag("escFirst"),
                    json.has("holdSettings") && flag("holdSettings"),
                    json.has("customOrder") && flag("customOrder"),
                    if (json.has("readingOrder")) parseReadingOrder(json.getString("readingOrder")) else defaultReadingOrder,
                    json.has("restrictedTripleTap") && flag("restrictedTripleTap"),
                    json.has("keepCockpitOnResize") && flag("keepCockpitOnResize"),
                    json.has("hideCockpitTileBackground") && flag("hideCockpitTileBackground"), title,
                    json.has("showUpdateChangelog") && flag("showUpdateChangelog")),
                OnlineOption.entries.filterTo(mutableSetOf()) { mask and (1 shl it.ordinal) != 0 }
            )
        }
    }
}
