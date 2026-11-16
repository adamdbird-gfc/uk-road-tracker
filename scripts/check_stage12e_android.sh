#!/usr/bin/env bash
set -euo pipefail

package=com.roadprints.capture
baseline="$RUNNER_TEMP/build289.apk"
fixtures="$RUNNER_TEMP/stage12e-fixtures"
log="$RUNNER_TEMP/stage12e-android.log"
mkdir -p "$fixtures"
trap 'adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/stage12e-android-final.log"' EXIT
python - "$fixtures" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
coords = [[0.3753447, 51.44112821], [0.3753871, 51.44128429]]
geometry = {"type": "LineString", "coordinates": coords}
for i, mode in enumerate(("driving", "driving", "walking")):
    journey_id = f"stage12e-{i}"
    row = {
        "journey_id": journey_id, "title": f"Owner retained {i}", "mode": mode,
        "transport_confirmation": "confirmed", "distance_meters": 23.4, "gps_point_count": 2,
        "started_at": "2026-11-15T12:00:00Z", "ended_at": "2026-11-15T12:01:00Z",
        "source": {"type": "android_activity_capture"}, "processing_status": "complete",
        "raw_capture_samples": [[*point, 2, 1794744000000 + j * 2000, 9, "gps"] for j, point in enumerate(coords)],
        "route_geometry": geometry,
        "processing_result": {"geojson": {"type": "FeatureCollection", "features": [{"type": "Feature", "geometry": geometry}]}},
    }
    if i == 0:
        phantom = {"type": "LineString", "coordinates": [[0.374, 51.442], [0.3742, 51.442]]}
        row["processing_result"]["geojson"]["features"].append({"type": "Feature", "geometry": phantom})
        row["journey_corrections"] = {"removed_matched_segments": [1], "removed_road_ids": ["name:ordnance road"]}
    (root / f"journey_{journey_id}.json").write_text(json.dumps(row))
(root / "roadprints_ncn_national_v2.xml").write_text('<map><boolean name="enabled" value="false"/><long name="revision" value="17"/><string name="activity">cycling</string></map>')
(root / "roadprints_achievements_v1.xml").write_text('<map><string name="unlocked">{"test-award":{"level":1,"unlocked_at":1794744000000}}</string></map>')
(root / "roadprints_onboarding.xml").write_text('<map><boolean name="complete" value="true"/></map>')
PY

adb install "$baseline"
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
adb shell am force-stop "$package"

seed() { adb shell "run-as $package sh -c 'cat > $1'" < "$2"; }
verify() { adb exec-out run-as "$package" cat "$1" | cmp - "$2"; }
adb shell run-as "$package" mkdir -p files shared_prefs
for file in "$fixtures"/journey_*.json; do seed "files/${file##*/}" "$file"; done
for file in "$fixtures"/*.xml; do seed "shared_prefs/${file##*/}" "$file"; done

adb install -r "$RUNNER_TEMP/$APK"
adb shell dumpsys package "$package" > "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionCode=290 " "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionName=0.25.244" "$RUNNER_TEMP/installed-roadprints.txt"
for file in "$fixtures"/journey_*.json; do verify "files/${file##*/}" "$file"; done
for file in "$fixtures"/*.xml; do verify "shared_prefs/${file##*/}" "$file"; done

wait_log() {
    for ((attempt = 0; attempt < 60; attempt++)); do
        adb logcat -d -s RoadprintsTiming AndroidRuntime > "$log"
        if grep -E "$1" "$log"; then return; fi
        sleep 2
    done
    cat "$log"
    return 1
}
adb logcat -c
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
wait_log 'map_local_roads.*calculated=2 reused=0 held=0 sections=1 lines=1 unique_mm=17603'
wait_log 'map_render'
cp "$log" "$RUNNER_TEMP/stage12e-android-cold.log"
for file in "$fixtures"/journey_*.json; do verify "files/${file##*/}" "$file"; done
verify shared_prefs/roadprints_achievements_v1.xml "$fixtures/roadprints_achievements_v1.xml"

adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
wait_log 'map_cache.*source=hit|map_local_roads.*calculated=0 reused=2 held=0 sections=1 lines=1 unique_mm=17603'
wait_log 'map_render'

printf '%s\n' 'Build 289 upgraded in place. Seeded archive and preferences were preserved. Cold and repeat Map showed one 17.603m section for two driving journeys.' | tee -a "$GITHUB_STEP_SUMMARY"
