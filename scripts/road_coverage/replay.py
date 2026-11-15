"""Read-only comparison of private build 288 exports against the Gravesend extract."""

import argparse
from collections import Counter
from itertools import pairwise
import json
import math
from pathlib import Path

from .ledger import Contribution, Ledger, assess, union
from .network import Fix, Network, POLICY, continuous_chain

EXTRACT = Path(__file__).with_name("gravesend-driving-v1.json.gz")


def road_geometry(journey, name):
    result = journey["processing_result"]
    return next(
        feature["geometry"]["coordinates"]
        for feature in result["road_geojson"]["features"]
        if feature["properties"]["name"] == name
    )


def build_comparison(network, exports):
    evidence, approximation = Ledger(network), Ledger(network)
    before = []
    details = []
    for export in exports:
        journey = export["journey"]
        journey_id = journey["journey_id"]
        coords = road_geometry(journey, "Ordnance Road")
        before.append(coords)
        # These Timeline exports do not include original accuracy or prove
        # the contemporaneous network. They cannot contribute covered intervals.
        fixes = [
            Fix(network.project(coord), time, None)
            for coord, time in zip(journey["route_geometry"]["coordinates"],
                                   journey["timeline_match_evidence"]["point_times_ms"], strict=True)
        ]
        result = assess(
            network, fixes, confirmed_driving=(
                journey["mode"], journey["transport_confirmation"]
            ) == ("driving", "confirmed"), reference_in_period=False,
        )
        evidence.replace(journey_id, result)
        visual = []
        missed_m = 0.0
        for first, second in pairwise(map(network.project, coords)):
            distance = math.dist(first, second)
            candidates = network.candidates(first, second)
            if continuous_chain(candidates, distance):
                visual.extend(candidate.span for candidate in candidates)
            else:
                missed_m += distance
        # The diagnostic layer is deliberately stored as unresolved, even
        # for pieces with a unique geometric alignment on today's network.
        approximation.replace(journey_id, Contribution(network.version, POLICY, (), union(visual), 0))
        details.append({
            "saved_points": len(fixes),
            "max_gap_seconds": max((b.time_ms - a.time_ms) / 1000 for a, b in pairwise(fixes)),
            "match_confidence": journey["processing_result"]["geojson"]["features"][0]["properties"]["confidence"],
            "held_pairs": result.held_pairs,
            "diagnostic_alignment_m": round(sum((s.end_mm - s.start_mm) / 1000 for s in union(visual)), 2),
            "diagnostic_unaligned_m": round(missed_m, 2),
        })

    baseline_pairs = [tuple(map(tuple, pair)) for path in before for pair in pairwise(path)]
    approximated = list(approximation.features(show_unresolved=True))
    strokes = list(approximation.strokes(show_unresolved=True))
    covered = list(evidence.features())
    lengths = Counter()
    for feature in approximated:
        props = feature["properties"]
        lengths[props["name"] or "(unnamed)"] += props["end_mm"] - props["start_mm"]

    metrics = {
        "dataset": network.version,
        "edges": len(network.edges),
        "occupied_grid_cells": len(network.grid),
        "original": {
            "paths": len(before),
            "vertices": sum(map(len, before)),
            "legs": len(baseline_pairs),
            "exact_duplicate_legs": len(baseline_pairs) - len(set(baseline_pairs)),
        },
        "diagnostic_only": {
            "atomic_paths": len(approximated),
            "atomic_vertices": 2 * len(approximated),
            "render_strokes": len(strokes),
            "render_vertices": sum(len(stroke["coordinates"]) for stroke in strokes),
            "unique_metres_by_reference_label": {label: mm / 1000 for label, mm in sorted(lengths.items())},
            "shared_metres": sum(f["properties"]["end_mm"] - f["properties"]["start_mm"]
                                 for f in approximated if len(f["properties"]["journeys"]) > 1) / 1000,
        },
        "coverage": {"paths": len(covered), "metres": 0 if not covered else sum(
            f["properties"]["end_mm"] - f["properties"]["start_mm"] for f in covered
        ) / 1000},
        "exports": details,
    }
    private_features = [
        {"type": "Feature", "properties": {"state": "original"}, "geometry": {
            "type": "LineString", "coordinates": coords,
        }}
        for coords in before
    ] + approximated + covered
    return metrics, {"type": "FeatureCollection", "features": private_features}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exports", nargs="+", type=Path)
    parser.add_argument("--private-geojson", type=Path, help="Optional personal geometry for local review")
    args = parser.parse_args()
    network = Network.load(EXTRACT)
    metrics, comparison = build_comparison(network, [json.loads(path.read_text()) for path in args.exports])
    print(json.dumps(metrics, indent=2))
    if args.private_geojson:
        with args.private_geojson.open("x", encoding="utf-8") as out:
            json.dump(comparison, out)


if __name__ == "__main__":
    main()
