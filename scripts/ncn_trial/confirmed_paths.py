"""Build bounded, identity-backed source-span trial catalogues from close leads.

This is not a whole-route denominator or an automatic GIS/alternative binding.
Membership establishes NCN identity; geometry limits admission to the previously
assessed published open extent. Saved journeys still require their own source
identity evidence. No nearby unlisted way can enter the registry.
"""
from collections import Counter,defaultdict
import gzip
import hashlib
import json
from pathlib import Path
from shapely.geometry import LineString,MultiLineString
from shapely.ops import unary_union
from shapely.strtree import STRtree
from matched_path_evidence import digest
from .access_evidence import mode_evidence
from .association_bundle import build_bundle,bundle_metrics
from .catalogue_extract import _source_index,_route_members
from .coverage import Section,TrialReference
from .journey_replay import projector
from .network_geometry import metric_projector,extract_geometry
from .network_reconciliation import way_geometry
from .numbered_reconciliation import non_uk_namespace
from .saved_paths import KnownPath


def eligible_modes(tags):
    if tags.get('highway') in (None,'construction','proposed','motorway','motorway_link'):
        return ()
    normal_roads={'residential','unclassified','tertiary','tertiary_link','secondary','secondary_link',
                  'primary','primary_link','living_street','service','road'}
    defaults={'cycling':normal_roads|{'cycleway','path','track'},
              'on_foot':normal_roads|{'footway','pedestrian','path','steps','track'}}
    modes=[]
    for mode in ('on_foot','cycling'):
        evidence=mode_evidence(tags,mode)
        if evidence['conditions'] or evidence['directional_access']:continue
        kind=evidence['static_kind']
        if kind=='positive_tag' or (kind=='not_explicitly_tagged' and tags['highway'] in defaults[mode]):modes.append(mode)
    return tuple(modes)


def build_candidate(key,profile_ids,profiles,index,source_hashes,network,network_hash,provider_by_id):
    kind,number=key.split(':');number=int(number)
    official=[f for f in network['features'] if f['properties'].get('RouteType')==kind and f['properties'].get('RouteNo')==number]
    if not official:raise ValueError('Official numbered extent missing')
    bounds=unary_union([LineString(f['geometry']['coordinates']) if f['geometry']['type']=='LineString' else
        MultiLineString(f['geometry']['coordinates']) for f in official]).bounds
    origin=[(bounds[0]+bounds[2])/2,(bounds[1]+bounds[3])/2];metric=metric_projector(origin)
    physical=projector({'projection_origin':origin})
    good=[];bad=[]
    for f in official:
        p=f['properties'];g=f['geometry'];parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
        target=good if p.get('OpenStatus')=='Open' and p.get('Desc_') in ('TrafficFree','OnRoad') and p.get('RouteCat')=='Main Route' else bad
        target.extend(LineString([metric(c) for c in part]) for part in parts)
    good_tree=STRtree(good);bad_tree=STRtree(bad)
    member_ids=set();roles=defaultdict(list);providers=defaultdict(list);roots=[]
    for identity in profile_ids:
        root=index.get(('relation',identity));tags=root.get('tags',{}) if root else {}
        if tags.get('network')!=kind.lower() or tags.get('ref')!=str(number) or non_uk_namespace(tags):
            raise ValueError('Current source classification/scope differs')
        missing,cycles,unsupported,empty,paths,relations,ways=_route_members(index,identity)
        if missing or cycles or unsupported or empty:raise ValueError('Current member structure incomplete')
        original=profiles[identity]
        if ways!={w['way_id'] for w in original['ways']}:raise ValueError('Source membership changed; reassess extent')
        roots.append({'id':identity,'version':root.get('version'),'timestamp':root.get('timestamp')})
        member_ids.update(ways)
        for wid,chains in paths.items():roles[wid].extend(chains)
        representation=extract_geometry(provider_by_id[identity],original)
        for w in representation['ways']:providers[w['way_id']].append(w)
    accepted=[];held=[]
    for wid in sorted(member_ids):
        way=index.get(('way',wid));reason=None
        if way is None or any(('node',n) not in index for n in way.get('nodes',[])):
            held.append({'way_id':wid,'reason':'missing_current_source_nodes'});continue
        nodes=way['nodes'];coords=[[index['node',n]['lon'],index['node',n]['lat']] for n in nodes]
        if len(coords)<2:held.append({'way_id':wid,'reason':'missing_current_source_nodes'});continue
        line=LineString([metric(c) for c in coords]);tags=way.get('tags',{});modes=eligible_modes(tags)
        if nodes[0]==nodes[-1] or tags.get('junction') in ('roundabout','circular'):
            reason='logical_branch_policy_required'
        elif any(role.lower() in ('alternative','alternate','excursion','link') for chain in roles[wid] for _,role in chain):
            reason='logical_branch_policy_required'
        elif not modes:reason='mode_policy_unresolved_or_restricted'
        elif not providers[wid]:reason='provider_geometry_missing_or_conflicting'
        elif any(line.hausdorff_distance(way_geometry(w,metric))>.03 for w in providers[wid]):
            reason='current_geometry_differs_from_assessed_provider'
        else:
            hits=[good[int(i)] for i in good_tree.query(line.buffer(10))]
            excluded=[bad[int(i)] for i in bad_tree.query(line.buffer(10))]
            if line.difference(unary_union([part.buffer(10) for part in hits])).length>1e-5:
                reason='source_way_not_wholly_in_published_open_main_extent'
            elif line.intersection(unary_union([part.buffer(10) for part in excluded])).length>1e-5:
                reason='ambiguous_nonqualifying_official_extent'
        if reason:held.append({'way_id':wid,'reason':reason});continue
        accepted.append({'way_id':wid,'version':way.get('version'),'node_ids':nodes,'coordinates':coords,
                         'modes':modes,'membership_chains':roles[wid]})
    audit={'route_key':key,'source_relations':roots,'accepted_way_count':len(accepted),
        'held_way_count':len(held),'held':held,'held_reason_counts':dict(Counter(r['reason'] for r in held)),
        'whole_route_completion_ready':False,'accepted_source_node_geometry_verified':bool(accepted),
        'scope':'explicit_member_source_spans_only_not_full_official_route',
        'mode_policy':'Current static tags with mode overrides; recognised highway defaults. Conditional/directional restrictions held. Not historical access certification.'}
    if not accepted:return None,audit
    metadata={'projection_origin':origin,'national_reference_sha256':network_hash,
        'source_snapshot_sha256':digest(sorted(source_hashes)),'membership_evidence_sha256':digest(accepted),
        'route_key':key,'scope':audit['scope'],'whole_route_completion_ready':False}
    version='numbered-source-spans:'+digest(metadata)
    scope=key+':eligible-source-spans'
    reference=TrialReference(version,[Section(str(w['way_id']),LineString([physical(c) for c in w['coordinates']]),(scope,)) for w in accepted])
    paths=tuple(KnownPath(str(w['way_id']),tuple(map(tuple,w['coordinates'])),(str(w['way_id']),),version,
        w['modes'],'exact_source_spans',tuple(w['node_ids'])) for w in accepted)
    provenance={'attribution':'OpenStreetMap contributors; official extent Walk Wheel Cycle Trust',
        'licence':'ODbL 1.0; official extent OGL 3.0 with original source attribution',
        'source_snapshot_sha256':metadata['source_snapshot_sha256'],
        'membership_evidence_sha256':metadata['membership_evidence_sha256']}
    raw=build_bundle(paths,reference,origin,provenance)
    payload={'metadata':metadata,'reference_version':version,'sections':[{'key':str(w['way_id']),
        'coordinates':w['coordinates'],'routes':[scope]} for w in accepted]}
    return (raw,payload,bundle_metrics(raw)),audit


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--research-root',type=Path,required=True)
    parser.add_argument('--network',type=Path,required=True)
    parser.add_argument('--expected-network-sha256',required=True)
    parser.add_argument('--output',type=Path,required=True)
    a=parser.parse_args();network_raw=gzip.decompress(a.network.read_bytes())
    if hashlib.sha256(network_raw).hexdigest()!=a.expected_network_sha256:raise ValueError('Official source checksum changed')
    network=json.loads(network_raw);root=a.research_root
    fetch=json.loads((root/'confirmed-sources/fetch-manifest.json').read_bytes())
    raws=[]
    for source in fetch['sources']:
        raw=gzip.decompress((root/'confirmed-sources'/source['file']).read_bytes())
        if hashlib.sha256(raw).hexdigest()!=source['sha256']:raise ValueError('OSM source checksum changed')
        raws.append(raw)
    index,hashes=_source_index(raws)
    profiles={p['relation_id']:p for p in json.load(gzip.open(root/'numeric-route-membership-profiles.json.gz'))['profiles']}
    report=json.loads((root/'physical-national/summary.json').read_bytes());provider={}
    for source in json.loads((root/'national-geometry/fetch-manifest.json').read_bytes())['sources']:
        raw=gzip.decompress((root/'national-geometry'/source['file']).read_bytes())
        if hashlib.sha256(raw).hexdigest()!=source['sha256']:raise ValueError('Provider source checksum changed')
        provider[source['relation_id']]=json.loads(raw)
    a.output.mkdir(exist_ok=True,parents=True);audits=[];identities=set()
    for row in report['routes']:
        if row['state']!='close_bidirectional_correspondence_candidate':continue
        key=row['official_route_key'];result=None
        try:result,audit=build_candidate(key,row['candidate_relation_ids'],profiles,index,hashes,network,a.expected_network_sha256,provider)
        except ValueError as e:audit={'route_key':key,'accepted_way_count':0,'route_hold':str(e),'whole_route_completion_ready':False}
        if result:
            raw,payload,metrics=result;folder=a.output/key.replace(':','-');folder.mkdir(exist_ok=True)
            (folder/'associations.json.gz').write_bytes(gzip.compress(raw,mtime=0))
            (folder/'reference.json.gz').write_bytes(gzip.compress(json.dumps(payload,separators=(',',':')).encode(),mtime=0))
            audit['transport']=metrics;identities.update(int(p['way_id']) for p in json.loads(raw)['paths'])
        audits.append(audit);print(key,audit['accepted_way_count'],'accepted',audit.get('held_way_count',0),'held',audit.get('route_hold',''),flush=True)
    summary={'scope':'First48 close candidate keys; bounded source-span trial catalogues, not whole-route completion',
        'route_keys_assessed':len(audits),'route_keys_with_catalogues':sum(x['accepted_way_count']>0 for x in audits),
        'distinct_accepted_way_ids':len(identities),'routes':audits,'source_fetch_complete':fetch['complete'],
        'whole_route_completion_ready':False,'app_publication_ready':False}
    (a.output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')


if __name__=='__main__':main()
