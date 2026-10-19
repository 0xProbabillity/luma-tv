#!/bin/bash
# Redraws the QR codes in res/drawable-nodpi from res/raw/support.txt and the source link in res/raw/about.txt.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v qrencode >/dev/null || { echo "Install qrencode first (brew install qrencode / apt install qrencode)." >&2; exit 1; }
rm -f res/drawable-nodpi/qr_*.png
i=0
grep -v '^#' res/raw/support.txt | grep . | while IFS='|' read -r _ _ address; do
    qrencode -t PNG -s 12 -m 2 -l M -o "res/drawable-nodpi/qr_$i.png" "$address"; i=$((i+1))
done
qrencode -t PNG -s 12 -m 2 -l M -o res/drawable-nodpi/qr_source.png "$(sed -n 's/^source=//p' res/raw/about.txt)"
ls res/drawable-nodpi/qr_*.png
