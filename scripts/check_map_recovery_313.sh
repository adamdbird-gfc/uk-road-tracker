#!/usr/bin/env bash
set -euo pipefail
package=com.roadprints.capture
fixtures="$RUNNER_TEMP/scale-fixtures"
mkdir -p "$fixtures"
python3 scripts/generate_map_recovery_fixture.py --reference "$RUNNER_TEMP/uk-road-reference-v3.bin" --output "$fixtures" --assets android-prototype/app/src/main/assets
tar -cf "$RUNNER_TEMP/scale-fixtures.tar" -C "$fixtures" .
adb install "$RUNNER_TEMP/roadprints312.apk" >/dev/null
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
collect baseline-cold
collect baseline
hash_archive > "$RUNNER_TEMP/scale-before.sha256"
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/roadprints-recovery.apk" > "$RUNNER_TEMP/scale-install.txt"
hash_archive > "$RUNNER_TEMP/scale-after.sha256"
cmp "$RUNNER_TEMP/scale-before.sha256" "$RUNNER_TEMP/scale-after.sha256"
adb exec-out run-as "$package" cat shared_prefs/roadprints_map_layers_v1.xml | cmp - "$RUNNER_TEMP/scale-settings.xml"
adb shell dumpsys package "$package" > "$RUNNER_TEMP/scale-package.txt"
grep -q 'versionCode=313' "$RUNNER_TEMP/scale-package.txt"
adb shell run-as "$package" rm -f shared_prefs/roadprints_performance_diagnostics.xml
collect candidate
hash_archive > "$RUNNER_TEMP/scale-ready.sha256"
cmp "$RUNNER_TEMP/scale-before.sha256" "$RUNNER_TEMP/scale-ready.sha256"

# Actual in-process tab navigation, not force-stop/relaunch masquerading as retention.
adb shell am start -W -n "$package/.JourneyListActivity" -f 0x20020000 >/dev/null
adb shell am send-trim-memory "$package" RUNNING_LOW
adb logcat -c
adb shell am start -W -n "$package/.MapActivity" -f 0x20020000 > "$RUNNER_TEMP/scale-tab-start.log"
for attempt in $(seq 1 25); do
  adb logcat -d -s RoadprintsTiming AndroidRuntime > "$RUNNER_TEMP/scale-tab.log"
  if grep -Eq 'source=(retained_view|process_snapshot)|map_outcome=failure|FATAL EXCEPTION' "$RUNNER_TEMP/scale-tab.log"; then break; fi
  sleep 1
done
grep -Eq 'source=(retained_view|process_snapshot)' "$RUNNER_TEMP/scale-tab.log"
! grep -Eq 'map_request_queued|source=archive|FATAL EXCEPTION|OutOfMemoryError' "$RUNNER_TEMP/scale-tab.log"
collect warm

python3 - "$RUNNER_TEMP" <<'PY'
import json,pathlib,re,sys
root=pathlib.Path(sys.argv[1])
def read(label):
    log=(root/f'scale-{label}.log').read_text()
    def event(name):
        lines=[s for s in log.splitlines() if 'RoadprintsTiming:' in s and s.split('RoadprintsTiming:',1)[1].lstrip().startswith(name+' ')]
        return lines[-1] if lines else ''
    def number(line,key):
        match=re.search(r'(?:^| )'+re.escape(key)+r'=(\d+)',line)
        return int(match[1]) if match else None
    local=event('map_local_roads'); nav=event('map_navigation'); gc=event('map_gc_checkpoint')
    return {'ready':'map_outcome=ready' in log,'time_ms':number(event('map_total'),'ms'),
            'heap_peak_mib':number(nav,'sampled_heap_peak_mib'),
            'gc_runs':number(gc,'gc_runs'),'gc_ms':number(gc,'gc_time_ms'),
            'sections':number(local,'sections'),'lines':number(local,'lines'),
            'unique_mm':number(local,'unique_mm'),'local':local,
            'international':event('map_international'),'mode':event('map_mode_coverage'),
            'ferry':event('map_ferry_overlay'),'navigation':nav}
base,candidate,warm=[read(x) for x in ('baseline','candidate','warm')]
assert candidate['ready'] and warm['ready']
assert candidate['lines'] >= 44000,candidate
assert candidate['heap_peak_mib'] is not None and candidate['heap_peak_mib'] < 240,candidate
if base['ready']:
    assert [base[k] for k in ('sections','lines','unique_mm')]==[candidate[k] for k in ('sections','lines','unique_mm')],(base,candidate)
    assert candidate['heap_peak_mib'] <= base['heap_peak_mib'],(base,candidate)
assert 'held=0' in candidate['international'],candidate
assert 'lines=2 ' in candidate['ferry'],candidate
assert 'foot_canonical_a_heap_deferred=0' in candidate['mode'],candidate
if 'source=archive' in warm['navigation']:
    match=re.search(r'known_uk_excluded=(\d+)',warm['international'])
    assert match and int(match[1]) >= 1400,warm
result={'fixture':'1681 records; 45260 distinct UK input edges, 793 UK walking/running sources, 10 worldwide sources, 2 ferries',
        'baseline312':base,'candidate313':candidate,'process_relaunch313':warm,
        'in_place_upgrade':'all 1681 archive files byte-identical; map preferences retained',
        'tab_return_after_running_low':'retained without archive work',
        'limitations':'Synthetic geometry-volume test, API35 256MiB heap. Not the actual owner archive or Pixel10 Android17 measurements.'}
(root/'scale-summary.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
PY
