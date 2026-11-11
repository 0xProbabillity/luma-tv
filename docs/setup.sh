#!/bin/bash
# shangOS quick setup, from any computer with adb. One line:
#
#     curl -fsSL https://shangos.madusha.dev/setup.sh | bash
#
# It downloads the newest release APK from github.com/madushas/shangos-launcher, checks it against
# the published SHA-256 checksums, installs it on the TV, and (unless told not to) allows the
# optional permissions and makes it the home screen. Every command that changes the TV is printed
# first (lines starting with +), so nothing happens out of sight.
#
# Without a terminal it runs on flags alone:
#     bash setup.sh --tv 192.168.1.50:5555 --variant offline --yes
# Undo everything (Home goes back to the TV's own launcher first):
#     bash setup.sh --uninstall [--tv 192.168.1.50:5555]
#
# This script only ever talks to github.com/madushas/shangos-launcher and to your TV. No other
# hosts, no telemetry, no files kept behind the APK in a temporary folder that is deleted on exit.
# Prefer reading it first?  curl -fsSL -o setup.sh https://shangos.madusha.dev/setup.sh && less setup.sh
set -euo pipefail

PKG="dev.madusha.shangos"
SHANGOS="$PKG/.MainActivity"
REPO="madushas/shangos-launcher"
# The stock TV launchers shangOS may have to switch off for Home to change (reversible; recorded).
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/shangos"
ENDPOINTS="$CONFIG_DIR/endpoints"
VARIANT="release"
TV=""
APK=""
DO_HOME=1
DO_WIDGETS=1
ASSUME_YES=0
UNINSTALL=0

usage() {
    cat <<'USAGE'
shangOS quick setup. One line, from any computer with adb:

    curl -fsSL https://shangos.madusha.dev/setup.sh | bash

It downloads the newest release APK from github.com/madushas/shangos-launcher, checks it
against the published SHA-256 checksums, installs it on the TV, and (unless told not to)
allows the optional permissions and makes it the home screen.

Options:
    --tv, -t ADDRESS     the TV's adb address (192.168.1.50:5555)
    --variant, -v NAME   release (default) or offline (no internet permission)
    --apk, -a PATH       install this APK instead of downloading the release
    --yes, -y            answer every question with its default, no prompts
    --no-home            do not make shangOS the home screen
    --no-widgets         do not grant the optional permissions
    --uninstall          remove shangOS (Home goes back to the TV's own launcher first)
    --help, -h           this text

Every command that changes the TV is printed first (lines starting with +). This script
only ever talks to github.com/madushas/shangos-launcher and to your TV.
USAGE
    exit "${1:-0}"
}
die() {
    echo "shangos setup: $*" >&2
    exit 1
}
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is needed for this and was not found. $2"; }
run() { # prints a command that changes the TV, then runs it
    echo "+ $*"
    "$@"
}
sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

while [ $# -gt 0 ]; do
    case "$1" in
        --tv | -t)
            TV="${2:?--tv needs an address, e.g. 192.168.1.50:5555}"
            shift 2
            ;;
        --variant | -v)
            VARIANT="${2:?--variant is release or offline}"
            case "$VARIANT" in release | offline) ;; *) die "--variant is release or offline" ;; esac
            shift 2
            ;;
        --apk | -a)
            APK="${2:?--apk needs a path}"
            shift 2
            ;;
        --yes | -y)
            ASSUME_YES=1
            shift
            ;;
        --no-home)
            DO_HOME=0
            shift
            ;;
        --no-widgets)
            DO_WIDGETS=0
            shift
            ;;
        --uninstall)
            UNINSTALL=1
            shift
            ;;
        --help | -h) usage 0 ;;
        *) usage 1 >&2 ;;
    esac
done

# ---- Asking questions -------------------------------------------------------------
# Run from a terminal, the questions are the same keyboard menus as ./shangos.sh wizard. Piped
# through curl | bash, they are read from the terminal directly (/dev/tty), because stdin carries
# the script itself. With neither, the script runs on its flags and defaults, and only asks what
# it cannot decide (which TV) by failing with an explanation. SHANGOS_SETUP_SCRIPTED=1 reads the
# answers as plain numbered prompts from stdin, for the automated tests.
UI_FD=0
UI_TTY=0
if [ -t 0 ] && [ -z "${SHANGOS_SETUP_SCRIPTED:-}" ] && [ "${TERM:-xterm}" != "dumb" ]; then
    UI_TTY=1
elif [ -z "${SHANGOS_SETUP_SCRIPTED:-}" ] && [ -r /dev/tty ] && [ "${TERM:-xterm}" != "dumb" ]; then
    exec 3</dev/tty || true
    UI_FD=3
    UI_TTY=1
fi
UI_COLOR=0
[ "$UI_TTY" = 1 ] && [ -t 1 ] && [ -z "${NO_COLOR:-}" ] && UI_COLOR=1
C_RESET='' C_BOLD='' C_DIM='' C_SEL=''
if [ "$UI_COLOR" = 1 ]; then
    C_RESET=$'\033[0m' C_BOLD=$'\033[1m' C_DIM=$'\033[2m'
    C_SEL=$'\033[7m'
fi
READ_ESC_T=1
[ "${BASH_VERSINFO[0]:-0}" -ge 4 ] && READ_ESC_T=0.05

read_key() {
    local c1 c2 c3
    KEY=''
    if ! IFS= read -rsn1 -u "$UI_FD" c1; then
        KEY="eof"
        return
    fi
    [ -n "${c1:-}" ] || {
        KEY="enter"
        return
    }
    case "$c1" in
        $'\033')
            KEY="esc"
            if IFS= read -rsn1 -t "$READ_ESC_T" -u "$UI_FD" c2 && { [ "$c2" = "[" ] || [ "$c2" = "O" ]; }; then
                if IFS= read -rsn1 -t "$READ_ESC_T" -u "$UI_FD" c3; then
                    case "$c3" in
                        A | a) KEY="up" ;;
                        B | b) KEY="down" ;;
                        C | c) KEY="right" ;;
                        D | d) KEY="left" ;;
                        H | h) KEY="home" ;;
                        F | f) KEY="end" ;;
                        [0-9])
                            while IFS= read -rsn1 -t "$READ_ESC_T" -u "$UI_FD" c3 && [ "$c3" != "~" ]; do :; done
                            ;;
                    esac
                fi
            fi
            ;;
        $'\n' | $'\r') KEY="enter" ;;
        $'\177' | $'\010') KEY="back" ;;
        $'\t') KEY="tab" ;;
        $'\003') KEY="ctrl-c" ;;
        j | J) KEY="down" ;;
        k | K) KEY="up" ;;
        *) KEY="$c1" ;;
    esac
}
term_cols() {
    local size
    size="$(stty size </dev/tty 2>/dev/null | cut -d' ' -f2)" || size=""
    case "$size" in '' | 0 | *[!0-9]*) echo 80 ;; *) echo "$size" ;; esac
}
cut_to_fit() {
    local width room
    width="$(term_cols)"
    room="${2:-6}"
    if [ "$width" -ge $((room + 12)) ]; then
        echo "${1:0:$((width - room))}"
    else
        echo "$1"
    fi
}

# choose "Title" option... -> CHOICE (1-based); CANCEL=1 when left with q/Esc
choose() {
    CANCEL=0
    local title="$1"
    shift
    local n=$# i=1 option sel=1 buf='' drew=0 lines hint
    [ "$n" -gt 0 ] || {
        CHOICE=0
        CANCEL=1
        return
    }
    if [ "$UI_TTY" != 1 ]; then
        echo
        echo "$title"
        for option in "$@"; do
            printf '  %d) %s\n' "$i" "$option"
            i=$((i + 1))
        done
        while true; do
            ask "Choose 1-$n:" ""
            if [[ "$REPLY" =~ ^[0-9]+$ ]] && [ "$REPLY" -ge 1 ] && [ "$REPLY" -le "$n" ]; then
                CHOICE="$REPLY"
                return
            fi
            echo "Please type a number from 1 to $n."
        done
    fi
    lines=$((n + 2))
    echo
    while true; do
        if [ "$drew" = 1 ]; then printf '\033[%dA' "$lines"; fi
        printf '\r\033[K%s\n' "${C_BOLD}${title}${C_RESET}"
        i=1
        for option in "$@"; do
            if [ "$i" -eq "$sel" ]; then
                printf '\r\033[K%s\n' "${C_SEL} > $(cut_to_fit "$option") ${C_RESET}"
            else
                printf '\r\033[K   %s \n' "$(cut_to_fit "$option")"
            fi
            i=$((i + 1))
        done
        if [ -n "$buf" ]; then
            hint="  number $buf  (Enter to choose, Esc to leave)"
        else
            hint="  up/down move, Enter choose, or type a number, q to leave"
        fi
        printf '\r\033[K%s\n' "${C_DIM}${hint}${C_RESET}"
        drew=1
        read_key
        case "$KEY" in
            up)
                sel=$((sel - 1))
                [ "$sel" -lt 1 ] && sel=$n
                ;;
            down)
                sel=$((sel + 1))
                [ "$sel" -gt "$n" ] && sel=1
                ;;
            home) sel=1 ;;
            end) sel=$n ;;
            back) buf="${buf%?}" ;;
            [0-9]) [ "${#buf}" -lt 3 ] && buf="$buf$KEY" ;;
            enter)
                if [ -n "$buf" ]; then
                    if [[ "$buf" =~ ^[0-9]+$ ]] && [ "$buf" -ge 1 ] && [ "$buf" -le "$n" ]; then
                        CHOICE="$buf"
                        return
                    fi
                    buf=''
                else
                    CHOICE="$sel"
                    return
                fi
                ;;
            q | Q | esc | ctrl-c | eof)
                CANCEL=1
                CHOICE=0
                return
                ;;
        esac
    done
}

ask() { # ask "Question" [default] -> REPLY
    local answer prompt
    if [ -n "${2:-}" ]; then
        prompt="$1 [$2] "
    else
        prompt="$1 "
    fi
    if [ "$UI_FD" = 0 ] && [ "$UI_TTY" = 1 ]; then
        read -r -e -p "$(printf '%s' "${C_BOLD}${prompt}${C_RESET}")" answer || die "no input"
    else
        read -r -p "$prompt" -u "$UI_FD" answer || die "no input: run from a terminal, or pass --yes (and --tv, --no-home, --no-widgets as needed)"
    fi
    REPLY="$answer"
    [ -n "$REPLY" ] || [ -z "${2:-}" ] || REPLY="$2"
}
yesno() { # yesno "Question" [y|n default] -> exit status; --yes answers the defaults without asking
    local def="${2:-n}" val picked
    if [ "$ASSUME_YES" = 1 ]; then
        [ "$def" = y ]
        return
    fi
    if [ "$UI_TTY" != 1 ]; then
        local hint="y/N"
        [ "$def" = y ] && hint="Y/n"
        ask "$1 ($hint)" ""
        [[ "${REPLY:-$def}" =~ ^[Yy] ]]
        return
    fi
    val="$def"
    while true; do
        if [ "$val" = y ]; then
            printf '\r\033[K%s%s%s  %s(y/n)%s  %s Yes %s   No  ' "$C_BOLD" "$1" "$C_RESET" "$C_DIM" "$C_RESET" "$C_SEL" "$C_RESET"
        else
            printf '\r\033[K%s%s%s  %s(y/n)%s    Yes  %s No %s ' "$C_BOLD" "$1" "$C_RESET" "$C_DIM" "$C_RESET" "$C_SEL" "$C_RESET"
        fi
        read_key
        case "$KEY" in
            y | Y)
                val=y
                break
                ;;
            n | N)
                val=n
                break
                ;;
            left | right | tab) [ "$val" = y ] && val=n || val=y ;;
            enter) break ;;
            esc | q | Q | ctrl-c | eof)
                val="$def"
                break
                ;;
        esac
    done
    picked="no"
    [ "$val" = y ] && picked="yes"
    printf '\r\033[K%s%s -> %s%s\n' "$C_DIM" "$1" "$picked" "$C_RESET"
    [ "$val" = y ]
}

# ---- The TV ----------------------------------------------------------------------
tv() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }
home_now() { tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }
show_home() {
    if tv dumpsys power | grep -q 'mWakefulness=Awake'; then tv input keyevent KEYCODE_HOME; fi
}
state_file() {
    mkdir -p "$CONFIG_DIR"
    chmod 700 "$CONFIG_DIR" 2>/dev/null || true
    echo "$CONFIG_DIR/home-$(tv getprop ro.serialno | tr -cd 'A-Za-z0-9').txt"
}
saved_endpoints() {
    if [ -f "$ENDPOINTS" ]; then grep -E '^[A-Za-z0-9._-]+(:[0-9]{1,5})?$' "$ENDPOINTS" || true; fi
}
connect_endpoint() {
    local endpoint="$1" out
    [[ "$endpoint" =~ ^[A-Za-z0-9._-]+(:[0-9]{1,5})?$ ]] || {
        echo "That does not look like an address (192.168.1.50 or 192.168.1.50:5555)."
        return 1
    }
    case "$endpoint" in *:*) ;; *) endpoint="$endpoint:5555" ;; esac
    echo "+ adb connect $endpoint"
    out="$(adb connect "$endpoint" 2>&1 || true)"
    echo "$out"
    case "$out" in
        *"connected to"*)
            DEVICE="$endpoint"
            if ! saved_endpoints | grep -qx "$endpoint" && yesno "Remember $endpoint for next time?" n; then
                mkdir -p "$CONFIG_DIR"
                chmod 700 "$CONFIG_DIR" 2>/dev/null || true
                echo "$endpoint" >>"$ENDPOINTS"
                chmod 600 "$ENDPOINTS" 2>/dev/null || true
            fi
            return 0
            ;;
    esac
    echo "Could not connect. On the TV: Settings → System → About, press Build 7 times, then Developer options → USB debugging."
    return 1
}
pick_tv() {
    if [ -n "$TV" ]; then
        connect_endpoint "$TV" || die "could not reach $TV"
        return
    fi
    local list lines serials labels saved line serial model i n_dev n_saved total options=()
    list="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    lines="$(adb devices -l | awk 'NR>1 && $2=="device"')"
    serials=()
    labels=()
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        serial="${line%% *}"
        model="$(sed -n 's/.*model:\([^ ]*\).*/\1/p' <<<"$line")"
        serials+=("$serial")
        labels+=("$serial${model:+  ($model)}")
    done <<<"$lines"
    saved=()
    while IFS= read -r line; do [ -n "$line" ] && saved+=("$line"); done < <(saved_endpoints)
    n_dev=${#serials[@]}
    n_saved=${#saved[@]}
    if [ "$UI_TTY" != 1 ] || [ "$ASSUME_YES" = 1 ]; then # nothing to ask (no terminal, or --yes): the flags have to say
        [ "$n_dev" -gt 0 ] || die "no TV found. Turn on ADB debugging on the TV (Settings → System → Developer options → USB debugging), then retry with --tv <TV IP>:5555"
        [ "$n_dev" = 1 ] || die "several TVs are connected ($list); choose one with --tv ADDRESS"
        DEVICE="${serials[0]}"
        return
    fi
    while true; do
        options=()
        for i in "${labels[@]+"${labels[@]}"}"; do options+=("TV: $i"); done
        for i in "${saved[@]+"${saved[@]}"}"; do options+=("Connect to saved address $i"); done
        options+=("Connect to another address (IP or host[:port])" "Refresh the list" "Quit")
        total=${#options[@]}
        choose "Which TV?" "${options[@]}"
        if [ "$CANCEL" = 1 ]; then
            echo "Nothing was changed."
            exit 0
        fi
        if [ "$CHOICE" -le "$n_dev" ]; then
            DEVICE="${serials[$((CHOICE - 1))]}"
            return
        elif [ "$CHOICE" -le $((n_dev + n_saved)) ]; then
            connect_endpoint "${saved[$((CHOICE - n_dev - 1))]}" && return
        elif [ "$CHOICE" -eq $((total - 2)) ]; then
            ask "TV address:" ""
            connect_endpoint "$REPLY" && return
        elif [ "$CHOICE" -eq "$total" ]; then
            echo "Nothing was changed."
            exit 0
        fi
    done
}

# ---- The actions -----------------------------------------------------------------
download_release() {
    need curl "Install curl, or pass --apk with an APK you already have."
    local json apk_url sums_url name sums expected actual
    json="$(curl -fsSL -H 'Accept: application/vnd.github+json' "https://api.github.com/repos/$REPO/releases/latest")" || {
        echo "Could not read the latest release (no internet, rate-limited, or no release published yet)."
        return 1
    }
    apk_url="$(grep -o "\"browser_download_url\": *\"[^\"]*shangos-[^\"/]*-$VARIANT\\.apk\"" <<<"$json" | head -n 1 | sed 's/.*: *"//; s/"$//')"
    sums_url="$(grep -o '"browser_download_url": *"[^"]*SHA256SUMS"' <<<"$json" | head -n 1 | sed 's/.*: *"//; s/"$//')"
    if [ -z "$apk_url" ] || [ -z "$sums_url" ]; then
        echo "The latest release has no $VARIANT APK with a checksum file."
        return 1
    fi
    case "$apk_url$sums_url" in *"github.com/$REPO/"*) ;; *)
        echo "Refusing a download that is not from github.com/$REPO."
        return 1
        ;;
    esac
    name="$(basename "$apk_url")"
    echo "+ curl $apk_url"
    curl -fSL -o "$TMP/$name" "$apk_url" || return 1
    sums="$(curl -fsSL "$sums_url")" || return 1
    expected="$(grep " $name\$" <<<"$sums" | head -n 1 | cut -d' ' -f1)"
    actual="$(sha256 "$TMP/$name")"
    if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
        rm -f "$TMP/$name"
        echo "The download does not match its published checksum, so it was deleted."
        return 1
    fi
    echo "Checksum verified: $actual"
    if command -v apksigner >/dev/null 2>&1; then
        echo "Signing certificate (also on the release page and in the app: Settings → Privacy → This build):"
        apksigner verify --print-certs "$TMP/$name" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: /  SHA-256: /p'
    fi
    APK="$TMP/$name"
}

do_install() {
    run adb -s "$DEVICE" install --no-incremental -r "$1" || die "install failed. If it says the signatures do not match, the TV has a build signed with another key: adb -s $DEVICE uninstall $PKG (this clears shangOS's settings), then run this again."
    if [ "${SHANGOS_AOT:-1}" = 1 ]; then
        echo "Compiling ahead of time (a few seconds)..."
        tv cmd package compile -m speed -f "$PKG" >/dev/null || echo "(AOT compile not available on this TV; harmless)"
    fi
    echo "Installed $(basename "$1")."
}
do_widgets() {
    run adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_TV_LISTINGS && echo "Up Next: Continue watching from your apps."
    run adb -s "$DEVICE" shell cmd notification allow_listener "$PKG/$PKG.MediaListener" && echo "Now Playing: music playing on the TV."
    run adb -s "$DEVICE" shell appops set "$PKG" GET_USAGE_STATS allow && echo "Up Next: recently used apps."
}
do_home() {
    local state current pkg
    state="$(state_file)"
    if [ ! -s "$state" ]; then echo "original=$(home_now)" >"$state"; fi
    run adb -s "$DEVICE" shell cmd package set-home-activity --user 0 "$SHANGOS" >/dev/null
    for _ in 1 2 3 4; do
        current="$(home_now)"
        [ "$current" = "$SHANGOS" ] && break
        pkg="${current%%/*}"
        case " $KNOWN " in
            *" $pkg "*)
                echo "Switching off $pkg, which the TV puts above any other home screen."
                run adb -s "$DEVICE" shell pm disable-user --user 0 "$pkg" >/dev/null
                echo "disabled=$pkg" >>"$state"
                run adb -s "$DEVICE" shell cmd package set-home-activity --user 0 "$SHANGOS" >/dev/null
                ;;
            *) die "the TV still opens $current at Home. Nothing else was changed; re-run with --uninstall to undo." ;;
        esac
    done
    [ "$(home_now)" = "$SHANGOS" ] || die "the TV did not accept shangOS as its home screen. Re-run with --uninstall to undo."
    show_home
    echo "shangOS is the home screen."
}
do_restore() {
    local state disabled original pkg
    state="$(state_file)"
    if [ -s "$state" ]; then
        disabled="$(sed -n 's/^disabled=//p' "$state")"
        original="$(sed -n 's/^original=//p' "$state" | head -n 1)"
    else
        disabled=""
        original=""
        for pkg in $KNOWN; do installed "$pkg" && tv pm list packages -d "$pkg" | grep -qx "package:$pkg" && disabled="$disabled $pkg"; done
    fi
    for pkg in $disabled; do
        echo "Switching $pkg back on."
        run adb -s "$DEVICE" shell pm enable --user 0 "$pkg" >/dev/null || true
    done
    if [ -n "$original" ] && [ "$original" != "$SHANGOS" ] && [[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]]; then
        run adb -s "$DEVICE" shell cmd package set-home-activity --user 0 "$original" >/dev/null || true
    fi
    rm -f "$state"
    show_home
    echo "Home now opens $(home_now)."
}

# ---- The script ------------------------------------------------------------------
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; stty echo 2>/dev/null || true' EXIT
trap 'echo; echo "Stopped. Nothing further was changed."; exit 130' INT TERM

need adb "Install the Android platform-tools: https://developer.android.com/tools/releases/platform-tools"
echo
printf '%s\n' "${C_BOLD}  shangOS setup${C_RESET}"
printf '%s\n' "${C_DIM}  --------------${C_RESET}"
echo "  Downloads the newest signed release from github.com/$REPO and installs it on your TV."
echo "  Every command that changes the TV is shown (a line starting with +) before it runs."
echo

pick_tv
echo "Using $DEVICE"
echo "TV:   $(tv getprop ro.product.manufacturer) $(tv getprop ro.product.model), Android $(tv getprop ro.build.version.release)"

if [ "$UNINSTALL" = 1 ]; then
    if ! installed "$PKG"; then die "shangOS is not installed on this TV"; fi
    if [ -s "$(state_file)" ] || [ "$(home_now)" = "$SHANGOS" ]; then
        echo "shangOS is the home screen: giving Home back to the TV's own launcher first."
        do_restore
    fi
    run adb -s "$DEVICE" uninstall "$PKG"
    echo "Removed. The TV's own launcher opens at Home again."
    exit 0
fi

if [ -n "$APK" ]; then
    [ -f "$APK" ] || die "no APK at $APK"
elif ! download_release; then
    die "could not get a verified release APK. Download one from https://github.com/$REPO/releases/latest and pass --apk <file>"
fi
do_install "$APK"
if [ "$DO_WIDGETS" = 1 ] && yesno "Allow the optional permissions (Up Next, Now Playing)?" y; then do_widgets; fi
if [ "$DO_HOME" = 1 ] && yesno "Make shangOS the home screen now?" y; then do_home; fi
echo
echo "Done. On the TV: Settings (or press the gear) walks you through the rest;"
echo "undo any time with: bash setup.sh --uninstall"
