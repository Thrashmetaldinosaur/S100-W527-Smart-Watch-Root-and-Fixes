$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "`n=== S100 YOUTUBE RELEASE BUILD v1.0 ===" -ForegroundColor Cyan

$toolRoot = Join-Path $env:LOCALAPPDATA 'S100-Android-BuildTools'
New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null

# Gradle 8.7 + Android Gradle Plugin 8.5.2 require a supported Java runtime.
# Android Studio's current JBR may be Java 25, which Gradle 8.7 cannot run.
# Use a Java 17 or Java 21 installation instead. Never change machine-wide Java.
function Get-UsableJavaVersion([string]$dir) {
    if ([string]::IsNullOrWhiteSpace($dir)) { return $null }
    $javaExe = Join-Path $dir 'bin\java.exe'
    if (!(Test-Path -LiteralPath $javaExe)) { return $null }
    $versionText = (& $javaExe --version | Out-String)
    if ($LASTEXITCODE -ne 0) { return $null }
    if ($versionText -match '^(?:openjdk|java)\s+(17|21)(?:[.\s+]|$)') {
        return $Matches[1]
    }
    return $null
}

$candidates = [System.Collections.Generic.List[string]]::new()
foreach ($candidate in @($env:S100_JAVA_HOME, $env:JAVA_HOME, (Join-Path $env:USERPROFILE '.jdks'),
                           'C:\Program Files\Android\Android Studio\jbr')) {
    if ($candidate -and (Test-Path $candidate)) { $candidates.Add($candidate) }
}
foreach ($base in @($toolRoot, 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java',
                    'C:\Program Files\Microsoft', 'C:\Program Files\Zulu',
                    (Join-Path $env:USERPROFILE '.jdks'))) {
    if (Test-Path $base) {
        Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates.Add($_.FullName)
            # Some vendors nest JDKs under a version-specific parent folder.
            if ($_.Name -like 'jdk17*') {
                Get-ChildItem -LiteralPath $_.FullName -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                    $candidates.Add($_.FullName)
                }
            }
        }
    }
}

$javaHome = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    $major = Get-UsableJavaVersion $candidate
    if ($major -eq '17') { $javaHome = $candidate; break }
    if ($major -eq '21' -and !$javaHome) { $javaHome = $candidate }
}

if (!$javaHome) {
    Write-Host "`nNo installed Java 17/21 found. Downloading portable Temurin JDK 17 (one time)..." -ForegroundColor Yellow
    $jdkRoot = Join-Path $toolRoot 'jdk17'
    New-Item -ItemType Directory -Path $jdkRoot -Force | Out-Null
    # Request official Windows x64 JDK ZIP metadata from the Eclipse Adoptium API.
    $api = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse'
    $assets = @(Invoke-RestMethod -Uri $api -TimeoutSec 60)
    $asset = $assets | Where-Object {
        $_.binary.package.link -match '\.zip($|\?)' -and $_.binary.package.checksum -match '^[a-fA-F0-9]{64}$'
    } | Select-Object -First 1
    if (!$asset) { throw 'Unable to retrieve a verified Temurin 17 ZIP. Install JDK 17 in Android Studio, then retry.' }
    $jdkZip = Join-Path $toolRoot 'Temurin17-windows-x64.zip'
    $expected = $asset.binary.package.checksum.ToUpperInvariant()
    $needsDownload = !(Test-Path $jdkZip)
    if (!$needsDownload) {
        $needsDownload = (Get-FileHash -LiteralPath $jdkZip -Algorithm SHA256).Hash -ne $expected
    }
    if ($needsDownload) {
        Invoke-WebRequest -Uri $asset.binary.package.link -OutFile $jdkZip -TimeoutSec 1800
    }
    $actual = (Get-FileHash -LiteralPath $jdkZip -Algorithm SHA256).Hash
    if ($actual -ne $expected) { throw 'Temurin JDK checksum mismatch. Refusing to extract or run.' }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkRoot -Force
    $javaHome = Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object {
        (Get-UsableJavaVersion $_.FullName) -eq '17'
    } | Select-Object -First 1 -ExpandProperty FullName
    if (!$javaHome) { throw 'Temurin JDK 17 extraction did not produce bin/java.exe.' }
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$(Join-Path $javaHome 'bin');$env:PATH"
Write-Host "Java selected: $javaHome" -ForegroundColor Green
& (Join-Path $env:JAVA_HOME 'bin\java.exe') --version
if ($LASTEXITCODE -ne 0) { throw 'Selected Java runtime did not start.' }

if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) {
    $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not (Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-34\android.jar'))) {
    throw "Android SDK Platform 34 not installed at '$env:ANDROID_HOME'. Open Android Studio > SDK Manager > SDK Platforms and install Android 14 / API 34, then retry."
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Write-Host "Android SDK: $env:ANDROID_HOME"

# AGP 8.5.2 requires Gradle 8.7. Verify official Gradle ZIP checksum.
$gradleBat = Join-Path $toolRoot 'gradle-8.7\bin\gradle.bat'
$archive = Join-Path $toolRoot 'gradle-8.7-bin.zip'
$expectedHash = '544C35D6BD849AE8A5ED0BCEA39BA677DC40F49DF7D1835561582DA2009B961D'
if (-not (Test-Path $gradleBat)) {
    if (Test-Path $archive) {
        $existingHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
        if ($existingHash -ne $expectedHash) { Remove-Item -LiteralPath $archive -Force }
    }
    if (-not (Test-Path $archive)) {
        Write-Host 'Downloading official Gradle 8.7 (once only)...' -ForegroundColor Cyan
        Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' -OutFile $archive
    }
    $actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    if ($actualHash -ne $expectedHash) { throw "Gradle ZIP checksum mismatch: $actualHash. Refusing to run." }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
}
if (-not (Test-Path $gradleBat)) { throw 'Gradle extraction failed.' }

Write-Host "`n=== COMPILE ===" -ForegroundColor Cyan
& $gradleBat :app:assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed. Send the full output to ChatGPT.' }

Write-Host "`n=== GENERATE GRADLE WRAPPER ===" -ForegroundColor Cyan
& $gradleBat wrapper --gradle-version 8.7 --distribution-type bin
if ($LASTEXITCODE -ne 0) { Write-Warning 'APK built, but Gradle wrapper generation failed.' }

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $apk)) { throw 'Gradle reported success but no APK was found.' }
Write-Host "`nAPK BUILT: $apk" -ForegroundColor Green
