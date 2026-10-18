"""Pin a bounded source-label correction to exact officially open target spans.

Source labels and historical inventories are preserved. This review does not
alias routes or transfer old journey evidence. It authorises only listed source
edges, and every ordinary geometry, access and branch gate still applies.
"""
import gzip
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union
from matched_path_evidence import digest
from .catalogue_extract import _route_members
from .numbered_reconciliation import uk_metadata_anchor, non_uk_namespace


def validate_review(raw, expected_sha256, key, official, network_sha256,
                    roots, index, source_hashes, providers):
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise ValueError('Source identity review checksum differs')
    r = json.loads(raw)
    source = r.get('source_route_key', '').split(':')
    if (r.get('schema_version') != 1 or r.get('target_route_key') != key
            or not key.startswith('NCN:') or len(source) != 2 or source[0] != 'NCN'
            or not source[1].isdecimal() or r['source_route_key'] == key
            or r.get('policy') != 'reviewed_source_edges_to_official_open_target'
            or r.get('route_alias') is not False
            or r.get('old_journey_evidence_transferred') is not False
            or r.get('whole_published_route_ready') is not False
            or r.get('official_network_sha256') != network_sha256
            or r.get('official_features_sha256') != digest(official)
            or r.get('source_snapshot_sha256') != digest(sorted(source_hashes))
            or r.get('provider_sha256') != digest(providers)
            or r.get('source_relation_ids') != sorted(roots)
            or not roots or len(set(roots)) != len(roots)
            or not r.get('identity_evidence_urls')):
        raise ValueError('Explicit pinned source-to-target identity review required')
    records = []
    members = set()
    for rid in roots:
        root = index.get(('relation', rid), {})
        tags = root.get('tags', {})
        if (not uk_metadata_anchor(tags) or non_uk_namespace(tags)
                or tags.get('network') != 'ncn' or tags.get('ref') != source[1]):
            raise ValueError('Reviewed original UK source classification differs')
        structure = _route_members(index, rid)
        if any(structure[:4]):
            raise ValueError('Reviewed source membership is incomplete')
        members.update(structure[-1])
        records.append(root)
    if r.get('source_relations_sha256') != digest(records):
        raise ValueError('Reviewed source relation versions differ')
    ids = r.get('approved_official_feature_ids', [])
    by_id = {f['properties'].get('FID'): f for f in official}
    if (not ids or any(type(i) is not int for i in ids)
            or len(set(ids)) != len(ids) or len(by_id) != len(official)
            or not set(ids) <= by_id.keys()):
        raise ValueError('Distinct reviewed official target features required')
    kind, number = key.split(':')
    for identity in ids:
        p = by_id[identity]['properties']
        if (p.get('RouteType') != kind or p.get('RouteNo') != int(number)
                or p.get('RouteCat') != 'Main Route' or p.get('OpenStatus') != 'Open'
                or p.get('Desc_') not in ('OnRoad', 'TrafficFree')):
            raise ValueError('Only explicitly open target main features may be reviewed')
    spans = r.get('approved_source_spans', [])
    edges = set()
    for span in spans:
        wid, low, high = (span.get(k) for k in ('way_id', 'from_vertex', 'to_vertex'))
        if (any(type(x) is not int for x in (wid, low, high)) or wid not in members
                or not 0 <= low < high < len(index.get(('way', wid), {}).get('nodes', []))):
            raise ValueError('Reviewed source span is not in the pinned membership')
        selected = {(wid, i) for i in range(low, high)}
        if selected & edges:
            raise ValueError('Duplicate reviewed source edges')
        edges.update(selected)
    if not edges:
        raise ValueError('Nonempty explicit source spans required')
    return source[0], int(source[1]), set(ids), edges


def read_catalogue(folder, expected_manifest_sha256):
    """Read exact reviewed catalogue bytes; no inventory alias or percent inferred."""
    from .association_bundle import read_bundle
    from .coverage import TrialReference, Section
    from .journey_replay import projector
    folder = Path(folder)
    raw = (folder/'manifest.json').read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_manifest_sha256:
        raise ValueError('Reviewed source catalogue manifest changed')
    m = json.loads(raw)
    if (m.get('schema_version') != 1 or m.get('route_alias') is not False
            or m.get('whole_published_route_ready') is not False):
        raise ValueError('Reviewed source catalogue scope differs')
    values = {}
    for name, sha_key in [('reference.json.gz', 'reference_sha256'),
                          ('associations.json.gz', 'association_sha256'),
                          ('identity-review.json', 'source_identity_review_sha256'),
                          ('open-extent.json', 'open_extent_sha256')]:
        data = (folder/name).read_bytes()
        if name.endswith('.gz'):data = gzip.decompress(data)
        if hashlib.sha256(data).hexdigest() != m.get(sha_key):
            raise ValueError('Reviewed source catalogue file changed')
        values[name] = data
    payload = json.loads(values['reference.json.gz'])
    review = json.loads(values['identity-review.json'])
    extent = json.loads(values['open-extent.json'])
    metadata = payload['metadata']
    key = review['target_route_key']
    if (metadata.get('route_key') != key or extent.get('route_key') != key
            or m.get('target_route_key') != key
            or metadata.get('source_identity_review_sha256') != m['source_identity_review_sha256']
            or review.get('route_alias') is not False
            or review.get('old_journey_evidence_transferred') is not False
            or review.get('whole_published_route_ready') is not False
            or extent.get('completion_binding_ready') is not False
            or extent.get('basis') != 'official_open_main_only'
            or payload['reference_version'] != 'numbered-source-spans:'+digest(metadata)):
        raise ValueError('Reviewed source catalogue identity differs')
    if ({f['properties'].get('FID') for f in extent['features']} != set(review['approved_official_feature_ids'])
            or len(extent['features']) != len(review['approved_official_feature_ids'])):
        raise ValueError('Published open extent differs from the reviewed features')
    for feature in extent['features']:
        p = feature['properties']
        if (f"{p.get('RouteType')}:{p.get('RouteNo')}" != key
                or p.get('RouteCat') != 'Main Route' or p.get('OpenStatus') != 'Open'
                or p.get('Desc_') not in ('OnRoad','TrafficFree')):
            raise ValueError('Non-open feature in published open extent')
    project = projector(metadata)
    lines=[]
    for feature in extent['features']:
        g=feature['geometry']
        if g['type'] not in ('LineString','MultiLineString'):
            raise ValueError('Open extent line geometry required')
        parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
        lines.extend(LineString([project(c) for c in part]) for part in parts)
    length=extent.get('canonical_open_union_m')
    if (extent.get('projection_origin') != metadata['projection_origin']
            or extent.get('unmatched_official_open_extent_retained') is not True
            or not isinstance(length,(int,float)) or isinstance(length,bool)
            or not math.isfinite(length) or length<=0
            or abs(unary_union(lines).length-length)>1e-5):
        raise ValueError('Complete official open geometry denominator required')
    allowed = {(s['way_id'], i) for s in review['approved_source_spans']
               for i in range(s['from_vertex'], s['to_vertex'])}
    for s in payload['sections']:
        if (s['routes'] != [key+':eligible-source-spans']
                or any((s['way_id'], i) not in allowed for i in range(s['from_vertex'], s['to_vertex']))):
            raise ValueError('Unreviewed source span in target catalogue')
    reference = TrialReference(payload['reference_version'], [Section(s['key'],
        LineString([project(c) for c in s['coordinates']]),tuple(s['routes'])) for s in payload['sections']])
    bundle = values['associations.json.gz']
    paths = read_bundle(bundle,reference,metadata['projection_origin'],digest(json.loads(bundle)))
    return reference, metadata, paths, extent
