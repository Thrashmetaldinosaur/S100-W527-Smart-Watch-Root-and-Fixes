# S100 Camera Flip Fix v1.0-test1

**Experimental LSPosed module, not tested on physical watch.** For S100/W527 Android 13 stock `com.android.camera2`.

## Behavior
- Adds small **↻ 180° OFF/ON** control in Photo mode at the upper right
- ON rotates live preview TextureView by 180° and adjusts `CameraProxy.setJpegOrientation(int)` by 180°
- Remembers toggle between camera launches; no global display rotation; no system APK replacement
- Photo mode only; video unsupported
- Based on analysis of the user's stock `Camera2.apk`; output must be verified on actual S100

## Installation
1. Download `app-debug.apk` from GitHub Actions artifact **S100-Camera-Flip-Fix-v1.0-test1**.
2. Install using `adb install -r app-debug.apk` on S100.
3. In LSPosed, activate module **S100 Camera Flip Fix**, scope only **Camera / com.android.camera2**.
4. Force-stop Camera / reboot if required, then relaunch.
5. In Photo mode try ↻ 180° OFF for outward-facing, ON for selfie. Take and inspect saved photos.

## Rollback
Turn toggle OFF, or disable module in LSPosed and force-stop camera. Uninstall with `adb uninstall com.s100.cameraflip`.

## Build
GitHub Actions workflow builds debug APK using Java 17, Android SDK 35, Gradle 8.9, and compile-only Xposed API 82.
Original stock camera APK is not redistributed.
