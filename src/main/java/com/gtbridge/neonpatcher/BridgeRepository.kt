package com.gtbridge.neonpatcher

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.gtbridge.OriginalBridgeUpdates
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

internal sealed interface BridgeSource {
    data object Installed : BridgeSource
    data class Document(val uri: Uri, val name: String) : BridgeSource
    data class Published(val release: OriginalBridgeUpdates.Release) : BridgeSource
}

internal data class BridgeVersion(val name: String, val code: Long) {
    val label: String get() = "$name · Build $code"
}

internal data class BridgeDisplayOptions(
    val hideStatusBar: Boolean = false,
    val compactNavigation: Boolean = false,
    val hideReadingsPopup: Boolean = false,
    val cockpitText: CockpitText = CockpitText.Standard,
    val hiddenReadings: Int = 0,
    val escFirst: Boolean = false,
    val holdSettings: Boolean = false,
    val customOrder: Boolean = false,
    val readingOrder: List<CockpitReading> = defaultReadingOrder,
    val restrictedTripleTap: Boolean = false,
    val keepPageOnResize: Boolean = false
) {
    fun shows(reading: CockpitReading) = !reading.hiddenIn(hiddenReadings)

    fun show(reading: CockpitReading, visible: Boolean) = copy(
        hiddenReadings = if (visible) hiddenReadings and reading.bit.inv() else hiddenReadings or reading.bit
    )

    val visibleOrderedReadings: List<CockpitReading> get() = readingOrder.filter {
        shows(it) && (!it.isDriveReading() || shows(CockpitReading.DRIVE))
    }

    val tripleTapEnabled: Boolean get() = restrictedTripleTap && (hideReadingsPopup || holdSettings)
}

internal val defaultReadingOrder = CockpitReading.entries.filter { it != CockpitReading.DRIVE }

internal fun parseReadingOrder(encoded: String): List<CockpitReading> {
    val readings = encoded.split(',').map(CockpitReading::valueOf)
    require(readings.size == defaultReadingOrder.size && readings.toSet() == defaultReadingOrder.toSet())
    return readings
}

internal enum class PatchStage(val title: String) {
    Validate("Original wird geprüft"),
    Patch("Anpassungen werden angewendet"),
    Sign("Kopie wird signiert"),
    Export("APK wird gespeichert"),
    ImportProfile("Patch-Einstellungen werden gelesen"),
    CheckUpdate("Bridge-Modding-Updates werden gesucht"),
    DownloadUpdate("Bridge-Modding-Update wird geladen und geprüft"),
    CheckOriginalUpdate("GT-Mod-Bridge-Updates werden gesucht"),
    DownloadOriginalUpdate("Original-Update wird geladen und geprüft"),
    ExportProfile("Patch-Einstellungen werden gespeichert")
}

internal data class PatchReport(val version: BridgeVersion, val changes: List<String>, val colorInstructions: Int)

internal class MissingAppliedProfile : IOException()

internal class BridgeRepository(private val context: Context) {
    private val manager = context.packageManager
    private val signatureFlags =
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
    private val noFlags = PackageManager.PackageInfoFlags.of(0)
    private val result = File(context.cacheDir, CLONE_FILE)

    fun installedVersion(packageName: String): BridgeVersion? = try {
        manager.getPackageInfo(packageName, noFlags).toVersion()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun readyVersion(): BridgeVersion? =
        result.takeIf { it.isFile }?.let { archive(it, noFlags)?.toVersion() }

    fun document(uri: Uri): BridgeSource.Document {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        return BridgeSource.Document(uri, name ?: "Original-APK")
    }

    fun patch(
        source: BridgeSource,
        profile: BridgeProfile,
        replacedClone: PackageInfo? = null,
        progress: (PatchStage) -> Unit
    ): PatchReport {
        val display = profile.display
        progress(PatchStage.Validate)
        val scratch = Files.createTempDirectory(context.cacheDir.toPath(), "patch-").toFile()
        val input = File(scratch, "source.apk")
        val unsigned = File(scratch, "unsigned.apk")
        val signed = File(scratch, "signed.apk")
        try {
            val original = when (source) {
                BridgeSource.Installed -> {
                    val info = try {
                        manager.getPackageInfo(BridgeNeonBuild.ORIGINAL, noFlags)
                    } catch (missing: PackageManager.NameNotFoundException) {
                        throw IOException("Wähle die Original-APK aus oder installiere die GT VESC Bridge.", missing)
                    }
                    if (!info.splitNames.isNullOrEmpty()) {
                        throw IOException("Wähle eine vollständige Original-APK der GT VESC Bridge aus.")
                    }
                    File(info.applicationInfo?.sourceDir ?: throw IOException("Installierte APK nicht gefunden."))
                }
                is BridgeSource.Document -> {
                    val stream = context.contentResolver.openInputStream(source.uri)
                        ?: throw IOException("Die APK lässt sich nicht öffnen.")
                    stream.use { incoming ->
                        input.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val count = incoming.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > 128L * 1024 * 1024) throw IOException("Die APK ist zu groß.")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    input
                }
                is BridgeSource.Published -> {
                    progress(PatchStage.DownloadOriginalUpdate)
                    OriginalBridgeUpdates.download(source.release, input)
                    input
                }
            }
            val origin = checkOriginal(archive(original, signatureFlags))
            if (source is BridgeSource.Published && origin.longVersionCode != source.release.code) {
                throw IOException("Der Versionscode der Original-APK stimmt nicht mit der Update-Quelle überein.")
            }
            if (replacedClone != null && origin.longVersionCode <= replacedClone.longVersionCode) {
                throw IOException("Wähle eine Original-APK, die neuer als die installierte GT Mod Bridge ist.")
            }

            progress(PatchStage.Patch)
            val helperDex = if (display.hideStatusBar || display.holdSettings || display.customOrder
                    || display.tripleTapEnabled || OnlineOption.Updates !in profile.blocked) {
                context.assets.open("clone-status/classes.dex").use { it.readBytes() }
            } else null
            val colors = profile.palette.colors
            val replacements = colors?.let {
                BridgeNeonBuild.palette(it[0], it[1], it[2])
            } ?: emptyMap()
            val grid = if (display.customOrder) {
                BridgeNeonBuild.CockpitGrid(display.visibleOrderedReadings.toTypedArray(),
                    colors?.get(0) ?: 0xff0b1218.toInt(), colors?.get(1) ?: 0xffedf2f5.toInt(),
                    colors?.get(2) ?: 0xffee405e.toInt())
            } else null
            val colorInstructions = BridgeNeonBuild.patch(original.toPath(), unsigned.toPath(),
                replacements, helperDex, display.hideStatusBar, display.compactNavigation, display.hideReadingsPopup,
                profile.onlineMask, display.cockpitText.percent, display.hiddenReadings, display.escFirst,
                display.holdSettings, grid, display.tripleTapEnabled, display.keepPageOnResize,
                profile.encode().toByteArray(Charsets.UTF_8))

            progress(PatchStage.Sign)
            try {
                sign(unsigned, signed)
            } catch (failure: Exception) {
                throw IOException("Die Kopie konnte nicht signiert werden.", failure)
            }
            val copy = archive(signed, signatureFlags)
            if (copy?.packageName != CLONE || copy.longVersionCode != origin.longVersionCode) {
                throw IOException("Die erzeugte APK ist ungültig.")
            }
            if (replacedClone != null) {
                val installedSigner = replacedClone.signingInfo?.apkContentsSigners?.singleOrNull()
                val newSigner = copy.signingInfo?.apkContentsSigners?.singleOrNull()
                if (newSigner == null || installedSigner != newSigner) {
                    throw IOException("Die Signatur passt nicht zur installierten GT Mod Bridge. Das Update wurde abgebrochen.")
                }
            }
            Files.move(signed.toPath(), result.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            return PatchReport(copy.toVersion(), profile.summary, colorInstructions)
        } finally {
            Files.deleteIfExists(input.toPath())
            Files.deleteIfExists(unsigned.toPath())
            Files.deleteIfExists(signed.toPath())
            Files.deleteIfExists(scratch.toPath())
        }
    }

    fun updateClone(source: BridgeSource, fallback: BridgeProfile?, progress: (PatchStage) -> Unit): PatchReport {
        val clone = try {
            manager.getPackageInfo(CLONE, signatureFlags)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IOException("GT Mod Bridge ist nicht installiert.")
        }
        val installedApk = clone.applicationInfo?.sourceDir ?: throw IOException("Installierte APK wurde nicht gefunden.")
        val applied = java.util.zip.ZipFile(installedApk).use { zip ->
            zip.getEntry(BridgeNeonBuild.PATCH_PROFILE)?.let { entry ->
                val bytes = zip.getInputStream(entry).use { it.readNBytes(16_385) }
                if (bytes.size > 16_384) throw IOException("Das Mod-Profil in der installierten APK ist zu groß.")
                try {
                    BridgeProfile.decode(bytes.toString(Charsets.UTF_8))
                } catch (failure: Exception) {
                    throw IOException("Das Mod-Profil in der installierten APK kann nicht gelesen werden.", failure)
                }
            }
        } ?: fallback ?: throw MissingAppliedProfile()
        return patch(source, applied, clone, progress)
    }

    fun readProfile(uri: Uri): BridgeProfile {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(16_385) }
            ?: throw IOException("Die Profildatei lässt sich nicht öffnen.")
        if (bytes.size > 16_384) throw IOException("Die Profildatei ist zu groß.")
        return try {
            BridgeProfile.decode(bytes.toString(Charsets.UTF_8))
        } catch (failure: Exception) {
            throw IOException("Diese Datei enthält kein unterstütztes Bridge-Modding-Profil.", failure)
        }
    }

    fun writeProfile(uri: Uri, profile: BridgeProfile) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Die Profildatei lässt sich nicht speichern.")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(profile.encode()) }
    }

    fun export(uri: Uri) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Die Zieldatei lässt sich nicht öffnen.")
        stream.use { Files.copy(result.toPath(), it) }
    }

    fun installIntent(update: Boolean): Intent {
        val name = if (update) UPDATE_FILE else CLONE_FILE
        val uri = Uri.parse("content://${context.packageName}.apk/$name")
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            .apply { clipData = ClipData.newRawUri(name, uri) }
    }

    private fun archive(file: File, flags: PackageManager.PackageInfoFlags) =
        manager.getPackageArchiveInfo(file.path, flags)

    private fun PackageInfo.toVersion() = BridgeVersion(versionName ?: "Unbekannte Version", longVersionCode)

    private fun checkOriginal(info: PackageInfo?): PackageInfo {
        val certificate = info?.signingInfo?.apkContentsSigners?.singleOrNull()
        if (info?.packageName != BridgeNeonBuild.ORIGINAL || certificate == null) {
            throw IOException("Wähle die vollständige Original-APK der GT VESC Bridge aus.")
        }
        val expected = OriginalBridgeUpdates.CERTIFICATE.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val actual = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
        if (!MessageDigest.isEqual(expected, actual)) throw IOException("Die Original-APK ist anders signiert.")
        return info
    }

    companion object {
        const val APK = "application/vnd.android.package-archive"
        const val CLONE_FILE = "GT-Mod-Bridge.apk"
        const val UPDATE_FILE = "Bridge-Modding-Update.apk"
        const val CLONE = BridgeNeonBuild.CLONE
        private const val KEY_ALIAS = "bridge-clone-v1"

        @Synchronized
        private fun sign(input: File, output: File) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(KEY_ALIAS)) {
                KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                    initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build())
                }.generateKeyPair()
            }
            val privateKey = store.getKey(KEY_ALIAS, null) as PrivateKey
            val certificate = store.getCertificate(KEY_ALIAS) as X509Certificate
            val signer = ApkSigner.SignerConfig.Builder(KEY_ALIAS, privateKey, listOf(certificate)).build()
            ApkSigner.Builder(listOf(signer)).setInputApk(input).setOutputApk(output)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(false)
                .setAlignmentPreserved(true).setLibraryPageAlignmentBytes(16384)
                .setV4SigningEnabled(false).setMinSdkVersion(33).build().sign()
        }
    }
}
