#!/usr/bin/env python3
"""Build a static UK motorway-service collection from OpenStreetMap.

The app consumes this small checked-in cache; it never needs to call a third
party while somebody is using the map. A Timeline visit can then be matched
against a known service-area point as confirmed collection evidence.
"""
import json
import math, re
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
    # Reuse Roadprints' committed motorway anchors rather than issuing a second
    # national Overpass request; this makes the collection build predictable.
    cache=json.loads(Path("canonical-motorways-v1.json").read_text())
    cell_size=1200
    index={}
    for road in (cache.get("roads") or {}).values():
        for point in road.get("anchors") or []:
            if not isinstance(point,list) or len(point)<2: continue
            lng,lat=point[0],point[1]
            if not isinstance(lat,(int,float)) or not isinstance(lng,(int,float)): continue
            x,y=mercator(lng,lat)
            cell=(int(x//cell_size),int(y//cell_size))
            index.setdefault(cell,[]).append((x,y))
    return index,cell_size

def is_near_motorway(lat,lng,index,cell_size):
    x,y=mercator(lng,lat); cell=(round(x//cell_size),round(y//cell_size))
    for dx in (-1,0,1):
        for dy in (-1,0,1):
            for mx,my in index.get((cell[0]+dx,cell[1]+dy),()):
                if (x-mx)**2+(y-my)**2 <= 650**2: return True
    return False

def haversine_m(a,b):
    lat1,lng1,lat2,lng2=map(math.radians,[a[0],a[1],b[0],b[1]])
    dlat,dlng=lat2-lat1,lng2-lng1
    value=math.sin(dlat/2)**2+math.cos(lat1)*math.cos(lat2)*math.sin(dlng/2)**2
    return 2*6371000*math.atan2(math.sqrt(value),math.sqrt(1-value))

def collection_name(value):
    value=re.sub(r"\b(northbound|southbound|eastbound|westbound|north|south|east|west)\b","",value.casefold())
    value=re.sub(r"\b(service station|services|service area)\b","",value)
    return re.sub(r"[^a-z0-9]+"," ",value).strip()

def deduplicate_services(services):
    groups=[]
    for service in services:
        key=collection_name(service["name"])
        matched=None
        if key:
            for group in groups:
                if group["key"]!=key: continue
                if any(haversine_m((service["lat"],service["lng"]),point)<=2500 for point in group["points"]):
                    matched=group; break
        if not matched:
            matched={"key":key,"members":[],"points":[]}
            groups.append(matched)
        matched["members"].append(service)
        matched["points"].append((service["lat"],service["lng"]))

    collection=[]
    for group in groups:
        members=group["members"]
        # Prefer the least directional source label, then retain every mapped
        # point for matching a confirmed visit on either carriageway.
        representative=min(members,key=lambda item:(len(item["name"]),item["name"]))
        points=[{"lat":lat,"lng":lng} for lat,lng in group["points"]]
        collection.append({
            "id":"msa:"+group["key"]+":"+str(round(sum(point["lat"] for point in points)/len(points),4))+":"+str(round(sum(point["lng"] for point in points)/len(points),4)),
            "name":representative["name"],
            "lat":round(sum(point["lat"] for point in points)/len(points),6),
            "lng":round(sum(point["lng"] for point in points)/len(points),6),
            "operator":representative["operator"],
            "road":representative["road"],
            "points":points,
            "source_ids":[member["id"] for member in members],
        })
    return sorted(collection,key=lambda item:(item["name"].casefold(),item["id"]))

def enrich_achievement_metadata(service):
    operator=(service.get("operator") or "").casefold().replace(" ","")
    group=None
    if "welcomebreak" in operator: group="Welcome Break"
    elif operator=="moto": group="Moto"
    elif "roadchef" in operator: group="Roadchef"
    elif operator in ("extra","extramsa"): group="Extra"
    elif "westmorland" in operator or "tebay" in service["name"].casefold(): group="Westmorland"
    if group: service["operator_group"]=group
    road=service.get("road")
    if service.get("region")=="NI": service["country"]="Northern Ireland"
    elif road in ("A74(M)","M74","M8","M9","M90"): service["country"]="Scotland"
    elif road=="M4" and service["lng"] < -2.7: service["country"]="Wales"
    elif road and service.get("region","GB")=="GB": service["country"]="England"
    return service


def apply_reviewed_reference(service,reference):
    existing=reference.get(service["id"])
    if existing is None:
        source_ids=set(service.get("source_ids",[]))
        candidates=[item for item in reference.values() if source_ids.intersection(item.get("source_ids",[]))]
        if len(candidates)==1: existing=candidates[0]
    if existing is None:
        def site_name(name):
            key=collection_name(name)
            return re.sub(r"^(roadchef|moto|welcome break|westmorland|applegreen|extra) ","",key)
        candidates=[item for item in reference.values()
                    if site_name(item["name"])==site_name(service["name"])
                    and haversine_m((item["lat"],item["lng"]),(service["lat"],service["lng"]))<=1500]
        if len(candidates)==1: existing=candidates[0]
    if existing:
        # Names and OSM centroids can change. Keep the collection identity for the same source site.
        service["id"]=existing["id"]
        for key in ("road","region","road_match_distance_m"):
            if key in existing: service[key]=existing[key]
    if not service.get("road") or not service.get("region"):
        raise ValueError("New service area needs a reviewed motorway/region assignment: "+service["name"])
    return enrich_achievement_metadata(service)


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
    reference_path=Path("android-prototype/app/src/main/assets/uk-motorway-services-v1.json")
    reference={s["id"]:s for s in json.loads(reference_path.read_text())["services"]}
    services=deduplicate_services(services)
    services=[apply_reviewed_reference(service,reference) for service in services]
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
