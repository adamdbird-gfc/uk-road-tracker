"""Read-only diagnostic replay. Proximity metrics never become collection credit.

Run locally with private exports; never commit those exports or generated outputs.
Android sample order: longitude, latitude, accuracy metres, epoch milliseconds,
speed m/s (WalkingJourneyPreparation.sampleJson). No time/accuracy is fabricated.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union

from .coverage import Fix, MODES, TrialLedger
from .fixtures import load_reference


def finite_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def projector(metadata):
    lng0, lat0 = metadata['projection_origin']
    def project(coordinate):
        if not isinstance(coordinate, (list, tuple)) or len(coordinate) < 2:
            raise ValueError('Missing coordinate')
        lng, lat = coordinate[:2]
        if not all(finite_number(v) for v in (lng, lat)) or not -180 <= lng <= 180 or not -90 <= lat <= 90:
            raise ValueError('Invalid WGS84 coordinate')
        return (6371008.8 * math.radians(lng - lng0) * math.cos(math.radians(lat0)),
                6371008.8 * math.radians(lat - lat0))
    return project


def recorded_fixes(journey, project):
    field = next((name for name in ('raw_capture_samples', 'capture_route_samples')
                  if isinstance(journey.get(name), list) and journey[name]), None)
    if field is None:
        return [], {'sample_source': None, 'sample_count': 0, 'invalid_samples': 0}
    rows, fixes, invalid = journey[field], [], 0
    for row in rows:
        try:
            if not isinstance(row, list) or len(row) < 4 or not all(finite_number(v) for v in row[:4]):
                raise ValueError('Incomplete timed sample')
            x, y = project(row)
            if row[2] <= 0 or row[3] <= 0:
                raise ValueError('Invalid accuracy or timestamp')
            fixes.append(Fix(x, y, row[3] / 1000, row[2]))
        except (ValueError, TypeError):
            # Preserve the break. Dropping a malformed sample could bridge a gap.
            fixes.append(Fix(math.nan, math.nan, math.nan, math.nan))
            invalid += 1
    return fixes, {'sample_source': field, 'sample_count': len(rows), 'invalid_samples': invalid}


def matched_linework(journey, project):
    result = journey.get('processing_result') or {}
    features = (result.get('geojson') or {}).get('features') or []
    corrections = journey.get('journey_corrections') or {}
    removed = set(corrections.get('removed_matched_segments') or [])
    if not removed and (corrections.get('removed_road_ids') or corrections.get('removed_road_labels')):
        return None, 'unsupported_road_only_correction'
    edges, index = [], 0
    try:
        for feature in features:
            geometry = feature.get('geometry') or {}
            coordinates = geometry.get('coordinates') or []
            lines = [coordinates] if geometry.get('type') == 'LineString' else coordinates if geometry.get('type') == 'MultiLineString' else []
            for line in lines:
                for a, b in zip(line, line[1:]):
                    if index not in removed:
                        edge = LineString([project(a), project(b)])
                        if edge.length > 0:
                            edges.append(edge)
                    index += 1
    except (ValueError, TypeError, AttributeError):
        return None, 'invalid_matched_geometry'
    return (unary_union(edges), 'stored_match_after_corrections') if edges else (None, 'no_stored_match')


def replay(export, reference, metadata, mode_override=None):
    if export.get('format') != 'roadprints_journey_diagnostics' or export.get('schema_version') != 1 \
            or export.get('export_complete') is not True or not isinstance(export.get('journey'), dict):
        raise ValueError('Complete schema-1 Roadprints journey diagnostic required')
    journey = export['journey']
    declared = str(journey.get('mode', 'unknown')).strip().lower()
    mode = declared if mode_override is None else mode_override
    if mode not in set(MODES) | {'driving', 'bus', 'train', 'plane', 'ferry', 'unknown'}:
        raise ValueError('Unsupported trial mode')
    project = projector(metadata)
    fixes, quality = recorded_fixes(journey, project)
    evidence = reference.observe(mode, fixes)
    ledger = TrialLedger(reference)
    ledger.put('private-replay', evidence)
    linework, match_status = matched_linework(journey, project)
    network = unary_union([s.line for s in reference.sections.values() if s.eligible])
    overlap = {str(radius): round(linework.intersection(network.buffer(radius)).length, 3)
               if linework is not None else 0 for radius in (3, 8, 15)}
    flags = []
    if mode not in MODES:
        flags.append('ineligible_mode')
    if not fixes:
        flags.append('missing_per_point_time_and_accuracy')
    if quality['invalid_samples']:
        flags.append('invalid_samples_preserved_as_breaks')
    if mode_override is not None:
        flags.append('synthetic_mode_only_geometry_time_accuracy_unchanged')
    # An exported reconstructed route is not independent ground truth. This
    # adapter has no verified path-membership or corridor conflation input yet.
    flags.append('matched_path_identity_and_competing_paths_unverified')
    return {'declared_mode': declared, 'trial_mode': mode, 'synthetic_mode_override': mode_override is not None,
            'reference_version': reference.version, 'fixture_bounds': metadata['bounds'],
            'quality': quality, 'match_status': match_status,
            'diagnostic_candidate_coverage': ledger.summary(),
            'observation_decisions': dict(Counter(evidence.decisions)),
            'matched_geometry_within_buffer_m': overlap,
            'verified_collection_credit_m': 0,
            'status': 'ineligible' if mode not in MODES else 'pending_evidence',
            'flags': flags, 'production_progress_written': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exports', nargs='+', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--mode-variants', action='store_true', help='Also replay walking/cycling/driving labels without changing evidence')
    args = parser.parse_args()
    reference, metadata = load_reference()
    reports = []
    for path in args.exports:
        raw = path.read_bytes()
        value = json.loads(raw)
        report = {'input_filename': path.name, 'input_sha256': hashlib.sha256(raw).hexdigest(),
                  'recorded': replay(value, reference, metadata)}
        if args.mode_variants:
            report['synthetic_variants'] = {mode: replay(value, reference, metadata, mode) for mode in ('walking', 'cycling', 'driving')}
        reports.append(report)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'scope': 'local_private_diagnostic_only', 'reports': reports}, indent=2, allow_nan=False) + '\n')
    print(f'Wrote {len(reports)} private diagnostic reports; no progress awarded')


if __name__ == '__main__':
    main()
