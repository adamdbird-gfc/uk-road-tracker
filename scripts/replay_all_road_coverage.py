#!/usr/bin/env python3
"""Produce a local GeoJSON comparison from private Roadprints journey exports."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
from time import perf_counter

from all_road_coverage import (
    Fix, Ledger, Network, Span, local_point, longitude_scale,
    observed_coverage,
)

REFERENCE = Path(__file__).resolve().parents[1] / "gravesend-network-v1.json.gz"


def feature(coordinates, **properties):
    return {"type": "Feature", "geometry": {"type": "LineString", "coordinates": coordinates},
            "properties": properties}


def replay_legacy(network, excluded, exports, step_name=None):
    """Keep sparse driving matches in comparison layers, separate from coverage."""
    approximation = Ledger(network)
    legacy = []
    exact_edges = set()
    approximate_holds = Counter()
    for path in exports:
        raw = path.read_bytes()
        journey = json.loads(raw)["journey"]
        if journey["mode"] != "driving":
            continue
        journey_id = journey["journey_id"]
        spans = []
        for row in journey.get("processing_result", {}).get("road_geojson", {}).get("features", []):
            geometry = row["geometry"]
            name = row["properties"].get("name")
            if geometry["type"] != "LineString" or (step_name and name != step_name):
                continue
            coordinates = geometry["coordinates"]
            legacy.append(feature(coordinates, journey_id=journey_id, name=name, state="legacy"))
            local = [local_point(network.bounds, point) for point in coordinates]
            for i, (first, last) in enumerate(zip(local, local[1:])):
                exact_edges.add(tuple(sorted(map(tuple, coordinates[i:i + 2]))))
                candidates, reason = network.align(first, last)
                if reason:
                    approximate_holds[reason] += 1
                elif any(span.edge_id in excluded for span in candidates):
                    approximate_holds["out_of_scope_road"] += 1
                else:
                    spans.extend(candidates)
        approximation.replace(journey_id, hashlib.sha256(raw).hexdigest(), lambda: spans)
    return approximation, legacy, exact_edges, dict(approximate_holds)


def coverage_features(ledger, state, journey_ids=None):
    """Stitch contiguous same-way edges at degree-two nodes only.

    Each drawn path carries its constituent section IDs and intervals so
    selection can resolve the actual section, including within a stitched path.
    """
    bounds = ledger.network.bounds
    neighbours = defaultdict(list)
    paths = {}
    for edge_id in sorted(ledger.aggregate):
        edge = ledger.network.edges[edge_id]
        way = edge_id.rsplit(":", 2)[0]
        # Keep the stored edge orientation. Entry/exit below refer to positions
        # in this path, which can be reversed while stitching.
        for start, end in ledger.intervals(edge_id, journey_ids):
            span = Span(edge_id, start, end)
            nodes = (edge.nodes[0] if start == 0 else None,
                     edge.nodes[1] if end == edge.length_mm else None)
            paths[span] = edge, nodes
            for index, node in enumerate(nodes):
                if node and ledger.network.degree[node] == 2:
                    neighbours[way, node].append((span, index))

    def lonlat(point):
        return [round(bounds["west"] + point[0] / longitude_scale(bounds), 8),
                round(bounds["south"] + point[1] / 111_195.08, 8)]

    def linked(span, side):
        node = paths[span][1][side]
        way = span.edge_id.rsplit(":", 2)[0]
        adjacent = neighbours.get((way, node), ())
        if len(adjacent) == 2:
            return next((other for other in adjacent if other[0] != span), None)
        return None

    # Start open chains at their ends, then process any remaining cycles.
    starts = [(span, side) for span in paths for side in (0, 1) if not linked(span, side)]
    seen = set()
    for span, side in starts + [(span, 0) for span in paths]:
        if span in seen:
            continue
        coordinates = []
        sections = []
        while span not in seen:
            seen.add(span)
            edge, _ = paths[span]
            offsets = (span.start_mm, span.end_mm)
            if not coordinates:
                coordinates.append(lonlat(edge.at(offsets[side] / edge.length_mm)))
            coordinates.append(lonlat(edge.at(offsets[1 - side] / edge.length_mm)))
            sections.append({
                "id": span.edge_id, "start_mm": span.start_mm, "end_mm": span.end_mm,
            })
            successor = linked(span, 1 - side)
            if successor is None:
                break
            span, side = successor
        yield feature(coordinates, sections=sections, state=state,
                      name=ledger.network.edges[sections[0]["id"]].label)


def read_fixes(path, network, ledger, excluded):
    raw = path.read_bytes()
    document = json.loads(raw)
    if document["mode"] != "driving":
        raise ValueError("The original-fix format is scoped to Driving")
    fixes = [Fix(local_point(network.bounds, (row["lon"], row["lat"])),
                 row["time_ms"], row["accuracy_m"]) for row in document["fixes"]]
    holds = {}

    def project():
        spans, reasons = observed_coverage(network, fixes, excluded)
        holds.update(reasons)
        return spans

    ledger.replace(document["journey_id"], hashlib.sha256(raw).hexdigest(), project)
    return holds


def geometry_counts(features):
    return {"paths": len(features), "points": sum(len(row["geometry"]["coordinates"])
                                                for row in features)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exports", type=Path, nargs="*")
    parser.add_argument("--reference", type=Path, default=REFERENCE)
    parser.add_argument("--step-name", help="Limit legacy inspection to named source steps")
    parser.add_argument("--original-fixes", type=Path, action="append", default=[])
    parser.add_argument("--journey-id", action="append", help="Filter display by exact journey ID")
    parser.add_argument("--select", nargs=2, metavar=("SECTION_ID", "OFFSET_MM"),
                        help="Return contributing journey IDs at a displayed section offset")
    parser.add_argument("--geojson", type=Path, help="Output comparison (keep owner data private)")
    args = parser.parse_args()

    started = perf_counter()
    network, excluded = Network.gravesend(args.reference)
    network_ms = (perf_counter() - started) * 1000
    started = perf_counter()
    approximation, legacy, exact, legacy_holds = replay_legacy(
        network, excluded, args.exports, args.step_name)
    coverage = Ledger(network)
    observed_holds = Counter()
    for path in args.original_fixes:
        observed_holds.update(read_fixes(path, network, coverage, excluded))
    selection = set(args.journey_id) if args.journey_id else None
    provisional_view = list(coverage_features(approximation, "unresolved", selection))
    covered_view = list(coverage_features(coverage, "covered", selection))
    display_legacy = [row for row in legacy
                      if selection is None or row["properties"]["journey_id"] in selection]
    replay_ms = (perf_counter() - started) * 1000

    selected = None
    if args.select:
        edge_id, raw_offset = args.select
        offset = int(raw_offset)
        covered_ids = coverage.contributors_at(edge_id, offset, selection)
        unresolved_ids = approximation.contributors_at(edge_id, offset, selection)
        selected = {
            "state": ("covered" if covered_ids else "unresolved" if unresolved_ids else "uncovered"),
            "covered_journey_ids": covered_ids, "unresolved_journey_ids": unresolved_ids,
        }

    print(json.dumps({
        "dataset_revision": network.revision, "policy": coverage.cache_version[1],
        "eligible_sections": len(network.edges) - len(excluded),
        "eligible_network_m": round(sum(edge.length for edge in network.edges.values()
                                        if edge.id not in excluded), 1),
        "reference_bytes_gzip": args.reference.stat().st_size,
        "reference_bytes_uncompressed": len(gzip.decompress(args.reference.read_bytes())),
        "legacy_all_time": geometry_counts(legacy), "legacy_display": geometry_counts(display_legacy),
        "legacy_all_time_edges": geometry_counts(legacy)["points"] - len(legacy),
        "legacy_all_time_unique_exact_edges": len(exact),
        "legacy_geometry_holds": legacy_holds, "observed_holds": dict(observed_holds),
        "unresolved_display": geometry_counts(provisional_view),
        "unresolved_all_time_approximate_m": approximation.unique_mm / 1000,
        "covered_display": geometry_counts(covered_view),
        "covered_all_time_m": coverage.unique_mm / 1000, "selection": selected,
        "network_load_ms": round(network_ms, 1), "replay_and_render_ms": round(replay_ms, 1),
    }, indent=2))
    if args.geojson:
        args.geojson.write_text(json.dumps({
            "type": "FeatureCollection", "dataset_revision": network.revision,
            "attribution": "© OpenStreetMap contributors, ODbL (https://www.openstreetmap.org/copyright)",
            "features": display_legacy + provisional_view + covered_view,
        }), encoding="utf-8")


if __name__ == "__main__":
    main()
