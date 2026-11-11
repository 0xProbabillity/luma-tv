#!/usr/bin/env bash
# shangOS setup wizard, from any Mac or Linux computer. One line:
#
#     curl -fsSL https://shangos.madusha.dev/setup.sh | bash
#
# On Windows use setup.ps1 (PowerShell). Both do the same thing, in the same order:
#   1. find your TV (adb devices, wireless-debugging discovery, saved addresses, or type one)
#   2. check it (Android 12+, TV, what is installed now)
#   3. pick the build and what to switch on
#   4. show the exact commands, ask once
#   5. download the signed release from github.com/madushas/shangos-launcher, verify its SHA-256,
#      install it, grant the optional permissions, make it the home screen
#
# Every command that changes the TV is printed first (dim lines starting with +). The script only
# talks to GitHub (github.com/madushas/shangos-launcher, served from GitHub's CDN) and to your TV; the single exception is your own
# package manager, if you let it install adb. No telemetry. The APK sits in a temporary folder that
# is deleted on exit. What it remembers (addresses you save, how to undo Home) lives in
# ~/.config/shangos.
#
# Without a terminal (CI, pipes) it runs on flags alone:
#     bash setup.sh --tv 192.168.1.50:5555 --variant offline --yes
# Undo everything (Home goes back to the TV's own launcher first):
#     bash setup.sh --uninstall [--tv 192.168.1.50:5555]
# Prefer reading it first?
#     curl -fsSL -o setup.sh https://shangos.madusha.dev/setup.sh && less setup.sh && bash setup.sh
set -u -o pipefail

# Safe defaults: die() and the cursor helpers can run before the terminal is set up.
UI=0
C_RESET='' C_ERR='' G_ERR='x'

SETUP_VERSION="2.1"
PKG="dev.madusha.shangos"
SHANGOS="$PKG/.MainActivity"
AMBIENT="$PKG/$PKG.AmbientDream"
REPO="madushas/shangos-launcher"
# System homes this script knows: restore switches them back on; it switches none off unless you pass --disable-stock.
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"
# Only these may be switched off, and only with --disable-stock. Never the setup app: some Google TVs cannot start without it.
DISABLABLE="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"
MIN_SDK=31 # Android 12
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/shangos"
ENDPOINTS="$CONFIG_DIR/endpoints"

VARIANT="release"
RELEASE_TAG=""
TV=""
APK=""
ACTION="" # install | uninstall | status | pair ; empty = ask
PAIR_ADDR=""
PAIR_CODE=""
DO_HOME=1
DISABLE_STOCK=0
DO_WIDGETS=1
DO_AOT=1
DO_SAVER=0
DO_DNS=0 # 1 = also turn on ad and tracker blocking (Private DNS) while installing
DNS_CHOICE="" # --dns: adguard, a hostname, or off
ASSUME_YES=0
PLAIN=0
NOCOLOR=0
DEVICE=""
TMP=""
CHANGED=0 # becomes 1 once a command that changes the TV has been started

usage() {
    cat <<'USAGE'
shangOS setup wizard (Mac and Linux; Windows has setup.ps1).

    curl -fsSL https://shangos.madusha.dev/setup.sh | bash

With no options it opens the guided wizard. Options:
    --tv, -t ADDRESS     the TV's adb address (192.168.1.50 or 192.168.1.50:5555)
    --variant, -v NAME   release (default) or offline (no internet permission at all)
    --release TAG        install this release (v0.4.0) instead of the latest
    --apk, -a PATH       install this APK instead of downloading a release
    --yes, -y            no questions: take every default (needs --tv unless one TV is connected)
    --no-home            do not make shangOS the home screen
    --disable-stock      only if the TV keeps opening its own home: switch that launcher off (never the setup app).
                         Some Google TVs cannot restart without it and loop, so it asks first; undo with --uninstall
    --no-widgets         do not grant the optional permissions
    --no-aot             skip the ahead-of-time compile
    --saver              also use the shangOS screen saver
    --block-ads          also turn on ad and tracker blocking for the whole TV (Private DNS, AdGuard DNS)
    --dns CHOICE         only change the TV's Private DNS: adguard, your own hostname, or off (back to automatic)
    --status             show what is installed on the TV and what Home opens
    --uninstall          remove shangOS (Home goes back to the TV's own launcher first)
    --pair HOST:PORT     pair with a TV that asks for a pairing code (Wireless debugging)
    --code NNNNNN        the six-digit pairing code (asked for when left out)
    --plain              no colours, no menus: plain text
    --no-color           keep the menus, drop the colours (NO_COLOR works too)
    --version            print the wizard's version
    --help, -h           this text

Every command that changes the TV is printed first (lines starting with +). This script
only ever talks to GitHub (github.com/madushas/shangos-launcher; GitHub serves the files from its own CDN) and to your TV.
USAGE
    exit "${1:-0}"
}

die() {
    ui_cursor_show
    printf '%s\n' "${C_ERR}${G_ERR} shangos setup: $*${C_RESET}" >&2
    exit 1
}
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is needed for this and was not found. ${2:-}"; }

# ---- Arguments ----------------------------------------------------------------------
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
        --release)
            RELEASE_TAG="${2:?--release needs a tag, e.g. v0.4.0}"
            case "$RELEASE_TAG" in [Vv]*) ;; *) RELEASE_TAG="v$RELEASE_TAG" ;; esac
            shift 2
            ;;
        --apk | -a)
            APK="${2:?--apk needs a path}"
            shift 2
            ;;
        --pair)
            PAIR_ADDR="${2:?--pair needs HOST:PORT (shown on the TV: Wireless debugging, Pair with pairing code)}"
            ACTION="pair"
            shift 2
            ;;
        --code)
            PAIR_CODE="${2:?--code needs the six-digit pairing code}"
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
        --disable-stock)
            DISABLE_STOCK=1
            shift
            ;;
        --no-widgets)
            DO_WIDGETS=0
            shift
            ;;
        --no-aot)
            DO_AOT=0
            shift
            ;;
        --saver)
            DO_SAVER=1
            shift
            ;;
        --block-ads)
            DO_DNS=1
            shift
            ;;
        --dns)
            DNS_CHOICE="${2:?--dns needs adguard, a hostname (dns.example.com) or off}"
            ACTION="dns"
            shift 2
            ;;
        --status)
            ACTION="status"
            shift
            ;;
        --uninstall)
            ACTION="uninstall"
            shift
            ;;
        --plain)
            PLAIN=1
            shift
            ;;
        --no-color)
            NOCOLOR=1
            shift
            ;;
        --version)
            echo "shangOS setup $SETUP_VERSION"
            exit 0
            ;;
        --help | -h) usage 0 ;;
        *) usage 1 >&2 ;;
    esac
done
[ -n "$APK" ] && [ -n "$RELEASE_TAG" ] && die "--apk and --release do not go together"

# ---- Terminal -----------------------------------------------------------------------
# Rich mode needs a terminal on stdout and a keyboard: stdin, or /dev/tty when the script itself
# arrives on stdin (curl | bash). Anything else runs on flags. SHANGOS_SETUP_SCRIPTED=1 reads
# numbered answers from stdin instead (for tests).
UI_FD=0
SCRIPTED=0
[ -n "${SHANGOS_SETUP_SCRIPTED:-}" ] && SCRIPTED=1
if [ "$PLAIN" = 0 ] && [ "$SCRIPTED" = 0 ] && [ "${TERM:-dumb}" != dumb ] && [ -t 1 ]; then
    if [ -t 0 ]; then
        UI=1
    elif [ -r /dev/tty ] && { : </dev/tty; } 2>/dev/null; then
        exec 3</dev/tty
        UI_FD=3
        UI=1
    fi
fi

C_RESET='' C_BOLD='' C_DIM='' C_ACC='' C_OK='' C_WARN='' C_ERR='' C_INV=''
if [ "$UI" = 1 ] && [ -z "${NO_COLOR:-}" ] && [ "$NOCOLOR" = 0 ]; then
    ESC=$'\033'
    C_RESET="${ESC}[0m" C_BOLD="${ESC}[1m" C_DIM="${ESC}[2m" C_INV="${ESC}[7m"
    C_OK="${ESC}[32m" C_WARN="${ESC}[33m" C_ERR="${ESC}[31m" C_ACC="${ESC}[36m"
    case "${COLORTERM:-}${TERM:-}" in *256* | *truecolor* | *24bit*) C_ACC="${ESC}[38;5;105m" ;; esac
fi
UTF8=0
case "${LC_ALL:-${LC_CTYPE:-${LANG:-}}}" in *[Uu][Tt][Ff]-8* | *[Uu][Tt][Ff]8*) UTF8=1 ;; esac
if [ "$UTF8" = 1 ]; then
    G_OK="✓" G_ERR="✗" G_WARN="!" G_ON="●" G_OFF="○" G_PTR="›" G_FULL="█" G_EMPTY="░" G_H="─" G_V="│"
    G_TL="╭" G_TR="╮" G_BL="╰" G_BR="╯" G_CHK="◼" G_UNCHK="◻"
    SPIN=("⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏")
else
    G_OK="v" G_ERR="x" G_WARN="!" G_ON="*" G_OFF="o" G_PTR=">" G_FULL="#" G_EMPTY="-" G_H="-" G_V="|"
    G_TL="+" G_TR="+" G_BL="+" G_BR="+" G_CHK="[x]" G_UNCHK="[ ]"
    SPIN=("|" "/" "-" "\\")
fi
READ_ESC_T=1
[ "${BASH_VERSINFO[0]:-0}" -ge 4 ] && READ_ESC_T=0.05

ui_cursor_hide() { [ "$UI" = 1 ] && printf '\033[?25l'; return 0; }
ui_cursor_show() { [ "$UI" = 1 ] && printf '\033[?25h'; return 0; }
cols() {
    local c=""
    c="$(tput cols 2>/dev/null)" || c=""
    case "$c" in '' | *[!0-9]*) c="${COLUMNS:-80}" ;; esac
    [ "$c" -lt 40 ] && c=40
    [ "$c" -gt 100 ] && c=100
    echo "$c"
}
repeat() { # repeat STRING COUNT
    local s="" i=0
    while [ "$i" -lt "$2" ]; do
        s="$s$1"
        i=$((i + 1))
    done
    printf '%s' "$s"
}
fit() { # fit TEXT [reserve]: cut plain text so a line never wraps (a wrapped line breaks redrawing)
    local w room="${2:-4}" t="$1"
    w="$(cols)"
    if [ "${#t}" -gt $((w - room)) ]; then t="${t:0:$((w - room - 1))}…"; [ "$UTF8" = 1 ] || t="${t%…}~"; fi
    printf '%s' "$t"
}
say() { printf '%s\n' "$*"; }
note() { printf '%s\n' "${C_DIM}  $*${C_RESET}"; }
good() { printf '%s\n' "${C_OK}${G_OK}${C_RESET} $*"; }
warn() { printf '%s\n' "${C_WARN}${G_WARN}${C_RESET} $*"; }
bad() { printf '%s\n' "${C_ERR}${G_ERR}${C_RESET} $*"; }
cmdline() { printf '%s\n' "${C_DIM}  + $*${C_RESET}"; } # a command that changes the TV, shown first

box() { # box TITLE LINES... : a rounded box that fits the terminal
    local title="$1" w inner line pad
    shift
    w="$(cols)"
    [ "$w" -gt 78 ] && w=78
    inner=$((w - 4))
    printf '%s\n' "${C_ACC}${G_TL}${G_H} ${C_BOLD}${title}${C_RESET}${C_ACC} $(repeat "$G_H" $((inner - ${#title} - 2)))${G_H}${G_TR}${C_RESET}"
    for line in "$@"; do
        [ -n "$line" ] || line=" "
        while IFS= read -r part; do
            pad=$((inner - ${#part}))
            [ "$pad" -lt 0 ] && pad=0
            printf '%s\n' "${C_ACC}${G_V}${C_RESET} ${part}$(repeat ' ' "$pad") ${C_ACC}${G_V}${C_RESET}"
        done < <(printf '%s\n' "$line" | fold -s -w "$inner")
    done
    printf '%s\n' "${C_ACC}${G_BL}$(repeat "$G_H" $((inner + 2)))${G_BR}${C_RESET}"
}
banner() {
    echo
    if [ "$UI" = 1 ]; then
        printf '%s\n' "  ${C_ACC}${C_BOLD}${G_ON} shangOS${C_RESET}  ${C_DIM}setup wizard $SETUP_VERSION${C_RESET}"
        printf '%s\n' "  ${C_DIM}A calm home screen for Android TV and Google TV${C_RESET}"
    else
        say "shangOS setup wizard $SETUP_VERSION"
    fi
    echo
}
STEPS=("TV" "Build" "Options" "Review" "Install")
stepbar() { # stepbar N (1-based)
    local i=1 s="" n="$1" w
    w="$(cols)"
    if [ "$w" -lt 62 ]; then
        printf '%s\n' "${C_DIM}  Step $n of ${#STEPS[@]}: ${STEPS[$((n - 1))]}${C_RESET}"
        return
    fi
    for name in "${STEPS[@]}"; do
        if [ "$i" -lt "$n" ]; then
            s="$s${C_OK}${G_OK} ${name}${C_RESET}"
        elif [ "$i" -eq "$n" ]; then
            s="$s${C_ACC}${C_BOLD}${G_ON} ${name}${C_RESET}"
        else
            s="$s${C_DIM}${G_OFF} ${name}${C_RESET}"
        fi
        [ "$i" -lt "${#STEPS[@]}" ] && s="$s${C_DIM} ${G_H}${G_H} ${C_RESET}"
        i=$((i + 1))
    done
    printf '%s\n\n' "  $s"
}

# ---- Keys and prompts -----------------------------------------------------------------
KEY=''
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
        ' ') KEY="space" ;;
        j | J) KEY="down" ;;
        k | K) KEY="up" ;;
        *) KEY="$c1" ;;
    esac
}

ask() { # ask "Question" [default] -> REPLY
    local prompt="$1"
    [ -n "${2:-}" ] && prompt="$1 [$2]"
    if [ "$UI" = 1 ]; then
        ui_cursor_show
        if [ "${BASH_VERSINFO[0]:-0}" -ge 4 ]; then
            IFS= read -r -e -p "  ${C_BOLD}${prompt}${C_RESET} " -u "$UI_FD" REPLY || die "no input"
        else
            IFS= read -r -p "  ${C_BOLD}${prompt}${C_RESET} " -u "$UI_FD" REPLY || die "no input"
        fi
    else
        IFS= read -r -p "$prompt " REPLY || die "no input: run from a terminal, or pass flags (see --help)"
    fi
    [ -n "$REPLY" ] || [ -z "${2:-}" ] || REPLY="$2"
}

# menu TITLE OPTION... -> CHOICE (1-based). Return 0 chosen, 1 back (Esc/left), 2 quit (q/Ctrl-C).
# MENU_DESC (array, optional, same order) shows a dim explanation of the highlighted option.
# MENU_START picks the first highlight. The finished menu collapses to one summary line.
MENU_DESC=()
MENU_START=1
CHOICE=0
menu() {
    local title="$1"
    shift
    local n=$# i sel="$MENU_START" drawn=0 buf="" opt desc hint
    local -a opts=("$@")
    [ "$n" -gt 0 ] || return 1
    if [ "$UI" != 1 ]; then
        say
        say "$title"
        i=1
        for opt in "${opts[@]}"; do
            printf '  %d) %s\n' "$i" "$opt"
            i=$((i + 1))
        done
        while true; do
            ask "Choose 1-$n:" ""
            if [[ "$REPLY" =~ ^[0-9]+$ ]] && [ "$REPLY" -ge 1 ] && [ "$REPLY" -le "$n" ]; then
                CHOICE="$REPLY"
                MENU_START=1
                return 0
            fi
            case "$REPLY" in q | Q) return 2 ;; esac
            say "Please type a number from 1 to $n."
        done
    fi
    ui_cursor_hide
    while true; do
        [ "$drawn" -gt 0 ] && printf '\033[%dA' "$drawn"
        printf '\r\033[K%s\n' "  ${C_BOLD}${title}${C_RESET}"
        i=1
        for opt in "${opts[@]}"; do
            if [ "$i" -eq "$sel" ]; then
                printf '\r\033[K%s\n' "  ${C_ACC}${G_PTR}${C_RESET} ${C_INV}${C_BOLD} $(fit "$opt" 10) ${C_RESET}"
            else
                printf '\r\033[K%s\n' "    $(fit "$opt" 10)"
            fi
            i=$((i + 1))
        done
        desc="${MENU_DESC[$((sel - 1))]:-}"
        printf '\r\033[K%s\n' "  ${C_DIM}$(fit "$desc" 6)${C_RESET}"
        if [ -n "$buf" ]; then
            hint="number $buf: Enter to choose"
        else
            hint="up/down move ${G_H} Enter choose ${G_H} Esc back ${G_H} q quit"
        fi
        printf '\r\033[K%s\n' "  ${C_DIM}$(fit "$hint" 6)${C_RESET}"
        drawn=$((n + 3))
        read_key
        case "$KEY" in
            up)
                sel=$((sel - 1))
                [ "$sel" -lt 1 ] && sel=$n
                buf=""
                ;;
            down)
                sel=$((sel + 1))
                [ "$sel" -gt "$n" ] && sel=1
                buf=""
                ;;
            home) sel=1 ;;
            end) sel=$n ;;
            back) buf="${buf%?}" ;;
            [0-9]) [ "${#buf}" -lt 3 ] && buf="$buf$KEY" ;;
            enter)
                if [ -n "$buf" ]; then
                    if [ "$buf" -ge 1 ] && [ "$buf" -le "$n" ]; then
                        sel="$buf"
                    else
                        buf=""
                        continue
                    fi
                fi
                CHOICE="$sel"
                printf '\033[%dA\033[J' "$drawn"
                printf '%s\n' "  ${C_OK}${G_OK}${C_RESET} ${title} ${C_DIM}${G_PTR}${C_RESET} ${C_BOLD}$(fit "${opts[$((sel - 1))]}" "$((${#title} + 10))")${C_RESET}"
                ui_cursor_show
                MENU_START=1
                return 0
                ;;
            esc | left)
                printf '\033[%dA\033[J' "$drawn"
                ui_cursor_show
                MENU_START=1
                return 1
                ;;
            q | Q | ctrl-c | eof)
                printf '\033[%dA\033[J' "$drawn"
                ui_cursor_show
                MENU_START=1
                return 2
                ;;
        esac
    done
}

# checklist TITLE OPTION... with CHK (array of 1/0) preset by the caller. Space toggles, a = all/none.
# Return 0 done, 1 back, 2 quit. Without a terminal each option is asked as yes/no.
CHK=()
checklist() {
    local title="$1"
    shift
    local n=$# i sel=1 drawn=0 opt allon
    local -a opts=("$@")
    if [ "$UI" != 1 ]; then
        i=0
        for opt in "${opts[@]}"; do
            if confirm "$opt" "$([ "${CHK[$i]}" = 1 ] && echo y || echo n)"; then CHK[i]=1; else CHK[i]=0; fi
            i=$((i + 1))
        done
        return 0
    fi
    ui_cursor_hide
    while true; do
        [ "$drawn" -gt 0 ] && printf '\033[%dA' "$drawn"
        printf '\r\033[K%s\n' "  ${C_BOLD}${title}${C_RESET}"
        i=1
        for opt in "${opts[@]}"; do
            local mark="$G_UNCHK" col="$C_DIM"
            [ "${CHK[$((i - 1))]}" = 1 ] && mark="$G_CHK" col="$C_OK"
            if [ "$i" -eq "$sel" ]; then
                printf '\r\033[K%s\n' "  ${C_ACC}${G_PTR}${C_RESET} ${col}${mark}${C_RESET} ${C_BOLD}$(fit "$opt" 12)${C_RESET}"
            else
                printf '\r\033[K%s\n' "    ${col}${mark}${C_RESET} $(fit "$opt" 12)"
            fi
            i=$((i + 1))
        done
        printf '\r\033[K%s\n' "  ${C_DIM}$(fit "${MENU_DESC[$((sel - 1))]:-}" 6)${C_RESET}"
        printf '\r\033[K%s\n' "  ${C_DIM}$(fit "up/down move ${G_H} Space switch ${G_H} a all/none ${G_H} Enter continue ${G_H} Esc back" 6)${C_RESET}"
        drawn=$((n + 3))
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
            space | right) if [ "${CHK[$((sel - 1))]}" = 1 ]; then CHK[sel - 1]=0; else CHK[sel - 1]=1; fi ;;
            a | A)
                allon=1
                for ((i = 0; i < n; i++)); do [ "${CHK[$i]}" = 1 ] || allon=0; done
                for ((i = 0; i < n; i++)); do CHK[i]=$((1 - allon)); done
                ;;
            enter)
                printf '\033[%dA\033[J' "$drawn"
                ui_cursor_show
                local on=""
                for ((i = 0; i < n; i++)); do [ "${CHK[$i]}" = 1 ] && on="${on:+$on, }${opts[$i]%% (*}"; done
                printf '%s\n' "  ${C_OK}${G_OK}${C_RESET} ${title} ${C_DIM}${G_PTR}${C_RESET} ${C_BOLD}$(fit "${on:-nothing}" "$((${#title} + 10))")${C_RESET}"
                return 0
                ;;
            esc | left)
                printf '\033[%dA\033[J' "$drawn"
                ui_cursor_show
                return 1
                ;;
            q | Q | ctrl-c | eof)
                printf '\033[%dA\033[J' "$drawn"
                ui_cursor_show
                return 2
                ;;
        esac
    done
}

# confirm "Question" [y|n default]: exit status 0 for yes. --yes answers the default without asking.
confirm() {
    local def="${2:-n}" val picked hint="y/N"
    if [ "$ASSUME_YES" = 1 ]; then
        [ "$def" = y ]
        return
    fi
    if [ "$UI" != 1 ] && [ "$SCRIPTED" = 0 ]; then # no keyboard: the default, as --yes would
        [ "$def" = y ]
        return
    fi
    if [ "$UI" != 1 ]; then
        [ "$def" = y ] && hint="Y/n"
        ask "$1 ($hint)" ""
        [[ "${REPLY:-$def}" =~ ^[Yy] ]]
        return
    fi
    val="$def"
    ui_cursor_hide
    while true; do
        if [ "$val" = y ]; then
            printf '\r\033[K%s' "  ${C_BOLD}$(fit "$1" 30)${C_RESET}  ${C_INV}${C_BOLD} Yes ${C_RESET}  ${C_DIM}No${C_RESET}   ${C_DIM}(y/n)${C_RESET}"
        else
            printf '\r\033[K%s' "  ${C_BOLD}$(fit "$1" 30)${C_RESET}  ${C_DIM}Yes${C_RESET}  ${C_INV}${C_BOLD} No ${C_RESET}   ${C_DIM}(y/n)${C_RESET}"
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
            left | right | tab | space) [ "$val" = y ] && val=n || val=y ;;
            enter) break ;;
            esc | q | Q | ctrl-c | eof)
                val=n
                break
                ;;
        esac
    done
    picked="no"
    [ "$val" = y ] && picked="yes"
    printf '\r\033[K%s\n' "  ${C_OK}${G_OK}${C_RESET} $(fit "$1" 24) ${C_DIM}${G_PTR}${C_RESET} ${C_BOLD}${picked}${C_RESET}"
    ui_cursor_show
    [ "$val" = y ]
}

# ---- Progress -------------------------------------------------------------------------
STEP_OUT=""
step() { # step "Label" command... : runs with a spinner; output kept in STEP_OUT; status is the command's
    local label="$1" pid i=0 rc start now secs
    shift
    STEP_OUT="$TMP/step.out"
    if [ "$UI" != 1 ]; then
        say "- $label"
        "$@" >"$STEP_OUT" 2>&1
        rc=$?
        if [ "$rc" = 0 ]; then say "  ok"; else say "  failed"; fi
        return "$rc"
    fi
    start="$(date +%s)"
    "$@" >"$STEP_OUT" 2>&1 &
    pid=$!
    ui_cursor_hide
    while kill -0 "$pid" 2>/dev/null; do
        printf '\r\033[K%s' "  ${C_ACC}${SPIN[$((i % ${#SPIN[@]}))]}${C_RESET} $(fit "$label" 8)"
        i=$((i + 1))
        sleep 0.1
    done
    wait "$pid"
    rc=$?
    now="$(date +%s)"
    secs=$((now - start))
    local took=""
    [ "$secs" -ge 2 ] && took=" ${C_DIM}${secs}s${C_RESET}"
    if [ "$rc" = 0 ]; then
        printf '\r\033[K%s\n' "  ${C_OK}${G_OK}${C_RESET} $(fit "$label" 14)$took"
    else
        printf '\r\033[K%s\n' "  ${C_ERR}${G_ERR}${C_RESET} $(fit "$label" 14)"
    fi
    ui_cursor_show
    return "$rc"
}
show_tail() { # the last lines of a failed step, indented and dim
    [ -s "$STEP_OUT" ] || return 0
    tr -d '\r' <"$STEP_OUT" | tail -n 6 | while IFS= read -r l; do printf '%s\n' "${C_DIM}    $(fit "$l" 8)${C_RESET}"; done
}
fmt_size() { # bytes -> 2.4 MB
    awk -v b="$1" 'BEGIN{ if (b>=1048576) printf "%.1f MB", b/1048576; else printf "%.0f KB", b/1024 }'
}
file_size() { { wc -c <"$1"; } 2>/dev/null | tr -d ' '; }
download() { # download LABEL URL DEST : a progress bar in a terminal, a plain curl otherwise
    local label="$1" url="$2" dest="$3" total cur pct w barw fill pid rc=0 i=0
    cmdline "curl $url"
    if [ "$UI" != 1 ]; then
        curl -fSL -o "$dest" "$url"
        return
    fi
    total="$(curl -fsIL "$url" 2>/dev/null | tr -d '\r' | awk 'tolower($1)=="content-length:"{n=$2} END{print n+0}')"
    curl -fsSL -o "$dest" "$url" 2>"$TMP/curl.err" &
    pid=$!
    ui_cursor_hide
    w="$(cols)"
    barw=$((w - 34))
    [ "$barw" -gt 40 ] && barw=40
    [ "$barw" -lt 8 ] && barw=8
    while kill -0 "$pid" 2>/dev/null; do
        cur="$(file_size "$dest")"
        cur="${cur:-0}"
        if [ "${total:-0}" -gt 0 ]; then
            pct=$((cur * 100 / total))
            [ "$pct" -gt 100 ] && pct=100
            fill=$((pct * barw / 100))
            printf '\r\033[K%s' "  ${C_ACC}${SPIN[$((i % ${#SPIN[@]}))]}${C_RESET} ${label} ${C_ACC}$(repeat "$G_FULL" "$fill")${C_DIM}$(repeat "$G_EMPTY" $((barw - fill)))${C_RESET} ${pct}% ${C_DIM}$(fmt_size "$cur")${C_RESET}"
        else
            printf '\r\033[K%s' "  ${C_ACC}${SPIN[$((i % ${#SPIN[@]}))]}${C_RESET} ${label} ${C_DIM}$(fmt_size "$cur")${C_RESET}"
        fi
        i=$((i + 1))
        sleep 0.1
    done
    wait "$pid" || rc=$?
    if [ "$rc" = 0 ]; then
        printf '\r\033[K%s\n' "  ${C_OK}${G_OK}${C_RESET} ${label} ${C_DIM}$(fmt_size "$(file_size "$dest")")${C_RESET}"
    else
        printf '\r\033[K%s\n' "  ${C_ERR}${G_ERR}${C_RESET} ${label}"
        [ -s "$TMP/curl.err" ] && note "$(head -n 1 "$TMP/curl.err")"
    fi
    ui_cursor_show
    return "$rc"
}

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | cut -d' ' -f1
    else
        openssl dgst -sha256 "$1" | sed 's/.*= *//'
    fi
}

# ---- adb ------------------------------------------------------------------------------
ADB_INSTALL_CMD=()
# shellcheck disable=SC2206 # $sudo is empty or one word on purpose
adb_install_hint() { # sets ADB_INSTALL_CMD to this computer's way of getting adb, or empty
    ADB_INSTALL_CMD=()
    local sudo=""
    [ "$(id -u 2>/dev/null || echo 1)" != 0 ] && command -v sudo >/dev/null 2>&1 && sudo="sudo"
    if command -v brew >/dev/null 2>&1; then
        ADB_INSTALL_CMD=(brew install --cask android-platform-tools)
    elif command -v apt-get >/dev/null 2>&1; then
        ADB_INSTALL_CMD=($sudo apt-get install -y adb)
    elif command -v dnf >/dev/null 2>&1; then
        ADB_INSTALL_CMD=($sudo dnf install -y android-tools)
    elif command -v pacman >/dev/null 2>&1; then
        ADB_INSTALL_CMD=($sudo pacman -S --noconfirm android-tools)
    elif command -v zypper >/dev/null 2>&1; then
        ADB_INSTALL_CMD=($sudo zypper install -y android-tools)
    elif command -v apk >/dev/null 2>&1; then
        ADB_INSTALL_CMD=($sudo apk add android-tools)
    fi
}
ensure_adb() {
    command -v adb >/dev/null 2>&1 && return 0
    warn "adb (Android platform-tools) is not installed on this computer."
    adb_install_hint
    if [ "${#ADB_INSTALL_CMD[@]}" -gt 0 ] && [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then
        cmdline "${ADB_INSTALL_CMD[*]}"
        if confirm "Install it with your package manager?" y; then
            "${ADB_INSTALL_CMD[@]}" || die "that did not work. Install platform-tools yourself: https://developer.android.com/tools/releases/platform-tools"
            command -v adb >/dev/null 2>&1 && return 0
        fi
    fi
    die "adb is needed. Install platform-tools (https://developer.android.com/tools/releases/platform-tools) and run this again."
}
tv() { adb -s "$DEVICE" shell "$@" 2>/dev/null | tr -d '\r'; }
prop() { tv getprop "$1"; }
home_now() { tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }
installed_version() { tv dumpsys package "$PKG" | sed -n 's/^ *versionName=//p' | head -n 1; }
show_home() {
    if tv dumpsys power | grep -q 'mWakefulness=Awake'; then tv input keyevent KEYCODE_HOME >/dev/null; fi
}
serial_tag() { tv getprop ro.serialno | tr -cd 'A-Za-z0-9'; }
private_dir() {
    mkdir -p "$CONFIG_DIR"
    chmod 700 "$CONFIG_DIR" 2>/dev/null || true
}
state_file() {
    private_dir
    echo "$CONFIG_DIR/home-$(serial_tag).txt"
}
saver_file() {
    private_dir
    echo "$CONFIG_DIR/ambient-$(serial_tag).txt"
}
dns_file() {
    private_dir
    echo "$CONFIG_DIR/dns-$(serial_tag).txt"
}
saved_endpoints() {
    if [ -f "$ENDPOINTS" ]; then grep -E '^[A-Za-z0-9._-]+(:[0-9]{1,5})?$' "$ENDPOINTS" || true; fi
}
valid_endpoint() { [[ "$1" =~ ^[A-Za-z0-9._-]+(:[0-9]{1,5})?$ ]]; }
with_port() { case "$1" in *:*) echo "$1" ;; *) echo "$1:5555" ;; esac; }

connect_endpoint() {
    local endpoint out
    valid_endpoint "$1" || {
        bad "That does not look like an address (192.168.1.50 or 192.168.1.50:5555)."
        return 1
    }
    endpoint="$(with_port "$1")"
    cmdline "adb connect $endpoint"
    out="$(adb connect "$endpoint" 2>&1 || true)"
    case "$out" in
        *"connected to"* | *"already connected"*)
            DEVICE="$endpoint"
            good "Connected to $endpoint"
            if ! saved_endpoints | grep -qx "$endpoint" && [ "$ASSUME_YES" = 0 ] && confirm "Remember $endpoint for next time?" n; then
                private_dir
                echo "$endpoint" >>"$ENDPOINTS"
                chmod 600 "$ENDPOINTS" 2>/dev/null || true
            fi
            return 0
            ;;
    esac
    bad "Could not connect: ${out:-no answer}"
    note "On the TV: Settings, System, About, press Build 7 times, then Developer options, USB debugging (or Wireless debugging)."
    note "TVs that show a pairing code need: bash setup.sh --pair HOST:PORT (or the Pair option in the wizard)."
    return 1
}
# Wait until the TV answers: the first connection shows an "Allow USB debugging?" box on the TV.
await_authorized() {
    local i=0 state
    while [ "$i" -lt 60 ]; do
        state="$(adb -s "$DEVICE" get-state 2>&1 || true)"
        case "$state" in device) return 0 ;; esac
        if [ "$i" = 0 ]; then
            warn "Look at your TV: choose ${C_BOLD}Always allow from this computer${C_RESET}, then OK."
            [ "$UI" = 1 ] || return 1
        fi
        printf '\r\033[K%s' "  ${C_ACC}${SPIN[$((i % ${#SPIN[@]}))]}${C_RESET} ${C_DIM}waiting for the TV to allow this computer (${i}s)${C_RESET}"
        sleep 1
        i=$((i + 1))
    done
    printf '\r\033[K'
    return 1
}
# Pairing: Android 11+ "Wireless debugging" (Pair with pairing code).
do_pair() {
    local out
    if [ -z "$PAIR_ADDR" ]; then
        note "On the TV: Developer options, Wireless debugging, Pair with pairing code. It shows an address and a 6-digit code."
        ask "Pairing address (IP:port):" ""
        PAIR_ADDR="$REPLY"
    fi
    valid_endpoint "$PAIR_ADDR" || die "that does not look like HOST:PORT"
    if [ -z "$PAIR_CODE" ]; then
        ask "Pairing code (6 digits):" ""
        PAIR_CODE="$REPLY"
    fi
    [[ "$PAIR_CODE" =~ ^[0-9]{6}$ ]] || die "the pairing code is six digits"
    cmdline "adb pair $PAIR_ADDR ******"
    out="$(adb pair "$PAIR_ADDR" "$PAIR_CODE" 2>&1 || true)"
    case "$out" in
        *"Successfully paired"*)
            good "Paired. Now connect with the address on the main Wireless debugging screen (a different port)."
            return 0
            ;;
    esac
    bad "Pairing failed: ${out:-no answer}"
    note "The code changes each time the pairing box is opened. Keep that box open on the TV while pairing."
    return 1
}
# Wireless-debugging TVs announce themselves on the network; adb can list them (adb 30+).
discover_mdns() { # prints "ip:port" of connectable TVs, one per line
    adb mdns services 2>/dev/null | awk '$2 ~ /_adb-tls-connect/ {print $NF}' | sort -u
}
pick_tv() {
    local lines serial model state i n_dev n_saved total opt rc
    local -a serials labels saved found options
    if [ -n "$TV" ]; then
        connect_endpoint "$TV" || die "could not reach $TV"
        await_authorized || die "the TV did not allow this computer. Accept the prompt on the TV and run this again."
        return 0
    fi
    adb start-server >/dev/null 2>&1 || true
    while true; do
        serials=() labels=() saved=() found=() options=() MENU_DESC=()
        lines="$(adb devices -l 2>/dev/null | awk 'NR>1 && NF>1')"
        while IFS= read -r line; do
            [ -n "$line" ] || continue
            serial="${line%% *}"
            state="$(awk '{print $2}' <<<"$line")"
            model="$(sed -n 's/.*model:\([^ ]*\).*/\1/p' <<<"$line")"
            case "$state" in
                device)
                    serials+=("$serial")
                    labels+=("$serial${model:+  ($model)}")
                    ;;
                unauthorized) note "$serial is waiting for you to allow it on the TV." ;;
                offline) note "$serial is offline. Wake the TV or reconnect it." ;;
            esac
        done <<<"$lines"
        while IFS= read -r line; do [ -n "$line" ] && saved+=("$line"); done < <(saved_endpoints)
        while IFS= read -r line; do
            [ -n "$line" ] || continue
            printf '%s\n' "${serials[@]+"${serials[@]}"}" | grep -qx "$line" || found+=("$line")
        done < <(discover_mdns)
        n_dev=${#serials[@]}
        n_saved=${#saved[@]}
        if [ "$UI" != 1 ] && [ "$SCRIPTED" = 0 ] || [ "$ASSUME_YES" = 1 ]; then
            [ "$n_dev" -gt 0 ] || die "no TV found. On the TV turn on USB debugging (Settings, System, Developer options), then run again with --tv <TV IP>:5555"
            [ "$n_dev" = 1 ] || die "several TVs are connected; choose one with --tv ADDRESS"
            DEVICE="${serials[0]}"
            return 0
        fi
        for i in "${labels[@]+"${labels[@]}"}"; do
            options+=("$i")
            MENU_DESC+=("Already connected through adb")
        done
        for i in "${found[@]+"${found[@]}"}"; do
            options+=("Found on your network: $i")
            MENU_DESC+=("Announced by Wireless debugging; connects without typing")
        done
        for i in "${saved[@]+"${saved[@]}"}"; do
            options+=("Saved address: $i")
            MENU_DESC+=("An address you chose to remember")
        done
        options+=("Type an address (IP or host[:port])" "Pair with a code (Android 11+ Wireless debugging)" "Look again")
        MENU_DESC+=("Find the IP in the TV's Settings, Network" "For TVs that show a pairing code" "Refresh this list")
        total=${#options[@]}
        menu "Which TV?" "${options[@]}"
        rc=$?
        [ "$rc" != 0 ] && return 2
        opt="$CHOICE"
        if [ "$opt" -le "$n_dev" ]; then
            DEVICE="${serials[$((opt - 1))]}"
            return 0
        elif [ "$opt" -le $((n_dev + ${#found[@]})) ]; then
            connect_endpoint "${found[$((opt - n_dev - 1))]}" && await_authorized && return 0
        elif [ "$opt" -le $((n_dev + ${#found[@]} + n_saved)) ]; then
            connect_endpoint "${saved[$((opt - n_dev - ${#found[@]} - 1))]}" && await_authorized && return 0
        elif [ "$opt" -eq $((total - 2)) ]; then
            ask "TV address:" ""
            connect_endpoint "$REPLY" && await_authorized && return 0
        elif [ "$opt" -eq $((total - 1)) ]; then
            PAIR_ADDR="" PAIR_CODE=""
            do_pair || true
        fi
    done
}

# ---- What is on the TV ----------------------------------------------------------------
TV_MODEL="" TV_ANDROID="" TV_SDK=0 TV_IS_TV=1 TV_HAS=""
inspect_tv() {
    TV_MODEL="$(prop ro.product.manufacturer) $(prop ro.product.model)"
    TV_ANDROID="$(prop ro.build.version.release)"
    TV_SDK="$(prop ro.build.version.sdk)"
    case "$TV_SDK" in '' | *[!0-9]*) TV_SDK=0 ;; esac
    TV_IS_TV=1
    [ "$(tv cmd package has-feature android.software.leanback)" = false ] && TV_IS_TV=0
    TV_HAS=""
    installed "$PKG" && TV_HAS="$(installed_version)"
}
tv_card() {
    local lines=("$TV_MODEL" "Android $TV_ANDROID (API $TV_SDK)" "Connected as $DEVICE")
    if [ -n "$TV_HAS" ]; then lines+=("shangOS ${TV_HAS:-?} is installed"); else lines+=("shangOS is not installed yet"); fi
    box "Your TV" "${lines[@]}"
}
check_tv() { # warns about a TV that is too old or not a TV; may stop
    if [ "$TV_SDK" -gt 0 ] && [ "$TV_SDK" -lt "$MIN_SDK" ]; then
        bad "This TV runs Android $TV_ANDROID. shangOS needs Android 12 or newer."
        confirm "Try anyway?" n || return 1
    fi
    if [ "$TV_IS_TV" = 0 ]; then
        warn "This device does not report itself as an Android TV."
        confirm "Install anyway?" n || return 1
    fi
    return 0
}

# ---- Release ----------------------------------------------------------------------------
APK_NAME="" APK_TAG=""
fetch_release() {
    need curl "Install curl, or pass --apk with an APK you already have."
    local url json code
    if [ -n "$RELEASE_TAG" ]; then
        url="https://api.github.com/repos/$REPO/releases/tags/$RELEASE_TAG"
    else
        url="https://api.github.com/repos/$REPO/releases/latest"
    fi
    json="$TMP/release.json"
    code="$(curl -sSL -H 'Accept: application/vnd.github+json' -o "$json" -w '%{http_code}' "$url" 2>/dev/null)" || code=000
    if [ "$code" = 404 ] && [ -z "$RELEASE_TAG" ]; then # only pre-releases exist: take the newest
        code="$(curl -sSL -H 'Accept: application/vnd.github+json' -o "$json" -w '%{http_code}' "https://api.github.com/repos/$REPO/releases?per_page=1" 2>/dev/null)" || code=000
    fi
    case "$code" in
        200) ;;
        403 | 429) echo "GitHub is rate-limiting this network. Wait a few minutes, or download the APK from https://github.com/$REPO/releases/latest and pass --apk <file>." >"$TMP/why" ;;
        404) echo "There is no published release${RELEASE_TAG:+ $RELEASE_TAG} yet." >"$TMP/why" ;;
        *) echo "Could not reach github.com (HTTP $code). Check the internet connection." >"$TMP/why" ;;
    esac
    [ "$code" = 200 ]
}
parse_release() { # sets APK_URL, SUMS_URL, APK_NAME, APK_TAG from release.json
    local json="$TMP/release.json"
    APK_URL="$(grep -o "\"browser_download_url\": *\"[^\"]*shangos-[^\"/]*-$VARIANT\\.apk\"" "$json" | head -n 1 | sed 's/.*: *"//; s/"$//')"
    SUMS_URL="$(grep -o '"browser_download_url": *"[^"]*SHA256SUMS"' "$json" | head -n 1 | sed 's/.*: *"//; s/"$//')"
    APK_TAG="$(sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' "$json" | head -n 1)"
    [ -n "$APK_URL" ] && [ -n "$SUMS_URL" ] || return 1
    case "$APK_URL" in "https://github.com/$REPO/releases/download/"*) ;; *) return 1 ;; esac
    case "$SUMS_URL" in "https://github.com/$REPO/releases/download/"*) ;; *) return 1 ;; esac
    APK_NAME="$(basename "$APK_URL")"
}
download_release() {
    local sums expected actual cert
    fetch_release || {
        bad "$(cat "$TMP/why" 2>/dev/null)"
        return 1
    }
    parse_release || {
        bad "The release has no $VARIANT APK with a checksum file, or its files are not on github.com/$REPO."
        return 1
    }
    good "Release ${APK_TAG:-?}: $APK_NAME"
    download "Downloading $APK_NAME" "$APK_URL" "$TMP/$APK_NAME" || return 1
    cmdline "curl $SUMS_URL"
    sums="$(curl -fsSL "$SUMS_URL")" || return 1
    expected="$(grep -F " $APK_NAME" <<<"$sums" | head -n 1 | cut -d' ' -f1)"
    actual="$(sha256 "$TMP/$APK_NAME")"
    if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
        rm -f "$TMP/$APK_NAME"
        bad "The download does not match its published checksum, so it was deleted."
        return 1
    fi
    good "Checksum verified ${C_DIM}${actual}${C_RESET}"
    cert="$(signer_of "$TMP/$APK_NAME")"
    if [ -n "$cert" ]; then
        good "Signing certificate SHA-256 ${C_DIM}${cert}${C_RESET}"
        note "Compare with the release page and Settings, Privacy, This build (on the TV)."
    else
        note "apksigner is not installed here, so the signing certificate is not shown. Compare it on the TV: Settings, Privacy, This build."
    fi
    APK="$TMP/$APK_NAME"
}
signer_of() {
    local tool=""
    if command -v apksigner >/dev/null 2>&1; then
        tool="apksigner"
    else
        tool="$(find "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/nonexistent}}/build-tools" -name apksigner 2>/dev/null | sort | tail -n 1)"
    fi
    [ -n "$tool" ] || return 0
    "$tool" verify --print-certs "$1" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'
}

# ---- The actions ------------------------------------------------------------------------
adb_do() { # adb_do LABEL ARGS... : prints the command, runs it with a spinner, stops with the output on failure
    local label="$1"
    shift
    cmdline "adb -s $DEVICE $*"
    CHANGED=1
    step "$label" adb -s "$DEVICE" "$@"
}
do_install() {
    local flags=(-r) rc
    adb install --help 2>&1 | grep -q -- '--no-incremental' && flags=(--no-incremental -r)
    cmdline "adb -s $DEVICE install ${flags[*]} $(basename "$1")"
    CHANGED=1
    step "Installing $(basename "$1")" adb -s "$DEVICE" install "${flags[@]}" "$1"
    rc=$?
    if [ "$rc" != 0 ] || ! grep -q Success "$STEP_OUT"; then
        if grep -q "UPDATE_INCOMPATIBLE\|signatures do not match" "$STEP_OUT"; then
            warn "The TV has shangOS signed with another key (a different build). Android will not update it in place."
            note "Removing it clears shangOS's own settings (wallpaper, order). Nothing else on the TV is touched."
            if confirm "Remove the old one and install this?" n; then
                adb_do "Removing the old build" uninstall "$PKG" || return 1
                step "Installing $(basename "$1")" adb -s "$DEVICE" install "${flags[@]}" "$1" && grep -q Success "$STEP_OUT" && return 0
            fi
        elif grep -q "VERSION_DOWNGRADE" "$STEP_OUT"; then
            warn "The TV has a newer shangOS than this one."
            if confirm "Install the older one anyway?" n; then
                step "Installing $(basename "$1") (downgrade)" adb -s "$DEVICE" install -d "${flags[@]}" "$1" && grep -q Success "$STEP_OUT" && return 0
            fi
        fi
        show_tail
        return 1
    fi
    return 0
}
do_aot() {
    cmdline "adb -s $DEVICE shell cmd package compile -m speed -f $PKG"
    step "Compiling ahead of time" adb -s "$DEVICE" shell cmd package compile -m speed -f "$PKG" || note "(not available on this TV; harmless)"
}
do_widgets() {
    adb_do "Up Next: Continue watching" shell pm grant "$PKG" android.permission.READ_TV_LISTINGS || show_tail
    adb_do "Now Playing: music on the TV" shell cmd notification allow_listener "$PKG/$PKG.MediaListener" || show_tail
    adb_do "Up Next: recently used apps" shell appops set "$PKG" GET_USAGE_STATS allow || show_tail
}
do_saver() {
    local state current
    state="$(saver_file)"
    current="$(tv settings get secure screensaver_components)"
    [ "$current" = "$AMBIENT" ] || echo "previous=$current" >"$state"
    adb_do "Screen saver: clock over your wallpaper" shell settings put secure screensaver_components "$AMBIENT" || return 1
    adb_do "Screen saver: on" shell settings put secure screensaver_enabled 1 || return 1
}
undo_saver() {
    local state previous current
    state="$(saver_file)"
    current="$(tv settings get secure screensaver_components)"
    [ "$current" = "$AMBIENT" ] || return 0
    previous=""
    [ -s "$state" ] && previous="$(sed -n 's/^previous=//p' "$state" | head -n 1)"
    if [ -z "$previous" ] || [ "$previous" = null ] || [ "$previous" = "$AMBIENT" ]; then
        adb_do "Screen saver: the TV's own again" shell settings delete secure screensaver_components || true
    else
        adb_do "Screen saver: the TV's own again" shell settings put secure screensaver_components "$previous" || true
    fi
    rm -f "$state"
}
# ---- Private DNS: optional ad and tracker blocking for the whole TV -----------------------------------
# Android's own Private DNS setting (Android 9+): every app that uses the system resolver asks this server, which
# answers "not found" for known ad and tracker domains. It does not touch shangOS. Apps that bring their own
# DNS (or a hard-coded one) are not affected; that needs a rule on your router.
valid_dns_host() { [[ "$1" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$ ]] && [[ "$1" == *.* ]]; }
dns_host_of() { case "$1" in adguard | AdGuard | adguard-dns) echo "dns.adguard-dns.com" ;; *) echo "$1" ;; esac; }
dns_lookup_works() { tv ping -c 1 -W 5 example.com 2>&1 | grep -qE '1 (packets )?received|bytes from'; }
do_dns() { # do_dns CHOICE
    local host state mode spec worked=0
    host="$(dns_host_of "$1")"
    valid_dns_host "$host" || {
        bad "\"$host\" is not a hostname (it looks like dns.example.com)."
        return 1
    }
    state="$(dns_file)"
    mode="$(tv settings get global private_dns_mode)"
    spec="$(tv settings get global private_dns_specifier)"
    [ -s "$state" ] || printf 'mode=%s\nhostname=%s\n' "$mode" "$spec" >"$state"
    dns_lookup_works && worked=1
    adb_do "Private DNS server: $host" shell settings put global private_dns_specifier "$host" || return 1
    adb_do "Private DNS: on" shell settings put global private_dns_mode hostname || return 1
    sleep 2
    if [ "$worked" = 1 ] && ! dns_lookup_works; then
        warn "The TV cannot look up names with $host. Putting the old setting back so the TV stays online."
        undo_dns || true
        return 1
    fi
    good "Ads and trackers on the TV now go through $host."
    note "Some apps may show fewer ads or load differently. Undo: setup.sh --dns off"
}
undo_dns() {
    local state mode spec
    state="$(dns_file)"
    mode="" spec=""
    if [ -s "$state" ]; then
        mode="$(sed -n 's/^mode=//p' "$state" | head -n 1)"
        spec="$(sed -n 's/^hostname=//p' "$state" | head -n 1)"
    fi
    case "$mode" in hostname | off | opportunistic) ;; *) mode=opportunistic ;; esac
    if [ "$mode" = hostname ] && valid_dns_host "$spec"; then
        adb_do "Private DNS server: $spec (as before)" shell settings put global private_dns_specifier "$spec" || return 1
    fi
    adb_do "Private DNS: back to ${mode/opportunistic/automatic}" shell settings put global private_dns_mode "$mode" || return 1
    rm -f "$state"
}
dns_status_line() {
    local mode spec
    mode="$(tv settings get global private_dns_mode)"
    spec="$(tv settings get global private_dns_specifier)"
    case "$mode" in
        hostname) echo "$spec (Private DNS)" ;;
        off) echo "off" ;;
        *) echo "automatic (the TV's default)" ;;
    esac
}
flow_dns() {
    local rc choice="$DNS_CHOICE"
    [ "$UI" = 1 ] && echo
    pick_tv
    rc=$?
    [ "$rc" != 0 ] && return "$rc"
    inspect_tv
    [ "$UI" = 1 ] && tv_card
    if [ "$TV_SDK" -gt 0 ] && [ "$TV_SDK" -lt 28 ]; then die "Private DNS needs Android 9 or newer."; fi
    if [ -z "$choice" ]; then
        MENU_DESC=("Ads, trackers and known malicious domains are refused by name, for every app that uses the TV's DNS. Free and public." "A hostname you control or trust: a NextDNS profile, your own AdGuard Home, Pi-hole with DNS-over-TLS." "Back to the TV's own setting (automatic). Whatever you had before is restored.")
        MENU_START=1
        menu "Ad and tracker blocking" "Block ads and trackers (AdGuard DNS)" "Use my own DNS hostname" "Turn it off (back to automatic)"
        rc=$?
        [ "$rc" != 0 ] && return 0
        case "$CHOICE" in
            1) choice="adguard" ;;
            2)
                ask "Hostname (for example dns.example.com)" ""
                choice="$REPLY"
                ;;
            3) choice="off" ;;
        esac
    fi
    case "$choice" in
        off | none | automatic) undo_dns && good "The TV's DNS is automatic again." ;;
        *)
            if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then
                note "This changes one system setting for the whole TV (all apps). It can be undone with one command."
                confirm "Go ahead?" y || {
                    say "Nothing was changed."
                    return 0
                }
            fi
            do_dns "$choice"
            ;;
    esac
}
# Switches back on any known system home or setup app that an older version (or you) switched off.
reenable_system_homes() {
    local pkg
    for pkg in $KNOWN; do
        if installed "$pkg" && tv pm list packages -d "$pkg" | grep -qx "package:$pkg"; then
            warn "$pkg was switched off. Some TVs cannot restart without it, so it is being switched back on."
            adb_do "Switching $pkg back on" shell pm enable --user 0 "$pkg" || true
        fi
    done
}
do_home() {
    local state current pkg
    state="$(state_file)"
    if [ ! -s "$state" ]; then echo "original=$(home_now)" >"$state"; fi
    [ "$DISABLE_STOCK" = 1 ] || reenable_system_homes
    adb_do "Making shangOS the home screen" shell cmd package set-home-activity --user 0 "$SHANGOS" || return 1
    adb_do "Asking Android to make shangOS the Home role holder" shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" || true
    current="$(home_now)"
    if [ "$current" != "$SHANGOS" ] && [ "$DISABLE_STOCK" != 1 ]; then
        warn "shangOS is chosen as the home screen, but the TV still opens $current at Home."
        warn "The TV's own home and setup apps stay on: on some Google TVs (a Sony Bravia, for one) the TV cannot start without them."
        echo "    Open shangOS from the app row, or choose \"Always\" if the TV asks which home to use."
        echo "    Knowing the risk, you can run again with --disable-stock. Undo: --uninstall, or see https://shangos.madusha.dev/setup/#loop"
        return 0
    fi
    if [ "$current" != "$SHANGOS" ]; then
        warn "Switching off a stock launcher can leave some Google TVs unable to restart (they loop at every restart)."
        [ "$ASSUME_YES" = 1 ] || confirm "Switch $current off anyway?" n || {
            bad "Nothing was changed."
            return 1
        }
    fi
    for _ in 1 2 3; do
        current="$(home_now)"
        [ "$current" = "$SHANGOS" ] && break
        pkg="${current%%/*}"
        case " $DISABLABLE " in
            *" $pkg "*)
                warn "Switching off $pkg (reversible; the setup app is never touched)."
                adb_do "Switching off $pkg" shell pm disable-user --user 0 "$pkg" || return 1
                echo "disabled=$pkg" >>"$state"
                adb_do "Making shangOS the home screen" shell cmd package set-home-activity --user 0 "$SHANGOS" || return 1
                ;;
            *)
                bad "The TV still opens $current at Home, and this script never switches that off. Nothing else was changed; run with --uninstall to undo."
                return 1
                ;;
        esac
    done
    [ "$(home_now)" = "$SHANGOS" ] || {
        bad "The TV did not accept shangOS as its home screen. Run with --uninstall to undo."
        return 1
    }
    show_home
    good "shangOS is the home screen."
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
        adb_do "Switching $pkg back on" shell pm enable --user 0 "$pkg" || true
    done
    if [ -n "$original" ] && [ "$original" != "$SHANGOS" ] && [[ "$original" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ ]]; then
        adb_do "Giving Home back to $original" shell cmd package set-home-activity --user 0 "$original" || true
    fi
    rm -f "$state"
    show_home
    good "Home now opens $(home_now)."
}

# ---- Flows ------------------------------------------------------------------------------
PLAN=()
make_plan() { # the exact steps, shown before anything runs
    PLAN=()
    if [ -n "$APK" ]; then PLAN+=("Use $APK"); else PLAN+=("Download shangos-${RELEASE_TAG:-<latest>}-$VARIANT.apk from github.com/$REPO" "Check it against the published SHA256SUMS"); fi
    PLAN+=("adb install -r  (the TV keeps your shangOS settings on an update)")
    [ "$DO_AOT" = 1 ] && PLAN+=("adb shell cmd package compile -m speed -f $PKG")
    [ "$DO_WIDGETS" = 1 ] && PLAN+=("adb shell pm grant $PKG READ_TV_LISTINGS" "adb shell cmd notification allow_listener ..." "adb shell appops set $PKG GET_USAGE_STATS allow")
    [ "$DO_HOME" = 1 ] && PLAN+=("adb shell cmd package set-home-activity $SHANGOS" "(the TV's own home and setup apps are left on; with --disable-stock, a stock launcher that wins is switched off after asking)")
    [ "$DO_SAVER" = 1 ] && PLAN+=("adb shell settings put secure screensaver_components $AMBIENT")
    [ "$DO_DNS" = 1 ] && PLAN+=("adb shell settings put global private_dns_specifier dns.adguard-dns.com" "adb shell settings put global private_dns_mode hostname  (blocks ads and trackers for every app; undone with --dns off)")
}
run_install() {
    local failed=0
    if [ -z "$APK" ]; then
        download_release || return 1
    elif [ ! -f "$APK" ]; then
        die "no APK at $APK"
    fi
    do_install "$APK" || return 1
    [ "$DO_AOT" = 1 ] && do_aot
    [ "$DO_WIDGETS" = 1 ] && do_widgets
    [ "$DO_HOME" = 1 ] && { do_home || failed=1; }
    [ "$DO_SAVER" = 1 ] && { do_saver || failed=1; }
    [ "$DO_DNS" = 1 ] && { do_dns adguard || failed=1; }
    return "$failed"
}
finish_card() {
    echo
    box "Done" \
        "shangOS is on your TV." \
        "Press Home on the remote. Settings (the gear) walks you through the rest." \
        "" \
        "Undo any time:" \
        "  curl -fsSL https://shangos.madusha.dev/setup.sh | bash -s -- --uninstall" \
        "What Undo needs is remembered in $CONFIG_DIR"
}
flow_install() {
    local s=1 rc
    while [ "$s" -ge 1 ] && [ "$s" -le 5 ]; do
        case "$s" in
            1) # TV
                [ "$UI" = 1 ] && { echo; stepbar 1; }
                pick_tv
                rc=$?
                if [ "$rc" != 0 ]; then return "$rc"; fi
                inspect_tv
                [ "$UI" = 1 ] && tv_card
                if ! check_tv; then
                    if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then continue; fi
                    return 1
                fi
                s=2
                ;;
            2) # Build
                if [ -n "$APK" ]; then
                    s=3
                    continue
                fi
                                MENU_DESC=("The normal build. Weather (off until you turn it on) and Up Next pictures use the internet." "No internet permission at all: Android itself blocks every connection. No weather, no remote posters.")
                [ "$VARIANT" = offline ] && MENU_START=2
                if [ "$ASSUME_YES" = 1 ] || { [ "$SCRIPTED" = 0 ] && [ "$UI" != 1 ]; }; then
                    s=3
                    continue
                fi
                menu "Which build?" "Standard (release)" "Offline (no internet permission)"
                rc=$?
                [ "$rc" = 1 ] && {
                    s=1
                    continue
                }
                [ "$rc" = 2 ] && return 2
                [ "$CHOICE" = 2 ] && VARIANT=offline || VARIANT=release
                s=3
                ;;
            3) # Options
                                if [ "$ASSUME_YES" = 1 ]; then
                    s=4
                    continue
                fi
                MENU_DESC=("Up Next, Now Playing and recent apps. Android asks for these in Settings; this grants them over adb." "Press Home and shangOS opens. If the TV's own launcher wins, it is switched off, and Undo switches it back on." "Compiles the app for speed once; a few seconds. Harmless if the TV cannot." "A clock over your wallpaper after the TV's own screen-saver delay. Off by default." "Uses AdGuard DNS as the TV's Private DNS: ad and tracker domains are refused for every app. Off by default; one command undoes it.")
                CHK=("$DO_WIDGETS" "$DO_HOME" "$DO_AOT" "$DO_SAVER" "$DO_DNS")
                checklist "What should I switch on?" "Optional permissions (Up Next, Now Playing)" "Make shangOS the home screen" "Compile ahead of time (faster)" "shangOS screen saver" "Block ads and trackers on the whole TV (Private DNS)"
                rc=$?
                [ "$rc" = 1 ] && {
                    s=2
                    continue
                }
                [ "$rc" = 2 ] && return 2
                DO_WIDGETS="${CHK[0]}" DO_HOME="${CHK[1]}" DO_AOT="${CHK[2]}" DO_SAVER="${CHK[3]}" DO_DNS="${CHK[4]}"
                s=4
                ;;
            4) # Review
                make_plan
                if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then
                    echo
                    box "Here is exactly what will happen" "${PLAN[@]}"
                    echo
                    if ! confirm "Go ahead?" y; then
                        s=3
                        continue
                    fi
                fi
                s=5
                ;;
            5) # Install
                [ "$UI" = 1 ] && { echo; stepbar 5; }
                if run_install; then
                    finish_card
                    return 0
                fi
                if [ "$CHANGED" = 1 ]; then
                    bad "Setup stopped. The commands listed above did run; undo them with --uninstall."
                else
                    bad "Setup stopped before anything on the TV was changed."
                fi
                return 1
                ;;
        esac
    done
    return 0
}
flow_uninstall() {
    local rc
    [ "$UI" = 1 ] && echo
    pick_tv
    rc=$?
    [ "$rc" != 0 ] && return "$rc"
    inspect_tv
    [ "$UI" = 1 ] && tv_card
    installed "$PKG" || die "shangOS is not installed on this TV"
    if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then
        echo
        confirm "Remove shangOS and give Home back to the TV's own launcher?" y || {
            say "Nothing was changed."
            return 0
        }
    fi
    if [ -s "$(state_file)" ] || [ "$(home_now)" = "$SHANGOS" ]; then
        note "shangOS is the home screen: giving Home back first, so the TV always has a launcher."
        do_restore
    fi
    undo_saver
    if [ -s "$(dns_file)" ]; then
        if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ]; then
            confirm "The TV's Private DNS was set by this wizard. Turn it back to automatic too?" n && undo_dns
        else
            note "Private DNS was left as it is. Turn it back with: setup.sh --dns off"
        fi
    fi
    adb_do "Removing shangOS" uninstall "$PKG" || {
        show_tail
        return 1
    }
    echo
    good "Removed. The TV's own launcher opens at Home again."
}
flow_status() {
    local rc home
    pick_tv
    rc=$?
    [ "$rc" != 0 ] && return "$rc"
    inspect_tv
    tv_card
    home="$(home_now)"
    say "  Home opens:        $home"
    say "  Screen saver:      $(tv settings get secure screensaver_components)"
    say "  Private DNS:       $(dns_status_line)"
    local disabled=""
    for pkg in $KNOWN; do tv pm list packages -d "$pkg" | grep -qx "package:$pkg" && disabled="$disabled $pkg"; done
    say "  Switched off:      ${disabled:-nothing}"
    say "  Saved state:       $(state_file)"
}
home_menu() {
    local rc
    banner
    while true; do
        MENU_DESC=("Download the signed release, install it, make it your home screen." "Give Home back to the TV's own launcher, then remove shangOS." "What is installed, what Home opens, what is switched off." "For TVs that show a pairing code (Android 11+ Wireless debugging)." "Optional: refuse ad and tracker domains for every app on the TV (Private DNS). One command undoes it.")
        menu "What would you like to do?" "Install or update shangOS" "Remove shangOS (undo everything)" "Check a TV" "Pair with a TV" "Block ads and trackers (DNS)"
        rc=$?
        [ "$rc" != 0 ] && return 0
        case "$CHOICE" in
            1) flow_install ;;
            2) flow_uninstall ;;
            3) flow_status ;;
            4) do_pair || true ;;
            5) flow_dns || true ;;
        esac
        echo
        confirm "Back to the main menu?" y || return 0
    done
}

# ---- Run ---------------------------------------------------------------------------------
TMP="$(mktemp -d 2>/dev/null || mktemp -d -t shangos)" || die "cannot make a temporary folder"
# shellcheck disable=SC2329 # runs from the EXIT trap
cleanup() {
    rm -rf "$TMP"
    ui_cursor_show
}
trap cleanup EXIT
trap 'echo; echo "Stopped. Whatever is listed above ran; nothing further was changed."; exit 130' INT TERM

ensure_adb
case "$ACTION" in
    install) ;;
    "")
        if [ "$UI" = 1 ] && [ "$ASSUME_YES" = 0 ] && [ -z "$TV" ] && [ -z "$APK" ]; then ACTION="menu"; else ACTION="install"; fi
        ;;
esac
[ "$UI" = 1 ] && [ "$ACTION" != menu ] && banner
[ "$UI" = 0 ] && [ "$SCRIPTED" = 0 ] && say "shangOS setup $SETUP_VERSION"

rc=0
case "$ACTION" in
    menu) home_menu ;;
    install) flow_install || rc=$? ;;
    uninstall) flow_uninstall || rc=$? ;;
    status) flow_status || rc=$? ;;
    dns) flow_dns || rc=$? ;;
    pair)
        do_pair || rc=$?
        ;;
esac
if [ "$rc" = 2 ]; then
    say "Nothing was changed."
    rc=0
fi
exit "$rc"
