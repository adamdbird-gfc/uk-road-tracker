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

QUERY="""[out:json][timeout:180];
area["ISO3166-1"="GB"][boundary=administrative]->.uk;
nwr["highway"="services"](area.uk);
out center tags;"""

def coordinate(element):
    if element.get("type")=="node":
        return element.get("lat"),element.get("lon")
    center=element.get("center") or {}
    return center.get("lat"),center.get("lon")

def main():
    request=Request(
        OVERPASS_URL,
        data=urlencode({"data":QUERY}).encode("utf-8"),
        headers={"Content-Type":"application/x-www-form-urlencoded","User-Agent":"Roadprints service collection builder"},
    )
    with urlopen(request,timeout=240) as response:
        payload=json.load(response)

    services=[]
    for element in payload.get("elements",[]):
        lat,lng=coordinate(element)
        if not isinstance(lat,(int,float)) or not isinstance(lng,(int,float)): continue
        tags=element.get("tags") or {}
        name=str(tags.get("name") or tags.get("operator") or "").strip()
        if not name: continue
        services.append({
            "id":f"osm:{element.get('type')}:{element.get('id')}",
            "name":name,
            "lat":round(lat,6),
            "lng":round(lng,6),
            "operator":str(tags.get("operator") or "").strip() or None,
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
