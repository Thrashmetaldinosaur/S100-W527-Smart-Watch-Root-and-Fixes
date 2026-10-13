$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\zvigs\Downloads\PC\platform-tools\adb.exe'
$id = '61896383612846'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $adb)) { throw 'ADB not found at expected path' }
if (!(Test-Path $apk)) { throw 'No compiled APK. Build the project first, or copy app-debug.apk into app/build/outputs/apk/debug/.' }
$connection = (& $adb -s $id get-state 2>&1 | Out-String).Trim()
if ($connection -ne 'device') { throw "S100 unavailable ($connection)" }
$version = (& $adb -s $id shell dumpsys package com.google.android.youtube | Select-String 'versionName=' | Select-Object -First 1).ToString().Trim()
Write-Host "YouTube: $version"
if ($version -notmatch '21\.40\.161') { throw 'This module targets YouTube 21.40.161. No changes made.' }
& $adb -s $id install --no-streaming -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK install failed.' }
Write-Host 'Installed. In LSPosed, enable S100 YouTube Fullscreen Fix scoped ONLY to com.google.android.youtube.' -ForegroundColor Yellow
Write-Host 'Force-stop YouTube and relaunch to load hooks. For rollback: disable in LSPosed and reboot.'
