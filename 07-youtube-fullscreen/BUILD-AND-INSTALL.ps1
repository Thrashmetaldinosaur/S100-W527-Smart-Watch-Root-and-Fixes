$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'BUILD-WINDOWS.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Installation stopped.' }
& (Join-Path $PSScriptRoot 'INSTALL-TEST-ON-S100.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Install failed.' }
Write-Host "`nNEXT: Check LSPosed > Modules > S100 YouTube Fullscreen Fix, enabled and scoped ONLY to YouTube." -ForegroundColor Cyan
Write-Host 'Then restart YouTube with am start (never adb monkey).' -ForegroundColor Yellow
