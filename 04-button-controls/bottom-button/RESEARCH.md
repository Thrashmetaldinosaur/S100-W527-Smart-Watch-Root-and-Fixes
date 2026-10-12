# Bottom POWER button — Android 13 research (not installed)

**Status:** experimental LSPosed design. No change to the watch's bottom button yet. Track work in [issue #1](https://github.com/Thrashmetaldinosaur/S100-W527-Smart-Watch-Root-and-Fixes/issues/1).

## Required mapping

| Gesture | Action |
| --- | --- |
| Single (screen on) | Home / CloX |
| Double (screen on) | Screen off |
| Long | Preserve Android power menu |
| Single (screen off) | Preserve native wake |

## Verified on S100 W527

- crDroid 9.3 Android 13 (SDK 33), Magisk 30.7 and a running LSPosed daemon.
- POWER is key code 116 on `/dev/input/event6`.
- `mShortPressOnPowerBehavior=SHORT_PRESS_POWER_GO_TO_SLEEP`
- `mDoublePressOnPowerBehavior=MULTI_PRESS_POWER_NOTHING`
- `mLongPressOnPowerBehavior=LONG_PRESS_POWER_GLOBAL_ACTIONS`
- Changing `settings put global power_button_short_press 4` did **not** change the policy. It was reverted.

## Researched approach

Android's `com.android.server.policy.PhoneWindowManager` has native multi-press detection and long/wake handling. A separate `getevent` listener cannot suppress the default sleep action.

A small **System Framework-scoped LSPosed hook** could:
1. Set the framework's short-press behavior to **Home (4)**.
2. Enable Android's existing double-press recognition using a nonzero double-press behavior (the placeholder never executes if the hook succeeds).
3. Intercept *only* the double-press `powerPress` callback and call the framework's internal sleep helper.
4. Leave native wake handling, power-button long press, and the physical GPIO mapping alone.

Source inspection confirms Home=4 and that the stock double-press options do not include sleep:
- [AOSP power-button config](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/res/res/values/config.xml)
- [AOSP PhoneWindowManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/services/core/java/com/android/server/policy/PhoneWindowManager.java)
- [LSPosed module scope](https://github.com/LSPosed/LSPosed/wiki/Module-Scope)

## Blocking checks before installation

- Pull and inspect the **exact** S100 `/system/framework/services.jar` via USB ADB and verify method signatures.
- Build and independently inspect the LSPosed APK.
- Confirm System Framework hook scope and a **tested rollback path** before first enable (a system_server hook can cause a boot loop).
- Verify sleep/wake, single, double and long gestures on-device.

Do **not** use kernel key remaps or replace `services.jar`. This document is research, not a ready-to-install module.
