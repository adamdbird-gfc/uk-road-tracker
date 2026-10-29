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


def _source_index(snapshots):
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
    return index, hashes


def _route_members(index, root_id):
    root=index.get(('relation',root_id))
    if root is None:raise ValueError('Root route relation missing')
    missing=set();cycles=set();unsupported=set();empty=set();roles=defaultdict(list);relations=set();way_ids=set()

    def visit(identity, ancestry=(), inherited=()):
        if identity in ancestry:
            cycles.add(identity);return
        relation=index.get(('relation',identity))
        if relation is None:missing.add(('relation',identity));return
        tags=relation.get('tags',{})
        if tags.get('type') not in ('route','superroute') or tags.get('route') not in ('bicycle','foot','hiking'):
            if identity == root_id:raise ValueError('Unsupported root route relation')
            unsupported.add(identity);return
        relations.add(identity)
        if not relation.get('members'):empty.add(identity)
        for member in relation.get('members',[]):
            role=str(member.get('role','')).strip()
            chain=inherited+((identity,role),)
            key=(member['type'],member['ref'])
            if member['type']=='relation':visit(member['ref'],ancestry+(identity,),chain)
            elif member['type']=='way':
                way_ids.add(member['ref'])
                roles[member['ref']].append(chain)
            # Guideposts and marker nodes are not route edges.
    visit(root_id)
    return missing,cycles,unsupported,empty,roles,relations,way_ids


def extract_route(snapshots, root_id):
    index,hashes=_source_index(snapshots)
    missing,cycles,unsupported,empty,roles,relations,way_ids=_route_members(index,root_id)
    root=index['relation',root_id]
    ways={identity:index['way',identity] for identity in way_ids if ('way',identity) in index}
    missing.update(('way',identity) for identity in way_ids if ('way',identity) not in index)
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
             'cyclic_relation_ids':sorted(cycles),'unsupported_relation_ids':sorted(unsupported),
             'empty_relation_ids':sorted(empty),
             'member_geometry_complete':bool(features) and not missing and not cycles and not unsupported and not empty,
             'completion_ready':False,'activity_access_verified':False,'current_ncn_membership_verified':False}
    return summary,features


def extract_memberships(snapshots, root_id):
    """Read explicit hierarchical way identities without claiming their geometry."""
    index,hashes=_source_index(snapshots)
    return _memberships_from_index(index,hashes,root_id)


def _memberships_from_index(index, hashes, root_id):
    missing,cycles,unsupported,empty,roles,relations,way_ids=_route_members(index,root_id)
    root=index['relation',root_id]
    return {'schema_version':1,'relation_id':root_id,'tags':root.get('tags',{}),
            'source_sha256':sorted(set(hashes)),
            'source_relations':[{'id':identity,'version':index['relation',identity].get('version'),
                                 'timestamp':index['relation',identity].get('timestamp')}
                                for identity in sorted(relations)],
            'ways':[{'way_id':identity,'membership_chains':roles[identity]} for identity in sorted(way_ids)],
            'missing_relations':[identity for kind,identity in sorted(missing) if kind=='relation'],
            'cyclic_relation_ids':sorted(cycles),'unsupported_relation_ids':sorted(unsupported),
            'empty_relation_ids':sorted(empty),
            'member_lists_complete':bool(way_ids) and not missing and not cycles and not unsupported and not empty,
            'way_geometry_verified':False,'official_gis_association_approved':False,
            'completion_ready':False}


def extract_membership_catalogue(snapshots, root_ids):
    """Reuse one validated index across a bounded national root census."""
    if len(set(root_ids)) != len(root_ids):raise ValueError('Distinct root identities required')
    index,hashes=_source_index(snapshots)
    return [_memberships_from_index(index,hashes,identity) for identity in sorted(root_ids)]
