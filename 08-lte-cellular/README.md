# 8. S100 LTE with recovery-root Magisk

**Status (October 14, 2026): verified on the test S100.** With Android 13 crDroid and Magisk 30.7 recovery-root, the module automatically started the cellular processor after reboot, initialized the modem, recognized a physical SIM as `LOADED`, restored the original vendor controller, and confirmed the controller service was running. Both `FINAL_EXIT_CODE` and `boot_helper_exit_code` were 0. **Carrier registration and actual mobile data have not yet been tested.**

## Problem and solution

Recovery-based Magisk starts Android with `androidboot.mode=recovery` in `/proc/cmdline`. The Unisoc `/vendor/bin/modem_control` detects this condition and deliberately skips CP startup. Android property overrides do not fix this, because the controller reads the kernel command line.

The boot-time module temporarily bind-mounts a **one-byte patched copy** of that controller, changing the string `androidboot.mode=recovery` to `androidboot.mode=Recovery`. It clears `nosuid` for that single temporary mount, starts the CP, allows SIM initialization, then unmounts the patched file and restarts the **stock** controller. SELinux remains enforcing. No system/vendor firmware partition, recovery image, Bootloader Control Block, or modem NV is written.

## Exact compatibility requirements

- Model: `XTQ_Watch`
- Hardware: `uws6152_1h10_go`
- Kernel boot mode: `androidboot.mode=recovery`
- Stock controller SHA-256: `b9157c0529a8cd56b2b4b7099857bcccb8367683227d05c49fd30660bfa54b74`
- Patched controller SHA-256: `0989bd2e5444f4cb62d277a91824390cbfe90232038375414031b16272a6c951`

The byte changed is at file offset `0x8A84` (decimal `35460`). **Do not bypass these checks for a different watch, vendor binary, or firmware version.** This is an advanced experimental modification with crash/boot risks.

## Build and install

The public repository contains **scripts only**. It does not distribute Unisoc's proprietary executable. During Magisk installation, `customize.sh` copies the watch's exact compatible original controller to the module area, performs the one-byte change, and checks the final patched hash before accepting installation.

1. Download this GitHub repository (Code → Download ZIP), extract it, and open PowerShell 7 in the `08-lte-cellular` folder.
2. Build the installation ZIP:

   ```powershell
   pwsh -NoProfile -ExecutionPolicy Bypass -File .\Build-Module.ps1
   ```

3. Install the newly created `S100-LTE-AutoBoot-v1.1.1.zip` in the **Magisk app → Modules → Install from storage**, or, with the correct S100 already selected in ADB:

   ```powershell
   adb push .\S100-LTE-AutoBoot-v1.1.1.zip /data/local/tmp/s100-lte-module.zip
   adb shell su -c 'magisk --install-module /data/local/tmp/s100-lte-module.zip'
   ```

4. Reboot using your existing **recovery-root** boot method.
5. After Android boots, verify the complete startup log and live status:

   ```powershell
   adb shell su -c 'cat /data/adb/modules/s100_lte_cp_bootstrap/last_boot.log'
   adb shell su -c 'cat /proc/cptl/status; getprop gsm.sim.state; getprop gsm.version.baseband; getprop init.svc.vendor.modem_control; sha256sum /vendor/bin/modem_control'
   ```

Wait until the log ends with `=== END S100 AUTO BOOTSTRAP ===`. The tested final result included `CP_STARTED`, `ROLLBACK_OK`, `CONTROLLER_READY`, `sim=LOADED`, `FINAL_EXIT_CODE=0`, and `boot_helper_exit_code=0`. An early check may catch the temporary patch while still mounted.

## Disable / recovery

With rooted ADB available, disable future automatic attempts:

```powershell
adb shell su -c 'touch /data/adb/modules/s100_lte_cp_bootstrap/disable'
```

This does not interrupt an active bootstrap process. The module automatically disables future attempts if its validation or rollback reports a failure. If Android cannot finish booting, use your established recovery access and Magisk module-removal mechanism. Keep working recovery backups.

## What was tested and what was not

- **Confirmed on one S100:** Boot-time Magisk service executes, CP starts automatically, baseband becomes available, SIM reaches `LOADED`, patched controller is removed, stock controller service resumes, and helper exits successfully.
- **Not confirmed:** Carrier registration, mobile data connection, different firmware or other S100 hardware variants.
- **Packaging note:** The final public **source-only installer** generates the exact tested patched binary on-device using a SHA-verified one-byte operation. That installer pathway was validated against the collected binary offline but has not been independently tested on another watch.

No proprietary firmware, IMEI/SIM identifiers, personal serial numbers, private keys or NV dumps are published.
