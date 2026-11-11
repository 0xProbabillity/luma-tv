#!/bin/bash
# Measures shangOS, Luma TV and the TV's own launcher on YOUR TV, one after the other, and writes one JSON file.
#   tools/bench.sh -s 192.168.1.50:5555                  everything that is installed
#   tools/bench.sh -s 192.168.1.50:5555 --only shangos,stock
#   tools/bench.sh -s 192.168.1.50:5555 --out docs/data/bench.json
# Needs: adb, python3, and each launcher installed once (open it once). Takes about 5 minutes per launcher; QUICK=1 shortens it.
# Do not touch the remote while it runs. At the end the TV gets its original Home screen back and anything this script
# switched on again is switched off again. The JSON says which TV, which versions and when; the release build can publish it
# (see docs/MEASURING.md). Method and fairness rules: docs/MEASURING.md.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
DEV="" ONLY="shangos,luma,stock" OUT="docs/data/bench.json" WORK=""
usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }
while [ $# -gt 0 ]; do
    case "$1" in
        -s) DEV="${2:?-s needs the TV address}"; shift 2 ;;
        --only) ONLY="${2:?--only needs a list: shangos,luma,stock}"; shift 2 ;;
        --out) OUT="${2:?--out needs a path}"; shift 2 ;;
        --help | -h) usage 0 ;;
        *) usage 1 >&2 ;;
    esac
done
ADB=(adb)
[ -n "$DEV" ] && ADB=(adb -s "$DEV")
sh() { "${ADB[@]}" shell "$@" | tr -d '\r'; }
command -v adb >/dev/null 2>&1 || { echo "adb is needed"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is needed"; exit 1; }
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "No TV connected: adb connect <IP>:5555"; exit 1; }
STOCK_PKGS="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"
installed() { sh pm list packages "$1" | grep -qx "package:$1"; }
disabled() { sh pm list packages -d "$1" | grep -qx "package:$1"; }
home_activity_of() { sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME "$1" | tail -1; }
ORIGINAL_HOME="$(sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
WAS_DISABLED=""
for p in $STOCK_PKGS; do installed "$p" && disabled "$p" && WAS_DISABLED="$WAS_DISABLED $p"; done
WORK="$(mktemp -d)"
# shellcheck disable=SC2329 # runs from the EXIT trap
restore() {
    for p in $WAS_DISABLED; do sh pm disable-user --user 0 "$p" >/dev/null 2>&1; done
    [[ "$ORIGINAL_HOME" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]] && sh cmd package set-home-activity --user 0 "$ORIGINAL_HOME" >/dev/null 2>&1
    rm -rf "$WORK"
    echo "The TV's Home is back to $ORIGINAL_HOME."
}
trap restore EXIT
trap 'echo; echo "Stopped."; exit 130' INT TERM

REPORTS=()
run_one() { # run_one LABEL PACKAGE/ACTIVITY
    local label="$1" comp="$2"
    echo "== Measuring $label ($comp): about 5 minutes, do not touch the remote"
    if OUTDIR="$WORK" SET_HOME=1 COMPILE=speed tools/baseline.sh ${DEV:+-s "$DEV"} "$comp" "$label"; then REPORTS+=("$WORK/measure-$label.txt"); else echo "   $label failed; it is left out"; fi
}
case ",$ONLY," in *,shangos,*)
    if installed dev.madusha.shangos; then run_one shangos dev.madusha.shangos/.MainActivity; else echo "shangOS is not installed on this TV: skipped"; fi ;;
esac
case ",$ONLY," in *,luma,*)
    if installed dev.luma.tv; then run_one luma dev.luma.tv/.MainActivity; else echo "Luma TV is not installed (baseline/upstream-luma-tv-2.4-8ce6838.apk): skipped"; fi ;;
esac
case ",$ONLY," in *,stock,*)
    found=""
    for p in $STOCK_PKGS; do
        installed "$p" || continue
        act="$(home_activity_of "$p")"
        [[ "$act" == "$p/"* ]] || continue
        disabled "$p" && sh pm enable --user 0 "$p" >/dev/null 2>&1
        found="$act"
        break
    done
    if [ -n "$found" ]; then run_one stock "$found"; else echo "No stock launcher found: skipped"; fi ;;
esac
if [ "${#REPORTS[@]}" -eq 0 ]; then echo "Nothing was measured."; exit 1; fi
python3 tools/bench-json.py "${REPORTS[@]}" --out "$OUT" && python3 tools/bench-json.py --check "$OUT"
echo "Next: python3 tools/build-site.py   (the website shows this file), then commit $OUT"
