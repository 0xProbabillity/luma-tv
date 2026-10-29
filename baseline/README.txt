upstream-luma-tv-2.4-8ce6838.apk
  The UNTOUCHED Luma TV 2.4 (upstream commit 8ce6838), package dev.luma.tv, built in this project's sandbox with the
  same build.sh and a throwaway local key. It exists only as the "before" for tools/baseline.sh: install it next to
  shangOS, measure it, then `adb uninstall dev.luma.tv`. It is NOT part of shangOS and has the problems listed in
  the change guide (cleartext enabled, House Control WebView, etc.). Do not make it your home screen.
    adb install baseline/upstream-luma-tv-2.4-8ce6838.apk
    tools/baseline.sh -s <TV_IP>:5555 dev.luma.tv/.MainActivity upstream
