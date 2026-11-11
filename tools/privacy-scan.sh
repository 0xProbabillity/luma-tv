#!/usr/bin/env bash
# Looks for things that must never be published: private network addresses, MAC addresses, e-mail addresses, keys and
# secret files. Run it before pushing (and in a pre-commit hook: ln -s ../../tools/privacy-scan.sh .git/hooks/pre-commit).
# Placeholders used in the docs (192.168.1.50, example.com) and the address tests in Net are allowed.
set -u
cd "$(dirname "$0")/.."

list() {
    if git rev-parse --git-dir >/dev/null 2>&1; then git ls-files; else
        find . -type f -not -path './.git/*' -not -path './build/*' -not -path './dist/*' -not -path './apks/*' \
            -not -path './.local/*' -not -path './.signing/*' | sed 's|^\./||' # the last two are ignored by git (.gitignore)
    fi
}
status=0
found() {
    echo "privacy-scan: $1" >&2
    status=1
}

# Text files only (grep -I skips binaries), and not the generated city list or the checked-in brand art.
mapfile -t files < <(list | grep -Ev '^(res/raw/cities\.txt|res/raw/zones\.txt|artwork/|docs/assets/|apks/|dist/|build/)')

private_ip='\b(10\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|192\.168\.[0-9]{1,3}\.[0-9]{1,3}|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]{1,3}\.[0-9]{1,3})\b'
hits="$(grep -InE "$private_ip" "${files[@]}" 2>/dev/null |
    grep -Ev '^(test/NetTest\.java|src/dev/madusha/shangos/Net\.java|tools/privacy-scan\.sh):' |
    grep -Ev '192\.168\.1\.50|192\.168\.1\.1\b|10\.0\.0\.5\b' || true)"
[ -z "$hits" ] || found "private network address (use 192.168.1.50 as the example):"$'\n'"$hits"

hits="$(grep -InE '\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b' "${files[@]}" 2>/dev/null || true)"
[ -z "$hits" ] || found "MAC address:"$'\n'"$hits"

hits="$(grep -InoE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "${files[@]}" 2>/dev/null |
    grep -Ev '@example\.(com|org)|noreply|@users\.noreply\.github\.com' || true)"
[ -z "$hits" ] || found "e-mail address (use a no-reply or example address):"$'\n'"$hits"

hits="$(grep -InE -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' -e 'AKIA[0-9A-Z]{16}' -e '(ghp|github_pat)_[A-Za-z0-9_]{20,}' \
    "${files[@]}" 2>/dev/null | grep -Ev '^tools/privacy-scan\.sh:' || true)"
[ -z "$hits" ] || found "key or token:"$'\n'"$hits"

hits="$(list | grep -E '\.(jks|keystore|p12|pfx|pem)$|(^|/)\.env$|(^|/)local\.properties$' || true)"
[ -z "$hits" ] || found "secret-looking file is tracked:"$'\n'"$hits"

[ "$status" -eq 0 ] && echo "privacy-scan: clean"
exit "$status"
