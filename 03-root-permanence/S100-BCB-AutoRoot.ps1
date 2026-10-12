<#
S100 W527 persistent Magisk recovery selection via misc BCB.
Requires verified working Magisk root in the existing recovery installation.
Modes: Status (read-only), Enable (boot-recovery), Disable (clear BCB command).
CAUTION: Tested on one S100 configuration only. Have working FDL2 recovery.
#>
param(
    [ValidateSet('Status','Enable','Disable')]
    [string]$Mode = 'Status',
    [string]$AdbPath = 'adb',
    [string]$Serial = '',
    [string]$BackupDir = (Join-Path $PSScriptRoot 'backups')
)

$ErrorActionPreference = 'Stop'
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}
if ($Serial) { $deviceSelector = @('-s', $Serial) }
else { $deviceSelector = @('-d') }

function Invoke-Adb {
    param([string[]]$Arguments)
    & $AdbPath @deviceSelector @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

Write-Host "=== S100 MAGISK BCB AUTO-ROOT: $Mode ===" -ForegroundColor Cyan
Write-Host 'Status reads only; Enable/Disable affect just the first 32 bytes of misc.'

$state = (& $AdbPath @deviceSelector get-state 2>$null | Out-String).Trim()
if ($state -ne 'device') { throw 'USB ADB device unavailable.' }

$root = (& $AdbPath @deviceSelector shell 'su -c id' 2>$null | Out-String).Trim()
if ($root -notmatch 'uid=0\(root\)') {
    throw 'Magisk root unavailable. Boot the pre-existing working recovery using adb reboot recovery, then retry.'
}
Write-Host "Root: $root" -ForegroundColor Green

New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$prefix = "s100_bcb_${Mode}_$stamp"
$before = Join-Path $BackupDir "${prefix}_misc_before.img"
$remoteBefore = "/data/local/tmp/${prefix}_before.img"

Write-Host "`nBacking up entire misc partition..." -ForegroundColor Cyan
Invoke-Adb -Arguments @('shell', "su -c 'dd if=/dev/block/by-name/misc of=$remoteBefore bs=4096'")
Invoke-Adb -Arguments @('pull', $remoteBefore, $before)
[byte[]]$original = [IO.File]::ReadAllBytes($before)
if ($original.Length -ne 1048576) {
    throw "Unexpected misc length $($original.Length); aborting. Backup: $before"
}
$currentCommand = [Text.Encoding]::ASCII.GetString($original,0,32).TrimEnd([char]0)
Write-Host "Current BCB: '$currentCommand'"
Write-Host "Backup SHA256: $((Get-FileHash -LiteralPath $before -Algorithm SHA256).Hash)"
Write-Host "Backup path: $before" -ForegroundColor Green

if ($Mode -eq 'Status') {
    Write-Host 'READ-ONLY status complete.' -ForegroundColor Green
    return
}
if ($currentCommand -notin @('', 'boot-recovery', 'bootonce-bootloader')) {
    throw 'Unexpected BCB command. Stop and inspect your backup.'
}
$desiredCommand = if ($Mode -eq 'Enable') { 'boot-recovery' } else { '' }
if ($currentCommand -eq $desiredCommand) {
    Write-Host "Already configured: '$desiredCommand'. No changes."
    return
}

[byte[]]$expected = [byte[]]$original.Clone()
[Array]::Clear($expected,0,32)
if ($desiredCommand) {
    [byte[]]$cmdBytes = [Text.Encoding]::ASCII.GetBytes($desiredCommand)
    [Array]::Copy($cmdBytes,0,$expected,0,$cmdBytes.Length)
}
[byte[]]$sector = New-Object byte[] 512
[Array]::Copy($expected,0,$sector,0,512)

$patch = Join-Path $BackupDir "${prefix}_sector.bin"
$expectedFile = Join-Path $BackupDir "${prefix}_misc_expected.img"
$after = Join-Path $BackupDir "${prefix}_misc_readback.img"
$remoteSector = "/data/local/tmp/${prefix}_sector.bin"
$remoteAfter = "/data/local/tmp/${prefix}_after.img"
[IO.File]::WriteAllBytes($patch,$sector)
[IO.File]::WriteAllBytes($expectedFile,$expected)

Write-Host "`nRequested: '$currentCommand' -> '$desiredCommand'" -ForegroundColor Yellow
if ($Mode -eq 'Enable') {
    Write-Host 'Selects EXISTING Magisk recovery; does not install Magisk.'
} else {
    Write-Host 'Clears the recovery override; does not restore bootonce-bootloader.'
    Write-Host 'Disable operation is not yet verified on the physical device.'
}
Write-Host 'Make sure original partition backups and a working FDL2 rescue procedure are available.' -ForegroundColor Yellow
$answer = (Read-Host "Type $($Mode.ToUpperInvariant()) to write the 512-byte sector").Trim()
if ($answer -cne $Mode.ToUpperInvariant()) {
    Write-Host 'Cancelled; no device modification.'
    return
}

Invoke-Adb -Arguments @('push',$patch,$remoteSector)
Invoke-Adb -Arguments @('shell',"su -c 'dd if=$remoteSector of=/dev/block/by-name/misc bs=512 count=1 conv=notrunc; sync'")
Invoke-Adb -Arguments @('shell',"su -c 'dd if=/dev/block/by-name/misc of=$remoteAfter bs=4096'")
Invoke-Adb -Arguments @('pull',$remoteAfter,$after)
if ((Get-Item -LiteralPath $after).Length -ne 1048576) {
    throw 'Unexpected readback size. DO NOT REBOOT.'
}
$expectedHash = (Get-FileHash -LiteralPath $expectedFile -Algorithm SHA256).Hash
$actualHash = (Get-FileHash -LiteralPath $after -Algorithm SHA256).Hash
Write-Host "Expected SHA256: $expectedHash"
Write-Host "Readback SHA256: $actualHash"
if ($expectedHash -ne $actualHash) {
    throw 'MISC READBACK MISMATCH. DO NOT REBOOT; keep backups.'
}
Write-Host "SUCCESS: $Mode verified. No reboot performed." -ForegroundColor Green
Write-Host "Before: $before"
Write-Host "After:  $after"
