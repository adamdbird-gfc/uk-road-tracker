#!/usr/bin/env python3
"""Build a static UK motorway-service collection from OpenStreetMap.

The app consumes this small checked-in cache; it never needs to call a third
party while somebody is using the map. A Timeline visit can then be matched
against a known service-area point as confirmed collection evidence.
"""
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

OUT=Path("collections/uk-motorway-services-v1.json")
OVERPASS_URL="https://overpass-api.de/api/interpreter"
MAJOR_OPERATORS=("moto","roadchef","welcome break","extra","westmorland","applegreen","eg on the move")

QUERY="""[out:json][timeout:180];
area["ISO3166-1"="GB"][boundary=administrative]->.uk;
nwr["highway"="services"](area.uk);
out center tags;"""

def coordinate(element):
    if element.get("type")=="node":
        return element.get("lat"),element.get("lon")
    center=element.get("center") or {}
    return center.get("lat"),center.get("lon")

def mercator(lng,lat):
    import math
    radius=6378137
    return radius*math.radians(lng), radius*math.log(math.tan(math.pi/4+math.radians(lat)/2))

def motorway_proximity_index():
    query='''[out:json][timeout:180];
area["ISO3166-1"="GB"][boundary=administrative]->.uk;
way["highway"="motorway"](area.uk);
out geom;'''
    request=Request(OVERPASS_URL,data=urlencode({"data":query}).encode("utf-8"),
      headers={"Content-Type":"application/x-www-form-urlencoded","User-Agent":"Roadprints service collection builder"})
    with urlopen(request,timeout=240) as response: payload=json.load(response)
    cell_size=1200
    index={}
    for way in payload.get("elements",[]):
        for point in way.get("geometry") or []:
            if not isinstance(point.get("lat"),(int,float)) or not isinstance(point.get("lon"),(int,float)): continue
            x,y=mercator(point["lon"],point["lat"])
            cell=(round(x//cell_size),round(y//cell_size))
            index.setdefault(cell,[]).append((x,y))
    return index,cell_size

def is_near_motorway(lat,lng,index,cell_size):
    x,y=mercator(lng,lat); cell=(round(x//cell_size),round(y//cell_size))
    for dx in (-1,0,1):
        for dy in (-1,0,1):
            for mx,my in index.get((cell[0]+dx,cell[1]+dy),()):
                if (x-mx)**2+(y-my)**2 <= 950**2: return True
    return False

def main():
    request=Request(
        OVERPASS_URL,
        data=urlencode({"data":QUERY}).encode("utf-8"),
        headers={"Content-Type":"application/x-www-form-urlencoded","User-Agent":"Roadprints service collection builder"},
    )
    with urlopen(request,timeout=240) as response:
        payload=json.load(response)

    motorway_index,cell_size=motorway_proximity_index()
    services=[]
    for element in payload.get("elements",[]):
        lat,lng=coordinate(element)
        if not isinstance(lat,(int,float)) or not isinstance(lng,(int,float)): continue
        tags=element.get("tags") or {}
        name=str(tags.get("name") or tags.get("operator") or "").strip()
        operator=str(tags.get("operator") or "").strip()
        searchable=f"{name} {operator}".casefold()
        # Do not treat every roadside fuel stop as a motorway service area.
        # Keep named services and established motorway-service operators only.
        if not name or ("service" not in searchable and not any(value in searchable for value in MAJOR_OPERATORS)):
            continue
        if not is_near_motorway(lat,lng,motorway_index,cell_size):
            continue
        services.append({
            "id":f"osm:{element.get('type')}:{element.get('id')}",
            "name":name,
            "lat":round(lat,6),
            "lng":round(lng,6),
            "operator":operator or None,
            "road":str(tags.get("ref") or tags.get("motorway") or "").strip() or None,
        })
    services.sort(key=lambda item:(item["name"].casefold(),item["id"]))
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps({
        "version":"v1",
        "source":"OpenStreetMap highway=services",
        "generated_at":datetime.now(timezone.utc).isoformat(),
        "services":services,
    },separators=(",",":")))
    print(f"Wrote {len(services)} UK motorway service locations.")

if __name__=="__main__":
    main()
