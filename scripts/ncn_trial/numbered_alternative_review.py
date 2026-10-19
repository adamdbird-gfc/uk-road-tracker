"""Explicit admission of a reviewed, officially numbered alternative as itself.

This is not a general bypass of the Main Route gate, a cross-route proxy, or
permission to match nearby ways. Each review pins exact official feature bytes,
source roots, route identity and completion policy into the resulting version.
"""
import hashlib
import json
import gzip
from pathlib import Path

from matched_path_evidence import digest
from .numbered_reconciliation import uk_metadata_anchor


def validate_review(raw, expected_sha256, key, official, network_sha256, roots, index):
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise ValueError('Numbered alternative review checksum differs')
    review = json.loads(raw)
    if (review.get('schema_version') != 1 or review.get('route_key') != key
            or not key.startswith('NCN:')
            or review.get('policy') != 'official_numbered_alternative_as_own_route'
            or review.get('cross_route_credit') is not False
            or review.get('whole_published_route_ready') is not False
            or review.get('official_network_sha256') != network_sha256
            or review.get('official_features_sha256') != digest(official)
            or review.get('source_relation_ids') != sorted(roots)
            or not roots or len(set(roots)) != len(roots)
            or not review.get('identity_evidence_urls')):
        raise ValueError('Explicit pinned numbered alternative scope required')
    ids = review.get('approved_feature_ids')
    if (not isinstance(ids, list) or not ids or len(set(ids)) != len(ids)
            or any(not isinstance(i, int) or isinstance(i, bool) for i in ids)):
        raise ValueError('Distinct official feature identities required')
    features = {f['properties'].get('FID'): f for f in official}
    if len(features) != len(official) or not set(ids) <= features.keys():
        raise ValueError('Reviewed official feature identity missing or duplicated')
    kind, number = key.split(':')
    for identity in roots:
        tags = index.get(('relation', identity), {}).get('tags', {})
        if (not uk_metadata_anchor(tags) or tags.get('network') != kind.lower()
                or tags.get('ref') != number):
            raise ValueError('Explicit UK numbered source identity required')
    for identity in ids:
        p = features[identity]['properties']
        if (p.get('RouteType') != kind or p.get('RouteNo') != int(number)
                or p.get('RouteCat') != 'Alternative Route'
                or p.get('OpenStatus') != 'Open'
                or p.get('Desc_') not in ('TrafficFree', 'OnRoad')):
            raise ValueError('Only explicitly open numbered alternatives may be reviewed')
    return set(ids)


def read_reviewed_overlay(folder, previous_plan_raw, expected_manifest_sha256):
    """Consume a reviewed overlay only against its exact preceding inventory."""
    from .inventory_completion import read_completion
    folder = Path(folder)
    manifest_raw = (folder / 'manifest.json').read_bytes()
    if hashlib.sha256(manifest_raw).hexdigest() != expected_manifest_sha256:
        raise ValueError('Numbered alternative overlay manifest changed')
    manifest = json.loads(manifest_raw)
    if (manifest.get('schema_version') != 1
            or manifest.get('whole_published_route_ready') is not False
            or manifest.get('cross_route_credit') is not False
            or manifest.get('replaces_plan_sha256') != hashlib.sha256(previous_plan_raw).hexdigest()):
        raise ValueError('Numbered alternative overlay predecessor or scope differs')
    review_raw = (folder / 'extent-review.json').read_bytes()
    if hashlib.sha256(review_raw).hexdigest() != manifest['numbered_alternative_review_sha256']:
        raise ValueError('Numbered alternative overlay review changed')
    review = json.loads(review_raw)
    raw = gzip.decompress((folder / 'plan.json.gz').read_bytes())
    associations = gzip.decompress((folder / 'associations.json.gz').read_bytes())
    ledger, paths, plan = read_completion(raw, associations, manifest['plan_sha256'], manifest['association_sha256'])
    previous = json.loads(previous_plan_raw)
    if (review.get('route_key') != plan['route_key'] or plan['route_key'] != previous['route_key']
            or review.get('policy') != 'official_numbered_alternative_as_own_route'
            or review.get('cross_route_credit') is not False
            or review.get('whole_published_route_ready') is not False
            or manifest.get('reference_version') != plan['reference_version']
            or plan['metadata'].get('numbered_alternative_review_sha256') != manifest['numbered_alternative_review_sha256']
            or any(plan['metadata'].get(k) != previous['metadata'].get(k) for k in
                   ('projection_origin', 'source_snapshot_sha256', 'national_reference_sha256'))
            or review.get('official_network_sha256') != plan['metadata'].get('national_reference_sha256')
            or any(abs(plan[k] - previous[k]) > 1e-5 for k in
                   ('source_inventory_length_m', 'canonical_inventory_length_m'))):
        raise ValueError('Numbered alternative overlay changes route identity or denominator')
    return ledger, paths, plan
