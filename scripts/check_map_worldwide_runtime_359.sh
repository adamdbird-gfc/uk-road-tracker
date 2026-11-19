#!/usr/bin/env bash
# No-release A2-vs-worldwide Android 35 synthetic cold/warm Map load.
set -euo pipefail
package=com.roadprints.capture
baseline="$RUNNER_TEMP/roadprints-baseline.apk"
candidate="$RUNNER_TEMP/roadprints-worldwide-runtime.apk"
fixtures="$RUNNER_TEMP/worldwide-runtime-fixtures"
mkdir -p "$fixtures"
python3 scripts/generate_dense_map_fixture_a2.py \
  --reference "$RUNNER_TEMP/uk-road-reference-v3.bin" \
  --output "$fixtures" --accepted 750 --train 925
python3 - "$fixtures" <<'PY'
import json,pathlib,sys
root=pathlib.Path(sys.argv[1])
# Matched, named overseas roads: small steps, no speculative international links.
locations=[
  ("paris",2.337,48.858,0.0007,0.0003, "Rue de Rivoli"),
  ("ibiza",1.432,38.918,0.0007,0.0002, "Carrer de Jaume I"),
  ("calais",1.844,50.951,0.0006,0.0002, "Boulevard Jacquard"),
]
for group,lon,lat,dx,dy,name in locations:
 for i in range(12):
  a=[round(lon+dx*i,7),round(lat+dy*i,7)]
  b=[round(a[0]+dx,7),round(a[1]+dy,7)]
  route=[a,b] if i%2==0 else [b,a]
  feat={"type":"Feature","properties":{"name":name,"distance_m":70.0},
        "geometry":{"type":"LineString","coordinates":route}}
  row={"journey_id":f"worldwide-{group}-{i:03d}","mode":"walking" if i%3==0 else "driving",
       "title":"Synthetic overseas road","distance_meters":70.0,
       "started_at":"2026-11-15T11:00:00Z","ended_at":"2026-11-15T11:04:00Z",
       "processing_status":"complete","source":{"type":"android_activity_capture"},
       "route_geometry":feat["geometry"],
       "processing_result":{"geojson":{"type":"FeatureCollection","features":[feat]},
                            "road_geojson":{"type":"FeatureCollection","features":[feat]}}}
  (root/f"journey_{row['journey_id']}.json").write_text(json.dumps(row))
  if group=="paris":
   # Different saved matched vertices for the SAME accepted physical corridor.
   # ~0.7m parallel drift and opposite direction on alternate journeys.
   moved=[[round(p[0],7),round(p[1]+0.000006,7)] for p in route]
   if i%2==0: moved=list(reversed(moved))
   variant=json.loads(json.dumps(row))
   variant["journey_id"]=f"worldwide-{group}-shifted-{i:03d}"
   variant["mode"]="running" if i%3==0 else "cycling"
   variant["route_geometry"]["coordinates"]=moved
   for key in ("geojson","road_geojson"):
    variant["processing_result"][key]["features"][0]["geometry"]["coordinates"]=moved
   (root/f"journey_{variant['journey_id']}.json").write_text(json.dumps(variant))
# Partial Paris journey fragments: same physical segment but one covers
# 20%-60%, another 40%-80%. The shared street is five atomic intervals
# with contributor sets that vary by section; no duplicate overpaint.
start=[2.337,48.858]
finish=[round(start[0]+0.0007,7),round(start[1]+0.0003,7)]
for label,start_fraction,end_fraction,mode in [
    ("lunch-walk",0.2,0.6,"walking"),("evening-run",0.4,0.8,"running")]:
 route=[[round(start[k]+(finish[k]-start[k])*fraction,7) for k in range(2)]
        for fraction in (start_fraction,end_fraction)]
 feat={"type":"Feature","properties":{"name":"Rue de Rivoli",
     "distance_m":24},"geometry":{"type":"LineString","coordinates":route}}
 row={"journey_id":"worldwide-paris-partial-"+label,"mode":mode,
 "title":"Synthetic accepted partial street","distance_meters":24.0,
 "started_at":"2026-11-16T11:00:00Z","ended_at":"2026-11-16T11:04:00Z",
 "processing_status":"complete","source":{"type":"android_activity_capture"},
 "route_geometry":feat["geometry"],
 "processing_result":{"geojson":{"type":"FeatureCollection","features":[feat]},
                      "road_geojson":{"type":"FeatureCollection","features":[feat]}}}
 (root/f"journey_{row['journey_id']}.json").write_text(json.dumps(row))
# Explicit ferry display, but no road/NCN credit.
ferry={"journey_id":"worldwide-dover-calais-ferry","mode":"ferry",
 "title":"Synthetic ferry (not owner's crossing)","distance_meters":42000,
 "started_at":"2026-11-15T14:00:00Z","ended_at":"2026-11-15T15:30:00Z",
 "processing_status":"complete","route_geometry":{"type":"LineString",
 "coordinates":[[1.312,51.127],[1.5,51.05],[1.85,50.95]]}}
(root/"journey_worldwide-dover-calais-ferry.json").write_text(json.dumps(ferry))
print("Added 36 corridors + 12 shifted variants + 2 partially-overlapping Paris journeys + ferry")
PY
tar -cf "$RUNNER_TEMP/worldwide-fixtures.tar" -C "$fixtures" .
seed_archive() {
 adb shell run-as "$package" mkdir -p files shared_prefs
 adb push "$RUNNER_TEMP/worldwide-fixtures.tar" /data/local/tmp/worldwide-fixtures.tar >/dev/null
 adb shell "run-as $package sh -c 'cd files && tar -xf /data/local/tmp/worldwide-fixtures.tar'"
 adb shell rm -f /data/local/tmp/worldwide-fixtures.tar
 adb shell "run-as $package sh -c 'cat > shared_prefs/roadprints_onboarding.xml'" < "$fixtures/roadprints_onboarding.xml"
}
launch_collect() {
 local label="$1" target="$RUNNER_TEMP/worldwide-$1.log"
 adb shell am force-stop "$package" || true
 adb logcat -c
 adb shell am start -W -n "$package/.OnboardingActivity" > "$RUNNER_TEMP/worldwide-$label-am-start.log"
 for attempt in $(seq 1 90); do
   adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
   if grep -q 'map_outcome=ready\|map_outcome=failure\|FATAL EXCEPTION' "$target"; then break; fi
   sleep 2
 done
 adb logcat -d -s RoadprintsTiming AndroidRuntime > "$target"
 adb shell dumpsys meminfo "$package" > "$RUNNER_TEMP/worldwide-$label-meminfo.txt" || true
 grep -q 'map_outcome=ready' "$target" || {
   echo "::error::$label Map not ready"; tail -n 70 "$target"; return 1;
 }
 ! grep -qE 'FATAL EXCEPTION|OutOfMemoryError' "$target"
}
adb install "$baseline" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
seed_archive
launch_collect baseline-cold
# Equal cold archive/install state. Candidate must then reuse its own cached rows.
adb uninstall "$package" >/dev/null
adb install "$candidate" >/dev/null
adb shell am start -W -n "$package/.OnboardingActivity" >/dev/null
adb shell am force-stop "$package"
seed_archive
for f in "$fixtures"/journey_dense-osm-0000.json "$fixtures"/journey_worldwide-paris-000.json "$fixtures"/journey_worldwide-dover-calais-ferry.json; do
 adb exec-out run-as "$package" cat "files/${f##*/}" | cmp - "$f"
done
launch_collect worldwide-cold
launch_collect worldwide-warm
python3 - "$RUNNER_TEMP/worldwide-baseline-cold.log" "$RUNNER_TEMP/worldwide-worldwide-cold.log" "$RUNNER_TEMP/worldwide-worldwide-warm.log" "$RUNNER_TEMP/worldwide-runtime-summary.json" <<'PY'
import json,pathlib,re,sys
def parse(p):
 lines=pathlib.Path(p).read_text().splitlines()
 names=("map_total","map_navigation","map_local_roads","map_international","map_ferry_overlay",
        "map_records","map_cache","map_heap_stage","map_gc","map_partial_corridors","map_outcome=ready")
 out={}
 for name in names:
  rows=[s for s in lines if name in s]
  out[name]={"events":len(rows),"last":rows[-1][-450:] if rows else ""}
  if rows:
   m=re.search(r"\bms=(\d+)",rows[-1])
   if m:out[name]["last_ms"]=int(m.group(1))
 return out
base,cold,warm=map(parse,sys.argv[1:4])
assert base["map_outcome=ready"]["events"] and cold["map_outcome=ready"]["events"] and warm["map_outcome=ready"]["events"]
assert cold["map_international"]["events"] > 0,"Worldwide instrumentation absent"
assert cold["map_ferry_overlay"]["events"] > 0,"Ferry overlay instrumentation absent"
m=re.search(r"rendered_lines=(\d+) rendered_points=(\d+)",cold["map_international"]["last"])
assert m and int(m.group(1))==38 and int(m.group(2))>=76, (
 "Original 36 world corridors and two independent partial accepted lines were not retained: "
 + (m.group(0) if m else "no match"))
partial=cold.get("map_partial_corridors",{})
assert partial.get("events",0)>0, "Partial interval instrumentation absent"
part=partial["last"]
match=re.search(r"status=(\w+) merged_inputs=(\d+) atomic_lines=(\d+) "
                r"ambiguity_holds=(\d+) displayed_lines=(\d+)",part)
assert match and match.group(1)=="applied" and int(match.group(2))==2, (
 "Two overlapping partial Paris journeys were not consolidated: "+part)
assert int(match.group(3))==5 and int(match.group(5))==40, (
 "Full street plus two partial walks must become five contributor-specific"
 " intervals, leaving 40 final worldwide display lines: "+part)
m=re.search(r"lines=(\d+) points=(\d+)",cold["map_ferry_overlay"]["last"])
assert m and int(m.group(1))>0 and int(m.group(2))>0, "No synthetic ferry overlay"
result={"fixture_journeys":1726,"international_accepted":50,"unique_international_corridors":36,"partial_journeys":2,"atomic_paris_segments":5,"ferry":1,"railway_excluded":925,
 "baseline":base,"candidate_cold":cold,"candidate_warm":warm,
 "note":"API35 deterministic synthetic comparison, not Pixel10 Android17 owner-archive proof. Cold and warm are process relaunches, not tab switches."}
pathlib.Path(sys.argv[4]).write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
PY
echo "Worldwide Map cold/warm and A2 baseline emulator measurements: see uploaded JSON, logcat and meminfo. No APK released." >> "$GITHUB_STEP_SUMMARY"
