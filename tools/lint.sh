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
xmllint --noout AndroidManifest.xml res/values/*.xml res/xml/*.xml
git diff --check
echo "Lint checks passed."
