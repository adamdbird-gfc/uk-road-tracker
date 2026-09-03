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

OUT = Path("canonical-a-roads-v4")
INDEX = OUT / "index.json"
ARCHIVE = Path(".cache/oproad_gpkg_gb.zip")
GPKG = Path(".cache/oproad_gb.gpkg")
DOWNLOAD_URL = "https://api.os.uk/downloads/v1/products/OpenRoads/downloads?area=GB&format=GeoPackage&redirect"
SAMPLE_M = 100

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

def build(roads):
    OUT.mkdir(exist_ok=True); index={"version":"v4","region":"GB","roads":{},"failures":{},"source":"OS Open Roads"}
    for ref,links in sorted(roads.items(),key=lambda item:(int(item[0][1:]),item[0])):
        anchors=[]
        for component,(_,line) in enumerate(links):
            anchors.extend([[*point,component] for point in sample_line(line)])
        if len(anchors)<3: continue
        total_km=sum(length for length,_ in links)/1000
        record={"version":"v4","id":f"GB:{ref}","region":"GB","ref":ref,"anchors":anchors,
                "total_km":round(total_km,3),"component_count":len(links),"source":"OS Open Roads"}
        filename=f"GB-{ref}.json"; (OUT/filename).write_text(json.dumps(record,separators=(",",":")))
        index["roads"][f"GB:{ref}"]={"file":filename,"total_km":record["total_km"]}
    index["generated_at"]=datetime.now(timezone.utc).isoformat(); INDEX.write_text(json.dumps(index,separators=(",",":")))
    print(f"Wrote {len(index['roads'])} official Great Britain A-road references.")

if __name__ == "__main__": build(load_links())
