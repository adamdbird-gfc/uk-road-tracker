#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
asset_dir="$script_dir/../app/src/main/assets/historic-counties"
# Offline by default. A missing pin must never silently consume Hosting bandwidth.
python3 "$script_dir/test_recover_county_reference.py"
if [ -f "$asset_dir/boundaries.bin" ]; then
  python3 "$script_dir/recover_county_reference.py" --boundaries "$asset_dir/boundaries.bin" "$asset_dir"
elif [ -n "${ROADPRINTS_COUNTY_REFERENCE_FILE:-}" ]; then
  python3 "$script_dir/recover_county_reference.py" --boundaries "$ROADPRINTS_COUNTY_REFERENCE_FILE" "$asset_dir"
else
  echo "Pinned county reference unavailable locally; Firebase access is disabled. Set ROADPRINTS_COUNTY_REFERENCE_FILE to an existing verified file." >&2
  exit 1
fi
