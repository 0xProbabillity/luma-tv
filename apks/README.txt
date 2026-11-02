Prebuilt from this exact source (0.3.0) in the build sandbox, signed with a throwaway DEV key (not the key ./build.sh creates on your machine;
switching between these and your own build needs `adb uninstall dev.madusha.shangos`, which clears settings).
shangos-release.apk: normal. shangos-offline.apk: no INTERNET permission. shangos-debug.apk: debuggable and slower; never benchmark with it.
Package id changed in 0.3.0: remove the old one first (adb uninstall com.madushas.shangos), or use ./shangos.sh wizard, Clean up.
