# Top F9 button — S100 handler v0.3 (device tested)

The **top physical key** is `KEY_F9` on `/dev/input/event6` on the tested **S100 W527 / crDroid 9.3 Android 13**.

| Gesture | Action |
| --- | --- |
| Single press | Android BACK (`input keyevent 4`) |
| Double press | Toggle screen rotation **0 ↔ 1** (portrait / 90° clockwise) |
| Long press (approximately 700 ms) | Show media volume controls |

**Current release: v0.3 — confirmed by the owner after reboot.** Single, double and long presses were all reported working on the watch. The earlier v0.2 sometimes missed single taps, despite the kernel delivering matching `KEY_F9 DOWN/UP` events.

## What changed from v0.2

- Removed the asynchronous 40 ms `valid` marker check that could race the release event and discard legitimate taps.
- Single-press processing occurs directly on `UP`, subject to the existing double-press decision window.
- Kept the **350 ms** pending single-press delay for double-press detection; single presses are *not* instantaneous.
- Preserved the double-press 0↔1 rotation toggle, long-press volume command and fail-closed `KEY_F9` device check.
- Logs moved to `/data/local/tmp/s100_f9_v03.log`; state now uses `/data/local/tmp/s100_f9_v03_state`. The Magisk `service.sh` launch loop is unchanged.

## Included files

- [`f9-handler.sh`](f9-handler.sh): working v0.3 source, copied from the script successfully installed on the test S100.
- [`f9-handler-v0.2-archived.sh`](f9-handler-v0.2-archived.sh): previous repository source for reference and rollback.
- [`service.sh`](service.sh): Magisk late-start restart loop (unchanged).
- [`module.prop`](module.prop): v0.3 metadata for future module installs.

## Upgrade an existing S100

**Requires Magisk root and working ADB.** Verify your button maps to `KEY_F9` on `event6` before installing. Only perform on the same verified S100 hardware/ROM.

1. Save a backup of `/data/adb/modules/s100_top_buttons/f9-handler.sh` (e.g. `f9-handler.sh.v02.bak`).
2. Replace *only* `f9-handler.sh` in that module directory with the published v0.3 script, set mode `755`, and check `sh -n` succeeds.
3. Reboot so Magisk `service.sh` starts the new handler. Confirm single/double/hold gestures and inspect the v0.3 log.

Do **not** use the archived v0.2 script on new installs unless rolling back. The `module.prop` in this repository is updated to v0.3; an existing user's Magisk app might still display the old module version if only the handler script was replaced.

Example verification (use the appropriate ADB device selector):

```sh
adb shell "su -c 'sh -n /data/adb/modules/s100_top_buttons/f9-handler.sh'"
adb shell "su -c 'tail -n 30 /data/local/tmp/s100_f9_v03.log'"
adb shell "su -c 'getevent -lp /dev/input/event6 | grep KEY_F9'"
```

**Rollback:** Restore the backed-up `f9-handler.sh.v02.bak` to `f9-handler.sh` and reboot, or disable only the `s100_top_buttons` Magisk module. If rotation remains locked, reset it with `cmd window user-rotation lock 0`.

**Scope:** This is the physical top-key handler, not the separate YouTube LSPosed BACK-to-mini-player fix. No changes to the bottom power key or SystemUI are included.
