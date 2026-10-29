"""Compose a named-extent candidate from explicit, source-backed way spans.

This inventories physical edges, not a navigable itinerary. It never adds an
edge between nearby coordinates or approves route access/completion policy.
"""
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
from matched_path_evidence import source_geometry_digest


def metres(first, second):
    x,y,z,w=map(math.radians,[*first,*second])
    h=math.sin((w-y)/2)**2+math.cos(y)*math.cos(w)*math.sin((z-x)/2)**2
    return 2*6371008.8*math.asin(min(1,math.sqrt(h)))


def compose_extent(sources, selections):
    indexes, memberships, provenance = {}, {}, {}
    for relation_id,raw in sources.items():
        value=json.loads(raw)
        if value.get('error') or value.get('remark'):raise ValueError('Incomplete source')
        index={(e['type'],e['id']):e for e in value['elements']}
        root=index.get(('relation',relation_id))
        if root is None or root.get('tags',{}).get('type')!='route':
            raise ValueError('Explicit direct route relation required')
        indexes[relation_id]=index
        memberships[relation_id]={m['ref'] for m in root['members'] if m['type']=='way'}
        provenance[str(relation_id)]={'source_sha256':hashlib.sha256(raw).hexdigest(),
                                      'relation_version':root.get('version'),
                                      'relation_timestamp':root.get('timestamp')}
    coordinates,edges,spans={},set(),[]
    for selection in selections:
        relation,way_id,start,end=(selection[k] for k in ('relation_id','way_id','from_vertex','to_vertex'))
        if not all(type(v) is int for v in (relation,way_id,start,end)):
            raise ValueError('Integer source identities/vertices required')
        if relation not in indexes or way_id not in memberships[relation]:
            raise ValueError('Selection is not a member of the declared source route')
        index=indexes[relation]
        try:
            way=index['way',way_id];nodes=way['nodes']
            points=[[index['node',n]['lon'],index['node',n]['lat']] for n in nodes]
        except KeyError as error:raise ValueError('Missing source geometry') from error
        geometry_hash=source_geometry_digest(points)
        if not 0 <= start < len(nodes) or not 0 <= end < len(nodes) or start==end:
            raise ValueError('Source vertex span out of range')
        low,high=sorted((start,end))
        for node,point in zip(nodes[low:high+1],points[low:high+1]):
            if node in coordinates and coordinates[node]!=point:
                raise ValueError('Source-node coordinates conflict; refresh reference')
            coordinates[node]=point
        for a,b in zip(nodes[low:high],nodes[low+1:high+1]):
            if a!=b:edges.add(tuple(sorted((a,b))))
        spans.append({**selection,'source_geometry_sha256':geometry_hash})
    if not edges:raise ValueError('At least one supported source edge required')
    graph=defaultdict(set)
    for a,b in edges:graph[a].add(b);graph[b].add(a)
    remaining=set(graph);components=0
    while remaining:
        todo=[next(iter(remaining))];components+=1
        while todo:
            node=todo.pop()
            if node not in remaining:continue
            remaining.remove(node);todo.extend(graph[node] & remaining)
    return {'schema_version':1,'extent_status':'source_backed_composite_candidate',
            'source_relations':provenance,'selected_spans':spans,
            'physical_edges':[{'nodes':[a,b],'coordinates':[coordinates[a],coordinates[b]]} for a,b in sorted(edges)],
            'unique_edge_count':len(edges),'approximate_unique_m':sum(metres(coordinates[a],coordinates[b]) for a,b in sorted(edges)),
            'connected_components':components,'degree_one_endpoint_count':sum(len(v)==1 for v in graph.values()),
            'branch_node_count':sum(len(v)>2 for v in graph.values()),
            'completion_ready':False,'activity_access_verified':False,'routing_instruction':False}


def compose_manifest(manifest_path):
    """Replay an explicit selection against checksum-pinned local OSM sources."""
    manifest_path = Path(manifest_path)
    manifest = json.loads(manifest_path.read_text())
    if manifest.get('schema_version') != 1:
        raise ValueError('Unsupported extent manifest version')
    sources = {}
    for binding in manifest['sources']:
        relation = binding['relation_id']
        if type(relation) is not int or relation in sources:
            raise ValueError('Distinct integer source relation identities required')
        raw = (manifest_path.parent / binding['file']).read_bytes()
        if hashlib.sha256(raw).hexdigest() != binding['sha256']:
            raise ValueError('Source checksum mismatch; refresh and review selections')
        sources[relation] = raw
    return compose_extent(sources, manifest['selected_spans'])


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = compose_manifest(args.manifest)
    args.output.write_text(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
