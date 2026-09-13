#!/usr/bin/env python3
"""Build a cached named-local-road inventory inside an ONS built-up area."""
import json, sys, urllib.parse, urllib.request, time
from pathlib import Path
from shapely.geometry import shape, box, LineString

ONS="https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0/query"
OVERPASS=["https://overpass-api.de/api/interpreter","https://overpass.kumi.systems/api/interpreter","https://overpass.private.coffee/api/interpreter"]
OUT=Path("settlement-inventories-v1")

def get_json(url, params):
    with urllib.request.urlopen(url+"?"+urllib.parse.urlencode(params),timeout=90) as r:
        return json.load(r)

def build(name):
    ons=get_json(ONS,{"where":f"BUA22NM='{name.replace("'","''")}'","outFields":"BUA22CD,BUA22NM","returnGeometry":"true","f":"geojson"})
    features=ons.get("features",[])
    if len(features)!=1: raise SystemExit(f"Expected one ONS built-up area for {name!r}; found {len(features)}")
    feature=features[0]; geometry=feature["geometry"]
    boundary=shape(geometry); west,south,east,north=boundary.bounds; roads=set()
    step=.025
    for lng in [west+i*step for i in range(int((east-west)/step)+1)]:
      for lat in [south+i*step for i in range(int((north-south)/step)+1)]:
        tile=box(lng,lat,min(lng+step,east),min(lat+step,north))
        if not boundary.intersects(tile): continue
        query='[out:json][timeout:60];way["highway"~"^(residential|unclassified|tertiary|living_street)$"]["name"]('+str(lat)+','+str(lng)+','+str(min(lat+step,north))+','+str(min(lng+step,east))+');out tags geom;'
        data=urllib.parse.urlencode({"data":query}).encode()
        osm=None
        for endpoint in OVERPASS:
          try:
            request=urllib.request.Request(endpoint,data=data,method="POST",headers={"User-Agent":"Roadprints settlement inventory builder/1.0"})
            with urllib.request.urlopen(request,timeout=120) as r: osm=json.load(r)
            break
          except Exception: time.sleep(1)
        if osm is None: raise RuntimeError("No Overpass mirror returned this settlement tile")
        for e in osm.get("elements",[]):
          coords=[(p["lon"],p["lat"]) for p in e.get("geometry",[])]
          name=e.get("tags",{}).get("name","").strip()
          if name and len(coords)>1 and boundary.intersects(LineString(coords)): roads.add(name)
    roads=sorted(roads)
    result={"version":1,"source":"ONS Built Up Areas (December 2022) Boundaries GB","code":feature["properties"]["BUA22CD"],"name":feature["properties"]["BUA22NM"],"count":len(roads),"roads":roads}
    OUT.mkdir(exist_ok=True); (OUT/(result["code"]+".json")).write_text(json.dumps(result,separators=(",",":")))
    print(json.dumps(result,indent=2))

if __name__=="__main__": build(" ".join(sys.argv[1:]) or "Gravesend")
