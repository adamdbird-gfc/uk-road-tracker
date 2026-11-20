#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture
fixtures="$RUNNER_TEMP/a2-owner-312-fixtures"
mkdir -p "$fixtures"
trap 'status=$?; adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/mixed-mode-final.log" || true; echo "mixed_mode_smoke_exit=$status" >> "$GITHUB_STEP_SUMMARY"' EXIT

python - "$fixtures" <<'PY'
import json,pathlib,sys
folder=pathlib.Path(sys.argv[1])
coords=[[0.3753447,51.44112821],[0.3753871,51.44128429]]
geometry={"type":"LineString","coordinates":coords}
for i,mode in enumerate(("driving","walking","running","cycling")):
    row={"journey_id":f"mixed-mode-{mode}","mode":mode,
         "title":f"Mixed-mode test {mode}","transport_confirmation":"confirmed",
         "distance_meters":17.603,"started_at":"2026-11-15T12:00:00Z",
         "ended_at":"2026-11-15T12:10:00Z","processing_status":"complete",
         "source":{"type":"android_activity_capture"},
         "route_geometry":geometry,
         "processing_result":{"geojson":{"type":"FeatureCollection",
             "features":[{"type":"Feature","geometry":geometry}]}}}
    (folder/f"journey_mixed-mode-{mode}.json").write_text(json.dumps(row))
# Owner-scale smoke: historic railway rows must not block network-only Map.
for i in range(1671):
    train={"journey_id":f"railway-history-{i:04d}","mode":"train",
           "title":"Historical train","distance_meters":12000,
           "started_at":"2026-11-15T08:00:00Z",
           "ended_at":"2026-11-15T08:30:00Z","processing_status":"complete"}
    (folder/f"journey_railway-history-{i:04d}.json").write_text(json.dumps(train))
(folder/"roadprints_onboarding.xml").write_text('<map><boolean name="complete" value="true"/></map>')
PY

adb install "$RUNNER_TEMP/build311.apk"
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F 'Status: ok'
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p files shared_prefs
tar -cf "$RUNNER_TEMP/owner312-fixtures.tar" -C "$fixtures" .
adb push "$RUNNER_TEMP/owner312-fixtures.tar" /data/local/tmp/owner312-fixtures.tar
adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/owner312-fixtures.tar'"
adb shell rm -f /data/local/tmp/owner312-fixtures.tar
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
adb install -r "$RUNNER_TEMP/$APK"
adb shell dumpsys package "$package" > "$RUNNER_TEMP/mixed-mode-package.txt"
grep -F 'versionCode=312 ' "$RUNNER_TEMP/mixed-mode-package.txt"
grep -F 'versionName=0.25.266' "$RUNNER_TEMP/mixed-mode-package.txt"
for file in "$fixtures"/journey_mixed-mode-*.json "$fixtures"/journey_railway-history-0000.json "$fixtures"/journey_railway-history-1670.json; do
    adb exec-out run-as "$package" cat "files/${file##*/}" | cmp - "$file"
done
adb logcat -c
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F 'Status: ok'
for attempt in $(seq 1 60); do
    adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/mixed-mode-map.log"
    if grep -q 'map_local_roads' "$RUNNER_TEMP/mixed-mode-map.log"; then break; fi
    sleep 2
done
python - "$RUNNER_TEMP/mixed-mode-map.log" <<'PYVERIFY'
import re,sys
lines=open(sys.argv[1]).read().splitlines()
rows=[line for line in lines if "map_local_roads" in line]
assert rows, 'Missing integrated cumulative attribution diagnostic'
values={k:int(v) for k,v in re.findall(
    r"(calculated|reused|held|budget_holds|sections|lines|unique_mm)=(\d+)",rows[-1])}
print("Mixed-mode integrated attribution",values,flush=True)
assert values.get("calculated",0)+values.get("reused",0)==4, values
assert values.get("held")==0 and values.get("budget_holds")==0,values
assert values.get("sections",0)>0 and values.get("lines",0)>0,values
assert values.get("unique_mm",0)>0,values
assert "foot_major_intervals=" in rows[-1], "Missing versioned major-road section metric"
assert "foot_unresolved_mm=" in rows[-1], "Missing on-foot attribution breakdown"
assert "foot_unresolved_reasons=" in rows[-1], "Missing diagnostic reason categories"
assert "major_or_other_mm=" in rows[-1], "Missing major-road classification"

PYVERIFY
for attempt in $(seq 1 30); do
    adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/mixed-mode-map.log"
    if grep -q 'map_network_layers' "$RUNNER_TEMP/mixed-mode-map.log"; then break; fi
    sleep 2
done
python - "$RUNNER_TEMP/mixed-mode-map.log" <<'PYLAYERS'
import re,sys
lines=open(sys.argv[1]).read().splitlines()
rows=[line for line in lines if "map_network_layers" in line]
assert rows, "Map did not reach network layer rendering"
values={k:int(v) for k,v in re.findall(r"(cumulative_minor|historical)=(\d+)",rows[-1])}
print("Rendered network",values,flush=True)
assert values.get("cumulative_minor",0)>0 and values.get("historical")==0,values
assert not any("FATAL EXCEPTION" in line or "OutOfMemoryError" in line for line in lines)
PYLAYERS
grep -q 'map_stage.*phase=archive_geometry_processing' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_stage.*phase=ready' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_motorway_reference_init' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_a_road_reference_init' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_archive_progress' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_outcome=ready' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_shared_corridors' "$RUNNER_TEMP/mixed-mode-map.log"
grep -q 'map_partial_corridors' "$RUNNER_TEMP/mixed-mode-map.log"
printf '%s\n' 'Owner-scale (1,675 journeys), mixed-mode fixtures and versioned national reference opened without an attributed-road identity error. In-place 311 to 312 upgrade and fixture preservation passed on Android emulator. Owner Pixel 10 archive and tab-retention acceptance remains the next gate.' >> "$GITHUB_STEP_SUMMARY"
