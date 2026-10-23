#!/bin/bash
# Luma on your TV, from this computer: build, install, make it the home screen, and undo that.
#   ./luma.sh [-s DEVICE] build | install | home | widgets | restore | status
# DEVICE is the TV's adb address (192.168.1.50:5555) or serial. Default: $LUMA_DEVICE, else the only device adb sees.
set -euo pipefail
cd "$(dirname "$0")"
DEVICE="${LUMA_DEVICE:-}"
if [ "${1:-}" = "-s" ]; then DEVICE="$2"; shift 2; fi
ACTION="${1:-status}"
LUMA="dev.luma.tv/.MainActivity"
# The only packages this script ever switches off: stock TV launchers, and Google TV's setup "recovery" home,
# which outranks any other home screen once the launcher is off.
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"

die() { echo "luma: $*" >&2; exit 1; }
pick_device() {
    if [ -n "$DEVICE" ]; then
        case "$DEVICE" in *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;; esac
        return
    fi
    local list; list="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    [ -n "$list" ] || die "no TV found. Turn on ADB debugging on the TV, then: adb connect <TV IP>:5555"
    [ "$(echo "$list" | wc -l | tr -d ' ')" = "1" ] || die "several devices connected; choose one with -s DEVICE:
$list"
    DEVICE="$list"
}
tv() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }
home_now() { tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }
state_file() { mkdir -p .local; echo ".local/home-$(tv getprop ro.serialno | tr -cd 'A-Za-z0-9').txt"; }
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }
show_home() { tv dumpsys power | grep -q 'mWakefulness=Awake' && tv input keyevent KEYCODE_HOME || true; } # never wakes a sleeping TV

case "$ACTION" in
build)
    bash build.sh ;;
install)
    pick_device
    [ -f dist/luma-tv.apk ] || bash build.sh
    adb -s "$DEVICE" install --no-incremental -r dist/luma-tv.apk ;;
home)
    pick_device
    installed dev.luma.tv || die "Luma is not installed yet: ./luma.sh install"
    state="$(state_file)"
    if [ ! -s "$state" ]; then echo "original=$(home_now)" > "$state"; fi
    tv cmd package set-home-activity --user 0 "$LUMA" >/dev/null
    for _ in 1 2 3 4; do
        current="$(home_now)"
        [ "$current" = "$LUMA" ] && break
        pkg="${current%%/*}"
        case " $KNOWN " in
        *" $pkg "*)
            echo "Switching off $pkg, which the TV puts above any other home screen."
            tv pm disable-user --user 0 "$pkg" >/dev/null
            echo "disabled=$pkg" >> "$state"
            tv cmd package set-home-activity --user 0 "$LUMA" >/dev/null ;;
        *) die "the TV still opens $current at Home, and luma.sh does not switch off unknown apps. Nothing else was changed; ./luma.sh restore undoes this." ;;
        esac
    done
    [ "$(home_now)" = "$LUMA" ] || die "the TV did not accept Luma as its home screen. ./luma.sh restore undoes the changes."
    show_home
    echo "Luma is the home screen. Undo at any time: ./luma.sh restore" ;;
widgets)
    # The Home widgets work without this; it switches on what a TV remote makes tedious to allow by hand.
    pick_device
    installed dev.luma.tv || die "Luma is not installed yet: ./luma.sh install"
    tv pm grant dev.luma.tv android.permission.READ_TV_LISTINGS && echo "Up Next: Continue watching from your apps."
    tv cmd notification allow_listener dev.luma.tv/dev.luma.tv.MediaListener && echo "Now Playing: music playing on the TV."
    tv appops set dev.luma.tv GET_USAGE_STATS allow && echo "Up Next: recently used apps."
    echo "Undo any of these in the TV's Settings → Apps → Special app access, or Settings → Apps → Luma TV → Permissions." ;;
restore)
    pick_device
    state="$(state_file)"
    if [ -s "$state" ]; then
        disabled="$(sed -n 's/^disabled=//p' "$state")"; original="$(sed -n 's/^original=//p' "$state" | head -n 1)"
    else # set up by hand or on another computer: switch every known launcher back on
        disabled=""; original=""
        for pkg in $KNOWN; do installed "$pkg" && tv pm list packages -d "$pkg" | grep -qx "package:$pkg" && disabled="$disabled $pkg"; done
    fi
    for pkg in $disabled; do echo "Switching $pkg back on."; tv pm enable --user 0 "$pkg" >/dev/null || true; done
    if [ -n "$original" ] && [ "$original" != "$LUMA" ] && [[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]]; then
        tv cmd package set-home-activity --user 0 "$original" >/dev/null || true
    fi
    rm -f "$state"
    show_home
    echo "Home now opens $(home_now). Luma stays installed (adb uninstall dev.luma.tv removes it)." ;;
status)
    pick_device
    echo "TV:   $(tv getprop ro.product.manufacturer) $(tv getprop ro.product.model), Android $(tv getprop ro.build.version.release)"
    echo "Luma: $(tv dumpsys package dev.luma.tv | sed -n 's/^ *versionName=//p' | head -n 1 | grep . || echo 'not installed')"
    echo "Home: $(home_now)" ;;
*)
    die "unknown command '$ACTION'. Use build, install, home, widgets, restore or status." ;;
esac
