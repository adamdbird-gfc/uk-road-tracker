#!/usr/bin/env bash
# Four *fresh-install*, same-fixture Android runs: 310 / A2 / A2 / 310.
# No owner data. No instrumentation-triggered GC. No APK publication.
set -euo pipefail
package=com.roadprints.capture
fixtures="$RUNNER_TEMP/a2-abba-fixtures"
mkdir -p "$fixtures"
python3 scripts/generate_dense_map_fixture_a2.py \
  --reference "$RUNNER_TEMP/uk-road-reference-v3.bin" \
  --output "$fixtures" --accepted 750 --train 925
tar -cf "$RUNNER_TEMP/a2-abba-fixtures.tar" -C "$fixtures" .

seed() {
  adb shell run-as "$package" mkdir -p files shared_prefs
  adb push "$RUNNER_TEMP/a2-abba-fixtures.tar" /data/local/tmp/a2-abba-fixtures.tar >/dev/null
  adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/a2-abba-fixtures.tar'"
  adb shell rm -f /data/local/tmp/a2-abba-fixtures.tar
  adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
  adb exec-out run-as "$package" cat files/journey_dense-osm-0000.json |
     cmp - "$fixtures/journey_dense-osm-0000.json"
  adb exec-out run-as "$package" cat files/journey_dense-osm-0749.json |
     cmp - "$fixtures/journey_dense-osm-0749.json"
}

run_once() {
  local index="$1" kind="$2"
  local apk="$RUNNER_TEMP/roadprints310.apk"
  if [[ "$kind" == "candidate" ]]; then apk="$RUNNER_TEMP/roadprints-a2-runtime.apk"; fi
  local label="$index-$kind" log="$RUNNER_TEMP/a2-abba-$index-$kind.log"
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb install "$apk" >/dev/null
  adb shell am start -W -n "$package/.OnboardingActivity" \
    > "$RUNNER_TEMP/a2-abba-$label-am-start.log"
  adb shell am force-stop "$package"
  seed
  adb logcat -c
  adb shell am start -W -n "$package/.OnboardingActivity" \
    >> "$RUNNER_TEMP/a2-abba-$label-am-start.log"
  for attempt in $(seq 1 100); do
    adb logcat -d -s RoadprintsTiming AndroidRuntime > "$log"
    if grep -Eq 'map_outcome=(ready|failure)|FATAL EXCEPTION' "$log"; then break; fi
    sleep 2
  done
  adb logcat -d -s RoadprintsTiming AndroidRuntime > "$log"
  adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/a2-abba-$label-meminfo.txt"
  grep -q 'map_outcome=ready' "$log" || {
    echo "::error::$label never became ready"; tail -n 80 "$log"; return 1;
  }
  ! grep -qE 'FATAL EXCEPTION|OutOfMemoryError' "$log"
}

run_once 1 baseline
run_once 2 candidate
run_once 3 candidate
run_once 4 baseline

python3 - "$RUNNER_TEMP" <<'PY'
import json,pathlib,re,statistics,sys
directory=pathlib.Path(sys.argv[1])
runs=[]
for number,kind in ((1,"baseline"),(2,"candidate"),(3,"candidate"),(4,"baseline")):
    prefix=directory/f"a2-abba-{number}-{kind}"
    log=(pathlib.Path(str(prefix)+".log")).read_text()
    mem=(pathlib.Path(str(prefix)+"-meminfo.txt")).read_text()
    def last(name):
        found=[line for line in log.splitlines() if "RoadprintsTiming:" in line and
               line.split("RoadprintsTiming:",1)[1].lstrip().startswith(name+" ")]
        return found[-1] if found else ""
    def value(line,name):
        m=re.search(r"(?:^|[ ])"+re.escape(name)+r"=([0-9]+)",line)
        return int(m.group(1)) if m else None
    total=last("map_total")
    nav=last("map_navigation")
    local=last("map_local_roads")
    assert total and nav and local,(number,kind)
    canonical=[value(local,key) for key in ("sections","lines","unique_mm","unresolved_mm")]
    assert None not in canonical,(number,canonical)
    assert canonical==[750,750,24137187,0],(number,kind,canonical)
    progress=[line for line in log.splitlines() if "map_progressive_publish " in line]
    gc=[line for line in log.splitlines() if "map_gc_checkpoint " in line]
    final_gc=next((line for line in reversed(gc) if "phase=after_major_roads " in line), "")
    if kind=="candidate":
        assert len(progress)>=2,(number,"progressive Map not published")
        assert gc and final_gc,(number,"missing GC checkpoints")
    # dumpsys meminfo has TOTAL PSS / TOTAL RSS on the summary line.
    total_line=next((line.strip() for line in mem.splitlines()
                     if re.match(r"^TOTAL\s+\d+\s+\d+\s+\d+",line.strip())), "")
    mem_items=re.findall(r"\d+",total_line)
    assert len(mem_items)>=4,(number,total_line)
    entry={
        "order":number,"build":kind,"map_total_ms":value(total,"ms"),
        "sampled_java_heap_peak_mib":value(nav,"sampled_heap_peak_mib"),
        "local_processing_ms":value(local,"ms"),"canonical":canonical,
        "progressive_publishes":len(progress),
        "total_pss_kib":int(mem_items[0]),
        "total_rss_kib":int(mem_items[-1]),
        "gc_runs":value(final_gc,"gc_runs") if final_gc else None,
        "gc_freed_kib":value(final_gc,"gc_freed_kib") if final_gc else None,
        "gc_time_ms":value(final_gc,"gc_time_ms") if final_gc else None
    }
    runs.append(entry)
assert [r["build"] for r in runs]==["baseline","candidate","candidate","baseline"]
by_build={k:[r for r in runs if r["build"]==k] for k in ("baseline","candidate")}
summary={"fixture":"750 distinct accepted OSM edge journeys +925 trains",
         "order":"ABBA, each fresh install and same reference/fixture",
         "runs":runs,
         "means":{kind:{metric:round(statistics.mean(r[metric] for r in rows),2)
                         for metric in ("map_total_ms","sampled_java_heap_peak_mib",
                                        "total_pss_kib","total_rss_kib")}
                  for kind,rows in by_build.items()},
         "limitations":"Two measurements per build; emulator API35, not Pixel10 Android17. Baseline lacks ART GC instrumentation; GC numbers are A2-only. Do not infer causal performance or owner-scale memory safety."}
(directory/"a2-abba-summary.json").write_text(json.dumps(summary,indent=2))
print(json.dumps(summary,indent=2))
PY
echo "A2 ABBA: four fresh installations, canonical coverage equality, progress evidence and memory receipts complete." >> "$GITHUB_STEP_SUMMARY"
