#!/usr/bin/env python3
"""Validate a built v3 UK road + pedestrian reference before any owner APK release."""
import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct

EDGE = struct.Struct(">qqqiiiibbH")
INDEX = struct.Struct(">iiii")
MAX_TILE_BYTES = 4 * 1024 * 1024
EXPECTED_SITES = {
    "Gravesend": (18, 2571),
    "London": (-6, 2576),
    "Cardiff": (-159, 2573),
    "Glasgow": (-213, 2799),
    "Belfast": (-296, 2730),
}

def verify(path):
    data = Path(path).read_bytes()
    assert data[:8] == b"RPRD0003", "Not the approved v3 schema"
    tiles, = struct.unpack_from(">i", data, 40)
    assert 2000 < tiles < 365000, f"Implausible national tile count: {tiles}"
    index_end = 44 + tiles * INDEX.size
    assert index_end <= len(data), "Truncated tile index"
    seen = set()
    stats = {"tiles": tiles, "physical_edge_records": 0,
             "pedestrian_edge_records": 0, "separate_a_ref_records": 0,
             "maximum_uncompressed_tile": 0}
    for tile in range(tiles):
        x, y, offset, size = INDEX.unpack_from(data, 44 + tile * INDEX.size)
        assert (x, y) not in seen, "Duplicate tile"
        seen.add((x, y))
        assert index_end <= offset <= len(data) - size and size > 0, "Invalid tile offset"
        raw = gzip.decompress(data[offset:offset + size])
        stats["maximum_uncompressed_tile"] = max(stats["maximum_uncompressed_tile"], len(raw))
        assert len(raw) <= MAX_TILE_BYTES, f"Tile {x},{y} exceeds Android's 4 MiB limit"
        cursor = 0
        while cursor < len(raw):
            assert cursor + EDGE.size <= len(raw), f"Truncated edge in tile {x},{y}"
            values = EDGE.unpack_from(raw, cursor)
            label_length = values[-1]
            flags = values[-2]
            cursor += EDGE.size
            assert cursor + label_length <= len(raw), "Truncated edge label"
            label = raw[cursor:cursor + label_length].decode("utf-8")
            cursor += label_length
            assert chr(31) in label, "Missing independent name/ref separator"
            _, road_ref = label.split(chr(31), 1)
            assert flags >> 2 < 64, "Road class exceeds Android packed flag capacity"
            stats["physical_edge_records"] += 1
            if flags & 2:
                stats["pedestrian_edge_records"] += 1
            if road_ref.startswith("A") and road_ref[1:2].isdigit():
                stats["separate_a_ref_records"] += 1
        assert cursor == len(raw), "Truncated tile payload"
    # Locations correspond to coarse OSM 0.02° reference tiles, including tile halo.
    for name, (x, y) in EXPECTED_SITES.items():
        assert any((x + dx, y + dy) in seen
                   for dx in range(-4, 5) for dy in range(-4, 5)), f"Missing {name} reference"
    assert stats["pedestrian_edge_records"] > 1000, "Pedestrian network appears absent"
    assert stats["separate_a_ref_records"] > 1000, "Separate official A road refs appear absent"
    stats["sha256"] = hashlib.sha256(data).hexdigest()
    stats["version_digest"] = data[8:40].hex()
    stats["bytes"] = len(data)
    return stats

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    stats = verify(args.reference)
    print(json.dumps(stats, indent=2, sort_keys=True))
    if args.json:
        args.json.write_text(json.dumps(stats, sort_keys=True, indent=2) + "\n")
