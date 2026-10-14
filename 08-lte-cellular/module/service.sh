#!/system/bin/sh
MODDIR="${0%/*}"
LOG="$MODDIR/last_boot.log"
{
 echo '=== S100 LTE AUTO BOOTSTRAP v1.1 ==='
 echo "date=$(date)"
 echo "module=$MODDIR"
 echo "root_uid=$(id -u)"
 echo "init_mount_ns=$(readlink /proc/1/ns/mnt)"
 echo "script_mount_ns=$(readlink /proc/self/ns/mnt)"
 if [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ]; then
   echo 'SKIP: disabled'; exit 0
 fi
 i=0
 while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$i" -lt 180 ]; do sleep 1; i=$((i+1)); done
 if [ "$(getprop sys.boot_completed)" != 1 ]; then
   echo 'ERROR: boot completion timeout'; touch "$MODDIR/disable"; exit 0
 fi
 echo "android_boot_completed_after_wait=${i}s"
 su -mm -c "sh $MODDIR/boot-helper.sh"
 rc=$?
 echo "boot_helper_exit_code=$rc"
 if [ "$rc" -ne 0 ]; then echo 'AUTO_DISABLE: bootstrap incomplete'; touch "$MODDIR/disable"; fi
 echo "last_cp=$(cat /proc/cptl/status 2>/dev/null)"
 echo "last_controller_service=$(getprop init.svc.vendor.modem_control)"
 echo "last_sim=$(getprop gsm.sim.state)"
 echo "last_baseband=$(getprop gsm.version.baseband)"
 echo '=== END S100 AUTO BOOTSTRAP ==='
} > "$LOG" 2>&1
exit 0
