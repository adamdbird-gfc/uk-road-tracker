#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
asset_dir="$script_dir/../app/src/main/assets/historic-counties"
task_dir="$(mktemp -d)"
trap 'rm -rf "$task_dir"' EXIT
python3 "$script_dir/test_recover_county_reference.py"
# Immutable Firebase reference, checked against the versioned catalogue before
# installation. This never replaces the current settlement membership index.
curl --fail --silent --show-error --location --retry 2 --connect-timeout 30 --max-time 180 --max-filesize 12000000 \
  https://roadprints-data-822cc.web.app/build-references/historic-counties/b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850/boundaries.bin \
  -o "$task_dir/boundaries.bin"
python3 "$script_dir/recover_county_reference.py" --boundaries "$task_dir/boundaries.bin" "$asset_dir"
