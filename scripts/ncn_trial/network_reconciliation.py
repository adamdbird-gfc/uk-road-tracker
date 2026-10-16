"""Nationwide route-scoped triage; alternative policy never approves proximity.

All numbered routes receive a report, including unavailable/partial source data.
Only explicit reviewed manifests (currently the separate Route177 trial) can turn
alternate physical paths into shared logical completion. Diagnostic similarity
is not an automatic binding, access decision or full-route completion claim.
"""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
from shapely.geometry import LineString,MultiLineString,box,shape
from shapely.ops import unary_union
from shapely.strtree import STRtree
from .network_geometry import extract_geometry,metric_projector
from .physical_crosswalk import compare_route


def disposition(comparison, *, source_complete, scope_anchored):
    if comparison is None:return 'source_geometry_unavailable'
    values=comparison['corridor_sensitivity']
    if not source_complete:return 'partial_source_geometry'
    if not scope_anchored:return 'source_scope_review_required'
    if values['25']['official_fraction']<.01:return 'no_substantial_official_correspondence'
    if min(values['10']['official_fraction'],values['10']['source_fraction'])>=.98:
        return 'close_bidirectional_correspondence_candidate'
    if values['25']['official_fraction']-values['3']['official_fraction']>.2:
        return 'offset_sensitive_correspondence_review'
    return 'partial_or_different_extent_review'


def canonical_coordinates(coordinates):
    forward=tuple(map(tuple,coordinates))
    return min(forward,tuple(reversed(forward)))


def way_geometry(way, project=None):
    parts=way.get('coordinate_parts',[way['coordinates']])
    if project:parts=[[project(c) for c in part] for part in parts]
    return LineString(parts[0]) if len(parts)==1 else MultiLineString(parts)


def prepare_sources(profiles, providers):
    if len({p['relation_id'] for p in profiles})!=len(profiles):raise ValueError('Distinct source profiles required')
    extracted={};errors={};identities={};conflicts=set()
    for profile in profiles:
        relation=profile['relation_id']
        if relation not in providers:continue
        try:report=extract_geometry(providers[relation],profile)
        except ValueError as error:errors[relation]=str(error);continue
        extracted[relation]=report
        for way in report['ways']:
            key=way['way_id'];coords=tuple(sorted(canonical_coordinates(part) for part in way.get('coordinate_parts',[way['coordinates']])))
            if key in identities and identities[key]!=coords:conflicts.add(key)
            else:identities[key]=coords
    return extracted,errors,conflicts


def reconcile(network, inventory, profiles, source_leads, providers, output=None, progress=None):
    code_hash=hashlib.sha256(b''.join((Path(__file__).parent/name).read_bytes() for name in
        ('network_reconciliation.py','network_geometry.py','physical_crosswalk.py'))).hexdigest()
    network_hash=hashlib.sha256(json.dumps(network,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    extracted,errors,global_conflicts=prepare_sources(profiles,providers)
    lead_index={r['official_route_key']:r for r in source_leads['routes']}
    official={r['id']:[] for r in inventory['routes']};all_sections=[]
    for f in network['features']:
        p=f['properties'];number=p.get('RouteNo');kind=p.get('RouteType');key=f'{kind}:{number}'
        geom=shape(f['geometry'])
        for index,part in enumerate([geom] if geom.geom_type=='LineString' else geom.geoms):
            row={'key':f"FID:{p['FID']}:{index}",'route_type':kind,'route_no':number,'geometry':part,'properties':p}
            all_sections.append(row)
            if key in official:official[key].append(row)
    tree=STRtree([s['geometry'] for s in all_sections]);rows=[]
    if output:Path(output).mkdir(exist_ok=True,parents=True)
    for key,targets in sorted(official.items()):
        lead=lead_index.get(key,{});relations=lead.get('candidate_relation_ids',[])
        present=[extracted[r] for r in relations if r in extracted]
        ways={w['way_id']:w for p in present for w in p['ways'] if w['way_id'] not in global_conflicts}
        complete=bool(relations) and all(r in extracted and extracted[r]['provider_geometry_complete'] and extracted[r]['member_lists_complete'] for r in relations)
        complete=complete and not any(w['way_id'] in global_conflicts for p in present for w in p['ways'])
        fingerprint=hashlib.sha256(json.dumps({'code':code_hash,'network':network_hash,'lead':lead,
            'ways':[ways[i] for i in sorted(ways)],'complete':complete,
            'profiles':[{k:v for k,v in p.items() if k!='ways'} for p in present],
            'errors':{str(r):errors[r] for r in relations if r in errors}},sort_keys=True,separators=(',',':')).encode()).hexdigest()
        destination=Path(output)/(key.replace(':','-')+'.json.gz') if output else None
        if destination and destination.exists():
            try:cached=json.loads(gzip.decompress(destination.read_bytes()))
            except (OSError,EOFError,ValueError):cached={}
            if cached.get('input_fingerprint')==fingerprint:
                rows.append(cached['row'])
                if progress:progress(key,cached['row']['state']+' (cached)',len(rows))
                continue
        report=None;failure=None
        if ways and targets:
            try:
                extent=unary_union([s['geometry'] for s in targets]+[way_geometry(w) for w in ways.values()]).bounds
                origin=[(extent[0]+extent[2])/2,(extent[1]+extent[3])/2];project=metric_projector(origin)
                nearby=[all_sections[int(i)] for i in tree.query(box(extent[0]-.001,extent[1]-.001,extent[2]+.001,extent[3]+.001))]
                sections=[{'key':s['key'],'route_type':s['route_type'],'route_no':s['route_no'],
                    'line':LineString([project(c) for c in s['geometry'].coords])} for s in nearby]
                projected=[{'way_id':w['way_id'],'line':way_geometry(w,project)} for w in ways.values()]
                kind,number=key.split(':');report=compare_route(projected,sections,kind,int(number))
                report['projection']={'kind':'spherical_azimuthal_equidistant_approximate','origin':origin}
                properties={s['key']:s['properties'] for s in targets}
                for section in report['official_sections']:
                    p=properties[section['key']]
                    section['source_properties']={k:p.get(k) for k in ('OpenStatus','RouteCat','Desc_','Surface')}
            except (ValueError,TypeError) as error:failure=str(error)
        state='missing_source_membership' if not relations else disposition(report,source_complete=complete,scope_anchored=lead.get('uk_metadata_anchor',False))
        row={'official_route_key':key,'state':state,'candidate_relation_ids':relations,
            'geometry_relation_count':len(present),'source_way_count':len(ways),'source_geometry_complete':complete,
            'uk_metadata_anchor':lead.get('uk_metadata_anchor',False),
            'official_section_count':len(targets),'association_approved':False,'completion_ready':False,
            'missing_geometry_relation_ids':[r for r in relations if r not in extracted],
            'source_error_relations':{str(r):errors[r] for r in relations if r in errors},'comparison_error':failure}
        if report:
            row.update({k:report[k] for k in ('source_unique_length_m','official_unique_length_m','corridor_sensitivity')})
            # Flags prioritize inspection; no generated logical group is approved.
            offset=report['corridor_sensitivity']
            row['alternative_or_offset_review_needed']=offset['25']['official_fraction']-offset['3']['official_fraction']>.2
            row['reference_extent_gap_m_10m']=offset['10']['official_without_source_m']
        if output:
            detailed={**row,'row':row,'input_fingerprint':fingerprint,'comparison':report,'policy':'Explicit alternatives may share logical coverage; unique continuations remain required. Diagnostic corridors alone never approve a binding.'}
            temporary=destination.with_suffix('.tmp')
            temporary.write_bytes(gzip.compress(json.dumps(detailed,separators=(',',':')).encode(),mtime=0))
            temporary.replace(destination)
        rows.append(row)
        if progress:progress(key,state,len(rows))
    return {'purpose':'national_physical_correspondence_diagnostics_not_progress',
        'official_numbered_route_count':len(rows),'provider_relation_count':len(providers),
        'extracted_relation_count':len(extracted),
        'extracted_distinct_way_count':len({w['way_id'] for p in extracted.values() for w in p['ways'] if w['way_id'] not in global_conflicts}),
        'directional_roundabout_way_count':len({w for p in extracted.values() for w in p['directional_roundabout_way_ids']}),
        'source_geometry_diagnostics':[{k:v for k,v in p.items() if k!='ways'} for p in extracted.values()],
        'source_parse_errors':{str(k):v for k,v in errors.items()},
        'cross_profile_conflicting_way_ids':sorted(global_conflicts),'state_counts':dict(Counter(r['state'] for r in rows)),
        'routes':rows,'association_approved':False,'completion_ready':False,
        'osm_node_identity_verified':False,'scope':'All official numbered keys; named routes and mileposts are separate inventories.'}


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('network','inventory','profiles','source-leads','geometry-root','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--expected-network-sha256',required=True)
    args=parser.parse_args();raw=gzip.decompress(args.network.read_bytes())
    if hashlib.sha256(raw).hexdigest()!=args.expected_network_sha256:raise ValueError('Official snapshot checksum differs')
    fetch_raw=(args.geometry_root/'fetch-manifest.json').read_bytes()
    fetch=json.loads(fetch_raw);providers={}
    for source in fetch['sources']:
        data=gzip.decompress((args.geometry_root/source['file']).read_bytes())
        if hashlib.sha256(data).hexdigest()!=source['sha256']:raise ValueError('Provider source checksum differs')
        providers[source['relation_id']]=json.loads(data)
    def read(path):
        b=path.read_bytes();return json.loads(gzip.decompress(b) if path.suffix=='.gz' else b)
    profiles=read(args.profiles)['profiles']
    report=reconcile(json.loads(raw),read(args.inventory),profiles,read(args.source_leads),providers,
        output=args.output/'routes',progress=lambda key,state,count:print(count,key,state,flush=True) if count%20==0 else None)
    report['input_sha256']={'network':args.expected_network_sha256,'inventory':hashlib.sha256(args.inventory.read_bytes()).hexdigest(),
        'profiles':hashlib.sha256(args.profiles.read_bytes()).hexdigest(),'source_leads':hashlib.sha256(args.source_leads.read_bytes()).hexdigest(),
        'geometry_fetch_manifest':hashlib.sha256(fetch_raw).hexdigest()}
    report['fetch_complete']=fetch['complete'];report['fetch_error_count']=len(fetch['errors'])
    args.output.mkdir(exist_ok=True,parents=True)
    (args.output/'summary.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report['state_counts'],indent=2),flush=True)


if __name__=='__main__':main()
