"""Run the bounded Route1 development trial; write evidence without any network IO."""
import argparse
from collections import Counter
import gzip
import json
from pathlib import Path
import time

from shapely.geometry import LineString

from .coverage import Fix, Section, TrialLedger, TrialReference
from .fixtures import fixes_along, load_reference


def main(output):
    output.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    reference, metadata = load_reference()
    load_seconds = time.perf_counter() - started
    road, path = reference.sections['FID:954:0'], reference.sections['FID:16404:0']
    cases = []
    def record(name, target, observations):
        ledger = TrialLedger(target)
        decisions = Counter()
        for activity_id, mode, fixes in observations:
            evidence = target.observe(mode, fixes)
            decisions.update(evidence.decisions)
            ledger.put(activity_id, evidence)
        row = {'name': name, 'observations_are_synthetic': True,
               'coverage': ledger.summary(), 'fix_decisions': dict(decisions)}
        cases.append(row)
        return ledger
    record('cycling_on_road', reference, [('ride', 'cycling', fixes_along(road.line))])
    record('walking_traffic_free', reference, [('walk', 'walking', fixes_along(path.line, period=8))])
    record('repeat_and_reverse', reference,
           [('ride', 'cycling', fixes_along(road.line)), ('repeat', 'cycling', fixes_along(road.line)),
            ('reverse', 'cycling', fixes_along(road.line, .8, .1))])
    record('mixed_modes_with_overlap', reference,
           [('ride', 'cycling', fixes_along(road.line, .1, .7)),
            ('walk', 'walking', fixes_along(road.line, .4, .9))])
    record('driven_ncn_road', reference, [('drive', 'driving', fixes_along(road.line))])
    record('poor_accuracy_on_reference', reference,
           [('uncertain', 'cycling', fixes_along(road.line, accuracy=25))])
    missing = fixes_along(road.line)
    record('missing_moving_fixes', reference, [('gap', 'cycling', missing[:8] + missing[20:])])
    alias = Section('test-overlap', LineString(list(road.line.coords)[::-1]), ('TEST:overlap',))
    record('synthetic_shared_route_identity', TrialReference(reference.version, [road, alias]),
           [('ride', 'cycling', fixes_along(road.line))])
    # Known straight-line alternatives isolate ambiguity from curvature effects.
    section = Section('route', LineString([(0, 0), (200, 0)]), ('TEST:NCN',))
    parallel = LineString([(0, 8), (200, 8)])
    known = TrialReference('synthetic-known-alternatives', [section], [parallel])
    for label, y in [('known_parallel_path', 8), ('ambiguous_midway', 4)]:
        record(label, known, [('ride', 'cycling', [Fix(x, y, i * 2) for i, x in enumerate(range(20, 181, 8))])])
    # A deliberate negative control demonstrates that proximity-only data is
    # insufficient. This positive credit is a recorded LIMITATION, not acceptance.
    unknown = TrialReference('synthetic-missing-alternative', [section])
    negative = TrialLedger(unknown)
    negative.put('nearby', unknown.observe('cycling', [Fix(x, 4, i * 2) for i, x in enumerate(range(20, 181, 8))]))
    assert negative.summary()['combined_m'] > 0
    fixture = Path(__file__).resolve().parents[2] / 'tests/fixtures/ncn-route1/reference.geojson'
    raw = fixture.read_bytes()
    report = {'status': 'local_development_trial_complete', 'runtime_integrated': False,
              'field_gps_validated': False, 'synthetic_observations': True,
              'source': metadata, 'reference_sections': len(reference.sections),
              'physical_edges': len(reference.edges),
              'fixture_bytes': len(raw), 'fixture_gzip_bytes': len(gzip.compress(raw, mtime=0)),
              'load_and_index_seconds_local': round(load_seconds, 6),
              'full_trial_seconds_local': round(time.perf_counter() - started, 6),
              'selected_source_features': {'on_road': {'fid': 954, 'length_m_local': road.line.length},
                                           'traffic_free': {'fid': 16404, 'length_m_local': path.line.length}},
              'thresholds_are_experimental': True,
              'thresholds': {'snap_m': reference.MAX_SNAP_M, 'accuracy_m': reference.MAX_ACCURACY_M,
                             'gap_seconds': reference.MAX_GAP_S, 'leg_m': reference.MAX_LEG_M},
              'cases': cases,
              'negative_control': {'name': 'nearby_path_without_competitor_context',
                                   'known_limitation': True, 'incorrect_credit_m': negative.summary()['combined_m'],
                                   'conclusion': 'Do not use NCN proximity alone for production credit. Require validated path evidence and competing-path context.'},
              'next_gates': ['Real road/path alignment and matched-geometry evidence',
                             'NI/Wales/Scotland source variants',
                             'Map design review with Adam',
                             'Offline/device memory and source-update reconciliation',
                             'Marker permission and disc-to-verse catalogue']}
    (output / 'route1-trial.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ['cases', 'source']}, indent=2))
    print('Cases:', [(r['name'], round(r['coverage']['combined_m'], 3)) for r in cases])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args().output)
