#!/usr/bin/env python3
"""Build compact, per-road canonical A-road references for Roadprints."""
import json, re, time
from datetime import datetime, timezone
from pathlib import Path

from build_motorway_cache import BATCH_SIZE, dedupe_points, request_overpass, sample_line

OUT = Path("canonical-a-roads-v1")
INDEX = OUT / "index.json"

def a_refs(value):
    return {
        cleaned for part in re.split(r"[;,/]", (value or "").upper())
        for cleaned in [re.sub(r"\s+", "", part)]
        if re.fullmatch(r"A\d+[A-Z]?", cleaned)
    }

def discover_refs():
    query = ('[out:json][timeout:180];area["ISO3166-1"="GB"][admin_level=2]->.gb;'
             'way(area.gb)["highway"~"^(motorway|trunk|primary|secondary|tertiary)$"]["ref"];out tags;')
    return sorted({ref for element in request_overpass(query, 210).get("elements", [])
                   for ref in a_refs((element.get("tags") or {}).get("ref"))},
                  key=lambda ref: (int(re.search(r"\d+", ref).group()), ref))

def load_index():
    if INDEX.exists(): return json.loads(INDEX.read_text())
    return {"version":"v1","roads":{},"failures":{}}

def save_index(index):
    index["generated_at"] = datetime.now(timezone.utc).isoformat()
    OUT.mkdir(exist_ok=True)
    INDEX.write_text(json.dumps(index, separators=(",", ":")))

def fetch_ref(ref):
    pattern = rf"(^|[;,/]){ref}($|[;,/])"
    query = ('[out:json][timeout:150];area["ISO3166-1"="GB"][admin_level=2]->.gb;'
             'way(area.gb)["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"]'
             f'["ref"~"{pattern}"];out geom;')
    ways=[]
    for element in request_overpass(query, 180).get("elements", []):
        coords=[[float(p["lon"]),float(p["lat"])] for p in element.get("geometry") or [] if "lon" in p and "lat" in p]
        if len(coords)>=2: ways.append(coords)
    anchors=dedupe_points([point for way in ways for point in sample_line(way)])
    if len(anchors)<3: raise RuntimeError("No usable geometry found")
    return {"version":"v1","ref":ref,"anchors":anchors,"total_km":round(len(anchors)*0.1,3),"source_way_count":len(ways)}

def main():
    index=load_index(); refs=discover_refs(); pending=[ref for ref in refs if ref not in index["roads"]]
    print(f"A roads: {len(refs)} known; {len(index['roads'])} cached; {len(pending)} pending")
    for ref in pending[:BATCH_SIZE]:
        try:
            data=fetch_ref(ref); OUT.mkdir(exist_ok=True); (OUT/f"{ref}.json").write_text(json.dumps(data,separators=(",",":")))
            index["roads"][ref]={"file":f"{ref}.json","total_km":data["total_km"]}; index["failures"].pop(ref,None)
            print(f"{ref}: {len(data['anchors'])} anchors")
        except Exception as exc:
            index["failures"][ref]=str(exc); print(f"{ref}: failed: {exc}")
        save_index(index); time.sleep(2)

if __name__ == "__main__": main()
