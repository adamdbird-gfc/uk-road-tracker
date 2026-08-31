#!/usr/bin/env python3
"""Build the bounded Gravesend multimodal feasibility network from OSM.

The normal Overpass endpoints are intentionally not required. The script uses
small, rate-limited OpenStreetMap map API tiles, retains highway ways, and
emits direction-independent consecutive-node segments for the browser POC.
"""

from __future__ import annotations

import json
import math
import time
import gzip
import argparse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


TILES = {
    "gravesend": {"south": 51.425, "west": 0.350, "north": 51.445, "east": 0.385},
    # Covers the Charing Cross / Salisbury Square walking corridor and nearby
    # central-London activity without downloading the whole London network.
    "central-london": {"south": 51.500, "west": -0.145, "north": 51.515, "east": -0.105},
}
BOUNDS = TILES["gravesend"]
ROWS = 4
COLUMNS = 4
USER_AGENT = "Roadprints-POC/0.1 (+https://adamdbird-gfc.github.io/uk-road-tracker/)"
OUTPUT = Path(__file__).resolve().parents[1] / "gravesend-network-v1.json.gz"

EXCLUDED_HIGHWAYS = {
    "abandoned", "construction", "elevator", "platform", "proposed",
    "raceway", "razed", "rest_area", "services", "street_lamp",
}
NON_DRIVING_HIGHWAYS = {
    "bridleway", "corridor", "cycleway", "footway", "path", "pedestrian",
    "steps",
}
NON_FOOT_HIGHWAYS = {"motorway", "motorway_link"}


def allowed(tags: dict[str, str], mode: str) -> bool:
    highway = tags.get("highway", "")
    access = tags.get("access", "")
    mode_access = tags.get(mode, "")
    if mode_access in {"no", "private"}:
        return False
    if access in {"no", "private"} and mode_access not in {"yes", "permissive", "designated"}:
        return False
    if mode == "motor_vehicle":
        return highway not in NON_DRIVING_HIGHWAYS
    if mode == "foot":
        return highway not in NON_FOOT_HIGHWAYS and tags.get("foot") != "no"
    if mode == "bicycle":
        return (
            highway not in {"motorway", "motorway_link", "steps"}
            and tags.get("bicycle") != "no"
        )
    return False


def haversine_metres(a: tuple[float, float], b: tuple[float, float]) -> float:
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    value = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * 6_371_008.8 * math.asin(math.sqrt(value))


def inside_midpoint(a: tuple[float, float], b: tuple[float, float]) -> bool:
    lat = (a[0] + b[0]) / 2
    lon = (a[1] + b[1]) / 2
    return BOUNDS["south"] <= lat <= BOUNDS["north"] and BOUNDS["west"] <= lon <= BOUNDS["east"]


def fetch_tile(west: float, south: float, east: float, north: float) -> bytes:
    url = (
        "https://api.openstreetmap.org/api/0.6/map?bbox="
        f"{west:.7f},{south:.7f},{east:.7f},{north:.7f}"
    )
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("tile", choices=sorted(TILES), nargs="?", default="gravesend")
    args = parser.parse_args()
    global BOUNDS, OUTPUT
    BOUNDS = TILES[args.tile]
    OUTPUT = Path(__file__).resolve().parents[1] / f"{args.tile}-network-v1.json.gz"
    lat_step = (BOUNDS["north"] - BOUNDS["south"]) / ROWS
    lon_step = (BOUNDS["east"] - BOUNDS["west"]) / COLUMNS
    nodes: dict[int, tuple[float, float]] = {}
    ways: dict[int, tuple[list[int], dict[str, str]]] = {}

    for row in range(ROWS):
        for column in range(COLUMNS):
            south = BOUNDS["south"] + row * lat_step
            north = south + lat_step
            west = BOUNDS["west"] + column * lon_step
            east = west + lon_step
            print(f"Fetching tile {row * COLUMNS + column + 1}/{ROWS * COLUMNS}…", flush=True)
            root = ET.fromstring(fetch_tile(west, south, east, north))
            for element in root:
                if element.tag == "node":
                    nodes[int(element.attrib["id"])] = (
                        float(element.attrib["lat"]),
                        float(element.attrib["lon"]),
                    )
                elif element.tag == "way":
                    node_ids = [int(item.attrib["ref"]) for item in element.findall("nd")]
                    tags = {item.attrib["k"]: item.attrib["v"] for item in element.findall("tag")}
                    if tags.get("highway") and tags["highway"] not in EXCLUDED_HIGHWAYS:
                        ways[int(element.attrib["id"])] = (node_ids, tags)
            time.sleep(1.05)

    features = []
    type_totals: dict[str, float] = {}
    for way_id, (node_ids, tags) in ways.items():
        modes = [
            label
            for label, access_key in (
                ("driving", "motor_vehicle"),
                ("on_foot", "foot"),
                ("cycling", "bicycle"),
            )
            if allowed(tags, access_key)
        ]
        if not modes:
            continue
        for first_id, second_id in zip(node_ids, node_ids[1:]):
            first = nodes.get(first_id)
            second = nodes.get(second_id)
            if not first or not second or not inside_midpoint(first, second):
                continue
            low, high = sorted((first_id, second_id))
            length = haversine_metres(first, second)
            highway = tags["highway"]
            type_totals[highway] = type_totals.get(highway, 0.0) + length
            features.append({
                "type": "Feature",
                "id": f"osm:{way_id}:{low}:{high}",
                "properties": {
                    "segment_id": f"osm:{way_id}:{low}:{high}",
                    "modes": modes,
                    "length_m": round(length, 2),
                },
                "geometry": {
                    "type": "LineString",
                    "coordinates": [[first[1], first[0]], [second[1], second[0]]],
                },
            })

    features.sort(key=lambda feature: feature["properties"]["segment_id"])
    result = {
        "type": "FeatureCollection",
        "metadata": {
            "name": f"{args.tile.replace('-', ' ').title()} bounded multimodal feasibility network",
            "version": "v1",
            "source": "OpenStreetMap contributors",
            "license": "ODbL",
            "bounds": BOUNDS,
            "segment_count": len(features),
            "total_length_m": round(sum(item["properties"]["length_m"] for item in features), 2),
            "length_by_highway_m": {key: round(value, 2) for key, value in sorted(type_totals.items())},
            "rules": "Direction-independent consecutive OSM way-node segments eligible for at least one mode; provisional POC denominator.",
        },
        "features": features,
    }
    OUTPUT.write_bytes(gzip.compress(json.dumps(result, separators=(",", ":")).encode("utf-8"), compresslevel=9))
    print(json.dumps(result["metadata"], indent=2))


if __name__ == "__main__":
    main()
