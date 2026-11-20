#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture
fixtures="$RUNNER_TEMP/scale-fixtures"
mkdir -p "$fixtures"
test -f "$fixtures/journey_dense-osm-0000.json"
test -f "$fixtures/journey_scale-ferry-1.json"
tar -cf "$RUNNER_TEMP/scale-fixtures.tar" -C "$fixtures" .
adb install "$RUNNER_TEMP/roadprints313.apk" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p files shared_prefs
adb push "$RUNNER_TEMP/scale-fixtures.tar" /data/local/tmp/scale-fixtures.tar >/dev/null
adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/scale-fixtures.tar'"
adb shell rm /data/local/tmp/scale-fixtures.tar
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
printf '%s' '<map><boolean name="TRAVEL" value="true"/><boolean name="REMAINING" value="false"/></map>' > "$RUNNER_TEMP/scale-settings.xml"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_map_layers_v1.xml'" < "$RUNNER_TEMP/scale-settings.xml"

collect() {
  local label="$1" log="$RUNNER_TEMP/scale-$1.log"
  local diagnostic="$RUNNER_TEMP/scale-$1-ui.xml"
  : > "$diagnostic"
  adb shell am force-stop "$package"
  adb logcat -c
  adb shell am start -W -n "$package/.OnboardingActivity" > "$RUNNER_TEMP/scale-$label-start.log"
  for attempt in $(seq 1 110); do
    adb logcat -d -s RoadprintsTiming AndroidRuntime ActivityManager > "$log"
    adb exec-out run-as "$package" cat shared_prefs/roadprints_performance_diagnostics.xml >> "$diagnostic" 2>/dev/null || true
    if grep -Eq 'map_outcome=(ready|failure)|FATAL EXCEPTION' "$log"; then break; fi
    sleep 2
  done
  adb logcat -d -s RoadprintsTiming AndroidRuntime ActivityManager > "$log"
  adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/scale-$label-meminfo.txt"
  if [[ "$label" != baseline* ]]; then
    grep -q 'map_outcome=ready' "$log"
    ! grep -Eq 'FATAL EXCEPTION|OutOfMemoryError|ANR in com.roadprints.capture' "$log"
    ! grep -q 'foreground_ui_stall_ms=' "$diagnostic"
  fi
}
hash_archive() {
  adb shell "run-as $package sh -c 'sha256sum files/journey_*.json'" | sort
}
snapshot_projection_cache() {
  adb exec-out run-as "$package" cat no_backup/compact-map-overseas-v1.db > "$RUNNER_TEMP/scale-$1-overseas.db"
}
collect baseline-cold
collect baseline
snapshot_projection_cache baseline
hash_archive > "$RUNNER_TEMP/scale-before.sha256"
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/roadprints-recovery.apk" > "$RUNNER_TEMP/scale-install.txt"
hash_archive > "$RUNNER_TEMP/scale-after.sha256"
cmp "$RUNNER_TEMP/scale-before.sha256" "$RUNNER_TEMP/scale-after.sha256"
adb exec-out run-as "$package" cat shared_prefs/roadprints_map_layers_v1.xml | cmp - "$RUNNER_TEMP/scale-settings.xml"
adb shell dumpsys package "$package" > "$RUNNER_TEMP/scale-package.txt"
grep -q 'versionCode=314' "$RUNNER_TEMP/scale-package.txt"
adb shell run-as "$package" rm -f shared_prefs/roadprints_performance_diagnostics.xml
collect candidate
snapshot_projection_cache candidate
hash_archive > "$RUNNER_TEMP/scale-ready.sha256"
cmp "$RUNNER_TEMP/scale-before.sha256" "$RUNNER_TEMP/scale-ready.sha256"

tap_tab() {
  local label="$1" file="$RUNNER_TEMP/scale-nav-$1.xml"
  adb shell uiautomator dump --compressed /sdcard/roadprints-navigation.xml >/dev/null
  adb exec-out cat /sdcard/roadprints-navigation.xml > "$file"
  local point
  point=$(python3 - "$file" "$label" <<'PYCODE'
import re,sys,xml.etree.ElementTree as E
root=E.parse(sys.argv[1]).getroot()
node=next(n for n in root.iter('node') if n.get('content-desc')==sys.argv[2])
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
assert x2>x1 and y2>y1
print((x1+x2)//2,(y1+y2)//2)
PYCODE
  )
  adb shell input tap $point
}

# Actual in-process tab navigation, not force-stop/relaunch masquerading as retention.
tap_tab Journeys
adb shell am send-trim-memory "$package" RUNNING_LOW
adb logcat -c
tap_tab Map
for attempt in $(seq 1 25); do
  adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/scale-tab.log"
  if grep -Eq 'source=(retained_view|process_snapshot)|map_outcome=failure|FATAL EXCEPTION' "$RUNNER_TEMP/scale-tab.log"; then break; fi
  sleep 1
done
grep -Eq 'source=(retained_view|process_snapshot)' "$RUNNER_TEMP/scale-tab.log"
! grep -Eq 'map_request_queued|source=archive|FATAL EXCEPTION|OutOfMemoryError' "$RUNNER_TEMP/scale-tab.log"
collect warm
snapshot_projection_cache warm
hash_archive > "$RUNNER_TEMP/scale-warm.sha256"
cmp "$RUNNER_TEMP/scale-before.sha256" "$RUNNER_TEMP/scale-warm.sha256"

# The update above retains the installed data and caches. Reset only this
# disposable emulator after taking that evidence for an independent cold run.
adb shell am force-stop "$package"
adb shell pm clear "$package" | grep -q Success
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p files shared_prefs
adb push "$RUNNER_TEMP/scale-fixtures.tar" /data/local/tmp/scale-fixtures.tar >/dev/null
adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/scale-fixtures.tar'"
adb shell rm /data/local/tmp/scale-fixtures.tar
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_map_layers_v1.xml'" < "$RUNNER_TEMP/scale-settings.xml"
collect candidate-cold

python3 - "$RUNNER_TEMP" <<'PY'
import json
import pathlib
import re
import sqlite3
import sys

root = pathlib.Path(sys.argv[1])

def read(label):
    log = (root / f"scale-{label}.log").read_text()
    assert "map_outcome=ready" in log, label
    assert not re.search(r"FATAL EXCEPTION|OutOfMemoryError|ANR in com.roadprints.capture", log), label
    def event(name):
        return [line.split("RoadprintsTiming:", 1)[1].strip() for line in log.splitlines()
                if "RoadprintsTiming:" in line
                and line.split("RoadprintsTiming:", 1)[1].lstrip().startswith(name + " ")]
    def values(name):
        sequence = event(name)
        assert sequence, (label, name)
        return {key: int(value) for key, value in re.findall(r"(\w+)=(\d+)", sequence[-1])}

    local = values("map_local_roads")
    layers = values("map_network_layers")
    return {
        "readiness_ms": values("map_total")["ms"],
        "peak_heap_mib": max(map(int, re.findall(r"sampled_heap_peak_mib=(\d+)", log))),
        "heap_limit_mib": values("map_navigation")["heap_limit_mib"],
        "gc_checkpoints": event("map_gc_checkpoint"),
        "canonical": {**{key: local[key] for key in ("sections", "lines", "unique_mm")},
                      **{key: layers[key] for key in ("covered_motorways", "uncovered_motorways",
                                                    "covered_a_roads", "uncovered_a_roads")}},
        "display": layers,
        "international": values("map_international"),
        "international_retry": values("map_international_retry") if event("map_international_retry") else None,
    }

def cache(label):
    with sqlite3.connect(root / f"scale-{label}-overseas.db") as conn:
        rows = conn.execute("select source, token, data from rows where kind='map-overseas-v1'")
        return {source: (token, json.loads(data)) for source, token, data in rows}

before, after, reopened = map(cache, ("baseline", "candidate", "warm"))
target = "journey_scale-paris-0.json"
base, candidate, warm, base_cold, candidate_cold = map(
    read, ("baseline", "candidate", "warm", "baseline-cold", "candidate-cold")
)
summary = {
    "fixture_records": 1681, "fixture_type": "synthetic",
    "fixture_control": "351 accepted overseas points appended as a separate source line in an existing fixture journey",
    "baseline_cold": base_cold, "baseline_warm": base, "upgrade": candidate,
    "candidate_reopen": warm, "candidate_cold": candidate_cold,
    "retained_navigation": (root / "scale-tab.log").read_text().splitlines()[-15:],
}
(root / "scale-summary.json").write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))

before_token, before_row = before[target]
after_token, after_row = after[target]
assert before_token == after_token
assert before_row["truncated"] and "projectionVersion" not in before_row
assert after_row["truncated"] and after_row["projectionVersion"] == 2
assert after[target] == reopened[target]
assert len(after_row["rows"]) == len(before_row["rows"]) + 1
recovered = after_row["rows"][-1]
assert recovered["original"] == 348 and len(recovered["points"]) >= 2, recovered
assert abs(recovered["points"][0][0] - 10.0) < 1e-7
assert abs(recovered["points"][-1][0] - 10.347) < 1e-7
for journey, cached in before.items():
    if not cached[1]["truncated"]:
        assert after[journey] == cached, journey
assert base["canonical"] == candidate["canonical"] == warm["canonical"]
assert base_cold["canonical"] == candidate_cold["canonical"]
assert candidate["international"]["rendered_lines"] > base["international"]["rendered_lines"]
assert candidate["display"]["international_lines"] > base["display"]["international_lines"]
assert warm["display"]["international_lines"] == candidate["display"]["international_lines"]
assert candidate["international_retry"]["held"] == candidate["international"]["held"]
for old, new in ((base, candidate), (base_cold, candidate_cold)):
    assert new["peak_heap_mib"] < min(240, new["heap_limit_mib"] - 16), new
    assert new["peak_heap_mib"] <= old["peak_heap_mib"] + 8, (old, new)
    assert new["readiness_ms"] <= old["readiness_ms"] + max(2000, old["readiness_ms"] * .20), (old, new)
    def gc_total(record, field):
        return max(int(match) for line in record["gc_checkpoints"]
                   for match in re.findall(rf"\b{field}=(\d+)", line))
    for field, noise in (("gc_runs", 3), ("gc_time_ms", 500)):
        old_total, new_total = gc_total(old, field), gc_total(new, field)
        assert new_total <= old_total + max(noise, old_total * .25), (field, old_total, new_total)

PY
