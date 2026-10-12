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

## B. CloX square viewport (confirmed)

The S100 CloX launcher was initially constrained by a **portrait orientation** declaration. The tested custom CloX APK changed its MainActivity's `android:screenOrientation` to `unspecified`, restoring use of the full square **480×480** viewport. The user also disabled Android's **Boosted Colors** setting to correct the display's oversaturated colors.

The app itself and a redistributed APK are **not included**. Android app modifications can be version-specific; back up the original APK and respect its licensing. The changes above are documented for reproduction, not an installer for an arbitrary CloX build.

## Still open

- Some third-party apps may continue to place controls beyond the display's physical round edges. Status-bar padding does **not** solve general app-specific clipping.
