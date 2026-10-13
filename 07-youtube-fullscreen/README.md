# S100 W527 — YouTube Fullscreen Fix v1.0

**Watch-tested release**, based on the confirmed working v0.3.2 implementation. This is an **LSPosed module**, not a Magisk firmware module and not a modified YouTube APK.

## Compatibility

- Watch: **S100 / XTQ_Watch / XinTaiQi W527**, 480 × 480 screen.
- Operating system: **Android 13**, crDroid 9.3 GSI, rooted with Magisk and LSPosed.
- YouTube: **official com.google.android.youtube version 21.40.161** (version check in installer).
- LSPosed scope: **YouTube only** (`com.google.android.youtube`).

Other YouTube releases and other watches have not been verified.

## What it fixes

- Suppresses YouTube's unwanted landscape orientation and keeps the watch upright.
- Displays an uncropped, correctly proportioned **480 × 270** 16:9 video centered vertically in the **480 × 480** watch screen.
- Resolves compositor occlusion by placing the existing `SurfaceView` above YouTube's original window during fullscreen; restores original z-order when minimized.
- Provides an attached bottom-band panel with **Play/Pause, −10s, +10s, Exit**, and a **draggable progress bar**.
- Panel and progress bar **auto-hide after 4 seconds**, then reappear when tapping the lower black band.
- Mirrors real YouTube **CC captions into the lower black band**, while controls are hidden.
- Restores YouTube's original mini-player video, dimensions and behavior when minimized.

Captions come from the app's runtime caption drawing path. The module does not download subtitle transcripts or change the original video stream. Exact subtitle styling may vary; for tested video, subtitle timing and repeated fullscreen/minimize cycles were confirmed working.

## Install or update on the tested Windows PC

Prerequisites: rooted watch, active LSPosed, Java 17, Android SDK Platform 34, USB ADB. Ensure the official YouTube 21.40.161 is installed. This project reuses the package ID of the preceding prototypes so `adb install -r` updates the same module without a separate uninstall.

1. Extract this ZIP into your Downloads directory. The extracted folder is `S100-YouTube-Fullscreen-Fix-v1.0`.
2. Open PowerShell and run:

   ```powershell
   & "$env:USERPROFILE\Downloads\S100-YouTube-Fullscreen-Fix-v1.0\BUILD-INSTALL-AND-TEST.ps1"
   ```

3. In **LSPosed > Modules**, enable **S100 YouTube Fullscreen Fix** and ensure the scope is **only YouTube**.
4. If the module is being enabled for the very first time, reboot or restart YouTube after applying LSPosed's scope instructions. Otherwise the installer launches YouTube without the Monkey utility.
5. Enter fullscreen with captions enabled. Tap the lower black band for controls; wait for them to hide.

**Important:** Never launch YouTube on this watch with `adb shell monkey ...`. Use `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.google.android.youtube` as the included scripts do. Monkey testing was associated with Developer Options becoming inaccessible on this watch; its exact causal role was not isolated.

## GitHub repository layout

Place this whole source folder under **`07-youtube-fullscreen/`** in the [S100 community fixes repository](https://github.com/Thrashmetaldinosaur/S100-W527-Smart-Watch-Root-and-Fixes). Keep `app/`, Gradle files, and PowerShell scripts together. Do not add official YouTube APKs, proprietary firmware, keystores, or ADB/debug logs to the repository.

## Optional GitHub publication

`PUBLISH-TO-GITHUB.ps1` clones the existing S100 repository into a fresh temporary directory and pushes a **new feature branch** with this folder at `07-youtube-fullscreen/`, plus a link in the root README. It prints a GitHub link for creating/reviewing a pull request. Requires Git for Windows, your configured Git author identity, and GitHub authentication. **It never pushes directly to `main`.** Run it only after reviewing the files and deciding to publish.

## Rebuilding / release APK

Run `BUILD-WINDOWS.ps1`, then get the compiled APK at `app\build\outputs\apk\debug\app-debug.apk`. Only source code is distributed; the ZIP does **not** contain a precompiled or signed release APK. The produced debug APK is signed using the local debug keystore; when updating an installed module, its signing key must match that of the original install. `adb install -r` will fail if the key differs. A source build is not proof of compatibility with other devices.

## Rollback and troubleshooting

- Disable this module in LSPosed, force-stop YouTube, and launch it again normally; reboot if LSPosed prompts you.
- Or reinstall the previously known-good **v0.3.2** build with the same signing key.
- If captions stop after a YouTube update, check the exact YouTube version; YouTube's internal subtitle renderer is not a stable public API.
- If a mini-player or progress bar fails, save `Downloads\S100-YT-V100-Test.txt` and compare with a working v0.3.2 install.

## Release notes

v1.0 is a packaging and logging cleanup of **v0.3.2**. It does not change the tested fullscreen layout, layer ordering, media-session controls, caption grouping, or mini-player recovery. Persistent logging of subtitle text is disabled by default to reduce noise and avoid retaining transcribed speech in diagnostics.
