#!/system/bin/sh
# S100 W527 top F9 handler; single Back / double 0-90 toggle / long volume.
MODDIR=${0%/*}
STATE=/data/local/tmp/s100_f9_v02_state
LOG=/data/local/tmp/s100_f9_v02.log
DEVICE=/dev/input/event6

mkdir -p "$STATE"
rm -f "$STATE"/down "$STATE"/valid "$STATE"/pending "$STATE"/second "$STATE"/held
: > "$LOG"

log() {
    [ -e "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 65536 ] && : > "$LOG"
    echo "$(date +%H:%M:%S) $*" >> "$LOG"
}

back_action() {
    input keyevent 4 >/dev/null 2>&1
    log 'SINGLE -> BACK'
}

rotate_action() {
    cur="$(settings get system user_rotation)"
    case "$cur" in 1) nxt=0 ;; *) nxt=1 ;; esac
    settings put system accelerometer_rotation 0
    if cmd window user-rotation lock "$nxt" >/dev/null 2>&1; then
        log "DOUBLE -> ROTATE $cur to $nxt"
    else
        log 'ERROR -> ROTATE COMMAND FAILED'
    fi
}

volume_action() {
    if cmd media_session volume --show --stream 3 --adj same >/dev/null 2>&1; then
        log 'HOLD -> VOLUME PANEL'
    else
        log 'ERROR -> VOLUME PANEL FAILED'
    fi
}

if ! getevent -lp "$DEVICE" 2>/dev/null | grep -q 'KEY_F9'; then
    log 'STOP -> expected F9 input not found at event6'
    exit 0
fi
log 'START -> F9 gesture handler v0.2'

SEQ=0
getevent -lt "$DEVICE" 2>/dev/null | while IFS= read -r line; do
    case "$line" in
        *EV_KEY*KEY_F9*DOWN*|*EV_KEY*KEY_F9*00000001*)
            [ -f "$STATE/down" ] && continue
            : > "$STATE/down"
            SEQ=$((SEQ + 1))
            seq="$SEQ"
            printf "%s" "$seq" > "$STATE/seq"
            rm -f "$STATE/valid" "$STATE/held" "$STATE/second"
            (
                sleep 0.04
                if [ -f "$STATE/down" ] && [ "$(cat "$STATE/seq" 2>/dev/null)" = "$seq" ]; then
                    : > "$STATE/valid"
                    if [ -f "$STATE/pending" ]; then
                        rm -f "$STATE/pending"
                        : > "$STATE/second"
                        rotate_action
                    else
                        (
                            sleep 0.66
                            if [ -f "$STATE/down" ] && [ -f "$STATE/valid" ] && [ "$(cat "$STATE/seq" 2>/dev/null)" = "$seq" ] && [ ! -f "$STATE/second" ]; then
                                : > "$STATE/held"
                                volume_action
                            fi
                        ) &
                    fi
                fi
            ) &
            ;;
        *EV_KEY*KEY_F9*UP*|*EV_KEY*KEY_F9*00000000*)
            rm -f "$STATE/down"
            if [ ! -f "$STATE/valid" ]; then
                continue
            fi
            rm -f "$STATE/valid"
            if [ -f "$STATE/second" ]; then
                rm -f "$STATE/second"
            elif [ -f "$STATE/held" ]; then
                rm -f "$STATE/held"
            else
                : > "$STATE/pending"
                (
                    sleep 0.35
                    if [ -f "$STATE/pending" ]; then
                        rm -f "$STATE/pending"
                        back_action
                    fi
                ) &
            fi
            ;;
    esac
done
log 'EXIT -> restarting soon if module enabled'
