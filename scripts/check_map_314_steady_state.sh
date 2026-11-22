#!/usr/bin/env bash
# Same-emulator Build 313 -> diagnostic candidate A/B. No source data mutation,
# no APK publication, no private owner data. Fails closed on missing evidence.
set -euo pipefail
package=com.roadprints.capture
fixtures="$RUNNER_TEMP/scale-fixtures"
test -f "$fixtures/journey_dense-osm-0000.json"
tar -cf "$RUNNER_TEMP/scale-fixtures.tar" -C "$fixtures" .
adb install "$RUNNER_TEMP/roadprints313.apk" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p files shared_prefs
adb push "$RUNNER_TEMP/scale-fixtures.tar" /data/local/tmp/scale-fixtures.tar >/dev/null
adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/scale-fixtures.tar'"
adb shell rm /data/local/tmp/scale-fixtures.tar
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
printf '%s' '<map><boolean name="TRAVEL" value="true"/><boolean name="REMAINING" value="false"/></map>' > "$RUNNER_TEMP/map314-layers.xml"
adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_map_layers_v1.xml'" < "$RUNNER_TEMP/map314-layers.xml"

archive_hash() {
    adb shell "run-as $package sh -c 'sha256sum files/journey_*.json'" | sort
}
record_load() {
    local label="$1" log="$RUNNER_TEMP/map314-$1.log"
    adb shell am force-stop "$package"
    adb logcat -c
    adb shell am start -W -n "$package/.OnboardingActivity" > "$RUNNER_TEMP/map314-$label-start.txt"
    for attempt in $(seq 1 90); do
      adb logcat -d -s RoadprintsTiming AndroidRuntime ActivityManager > "$log"
      if grep -Eq 'map_outcome=(ready|failure)|FATAL EXCEPTION' "$log"; then break; fi
      sleep 2
    done
    adb logcat -d -s RoadprintsTiming AndroidRuntime ActivityManager > "$log"
    adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/map314-$label-meminfo.txt"
    grep -q 'map_outcome=ready' "$log"
    ! grep -Eq 'FATAL EXCEPTION|OutOfMemoryError|ANR in com.roadprints.capture' "$log"
}
record_load baseline-cold
record_load baseline-steady1
record_load baseline-steady2
archive_hash > "$RUNNER_TEMP/map314-archive-before.sha256"
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/roadprints314.apk" > "$RUNNER_TEMP/map314-upgrade.txt"
archive_hash > "$RUNNER_TEMP/map314-archive-after.sha256"
cmp "$RUNNER_TEMP/map314-archive-before.sha256" "$RUNNER_TEMP/map314-archive-after.sha256"
adb exec-out run-as "$package" cat shared_prefs/roadprints_map_layers_v1.xml | cmp - "$RUNNER_TEMP/map314-layers.xml"
adb shell dumpsys package "$package" | grep -q 'versionCode=314'
record_load upgrade-first
record_load candidate-steady1
record_load candidate-steady2
archive_hash > "$RUNNER_TEMP/map314-archive-final.sha256"
cmp "$RUNNER_TEMP/map314-archive-before.sha256" "$RUNNER_TEMP/map314-archive-final.sha256"
python3 - "$RUNNER_TEMP" <<'PY'
import json,pathlib,re,statistics,sys
root=pathlib.Path(sys.argv[1])
names=['baseline-cold','baseline-steady1','baseline-steady2',
       'upgrade-first','candidate-steady1','candidate-steady2']
def parse(name):
    lines=(root/f'map314-{name}.log').read_text().splitlines()
    def last(tag):
        return next((line for line in reversed(lines)
                     if 'RoadprintsTiming:' in line
                     and line.split('RoadprintsTiming:',1)[1].lstrip().startswith(tag+' ')), '')
    def val(line,key):
        m=re.search(r'(?:^|\s)'+re.escape(key)+r'=(\d+)',line)
        return int(m.group(1)) if m else None
    peak=[int(x) for line in lines for x in re.findall(r'sampled_heap_peak_mib=(\d+)',line)]
    gc=last('map_gc_checkpoint')
    return {'ready':any('map_outcome=ready' in l for l in lines),
            'map_ready_ms':val(last('map_total'),'ms'),
            'heap_peak_mib':max(peak) if peak else None,
            'gc_runs':val(gc,'gc_runs'),
            'gc_time_ms':val(gc,'gc_time_ms'),
            'map_navigation':last('map_navigation'),
            'map_international':last('map_international'),
            'map_local_roads':last('map_local_roads')}
data={name:parse(name) for name in names}
for name,entry in data.items():
    assert entry['ready'],f'{name}: map not ready'
    for metric in ['map_ready_ms','heap_peak_mib','gc_runs','gc_time_ms']:
        assert entry[metric] is not None,f'{name}: missing {metric}'
baseline=[data[f'baseline-steady{i}']['map_ready_ms'] for i in (1,2)]
candidate=[data[f'candidate-steady{i}']['map_ready_ms'] for i in (1,2)]
base_mean=statistics.mean(baseline); cand_mean=statistics.mean(candidate)
# Use mean of two force-stop/relaunch steady loads for both versions.
# A strict 20% relative bound, supplemented by independent memory/GC checks.
assert cand_mean<=1.2*base_mean,(
    f'Steady Map performance regression: baseline={baseline}, candidate={candidate}')
first=data['upgrade-first']['map_ready_ms']
assert first<=max(25_000,2*base_mean),(
    f'First-upgrade migration unbounded: {first}ms versus {base_mean:.0f}ms')
# No candidate steady read may exhibit grossly worse memory or collection
# compared with same-emulator baseline steady states.
for metric in ['heap_peak_mib','gc_runs','gc_time_ms']:
    baseline_peak=max(data[f'baseline-steady{i}'][metric] for i in (1,2))
    candidate_peak=max(data[f'candidate-steady{i}'][metric] for i in (1,2))
    allowed=baseline_peak*1.2+ (4 if metric=='heap_peak_mib' else 1)
    assert candidate_peak<=allowed,(
        f'Candidate {metric} regression: baseline={baseline_peak},'
        f' candidate={candidate_peak}, allowed={allowed}')
report={'fixture':'1681 synthetic archived journeys, immutable between versions',
        'environment':'same Android API35 emulator, same heap and layer filters',
        'baseline_version':'313','candidate_version':'314',
        'measurement':'two force-stop/relaunch steady Map loads per version',
        'baseline_steady_mean_ms':base_mean,
        'candidate_steady_mean_ms':cand_mean,
        'steady_ratio':cand_mean/base_mean,
        'migration_first_load_ms':first,
        'warm_migration_bounded':True,
        'archive_hashes_identical':True,
        'runs':data,
        'limits':'Synthetic archive, not owner Pixel10/Android17; UI renderer visual acceptance remains outstanding'}
(root/'map314-performance.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
PY
