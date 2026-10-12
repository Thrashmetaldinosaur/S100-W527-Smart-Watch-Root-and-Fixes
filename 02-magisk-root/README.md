# 2 — Magisk root on the S100 W527

**Previous:** [1 — Install Android 13](../01-android-13/README.md)  
**Next:** [3 — Root permanence](../03-root-permanence/README.md)

## Confirmed result

On the tested S100 W527 running crDroid 9.3 Android 13, **Magisk 30.7 rooted Android when booted through the recovery partition**. Unlike a standard phone, its 35 MiB normal boot image had **no ramdisk**. Its recovery partition was 40 MiB.

The user verified:

```powershell
adb reboot recovery
adb wait-for-device
adb shell getprop sys.boot_completed
adb shell getprop ro.bootmode
adb shell "su -v"
adb shell "su -c id"
```

Expected on the tested unit: Android boot completed `1`, boot mode `recovery`, Magisk `30.7:MAGISKSU`, and `uid=0(root)`.

## Prerequisites

- Complete the [Android 13 setup](../01-android-13/README.md).
- Keep verified, device-specific backups of the original `boot`, `recovery`, and `vbmeta` partitions.
- Confirm a working rescue connection to your watch's Unisoc download mode **before** modifying its recovery.
- Do not use another hardware variant's images.

## What was done on the test unit

1. The original 40 MiB recovery image was backed up.
2. Magisk **30.7** was used to patch that recovery image with **Recovery Mode ON**.
3. Because the bootloader was locked and verified boot enabled, the modified recovery was reconstructed with a valid signature for this unit and checked against the firmware's trusted public-key configuration.
4. The verified recovery image was written to the **recovery** partition only and read back for a complete hash match. Original `boot` and `vbmeta` were left untouched.
5. `adb reboot recovery` booted Android 13 with working Magisk root.

### Important reproduction limitation

The signature-rebuilding process is **not yet available here as a public, independently reproducible utility**. A raw Magisk output **is not guaranteed to be flash-ready** on a locked-bootloader device. Do not attempt this operation until the signing requirements for your exact firmware have been independently satisfied.

**No firmware images or signing secrets are distributed in this repository.**

## Reference checksums (the tested device only)

| Image | SHA-256 |
|---|---|
| Original `boot.bin` | `8CCE2C086D754E6A2F9E00E6419A3EC55EEAFC0CFDEC8B9242E0EBC37D8D2AD2` |
| Original `recovery.bin` | `74D1604BC7735FE130A0C2F0E83334B89B1880D7350DA7921436F8872A09D58E` |
| Working modified recovery image | `4A81E835E17CEBB61F4D0AF3D0D912C8C0737D66063A846AB95E23C4827791AC` |

These identify one unit's files and **are not universal S100 checksums**.

## Known failure avoided

A later test attempted to put Magisk in the normal boot partition instead. Although its offline image verification passed, the watch remained at the NOEMIE startup screen. Original `boot` was restored successfully through FDL2. **Do not flash the experimental normal-boot image.**

## Next: Make root automatic

At this stage, `adb reboot recovery` works, but an ordinary power-on does not automatically select recovery. Continue to [3 — Root permanence](../03-root-permanence/README.md) only after confirming that the recovery-root setup works.
