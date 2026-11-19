#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture
baseline="$RUNNER_TEMP/roadprints310.apk"
candidate="$RUNNER_TEMP/roadprints-a2-runtime.apk"
fixtures="$RUNNER_TEMP/a2-scaled-scaled-fixtures"
mkdir -p "$fixtures"
python3 scripts/generate_dense_map_fixture_a2.py \
    --reference "$RUNNER_TEMP/uk-road-reference-v3.bin" \
    --output "$fixtures" --accepted 750 --train 925 --edges-per-journey 2

tar -cf "$RUNNER_TEMP/a2-scaled-scaled-fixtures.tar" -C "$fixtures" .
seed_archive() {
 adb shell run-as "$package" mkdir -p files shared_prefs
 adb push "$RUNNER_TEMP/a2-scaled-scaled-fixtures.tar" /data/local/tmp/a2-scaled-scaled-fixtures.tar >/dev/null
 adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/a2-scaled-scaled-fixtures.tar'"
 adb shell rm -f /data/local/tmp/a2-scaled-scaled-fixtures.tar
 adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
}
launch_collect() {
 local label="$1" target="$RUNNER_TEMP/a2-scaled-$1.log"
 adb shell am force-stop "$package" || true
 adb logcat -c
 adb shell am start -W -n "$package/.OnboardingActivity" > "$RUNNER_TEMP/a2-scaled-$label-am-start.log"
 for attempt in $(seq 1 85); do
   adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
   if grep -q 'map_outcome=ready\|map_outcome=failure\|FATAL EXCEPTION' "$target"; then break; fi
   sleep 2
 done
 adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
 adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/a2-scaled-$label-meminfo.txt" || true
 grep -q 'map_outcome=ready' "$target" || { echo "::error::$label Map did not become ready"; tail -n 80 "$target"; return 1; }
 ! grep -qE 'FATAL EXCEPTION|OutOfMemoryError' "$target"
}
adb install "$baseline" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
seed_archive
launch_collect baseline
# Equivalent cold caches: reinstall each build from a clean app data state.
# This comparison is expressly NOT the separate in-place upgrade test.
adb uninstall "$package" >/dev/null
adb install "$candidate" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
seed_archive
for f in "$fixtures"/journey_dense-osm-0000.json "$fixtures"/journey_dense-osm-0749.json "$fixtures"/journey_railway-history-0000.json "$fixtures"/journey_railway-history-0924.json; do
 adb exec-out run-as "$package" cat "files/${f##*/}" | cmp - "$f"
done
launch_collect candidate
python3 - "$RUNNER_TEMP/a2-scaled-baseline.log" "$RUNNER_TEMP/a2-scaled-candidate.log" "$RUNNER_TEMP/a2-scaled-runtime-summary.json" <<'PY'
import json,re,sys,pathlib
def parse(path):
 lines=pathlib.Path(path).read_text().splitlines()
 metrics={}
 for name in ("map_total","map_navigation","map_local_roads","map_progressive_publish",
              "map_progressive_disabled","map_heap_stage","map_archive_progress",
              "map_batch_boundary","map_outcome=ready"):
  rows=[r for r in lines if name in r]
  metrics[name]={"events":len(rows),"last":rows[-1][-360:] if rows else ""}
  if rows:
   m=re.search(r"\bms=(\d+)", rows[-1])
   if m:metrics[name]["last_ms"]=int(m.group(1))
 assert metrics["map_outcome=ready"]["events"],path
 return metrics
baseline,candidate=map(parse,sys.argv[1:3])
assert candidate["map_batch_boundary"]["events"]>0,"Candidate did not scan in batches"
assert candidate["map_progressive_publish"]["events"]>=2,"Dense fixture never progressively published"
def sections(metrics):
 row=metrics["map_local_roads"]["last"]
 match=re.search(r"sections=(\d+) lines=(\d+) unique_mm=(\d+)",row)
 assert match, row
 return tuple(int(v) for v in match.groups())
base_sections=sections(baseline)
new_sections=sections(candidate)
assert base_sections==new_sections, (base_sections,new_sections)
assert base_sections[0]>=1200, f"Too few distinct canonical sections in scaled test: {base_sections}"
assert candidate["map_heap_stage"]["events"]>0,"Missing heap stage markers"
output={"fixture_journeys":1675,"accepted_road_fixtures":1500,"road_journeys":750,
 "matched_sections":base_sections[0],"covered_lines":base_sections[1],
 "cumulative_mm":base_sections[2],
 "same_cold_cache_state":True,"baseline":baseline,"candidate":candidate,
 "note":"Deterministic synthetic accepted OSM edge fixtures, NOT owner history. Identical cold-cache fresh-install conditions. Emulator API35 results do not establish Pixel 10 Android17 performance."}
pathlib.Path(sys.argv[3]).write_text(json.dumps(output,indent=2))
print(json.dumps(output,indent=2))
PY
echo "A2 scaled OSM-backed runtime: 1,675 records with 1,500 accepted edge fixtures across 750 road journeys. Cold-cache comparison, coverage equality and incremental publication checked. See artifact JSON, logcat, meminfo." >> "$GITHUB_STEP_SUMMARY"
