#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
asset_dir="$script_dir/../app/src/main/assets/historic-counties"
task_dir="$(mktemp -d)"
trap 'rm -rf "$task_dir"' EXIT
python3 -m venv "$task_dir/venv"
"$task_dir/venv/bin/pip" install --quiet pyshp==3.1.6 shapely==2.2.0
curl --fail --silent --show-error --location --retry 2 --connect-timeout 30 --max-time 180 \
  https://county-borders.co.uk/UKDefinitionA_WG84_Full_Resolution.zip -o "$task_dir/source.zip"
"$task_dir/venv/bin/python" "$script_dir/package_historic_counties.py" "$task_dir/source.zip" "$asset_dir"
