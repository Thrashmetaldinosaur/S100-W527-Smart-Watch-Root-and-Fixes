# S100 LTE recovery-root fix

Confirmed on the S100 XTQ_Watch (Unisoc UWS6152) with Android 13 crDroid and Magisk 30.7 recovery-root. A temporary one-byte patched modem controller allows the cellular processor to boot. The Magisk service then restores the stock executable and verifies that the controller service is running. The verified October 14, 2026 boot log showed CP started, SIM LOADED, stock executable restored, and both exit codes 0. Cellular network registration/data connectivity remain untested.

## Technical background

Recovery-root sets `androidboot.mode=recovery`. The vendor `modem_control` executable checks `/proc/cmdline` and skips modem startup in recovery mode. The validated patch changes one byte in a temporary copy of its search string, `androidboot.mode=recovery` -> `androidboot.mode=Recovery`, at offset 0x8A84. That starts the cellular processor without changing the on-flash vendor executable or recovery-root configuration.

## Device restrictions

Only tested on model XTQ_Watch, hardware uws6152_1h10_go, with exact original `/vendor/bin/modem_control` SHA-256 `b9157c0529a8cd56b2b4b7099857bcccb8367683227d05c49fd30660bfa54b74`. Patched SHA-256 is `0989bd2e5444f4cb62d277a91824390cbfe90232038375414031b16272a6c951`. Do not attempt on other firmware or variants.

## Scope

Boot-time modem startup, SIM recognition and rollback are verified on one device. Carrier data has not yet been tested. The public distribution does not include proprietary modem firmware or a personal device serial number; a compatible user's watch supplies its own firmware to generate the same verified patch.

**Installation scripts and rollback instructions will be added alongside this note.**