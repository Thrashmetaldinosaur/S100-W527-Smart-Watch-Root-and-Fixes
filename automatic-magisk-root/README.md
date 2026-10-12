# Automatic Magisk root via persistent recovery selection

**Tested on one S100 W527** (Unisoc UWS6152, crDroid 9.3 Android 13, working Magisk 30.7 recovery). An ordinary `adb reboot` and a complete power-off/cold boot both started Android with `ro.bootmode=recovery`, `su -v=30.7:MAGISKSU`, and `su -c id` returning `uid=0(root)`. The 1 MiB `misc` partition retained the `boot-recovery` BCB command after reboot.

## What this does

The tested stock `boot` partition has no ramdisk. Magisk root already worked via `adb reboot recovery` with a **previously installed, working, signed Magisk-patched recovery image**. Setting the Bootloader Control Block (BCB) command (bytes 0–31 of `misc`) to `boot-recovery` caused this particular bootloader to select that recovery for normal and cold boots.

**This tool does not install Magisk or patch recovery.** Do not apply it until you have independently verified that `adb reboot recovery` starts Android fully with root.

## Tested configuration

- S100 W527, Unisoc UWS6152; crDroid 9.3 Android 13 GSI.
- Original `boot`: 35 MiB, SHA-256 `8CCE2C086D754E6A2F9E00E6419A3EC55EEAFC0CFDEC8B9242E0EBC37D8D2AD2`.
- Separate, previously patched 40 MiB Magisk 30.7 recovery.
- `misc`: 1 MiB at `/dev/block/by-name/misc`. Prior command on the tested unit was `bootonce-bootloader`.
- Normal-boot Magisk image experiment **failed to boot** and was rolled back. This BCB method reuses the **working recovery** instead.
- Rooted reboot results: `sys.boot_completed=1`, `ro.bootmode=recovery`, `su -v=30.7:MAGISKSU`, `su -c id=uid=0(root)`.
- Cold-boot persistence was reported successful by the owner.

## Critical precautions

1. Keep verified backups of **boot, recovery, vbmeta and the entire misc partition** off-device. This tool backs up `misc` at every invocation.
2. Confirm USB ADB and **working Magisk root in recovery** before enabling.
3. Confirm a practical **Unisoc BootROM/FDL2** recovery/rollback method in case Android stops booting. On the tested watch, power off and hold the **top** button while connecting USB.
4. This changes a bootloader request in `misc`: incompatible hardware, firmware updates or recovery images can make the device unbootable. Never treat this as universal S100 support.
5. Never flash another model's firmware, change VBMeta or U-Boot, or factory-reset as part of this fix.

## Usage on Windows

Run the included [`S100-BCB-AutoRoot.ps1`](S100-BCB-AutoRoot.ps1) in Windows PowerShell or PowerShell 7 with ADB installed:

```powershell
# Read-only inspection and fresh full misc backup
.\S100-BCB-AutoRoot.ps1 -Mode Status -AdbPath "C:\path\to\platform-tools\adb.exe"

# Set the persistent recovery-boot command, after verifying working root
.\S100-BCB-AutoRoot.ps1 -Mode Enable -AdbPath "C:\path\to\platform-tools\adb.exe"

# Clear recovery override for normal, unrooted boot (not yet device-tested)
.\S100-BCB-AutoRoot.ps1 -Mode Disable -AdbPath "C:\path\to\platform-tools\adb.exe"
```

Supply `-Serial "ADB_SERIAL"` if needed; otherwise the script uses `adb -d` for a single USB device. All modes require currently active `su` root. If unavailable, use the **already-working** `adb reboot recovery` before running the tool.

The utility saves an entire 1 MiB `misc` backup, modifies **only the BCB command (32 bytes)** in a preserved 512-byte sector, verifies the **whole 1 MiB** readback, and **does not reboot automatically**.

### Verification

After Enable, run a **normal** `adb reboot` (not `adb reboot recovery`). Confirm:

```powershell
adb shell getprop sys.boot_completed
adb shell getprop ro.bootmode
adb shell "su -v"
adb shell "su -c id"
```

Expected on the tested watch: `1`, `recovery`, `30.7:MAGISKSU`, and `uid=0(root)`. Then inspect `-Mode Status`: the BCB should still be `boot-recovery`. Finally power off completely (`adb reboot -p`), unplug, wait, power on normally, and repeat the checks.

## Disabling / rollback

`-Mode Disable` clears **only** the 32-byte BCB command, intended to restore ordinary boot selection. This disable operation **has not yet been verified on the watch**. It deliberately does **not** restore `bootonce-bootloader`, which could request bootloader mode on next startup.

Full original `misc` backups are retained, but **do not blindly restore a stale full-partition image** if other contents have changed. If the watch fails to boot, use your pre-verified FDL2 rollback path. Firmware updates, recovery changes, and bootloader BCB consumers may change behavior.

## Scope

This method deliberately boots Android through the **recovery partition** on a locked-bootloader unit. It does not unlock the bootloader, modify normal `boot`, remove verified boot, or guarantee compatibility with other S100 variants.
