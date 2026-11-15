#!/usr/bin/env python3
"""Audit original Android Driving fixes against the tagged Gravesend reference."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from math import hypot, isfinite, nan
from pathlib import Path

from all_road_coverage import ALIGN_RADIUS_M, Fix, Ledger, Network, cells, local_point, observed_coverage
from replay_all_road_coverage import REFERENCE, coverage_features

TAGGED = REFERENCE.parent / "reference-data/all-road-coverage/gravesend-tagged-v1.json.gz"
TARGETS = ("Ordnance Road", "Albion Terrace", "Milton Place")
TARGET_PROXIMITY_M = 25


def number(value):
    return type(value) in (int, float) and isfinite(value)


def original_fix(bounds, row):
    # Keep rejected samples in sequence so a rejected fix never creates a new
    # adjacent pair. Source order is the app's original capture order.
    if not isinstance(row, list) or len(row) < 6 or not all(map(number, row[:4])):
        return Fix((nan, nan), 0, nan)
    lon, lat, accuracy, timestamp = row[:4]
    if not (-180 <= lon <= 180 and -90 <= lat <= 90):
        return Fix((nan, nan), 0, nan)
    if row[5] not in ("gps", "fused") or type(timestamp) is not int or timestamp <= 0:
        accuracy = nan
    return Fix(local_point(bounds, (lon, lat)), timestamp, accuracy)


def nearby_targets(network, point):
    names = set()
    for cell in cells(point, point, TARGET_PROXIMITY_M):
        for edge_id in network.grid.get(cell, ()):
            edge = network.edges[edge_id]
            if edge.label not in TARGETS or edge.label in names:
                continue
            dx, dy = edge.b[0] - edge.a[0], edge.b[1] - edge.a[1]
            fraction = ((point[0] - edge.a[0]) * dx + (point[1] - edge.a[1]) * dy) / (dx * dx + dy * dy)
            x, y = edge.at(max(0, min(1, fraction)))
            if hypot(point[0] - x, point[1] - y) <= TARGET_PROXIMITY_M:
                names.add(edge.label)
    return names


def audit(paths, network, excluded):
    exports = Counter()
    driving = {}
    for path in paths:
        document = json.loads(path.read_bytes())
        if (document.get("format") != "roadprints_journey_diagnostics"
                or document.get("schema_version") != 1
                or document.get("export_complete") is not True):
            exports["unsupported_or_incomplete"] += 1
            continue
        journey = document["journey"]
        source = journey.get("source", {}).get("type")
        if source != "android_activity_capture":
            exports["other_source"] += 1
            continue
        if journey.get("mode") != "driving" or journey.get("transport_confirmation") != "confirmed":
            exports["other_or_unconfirmed_mode"] += 1
            continue
        rows = journey.get("raw_capture_samples")
        if not isinstance(rows, list) or len(rows) < 2:
            exports["missing_original_samples"] += 1
            continue
        journey_id = journey["journey_id"]
        exports["native_driving"] += 1
        if journey_id in driving:
            previous = driving[journey_id][1]
            if previous != rows:
                raise ValueError("Original samples differ across exports of one journey; select one revision")
            continue
        driving[journey_id] = path.name, rows

    ledger = Ledger(network)
    captures = []
    for index, (name, rows) in enumerate(driving.values()):
        fixes = [original_fix(network.bounds, row) for row in rows]
        # Count only adjacent pairs touching the extract. A boundary crossing
        # is passed intact to the matcher and cannot add clipped coverage.
        inside = [network.inside_extract(fix.point, 0) for fix in fixes]
        target_fixes = Counter(
            target for fix, in_extract in zip(fixes, inside) if in_extract
            for target in nearby_targets(network, fix.point)
        )
        record = Counter()
        holds = Counter()
        spans = []
        for i, pair in enumerate(zip(fixes, fixes[1:])):
            if not (inside[i] or inside[i + 1]):
                continue
            record["pairs_touching_extract"] += 1
            if all(network.inside_extract(fix.point, ALIGN_RADIUS_M) for fix in pair):
                record["pairs_inside_base_margin"] += 1
            accepted, reasons = observed_coverage(network, pair, excluded)
            holds.update(reasons)
            if accepted:
                record["accepted_pairs_unlabelled"] += 1
                spans.extend(accepted)
        ledger.replace(str(index), str(index), lambda: spans)
        captures.append({
            "export": name,
            "raw_fixes": len(fixes),
            "fixes_inside_extract": sum(inside),
            **{key: record[key] for key in ("pairs_touching_extract", "pairs_inside_base_margin",
                                            "accepted_pairs_unlabelled")},
            "holds": dict(holds),
            "fixes_within_25m_of_target": dict.fromkeys(TARGETS, 0) | target_fixes,
            "accepted_targets_unlabelled": sorted({network.edges[span.edge_id].label for span in spans}
                                                  & set(TARGETS)),
        })
    features = list(coverage_features(ledger, "candidate"))
    return {
        "reference_revision": network.revision,
        "policy": ledger.cache_version[1],
        "exports": dict(exports),
        "unique_native_driving_captures": len(driving),
        "native_driving_captures": captures,
        "unlabelled_candidate_unique_m": ledger.unique_mm / 1000,
        "unlabelled_candidate_paths": len(features),
        "unlabelled_candidate_vertices": sum(len(feature["geometry"]["coordinates"]) for feature in features),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("exports", nargs="+", type=Path)
    parser.add_argument("--reference", type=Path, default=TAGGED,
                        help="Use a different reference for an unlabelled comparison")
    args = parser.parse_args()
    network, excluded = Network.gravesend(args.reference)
    print(json.dumps(audit(args.exports, network, excluded), indent=2))


if __name__ == "__main__":
    main()
