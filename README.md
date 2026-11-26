# Bridge Modding

Android-App zum Erstellen persönlicher Kopien der GT VESC Bridge mit eigenen Farben, Cockpit- und Online-Optionen. Die Original-App wird beim Erstellen einer Kopie als Eingabe benötigt.

## Installation und Updates

Lade die signierte APK aus den [GitHub Releases](https://github.com/error8x69-ux/bridge-modding/releases/latest). In **APK → Bridge Modding** stehen die installierte Version und die manuelle Updateprüfung. Die App prüft Paketkennung, Versionscode und Signatur der heruntergeladenen APK. Android bestätigt die Installation.

Stabile Releases verwenden Tags wie `v0.13` und ein APK-Asset wie `Bridge-Modding-0.13.apk`. Jedes Update braucht einen höheren Android-Versionscode und denselben Signaturschlüssel.

Die Android-Oberfläche nutzt Kotlin, Jetpack Compose und Material 3. Der Java-Patchkern wird von der APK-Verarbeitung aufgerufen und kann auch direkt auf dem Desktop geprüft werden.

## Build

Benötigt werden JDK 17, Gradle 8.14.3, Android SDK 35 und Build Tools 36.0.0. Für ein Update einer vorhandenen Installation muss der bisherige Signaturschlüssel verwendet werden. Schlüsseldateien gehören nicht in den Quellcode.

```powershell
.\Build-BridgeFarbpatcher.ps1
```

`-Offline` verwendet lokale Gradle-Abhängigkeiten. `-GradleLauncher`, `-SdkPath` und `-SigningStore` setzen alternative Pfade.

## Aufbau

- `BridgeTheme.kt` definiert Farben, Typografie, Formen und gemeinsame Panels.
- `BridgeApp.kt` steuert Navigation und das Layout für schmale und breite Fenster. Funktionen bekommen eigene Screens.
- `PatchViewModel.kt` hält den UI-Zustand und koordiniert Hintergrundarbeit.
- `BridgeRepository.kt` kümmert sich um Quellen, Signatur und APK-Dateien. Die Verarbeitung der Farbwerte liegt in `BridgeNeonBuild.java` und `BridgeNeonDex.java`.
- `PatcherActivity.kt` verbindet die Screens mit den Android-Dialogen für Dateiauswahl, Export und Installation.
