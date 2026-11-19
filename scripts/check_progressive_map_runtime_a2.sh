#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture
baseline="$RUNNER_TEMP/roadprints310.apk"
candidate="$RUNNER_TEMP/roadprints-a2-runtime.apk"
fixtures="$RUNNER_TEMP/a2-runtime-fixtures"
mkdir -p "$fixtures"
python3 - "$fixtures" <<'PY'
import json,pathlib,sys
folder=pathlib.Path(sys.argv[1])
# Use exactly comparable fixtures on both builds, including road/foot/cycle.
coords=[[0.3753447,51.44112821],[0.3753871,51.44128429]]
geometry={"type":"LineString","coordinates":coords}
for mode in ("driving","walking","running","cycling"):
 row={"journey_id":f"mixed-mode-{mode}","mode":mode,
 "title":f"Mode {mode}","transport_confirmation":"confirmed",
 "distance_meters":17.603,"started_at":"2026-11-15T12:00:00Z",
 "ended_at":"2026-11-15T12:10:00Z","processing_status":"complete",
 "source":{"type":"android_activity_capture"},"route_geometry":geometry,
 "processing_result":{"geojson":{"type":"FeatureCollection",
 "features":[{"type":"Feature","geometry":geometry}]}}}
 (folder/f"journey_mixed-mode-{mode}.json").write_text(json.dumps(row))
for i in range(1671):
 row={"journey_id":f"railway-history-{i:04d}","mode":"train",
 "title":"Historical train","distance_meters":12000,
 "started_at":"2026-11-15T08:00:00Z",
 "ended_at":"2026-11-15T08:30:00Z","processing_status":"complete"}
 (folder/f"journey_railway-history-{i:04d}.json").write_text(json.dumps(row))
(folder/"roadprints_onboarding.xml").write_text('<map><boolean name="complete" value="true"/></map>')
PY
tar -cf "$RUNNER_TEMP/a2-fixtures.tar" -C "$fixtures" .
seed_archive() {
 adb shell run-as "$package" mkdir -p files shared_prefs
 adb push "$RUNNER_TEMP/a2-fixtures.tar" /data/local/tmp/a2-fixtures.tar >/dev/null
 adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/a2-fixtures.tar'"
 adb shell rm -f /data/local/tmp/a2-fixtures.tar
 adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
}
launch_collect() {
 local label="$1" target="$RUNNER_TEMP/a2-$1.log"
 adb shell am force-stop "$package" || true
 adb logcat -c
 adb shell am start -W -n "$package/.OnboardingActivity" > "$RUNNER_TEMP/a2-$label-am-start.log"
 for attempt in $(seq 1 85); do
   adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
   if grep -q 'map_outcome=ready\|map_outcome=failure\|FATAL EXCEPTION' "$target"; then break; fi
   sleep 2
 done
 adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
 adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/a2-$label-meminfo.txt" || true
 grep -q 'map_outcome=ready' "$target" || { echo "::error::$label Map did not become ready"; tail -n 80 "$target"; return 1; }
 ! grep -qE 'FATAL EXCEPTION|OutOfMemoryError' "$target"
}
adb install "$baseline" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
seed_archive
launch_collect baseline
# In-place signed upgrade, without erasing the owner's equivalent 1675 records.
adb install -r "$candidate" >/dev/null
for f in "$fixtures"/journey_mixed-mode-*.json "$fixtures"/journey_railway-history-0000.json "$fixtures"/journey_railway-history-1670.json; do
 adb exec-out run-as "$package" cat "files/${f##*/}" | cmp - "$f"
done
# Delete disposable cache so the candidate rebuilds the same archive cold.
# Preserve files, shared preferences, journey data and authoritative coverage.
adb shell "run-as $package sh -c 'rm -rf cache/*'" || true
launch_collect candidate
python3 - "$RUNNER_TEMP/a2-baseline.log" "$RUNNER_TEMP/a2-candidate.log" "$RUNNER_TEMP/a2-runtime-summary.json" <<'PY'
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
assert candidate["map_heap_stage"]["events"]>0,"Missing heap stage markers"
output={"fixture_journeys":1675,"baseline":baseline,"candidate":candidate,
 "note":"Synthetic fixture has four accepted road journeys; preview publication may be legitimately absent if heap headroom is low or fewer edges are eligible. This is not an owner-scale mapped-road benchmark."}
pathlib.Path(sys.argv[3]).write_text(json.dumps(output,indent=2))
print(json.dumps(output,indent=2))
PY
echo "A2 runtime: baseline/candidate 1,675-record smoke and data preservation passed. See artifact JSON, logcat, meminfo." >> "$GITHUB_STEP_SUMMARY"
