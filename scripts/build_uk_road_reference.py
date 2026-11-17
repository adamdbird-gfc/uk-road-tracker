#!/usr/bin/env python3
"""Build the seekable Roadprints road reference from pinned Geofabrik OSM extracts."""

import argparse
from collections import Counter, OrderedDict
import gzip
from hashlib import file_digest, sha256
from pathlib import Path
import struct
import tempfile

import osmium

from build_gravesend_coverage import ROAD_CLASSES, direction, eligible


TILE = 200_000  # 0.02 degrees in OSM's integer coordinate units
HALO = 4_000  # At least 21 m within the UK
CLASSES = tuple(sorted(ROAD_CLASSES))
MAJOR = {"motorway", "trunk", "primary"}
UK_BOUNDS = (-9 * 10**7, 49 * 10**7, 3 * 10**7, 62 * 10**7)
EDGE = struct.Struct(">qqqiiiibbH")


class Tiles:
    def __init__(self, root):
        self.root = root
        self.open_files = OrderedDict()

    def append(self, tile, data):
        handle = self.open_files.get(tile)
        if handle is None:
            if len(self.open_files) == 96:
                self.open_files.popitem(last=False)[1].close()
            handle = (self.root / f"{tile[0]}_{tile[1]}").open("ab")
            self.open_files[tile] = handle
        else:
            self.open_files.move_to_end(tile)
        handle.write(data)

    def close(self):
        for handle in self.open_files.values():
            handle.close()


def collect(source, tiles):
    for way in osmium.FileProcessor(source).with_locations("flex_mem").with_filter(
        osmium.filter.EntityFilter(osmium.osm.WAY)
    ):
        highway = way.tags.get("highway")
        if highway not in ROAD_CLASSES:
            continue
        tags = dict(way.tags)
        travel_direction = direction(tags)
        major = highway.removesuffix("_link") in MAJOR or any(
            ref.strip().startswith("A") and ref.strip()[1:2].isdigit()
            for ref in tags.get("ref", "").split(";")
        )
        local = eligible(tags) and not major
        label = (tags.get("name") or tags.get("ref") or "").encode("utf-8")
        if len(label) > 1024:
            label = b""
        nodes = list(way.nodes)
        repeats = Counter(tuple(sorted((a.ref, b.ref))) for a, b in zip(nodes, nodes[1:]))
        for a, b in zip(nodes, nodes[1:]):
            if not (a.location.valid() and b.location.valid()):
                continue
            if repeats[tuple(sorted((a.ref, b.ref)))] != 1:
                continue
            ax, ay, bx, by = a.x, a.y, b.x, b.y
            if (ax, ay) == (bx, by):
                continue
            west, south, east, north = UK_BOUNDS
            if not (west <= ax <= east and west <= bx <= east
                    and south <= ay <= north and south <= by <= north):
                continue
            data = EDGE.pack(way.id, a.ref, b.ref, ax, ay, bx, by,
                             2 if travel_direction is None else travel_direction,
                             (CLASSES.index(highway) << 1) | int(local), len(label)) + label
            # Add the same physical edge to each intersecting tile, including the
            # attribution search halo. Identity and orientation are independent of tile.
            for x in range((min(ax, bx) - HALO) // TILE, (max(ax, bx) + HALO) // TILE + 1):
                for y in range((min(ay, by) - HALO) // TILE, (max(ay, by) + HALO) // TILE + 1):
                    tiles.append((x, y), data)


def build(sources, output):
    hashes = []
    for source in sources:
        with source.open("rb") as stream:
            hashes.append(file_digest(stream, "sha256").digest())
    version = sha256(b"roadprints-osm-road-tiles-v2\0" + b"".join(hashes)).digest()
    with tempfile.TemporaryDirectory(prefix="roadprints-road-tiles-") as directory:
        root = Path(directory)
        tiles = Tiles(root)
        try:
            for source in sources:
                collect(source, tiles)
        finally:
            tiles.close()
        paths = sorted(root.iterdir(), key=lambda path: tuple(map(int, path.name.split("_"))))
        with output.open("wb") as target:
            target.write(b"RPRD0002" + version + struct.pack(">i", len(paths)))
            target.seek(44 + 16 * len(paths))
            index = []
            for path in paths:
                x, y = map(int, path.name.split("_"))
                raw = path.read_bytes()
                if len(raw) > 4 * 1024 * 1024:
                    raise ValueError(f"Uncompressed tile exceeds reader budget at {x},{y}")
                data = gzip.compress(raw, compresslevel=6, mtime=0)
                index.append((x, y, target.tell(), len(data)))
                target.write(data)
            target.seek(44)
            for row in index:
                target.write(struct.pack(">iiii", *row))
    print(f"{len(paths)} tiles, {output.stat().st_size} bytes, version {version.hex()}")
    for source, digest in zip(sources, hashes):
        print(f"{digest.hex()}  {source.name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("sources", type=Path, nargs="+")
    args = parser.parse_args()
    build(args.sources, args.output)


if __name__ == "__main__":
    main()
