#!/usr/bin/env python3
"""Build Great Britain A-road references from OS Open Roads centreline data.

Unlike raw OpenStreetMap ways, OS Open Roads is a topological centreline
network.  Every official A-road link keeps its own component identity, so a
numbered road may legitimately contain several disconnected stretches.
"""
import json, math, os, shutil, sqlite3, struct, time, urllib.request, zipfile
from datetime import datetime, timezone
from pathlib import Path

from build_motorway_cache import sample_line

OUT = Path("canonical-a-roads-v5")
INDEX = OUT / "index.json"
ARCHIVE = Path(".cache/oproad_gpkg_gb.zip")
GPKG = Path(".cache/oproad_gb.gpkg")
DOWNLOAD_URL = "https://api.os.uk/downloads/v1/products/OpenRoads/downloads?area=GB&format=GeoPackage&redirect"
SAMPLE_M = 100
# Newly opened routes can appear in OpenStreetMap before the next OS Open Roads
# release. These fallbacks are resolved only while building the static cache.
OSM_FALLBACK_BOUNDS = {"A1026":"51.47,-0.04,51.54,0.08"}
OVERPASS_URL = "https://overpass-api.de/api/interpreter"

def download_source():
    if GPKG.exists(): return
    ARCHIVE.parent.mkdir(exist_ok=True)
    if not ARCHIVE.exists():
        print("Downloading OS Open Roads Great Britain GeoPackage…")
        with urllib.request.urlopen(DOWNLOAD_URL, timeout=900) as response, ARCHIVE.open("wb") as output:
            shutil.copyfileobj(response, output)
    print("Extracting OS Open Roads GeoPackage…")
    with zipfile.ZipFile(ARCHIVE) as bundle:
        name=next(name for name in bundle.namelist() if name.lower().endswith(".gpkg"))
        with bundle.open(name) as source, GPKG.open("wb") as output: shutil.copyfileobj(source, output)

def source_table(connection):
    rows=connection.execute("SELECT table_name FROM gpkg_contents WHERE data_type='features'").fetchall()
    names=[row[0] for row in rows]
    return next((name for name in names if name.lower() in {"road_link","roadlink"}), None) or names[0]

def quoted(name): return '"' + name.replace('"','""') + '"'

def column_lookup(connection, table):
    return {row[1].lower().replace("_", ""):row[1] for row in connection.execute(f"PRAGMA table_info({quoted(table)})")}

def gpkg_line_parts(blob):
    """Read LineString/MultiLineString WKB from a GeoPackage geometry blob."""
    if not blob or blob[:2] != b"GP": return []
    flags=blob[3]; envelope=(flags >> 1) & 7
    offset=8 + {0:0,1:32,2:48,3:48,4:64}.get(envelope,0)
    def read_geometry(pos):
        endian="<" if blob[pos] == 1 else ">"; pos+=1
        geometry_type=struct.unpack_from(endian+"I",blob,pos)[0] & 0xff; pos+=4
        if geometry_type == 2:
            count=struct.unpack_from(endian+"I",blob,pos)[0]; pos+=4
            points=[]
            for _ in range(count):
                x,y=struct.unpack_from(endian+"dd",blob,pos); pos+=16; points.append((x,y))
            return [points],pos
        if geometry_type == 5:
            count=struct.unpack_from(endian+"I",blob,pos)[0]; pos+=4; lines=[]
            for _ in range(count):
                child,pos=read_geometry(pos); lines.extend(child)
            return lines,pos
        return [],pos
    return read_geometry(offset)[0]

def bng_to_wgs84(easting,northing):
    # EPSG:27700 to WGS84, using the standard Airy/Helmert conversion.
    a,b,f0,lat0,lon0,n0,e0=6377563.396,6356256.909,0.9996012717,math.radians(49),math.radians(-2),-100000,400000
    e2=1-(b*b)/(a*a); n=(a-b)/(a+b); lat=lat0; m=0
    while abs(northing-n0-m)>=0.00001:
        lat+=(northing-n0-m)/(a*f0)
        ma=(1+n+5*n*n/4+5*n**3/4)*(lat-lat0)
        mb=(3*n+3*n*n+21*n**3/8)*math.sin(lat-lat0)*math.cos(lat+lat0)
        mc=(15*n*n/8+15*n**3/8)*math.sin(2*(lat-lat0))*math.cos(2*(lat+lat0))
        md=35*n**3/24*math.sin(3*(lat-lat0))*math.cos(3*(lat+lat0)); m=b*f0*(ma-mb+mc-md)
    sin_lat,cos_lat,tan_lat=math.sin(lat),math.cos(lat),math.tan(lat)
    nu=a*f0/math.sqrt(1-e2*sin_lat*sin_lat); rho=a*f0*(1-e2)/(1-e2*sin_lat*sin_lat)**1.5; eta2=nu/rho-1; de=easting-e0
    lat-=tan_lat/(2*rho*nu)*de**2-tan_lat/(24*rho*nu**3)*(5+3*tan_lat*tan_lat+eta2-9*tan_lat*tan_lat*eta2)*de**4
    lon=lon0+de/(nu*cos_lat)-de**3/(6*nu**3*cos_lat)*(1+2*tan_lat*tan_lat+eta2)+de**5/(120*nu**5*cos_lat)*(5+28*tan_lat*tan_lat+24*tan_lat**4)
    # Airy 1830 -> WGS84 via OSGB36 Cartesian coordinates.
    sin_lat,cos_lat=math.sin(lat),math.cos(lat); nu_air=a/math.sqrt(1-e2*sin_lat*sin_lat)
    h=0; x1=(nu_air+h)*cos_lat*math.cos(lon); y1=(nu_air+h)*cos_lat*math.sin(lon); z1=((1-e2)*nu_air+h)*sin_lat
    tx,ty,tz,s,rx,ry,rz=446.448,-125.157,542.060,20.4894e-6,math.radians(0.1502/3600),math.radians(0.2470/3600),math.radians(0.8421/3600)
    x2=tx+(1+s)*x1-rz*y1+ry*z1; y2=ty+rz*x1+(1+s)*y1-rx*z1; z2=tz-ry*x1+rx*y1+(1+s)*z1
    a2,b2=6378137.0,6356752.3141; e22=1-(b2*b2)/(a2*a2); p=math.hypot(x2,y2); lat2=math.atan2(z2,p*(1-e22))
    for _ in range(8):
        nu2=a2/math.sqrt(1-e22*math.sin(lat2)**2); lat2=math.atan2(z2+e22*nu2*math.sin(lat2),p)
    return [round(math.degrees(math.atan2(y2,x2)),7),round(math.degrees(lat2),7)]

def load_links():
    download_source()
    connection=sqlite3.connect(GPKG); table=source_table(connection); columns=column_lookup(connection,table)
    number=columns["roadclassificationnumber"]; classification=columns["roadclassification"]; form=columns.get("formofway"); length=columns["length"]
    geometry=connection.execute("SELECT column_name FROM gpkg_geometry_columns WHERE table_name=?",(table,)).fetchone()[0]
    where=f"{quoted(classification)}='A Road' AND {quoted(number)} IS NOT NULL"
    if form: where+=f" AND COALESCE({quoted(form)},'') <> 'Slip Road'"
    query=f"SELECT {quoted(number)},{quoted(length)},{quoted(geometry)} FROM {quoted(table)} WHERE {where}"
    roads={}
    for ref,length_m,blob in connection.execute(query):
        ref=str(ref or "").upper().replace(" ","")
        if not ref.startswith("A") or not ref[1:].isdigit(): continue
        for line in gpkg_line_parts(blob):
            if len(line)<2: continue
            roads.setdefault(ref,[]).append((float(length_m or 0),[bng_to_wgs84(x,y) for x,y in line]))
    connection.close(); return roads

def geodesic_length_m(line):
    radius=6371000
    total=0
    for (lng1,lat1),(lng2,lat2) in zip(line,line[1:]):
        dlat=math.radians(lat2-lat1); dlng=math.radians(lng2-lng1)
        a=math.sin(dlat/2)**2+math.cos(math.radians(lat1))*math.cos(math.radians(lat2))*math.sin(dlng/2)**2
        total+=2*radius*math.atan2(math.sqrt(a),math.sqrt(1-a))
    return total

def load_osm_fallback_links(ref):
    """Fetch a short-lived reference gap from OSM for the static build only."""
    bounds=OSM_FALLBACK_BOUNDS[ref]
    query=(
        '[out:json][timeout:60];'
        f'way["highway"]["ref"~"(^|;){ref}(;|$)"]({bounds});'
        'out geom;'
    )
    request=urllib.request.Request(
        OVERPASS_URL,
        data=query.encode("utf-8"),
        headers={"Content-Type":"application/x-www-form-urlencoded","User-Agent":"roadprints-cache-builder"},
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        payload=json.load(response)
    links=[]
    for element in payload.get("elements",[]):
        geometry=element.get("geometry") or []
        if len(geometry)<2: continue
        line=[[point["lon"],point["lat"]] for point in geometry]
        links.append((geodesic_length_m(line),line))
    return links

def add_osm_fallbacks(roads):
    for ref in OSM_FALLBACK_BOUNDS:
        if roads.get(ref): continue
        print(f"OS Open Roads has no {ref}; building its static fallback from OpenStreetMap…")
        links=load_osm_fallback_links(ref)
        if not links:
            raise RuntimeError(f"OpenStreetMap fallback returned no geometry for {ref}.")
        roads[ref]=links
    return roads

def stitch_links(links):
    """Join adjacent RoadLinks into maximal continuous paths.

    RoadLinks split at every junction, but their end coordinates are
    topological.  Joining degree-two endpoints here retains the road shape
    without asking the browser to guess across unrelated links.
    """
    lines=[line for _,line in links if len(line)>=2]
    if not lines: return []
    def endpoint_key(point):
        return (round(point[0],6),round(point[1],6))
    adjacency={}
    for index,line in enumerate(lines):
        adjacency.setdefault(endpoint_key(line[0]),[]).append((index,False))
        adjacency.setdefault(endpoint_key(line[-1]),[]).append((index,True))
    used=set()
    def walk(index,at_end):
        line=list(reversed(lines[index])) if at_end else list(lines[index])
        used.add(index)
        output=line
        current_key=endpoint_key(output[-1])
        while len(adjacency.get(current_key,[]))==2:
            next_link=next((item for item in adjacency[current_key] if item[0] not in used),None)
            if not next_link: break
            next_index,next_at_end=next_link
            next_line=list(reversed(lines[next_index])) if next_at_end else list(lines[next_index])
            used.add(next_index)
            output.extend(next_line[1:])
            current_key=endpoint_key(output[-1])
        return output
    # Start at junctions/endpoints so each straight chain is represented once.
    for index,line in enumerate(lines):
        if index in used: continue
        start_key,end_key=endpoint_key(line[0]),endpoint_key(line[-1])
        if len(adjacency[start_key])!=2:
            yield walk(index,False)
        elif len(adjacency[end_key])!=2:
            yield walk(index,True)
    # Remaining links are closed loops where every endpoint has degree two.
    for index in range(len(lines)):
        if index not in used: yield walk(index,False)

def build(roads):
    OUT.mkdir(exist_ok=True); index={"version":"v5","region":"GB","roads":{},"failures":{},"source":"OS Open Roads"}
    for ref,links in sorted(roads.items(),key=lambda item:(int(item[0][1:]),item[0])):
        # Keep each official RoadLink as a distinct, continuous geometry path.
        # Flattening all points discarded topology in the browser and caused
        # short links to disappear during zoom-level sampling.
        paths=[]
        for component,line in enumerate(stitch_links(links)):
            sampled=[[*point,component] for point in sample_line(line)]
            if len(sampled)>=2: paths.append(sampled)
        anchor_count=sum(len(path) for path in paths)
        if anchor_count<3: continue
        total_km=sum(length for length,_ in links)/1000
        record={"version":"v5","id":f"GB:{ref}","region":"GB","ref":ref,"paths":paths,
                "total_km":round(total_km,3),"component_count":len(paths),"source":"OS Open Roads"}
        filename=f"GB-{ref}.json"; (OUT/filename).write_text(json.dumps(record,separators=(",",":")))
        index["roads"][f"GB:{ref}"]={"file":filename,"total_km":record["total_km"]}
    index["generated_at"]=datetime.now(timezone.utc).isoformat(); INDEX.write_text(json.dumps(index,separators=(",",":")))
    print(f"Wrote {len(index['roads'])} official Great Britain A-road references.")

if __name__ == "__main__": build(add_osm_fallbacks(load_links()))
