# S100 Camera Flip Fix v1.1-test2

**Experimental LSPosed module. The toggle and rotation still need to be tested on the physical S100/W527 Android 13 watch.** Targets the user's stock `com.android.camera2` only.

## Changes from v1.0-test1
- The LSPosed hook was confirmed to load, but the toggle never appeared in the 480×480 camera UI hierarchy.
- Adds a reliable `Instrumentation.callActivityOnResume(Activity)` hook, with optional camera-specific retry points.
- Attaches the toggle directly to the confirmed `activity_root_view` (`FrameLayout`); uses `android:id/content` as fallback.
- Removes the fragile `mCurrentModule == PhotoModule` condition that could prevent the button from appearing during startup.
- Places the toggle at upper left, away from the stock Options and Shutter controls.
- Adds LSPosed module logs for resume events, button attachment, toggles, preview application and errors.

## Functionality
- Shows **↻ 180° OFF / ON** in the camera.
- When ON, rotates the live preview `CameraAppUI.mTextureView` 180° and modifies `CameraProxy.setJpegOrientation(int)` by +180°.
- The setting is persisted in stock camera's private preferences; no system APK replacement.
- Video orientation correction is not supported; test photographs and saved JPEG orientation separately.
- **Do not assume saved image rotation is correct without taking and inspecting a real photo.**

## Installation on the S100 (USB ADB)
1. Download `app-debug.apk` from the GitHub Actions artifact **S100-Camera-Flip-Fix-v1.1-test2**.
2. This debug APK is signed independently of v1.0; Android may reject an in-place upgrade due to a different signing key. Disable the old module in LSPosed and uninstall `com.s100.cameraflip`, then install the new APK.
3. If normal `adb install` returns `INSTALL_FAILED_VERIFICATION_FAILURE`, push APK to `/data/local/tmp/` and install with `su -c 'pm install -r -t /data/local/tmp/s100-camera-flip.apk'`, as already verified on this S100. No package-verifier settings need to be changed.
4. In LSPosed: enable **S100 Camera Flip Fix**, scope it to **Camera / com.android.camera2** only, and force-stop/relaunch Camera (reboot if required).
5. Confirm the upper-left toggle appears. Toggle ON and OFF, test live preview, capture a photo and inspect its orientation.

## Diagnostics
LSPosed Manager > Logs > Modules, or via root:
```sh
cat /data/adb/lspd/log/modules_*.log | grep 'S100CameraFlip'
```
Expected log events include `Activity resumed`, `Button ATTACHED`, `Button visible`, `Toggle clicked` and `JPEG orientation` during capture.

## Rollback
Turn the toggle OFF; or disable module in LSPosed and force-stop camera, then uninstall `com.s100.cameraflip`.

## Build
GitHub Actions compiles an Android debug APK with Java 17, Android SDK 35, Gradle 8.9 and compile-only Xposed API 82. The original stock `Camera2.apk` is not redistributed.
