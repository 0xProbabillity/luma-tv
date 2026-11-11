#!/bin/bash
# Unit tests for pure logic. Needs JDK 17 and JUnit 4 + Hamcrest jars (apt: junit4; or Maven Central).
set -euo pipefail
cd "$(dirname "$0")/.."
J="${JUNIT_JAR:-/usr/share/java/junit4.jar}"
H="${HAMCREST_JAR:-/usr/share/java/hamcrest-core.jar}"
[ -f "$J" ] || {
    echo "junit jar not found: set JUNIT_JAR and HAMCREST_JAR"
    exit 1
}
ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR (android.jar is needed to compile Net.java)}"
PKG="$(sed -n 's/.*package="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
OUT=build/test
rm -rf "$OUT"
mkdir -p "$OUT/gen"
printf 'package %s;\nfinal class Flavor { static final boolean DEBUG=false, NETWORK=true; private Flavor(){} }\n' "$PKG" >"$OUT/gen/Flavor.java"
SRC=src/${PKG//.//}
SOURCES=("$SRC/Net.java" "$SRC/Dbg.java" "$SRC/Spring.java" "$SRC/Prefs.java" "$SRC/Hardware.java" "$SRC/AppCache.java" "$SRC/Tint.java" "$SRC/Recency.java" "$SRC/AppList.java" "$SRC/Resume.java" "$SRC/Places.java" "$SRC/Cities.java" "$SRC/Frost.java" "$SRC/CrashGuard.java" "$SRC/SystemHome.java" "$SRC/Daylight.java" test/*.java)
javac -encoding UTF-8 -d "$OUT" -cp "$ANDROID_JAR:$J:$H" "$OUT/gen/Flavor.java" "${SOURCES[@]}"
mapfile -t TESTS < <(find test -name '*Test.java' -printf '%f
' | sed "s/\.java\$//; s/^/$PKG./" | sort)
java -cp "$OUT:$J:$H:$ANDROID_JAR" org.junit.runner.JUnitCore "${TESTS[@]}"
