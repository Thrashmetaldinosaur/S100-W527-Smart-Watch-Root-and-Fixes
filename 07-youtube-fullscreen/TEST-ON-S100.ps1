$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\zvigs\Downloads\PC\platform-tools\adb.exe'
$id = '61896383612846'
$out = Join-Path $env:USERPROFILE 'Downloads\S100-YT-V100-Test.txt'
if ((& $adb -s $id get-state | Out-String).Trim() -ne 'device') { throw 'S100 disconnected' }
& $adb -s $id shell am force-stop com.google.android.youtube
& $adb -s $id shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.youtube
Read-Host 'Turn on English CC for a video KNOWN to have captions. Enter fullscreen, wait 20 seconds, then press Enter'
Read-Host 'Tap lower black band, verify seek bar and controls appear then disappear, minimize the video, press Enter'
$lines = & $adb -s $id shell "su -c 'grep -h S100YT-V100 /data/adb/lspd/log/modules_*.log 2>/dev/null | tail -n 400'"
$lines | Set-Content -LiteralPath $out -Encoding UTF8
$lines | Select-String 'CAPTION_|CONTROL_PANEL|TOP_LAYER|MINIPLAYER|RESTORED|failure|exception' | Select-Object -Last 100
Write-Host "`nLogs saved: $out" -ForegroundColor Green
