#!/usr/bin/env bash
set -euo pipefail
project="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-$project/app/build/ui-previews}"
if (( $# )); then shift; fi
mkdir -p -- "$output"
output="$(cd -- "$output" && pwd)"
gradle -p "$project" :app:testDebugUnitTest \
    --tests com.roadprints.capture.RoadprintsUiPreviewTest \
    "-ProadprintsPreviewDir=$output" "$@"
printf 'Preview PNGs: %s\n' "$output"
