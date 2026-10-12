# Top F9 button — tested S100 handler

- **Single press:** Back (`input keyevent 4`).
- **Double press:** Toggle `user_rotation` **0 ↔ 1 only** (portrait and 90° clockwise). The earlier four-orientation cycle was replaced and the two-way version was verified on the device after reboot.
- **Long press (~700 ms):** Show media volume control via `cmd media_session volume --show --stream 3 --adj same`.
- **Debounce:** The handler ignores button presses shorter than about 40 ms.

## Published source

- [`f9-handler.sh`](f9-handler.sh) — final two-orientation handler logic.
- [`service.sh`](service.sh) — Magisk late-start restart loop.
- [`module.prop`](module.prop) — Magisk module metadata, ID `s100_top_buttons`.

These files are the tested base v0.2 module with the *exact* user-verified four-way-to-two-way rotation substitution applied. They are reconstructed from saved test files and command history; they were not downloaded afresh from the watch.

## Reproducing and verifying

On the tested unit the module lived at `/data/adb/modules/s100_top_buttons/`, containing `module.prop`, `service.sh`, and `f9-handler.sh`. Source files must be copied to that folder under root and shell scripts made executable. Confirm all three files are present **before reboot**; a prior ZIP-based installation on the bottom module omitted an auxiliary script and needed manual repair.

Example checks, using `adb` for a single device:

```sh
adb shell "su -c 'ls -l /data/adb/modules/s100_top_buttons'"
adb shell "su -c 'sh -n /data/adb/modules/s100_top_buttons/f9-handler.sh'"
adb shell "su -c 'tail -n 25 /data/local/tmp/s100_f9_v02.log'"
```

Expected log events: `SINGLE -> BACK`, `DOUBLE -> ROTATE 0 to 1` then `1 to 0`, and `HOLD -> VOLUME PANEL`. A reboot was tested successfully.

**Rollback:** disable the `s100_top_buttons` Magisk module only. Rotation may remain locked after disable; use `cmd window user-rotation lock 0` to reset if needed.
