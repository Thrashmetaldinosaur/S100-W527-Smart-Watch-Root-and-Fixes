# Bottom POWER button — tested double press to CloX watch face

This replaces the failed **LSPosed framework override** with a lightweight Magisk listener. The actual on-device behavior was verified by the owner after reboot.

| POWER gesture | Outcome |
| --- | --- |
| Single (screen on) | Native Android display sleep — unchanged |
| Double within 500 ms | Watch face via **Home → wait 250 ms → Back** |
| Hold | Native Android power menu — unchanged |
| Screen off | Native wake action — unchanged |

The physical input remains `KEY_POWER` (116) at `/dev/input/event6`; no `keylayout`, `services.jar` or boot partition changes were required.

## Why this exact sequence

A generic `input keyevent 3` (Home) could open CloX's app drawer. A direct Android launch of `com.ailife.clox` merely delivered an intent to the already running launcher, leaving the drawer open. The successful workaround was:

```sh
input keyevent 3
sleep 0.25
input keyevent 4
```

User tested this sequence from **Settings**, the **CloX app drawer** and the watch face itself, and confirmed that the final bottom-button module works after reboot.

Android's competing double-power Camera gesture also needed to be disabled:

```sh
adb shell "su -c 'settings put secure camera_double_tap_power_gesture_disabled 1'"
adb shell settings get secure camera_double_tap_power_gesture_disabled
# Expected: 1
```

The original value on the test watch was `null`. To restore that original state:

```sh
adb shell "su -c 'settings delete secure camera_double_tap_power_gesture_disabled'"
```

## Published source

- [`power-double-home.sh`](power-double-home.sh) — passive KEY_POWER listener, kernel timestamps, 35ms debounce, 500ms double window and 700ms long-press exclusion.
- [`service.sh`](service.sh) — Magisk late-start background loop.
- [`module.prop`](module.prop) — module ID `s100_bottom_double_home`.

These were reconstructed from the original tested Magisk v0.1 ZIP and the precise validated Home+Back patch; **not fresh readbacks from the watch**. The older ZIP did not reliably install its auxiliary handler file. For a new installation, verify the actual module directory contains all files before rebooting; do not reuse that incomplete installer blindly.

On the working device they reside under `/data/adb/modules/s100_bottom_double_home/`. Validate with:

```sh
adb shell "su -c 'ls -l /data/adb/modules/s100_bottom_double_home'"
adb shell "su -c 'sh -n /data/adb/modules/s100_bottom_double_home/power-double-home.sh'"
adb shell "su -c 'tail -n 25 /data/local/tmp/s100_bottom_double_home.log'"
```

The final successful action is logged as `DOUBLE -> WATCHFACE` (older log lines may still show `DOUBLE -> HOME`). The module does not intercept or block Android's native POWER events.

**Rollback:** Disable **only** `s100_bottom_double_home` in Magisk (or create its `disable` marker and reboot); restoring the double-power camera gesture is a **separate**, optional step. Do not enable the earlier experimental `dev.s100.buttons` LSPosed module alongside this fix.
