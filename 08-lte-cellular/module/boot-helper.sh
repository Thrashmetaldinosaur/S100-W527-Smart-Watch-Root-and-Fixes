#!/system/bin/sh
# Proven S100 transient modem controller replacement and stock rollback.
set -u
MODDIR="${0%/*}"
T=/vendor/bin/modem_control
D=/data/local/tmp/s100_lte_bootstrap_$$
STOCK=b9157c0529a8cd56b2b4b7099857bcccb8367683227d05c49fd30660bfa54b74
PATCH=0989bd2e5444f4cb62d277a91824390cbfe90232038375414031b16272a6c951
mounted=0
sha() { sha256sum "$1" 2>/dev/null | cut -d ' ' -f1; }
cpstate() { cat /proc/cptl/status 2>/dev/null | tr -d '\r\n'; }
mountline() { awk '$5=="/vendor/bin/modem_control"{print;exit}' /proc/self/mountinfo; }
mountopts() { awk '$5=="/vendor/bin/modem_control"{print $6;exit}' /proc/self/mountinfo; }
report() { echo "STATUS:$1 cp=$(cpstate) controller=$(getprop init.svc.vendor.modem_control) sha=$(sha "$T") sim=$(getprop gsm.sim.state) baseband=$(getprop gsm.version.baseband)"; }
wait_stock_controller() {
 waited=0
 while [ "$waited" -lt 25 ]; do
  if [ "$(getprop init.svc.vendor.modem_control)" = running ]; then
   echo "CONTROLLER_READY: stock service running after ${waited}s"; return 0
  fi
  sleep 1; waited=$((waited+1))
 done
 echo 'CONTROLLER_SLOW: re-requesting stock service start once'
 setprop ctl.start vendor.modem_control || true
 waited=0
 while [ "$waited" -lt 12 ]; do
  if [ "$(getprop init.svc.vendor.modem_control)" = running ]; then
   echo "CONTROLLER_READY_AFTER_RETRY: ${waited}s"; return 0
  fi
  sleep 1; waited=$((waited+1))
 done
 echo "CONTROLLER_FAILED: $(getprop init.svc.vendor.modem_control)"
 return 1
}
finish() {
 rc=$?
 trap - EXIT HUP INT TERM
 if [ "$mounted" -eq 1 ]; then
  echo 'RESTORE: stopping patched controller before unmount'
  setprop ctl.stop vendor.modem_control
  sleep 2
  n=0
  while [ "$n" -lt 6 ]; do
   umount "$T" >/dev/null 2>&1 && break
   n=$((n+1)); sleep 1
  done
  if [ "$(sha "$T")" != "$STOCK" ]; then
   echo 'RESTORE: attempting lazy unmount'
   umount -l "$T" >/dev/null 2>&1 || true; sleep 1
  fi
  if [ "$(sha "$T")" != "$STOCK" ]; then
   echo 'ERROR: stock controller not visible; do not restart'; rc=90
  else
   echo 'ROLLBACK_OK: stock executable restored'
   setprop ctl.start vendor.modem_control || true
  fi
 fi
 if [ -z "$(mountline)" ]; then
  [ -d "$D" ] && rm -rf "$D"
 else
  echo 'ERROR: bind mount still present'; rc=91
 fi
 if [ "$(sha "$T")" != "$STOCK" ]; then echo 'ERROR: wrong stock hash'; rc=92; fi
 if [ "$rc" -eq 0 ] && [ "$(cpstate)" != started ]; then echo 'ERROR: CP stopped'; rc=93; fi
 if [ "$rc" -eq 0 ]; then
  if [ "$(getprop init.svc.vendor.modem_control)" != running ]; then
   echo 'CONTROLLER_WAIT: stock service settling'; wait_stock_controller || rc=94
  else
   echo 'CONTROLLER_READY: stock service running'
  fi
 fi
 report FINAL
 [ "$rc" -ne 0 ] || echo 'SUCCESS: CP started, stock binary restored, controller running'
 echo "FINAL_EXIT_CODE=$rc"
 exit "$rc"
}
trap finish EXIT HUP INT TERM
if [ "$(id -u)" != 0 ]; then echo 'ABORT: not root'; exit 2; fi
if [ "$(getprop ro.product.model)" != XTQ_Watch ] || [ "$(getprop ro.boot.hardware)" != uws6152_1h10_go ]; then echo 'ABORT: wrong hardware'; exit 3; fi
if ! grep -q 'androidboot.mode=recovery' /proc/cmdline; then echo 'ABORT: not recovery-root mode'; exit 5; fi
if [ "$(readlink /proc/self/ns/mnt)" != "$(readlink /proc/1/ns/mnt)" ]; then echo 'ABORT: wrong mount namespace'; exit 6; fi
if [ "$(sha "$MODDIR/payload/modem_control.patched")" != "$PATCH" ]; then echo 'ABORT: patch checksum'; exit 7; fi
if [ -n "$(mountline)" ]; then echo 'ABORT: another controller mount present'; exit 8; fi
if [ "$(sha "$T")" != "$STOCK" ]; then echo 'ABORT: stock checksum'; exit 9; fi
report BASELINE
if [ "$(cpstate)" = started ]; then echo 'CP_ALREADY_STARTED: no restart needed'; exit 0; fi
if [ "$(cpstate)" != stopped ]; then echo 'ABORT: unexpected CP state'; exit 10; fi
mkdir -p "$D" || exit 11
cp "$MODDIR/payload/modem_control.patched" "$D/modem_control.patched" || exit 12
if ! magisk --clone-attr "$T" "$D/modem_control.patched"; then echo 'ABORT: clone security attributes failed'; exit 13; fi
chmod 755 "$D/modem_control.patched" || exit 14
if ! ls -Z "$D/modem_control.patched" 2>/dev/null | grep -q modem_control_exec; then echo 'ABORT: SELinux label missing'; exit 15; fi
if [ "$(sha "$D/modem_control.patched")" != "$PATCH" ]; then echo 'ABORT: staged hash'; exit 16; fi
if ! mount --bind "$D/modem_control.patched" "$T"; then echo 'ABORT: bind mount'; exit 17; fi
mounted=1
if [ "$(sha "$T")" != "$PATCH" ] || [ "$(sha /proc/1/root/vendor/bin/modem_control)" != "$PATCH" ]; then echo 'ABORT: init namespace not patched'; exit 18; fi
mount -o remount,bind,suid,exec "$T" || true
case ",$(mountopts)," in
 *,nosuid,*|*,noexec,*) echo 'ABORT: nosuid/noexec remains'; exit 19;;
esac
if [ "$(sha "$T")" != "$PATCH" ]; then echo 'ABORT: patch mount hash'; exit 20; fi
echo 'PATCH_MOUNT_READY: SELinux enforcing and nosuid cleared'
setprop ctl.restart vendor.modem_control || exit 21
n=0
while [ "$n" -lt 24 ]; do
 sleep 1; n=$((n+1))
 if [ "$(cpstate)" = started ]; then echo "CP_STARTED after ${n}s"; break; fi
done
if [ "$(cpstate)" != started ]; then echo 'ERROR: CP did not start'; exit 22; fi
sleep 12
report BEFORE_ROLLBACK
exit 0
