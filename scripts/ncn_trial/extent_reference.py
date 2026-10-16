"""Bind explicit named-extent source spans to a versioned walking trial.

The national snapshot is pinned for reconciliation context, not used to infer
official NCN membership. Named-extent sections retain their separate scope.
"""
import hashlib
import json

from shapely.geometry import LineString

from matched_path_evidence import digest, source_geometry_digest
from .coverage import Section, TrialReference
from .extent_composition import compose_extent
from .journey_replay import projector
from .saved_paths import KnownPath


def build_extent_reference(candidates, sources, network_raw, expected_network_sha256):
    if hashlib.sha256(network_raw).hexdigest() != expected_network_sha256:
        raise ValueError('National reference checksum mismatch')
    if not candidates or len({key for key, _ in candidates}) != len(candidates):
        raise ValueError('Distinct named-extent scopes required')
    sections, ways, bindings = {}, {}, {}
    indexes = {rid: {(e['type'], e['id']): e for e in json.loads(raw)['elements']}
               for rid, raw in sources.items()}
    for scope, extent in sorted(candidates):
        selected_sources = {int(r): sources[int(r)] for r in extent['source_relations']}
        replay = compose_extent(selected_sources, extent['selected_spans'])
        if any(replay[field] != extent[field] for field in
               ('source_relations', 'selected_spans', 'physical_edges')):
            raise ValueError('Named extent/source reconciliation required')
        bindings[scope] = digest(replay)
        for span in replay['selected_spans']:
            rid, wid = span['relation_id'], str(span['way_id'])
            index = indexes[rid]
            node_ids = index['way', int(wid)]['nodes']
            coordinates = tuple((index['node', n]['lon'], index['node', n]['lat']) for n in node_ids)
            low, high = sorted((span['from_vertex'], span['to_vertex']))
            key = f'OSM:{rid}:{wid}:{low}:{high}'
            entry = sections.setdefault(key, {'coordinates': coordinates[low:high+1], 'routes': set()})
            entry['routes'].add(scope)
            if wid in ways and source_geometry_digest(ways[wid]['coordinates']) != source_geometry_digest(coordinates):
                raise ValueError('Source way changed between named extents')
            way = ways.setdefault(wid, {'coordinates': coordinates, 'sections': set()})
            way['sections'].add(key)
    points = [p for section in sections.values() for p in section['coordinates']]
    origin = [(min(p[0] for p in points)+max(p[0] for p in points))/2,
              (min(p[1] for p in points)+max(p[1] for p in points))/2]
    metadata = {'projection_origin': origin, 'national_reference_sha256': expected_network_sha256,
                'extent_sha256_by_scope': bindings,
                'scope': 'selected_named_extents_not_official_network_membership',
                'completion_ready': False}
    version = 'named-extents:' + digest(metadata)
    project = projector(metadata)
    reference = TrialReference(version, [Section(key, LineString([project(p) for p in entry['coordinates']]),
                                                 tuple(sorted(entry['routes'])))
                                         for key, entry in sorted(sections.items())])
    paths = tuple(KnownPath(wid, entry['coordinates'], tuple(sorted(entry['sections'])), version,
                            ('on_foot',), 'exact_source_spans')
                  for wid, entry in sorted(ways.items()))
    return reference, metadata, paths
