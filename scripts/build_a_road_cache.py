#!/usr/bin/env python3
"""Build compact, per-road canonical A-road references for Roadprints."""
import json, re, time
from datetime import datetime, timezone
from pathlib import Path

from build_motorway_cache import BATCH_SIZE, dedupe_points, request_overpass, sample_line

OUT = Path("canonical-a-roads-v2")
INDEX = OUT / "index.json"
REGIONS = {
    "GB": 'area["ISO3166-1"="GB"][admin_level=2]->.region;',
    "NI": 'area["name"="Northern Ireland"][boundary="administrative"][admin_level="4"]->.region;',
}

def a_refs(value):
    return {
        cleaned for part in re.split(r"[;,/]", (value or "").upper())
        for cleaned in [re.sub(r"\s+", "", part)]
        if re.fullmatch(r"A\d+[A-Z]?", cleaned)
    }

def road_key(region, ref):
    return f"{region}:{ref}"

def route_query(region, ref_pattern='.*', output='out tags;'):
    return (
        f'[out:json][timeout:180];{REGIONS[region]}'
        'rel(area.region)["type"="route"]["route"="road"]'
        f'["ref"~"{ref_pattern}"];{output}'
    )

def discover_refs(region):
    # Route relations describe the designated road, unlike every OSM way that
    # happens to carry the same ref (carriageways, links and former alignments).
    data=request_overpass(route_query(region), 210)
    return sorted({ref for element in data.get("elements", [])
                   for ref in a_refs((element.get("tags") or {}).get("ref"))},
                  key=lambda ref: (int(re.search(r"\d+", ref).group()), ref))

def load_index():
    if INDEX.exists(): return json.loads(INDEX.read_text())
    return {"version":"v2","roads":{},"failures":{}}

def save_index(index):
    index["generated_at"] = datetime.now(timezone.utc).isoformat()
    OUT.mkdir(exist_ok=True)
    INDEX.write_text(json.dumps(index, separators=(",", ":")))

def fetch_ref(region, ref):
    pattern = rf"(^|[;,/]){ref}($|[;,/])"
    query=route_query(region, pattern, 'way(r);out geom;')
    ways=[]
    for element in request_overpass(query, 180).get("elements", []):
        coords=[[float(p["lon"]),float(p["lat"])] for p in element.get("geometry") or [] if "lon" in p and "lat" in p]
        if len(coords)>=2: ways.append(coords)
    anchors=dedupe_points([point for way in ways for point in sample_line(way)])
    if len(anchors)<3: raise RuntimeError("No usable geometry found")
    return {"version":"v2","id":road_key(region,ref),"region":region,"ref":ref,"anchors":anchors,
            "total_km":round(len(anchors)*0.1,3),"source_way_count":len(ways)}

def main():
    index=load_index()
    refs=[(region,ref) for region in REGIONS for ref in discover_refs(region)]
    pending=[(region,ref) for region,ref in refs if road_key(region,ref) not in index["roads"]]
    print(f"A roads: {len(refs)} known; {len(index['roads'])} cached; {len(pending)} pending")
    for region,ref in pending[:BATCH_SIZE]:
        key=road_key(region,ref)
        try:
            data=fetch_ref(region,ref); filename=f"{region}-{ref}.json"; OUT.mkdir(exist_ok=True); (OUT/filename).write_text(json.dumps(data,separators=(",",":")))
            index["roads"][key]={"file":filename,"total_km":data["total_km"]}; index["failures"].pop(key,None)
            print(f"{key}: {len(data['anchors'])} anchors")
        except Exception as exc:
            index["failures"][key]=str(exc); print(f"{key}: failed: {exc}")
        save_index(index); time.sleep(2)

if __name__ == "__main__": main()
