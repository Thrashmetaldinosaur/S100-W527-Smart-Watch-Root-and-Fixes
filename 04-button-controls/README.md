# 4 — Confirmed button controls (S100 W527 / Android 13)

**Tested on one S100 after ordinary reboot.** No LSPosed framework hook is required. Both handlers watch the existing `gpio-keys` input device `/dev/input/event6` through Magisk service scripts.

| Physical key | Single | Double | Hold | With display off |
| --- | --- | --- | --- | --- |
| Top, `KEY_F9` (67) | Back | Toggle portrait ↔ 90° clockwise | Volume panel | Retains existing Android wake behavior |
| Bottom, `KEY_POWER` (116) | Original sleep action | Return to CloX watch face | Original Android power menu | Original wake action |

**Use the final version of each handler:** [Top button](top-button/README.md) / [Bottom button](bottom-button/README.md).

**Top button:** v0.3 fixes intermittently missed single BACK presses by removing the earlier asynchronous 40 ms press-validation race. After reboot, the owner confirmed single BACK, double-press rotation and long-press volume all work. The prior v0.2 source is retained under the top-button folder for rollback. The bottom-power-button handler is unchanged.

The original module ZIPs used during experiments did **not** always install all their files. This repo provides the **final handler source + Magisk service files**, rather than claiming that the old test ZIPs are finished installers. Most earlier files were reconstructed from saved edits; the published top-button v0.3 source matches the script installed for the owner's successful hardware test.

**Compatibility:** Confirm `getevent -lp /dev/input/event6` contains both `KEY_F9` and `KEY_POWER` before installing. Other S100 variants may differ. The handlers depend on root, `getevent`, `input`, shell and the Android 13 command interfaces used on the tested GSI. They are not universal wearable fixes.

For a different watch, preserve its original power-button logic and recovery method. To disable either module, use Magisk's Modules UI or add the `disable` marker to only its module folder and reboot. Do not disable all of Magisk or LSPosed merely to stop these scripts.

**The final bottom-button fix supersedes the unsuccessful LSPosed power-policy experiment**; that hook is not part of the release.
