#!/usr/bin/env python3
import json, math, re, time, urllib.parse, urllib.request
from pathlib import Path
from datetime import datetime, timezone

OUT = Path("canonical-motorways-v1.json")
OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

SAMPLE_M = 100.0
DEDUPE_RADIUS_M = 95.0
DEDUPE_CELL_M = 110.0

DEFAULT_MOTORWAY_REFS = [
    "M1","M2","M3","M4","M5","M6","M8","M9","M11","M18","M20","M23","M25",
    "M26","M27","M32","M40","M42","M45","M48","M49","M50","M53","M54","M55",
    "M56","M57","M58","M60","M61","M62","M65","M66","M67","M69","M73","M74",
    "M77","M80","M90","M180","M181","M271","M275","M602","M606","M621",
    "M876","M898",
    "A1(M)","A3(M)","A48(M)","A57(M)","A58(M)","A64(M)","A66(M)","A74(M)",
    "A194(M)","A308(M)","A329(M)","A404(M)","A601(M)","A627(M)"
]

def request_overpass(query, timeout=120):
    data = urllib.parse.urlencode({"data": query}).encode()
    last = None
    for endpoint in OVERPASS:
        for attempt in range(3):
            try:
                req = urllib.request.Request(
                    endpoint,
                    data=data,
                    headers={"User-Agent": "uk-road-tracker-poc-cache-builder/1.0"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=timeout) as r:
                    return json.loads(r.read().decode("utf-8"))
            except Exception as e:
                last = e
                time.sleep(3 + attempt * 3)
    raise RuntimeError(f"Overpass failed: {last}")

def haversine(a, b):
    r = 6371000.0
    lon1, lat1 = map(math.radians, a)
    lon2, lat2 = map(math.radians, b)
    dlat = lat2-lat1
    dlon = lon2-lon1
    h = math.sin(dlat/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
    return 2*r*math.asin(math.sqrt(h))

def mercator(lon, lat):
    r = 6378137.0
    lat = max(-85.0, min(85.0, lat))
    x = r*math.radians(lon)
    y = r*math.log(math.tan(math.pi/4 + math.radians(lat)/2))
    return x, y

def interp(a,b,t):
    return [a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t]

def sample_line(coords):
    pts=[]
    for i in range(1,len(coords)):
        a,b=coords[i-1],coords[i]
        length=haversine(a,b)
        if length <= 0:
            continue
        n=max(1, math.ceil(length/SAMPLE_M))
        for s in range(n):
            pts.append(interp(a,b,s/n))
    if coords:
        pts.append(coords[-1])
    return pts

def neighbour_keys(x,y):
    gx,gy=math.floor(x/DEDUPE_CELL_M), math.floor(y/DEDUPE_CELL_M)
    for dx in (-1,0,1):
        for dy in (-1,0,1):
            yield f"{gx+dx},{gy+dy}"

def dedupe_points(points):
    anchors=[]
    index={}
    for lon,lat in points:
        x,y=mercator(lon,lat)
        duplicate=False
        for key in neighbour_keys(x,y):
            for idx in index.get(key,()):
                a=anchors[idx]
                if math.hypot(x-a["x"], y-a["y"]) <= DEDUPE_RADIUS_M:
                    duplicate=True
                    break
            if duplicate:
                break
        if duplicate:
            continue
        idx=len(anchors)
        anchors.append({"lon":lon,"lat":lat,"x":x,"y":y})
        key=f"{math.floor(x/DEDUPE_CELL_M)},{math.floor(y/DEDUPE_CELL_M)}"
        index.setdefault(key,[]).append(idx)
    return [[round(a["lon"],7), round(a["lat"],7)] for a in anchors]

def discover_refs():
    """
    Try to discover motorway refs from Overpass, but never let discovery
    failure abort the cache build.  The public Overpass API occasionally
    returns HTTP 500/504 on broad country-wide queries, so we keep a curated
    fallback list and merge live discoveries into it when available.
    """
    refs = set(DEFAULT_MOTORWAY_REFS)

    q = '[out:json][timeout:90];area["ISO3166-1"="GB"][admin_level=2]->.gb;way(area.gb)["highway"="motorway"]["ref"];out tags;'

    try:
        data = request_overpass(q)
        for el in data.get("elements", []):
            ref = (el.get("tags") or {}).get("ref", "").strip().upper().replace(" ", "")
            if re.fullmatch(r"(?:M\d+[A-Z]?|A\d+\(M\))", ref):
                refs.add(ref)
    except Exception as e:
        print(f"Motorway discovery query failed; using fallback list: {e}")

    def sort_key(s):
        nums = re.findall(r"\d+", s)
        n = int(nums[0]) if nums else 99999
        return (s.startswith("A"), n, s)

    return sorted(refs, key=sort_key)

def fetch_ref(ref):
    q = f'[out:json][timeout:120];area["ISO3166-1"="GB"][admin_level=2]->.gb;way(area.gb)["highway"="motorway"]["ref"="{ref}"];out body geom;'
    data=request_overpass(q)
    ways=[]
    for el in data.get("elements",[]):
        geom=el.get("geometry") or []
        coords=[[float(p["lon"]),float(p["lat"])] for p in geom if "lon" in p and "lat" in p]
        if len(coords)>=2:
            ways.append(coords)
    if not ways:
        raise RuntimeError(f"No geometry found for {ref}")
    sampled=[]
    for coords in ways:
        sampled.extend(sample_line(coords))
    anchors=dedupe_points(sampled)
    return {
        "anchors": anchors,
        "total_km": round(len(anchors)*SAMPLE_M/1000, 3),
        "source_way_count": len(ways),
    }

def main():
    refs=discover_refs()
    print(f"Discovered {len(refs)} motorway refs")
    roads={}
    failures={}
    for i,ref in enumerate(refs,1):
        print(f"[{i}/{len(refs)}] {ref}")
        try:
            roads[ref]=fetch_ref(ref)
        except Exception as e:
            failures[ref]=str(e)
            print("  FAILED:",e)
        time.sleep(2.0)

    payload={
        "version":"v1",
        "generated_at":datetime.now(timezone.utc).isoformat(),
        "sample_spacing_m":SAMPLE_M,
        "dedupe_radius_m":DEDUPE_RADIUS_M,
        "roads":roads,
        "failures":failures,
    }
    OUT.write_text(json.dumps(payload,separators=(",",":")), encoding="utf-8")
    print(f"Wrote {OUT} with {len(roads)} roads; {len(failures)} failures")

if __name__=="__main__":
    main()
