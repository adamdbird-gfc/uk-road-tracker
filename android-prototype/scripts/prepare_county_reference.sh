#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
asset_dir="$script_dir/../app/src/main/assets/historic-counties"
task_dir="$(mktemp -d)"
trap 'rm -rf "$task_dir"' EXIT
python3 -m venv "$task_dir/venv"
"$task_dir/venv/bin/pip" install --quiet pyshp==3.1.6 shapely==2.2.0
"$task_dir/venv/bin/python" "$script_dir/test_recover_county_reference.py"
if curl --fail --silent --show-error --location --retry 2 --connect-timeout 30 --max-time 180 \
  https://county-borders.co.uk/UKDefinitionA_WG84_Full_Resolution.zip -o "$task_dir/source.zip" \
  && "$task_dir/venv/bin/python" "$script_dir/package_historic_counties.py" "$task_dir/source.zip" "$asset_dir"; then
  exit 0
fi
# Keep the versioned reference when the upstream source changes. The fallback
# carries the previously verified bytes; both catalogue and data hash must match.
echo 'County source changed; recovering the exact pinned reference from Roadprints 0.25.167'
curl --fail --silent --show-error --location --retry 2 --connect-timeout 30 --max-time 180 \
  https://github.com/adamdbird-gfc/uk-road-tracker/releases/download/android-0.25.167/roadprints-0.25.167.apk -o "$task_dir/pinned.apk"
"$task_dir/venv/bin/python" "$script_dir/recover_county_reference.py" "$task_dir/pinned.apk" "$asset_dir"
