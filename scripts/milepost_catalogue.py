"""Offline candidate compiler and bounded reader. No network or award integration.

Schema 1 intentionally supports candidate inventory only. Verification/eligibility
requires a separately reviewed schema/evaluator change, never a source-tag refresh.
"""
import argparse
from collections import Counter, OrderedDict
from datetime import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import re
import tempfile

ROOT = Path(__file__).resolve().parents[1] / 'reference-data/millennium-mileposts'
DESIGNS = {'mills', 'rowe', 'mccoll', 'dudgeon'}
MAX_SOURCE = 8 * 1024 * 1024
MAX_CATALOGUE = 16 * 1024 * 1024
MAX_ROW = 32768
MAX_RECORDS = 10000
MAX_MANIFEST = 32768
HEX = re.compile(r'[a-f0-9]{64}')
ID = re.compile(r'osm:(node|way|relation):[1-9][0-9]*')


def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False,
                       allow_nan=False) + '\n').encode('utf-8')


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for b in iter(lambda: f.read(65536), b''):
            h.update(b)
    return h.hexdigest()


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key: ' + key)
            result[key] = value
        return result
    def bad(value):
        raise ValueError('Non-finite JSON value: ' + value)
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=bad)


def read_json(path, limit):
    with Path(path).open('rb') as f:
        raw = f.read(limit + 1)
    if len(raw) > limit:
        raise ValueError('JSON exceeds size limit')
    return strict_json(raw)


def timestamp(value):
    if not isinstance(value, str) or not value.endswith('Z'):
        raise ValueError('Expected UTC timestamp')
    datetime.fromisoformat(value.replace('Z', '+00:00'))


def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError('Invalid integer')


def coordinate(value, low, high):
    if type(value) not in (float, int) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError('Invalid coordinate')


def validate_manifest(m):
    keys = {'schema_version', 'revision', 'scope', 'generated_at', 'source', 'sha256',
            'bytes', 'record_count', 'eligible_count', 'validated_physical_count',
            'design_counts', 'filename', 'attribution', 'supported_reader_versions'}
    if not isinstance(m, dict) or set(m) != keys:
        raise ValueError('Manifest fields differ')
    integer(m['schema_version'], 1, 1)
    integer(m['revision'], 1, 2147483647)
    integer(m['bytes'], 1, MAX_CATALOGUE)
    integer(m['record_count'], 1, MAX_RECORDS)
    integer(m['eligible_count'], 0, 0)
    integer(m['validated_physical_count'], 0, 0)
    if m['scope'] != 'candidate-inventory-only' or m['supported_reader_versions'] != [1]:
        raise ValueError('Unsupported catalogue scope or reader')
    if not isinstance(m['sha256'], str) or not HEX.fullmatch(m['sha256']):
        raise ValueError('Invalid catalogue digest')
    if m['filename'] != f"collections/published/millennium-mileposts/{m['sha256']}/catalogue.jsonl":
        raise ValueError('Invalid immutable path')
    timestamp(m['generated_at'])
    source = m['source']
    expected_source = {'sha256', 'bytes', 'snapshot_at', 'extracted_at', 'query', 'url',
                       'licence', 'archive_sha256', 'archive_entry'}
    if not isinstance(source, dict) or set(source) != expected_source:
        raise ValueError('Source provenance missing')
    for key in ('sha256', 'archive_sha256'):
        if not isinstance(source[key], str) or not HEX.fullmatch(source[key]):
            raise ValueError('Invalid source hash')
    integer(source['bytes'], 1, MAX_SOURCE)
    timestamp(source['snapshot_at'])
    if source['extracted_at'] is not None:
        timestamp(source['extracted_at'])
    for key in ('query', 'url', 'licence', 'archive_entry'):
        if not isinstance(source[key], str) or not source[key]:
            raise ValueError('Source provenance empty')
    if not isinstance(m['attribution'], str) or not m['attribution']:
        raise ValueError('Attribution missing')
    counts = m['design_counts']
    if not isinstance(counts, dict) or not set(counts) <= DESIGNS | {'unknown'}:
        raise ValueError('Unsupported design count')
    for count in counts.values():
        integer(count, 1, MAX_RECORDS)
    if sum(counts.values()) != m['record_count']:
        raise ValueError('Design totals differ')


def validate_record(r, m):
    keys = {'osm_id', 'osm_version', 'latitude', 'longitude', 'source_design_tag',
            'design', 'designer', 'designer_status', 'sustrans_ref', 'source_tags',
            'source_sha256', 'source_snapshot_at', 'revision', 'physical_marker_id',
            'identity_status', 'location_status', 'validation_status', 'eligible',
            'eligibility_reason', 'boundary_status', 'boundary_exception',
            'position_valid_from', 'position_valid_to'}
    if not isinstance(r, dict) or set(r) != keys:
        raise ValueError('Record fields differ')
    if not isinstance(r['osm_id'], str) or not ID.fullmatch(r['osm_id']):
        raise ValueError('Invalid typed OSM identity')
    coordinate(r['latitude'], -90, 90)
    coordinate(r['longitude'], -180, 180)
    if r['osm_version'] is not None:
        integer(r['osm_version'], 1, 2147483647)
    tags = r['source_tags']
    if not isinstance(tags, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in tags.items()):
        raise ValueError('Invalid original tags')
    tag = tags.get('ncn_milepost')
    if not isinstance(tag, str) or r['source_design_tag'] != tag:
        raise ValueError('Original design tag differs')
    design = tag if tag in DESIGNS else 'unknown'
    if (r['design'], r['designer'], r['designer_status']) != (design, design if design != 'unknown' else None,
                                                               'provisional_osm_design_tag' if design != 'unknown' else 'unsupported'):
        raise ValueError('Unsupported designer attribution')
    if r['sustrans_ref'] != tags.get('sustrans_ref'):
        raise ValueError('MP reference differs')
    if type(r['revision']) is not int or r['revision'] != m['revision'] or r['source_sha256'] != m['source']['sha256'] or r['source_snapshot_at'] != m['source']['snapshot_at']:
        raise ValueError('Record provenance differs')
    required = {'physical_marker_id': None, 'identity_status': 'unverified',
                'location_status': 'unverified', 'validation_status': 'source_candidate',
                'eligible': False, 'eligibility_reason': 'physical_identity_location_uk_scope_not_verified',
                'boundary_status': 'unreviewed', 'boundary_exception': None,
                'position_valid_from': None, 'position_valid_to': None}
    if any(r[key] != value or type(r[key]) is not type(value) for key, value in required.items()):
        raise ValueError('Schema 1 cannot approve physical markers or awards')


def records(path, manifest):
    """Stream one bounded row at a time, validating the full catalogue before use."""
    previous = ''; counts = Counter(); count = 0
    with Path(path).open('rb') as f:
        while True:
            line = f.readline(MAX_ROW + 1)
            if not line:
                break
            if len(line) > MAX_ROW:
                raise ValueError('Catalogue row exceeds limit')
            r = strict_json(line)
            validate_record(r, manifest)
            if canonical(r) != line or r['osm_id'] <= previous:
                raise ValueError('Non-canonical ordering or duplicate OSM identity')
            previous = r['osm_id']; counts[r['design']] += 1; count += 1
            if count > MAX_RECORDS:
                raise ValueError('Too many records')
            yield r
    if count != manifest['record_count'] or dict(counts) != manifest['design_counts']:
        raise ValueError('Catalogue counts differ')


def verify(path, manifest):
    validate_manifest(manifest)
    path = Path(path)
    if path.stat().st_size != manifest['bytes'] or digest(path) != manifest['sha256']:
        raise ValueError('Catalogue integrity differs')
    for _ in records(path, manifest):
        pass


def compile_inventory(source, provenance, revision, generated_at, output):
    """Compile exact pinned OSM bytes; source refresh requires a new reviewed pin."""
    integer(revision, 1, 2147483647); timestamp(generated_at)
    source = Path(source)
    if source.stat().st_size != provenance['bytes'] or digest(source) != provenance['sha256']:
        raise ValueError('Original OSM source integrity differs')
    root = read_json(source, MAX_SOURCE)
    if root['osm3s']['timestamp_osm_base'] != provenance['snapshot_at']:
        raise ValueError('Source timestamp differs')
    elements = root['elements']
    if not isinstance(elements, list) or not 1 <= len(elements) <= MAX_RECORDS:
        raise ValueError('Invalid source count')
    rows = {}; counts = Counter()
    for e in elements:
        integer(e['id'], 1, 2**63 - 1)
        if e['type'] not in ('node', 'way', 'relation'):
            raise ValueError('Unsupported OSM type')
        identity = f"osm:{e['type']}:{e['id']}"
        if identity in rows:
            raise ValueError('Duplicate OSM identity')
        tags = e['tags']; tag = tags['ncn_milepost']
        design = tag if tag in DESIGNS else 'unknown'
        point = e if e['type'] == 'node' else e['center']
        rows[identity] = {'osm_id': identity, 'osm_version': e.get('version'),
            'latitude': point['lat'], 'longitude': point['lon'], 'source_design_tag': tag,
            'design': design, 'designer': design if design != 'unknown' else None,
            'designer_status': 'provisional_osm_design_tag' if design != 'unknown' else 'unsupported',
            'sustrans_ref': tags.get('sustrans_ref'), 'source_tags': tags,
            'source_sha256': provenance['sha256'], 'source_snapshot_at': provenance['snapshot_at'],
            'revision': revision, 'physical_marker_id': None, 'identity_status': 'unverified',
            'location_status': 'unverified', 'validation_status': 'source_candidate',
            'eligible': False, 'eligibility_reason': 'physical_identity_location_uk_scope_not_verified',
            'boundary_status': 'unreviewed', 'boundary_exception': None,
            'position_valid_from': None, 'position_valid_to': None}
        counts[design] += 1
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    # Do not partially replace an already-reviewed generation on validation failure.
    with tempfile.TemporaryDirectory(dir=output) as temporary:
        path = Path(temporary) / 'catalogue.jsonl'
        with path.open('wb') as f:
            for key in sorted(rows):
                raw = canonical(rows[key])
                if len(raw) > MAX_ROW:
                    raise ValueError('Source record too large')
                f.write(raw)
        m = {'schema_version': 1, 'revision': revision, 'scope': 'candidate-inventory-only',
             'generated_at': generated_at, 'source': provenance, 'sha256': digest(path),
             'bytes': path.stat().st_size, 'record_count': len(rows), 'eligible_count': 0,
             'validated_physical_count': 0, 'design_counts': dict(counts),
             'filename': f'collections/published/millennium-mileposts/{digest(path)}/catalogue.jsonl',
             'attribution': '© OpenStreetMap contributors; ODbL-1.0; https://www.openstreetmap.org/copyright',
             'supported_reader_versions': [1]}
        verify(path, m)
        manifest = canonical(m)
        for name, raw in [('catalogue.jsonl', path.read_bytes()), ('manifest.json', manifest)]:
            target = output / name
            if target.exists() and target.read_bytes() != raw:
                raise ValueError('Refusing to overwrite an immutable generation')
        (output / 'catalogue.jsonl').write_bytes(path.read_bytes())
        (output / 'manifest.json').write_bytes(manifest)
    return m


class Catalogue:
    """Offline tooling reader. LRU <=32 rows; no Android startup/download hook.

    Every open verifies bytes and all records. Per-ID lookups scan the immutable
    file without retaining a national object graph. No achievement API exists.
    """
    def __init__(self, path, manifest):
        verify(path, manifest)
        self.path = Path(path); self.manifest = strict_json(canonical(manifest))
        self.cache = OrderedDict()

    def __iter__(self):
        # Revalidate integrity at each scan; a corrupt generation never supplies rows.
        verify(self.path, self.manifest)
        return records(self.path, self.manifest)

    def get(self, osm_id):
        verify(self.path, self.manifest)
        if osm_id in self.cache:
            self.cache.move_to_end(osm_id)
            return strict_json(self.cache[osm_id])
        for row in self:
            if row['osm_id'] == osm_id:
                self.cache[osm_id] = canonical(row)
                if len(self.cache) > 32:
                    self.cache.popitem(last=False)
                return row
        return None


def atomic_json(path, value):
    path = Path(path)
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.pointer-')
    try:
        with os.fdopen(fd, 'wb') as f:
            f.write(canonical(value)); f.flush(); os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


class CatalogueCache:
    """Single-writer local cache, active+previous pointer committed atomically.

    Schema 1 has no approved assignments. Rejecting later schemas prevents a
    refresh from silently reassigning future approved physical IDs/designers.
    No automatic download, publication, deletion or entitlement operation.
    """
    def __init__(self, directory):
        self.directory = Path(directory); self.directory.mkdir(parents=True, exist_ok=True)

    def _pointer(self):
        path = self.directory / 'state.json'
        if not path.exists():
            return {'active': None, 'previous': None, 'max_revision': 0}
        p = read_json(path, MAX_MANIFEST)
        if not isinstance(p, dict) or set(p) != {'active', 'previous', 'max_revision'}:
            raise ValueError('Invalid cache pointer')
        integer(p['max_revision'], 0, 2147483647)
        for h in (p['active'], p['previous']):
            if h is not None and (not isinstance(h, str) or not HEX.fullmatch(h)):
                raise ValueError('Invalid cache generation')
        return p

    def _open(self, h):
        d = self.directory / h
        m = read_json(d / 'manifest.json', MAX_MANIFEST)
        if m['sha256'] != h:
            raise ValueError('Generation pointer differs')
        return Catalogue(d / 'catalogue.jsonl', m)

    def open(self):
        p = self._pointer()
        for h in (p['active'], p['previous']):
            if h is not None:
                try:
                    return self._open(h)
                except (ValueError, OSError, KeyError, TypeError):
                    pass
        raise ValueError('No compatible verified milepost catalogue')

    def install(self, path, manifest):
        verify(path, manifest)  # No pointer mutation until all checks pass.
        p = self._pointer()
        old = None
        try:
            old = self.open()
        except ValueError:
            if p['active'] is not None or p['previous'] is not None:
                raise ValueError('Recover corrupt cache explicitly before installation')
        if manifest['revision'] < p['max_revision']:
            raise ValueError('Revision decrease below last activated revision')
        if old:
            old_m = old.manifest
            if manifest['revision'] < old_m['revision'] or (manifest['revision'] == old_m['revision'] and manifest != old_m):
                raise ValueError('Revision decrease or reuse')
            if manifest == old_m and manifest['revision'] == p['max_revision']:
                return old
            if manifest['revision'] == p['max_revision']:
                raise ValueError('Revision reuse after fallback')
        h = manifest['sha256']; target = self.directory / h
        if target.exists():
            self._open(h)
            if read_json(target / 'manifest.json', MAX_MANIFEST) != manifest:
                raise ValueError('Immutable generation metadata differs')
        else:
            with tempfile.TemporaryDirectory(dir=self.directory) as tmp:
                d = Path(tmp) / h; d.mkdir()
                with Path(path).open('rb') as source, (d / 'catalogue.jsonl').open('wb') as out:
                    import shutil
                    shutil.copyfileobj(source, out, 65536); out.flush(); os.fsync(out.fileno())
                atomic_json(d / 'manifest.json', manifest)
                verify(d / 'catalogue.jsonl', manifest)
                os.rename(d, target)
        atomic_json(self.directory / 'state.json', {'active': h, 'previous': old.manifest['sha256'] if old else None, 'max_revision': manifest['revision']})
        return self._open(h)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'revision-1')
    args = parser.parse_args()
    m = compile_inventory(ROOT / 'source/osm-mileposts.json',
                          read_json(ROOT / 'source/provenance.json', MAX_MANIFEST),
                          1, '2026-11-05T20:01:51Z', args.output)
    print(json.dumps(m, indent=2))


if __name__ == '__main__':
    main()
