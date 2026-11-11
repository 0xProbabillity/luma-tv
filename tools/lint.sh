#!/bin/bash
# Repository checks that do not require a device or private developer tooling.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v shellcheck >/dev/null || {
    echo "lint: shellcheck is required (install shellcheck)" >&2
    exit 1
}
command -v xmllint >/dev/null || {
    echo "lint: xmllint is required (install libxml2-utils)" >&2
    exit 1
}

shellcheck -x build.sh shangos.sh test/run.sh

# Size budget (docs/dev/REFACTOR.md). A ratchet: lower MAIN_MAX after every extraction, never raise it.
MAIN_MAX=800 # the composition root; do not raise
OTHER_MAX=700 # any other class; Widget subclasses are exempt
SRC=src/dev/madusha/shangos
over=0
for f in "$SRC"/*.java; do
    n=$(wc -l <"$f")
    case "$(basename "$f")" in
    MainActivity.java) max=$MAIN_MAX ;;
    *Widget.java) continue ;;
    *) max=$OTHER_MAX ;;
    esac
    if [ "$n" -gt "$max" ]; then
        echo "lint: $f has $n lines (budget $max)" >&2
        over=1
    fi
done
[ "$over" -eq 0 ] || exit 1
xmllint --noout AndroidManifest.xml res/values/*.xml res/xml/*.xml
git diff --check
echo "Lint checks passed."
