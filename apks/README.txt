Prebuilt from this exact source (Phase 2) in the build sandbox, signed with a throwaway DEV key.
- shangos-release.apk : normal build (INTERNET permission, weather off by default)
- shangos-offline.apk : no INTERNET permission at all
- shangos-debug.apk   : debuggable, QA hooks on
Different key from the one ./build.sh creates on your machine: switching between a prebuilt APK and your own build
needs `adb uninstall com.madushas.shangos` first (this clears settings).
Built with the Debian dx tool instead of d8 (same Java 8 bytecode); your ./build.sh uses d8 when the SDK has it.
