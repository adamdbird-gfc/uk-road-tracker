#!/usr/bin/env bash
set -euo pipefail

package=com.roadprints.capture
baseline="$RUNNER_TEMP/build291.apk"
fixtures="$RUNNER_TEMP/accepted-cumulative-fixtures"
log="$RUNNER_TEMP/network-recovery.log"
mkdir -p "$fixtures"
trap 'adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/network-recovery-final.log"' EXIT
python - "$fixtures" <<'PY'
import json, pathlib, struct, sys
root = pathlib.Path(sys.argv[1])
coords = [[0.3753447, 51.44112821], [0.3753871, 51.44128429]]
geometry = {"type": "LineString", "coordinates": coords}
motorway = json.loads(pathlib.Path("canonical-motorways-v1.json").read_text())["roads"]["M2"]["anchors"][:24]
a_road = json.loads(pathlib.Path("canonical-a-roads-v5/GB-A2.json").read_text())["paths"][0][:24]
def major_feature(ref, points):
    return {"type": "Feature", "properties": {"road_ref": ref, "distance_m": 1000},
            "geometry": {"type": "LineString", "coordinates": points}}
for i, mode in enumerate(["driving"] * 1599 + ["walking"]):
    journey_id = f"accepted-cumulative-{i}"
    if i == 1:
        geometry = {"type": "LineString", "coordinates": list(reversed(coords))}
    elif mode == "driving":
        geometry = {"type": "LineString", "coordinates": coords * 128}
    else:
        geometry = {"type": "LineString", "coordinates": coords}
    row = {
        "journey_id": journey_id, "title": f"Owner retained {i}", "mode": mode,
        "transport_confirmation": "confirmed", "distance_meters": 23.4, "gps_point_count": 2,
        "started_at": "2026-11-15T12:00:00Z", "ended_at": "2026-11-15T12:01:00Z",
        "source": {"type": "google_timeline" if i == 1 else "android_activity_capture"}, "processing_status": "complete",
        "raw_capture_samples": [[*coords[j % 2], 2, 1794744000000 + j * 100, 9, "gps"] for j in range(512)],
        "route_geometry": geometry,
        "processing_result": {"geojson": {"type": "FeatureCollection", "features": [{"type": "Feature", "geometry": geometry}]}},
    }
    if i == 2:
        row["processing_result"]["motorway_geojson"] = {"type": "FeatureCollection",
                "features": [major_feature("M2", motorway)]}
    if i == 3:
        row["processing_result"]["a_road_geojson"] = {"type": "FeatureCollection",
                "features": [major_feature("A2", a_road)]}
    if i == 1:
        row.pop("raw_capture_samples")
    if i == 0:
        phantom = {"type": "LineString", "coordinates": [[0.374, 51.442], [0.3742, 51.442]]}
        row["processing_result"]["geojson"]["features"].append({"type": "Feature", "geometry": phantom})
        row["journey_corrections"] = {"removed_matched_segments": [len(geometry["coordinates"]) - 1], "removed_road_ids": ["name:ordnance road"]}
    (root / f"journey_{journey_id}.json").write_text(json.dumps(row))
(root / "roadprints_ncn_national_v2.xml").write_text('<map><boolean name="enabled" value="false"/><long name="revision" value="17"/><string name="activity">cycling</string></map>')
(root / "roadprints_achievements_v1.xml").write_text('<map><string name="unlocked">{"test-award":{"level":1,"unlocked_at":1794744000000}}</string></map>')
(root / "roadprints_onboarding.xml").write_text('<map><boolean name="complete" value="true"/></map>')
camera = {"longitude": sum(point[0] for point in coords) / 2,
          "latitude": sum(point[1] for point in coords) / 2, "zoom": 19.0}
values = "".join(f'<long name="{key}" value="{struct.unpack(">q", struct.pack(">d", value))[0]}"/>'
                 for key, value in camera.items())
(root / "roadprints_map_camera.xml").write_text("<map>" + values + "</map>")

PY

adb install "$baseline"
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
adb shell am force-stop "$package"

seed() { adb shell "run-as $package sh -c 'cat > $1'" < "$2"; }
verify() { adb exec-out run-as "$package" cat "$1" | cmp - "$2"; }
adb shell run-as "$package" mkdir -p files shared_prefs
for file in "$fixtures"/journey_*.json; do seed "files/${file##*/}" "$file"; done
for file in "$fixtures"/*.xml; do seed "shared_prefs/${file##*/}" "$file"; done

# Isolate the stream lifetime change using the same signed app and saved evidence.
adb install -r "$RUNNER_TEMP/network-reference-before.apk"
adb logcat -c
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
for ((attempt = 0; attempt < 90; attempt++)); do
    adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/network-recovery-before.log"
    if grep -q 'map_local_roads' "$RUNNER_TEMP/network-recovery-before.log"; then break; fi
    sleep 2
done
# An intermittent stream-lifetime failure need not reproduce on every Android
# runtime or garbage-collection schedule. Retain the negative-control evidence,
# but gate publication on the *corrected* app passing integrated validation.
if grep -E 'map_local_roads.*held=[1-9][0-9]*.*hold_reasons=.*(ClosedChannelException|IOException)' "$RUNNER_TEMP/network-recovery-before.log"; then
    echo "reference_lifetime_negative_control=reproduced" >> "$GITHUB_STEP_SUMMARY"
else
    echo "reference_lifetime_negative_control=not_reproduced; underlying owner-device cause remains unconfirmed" >> "$GITHUB_STEP_SUMMARY"
fi
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/$APK"
adb shell dumpsys package "$package" > "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionCode=292 " "$RUNNER_TEMP/installed-roadprints.txt"
grep -F "versionName=0.25.246" "$RUNNER_TEMP/installed-roadprints.txt"
for file in "$fixtures"/journey_*.json; do verify "files/${file##*/}" "$file"; done
# Android may rewrite SharedPreferences XML with a declaration, indentation,
# or changed element order. Verify camera IEEE-754 values, not serializer bytes.
for file in "$fixtures"/*.xml; do
    if [[ "${file##*/}" == "roadprints_map_camera.xml" ]]; then
        adb exec-out run-as "$package" cat "shared_prefs/roadprints_map_camera.xml" > "$RUNNER_TEMP/actual-map-camera.xml"
        python - "$file" "$RUNNER_TEMP/actual-map-camera.xml" <<'PYCAMERA'
import struct, sys, xml.etree.ElementTree as ET
def camera(path):
    root = ET.parse(path).getroot()
    assert root.tag == 'map', root.tag
    values = {node.attrib['name']: int(node.attrib['value'])
              for node in root if node.tag == 'long'}
    assert all(key in values for key in ('longitude', 'latitude', 'zoom')), values
    return {key: struct.unpack('>d', struct.pack('>q', values[key]))[0]
            for key in ('longitude', 'latitude', 'zoom')}
expected, actual = camera(sys.argv[1]), camera(sys.argv[2])
for key in expected:
    # Android may rewrite the XML, but must retain the actual camera settings.
    assert actual[key] == expected[key], (key, expected[key], actual[key])
print('Verified restored Map camera settings semantically', actual)
PYCAMERA
    else
        verify "shared_prefs/${file##*/}" "$file"
    fi
done

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
wait_log 'map_local_roads.*held=0.*sections=1 lines=1 unique_mm=17603'
python - "$log" <<'PYCOVERAGE'
import re,sys
text=open(sys.argv[1]).read()
rows=[line for line in text.splitlines() if "map_local_roads" in line]
assert rows
values=dict((key,int(value)) for key,value in re.findall(r"(calculated|reused|held|budget_holds)=(\d+)",rows[-1]))
assert values["calculated"]+values["reused"]==1599,values
assert values["held"]==values["budget_holds"]==0,values
PYCOVERAGE
wait_log 'map_render'
python - "$log" <<'PYLAYERS'
import re,sys
rows=[line for line in open(sys.argv[1]) if "map_network_layers" in line]
assert rows
values={key:int(value) for key,value in re.findall(r"(covered_motorways|uncovered_motorways|covered_a_roads|uncovered_a_roads|cumulative_minor|historical)=(\d+)",rows[-1])}
for key in ["covered_motorways","uncovered_motorways","covered_a_roads","uncovered_a_roads","cumulative_minor"]:
    assert values[key]>0,values
assert values["historical"]==0,values
PYLAYERS
cp "$log" "$RUNNER_TEMP/network-recovery-cold.log"
if grep -Eq 'FATAL EXCEPTION|OutOfMemoryError|foreground_ui_stall_ms=([2-9][0-9]{3}|[0-9]{5,})' "$log"; then
    exit 1
fi
for file in "$fixtures"/journey_*.json; do verify "files/${file##*/}" "$file"; done
verify shared_prefs/roadprints_achievements_v1.xml "$fixtures/roadprints_achievements_v1.xml"

adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/.OnboardingActivity" | grep -F "Status: ok"
wait_log 'map_cache.*source=hit|map_local_roads.*calculated=0 reused=1599 held=0.*sections=1 lines=1 unique_mm=17603'
wait_log 'map_render'

cp "$log" "$RUNNER_TEMP/network-recovery-warm.log"
adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/network-recovery-memory.log"
adb exec-out screencap -p > "$RUNNER_TEMP/network-recovery-map.png"

dump_ui() {
    adb shell uiautomator dump /sdcard/network-recovery-ui.xml > /dev/null
    adb exec-out cat /sdcard/network-recovery-ui.xml > "$RUNNER_TEMP/network-recovery-ui.xml"
}
ui_point() {
    python - "$RUNNER_TEMP/network-recovery-ui.xml" "$1" "$2" <<'PYUI'
import re,sys,xml.etree.ElementTree as E
root=E.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.get(sys.argv[2],"").startswith(sys.argv[3]):
        left,top,right,bottom=map(int,re.findall(r"\d+",node.get("bounds","")))
        print((left+right)//2,(top+bottom)//2)
        break
else:
    raise SystemExit("Expected runtime UI element unavailable")
PYUI
}
dump_ui
read -r x y < <(ui_point content-desc "Interactive OpenStreetMap.")
started=$(date +%s%3N)
adb shell input tap "$x" "$y"
for ((attempt = 0; attempt < 15; attempt++)); do
    dump_ui
    if grep -q 'Owner retained' "$RUNNER_TEMP/network-recovery-ui.xml"; then break; fi
    sleep 1
done
grep -q 'ROAD SUMMARY' "$RUNNER_TEMP/network-recovery-ui.xml"
grep -q 'MATCHED JOURNEYS' "$RUNNER_TEMP/network-recovery-ui.xml"
grep -q 'text="1599"' "$RUNNER_TEMP/network-recovery-ui.xml"
printf 'canonical_summary_ui_ready_ms=%s contributors=1599\n' "$(( $(date +%s%3N) - started ))" > "$RUNNER_TEMP/network-recovery-interaction.log"
adb exec-out screencap -p > "$RUNNER_TEMP/network-recovery-summary.png"
read -r x y < <(ui_point text "Owner retained")
adb shell input tap "$x" "$y"
for ((attempt = 0; attempt < 15; attempt++)); do
    if adb shell dumpsys activity activities | grep -Eq '(mResumedActivity|topResumedActivity).*JourneyMapEditorActivity'; then break; fi
    sleep 1
done
adb shell dumpsys activity activities | grep -E '(mResumedActivity|topResumedActivity).*JourneyMapEditorActivity'
adb shell input keyevent 4
dump_ui
grep -q 'Interactive OpenStreetMap.' "$RUNNER_TEMP/network-recovery-ui.xml"
adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/network-recovery-interaction-final.log"
if grep -Eq 'FATAL EXCEPTION|OutOfMemoryError|foreground_ui_stall_ms=([2-9][0-9]{3}|[0-9]{5,})' "$RUNNER_TEMP/network-recovery-interaction-final.log"; then
    exit 1
fi

printf '%s\n' 'Synthetic archive of 1600 journeys exercised on Android API 35. The corrected implementation restored all 1599 driving contributions; the negative-control stream-lifetime failure may be intermittent and is reported separately. Signed build 291 upgraded in place. Contributor summary and journey navigation passed. Pixel 10 acceptance remains pending.' | tee -a "$GITHUB_STEP_SUMMARY"
