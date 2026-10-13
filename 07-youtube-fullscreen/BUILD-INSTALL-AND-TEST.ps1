$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'BUILD-AND-INSTALL.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Build/install did not complete' }
$adb = 'C:\Users\zvigs\Downloads\PC\platform-tools\adb.exe'
$id = '61896383612846'
& $adb -s $id shell am force-stop com.google.android.youtube
& $adb -s $id shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.youtube
if ($LASTEXITCODE -ne 0) { throw 'Unable to launch YouTube' }
Read-Host 'Play a CC-enabled video in fullscreen, test subtitles, controls and progress slider, then press Enter'
Read-Host 'Minimize and reopen fullscreen, confirm playback is normal, then press Enter'
$logs = & $adb -s $id shell "su -c 'grep -h S100YT-V100 /data/adb/lspd/log/modules_*.log 2>/dev/null | tail -n 100'"
$logPath = Join-Path $env:USERPROFILE 'Downloads\S100-YT-V100-Test.txt'
$logs | Set-Content -LiteralPath $logPath -Encoding UTF8
$logs | Select-String 'loaded package|FULLSCREEN|MINIPLAYER|RESTORED|failure|error'
Write-Host "`nTest logs saved: $logPath" -ForegroundColor Green
