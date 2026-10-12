#!/system/bin/sh
MODDIR=${0%/*}
(
  i=0
  while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 120 ]; do
    sleep 2
    i=$((i + 1))
  done
  [ "$(getprop sys.boot_completed)" = "1" ] || exit 0
  sleep 3
  while [ ! -f "$MODDIR/disable" ]; do
    /system/bin/sh "$MODDIR/f9-handler.sh" >/dev/null 2>&1
    sleep 3
  done
) &
