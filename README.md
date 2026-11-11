# shangOS

A fast, private, modern home screen for Android TV. Plain Java, **no third-party libraries**, no telemetry, no
accounts, no ads. Source: https://github.com/madushas/shangos-launcher. Forked from [Luma TV](https://github.com/0xProbabillity/luma-tv) (MIT); see `NOTICE.md`.

**Status: 0.4.6 is the latest release. Built, unit-tested and linted; field-tested on one TV stick.**

Website and setup guide: https://shangos.madusha.dev (source in `docs/`). Releases are signed; the fingerprint is on each release and in the app.

Target: Android 12+ (API 31), landscape, leanback. Developed on a 1.4 GB, 32-bit, 720p Android 14 stick: if it is smooth there, it is smooth anywhere.
A screen saver (clock over your wallpaper), tiles tinted from each app's own icon, and a hero line that is real information are shangOS's own.
A hardware check picks how much the wallpaper animates (Light, Balanced or Quality); Settings, Performance overrides it.

## Privacy, in one table
| | Full build (`release`) | `offline` build |
|---|---|---|
| INTERNET permission | yes | **none**: Android blocks every connection |
| Weather | **off** until you turn it on; contacts `api.open-meteo.com` (and `geocoding-api.open-meteo.com` while you search for a city to pin) | not available |
| Remote Up Next posters | Wi-Fi/Ethernet only by default; https only, never to local addresses | not available |
| Analytics, crash reports, accounts, update checks | none | none |
| Cleartext http | refused (platform config and code) | refused |

Settings → Privacy shows the live state of everything above, each permission, and this build's signing
fingerprint. `Offline mode` there switches all network use off at runtime.

## Build
Needs JDK 17+, the Android SDK (`build-tools`, `platforms;android-34`), python3, zip. Linux, macOS, or Windows via WSL2.
```
export ANDROID_SDK_ROOT=$HOME/android-sdk
./build.sh release        # or: offline | debug        -> dist/shangos-<variant>.apk
./test/run.sh             # unit tests (needs JUnit 4: apt install junit4)
```
For repository checks, install `shellcheck`, `shfmt`, `xmllint`, and
`google-java-format`, then run:
```
./tools/lint.sh
./tools/format.sh --check
./tools/format.sh --write
```
Version comes from `VERSION`. Without `SHANGOS_KEYSTORE` the build is signed with a local dev key
(`.signing/`, git-ignored, random password). For a release key:
```
keytool -genkeypair -keystore ~/keys/shangos-release.jks -alias shangos -keyalg RSA -keysize 4096 -validity 36500
SHANGOS_KEYSTORE=~/keys/shangos-release.jks ./build.sh release     # prompts for the password, or set SHANGOS_KS_PASS
```
Back the key up: Android refuses updates signed with a different key.

## Install on the TV
Enable USB debugging on the TV, then:
```
adb connect <TV_IP>:5555
./shangos.sh -s <TV_IP>:5555 install [release|offline]   # also compiles ahead of time
./shangos.sh -s <TV_IP>:5555 widgets                     # grants Up Next / Now Playing / usage access
./shangos.sh -s <TV_IP>:5555 home                        # becomes the home screen (reversible)
./shangos.sh -s <TV_IP>:5555 restore                     # undo everything
```

## Layout
`src/dev/madusha/shangos` code · `res` assets · `test` JUnit · `build.sh` / `shangos.sh` scripts · `docs/` the website (static) and `docs/dev/` developer docs.
