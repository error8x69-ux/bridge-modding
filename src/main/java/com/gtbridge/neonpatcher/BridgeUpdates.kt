package com.gtbridge.neonpatcher

import android.content.Context
import android.content.pm.PackageManager
import com.android.apksig.ApkVerifier
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.net.ssl.HttpsURLConnection

internal data class BridgeRelease(val version: String, val apk: URI)

internal class BridgeUpdates(private val context: Context) {
    fun latest(): BridgeRelease? {
        val endpoint = URI("https://api.github.com/repos/$REPOSITORY/releases/latest")
        val release = request(endpoint) { stream ->
            val bytes = stream.readNBytes(MAX_METADATA + 1)
            if (bytes.size > MAX_METADATA) throw IOException("GitHub hat zu viele Release-Daten gesendet.")
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        val tag = release.getString("tag_name")
        val version = tag.removePrefix("v")
        if (tag != "v$version" || !versionPattern.matches(version)) {
            throw IOException("Das aktuelle GitHub-Release hat ein unbekanntes Versionsformat.")
        }
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
        val uri = URI("https://github.com/$REPOSITORY/releases/download/$tag/Bridge-Modding-$version.apk")
        val assets = release.getJSONArray("assets")
        if ((0 until assets.length()).none {
                assets.getJSONObject(it).optString("browser_download_url") == uri.toString()
            }) {
            throw IOException("Die APK fehlt im aktuellen GitHub-Release.")
        }
        return BridgeRelease(version, uri)
    }

    fun download(release: BridgeRelease): BridgeVersion {
        val scratch = File.createTempFile("bridge-update-", ".apk", context.cacheDir)
        try {
            request(release.apk) { input ->
                scratch.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        if (total > MAX_APK) throw IOException("Die Update-APK ist zu groß.")
                        output.write(buffer, 0, size)
                    }
                }
            }
            val version = verify(scratch, release.version)
            val destination = File(context.cacheDir, BridgeRepository.UPDATE_FILE)
            Files.move(scratch.toPath(), destination.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            return version
        } finally {
            scratch.delete()
        }
    }

    private fun verify(file: File, version: String): BridgeVersion {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val incoming = manager.getPackageArchiveInfo(file.path, flags)
        if (incoming?.packageName != context.packageName || incoming.versionName != version ||
            incoming.longVersionCode <= installed.longVersionCode) {
            throw IOException("Diese Datei ist kein passendes Update für Bridge Modding.")
        }
        val trusted = installed.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()
        val verified = ApkVerifier.Builder(file).build().verify()
        val signer = verified.signerCertificates.singleOrNull()
        if (!verified.isVerified || trusted == null || signer == null || !trusted.contentEquals(signer.encoded)) {
            throw IOException("Die Signatur der Update-APK stimmt nicht mit der installierten App überein.")
        }
        return BridgeVersion(version, incoming.longVersionCode)
    }

    private fun <T> request(start: URI, read: (InputStream) -> T): T {
        var uri = start
        repeat(5) {
            if (uri.scheme != "https" || uri.host !in githubHosts || uri.userInfo != null) {
                throw IOException("GitHub hat auf eine unbekannte Download-Adresse verwiesen.")
            }
            val connection = uri.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "Bridge-Modding/${BuildConfig.VERSION_NAME}")
                if (uri.host == "api.github.com") {
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                }
                when (val code = connection.responseCode) {
                    200 -> return connection.inputStream.use(read)
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("GitHub hat kein Download-Ziel angegeben.")
                        uri = uri.resolve(location)
                    }
                    else -> throw IOException("GitHub-Release vorübergehend nicht verfügbar (HTTP $code).")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("GitHub hat zu oft weitergeleitet.")
    }

    companion object {
        const val REPOSITORY = "error8x69-ux/bridge-modding"
        private const val MAX_METADATA = 1024 * 1024
        private const val MAX_APK = 64L * 1024 * 1024
        private val versionPattern = Regex("[0-9]{1,6}(\\.[0-9]{1,6}){1,2}")
        private val githubHosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com")

        private fun isNewer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.').map(String::toInt)
            val b = installed.split('.').map(String::toInt)
            for (index in 0 until maxOf(a.size, b.size)) {
                val comparison = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
                if (comparison != 0) return comparison > 0
            }
            return false
        }
    }
}
