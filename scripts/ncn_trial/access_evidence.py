"""Read-only access evidence for explicitly selected source spans.

These are source-tag observations, not routing permissions or journey-credit
decisions. Missing tags and present-day restrictions cannot establish the legal
status of a historic journey. No conditional expression is evaluated here.
"""
from collections import Counter
import json

from .extent_composition import compose_extent


def mode_evidence(tags, mode):
    hierarchy = {'on_foot': ('foot', 'access'),
                 'cycling': ('bicycle', 'vehicle', 'access')}[mode]
    key = next((key for key in hierarchy if tags.get(key)), None)
    value = tags.get(key)
    if value in ('yes', 'designated', 'official', 'permissive'):
        kind = 'positive_tag'
    elif value == 'no':
        kind = 'negative_tag'
    elif value == 'dismount' and mode == 'cycling':
        kind = 'dismount_tag'
    elif value is None:
        kind = 'not_explicitly_tagged'
    else:
        kind = 'context_or_unrecognised_value'
    conditions = {k: v for k, v in tags.items()
                  if k.split(':')[0] in hierarchy and 'conditional' in k.split(':')}
    directional = {k: v for k, v in tags.items()
                   if k.split(':')[0] in hierarchy
                   and any(x in k.split(':') for x in ('forward', 'backward'))}
    return {'static_kind': kind, 'static_key': key, 'static_value': value,
            'conditions': conditions, 'directional_access': directional,
            'permission_verified': False}


def observed_tags(tags):
    families = {'access', 'foot', 'bicycle', 'vehicle', 'motor_vehicle', 'oneway'}
    singles = {'highway', 'barrier', 'junction', 'seasonal', 'opening_hours',
               'locked', 'construction', 'proposed', 'sidewalk'}
    return {k: v for k, v in tags.items() if k.split(':')[0] in families or k in singles}


def audit_extent(extent, sources):
    replay = compose_extent(sources, extent['selected_spans'])
    if replay['source_relations'] != extent['source_relations'] \
            or replay['selected_spans'] != extent['selected_spans'] \
            or replay['physical_edges'] != extent['physical_edges']:
        raise ValueError('Extent/source provenance mismatch; recompose before audit')
    indexes = {rid: {(e['type'], e['id']): e for e in json.loads(raw)['elements']}
               for rid, raw in sources.items()}
    ways, nodes = {}, {}
    for span in extent['selected_spans']:
        rid, wid = span['relation_id'], span['way_id']
        index = indexes[rid]
        way = index['way', wid]
        tags = way.get('tags', {})
        record = ways.setdefault((rid, wid), {
            'relation_id': rid, 'way_id': wid, 'source_version': way.get('version'),
            'member_roles': [m.get('role', '') for m in index['relation', rid]['members']
                             if m['type'] == 'way' and m['ref'] == wid],
            'selected_spans': [], 'tags': observed_tags(tags),
            'modes': {mode: mode_evidence(tags, mode) for mode in ('on_foot', 'cycling')}})
        record['selected_spans'].append([span['from_vertex'], span['to_vertex']])
        low, high = sorted((span['from_vertex'], span['to_vertex']))
        for nid in way['nodes'][low:high+1]:
            node = index['node', nid]
            node_tags = observed_tags(node.get('tags', {}))
            if node_tags:
                nodes[rid, nid] = {'relation_id': rid, 'node_id': nid,
                                   'tags': node_tags,
                                   'modes': {mode: mode_evidence(node.get('tags', {}), mode)
                                             for mode in ('on_foot', 'cycling')}}
    records = [ways[k] for k in sorted(ways)]
    summary = {}
    for mode in ('on_foot', 'cycling'):
        summary[mode] = {
            'way_static_tag_counts': dict(Counter(r['modes'][mode]['static_kind'] for r in records)),
            'conditional_way_ids': sorted({r['way_id'] for r in records if r['modes'][mode]['conditions']}),
            'directional_access_way_ids': sorted({r['way_id'] for r in records if r['modes'][mode]['directional_access']})}
    return {'schema_version': 1, 'purpose': 'selected_span_access_evidence_only',
            'source_relations': replay['source_relations'],
            'selected_way_record_count': len(records), 'modes': summary,
            'ways': records, 'nodes': [nodes[k] for k in sorted(nodes)],
            'permission_verified': False, 'completion_ready': False,
            'journey_credit_changed': False}
