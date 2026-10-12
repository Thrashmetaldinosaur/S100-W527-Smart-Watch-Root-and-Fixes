#!/system/bin/sh
# Magisk late_start service; preserve stock POWER behavior.
MODDIR=${0%/*}
(
  i=0
  while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 150 ]; do
    sleep 2
    i=$((i + 1))
  done
  [ "$(getprop sys.boot_completed)" = "1" ] || exit 0
  sleep 3
  while [ ! -e "$MODDIR/disable" ]; do
    /system/bin/sh "$MODDIR/power-double-home.sh"
    sleep 3
  done
) >/dev/null 2>&1 &
