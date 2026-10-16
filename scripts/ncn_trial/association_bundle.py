"""Portable public reference associations; no journey data or database writes.

The caller supplies independently verified membership links. This format never
creates membership from proximity. The checksum is integrity, not a signature:
production distribution must pin it in a trusted release manifest.
"""
import gzip
import json

from matched_path_evidence import digest, source_geometry_digest
from .saved_paths import KnownPath
from .journey_replay import projector
from shapely.geometry import LineString


def reference_digest(reference):
    return digest([{'key': s.key, 'coordinates': list(s.line.coords),
                    'routes': list(s.routes), 'eligible': s.eligible}
                   for s in sorted(reference.sections.values(), key=lambda s: s.key)])


def build_bundle(paths, reference, projection_origin, provenance):
    """Serialize reviewed associations, bound to geometry and projection as well as version."""
    rows = []
    for path in sorted(paths, key=lambda p: p.way_id):
        if path.reference_version != reference.version:
            raise ValueError('Reference reconciliation required')
        rows.append({'way_id': path.way_id, 'coordinates': path.coordinates,
                     'source_geometry_sha256': source_geometry_digest(path.coordinates),
                     'section_keys': path.section_keys, 'modes': path.modes,
                     'target_geometry': path.target_geometry})
    payload = {'schema_version': 1, 'reference_version': reference.version,
               'reference_sha256': reference_digest(reference),
               'projection_origin': list(projection_origin),
               'provenance': provenance, 'paths': rows}
    raw = json.dumps(payload, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()
    # Validate the same contract used after a download before publishing bytes.
    read_bundle(raw, reference, projection_origin, digest(payload))
    return raw


def read_bundle(raw, reference, projection_origin, expected_sha256):
    """Validate a decoded bundle against a separately pinned canonical digest.

    No partial result is returned if any row is invalid. Empty regional bundles
    are valid and explicitly yield no associations, without proximity fallback.
    """
    try:
        payload = json.loads(raw)
        if not isinstance(expected_sha256, str) or len(expected_sha256) != 64 \
                or digest(payload) != expected_sha256:
            raise ValueError('Association checksum mismatch')
        if type(payload['schema_version']) is not int or payload['schema_version'] != 1:
            raise ValueError('Unsupported association schema')
        if payload['reference_version'] != reference.version \
                or payload['reference_sha256'] != reference_digest(reference):
            raise ValueError('Reference reconciliation required')
        if payload['projection_origin'] != list(projection_origin):
            raise ValueError('Reference projection changed')
        provenance = payload['provenance']
        if not isinstance(provenance, dict) or not all(
                isinstance(provenance.get(k), str) and provenance[k].strip()
                for k in ('attribution', 'licence', 'source_snapshot_sha256', 'membership_evidence_sha256')):
            raise ValueError('Public source provenance required')
        for field in ('source_snapshot_sha256', 'membership_evidence_sha256'):
            if len(provenance[field]) != 64 or any(c not in '0123456789abcdef' for c in provenance[field]):
                raise ValueError('Source evidence checksum required')
        if not isinstance(payload['paths'], list):
            raise ValueError('Association rows required')
        paths, seen = [], set()
        for row in payload['paths']:
            key, coordinates = row['way_id'], row['coordinates']
            if not isinstance(key, str) or not key.isdecimal() or int(key) <= 0 or key in seen:
                raise ValueError('Distinct positive OSM way identities required')
            seen.add(key)
            if source_geometry_digest(coordinates) != row['source_geometry_sha256'] \
                    or any(len(p) != 2 for p in coordinates):
                raise ValueError('Source geometry mismatch')
            sections, modes = row['section_keys'], row['modes']
            target_geometry = row.get('target_geometry', 'reviewed_reference')
            if target_geometry not in ('reviewed_reference', 'exact_source_spans'):
                raise ValueError('Unsupported target geometry contract')
            if not isinstance(sections, list) or not sections or any(not isinstance(s, str) for s in sections) \
                    or len(set(sections)) != len(sections) or any(s not in reference.sections for s in sections):
                raise ValueError('Known distinct target sections required')
            if not isinstance(modes, list) or not modes or any(m not in ('on_foot', 'cycling') for m in modes) \
                    or len(set(modes)) != len(modes):
                raise ValueError('Explicit supported activity modes required')
            if target_geometry == 'exact_source_spans':
                project = projector({'projection_origin': projection_origin})
                source_line = LineString([project(p) for p in coordinates])
                if any(not source_line.buffer(1e-6).covers(reference.sections[s].line) for s in sections):
                    raise ValueError('Exact source target is outside source way geometry')
            paths.append(KnownPath(key, tuple(tuple(p) for p in coordinates), tuple(sections),
                                   reference.version, tuple(modes), target_geometry))
        return tuple(paths)
    except (KeyError, TypeError, IndexError, AttributeError) as error:
        raise ValueError('Malformed association bundle') from error


def bundle_metrics(raw):
    """Reproducible transport size; compression does not change logical integrity."""
    payload = json.loads(raw)
    return {'paths': len(payload['paths']), 'json_bytes': len(raw),
            'gzip_bytes': len(gzip.compress(raw, mtime=0)), 'content_sha256': digest(payload)}


def main():
    import argparse
    from pathlib import Path
    from .fixtures import ROOT, load_reference
    from .saved_paths import fixture_paths
    parser = argparse.ArgumentParser(description='Build bounded Route 1 public association trial bundle')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    reference, metadata = load_reference()
    source = json.loads((ROOT / 'tests/fixtures/ncn-route1-context/source.json').read_text())
    provenance = {'attribution': source['attribution'], 'licence': source['licence'],
                  'source_snapshot_sha256': source['fixture_sha256'],
                  'membership_evidence_sha256': digest(source['memberships'])}
    raw = build_bundle(fixture_paths(reference), reference, metadata['projection_origin'], provenance)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'associations.json.gz').write_bytes(gzip.compress(raw, mtime=0))
    manifest = {**bundle_metrics(raw), 'scope': 'bounded-route1-trial',
                'reference_version': reference.version, 'provenance': provenance,
                'compressed_sha256': __import__('hashlib').sha256(gzip.compress(raw, mtime=0)).hexdigest()}
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
