# S100 W527 Bottom Power Button — experimental LSPosed source v0.2

**NOT yet built or tested on the S100. Do not enable without a recovery plan.**

## Verified against supplied S100 services.jar

- Android 13 / crDroid 9.3 SDK 33
- `PhoneWindowManager.init(Context, WindowManagerPolicy.WindowManagerFuncs)`
- `powerPress(long, int, boolean)` for count and whether the gesture began non-interactively
- `sleepDefaultDisplayFromPowerButton(long,int)` returning boolean
- `getMaxMultiPressPowerCount()`, `shortPressPowerGoHome()` and two mutable power behavior fields

Stock policy: single sleep, double nothing, long GLOBAL_ACTIONS. Writing `settings put global power_button_short_press 4` did not change the policy.

## Target behavior

| Bottom gesture | Planned action |
| --- | --- |
| Single while awake | Home / CloX |
| Double while awake | Sleep display |
| Long | Original Android power menu |
| When sleeping | Original hardware wake |

The LSPosed hook registers a double-press handler, then overrides short/double policy **after** `PhoneWindowManager.init`. This lets Android's native `PowerKeyRule` retain key timing and native wake and long-press behavior. Double presses originating while the screen was off are deliberately ignored after waking. No keylayouts, partitions or system JARs are modified.

## Build / testing

Build with Android Studio and JDK 17 using `:app:assembleDebug` or the GitHub Actions workflow. The Xposed API 82 dependency is compile-only. Successful compilation **does not prove safe operation in system_server**. Before enabling in LSPosed (scope: System Framework), have tested USB/FDL2 recovery. A framework hook error can cause boot loops and wireless ADB may not be available.

First test: verify power menu on long press, Home on single, display sleep on double, wake from asleep, then reboot. Confirm `dumpsys window policy` displays short Home and double nonzero.
