# S100 / W527 Smart Watch — Root and Fixes

Community notes for the S100 full-Android smartwatch (Unisoc UWS6152 / W527). Tested on **crDroid 9.3 / Android 13** on one device. Firmware variants may differ.

## Available fix

- **[Automatic Magisk root via recovery BCB](automatic-magisk-root/README.md)** — tested: normal reboot and cold power-on enter a previously rooted Magisk recovery automatically. Includes a PowerShell utility, verification, backups and a disable option.

**Prerequisite:** This BCB fix does **not** root an unrooted watch. The tested S100 already had a verified, working Magisk 30.7-patched recovery image. See the fix-specific README before proceeding.

## Safety

- Do not use on another S100 firmware/partition layout without independent verification.
- Keep full original partition backups and verify a working Unisoc FDL2 recovery path before changing `misc`.
- No boot, recovery, or VBMeta images, private signing keys, device IDs or user backups are distributed here.
- The automatic-root **enable** configuration was tested on one device; the **disable** operation is documented but has not yet been device-tested.

Other S100 fixes can be added to this repository separately.