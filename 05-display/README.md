# 5 — Display and rounded-corner improvements

## A. Status-bar clock and icons (confirmed)

**Problem:** The S100 display is physically rounded, but Android 13 reported **0 px rounded-corner radius** and **no display cutout**. At 480×480 px with an effective density override of 160 dpi, SystemUI put its clock and battery indicators close to the physical corners.

The tested crDroid SystemUI resources were:

- `com.android.systemui:dimen/status_bar_padding_start` — **8.0dip** stock
- `com.android.systemui:dimen/status_bar_padding_end` — **8.0dip** stock

The confirmed solution creates two **fabricated overlays** which override these to **64.0dip**. They were checked by resource lookup and the owner confirmed improved placement **and persistence after reboot**.

With ADB and functioning root, on this exact S100 build:

```sh
adb shell "su -c 'cmd overlay fabricate --target com.android.systemui --name s100_corner_status_left64 com.android.systemui:dimen/status_bar_padding_start 0x05 0x00004001'"
adb shell "su -c 'cmd overlay fabricate --target com.android.systemui --name s100_corner_status_right64 com.android.systemui:dimen/status_bar_padding_end 0x05 0x00004001'"
adb shell "su -c 'cmd overlay enable --user 0 com.android.shell:s100_corner_status_left64'"
adb shell "su -c 'cmd overlay enable --user 0 com.android.shell:s100_corner_status_right64'"
adb shell "cmd overlay lookup --user 0 com.android.systemui com.android.systemui:dimen/status_bar_padding_start"
adb shell "cmd overlay lookup --user 0 com.android.systemui com.android.systemui:dimen/status_bar_padding_end"
```

Both lookups should show `64.0dip`. Fabricated overlay registration only needs to be performed once; rerunning a `fabricate` with the same names may report it already exists.

**Reversal (only disable our overlays):**

```sh
adb shell "su -c 'cmd overlay disable --user 0 com.android.shell:s100_corner_status_left64'"
adb shell "su -c 'cmd overlay disable --user 0 com.android.shell:s100_corner_status_right64'"
```

This adjustment changes **SystemUI's status bar**, not arbitrary apps, CloX full-screen view, display resolution or DPI. It does not make Android correctly report hardware corner geometry to every app. The earlier attempt `settings put secure sysui_rounded_content_padding 64` alone did not visibly fix the problem.

## A2. Persistent status-bar padding after reboot (confirmed October 12, 2026)

On the test S100, the two fabricated overlays later disappeared, restoring the original `8.0dip` SystemUI padding. Recreating/enabling the overlays immediately restored `64.0dip` on both sides. To automatically restore them on boot, the owner installed a Magisk `service.d` script at:

```text
/data/adb/service.d/s100-statusbar-corners.sh
```

**Verified on device:** the script passed `sh -n`, manually reported `SUCCESS left=64.0dip right=64.0dip`, and the owner subsequently confirmed that the fix worked after a normal reboot. Its long-term ability to resist overlay removal during an already-running session remains untested.

The installed script waits for `sys.boot_completed`, waits another five seconds, then recreates any missing overlays, enables them, verifies both resources are `64.0dip`, and exits. It retries up to 20 times at 3-second intervals; it does **not** run continuously.

```sh
#!/system/bin/sh
LOG=/data/local/tmp/s100-statusbar-corners.log
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 60 ]; do
    sleep 2
    i=$((i + 1))
done
sleep 5

fix_overlay() {
    name="$1"
    resource="$2"
    overlay="com.android.shell:$name"
    if ! cmd overlay list --user 0 com.android.systemui 2>/dev/null | grep -Fq "$overlay"; then
        cmd overlay fabricate --target com.android.systemui --name "$name" "com.android.systemui:dimen/$resource" 0x05 0x00004001 >/dev/null 2>&1
    fi
    cmd overlay enable --user 0 "$overlay" >/dev/null 2>&1
}

i=0
while [ "$i" -lt 20 ]; do
    fix_overlay s100_corner_status_left64 status_bar_padding_start
    fix_overlay s100_corner_status_right64 status_bar_padding_end
    left=$(cmd overlay lookup --user 0 com.android.systemui com.android.systemui:dimen/status_bar_padding_start 2>/dev/null)
    right=$(cmd overlay lookup --user 0 com.android.systemui com.android.systemui:dimen/status_bar_padding_end 2>/dev/null)
    if [ "$left" = "64.0dip" ] && [ "$right" = "64.0dip" ]; then
        echo "$(date): SUCCESS left=$left right=$right" >> "$LOG"
        exit 0
    fi
    sleep 3
    i=$((i + 1))
done
echo "$(date): FAILED left=$left right=$right" >> "$LOG"
exit 1
```

Install on this rooted test configuration by saving the file with LF line endings and executable mode `755` in `/data/adb/service.d/`. Test manually with `su -c 'sh /data/adb/service.d/s100-statusbar-corners.sh'`, verify the log and resource lookups, then reboot and check for a new `SUCCESS` entry. Do not add a long-running polling service unless there is evidence overlays disappear during an uninterrupted session.

## B. CloX square viewport (confirmed)

The S100 CloX launcher was initially constrained by a **portrait orientation** declaration. The tested custom CloX APK changed its MainActivity's `android:screenOrientation` to `unspecified`, restoring use of the full square **480×480** viewport. The user also disabled Android's **Boosted Colors** setting to correct the display's oversaturated colors.

The app itself and a redistributed APK are **not included**. Android app modifications can be version-specific; back up the original APK and respect its licensing. The changes above are documented for reproduction, not an installer for an arbitrary CloX build.

## Still open

- Some third-party apps may continue to place controls beyond the display's physical round edges. Status-bar padding does **not** solve general app-specific clipping.
