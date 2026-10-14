#!/system/bin/sh
ui_print '- Verifying exact S100 board and vendor modem controller'
[ "$(getprop ro.product.model)" = XTQ_Watch ] || abort '! Wrong model'
[ "$(getprop ro.boot.hardware)" = uws6152_1h10_go ] || abort '! Wrong hardware'
grep -q 'androidboot.mode=recovery' /proc/cmdline || abort '! Recovery boot required'
STOCK=b9157c0529a8cd56b2b4b7099857bcccb8367683227d05c49fd30660bfa54b74
PATCH=0989bd2e5444f4cb62d277a91824390cbfe90232038375414031b16272a6c951
TARGET=/vendor/bin/modem_control
[ "$(sha256sum "$TARGET" | cut -d ' ' -f1)" = "$STOCK" ] || abort '! Stock controller hash mismatch'
awk '$5=="/vendor/bin/modem_control"{found=1} END{exit !found}' /proc/self/mountinfo && abort '! Existing bind mount detected'
mkdir -p "$MODPATH/payload" || abort '! Cannot create module payload'
cp "$TARGET" "$MODPATH/payload/modem_control.patched" || abort '! Cannot copy original controller'
# Tested one-byte patch at offset 0x8A84: recovery -> Recovery
printf R | dd of="$MODPATH/payload/modem_control.patched" bs=1 seek=35460 conv=notrunc 2>/dev/null || abort '! Patch failed'
[ "$(sha256sum "$MODPATH/payload/modem_control.patched" | cut -d ' ' -f1)" = "$PATCH" ] || abort '! Patched SHA mismatch'
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/boot-helper.sh" 0 0 0755
set_perm "$MODPATH/payload/modem_control.patched" 0 0 0644
ui_print '- Local patch successfully verified; vendor partition unchanged'
