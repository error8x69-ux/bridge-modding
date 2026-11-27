package com.gtbridge.neonpatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class OriginalChangelog(val version: BridgeVersion, val notes: String)

private val versionHeading = Regex(
    """^([0-9]+(?:\.[0-9]+)+(?:[-+][A-Za-z0-9.+-]+)?)[ \t]*\(code[ \t]+([0-9]+)\)[ \t]*$""",
    RegexOption.MULTILINE
)

internal fun parseOriginalChangelogs(version: BridgeVersion, changelog: String): List<OriginalChangelog> {
    val text = changelog.trim()
    if (text.isEmpty()) return emptyList()
    val headings = versionHeading.findAll(text).toList()
    if (headings.firstOrNull()?.range?.first != 0) return listOf(OriginalChangelog(version, text))
    return headings.mapIndexedNotNull { index, heading ->
        val code = heading.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
        val end = headings.getOrNull(index + 1)?.range?.first ?: text.length
        val notes = text.substring(heading.range.last + 1, end).trim()
        if (notes.isEmpty()) return@mapIndexedNotNull null
        OriginalChangelog(BridgeVersion(heading.groupValues[1], code), notes)
    }
}

internal class OriginalChangelogHistory(context: Context) {
    private val preferences = context.getSharedPreferences("original_changelogs", Context.MODE_PRIVATE)

    fun load(): List<OriginalChangelog> {
        val saved = preferences.getString("entries", null) ?: return emptyList()
        return try {
            val entries = JSONArray(saved)
            List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                OriginalChangelog(BridgeVersion(entry.getString("name"), entry.getLong("code")),
                    entry.getString("notes"))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun merge(saved: List<OriginalChangelog>, received: List<OriginalChangelog>): List<OriginalChangelog> {
        if (received.isEmpty()) return saved
        val entries = (saved + received).associateBy { it.version.code }.values
            .sortedByDescending { it.version.code }
        if (entries != saved) {
            val json = JSONArray()
            entries.forEach {
                json.put(JSONObject().put("code", it.version.code).put("name", it.version.name)
                    .put("notes", it.notes))
            }
            preferences.edit().putString("entries", json.toString()).apply()
        }
        return entries
    }
}
