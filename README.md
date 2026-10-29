# shangOS

A fast, private, modern home screen for Android TV. Plain Java, **no third-party libraries**, no telemetry, no
accounts, no ads. Source: https://github.com/madushas/shangos-launcher. Forked from [Luma TV](https://github.com/0xProbabillity/luma-tv) (MIT); see `NOTICE.md`.

**Status: Phase 2 of 5 (speed and memory, code structure). Built and unit-tested.**

Target: Android 12+ (API 31), landscape, leanback. Developed against a 2 GB Android 12 stick.

## Privacy, in one table
| | Full build (`release`) | `offline` build |
|---|---|---|
| INTERNET permission | yes | **none**: Android blocks every connection |
| Weather | **off** until you turn it on; contacts `api.open-meteo.com` | not available |
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
`src/com.madushas.shangos` code · `res` assets · `test` JUnit · `tools` rename, audit, measure · `build.sh` / `shangos.sh` scripts.
