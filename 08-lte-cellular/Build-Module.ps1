#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Join-Path $PSScriptRoot 'module'
$zip=Join-Path $PSScriptRoot 'S100-LTE-AutoBoot-v1.1.1.zip'
foreach ($name in @('module.prop','customize.sh','service.sh','boot-helper.sh','skip_mount')) {
 if (!(Test-Path (Join-Path $root $name))) {throw "Missing module file: $name"}
}
if (Test-Path $zip) {Remove-Item $zip -Force}
Compress-Archive -Path (Join-Path $root '*') -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Module created: $zip" -ForegroundColor Green
Write-Host 'Firmware-specific patched executable will be generated from the watch during install.'
