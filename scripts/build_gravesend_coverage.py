#!/usr/bin/env python3
"""Build the offline driving-coverage extract from one saved OSM map API response."""

import argparse
import gzip
import json
import xml.etree.ElementTree as ET
from collections import Counter
from hashlib import sha256
from pathlib import Path

PUBLIC_CLASSES = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
    "residential", "living_street",
}
ROAD_CLASSES = PUBLIC_CLASSES | {kind + "_link" for kind in PUBLIC_CLASSES} | {
    "service", "track", "road", "construction", "busway", "pedestrian",
}
PUBLIC_ACCESS = {"yes", "designated", "permissive"}


def direction(tags):
    for key in ("oneway:motorcar", "oneway:motor_vehicle", "oneway:vehicle", "oneway"):
        if key in tags:
            return {"yes": 1, "1": 1, "true": 1, "-1": -1,
                    "no": 0, "0": 0, "false": 0}.get(tags[key])
    return int(tags["highway"] in {"motorway", "motorway_link"} or
               tags.get("junction") == "roundabout")


def eligible(tags):
    # Excluded motor-access ways remain possible alignment competitors.
    access = next((tags[key] for key in ("motorcar", "motor_vehicle", "vehicle", "access")
                   if key in tags), "yes")
    return (tags["highway"].removesuffix("_link") in PUBLIC_CLASSES
            and access in PUBLIC_ACCESS
            and not any(key.endswith(":conditional") or key.endswith(":forward")
                        or key.endswith(":backward") for key in tags
                        if key.split(":")[0] in {"oneway", "motorcar", "motor_vehicle", "vehicle", "access"}))


def build(raw, retrieved_utc):
    root = ET.fromstring(raw)
    bounds = {k: float(v) for k, v in root.find("bounds").attrib.items()}
    nodes = {int(node.attrib["id"]): [round(float(node.attrib[key]) * 10_000_000)
                                     for key in ("lon", "lat")]
             for node in root.findall("node")}
    ways = []
    for way in root.findall("way"):
        tags = {tag.attrib["k"]: tag.attrib["v"] for tag in way.findall("tag")}
        highway = tags.get("highway")
        if highway not in ROAD_CLASSES:
            continue
        refs = [int(nd.attrib["ref"]) for nd in way.findall("nd")]
        # The API includes entire ways, with some nodes outside its bounding box.
        # Include edges only between nodes supplied by this response.
        edges = [(a, b) for a, b in zip(refs, refs[1:])
                 if a in nodes and b in nodes and nodes[a] != nodes[b]
                 and bounds["minlon"] <= (nodes[a][0] + nodes[b][0]) / 20_000_000 <= bounds["maxlon"]
                 and bounds["minlat"] <= (nodes[a][1] + nodes[b][1]) / 20_000_000 <= bounds["maxlat"]]
        repeated = Counter(tuple(sorted(edge)) for edge in edges)
        for a, b in edges:
            if repeated[tuple(sorted((a, b)))] > 1:
                continue  # An OSM way that reuses the same edge needs a separate identity rule.
            ways.append([int(way.attrib["id"]), a, b, tags.get("name"), highway,
                         direction(tags), eligible(tags)])
    used = {node for _, a, b, *_ in ways for node in (a, b)}
    return {"format": "roadprints-gravesend-tagged-v1", "source": "OpenStreetMap contributors", "license": "ODbL-1.0",
            "retrieved_utc": retrieved_utc, "bounds": bounds,
            "source_sha256": sha256(raw).hexdigest(),
            "nodes": {node: nodes[node] for node in sorted(used)}, "edges": sorted(ways)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("osm", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--retrieved-utc", required=True)
    args = parser.parse_args()
    data = build(args.osm.read_bytes(), args.retrieved_utc)
    args.output.write_bytes(gzip.compress(json.dumps(data, separators=(",", ":")).encode(), mtime=0))
    print(f'{data["retrieved_utc"]}: {len(data["nodes"])} nodes, {len(data["edges"])} edges')


if __name__ == "__main__":
    main()
