#!/bin/bash
# Prints the CHANGELOG.md section for one version (used by the release workflow).
#   tools/release-notes.sh 0.3.0
set -euo pipefail
cd "$(dirname "$0")/.."
version="${1:?usage: tools/release-notes.sh VERSION (for example 0.3.0)}"
notes="$(awk -v v="$version" '
    /^## / {
        if (found) exit
        title = substr($0, 4)
        # "## 0.3.0: summary", "## 0.3.0 - summary" and "## 0.3.0" all match 0.3.0, but 0.3.0-rc1 and 0.3.01 do not.
        if (index(title, v) == 1) { rest = substr(title, length(v) + 1); if (rest == "" || rest ~ /^[^0-9A-Za-z.-]/) { found = 1; next } }
    }
    found { print }
' CHANGELOG.md | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')"
if [ -z "$notes" ]; then
    echo "release-notes: no section for $version in CHANGELOG.md" >&2
    exit 1
fi
printf '%s\n' "$notes"
