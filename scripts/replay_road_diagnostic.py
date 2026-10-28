"""Replay prepared road evidence locally or through the ordinary Roadprints API.

Personal exports and response fixtures belong outside the public repository.
This never writes a device record or contacts the external router directly.
"""
import argparse
import json
import math
from pathlib import Path
from types import SimpleNamespace
import sys
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from road_distance_evidence import observation_distance


def prepared_request(export):
    journey = export['journey']
    coordinates = journey['route_geometry']['coordinates']
    samples = journey['capture_route_samples']
    assert len(coordinates) == len(samples)
    assert all(p == s[:2] for p, s in zip(coordinates, samples))
    attempt = journey['last_match_attempt']
    preparation = attempt['road_preparation']
    assert preparation['version'] == 2, 'Use a matching preparation implementation for newer versions'
    omitted = preparation.get('uncorroborated_fix_indices', []) + preparation.get('redundant_point_indices', [])
    assert len(omitted) == len(set(omitted))
    assert all(isinstance(i, int) and 0 < i < len(coordinates)-1 for i in omitted)
    omitted = set(omitted)
    points = [dict(lat=p[1], lng=p[0]) for i, p in enumerate(coordinates) if i not in omitted]
    assert len(points) == attempt['submitted_points']
    distance = observation_distance([SimpleNamespace(**p) for p in points])
    assert abs(distance-attempt['submitted_geometry_distance_m']) < .02
    return dict(points=points, road_recovery=True)


def verify(result, request):
    count = len(request['points'])
    assert result['input_points'] == count
    matched, unmatched = result['matched_point_indices'], result['unmatched_point_indices']
    assert len(matched) == result['matched_tracepoints'] and len(matched) >= 2
    assert sorted(matched+unmatched) == list(range(count)), 'Incomplete or duplicated point accounting'
    failures = result['failed_sections']
    for failure in failures:
        assert 0 <= failure['start_point_index'] < failure['end_point_index'] < count
    distance = observation_distance([SimpleNamespace(**p) for p in request['points']])
    matched_distance = result['matched_distance_m']
    assert math.isfinite(matched_distance) and 0 < matched_distance <= max(distance*1.4, distance+300)
    assert result['matched_distance_is_deduplicated']
    partial = bool(unmatched or failures)
    if partial:
        # Existing Android captured-partial acceptance remains stricter.
        assert matched_distance <= max(distance*1.2, distance+300)
    assert result['geojson']['features']
    assert result['road_distance_evidence_version'] == 1
    return dict(input_points=count, matched_points=len(matched), unmatched_points=len(unmatched),
                failed_intervals=len(failures), observation_distance_m=distance,
                matched_distance_m=matched_distance, existing_android_accepts=True, partial=partial)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('export')
    parser.add_argument('--response', help='Check a preserved response offline')
    parser.add_argument('--live', help='Explicit ordinary backend base URL; sends only prepared match points')
    parser.add_argument('--repeat', type=int, default=1)
    parser.add_argument('--output-prefix', required=True)
    args = parser.parse_args()
    assert bool(args.response) != bool(args.live), 'Choose a preserved response or a live backend'
    original = Path(args.export).read_bytes()
    request = prepared_request(json.loads(original))
    prefix = Path(args.output_prefix)
    prefix.parent.mkdir(parents=True, exist_ok=True)
    prefix.with_suffix('.request.json').write_text(json.dumps(request))
    results = []
    summaries = []
    for repeat in range(args.repeat):
        if args.live:
            req = urllib.request.Request(args.live.rstrip('/')+'/match', data=json.dumps(request).encode(),
                                         headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(req, timeout=60) as response:
                assert response.status == 200
                result = json.load(response)
        else:
            result = json.loads(Path(args.response).read_text())
        summaries.append(verify(result, request))
        if results:
            assert results[0] == result, 'Repeated matching changed geometry or accounting'
        results.append(result)
        prefix.with_name(prefix.name+f'.response-{repeat+1}.json').write_text(json.dumps(result))
    assert Path(args.export).read_bytes() == original, 'The export must remain unchanged'
    prefix.with_suffix('.summary.json').write_text(json.dumps(summaries, indent=2))
    print(json.dumps(summaries, indent=2))


if __name__ == '__main__':
    main()
