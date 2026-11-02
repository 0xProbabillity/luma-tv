#!/bin/bash
# Format shell and Java sources. Install shfmt and google-java-format first.
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:---check}"
case "$MODE" in --check|--write) ;; *) echo "usage: $0 [--check|--write]" >&2; exit 2 ;; esac

command -v shfmt >/dev/null || {
    echo "format: shfmt is required (install shfmt)" >&2
    exit 1
}
command -v google-java-format >/dev/null || {
    echo "format: google-java-format is required (install google-java-format)" >&2
    exit 1
}

SHFMT=(shfmt -i 4 -ci)
JAVA=(google-java-format --aosp)
if [ "$MODE" = "--check" ]; then
    SHFMT+=(--diff)
    JAVA+=(--dry-run --set-exit-if-changed)
fi

"${SHFMT[@]}" build.sh shangos.sh test/run.sh
mapfile -t JAVA_FILES < <(find src test tools -name '*.java' -print | sort)
[ "${#JAVA_FILES[@]}" -eq 0 ] || "${JAVA[@]}" "${JAVA_FILES[@]}"
echo "Format checks passed."
