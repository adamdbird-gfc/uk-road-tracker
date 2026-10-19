"""Pin a bounded source-label correction to exact officially open target spans.

Source labels and historical inventories are preserved. Explicit partial-provider
reviews may retain the same source/target label while admitting only verified
spans. This review does not
alias routes or transfer old journey evidence. It authorises only listed source
edges, and every ordinary geometry, access and branch gate still applies.
"""
import gzip
import hashlib
import json
import math
import xml.etree.ElementTree as ET
from pathlib import Path

from shapely.geometry import LineString, shape
from shapely.ops import unary_union
from shapely.strtree import STRtree
from matched_path_evidence import digest
from .catalogue_extract import _route_members
from .numbered_reconciliation import uk_metadata_anchor, non_uk_namespace


def policy_matches_source(review, key):
    policy=review.get('policy')
    same=review.get('source_route_key')==key
    return ((policy=='reviewed_source_edges_to_official_open_target' and not same)
            or (policy in ('partial_provider_verified_spans_only',
                           'official_uk_spans_scope_review') and same))


def validate_review(raw, expected_sha256, key, official, network_sha256,
                    roots, index, source_hashes, providers):
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise ValueError('Source identity review checksum differs')
    r = json.loads(raw)
    source = r.get('source_route_key', '').split(':')
    regional_scope=(key.startswith('RCN:') and r.get('policy')=='official_uk_spans_scope_review'
                    and r.get('source_route_key')==key)
    if (r.get('schema_version') != 1 or r.get('target_route_key') != key
            or (not key.startswith('NCN:') and not regional_scope)
            or len(source) != 2 or source[0] not in ('NCN','RCN')
            or (source[0]=='RCN' and not regional_scope)
            or not source[1].isdecimal() or not policy_matches_source(r,key)
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
    spatial_scope = r.get('uk_scope_basis') == 'exact_source_spans_on_pinned_official_uk_open_extent'
    if r.get('policy') == 'official_uk_spans_scope_review' and not spatial_scope:
        raise ValueError('Explicit UK span-scope evidence required')
    permitted_empty=r.get('approved_empty_relation_ids',[])
    if (not isinstance(permitted_empty,list) or any(type(i)is not int for i in permitted_empty)
            or len(set(permitted_empty))!=len(permitted_empty)
            or any(index.get(('relation',i),{}).get('members',[])!=[] for i in permitted_empty)):
        raise ValueError('Only explicitly captured empty relations may be reviewed')
    empty_evidence=r.get('empty_relation_evidence',[])
    if (len(empty_evidence)!=len(permitted_empty)
            or {e.get('relation_id')for e in empty_evidence}!=set(permitted_empty)):
        raise ValueError('Independent empty-relation evidence required')
    for evidence in empty_evidence:
        rid=evidence['relation_id'];text=evidence.get('xml','');original=index['relation',rid]
        if (not isinstance(text,str)or len(text)>20_000 or '<!DOCTYPE' in text
                or hashlib.sha256(text.encode()).hexdigest()!=evidence.get('sha256')
                or evidence.get('url')!=f'https://api.openstreetmap.org/api/0.6/relation/{rid}'):
            raise ValueError('Pinned empty-relation XML receipt required')
        try:
            xml=ET.fromstring(text);relations=xml.findall('relation')
            if (xml.tag!='osm'or len(relations)!=1 or len(xml)!=1
                    or relations[0].get('id')!=str(rid)
                    or relations[0].get('version')!=str(original.get('version'))
                    or relations[0].get('timestamp')!=original.get('timestamp')
                    or any(c.tag!='tag'for c in relations[0])
                    or {t.get('k'):t.get('v')for t in relations[0].findall('tag')}!=original.get('tags',{})):
                raise ValueError('XML does not confirm the captured relation is empty')
        except ET.ParseError as error:
            raise ValueError('Incomplete empty-relation XML')from error
    observed_empty=set()
    records = []
    members = set()
    for rid in roots:
        root = index.get(('relation', rid), {})
        tags = root.get('tags', {})
        if ((not uk_metadata_anchor(tags) and not spatial_scope) or non_uk_namespace(tags)
                or tags.get('network') != source[0].lower() or tags.get('ref') != source[1]):
            raise ValueError('Reviewed original UK source classification differs')
        structure = _route_members(index, rid)
        observed_empty.update(structure[3])
        if any(structure[:3]) or set(structure[3])-set(permitted_empty):
            raise ValueError('Reviewed source membership is incomplete')
        members.update(structure[-1])
        records.append(root)
    if observed_empty!=set(permitted_empty):
        raise ValueError('Empty-relation review differs from observed hierarchy')
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
    if r.get('approved_open_features_sha256') != digest([f for f in official if f['properties']['FID'] in ids]):
        raise ValueError('Exact reviewed open geometry required')
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
    if spatial_scope:
        # A new explicit scope review can replace missing operator metadata only
        # for exact edges on the publisher's UK Open Main linework. It never
        # declares the entire source relation British or aliases route labels.
        from .network_geometry import metric_projector
        bounds = unary_union([shape(f['geometry']) for f in official]).bounds
        metric = metric_projector([(bounds[0]+bounds[2])/2,(bounds[1]+bounds[3])/2])
        def lines(feature):
            g=feature['geometry'];parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
            return [LineString([metric(c) for c in part]) for part in parts]
        allowed = STRtree([line for f in official if f['properties']['FID'] in ids for line in lines(f)])
        excluded = STRtree([line for f in official if f['properties']['FID'] not in ids for line in lines(f)])
        for wid, edge_index in edges:
            nodes=index['way',wid]['nodes'][edge_index:edge_index+2]
            line=LineString([metric([index['node',n]['lon'],index['node',n]['lat']]) for n in nodes])
            good=unary_union([allowed.geometries[int(i)].buffer(10)for i in allowed.query(line.buffer(10))])
            bad=unary_union([excluded.geometries[int(i)].buffer(10)for i in excluded.query(line.buffer(10))])
            if line.difference(good).length > 1e-5 or line.intersection(bad).length > 1e-5:
                raise ValueError('Reviewed UK source edge lies outside approved open main extent')
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
            or not policy_matches_source(review,key)
            or extent.get('completion_binding_ready') is not False
            or extent.get('basis') != 'official_open_main_only'
            or payload['reference_version'] != 'numbered-source-spans:'+digest(metadata)):
        raise ValueError('Reviewed source catalogue identity differs')
    if ({f['properties'].get('FID') for f in extent['features']} != set(review['approved_official_feature_ids'])
            or len(extent['features']) != len(review['approved_official_feature_ids'])
            or digest(extent['features']) != review.get('approved_open_features_sha256')):
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
