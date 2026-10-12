# 6 — Google Play on crDroid 9.3 Android 13 (confirmed)

On the original vanilla ARM64/A-only GSI, none of these were installed:

- `com.google.android.gms` — Google Play services
- `com.google.android.gsf` — Google Services Framework
- `com.android.vending` — Google Play Store

The owner installed **LiteGapps for Android 13 ARM64** and subsequently confirmed: **“Liteapps works also btw and I have google play.”** Thus basic Play availability is **tested on the watch**. The original installation log / exact installed Magisk module ID has not been preserved here, and specific Google apps beyond Play were not individually verified.

## Package used

- Upstream: [LiteGapps official July 9, 2026 release](https://github.com/litegapps/litegapps/releases/tag/lite-build-20260709-3)
- Exact file: `LiteGapps-arm64-13.0-20260709-official.zip` (ARM64, Android 13 / SDK 33)
- Recorded expected SHA-256: `d0dba3c694fa61e320cb5a45dcf84e9fc897ad29f0891d811284b708b56f7f0c`

**Use upstream downloads only.** Before installing, independently verify the ZIP against the official release's checksum file. On Windows PowerShell:

```powershell
(Get-FileHash "$env:USERPROFILE\Downloads\LiteGapps-arm64-13.0-20260709-official.zip" -Algorithm SHA256).Hash
```

After verifying correct hardware/Android version and full system backups, the tested path was **systemless installation through Magisk** rather than changing the crDroid GSI's `system` partition:

```sh
adb push LiteGapps-arm64-13.0-20260709-official.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/LiteGapps-arm64-13.0-20260709-official.zip'"
# Inspect the installation result and module before rebooting.
```

Following an approved normal reboot, check package availability:

```sh
adb shell pm path com.google.android.gms
adb shell pm path com.google.android.gsf
adb shell pm path com.android.vending
```

Sign in and test Play Store downloads on the device. Minimal GApps is preferred on this resource-limited watch; follow the LiteGapps project's instructions if its packaging or supported versions change.

### Important limitations

- **Google Play works** on the test watch, as reported by the owner; this is *not* proof of Play Protect certification, Play Integrity verdicts, Google Wallet/NFC eligibility, Google account backup/sync, or error-free background service behavior.
- A `playintegrityfix` Magisk module was previously present, but it was not what installed GMS/Play Store. Do not assume root or that module guarantees certification or payments.
- Some functions can be limited in systemless GApps setups. Back up data and know how to disable just the newly added module if Android has a boot problem.

No Google APK or LiteGapps archive is mirrored in this repository.
