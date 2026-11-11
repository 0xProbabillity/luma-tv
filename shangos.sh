#!/bin/bash
# shangOS on your TV, from this computer.
#   ./shangos.sh wizard                      guided: pick the TV, install, make it the home screen, clean up
#   ./shangos.sh [-s DEVICE] build [VARIANT] | install [VARIANT] | home | widgets | restore | status | cleanup
#   ./shangos.sh [-s DEVICE] ambient [on|off|preview]    the shangOS Ambient screen saver (a clock over your wallpaper)
#   ./shangos.sh [-s DEVICE] wallpaper FILE | --remove    your own picture as the wallpaper (and screen saver), no permission needed
# VARIANT: release (default), offline (no network permission at all) or debug.
# DEVICE is the TV's adb address (192.168.1.50:5555) or serial. Default: $SHANGOS_DEVICE, else the only device adb sees.
# Every command that changes the TV is printed first (lines starting "+"), so nothing happens out of sight.
set -euo pipefail
cd "$(dirname "$0")"
DEVICE="${SHANGOS_DEVICE:-}"
if [ "${1:-}" = "-s" ]; then
    DEVICE="$2"
    shift 2
fi
ACTION="${1:-status}"
PKG="dev.madusha.shangos"
SHANGOS="$PKG/.MainActivity"
AMBIENT="$PKG/$PKG.AmbientDream"
REPO="madushas/shangos-launcher"
# Earlier ids of this app, and the project it started from: cleanup can remove them, nothing else ever does.
EARLIER_IDS="com.madushas.shangos app.vela.tv"
LUMA_ID="dev.luma.tv"
# The only packages this script ever switches off: stock TV launchers, and Google TV's setup "recovery" home,
# which outranks any other home screen once the launcher is off.
KNOWN="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/shangos"
ENDPOINTS="$CONFIG_DIR/endpoints"
VARIANT="release"
APK_PATH=""

die() {
    echo "shangos: $*" >&2
    exit 1
}
# Prints a command that changes the TV, then runs it.
run() {
    echo "+ $*"
    "$@"
}
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is needed for this and was not found. $2"; }

pick_device() {
    if [ -n "$DEVICE" ]; then
        case "$DEVICE" in *:*) adb connect "$DEVICE" >/dev/null 2>&1 || true ;; esac
        return
    fi
    local list
    list="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    [ -n "$list" ] || die "no TV found. Turn on ADB debugging on the TV, then: adb connect <TV IP>:5555 (or ./shangos.sh wizard)"
    [ "$(echo "$list" | wc -l | tr -d ' ')" = "1" ] || die "several devices connected; choose one with -s DEVICE:
$list"
    DEVICE="$list"
}
tv() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }
home_now() { tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }
state_file() {
    mkdir -p .local
    echo ".local/home-$(tv getprop ro.serialno | tr -cd 'A-Za-z0-9').txt"
}
installed() { tv pm list packages "$1" | grep -qx "package:$1"; }
show_home() { # never wakes a sleeping TV
    if tv dumpsys power | grep -q 'mWakefulness=Awake'; then tv input keyevent KEYCODE_HOME; fi
}
sha256() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

# ---- The actions (shared by the commands and the wizard) ----
do_build() { bash build.sh "${1:-release}"; }

# do_install APK: installs or updates, then compiles ahead of time.
do_install() {
    local apk="$1"
    [ -f "$apk" ] || die "no APK at $apk"
    run adb -s "$DEVICE" install --no-incremental -r "$apk" || die "install failed. If it says the signatures do not match, the TV has a build signed with another key: adb -s $DEVICE uninstall $PKG (this clears shangOS's settings), then install again."
    # Sideloaded apps start on the interpreter; compiling ahead of time makes the first launches smoother. Set SHANGOS_AOT=0 to skip.
    if [ "${SHANGOS_AOT:-1}" = 1 ]; then
        echo "Compiling ahead of time (a few seconds)..."
        tv cmd package compile -m speed -f "$PKG" >/dev/null || echo "(AOT compile not available on this TV; harmless)"
    fi
    echo "Installed $(basename "$apk")."
}

do_home() {
    installed "$PKG" || die "shangOS is not installed yet: ./shangos.sh install"
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
            *) die "the TV still opens $current at Home, and shangos.sh does not switch off unknown apps. Nothing else was changed; ./shangos.sh restore undoes this." ;;
        esac
    done
    [ "$(home_now)" = "$SHANGOS" ] || die "the TV did not accept shangOS as its home screen. ./shangos.sh restore undoes the changes."
    show_home
    echo "shangOS is the home screen. Undo at any time: ./shangos.sh restore"
}

do_widgets() {
    # The Home widgets work without this; it switches on what a TV remote makes tedious to allow by hand.
    installed "$PKG" || die "shangOS is not installed yet: ./shangos.sh install"
    run adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_TV_LISTINGS && echo "Up Next: Continue watching from your apps."
    run adb -s "$DEVICE" shell cmd notification allow_listener "$PKG/$PKG.MediaListener" && echo "Now Playing: music playing on the TV."
    run adb -s "$DEVICE" shell appops set "$PKG" GET_USAGE_STATS allow && echo "Up Next: recently used apps."
    echo "Undo any of these in the TV's Settings → Apps → Special app access, or Settings → Apps → shangOS → Permissions."
}

do_restore() {
    local state disabled original pkg
    state="$(state_file)"
    if [ -s "$state" ]; then
        disabled="$(sed -n 's/^disabled=//p' "$state")"
        original="$(sed -n 's/^original=//p' "$state" | head -n 1)"
    else # set up by hand or on another computer: switch every known launcher back on
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
    echo "Home now opens $(home_now). shangOS stays installed (./shangos.sh cleanup removes it)."
}

ambient_state() {
    mkdir -p .local
    echo ".local/ambient-$(tv getprop ro.serialno | tr -cd 'A-Za-z0-9').txt"
}
ambient_current() { tv settings get secure screensaver_components; }

# do_ambient on|off|preview: sets shangOS Ambient as the screen saver, puts the previous one back, or shows it now.
do_ambient() {
    local mode="${1:-on}" state current previous
    state="$(ambient_state)"
    current="$(ambient_current)"
    case "$mode" in
        on)
            installed "$PKG" || die "shangOS is not installed yet: ./shangos.sh install"
            if [ "$current" != "$AMBIENT" ]; then echo "previous=$current" >"$state"; fi
            run adb -s "$DEVICE" shell settings put secure screensaver_components "$AMBIENT"
            run adb -s "$DEVICE" shell settings put secure screensaver_enabled 1
            echo "shangOS Ambient is the screen saver. It starts after the TV's own screen saver delay (the TV's Settings, Display). Preview it now: ./shangos.sh ambient preview"
            ;;
        off)
            if [ "$current" != "$AMBIENT" ]; then
                echo "shangOS Ambient is not the screen saver on this TV."
                return 0
            fi
            previous=""
            if [ -s "$state" ]; then previous="$(sed -n 's/^previous=//p' "$state" | head -n 1)"; fi
            if [ -z "$previous" ] || [ "$previous" = "null" ] || [ "$previous" = "$AMBIENT" ]; then
                run adb -s "$DEVICE" shell settings delete secure screensaver_components
            else
                run adb -s "$DEVICE" shell settings put secure screensaver_components "$previous"
            fi
            rm -f "$state"
            echo "The previous screen saver is back."
            ;;
        preview)
            [ "$current" = "$AMBIENT" ] || die "shangOS Ambient is not the screen saver yet: ./shangos.sh ambient on"
            run adb -s "$DEVICE" shell am start -n com.android.systemui/.Somnambulator
            ;;
        *) die "ambient takes on, off or preview" ;;
    esac
}

do_status() {
    echo "TV:   $(tv getprop ro.product.manufacturer) $(tv getprop ro.product.model), Android $(tv getprop ro.build.version.release)"
    echo "shangOS: $(tv dumpsys package "$PKG" | sed -n 's/^ *versionName=//p' | head -n 1 | grep . || echo 'not installed')"
    echo "Home: $(home_now)"
    if [ "$(ambient_current)" = "$AMBIENT" ]; then echo "Screen saver: shangOS Ambient"; fi
}

# ---- Asking questions (the wizard) ----
ask() { # ask "Question" [default] -> REPLY
    local answer
    if [ -n "${2:-}" ]; then
        read -r -p "$1 [$2] " answer || die "no input (the wizard needs a terminal)"
        REPLY="${answer:-$2}"
    else
        read -r -p "$1 " answer || die "no input (the wizard needs a terminal)"
        REPLY="$answer"
    fi
}
yesno() { # yesno "Question" [y|n default]
    local default="${2:-n}" hint="y/N"
    [ "$default" = y ] && hint="Y/n"
    ask "$1 ($hint)" ""
    [[ "${REPLY:-$default}" =~ ^[Yy] ]]
}
choose() { # choose "Title" option... -> CHOICE (1-based)
    local title="$1" n i=1 option
    shift
    n=$#
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
}

# ---- The wizard: choosing the TV ----
saved_endpoints() {
    if [ -f "$ENDPOINTS" ]; then grep -E '^[A-Za-z0-9._-]+(:[0-9]{1,5})?$' "$ENDPOINTS" || true; fi
}

# connect_endpoint host[:port] -> DEVICE
connect_endpoint() {
    local endpoint="$1" out
    [[ "$endpoint" =~ ^[A-Za-z0-9._-]+(:[0-9]{1,5})?$ ]] || {
        echo "That does not look like an address. Use an IP or host name, optionally with :port (192.168.1.50 or 192.168.1.50:5555)."
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
    echo "Could not connect. On the TV: Settings → System → About, press Build 7 times, then Developer options → USB debugging (network debugging on some TVs)."
    return 1
}

choose_tv() {
    local lines serials labels saved line serial model i n_dev n_saved total
    while true; do
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
        local waiting
        waiting="$(adb devices | awk 'NR>1 && $2=="unauthorized" {print $1}' | tr '\n' ' ')"
        [ -z "$waiting" ] || echo "Waiting for you to accept the USB-debugging prompt on the TV: $waiting(then choose Refresh)"
        saved=()
        while IFS= read -r line; do [ -n "$line" ] && saved+=("$line"); done < <(saved_endpoints)
        n_dev=${#serials[@]}
        n_saved=${#saved[@]}
        local options=()
        for i in "${labels[@]+"${labels[@]}"}"; do options+=("TV: $i"); done
        for i in "${saved[@]+"${saved[@]}"}"; do options+=("Connect to saved address $i"); done
        options+=("Connect to another address (IP or host[:port])" "Refresh the list" "Quit")
        total=${#options[@]}
        choose "Which TV?" "${options[@]}"
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

# ---- The wizard: which build ----
choose_variant() {
    choose "Which edition?" \
        "Release: the normal build. Has the internet permission (weather is off until you turn it on)." \
        "Offline: no internet permission at all. Weather and remote posters are unavailable." \
        "Debug: for testing. Slower, with test hooks."
    case "$CHOICE" in 1) VARIANT=release ;; 2) VARIANT=offline ;; *) VARIANT=debug ;; esac
}

# download_release: fetches the latest GitHub release's APK for $VARIANT and verifies its SHA-256 before using it.
download_release() {
    need curl "Install curl, or pick another source."
    local json apk_url sums_url dir name sums expected actual
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
    dir="dist/downloads"
    mkdir -p "$dir"
    name="$(basename "$apk_url")"
    echo "+ curl $apk_url"
    curl -fSL -o "$dir/$name" "$apk_url" || return 1
    sums="$(curl -fsSL "$sums_url")" || return 1
    expected="$(grep " $name\$" <<<"$sums" | head -n 1 | cut -d' ' -f1)"
    actual="$(sha256 "$dir/$name")"
    if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
        rm -f "$dir/$name"
        echo "The download does not match its published checksum, so it was deleted."
        return 1
    fi
    echo "Checksum verified: $actual"
    if command -v apksigner >/dev/null 2>&1; then
        echo "Signing certificate (compare with the one on the website and in the app's Privacy screen):"
        apksigner verify --print-certs "$dir/$name" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: /  SHA-256: /p'
    fi
    APK_PATH="$dir/$name"
}

choose_apk() {
    local options=() actions=() f
    f="dist/shangos-$VARIANT.apk"
    if [ -f "$f" ]; then
        options+=("Use the one already built: $f")
        actions+=(existing)
    fi
    options+=("Build it now from this source (needs the Android SDK)" "Download the latest release from GitHub (checksum verified)" "Use an APK file I choose")
    actions+=(build download path)
    while true; do
        choose "Where does the APK come from?" "${options[@]}"
        case "${actions[$((CHOICE - 1))]}" in
            existing)
                APK_PATH="$f"
                return
                ;;
            build)
                do_build "$VARIANT" && APK_PATH="$f" && return
                ;;
            download) download_release && return ;;
            path)
                ask "Path to the APK:" ""
                if [ -f "$REPLY" ]; then
                    APK_PATH="$REPLY"
                    return
                fi
                echo "No such file."
                ;;
        esac
    done
}

# ---- The wizard: cleaning up ----
uninstall_package() {
    local pkg="$1"
    # If the stock launcher was switched off, removing the home screen first could leave the TV with none.
    if [ "$pkg" = "$PKG" ] && { [ -s "$(state_file)" ] || [ "$(home_now)" = "$SHANGOS" ]; }; then
        echo "shangOS is the home screen: giving Home back to the TV's own launcher first."
        do_restore
    fi
    if [ "$pkg" = "$PKG" ] && [ "$(ambient_current)" = "$AMBIENT" ]; then
        echo "shangOS Ambient is the screen saver: putting the previous one back first."
        do_ambient off
    fi
    run adb -s "$DEVICE" uninstall "$pkg"
}

do_cleanup() {
    local found pkg
    while true; do
        choose "Clean up" \
            "Remove shangOS from this TV (its settings are cleared; Home goes back to the TV's own launcher first)" \
            "Remove earlier shangOS test installs found on this TV" \
            "Remove Luma TV (the project shangOS started from, used for comparisons) if it is installed" \
            "Forget the saved TV addresses on this computer" \
            "Disconnect this TV (adb)" \
            "Back"
        case "$CHOICE" in
            1)
                if installed "$PKG"; then
                    if yesno "Remove shangOS from this TV?" n; then uninstall_package "$PKG"; fi
                else echo "shangOS is not installed on this TV."; fi
                ;;
            2)
                found=0
                for pkg in $EARLIER_IDS; do
                    if installed "$pkg"; then
                        found=1
                        if yesno "Remove the earlier install $pkg?" n; then uninstall_package "$pkg"; fi
                    fi
                done
                [ "$found" = 1 ] || echo "None found."
                ;;
            3)
                if installed "$LUMA_ID"; then
                    if yesno "Remove Luma TV ($LUMA_ID)? If it is your home screen, Home first goes back to the TV's own launcher." n; then
                        [ "$(home_now)" = "$LUMA_ID/.MainActivity" ] && echo "Luma TV is the home screen: run ./shangos.sh restore if Home looks wrong afterwards."
                        run adb -s "$DEVICE" uninstall "$LUMA_ID"
                    fi
                else echo "Luma TV is not installed on this TV."; fi
                ;;
            4)
                if [ -f "$ENDPOINTS" ] && yesno "Delete $ENDPOINTS?" n; then
                    rm -f "$ENDPOINTS"
                    echo "Forgotten."
                else echo "Nothing saved."; fi
                ;;
            5)
                case "$DEVICE" in
                    *:*) run adb disconnect "$DEVICE" ;;
                    *) echo "$DEVICE is a USB device: unplug it." ;;
                esac
                ;;
            *) return ;;
        esac
    done
}

# ---- The wizard ----
wizard() {
    need adb "Install the Android platform-tools: https://developer.android.com/tools/releases/platform-tools"
    if [ ! -t 0 ] && [ -z "${SHANGOS_WIZARD_SCRIPTED:-}" ]; then die "the wizard asks questions: run it in a terminal."; fi
    echo "shangOS setup. Every command that changes your TV is shown (a line starting with +) before it runs."
    if [ -z "$DEVICE" ]; then choose_tv; else connect_endpoint "$DEVICE" >/dev/null 2>&1 || true; fi
    echo "Using $DEVICE"
    do_status
    while true; do
        choose "What would you like to do?" \
            "Set everything up: install, allow the optional permissions, make it the home screen" \
            "Install or update shangOS" \
            "Make shangOS the home screen" \
            "Allow the optional permissions (Up Next, Now Playing)" \
            "Use shangOS Ambient as the screen saver" \
            "Preview the screen saver now" \
            "Go back to the previous screen saver" \
            "Give Home back to the TV's own launcher" \
            "Show the TV's status" \
            "Clean up" \
            "Choose another TV" \
            "Quit"
        case "$CHOICE" in
            1)
                choose_variant
                choose_apk
                do_install "$APK_PATH"
                yesno "Allow the optional permissions now?" y && do_widgets
                yesno "Make shangOS the home screen now?" y && do_home
                ;;
            2)
                choose_variant
                choose_apk
                do_install "$APK_PATH"
                ;;
            3) do_home ;;
            4) do_widgets ;;
            5) do_ambient on ;;
            6) do_ambient preview ;;
            7) do_ambient off ;;
            8) do_restore ;;
            9) do_status ;;
            10) do_cleanup ;;
            11) choose_tv ;;
            *)
                echo "Done."
                return
                ;;
        esac
    done
}

# do_wallpaper FILE|--remove: hands a picture to the app's own storage over adb. The app reads no media, needs no
# permission and no cloud account; only this computer, over adb, can write it (the provider is protected by DUMP).
do_wallpaper() {
    local file="${1:-}" uri="content://$PKG.photo/wallpaper"
    installed "$PKG" || die "shangOS is not installed on this TV: ./shangos.sh install"
    case "$file" in
        "") die "wallpaper takes a picture: ./shangos.sh wallpaper ~/photo.jpg   (or --remove)" ;;
        --remove)
            run adb -s "$DEVICE" shell content delete --uri "$uri" >/dev/null
            echo "Your photo is removed; the default wallpaper is back."
            ;;
        *)
            [ -f "$file" ] || die "no such file: $file"
            [ "$(wc -c <"$file")" -le 25165824 ] || die "that picture is larger than 24 MB; resize it first"
            echo "+ adb -s $DEVICE shell content write --uri $uri < $file"
            adb -s "$DEVICE" shell content write --uri "$uri" <"$file" || die "the TV refused the picture"
            echo "Done. Settings, Wallpaper now offers \"Your photo\", and the screen saver shows it dimmed."
            ;;
    esac
}

# ---- The commands ----
case "$ACTION" in
    wizard) wizard ;;
    build) do_build "${2:-release}" ;;
    install)
        pick_device
        VARIANT="${2:-release}"
        APK_PATH="dist/shangos-$VARIANT.apk"
        [ -f "$APK_PATH" ] || do_build "$VARIANT"
        do_install "$APK_PATH"
        ;;
    home)
        pick_device
        do_home
        ;;
    widgets)
        pick_device
        do_widgets
        ;;
    restore)
        pick_device
        do_restore
        ;;
    status)
        pick_device
        do_status
        ;;
    cleanup)
        pick_device
        do_cleanup
        ;;
    ambient)
        pick_device
        do_ambient "${2:-on}"
        ;;
    wallpaper)
        pick_device
        do_wallpaper "${2:-}"
        ;;
    *)
        die "unknown command '$ACTION'. Use wizard, build, install, home, widgets, restore, status, cleanup, ambient or wallpaper."
        ;;
esac
