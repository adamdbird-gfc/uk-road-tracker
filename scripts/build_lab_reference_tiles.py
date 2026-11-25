#!/usr/bin/env python3
"""Package public OSM extracts for the Map Laboratory (requires osmium)."""

import argparse
import gzip
import hashlib
import json
import math
import re
from collections import defaultdict
from pathlib import Path

import osmium

CELLS_PER_DEGREE = 50
ROAD = re.compile(r"(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(_link)?")
PATH = {"footway", "pedestrian", "path", "steps", "cycleway", "bridleway", "corridor"}


def cells(a, b):
    scale = CELLS_PER_DEGREE
    for x in range(math.floor(min(a[0], b[0]) * scale), math.floor(max(a[0], b[0]) * scale) + 1):
        for y in range(math.floor(min(a[1], b[1]) * scale), math.floor(max(a[1], b[1]) * scale) + 1):
            yield x, y


def write_json(path, data, compressed=False):
    payload = (json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n").encode()
    path.write_bytes(gzip.compress(payload, mtime=0) if compressed else payload)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="New version directory")
    parser.add_argument("--region", nargs=7, action="append", required=True,
                        metavar=("PBF", "SOURCE_URL", "SHA256", "WEST", "SOUTH", "EAST", "NORTH"))
    parser.add_argument("--entrances-only", action="store_true",
                        help="Build station entrance metadata for the named existing tile directory")
    parser.add_argument("--driving", action="store_true",
                        help="Build a separate road tile set with direction and service roads")
    args = parser.parse_args()
    tiles, sources, entrances = defaultdict(dict), [], {}
    for filename, url, expected, west, south, east, north in args.region:
        with open(filename, "rb") as stream:
            checksum = hashlib.file_digest(stream, "sha256").hexdigest()
        if checksum != expected:
            parser.error(f"SHA-256 mismatch for {filename}")
        wanted = set(cells((float(west), float(south)), (float(east), float(north))))
        reader = osmium.io.Reader(filename)
        with reader:
            timestamp = reader.header().get("osmosis_replication_timestamp")
        sources.append({"url": url, "sha256": checksum, "timestamp": timestamp})
        if args.entrances_only:
            points = osmium.FileProcessor(filename).with_filter(osmium.filter.EntityFilter(osmium.osm.NODE))
            for node in points:
                if node.tags.get("railway") not in {"train_station_entrance", "subway_entrance"}:
                    continue
                if node.tags.get("access") in {"private", "no"} or node.tags.get("entrance") == "emergency":
                    continue
                coords = [node.lon, node.lat]
                if any(cell in wanted for cell in cells(coords, coords)):
                    entrances[node.id] = {"id": node.id, "point": coords}
                    if node.tags.get("entrance") == "exit":
                        entrances[node.id]["exitOnly"] = True
            continue
        for way in osmium.FileProcessor(filename).with_locations().with_filter(osmium.filter.EntityFilter(osmium.osm.WAY)):
            kind = way.tags.get("highway", "")
            if args.driving and not (ROAD.fullmatch(kind) or kind == "service"):
                continue
            if not args.driving and not ROAD.fullmatch(kind) and kind not in PATH:
                continue
            if way.tags.get("area") == "yes":
                continue
            if (ROAD.fullmatch(kind) or args.driving) and way.tags.get("access") in {"private", "no"}:
                continue
            if len(way.nodes) < 2 or not all(n.location.valid() for n in way.nodes):
                continue
            coords = [[n.lon, n.lat] for n in way.nodes]
            hits = {cell for a, b in zip(coords, coords[1:]) for cell in cells(a, b) if cell in wanted}
            if not hits:
                continue
            tags = {key: way.tags[key] for key in ("name", "footway", "indoor", "layer", "bridge", "tunnel") if key in way.tags}
            if args.driving:
                tags.update({key: way.tags[key] for key in ("junction", "oneway") if key in way.tags})
            feature = {"id": way.id, "kind": kind, "coords": coords,
                       "nodes": [node.ref for node in way.nodes], **tags}
            for cell in hits:
                tiles[cell][way.id] = feature
    if args.entrances_only:
        index = json.loads((args.output / "index.json").read_text())
        if sources != index["sources"]:
            parser.error("station entrance inputs must match the pinned road index")
        write_json(args.output / "station-entrances.json", {
            "schema": 1, "entrances": [entrances[key] for key in sorted(entrances)],
        })
        return
    args.output.mkdir(parents=True, exist_ok=False)
    keys = []
    for (x, y), ways in sorted(tiles.items()):
        key = f"{x}_{y}"
        keys.append(key)
        write_json(args.output / f"{key}.json.gz", [ways[wid] for wid in sorted(ways)], compressed=True)
    write_json(args.output / "index.json", {
        "schema": 1, "cellsPerDegree": CELLS_PER_DEGREE, "tiles": keys,
        "attribution": "© OpenStreetMap contributors", "license": "ODbL-1.0", "sources": sources,
    })


if __name__ == "__main__":
    main()
