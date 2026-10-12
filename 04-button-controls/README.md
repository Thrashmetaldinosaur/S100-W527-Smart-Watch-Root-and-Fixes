# 4 — Confirmed button controls (S100 W527 / Android 13)

**Tested on one S100 after ordinary reboot.** No LSPosed framework hook is required. Both handlers watch the existing `gpio-keys` input device `/dev/input/event6` through Magisk service scripts.

| Physical key | Single | Double | Hold | With display off |
| --- | --- | --- | --- | --- |
| Top, `KEY_F9` (67) | Back | Toggle portrait ↔ 90° clockwise | Volume panel | Retains existing Android wake behavior |
| Bottom, `KEY_POWER` (116) | Original sleep action | Return to CloX watch face | Original Android power menu | Original wake action |

**Use the final version of each handler:** [Top button](top-button/README.md) / [Bottom button](bottom-button/README.md).

The original module ZIPs used during experiments did **not** always install all their files. This repo provides the **final handler source + Magisk service files**, rather than claiming that the old test ZIPs are finished installers. Files here reconstruct the precise text edits used on the working watch; they are **not byte-level on-device readbacks**.

**Compatibility:** Confirm `getevent -lp /dev/input/event6` contains both `KEY_F9` and `KEY_POWER` before installing. Other S100 variants may differ. The handlers depend on root, `getevent`, `input`, shell and the Android 13 command interfaces used on the tested GSI. They are not universal wearable fixes.

For a different watch, preserve its original power-button logic and recovery method. To disable either module, use Magisk's Modules UI or add the `disable` marker to only its module folder and reboot. Do not disable all of Magisk or LSPosed merely to stop these scripts.

**The final bottom-button fix supersedes the unsuccessful LSPosed power-policy experiment**; that hook is not part of the release.
