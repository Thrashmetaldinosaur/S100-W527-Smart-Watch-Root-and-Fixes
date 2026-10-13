# S100 W527 Smart Watch — Android 13, Root, and Fixes

A step-by-step record of upgrading the **S100 W527 / Unisoc UWS6152** smartwatch from Android 9 to **crDroid 9.3 Android 13**, rooting through Magisk recovery, and making that root selection persist across reboots and power-offs.

**Read in this exact order:**

## 1. [Android 13 installation](01-android-13/README.md)

Flash the ARM64 A-only crDroid 9.3 GSI, prepare the stock vendor image for the Android 13 encryption configuration, and wipe userdata using the verified recovery path.

**Status:** Successful on the test S100. Requires original firmware backups and advanced EXT4 image preparation; the exact unofficial GSI download and vendor-preparation script have not been republished here.

## 2. [Magisk root](02-magisk-root/README.md)

Understand the S100's **recovery-based Magisk 30.7** setup and verification. The stock normal `boot` image has no ramdisk; the successfully rooted setup uses the separate patched recovery.

**Status:** Root working on the test S100. **Recovery image signature rebuilding is not yet provided as a general-purpose installer.** Do not flash raw Magisk output without appropriate firmware-specific verification.

## 3. [Root permanence](03-root-permanence/README.md)

Make a **previously working Magisk recovery** the default boot choice by writing `boot-recovery` to the Bootloader Control Block in `misc`. Includes a [PowerShell Status / Enable / Disable utility](03-root-permanence/S100-BCB-AutoRoot.ps1) with full `misc` backup and readback verification.

**Status:** Automatic root verified after a normal reboot **and a complete power-off** on the test S100. The Disable operation is documented but not yet tested on hardware.

---

## Hardware tested

- **Model:** XinTaiQi/XTQ S100 W527 (`XTQ_Watch`).
- **SoC:** Unisoc UWS6152/W527.
- **Original OS:** Android 9; tested upgrade: crDroid 9.3 ARM64 A-only Android 13.
- **Bootloader:** locked; verified boot enabled.
- **Storage:** eMMC, single slot; `boot` 35 MiB, `recovery` 40 MiB, `system` 4,500 MiB, `vendor` 400 MiB, `misc` 1 MiB.
- **FDL2 entry on tested unit:** power off, start loader wait, hold **top** button while connecting USB.

**Compatibility warning:** Other watches branded S100 may contain different boards, partitions or signed firmware. Verify your own exact hardware and backups. All three stages can cause data loss or loss of boot if misapplied. This repository contains **no downloadable firmware images, encryption keys, or private signing material**.

### Project history

The permanent-root notes previously lived under [`automatic-magisk-root/`](automatic-magisk-root/README.md). That older path remains as a compatibility link; the numbered path **03-root-permanence** is now canonical.

## 4. [Button controls — working](04-button-controls/README.md)

**Status: confirmed after reboot.** Top-key handler **v0.3** resolves intermittent missed BACK presses while retaining double-press portrait ↔ 90° rotation and long-press volume panel.  Bottom key: single sleep, double **CloX watch face**, long power menu, press asleep wakes. The final bottom fix is a passive Magisk KEY_POWER listener; **the previous LSPosed experiment was discarded**. Final script sources and module service files are included; earlier files were reconstructed from tested patches, while the top-key v0.3 source was preserved from the successfully installed test script.

## 5. [Round-display and CloX fixes — working](05-display/README.md)

**Status: confirmed and persistent after reboot.** Two fabricated SystemUI overlays change status-bar start/end padding from **8 dp to 64 dp**, preventing clock and status icons from falling behind the physical round corners. CloX's portrait-orientation constraint was separately changed to enable the 480×480 square viewport; Boosted Colors was disabled for natural color.

## 6. [Google Play services and Store — working](06-google-play/README.md)

**Status: Google Play confirmed by the owner.** Android 13 ARM64 LiteGapps (July 9, 2026) was installed after confirming the vanilla GSI lacked GMS, GSF and Play Store. This does **not** establish Wallet, Play Protect certification or all Google sync features.

---

### Outstanding checks

- Other apps can still clip at round edges; the tested padding fix targets **SystemUI status bar only**.
- Play certification, Wallet and full background/sync behavior have not been verified.
- S13 features such as YouTube square-screen fixes, wrist-to-wake and performance adjustments are candidates for evaluation, **not confirmed S100 fixes**.
- The original Android 13 GSI download, vendor-preparation script and full recovery-signing instructions are not all present; the initial installation/root stages remain advanced device-specific notes.

### Publishing policy

Only publish fixes **after they work on the S100**. Provisional trials (including the unsuccessful experimental power-key LSPosed code) are not the released implementation. No Google apps, proprietary CloX APKs, firmware images or signing keys are redistributed.

## YouTube fullscreen on the S100 (Android 13)

See [07-youtube-fullscreen](07-youtube-fullscreen/README.md) for the watch-tested LSPosed YouTube Fullscreen Fix v1.0: centered 16:9 playback, subtitles in the lower letterbox band, draggable seek bar, auto-hiding controls, and restored mini-player behavior. Scoped to official YouTube 21.40.161 only.
