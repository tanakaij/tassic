#!/usr/bin/env bash
# Regenerates the full PWA icon set from the root logo.
#
# This now delegates to generate_icons.py (cross-platform, Pillow-based),
# which strips the white background to real transparency and builds a
# properly safe-zoned maskable icon — the old sips-based version baked
# a white square behind the logo on every icon, including the maskable
# one, which is why the launcher icon and in-app logo showed a visible
# white box. Kept as a thin wrapper so `./tools/generate_icons.sh` still
# works the same way it always has.
set -euo pipefail

SRC="${1:-Logo.png}"
python3 "$(dirname "$0")/generate_icons.py" "$SRC"
