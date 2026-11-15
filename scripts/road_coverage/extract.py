"""Build the offline Stage 1.2C road extract from a saved OSM map response."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROAD_CLASSES = {
    "motorway", "trunk", "primary", "secondary", "tertiary",
    "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    "unclassified", "residential", "living_street", "service",
}
POSSIBLE_DRIVING_CLASSES = ROAD_CLASSES | {"track", "busway", "bus_guideway", "pedestrian"}
SIDE_SERVICES = {"driveway", "parking_aisle", "drive-through", "emergency_access"}
OPEN_ACCESS = {"yes", "permissive", "designated"}


def driving_direction(tags):
    highway = tags.get("highway")
    if highway not in ROAD_CLASSES or tags.get("service") in SIDE_SERVICES:
        return None
    access_keys = ("motorcar", "motor_vehicle", "vehicle", "access")
    if any(f"{key}:conditional" in tags for key in (*access_keys, "oneway", "oneway:motorcar", "oneway:motor_vehicle")):
        return None
    access = next((tags[key] for key in access_keys if key in tags), "yes")
    if access not in OPEN_ACCESS:
        return None
    default = "yes" if tags.get("junction") == "roundabout" or highway == "motorway" else "no"
    direction = tags.get("oneway:motorcar", tags.get("oneway:motor_vehicle", tags.get("oneway", default)))
    return {"yes": 1, "true": 1, "1": 1, "-1": -1, "no": 0, "false": 0, "0": 0}.get(direction)


def build(raw, source_date):
    root = ET.fromstring(raw)
    if root.tag != "osm":
        raise ValueError("Expected an OSM map response")
    bounds = {key: float(value) for key, value in root.find("bounds").attrib.items()}
    nodes = {int(n.get("id")): [float(n.get("lon")), float(n.get("lat"))] for n in root.iterfind("node")}
    retained_nodes = set()
    ways = []
    for way in root.iterfind("way"):
        tags = {t.get("k"): t.get("v") for t in way.iterfind("tag")}
        if tags.get("highway") not in POSSIBLE_DRIVING_CLASSES:
            continue
        direction = driving_direction(tags)
        refs = [int(n.get("ref")) for n in way.iterfind("nd")]
        if not all(ref in nodes for ref in refs):
            raise ValueError("Incomplete way in OSM map response")
        ways.append([int(way.get("id")), tags["highway"], tags.get("name"), tags.get("ref"), direction, refs])
        retained_nodes.update(refs)
    return {
        "format": 2,
        "source": "OpenStreetMap contributors",
        "attribution": "https://www.openstreetmap.org/copyright",
        "license": "https://opendatacommons.org/licenses/odbl/1-0/",
        "source_retrieved_utc": source_date,
        "source_sha256": hashlib.sha256(raw).hexdigest(),
        "bounds": bounds,
        "nodes": [[node, *nodes[node]] for node in sorted(retained_nodes)],
        "ways": sorted(ways),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("osm", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-date", required=True)
    args = parser.parse_args()
    extract = build(args.osm.read_bytes(), args.source_date)
    args.output.write_bytes(gzip.compress(json.dumps(extract, separators=(",", ":")).encode(), mtime=0))


if __name__ == "__main__":
    main()
