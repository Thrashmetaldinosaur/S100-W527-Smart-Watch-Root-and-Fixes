#!/system/bin/sh
# S100 W527 passive GPIO POWER listener.
# Native POWER single sleep, long menu, and wake remain handled by Android.
DEVICE=/dev/input/event6
LOG=/data/local/tmp/s100_bottom_double_home.log
WINDOW_MS=500
LONG_MS=700
BOUNCE_MS=35

log() {
    [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 32768 ] && : > "$LOG"
    echo "$(date +%H:%M:%S) $*" >> "$LOG"
}

if ! getevent -lp "$DEVICE" 2>/dev/null | grep -q 'KEY_POWER'; then
    log 'STOP: expected KEY_POWER not at /dev/input/event6; refusing to start'
    exit 1
fi
log 'START: S100 bottom double-home v0.1 (single/long/wake untouched)'

monitor() {
last_up=-1
down_ms=-1
while IFS= read -r line; do
    case "$line" in
        *EV_KEY*KEY_POWER*DOWN*) kind=down ;;
        *EV_KEY*KEY_POWER*UP*)   kind=up ;;
        *) continue ;;
    esac

    stamp=${line#*\[}
    stamp=${stamp%%\]*}
    stamp=${stamp##* }
    case "$stamp" in *.*) ;; *) continue ;; esac
    sec=${stamp%%.*}
    fraction=${stamp#*.}
    case "$sec:$fraction" in *[!0-9:]*|:*|*:) continue ;; esac
    padded="${fraction}000"
    frac3=${padded%${padded#???}}
    event_ms=$((sec * 1000 + (1$frac3 - 1000)))

    if [ "$kind" = down ]; then
        if [ "$down_ms" -lt 0 ]; then
            down_ms=$event_ms
        fi
        continue
    fi

    [ "$down_ms" -ge 0 ] || continue
    duration=$((event_ms - down_ms))
    down_ms=-1
    if [ "$duration" -lt "$BOUNCE_MS" ] || [ "$duration" -lt 0 ]; then
        continue
    fi
    if [ "$duration" -ge "$LONG_MS" ]; then
        last_up=-1
        log "LONG observed (${duration} ms); Android handles power menu"
        continue
    fi

    delta=$((event_ms - last_up))
    if [ "$last_up" -ge 0 ] && [ "$delta" -ge 0 ] && [ "$delta" -le "$WINDOW_MS" ]; then
        last_up=-1
        sleep 0.12
        if input keyevent 3 >/dev/null 2>&1; then
            sleep 0.25
            if input keyevent 4 >/dev/null 2>&1; then
                log "DOUBLE -> WATCHFACE (${delta} ms between releases)"
            else
                log 'ERROR: BACK injection failed'
            fi
        else
            log 'ERROR: HOME injection failed'
        fi
    else
        last_up=$event_ms
        log "SINGLE observed (${duration} ms); stock action unchanged"
    fi
done
}

if [ -n "${S100_TEST_SECONDS:-}" ]; then
    timeout "$S100_TEST_SECONDS" getevent -lt "$DEVICE" 2>/dev/null | monitor
else
    getevent -lt "$DEVICE" 2>/dev/null | monitor
fi
log 'EXIT: button monitor ended'
