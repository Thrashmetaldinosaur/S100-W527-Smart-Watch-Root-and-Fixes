#!/system/bin/sh
# S100 W527 top F9 button handler v0.3
# Single BACK; double rotation; long press volume panel.
# v0.3 removes the asynchronous 40ms valid-marker race.
STATE=/data/local/tmp/s100_f9_v03_state
LOG=/data/local/tmp/s100_f9_v03.log
DEVICE=/dev/input/event6
mkdir -p "$STATE"
rm -f "$STATE/down" "$STATE/pending" "$STATE/second" "$STATE/held" "$STATE/seq"
: > "$LOG"

log() {
    [ -e "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 65536 ] && : > "$LOG"
    echo "$(date +%H:%M:%S) $*" >> "$LOG"
}
back_action() {
    if input keyevent 4 >/dev/null 2>&1; then
        log 'SINGLE -> BACK'
    else
        log 'ERROR -> BACK COMMAND FAILED'
    fi
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
log 'START -> F9 handler v0.3'
SEQ=0
getevent -lt "$DEVICE" 2>/dev/null | while IFS= read -r line; do
    case "$line" in
        *EV_KEY*KEY_F9*DOWN*|*EV_KEY*KEY_F9*00000001*)
            [ -f "$STATE/down" ] && continue
            SEQ=$((SEQ + 1))
            seq="$SEQ"
            printf '%s' "$seq" > "$STATE/seq"
            : > "$STATE/down"
            rm -f "$STATE/held" "$STATE/second"
            if [ -f "$STATE/pending" ]; then
                rm -f "$STATE/pending"
                : > "$STATE/second"
                rotate_action
            else
                (
                    sleep 0.70
                    if [ -f "$STATE/down" ] &&
                       [ "$(cat "$STATE/seq" 2>/dev/null)" = "$seq" ] &&
                       [ ! -f "$STATE/second" ]; then
                        : > "$STATE/held"
                        volume_action
                    fi
                ) &
            fi
            ;;
        *EV_KEY*KEY_F9*UP*|*EV_KEY*KEY_F9*00000000*)
            [ -f "$STATE/down" ] || continue
            rm -f "$STATE/down"
            if [ -f "$STATE/second" ]; then
                rm -f "$STATE/second"
            elif [ -f "$STATE/held" ]; then
                rm -f "$STATE/held"
            else
                : > "$STATE/pending"
                seq="$SEQ"
                (
                    sleep 0.35
                    if [ -f "$STATE/pending" ] &&
                       [ "$(cat "$STATE/seq" 2>/dev/null)" = "$seq" ]; then
                        rm -f "$STATE/pending"
                        back_action
                    fi
                ) &
            fi
            ;;
    esac
done
log 'EXIT -> input stream ended'
