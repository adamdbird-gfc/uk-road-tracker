#!/usr/bin/env python3
"""1681 synthetic records with ~45k distinct UK edges, 793 foot contributors.

Uses immutable reference endpoints, never private movement data. This stresses
geometry volume; it is not a replay of the owner's actual archive.
"""
import argparse, gzip, json, math, pathlib, struct
from collections import OrderedDict
import generate_dense_map_fixture_a2 as dense

def pool(path):
    found = OrderedDict()
    with open(path, "rb") as stream:
        header = stream.read(44)
        assert header[:8] == b"RPRD0003"
        count, = struct.unpack(">i", header[40:])
        index = list(struct.iter_unpack(">iiii", stream.read(count * 16)))
        for _, _, offset, size in index:
            stream.seek(offset)
            data = gzip.decompress(stream.read(size))
            cursor = 0
            while cursor < len(data):
                way, a, b, ax, ay, bx, by, direction, flags, label = struct.unpack_from(">QQQiiiibBH", data, cursor)
                cursor += 44 + label
                if not flags & 1:
                    continue
                coords = [[ax / 1e7, ay / 1e7], [bx / 1e7, by / 1e7]]
                metres = 111195 * math.hypot((bx-ax)/1e7*math.cos(math.radians((ay+by)/2e7)), (by-ay)/1e7)
                if 10 <= metres <= 500:
                    found.setdefault((way, min(a,b), max(a,b)), coords)
            if len(found) >= 1460 * 31:
                return found
    raise ValueError(f"Insufficient distinct physical edges: {len(found)}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--assets", required=True)
    args = parser.parse_args()
    dense.edges_from_reference = pool
    dense.generate(args.reference, args.output, 1460, 209, 31)
    target = pathlib.Path(args.output)
    for i in range(1460):
        path = target / f"journey_dense-osm-{i:04d}.json"
        row = json.loads(path.read_text())
        row["mode"] = "driving" if i < 660 else "walking" if i < 1453 else "cycling"
        if 660 <= i < 1453 and i % 3 == 0:
            row["mode"] = "running"
        row["journey_corrections"] = {}
        path.write_text(json.dumps(row))
    # Add independently accepted major-road features to stress the national
    # reference layers as well as the ~45k minor-road layer. No connectors.
    assets = pathlib.Path(args.assets)
    index = json.loads((assets / "index.json").read_text())["roads"]
    references = []
    for key, value in index.items():
        if not key.startswith("GB:A"):
            continue
        path = assets / value["file"]
        data = json.loads(path.read_text())
        paths = data.get("paths", [])
        if paths and len(paths[0]) >= 2:
            references.append((key[3:], paths[0][:12]))
        if len(references) == 56:
            break
    assert len(references) == 56
    for i, (ref, coords) in enumerate(references):
        path = target / f"journey_dense-osm-{i:04d}.json"
        row = json.loads(path.read_text())
        row["processing_result"]["a_road_geojson"] = {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"road_ref": ref, "distance_m": 1000},
             "geometry": {"type": "LineString", "coordinates": coords}}]}
        path.write_text(json.dumps(row))
    for i in range(10):
        coords = [[2.35 + i * .003, 48.86], [2.352 + i * .003, 48.86]]
        row = {"journey_id": f"scale-paris-{i}", "mode": "walking" if i % 2 else "driving",
               "processing_status": "complete", "started_at": "2026-11-15T12:00:00Z",
               "ended_at": "2026-11-15T12:10:00Z",
               "processing_result": {"geojson": {"type": "FeatureCollection", "features": [
                   {"type": "Feature", "geometry": {"type": "LineString", "coordinates": coords}}]}}}
        (target / f"journey_scale-paris-{i}.json").write_text(json.dumps(row))
    for i in range(2):
        row = {"journey_id": f"scale-ferry-{i}", "mode": "ferry", "processing_status": "complete",
               "started_at": "2026-11-15T14:00:00Z", "ended_at": "2026-11-15T15:30:00Z",
               "route_geometry": {"type": "LineString", "coordinates": [[1.312,51.127],[1.5,51.05],[1.85,50.95]]}}
        (target / f"journey_scale-ferry-{i}.json").write_text(json.dumps(row))
    assert len(list(target.glob("journey_*.json"))) == 1681
    print(json.dumps({"records": 1681, "canonical_input_edges": 45260,
                      "uk_foot_sources": 793, "major_road_references": 56, "worldwide_sources": 10, "ferries": 2,
                      "limitation": "Synthetic independent-edge geometry, no owner movement data"}))

if __name__ == "__main__":
    main()
