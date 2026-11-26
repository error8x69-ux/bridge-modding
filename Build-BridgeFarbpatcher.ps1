param(
    [string]$SdkPath = (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
    [string]$SigningStore = (Join-Path $env:USERPROFILE '.android\debug.keystore'),
    [string]$GradleLauncher,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$gradleCache = Join-Path $env:USERPROFILE '.gradle'
if (!$GradleLauncher) {
    $distribution = Join-Path $gradleCache 'wrapper\dists\gradle-8.14.3-bin'
    $GradleLauncher = Get-ChildItem $distribution -Filter 'gradle-gradle-cli-main-8.14.3.jar' -File -Recurse |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$GradleLauncher -or !(Test-Path -LiteralPath $GradleLauncher -PathType Leaf)) {
    throw 'Pass the Gradle 8.14.3 launcher JAR with -GradleLauncher.'
}
if (!(Test-Path -LiteralPath $SigningStore -PathType Leaf)) {
    throw 'The signing keystore is required to build an in-place update.'
}

$env:ANDROID_HOME = $SdkPath
$env:ANDROID_USER_HOME = Join-Path $env:TEMP 'bridge-android'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle\bridge-build-home'
$env:GRADLE_RO_DEP_CACHE = Join-Path $gradleCache 'caches'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$gradleArgs = @(
    '--no-daemon', '--max-workers', '2', '--console=plain',
    '-p', $PSScriptRoot, "-PbridgeSigningStore=$SigningStore",
    '-Pkotlin.compiler.execution.strategy=in-process', 'assembleDebug'
)
if ($Offline) {
    $gradleArgs += '--offline'
}
& java -Xmx2g '-Dfile.encoding=UTF-8' '-Dorg.gradle.internal.instrumentation.agent=false' -jar $GradleLauncher @gradleArgs
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed ($LASTEXITCODE)."
}

$apkFolder = Join-Path $PSScriptRoot 'build\outputs\apk\debug'
$build = Get-Content -Raw -LiteralPath (Join-Path $apkFolder 'output-metadata.json') | ConvertFrom-Json
$variant = $build.elements | Select-Object -First 1
$sourcePath = Join-Path $apkFolder $variant.outputFile
$outputPath = Join-Path (Split-Path $PSScriptRoot) "Bridge-Modding-$($variant.versionName).apk"
$buildTools = Join-Path $SdkPath 'build-tools\36.0.0'
& java -jar (Join-Path $buildTools 'lib\apksigner.jar') verify --verbose $sourcePath
if ($LASTEXITCODE -ne 0) {
    throw 'APK signature verification failed.'
}
& (Join-Path $buildTools 'zipalign.exe') -c 4 $sourcePath
if ($LASTEXITCODE -ne 0) {
    throw 'APK alignment verification failed.'
}
Copy-Item -LiteralPath $sourcePath -Destination $outputPath -Force
Write-Output $outputPath
