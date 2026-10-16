"""Extract bounded OSM named-route candidates without inventing joins or access.

Nested relation membership is retained. Complete member geometry does not prove
route completeness in the field, current NCN status, or walking/cycling access.
"""
from collections import defaultdict
import hashlib
import json
import math


def _public(element):
    return {k:element[k] for k in ('type','id','version','tags','nodes','members','lat','lon') if k in element}


def extract_route(snapshots, root_id):
    index, hashes = {}, []
    for raw in snapshots:
        value=json.loads(raw)
        hashes.append(hashlib.sha256(raw).hexdigest())
        if value.get('remark') or value.get('error'):raise ValueError('Incomplete source response')
        for element in value['elements']:
            key=(element['type'],element['id'])
            if key in index and _public(index[key]) != _public(element):
                raise ValueError('Conflicting source element versions; refresh snapshots')
            index[key]=element
    root=index.get(('relation',root_id))
    if root is None:raise ValueError('Root route relation missing')
    missing=set();cycles=set();roles=defaultdict(list);relations=set();ways={}

    def visit(identity, ancestry=(), inherited=()):
        if identity in ancestry:
            cycles.add(identity);return
        relation=index.get(('relation',identity))
        if relation is None:missing.add(('relation',identity));return
        tags=relation.get('tags',{})
        if tags.get('type') not in ('route','superroute') or tags.get('route') not in ('bicycle','foot','hiking'):
            raise ValueError('Unsupported route relation')
        relations.add(identity)
        for member in relation['members']:
            role=str(member.get('role','')).strip()
            chain=inherited+((identity,role),)
            key=(member['type'],member['ref'])
            if member['type']=='relation':visit(member['ref'],ancestry+(identity,),chain)
            elif member['type']=='way':
                way=index.get(key)
                if way is None:missing.add(key);continue
                ways[way['id']]=way
                roles[way['id']].append(chain)
            # Guideposts and marker nodes are not route edges.
    visit(root_id)
    graph=defaultdict(set);edges=set();features=[]
    for identity,way in sorted(ways.items()):
        absent={('node',n) for n in way['nodes'] if ('node',n) not in index}
        missing.update(absent)
        if absent:continue  # Never bridge a missing node by dropping it.
        coordinates=[[index[('node',n)]['lon'],index[('node',n)]['lat']] for n in way['nodes']]
        if len(coordinates)<2 or any(
                not all(isinstance(v,(int,float)) and not isinstance(v,bool) and math.isfinite(v) for v in point)
                or not -180 <= point[0] <= 180 or not -90 <= point[1] <= 90 for point in coordinates):
            raise ValueError('Invalid source way')
        for a,b in zip(way['nodes'],way['nodes'][1:]):
            if a!=b:
                graph[a].add(b);graph[b].add(a);edges.add(tuple(sorted((a,b))))
        tags={k:v for k,v in way.get('tags',{}).items() if k in
              ('name','highway','foot','bicycle','access','oneway','oneway:bicycle','surface','service','bridge','tunnel')}
        features.append({'type':'Feature','properties':{'osm_relation':root_id,'osm_way':identity,
                         'membership_chains':roles[identity],**tags},
                         'geometry':{'type':'LineString','coordinates':coordinates}})
    seen=set();components=[]
    for n in graph:
        if n in seen:continue
        todo=[n];part=set()
        while todo:
            a=todo.pop()
            if a in part:continue
            part.add(a);seen.add(a);todo.extend(graph[a]-part)
        components.append(len(part))
    ends=[n for n in graph if len(graph[n])==1]
    tags=root.get('tags',{})
    summary={'relation_id':root_id,'name':tags.get('name'),'source_route_type':tags.get('route'),
             'osm_network':tags.get('network'),'ref':tags.get('ref'),'from_tag':tags.get('from'),
             'to_tag':tags.get('to'),'relation_version':root.get('version'),
             'relation_timestamp':root.get('timestamp'),'source_sha256':sorted(set(hashes)),
             'nested_relation_ids':sorted(relations-{root_id}),
             'unique_way_count':len(ways),'geometry_way_count':len(features),
             'unique_node_edges':len(edges),'connected_components':len(components),
             'component_node_counts':sorted(components,reverse=True),
             'degree_one_endpoint_count':len(ends),
             'degree_one_endpoint_coordinates':[[index[('node',n)]['lon'],index[('node',n)]['lat']] for n in ends],
             'branch_node_count':sum(len(v)>2 for v in graph.values()),
             'missing_members':[{'type':kind,'id':identity} for kind,identity in sorted(missing)],
             'cyclic_relation_ids':sorted(cycles),'member_geometry_complete':bool(features) and not missing and not cycles,
             'completion_ready':False,'activity_access_verified':False,'current_ncn_membership_verified':False}
    return summary,features
