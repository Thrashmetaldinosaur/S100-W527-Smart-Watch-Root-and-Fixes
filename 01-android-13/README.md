# 1 — Install Android 13 (crDroid 9.3 GSI) on the S100 W527

> **Evidence-based device notes, not a one-click installer.** This records the successful S100 installation performed in October 2026. It has **not** been independently repeated on a second unit. Do not flash images using this guide until your own partition dumps, loaders, image formats and recovery options are verified.

**Next: [2 — Magisk root](../02-magisk-root/README.md)**

## Tested device

- XinTaiQi/XTQ **S100 W527** smartwatch; Android reports `XTQ_Watch`.
- Unisoc UWS6152 / W527; 480 × 480 display.
- Original firmware identifier: `DW_W527_FW13FBA_V35_XTQ_S100_J0_GB_CN_ZX_W4.0T_4.4V_256X24B_CN_480X480_V04_20260525_1642`.
- Original Android 9; flashed crDroid 9.3 Android 13 **ARM64, A-only, vanilla/no GApps** GSI.
- Tested GSI filename: `crDroid-9.3-arm64_bvN-Unofficial-002.img`, raw EXT4, **2,967,568,384 bytes**. The prepared flash file was named `system_A13.img`.
- Bootloader remained locked with verified boot enabled. No unlock was necessary for the tested FDL2 route.

This is **not** a general procedure for every device called S100. Hardware/firmware variants may differ.

## Prerequisites

1. Windows computer with functional Unisoc/Spreadtrum USB download drivers, ADB and the `spd_dump` utility. Tested tool identification: stable branch, SHA1 `2a202404a7512026d146b62624f089aceb408520`.
2. Valid signed W527 FDL loaders **for your own firmware**, named `fdl1-sign.bin` (47,076 bytes on tested unit) and `fdl2-sign.bin` (572,180 bytes on tested unit).
3. Full **verified offline backups of your own device**, especially `boot`, `recovery`, `misc`, `vbmeta`, `system`, `vendor`, and any relevant NV/calibration partitions. A full GPT dump is preferable. No firmware dumps are included here.
4. A suitable uncompressed **raw** ARM64/A-only GSI; do not flash an `.xz`, `.gz`, or Android sparse image as if it were raw EXT4.
5. The original raw EXT4 vendor partition, tools capable of safely editing the vendor EXT4 filesystem offline, and EXT4 consistency checks.
6. A known-working method of entering **BootROM/FDL2**, and a way to recover if startup fails.
7. **Back up personal data**. Installing Android 13 required a `userdata` wipe and destroys local data.

## Recorded partition map (confirm yours before continuing)

| Partition | Observed size | Purpose |
|---|---:|---|
| `boot` | 35 MiB | Kept original for Android 13 |
| `recovery` | 40 MiB | Kept stock until rooting in step 2 |
| `system` | 4,500 MiB | GSI destination |
| `vendor` | 400 MiB | Stock vendor with fstab modification |
| `product` | 100 MiB | Not flashed in this procedure |
| `vbmeta` | 1 MiB | Not modified |
| `misc` | 1 MiB | Recovery/wipe BCB |
| `userdata` | 54,245 MiB | Wiped on first Android 13 boot |

eMMC, 42 partitions, non-VAB/single-slot layout. The exact partition IDs and sizes must be confirmed against **your** device's GPT before writing.

## 1. Connect to BootROM / FDL2

On the tested watch, power it completely off and unplug USB. Start `spd_dump` on the PC; when it prints `Waiting for dl_diag connection`, hold the **top button** and connect USB. Release it once the connection succeeds.

The tested loader chain used:

```powershell
$spd  = "C:\path\to\spd_dump.exe"
$fdl1 = "C:\path\to\fdl1-sign.bin"
$fdl2 = "C:\path\to\fdl2-sign.bin"
& $spd --wait 180 fdl $fdl1 0x5500 fdl $fdl2 0x9efffe00 exec
```

Interactive FDL2 should show the GPT and the `FDL2 >` prompt. The tested firmware printed `FDL2: incompatible partition` **before** a successful `EXEC FDL2`; it was not fatal on that test. Do **not** ignore a failure to execute FDL2 or read your partition map.

## 2. Prepare the Android 13 system image

The successfully flashed file, `system_A13.img`, contained the raw `crDroid-9.3-arm64_bvN-Unofficial-002.img` EXT4 GSI, **2,967,568,384 bytes**. Confirm it is decompressed, raw EXT4 and smaller than the 4,500 MiB target partition. Do not pad it unnecessarily or flash it to any partition other than `system`.

The image was **not hosted in this repository**. Obtain and independently verify an appropriate build; similar filenames do not prove byte-for-byte equivalence. A trustworthy upstream download/checksum for this exact unofficial build is **not yet documented** here.

## 3. Prepare the Android 13 vendor image

Flashing the GSI alone was insufficient: the stock vendor configuration still required forced filesystem encryption incompatible with the test installation.

The successful modified vendor was made from the watch's **own 400 MiB raw EXT4 `vendor.bin`**. The file `/etc/fstab.uws6152_1h10_go` was patched in two occurrences by removing the exact option:

```text
,fileencryption=aes-256-xts
```

The result was `vendor_A13_noencryption.bin`, **419,430,400 bytes**. The preparation preserved the EXT4 image's size and metadata; an `e2fsck` consistency check passed.

**Important:** The original `prepare_vendor.py` script is not available in this public repository. This step must currently be reproduced with suitable offline EXT4 editing tools and verified before flashing. Do **not** hex-replace arbitrary bytes, flash vendor from another watch, or remove other fstab options by guesswork.

## 4. Flash `system` and the prepared `vendor`

These interactive FDL2 commands were executed successfully on the tested device (adjust file paths):

```text
FDL2 > w system C:\path\to\system_A13.img
FDL2 > w vendor C:\path\to\vendor_A13_noencryption.bin
```

Successful output included:

```text
Write Part Done: system, target: 0xb0e18000, written: 0xb0e18000
Write Part Done: vendor, target: 0x19000000, written: 0x19000000
```

**Verify both partitions after flashing, before resetting.** The test used:

```text
FDL2 > path C:\path\to\readbacks
FDL2 > r vendor
FDL2 > read_part system 0 0xb0e18000 system_readback.img
```

Compare each readback with the respective input image using SHA-256 (including the expected read length). Preserve untouched `boot`, `recovery` and `vbmeta`.

## 5. Wipe userdata through stock recovery BCB

A first boot on this device required a real userdata wipe to resolve the previous encryption state. **THIS DELETES USER DATA.**

The tested procedure did not directly write a new image to `userdata`. Instead, with a full verified 1 MiB backup of `misc`, it prepared a modified BCB request:

- BCB command, at offset **0**: `boot-recovery` (NUL-terminated in a 32-byte field).
- BCB recovery instructions, at offset **64**: `recovery\n--wipe_data\n--reason=enablefilecrypto_failed\n`.
- The rest of `misc` was retained from the backup.

The prepared file was called `misc_autowipe.bin`. The tested full-image SHA-256 was:

```text
27A40F1347516B5A1A2F08E6BEB0A403F51F364E44EF2D1B47E579F08457BF7A
```

This hash documents **one device-specific misc image**, **not** a file to copy to other watches. Never flash another unit's `misc_autowipe.bin`: it may contain unrelated settings. An interactive `w misc ...\misc_autowipe.bin` was followed by `r misc`, a matching complete readback, then `reset`. Recovery performed the wipe, after which crDroid Android 13 started.

**Safer alternative when available:** If your existing recovery offers an equivalent full data/factory wipe, prefer that over directly editing `misc`. Whatever method you use, confirm exactly what will be erased before proceeding.

## 6. Verify Android 13

Once Android boots, verify that the GSI actually starts:

```powershell
adb devices
adb shell getprop ro.build.version.release
adb shell getprop sys.boot_completed
adb shell getprop ro.product.cpu.abi
```

On the working test setup Android reported release `13` and `sys.boot_completed=1`. The final watch UI used CloX; UI customization is outside this installation guide.

**Next: [2 — Root with Magisk recovery](../02-magisk-root/README.md).**

## Failure/rollback precautions

- Restore original `system` and `vendor` **from your own verified backups** if the GSI will not boot. A prior-userdata wipe cannot be undone without backups.
- Do not substitute a patched `boot` image. On this watch, an experimental boot-ramdisk approach later stopped at the NOEMIE startup screen; original `boot` had to be restored through FDL2.
- Do not disable AVB or change `vbmeta` based on generic phone instructions.
- There is no warranty, broad-variant compatibility testing, or guarantee that vendor modifications will transfer to a different W527 firmware.
