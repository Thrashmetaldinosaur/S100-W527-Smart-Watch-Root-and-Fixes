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

## Confirmed fix: Play Store says "You're offline" despite working Wi-Fi (October 12, 2026)

On the tested S100 (crDroid 9.3, Android 13, LiteGapps), Play Store reported **"You're offline"** although Wi-Fi was connected and validated, Android had the correct time, a Google account was signed in, and `adb shell ping play-fe.googleapis.com` succeeded.

Play Store/Finsky logs reported `ERR_NAME_NOT_RESOLVED` and `ERR_CONNECTION_REFUSED`. The decisive diagnostic was Android's network policy output for the Play Store UID (10324 **on this particular watch**, not necessarily on yours):

```text
blocked_state={blocked=RESTRICTED_MODE,...,effective=RESTRICTED_MODE}
```

**The owner confirmed that disabling Android Restricted Networking Mode immediately fixed Google Play connectivity.** This was not solved by APK updates or changing DNS. Restricted mode can leave shell/system networking functional while blocking ordinary applications.

### Check and apply the fix

```sh
adb devices
adb shell cmd netpolicy get restricted-mode
adb shell settings get global restricted_networking_mode

# If enabled / equal to 1:
adb shell settings put global restricted_networking_mode 0

# Verify it is disabled / 0:
adb shell cmd netpolicy get restricted-mode
adb shell settings get global restricted_networking_mode

# If necessary, restart only Play Store:
adb shell am force-stop com.android.vending
adb shell monkey -p com.android.vending -c android.intent.category.LAUNCHER 1
```

The global setting is persistent in normal Android operation; **persistence after subsequent reboots has not yet been separately verified on this S100**. If it re-enables itself, investigate ROM boot scripts or configuration before adding a Magisk startup override. Disabling this mode is a **device-wide networking-policy change**, not a Google-specific permission grant.

### Why was it enabled?

The vanilla GSI originally lacked Google services; LiteGapps added them systemlessly. It's plausible that Play Store did not have the privilege needed to bypass restricted mode, but **we have not established which ROM component or setting enabled restricted mode in the first place**. Do not assume LiteGapps enabled it.

### Google versions observed during troubleshooting

- Google Play Services updated from `22.18.21` to `26.39.33`.
- Google Play Store updated from `30.4.17` to `53.4.34`.
- Google Services Framework reported version `13`; a GSF Android ID existed.

Updating Play Services and Play Store did **not** resolve the offline symptom until restricted mode was disabled. Do not wipe GSF data or replace system components to fix this particular problem. These proprietary APKs are not included in the repository.


### Important limitations

- **Google Play works** on the test watch, as reported by the owner; this is *not* proof of Play Protect certification, Play Integrity verdicts, Google Wallet/NFC eligibility, Google account backup/sync, or error-free background service behavior.
- A `playintegrityfix` Magisk module was previously present, but it was not what installed GMS/Play Store. Do not assume root or that module guarantees certification or payments.
- Some functions can be limited in systemless GApps setups. Back up data and know how to disable just the newly added module if Android has a boot problem.

No Google APK or LiteGapps archive is mirrored in this repository.
