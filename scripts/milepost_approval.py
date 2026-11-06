"""Deterministic Stage 9 review checkpoint. Holds are evidence gaps, not invalid posts."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'reference-data/millennium-mileposts'
ASSET = ROOT / 'android-prototype/app/src/main/assets/millennium-mileposts/catalogue.json'


def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False) + '\n').encode()


def build():
    candidates = [json.loads(line) for line in (DATA/'revision-1/catalogue.jsonl').read_text().splitlines()]
    reviewed = {
        'osm:node:33130857': ['coordinate_correspondence_unresolved', 'position_validity_not_established'],
        'osm:node:33190372': ['subject_precision_insufficient', 'position_validity_not_established'],
        'osm:node:35181782': ['coordinate_correspondence_unresolved', 'position_validity_not_established'],
        'osm:node:3826485563': ['subject_precision_insufficient', 'position_validity_not_established'],
    }
    decisions = [dict(candidate_id=r['osm_id'], status='held', reasons=reviewed.get(r['osm_id'],
        ['physical_identity_location_scope_not_verified']), reviewed_at='2026-11-06',
        review_kind='targeted_source_review' if r['osm_id'] in reviewed else 'inventory_evidence_gap') for r in candidates]
    # No claim that every candidate was individually investigated. No inferred approval.
    catalogue = dict(schema_version=1, revision=2, scope='approved-physical-mileposts',
        evaluator='android-archive-milepost-1/milepost-proximity-1', records=[],
        licence='ODbL-1.0', attribution='© OpenStreetMap contributors; https://www.openstreetmap.org/copyright',
        source_url='https://github.com/adamdbird-gfc/uk-road-tracker/tree/main/reference-data/millennium-mileposts')
    # Evaluator value is read from source in validation; blocked catalogue must not be activatable.
    return canonical(catalogue), b''.join(canonical(d) for d in decisions)


def verify():
    payload, decisions = build()
    assert ASSET.read_bytes() == payload
    assert (DATA/'revision-2/decisions.jsonl').read_bytes() == decisions
    receipt = json.loads((DATA/'revision-2/manifest.json').read_text())
    assert receipt['sha256'] == hashlib.sha256(payload).hexdigest()
    assert receipt['decisions_sha256'] == hashlib.sha256(decisions).hexdigest()
    assert receipt['counts'] == dict(approved=0, held=889, rejected=0)
    assert receipt['production_ready'] is False
    print('PASS: revision 2, approved=0 held=889 rejected=0; reproducible pins')


if __name__ == '__main__':
    verify()
