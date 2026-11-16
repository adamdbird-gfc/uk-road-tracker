"""Build the offline passenger railway catalogue from a pinned Geofabrik UK PBF.

Requires osmium and pyproj. The result is an ODbL derivative database.
Track edges meet only at shared OSM nodes, including through tunnels.
"""
import argparse
import csv
from collections import defaultdict
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import sqlite3
import tempfile

import osmium
from pyproj import Geod, Transformer

PROJECTION = Transformer.from_crs(4326, 32630, always_xy=True)
GEODESIC = Geod(ellps="WGS84")
CELL = 128
# Reviewed Network Rail running tracks and crossovers at Cardiff Canton.
CARDIFF_RUNNING_WAYS = {118601533,118601560,118601568,118601573,118710722,118710983,367718496}
ROAD_TYPES = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
    "residential", "living_street", "service", "track", "road",
}
SCHEMA = """
CREATE TABLE edge(id INTEGER PRIMARY KEY, way INTEGER, a INTEGER, b INTEGER,
    ax REAL, ay REAL, bx REAL, by REAL, metres REAL, tunnel INTEGER, ref TEXT);
CREATE TABLE station(id INTEGER PRIMARY KEY, name TEXT, x REAL, y REAL);
CREATE TABLE attachment(station INTEGER, node INTEGER, PRIMARY KEY(station,node)) WITHOUT ROWID;
CREATE TABLE road(id INTEGER PRIMARY KEY, ax REAL, ay REAL, bx REAL, by REAL);
CREATE TABLE road_cell(x INTEGER,y INTEGER,id INTEGER,PRIMARY KEY(x,y,id)) WITHOUT ROWID;
"""


def passenger_members(source):
    ways = set()
    stops = set()
    for relation in osmium.FileProcessor(source, osmium.osm.RELATION):
        tags = relation.tags
        if tags.get("type") != "route" or tags.get("route") != "train":
            continue
        if tags.get("service") in {"freight", "tourism", "preserved", "heritage"}:
            continue
        for member in relation.members:
            if member.type == "w" and member.role in {"", "forward", "backward"}:
                ways.add(member.ref)
            if member.type == "n" and member.role in {"stop", "stop_entry_only", "stop_exit_only"}:
                stops.add(member.ref)
    return ways, stops


def nir_passenger_track(tags):
    return (
        tags.get("operator") == "Translink NI Railways"
        and tags.get("gauge") == "1600"
        and tags.get("railway:traffic_mode") == "passenger"
        and (tags.get("usage") in {"main", "branch"} or tags.get("service") == "crossover")
        and tags.get("service") not in {"yard", "spur", "siding"}
        and tags.get("usage") not in {"industrial", "military", "tourism", "heritage"}
        and tags.get("access") != "no"
    )


def reviewed_cardiff_track(way):
    tags = way.tags
    return (way.id in CARDIFF_RUNNING_WAYS and tags.get("operator") == "Network Rail"
            and (tags.get("ref") == "SWM2" or tags.get("service") == "crossover"))


def usable_track(way, passenger):
    tags = way.tags
    return (
        tags.get("railway") == "rail"
        and (nir_passenger_track(tags) or reviewed_cardiff_track(way) or (
        way.id in passenger
        and tags.get("usage") not in {"industrial", "military", "tourism", "heritage"}
        and tags.get("service") not in {"yard", "spur", "crossover"}
        and tags.get("access") != "no"
        ))
    )


def road_way(way):
    kind = way.tags.get("highway", "").removesuffix("_link")
    return kind in ROAD_TYPES and way.tags.get("motor_vehicle") != "no"


def positions(nodes):
    locations = [(node.ref, node.lon, node.lat) for node in nodes if node.location.valid()]
    if len(locations) != len(nodes) or not locations:
        return []
    ids, lons, lats = zip(*locations)
    xs, ys = PROJECTION.transform(lons, lats)
    return list(zip(ids, lons, lats, xs, ys))


def box(ax, ay, bx, by):
    return min(ax,bx), max(ax,bx), min(ay,by), max(ay,by)


def cells(ax, ay, bx, by, pad=0):
    left, right, bottom, top = box(ax, ay, bx, by)
    for x in range(math.floor((left-pad)/CELL), math.floor((right+pad)/CELL)+1):
        for y in range(math.floor((bottom-pad)/CELL), math.floor((top+pad)/CELL)+1):
            yield x, y


def tracks(db, source, passenger, stop_members, locations):
    nearby_roads = set()
    track_nodes = {}
    stops = []
    edge_count = 0
    processor = osmium.FileProcessor(source, osmium.osm.NODE | osmium.osm.WAY).with_locations(f"sparse_file_array,{locations}")
    for obj in processor.with_filter(osmium.filter.EmptyTagFilter()):
        if obj.is_node():
            tags = obj.tags
            name = tags.get("name")
            is_station = tags.get("railway") in {"station","halt"}
            is_stop = passenger_stop(tags)
            if name and (is_station or is_stop or obj.id in stop_members):
                x, y = PROJECTION.transform(obj.lon, obj.lat)
                stops.append((obj.id,name,x,y,is_station,is_stop))
            continue
        if not usable_track(obj, passenger):
            continue
        points = positions(obj.nodes)
        tunnel = obj.tags.get("tunnel", "no") not in {"no", "building_passage"}
        ref = obj.tags.get("ref", obj.tags.get("name", ""))
        for first, last in zip(points, points[1:]):
            a, lon, lat, ax, ay = first
            b, end_lon, end_lat, bx, by = last
            metres = GEODESIC.inv(lon, lat, end_lon, end_lat)[2]
            if a == b or not 0 < metres <= 5000:
                continue
            edge_count += 1
            db.execute("INSERT INTO edge VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                       (edge_count,obj.id,a,b,ax,ay,bx,by,metres,int(tunnel),ref))
            track_nodes[a], track_nodes[b] = (ax, ay), (bx, by)
            nearby_roads.update(cells(ax,ay,bx,by,pad=25))
    attach_stations(db, stops, stop_members, track_nodes)
    reviewed_ni_attachments(db)
    db.execute("CREATE INDEX station_x ON station(x)")
    db.execute("CREATE INDEX edge_a ON edge(a)")
    db.execute("CREATE INDEX edge_b ON edge(b)")
    print(f"{edge_count:,} rail segments", flush=True)
    return nearby_roads


def station_name(name):
    return name.casefold().removesuffix(" station")


def passenger_stop(tags):
    return (tags.get("railway") == "stop" and tags.get("train") == "yes"
            or tags.get("public_transport") == "stop_position"
            and tags.get("network") in {"NIR", "NI Railways"})


def attach_stations(db, stops, stop_members, track_nodes):
    """Use actual on-track stop positions even when route relations list station objects."""
    station_grid = defaultdict(list)
    for identifier, name, x, y, is_station, is_stop in stops:
        if is_station:
            station_grid[math.floor(x/256),math.floor(y/256)].append((identifier,name,x,y))
    attached = set()
    for node, name, x, y, _, is_stop in sorted(stops):
        if node not in track_nodes or not (is_stop or node in stop_members):
            continue
        candidates = [
            (math.hypot(s[2]-x,s[3]-y), s) for i in range(-2,3) for j in range(-2,3)
            for s in station_grid.get((math.floor(x/256)+i, math.floor(y/256)+j), ())
            if station_name(name) == station_name(s[1])
        ]
        distance, station = min(candidates, default=(math.inf,None))
        if distance > 450:
            station = (node,name,x,y)
        identifier, name, sx, sy = station
        if identifier not in attached:
            db.execute("INSERT INTO station VALUES(?,?,?,?)",(identifier,name,sx,sy))
            attached.add(identifier)
        db.execute("INSERT INTO attachment VALUES(?,?)",(identifier,node))
    print(f"{len(attached):,} station objects", flush=True)


def reviewed_ni_attachments(db):
    # These station objects have no attached stop-position node in the extract.
    # Pin verified platform-track vertices without adding track or distance.
    fixture = Path(__file__).with_name("national_railway_ni_stations.csv")
    with fixture.open(newline="") as source:
        for station, name, way, node in csv.reader(source):
            position = db.execute(
                "SELECT ax,ay FROM edge WHERE way=? AND a=? UNION "
                "SELECT bx,by FROM edge WHERE way=? AND b=?", (way,node,way,node)
            ).fetchone()
            if position is None:
                raise ValueError(f"Missing reviewed station attachment: {name}, {way}, {node}")
            db.execute("INSERT OR IGNORE INTO station VALUES(?,?,?,?)", (station,name,*position))
            db.execute("INSERT OR IGNORE INTO attachment VALUES(?,?)", (station,node))


def roads(db, source, nearby, locations):
    count = 0
    processor = osmium.FileProcessor(source, osmium.osm.NODE | osmium.osm.WAY).with_locations(f"sparse_file_array,{locations}")
    for way in processor.with_filter(osmium.filter.KeyFilter("highway")):
        if not way.is_way() or not road_way(way):
            continue
        points = positions(way.nodes)
        for first, last in zip(points, points[1:]):
            ax, ay = first[3:]
            bx, by = last[3:]
            relevant = [cell for cell in cells(ax,ay,bx,by) if cell in nearby]
            if (ax,ay) == (bx,by) or not relevant:
                continue
            count += 1
            db.execute("INSERT INTO road VALUES(?,?,?,?,?)",(count,ax,ay,bx,by))
            db.executemany("INSERT INTO road_cell VALUES(?,?,?)",((x,y,count) for x,y in relevant))
    print(f"{count:,} road comparison segments", flush=True)


def build(source, destination, source_sha):
    with source.open("rb") as stream:
        actual_sha = hashlib.file_digest(stream, "sha256").hexdigest()
    if source_sha != actual_sha:
        raise ValueError(f"Source SHA-256 mismatch. Read {actual_sha}")
    passenger, stops = passenger_members(source)
    with tempfile.TemporaryDirectory(dir=destination.resolve().parent) as scratch:
        database = Path(scratch) / "national.sqlite"
        with sqlite3.connect(database) as db:
            db.executescript(SCHEMA)
            location_cache = Path(scratch) / "locations.idx"
            nearby = tracks(db, source, passenger, stops, location_cache)
            db.commit()
            location_cache.unlink(missing_ok=True)
            roads(db, source, nearby, location_cache)
            location_cache.unlink(missing_ok=True)
            counts = {table: db.execute(f"SELECT count(*) FROM {table}").fetchone()[0]
                      for table in ("edge", "station", "attachment", "road")}
            db.execute("PRAGMA user_version=1")
            db.commit()
            db.execute("VACUUM")
            if db.execute("PRAGMA integrity_check").fetchone() != ("ok",):
                raise ValueError("Catalogue failed integrity check")
        manifest = dict(version="uk-osm-20261112-r2", source=source.name, source_sha256=actual_sha,
                        attribution="© OpenStreetMap contributors", licence="ODbL-1.0",
                        counts=counts, bytes=database.stat().st_size)
        with database.open("rb") as stream:
            manifest["sha256"] = hashlib.file_digest(stream, "sha256").hexdigest()
        destination.mkdir(parents=True,exist_ok=True)
        with database.open("rb") as stream, (destination / "national.sqlite.gzip").open("wb") as output:
            with gzip.GzipFile(filename="", fileobj=output, mode="wb", compresslevel=6, mtime=0) as compressed:
                shutil.copyfileobj(stream,compressed)
        (destination / "national.json").write_text(json.dumps(manifest,indent=2)+"\n")
        print(json.dumps(manifest))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source",type=Path)
    parser.add_argument("destination",type=Path)
    parser.add_argument("--source-sha256",required=True)
    args = parser.parse_args()
    build(args.source,args.destination,args.source_sha256)
