"""Replay explicit remaining-route plans without changing historical triage.

Metadata-poor routes require a new pinned UK span-scope review. Only exact
source edges on publisher Open Main linework can pass that review. Held cases
remain first-class results; a completed review is not whole-route activation.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import LineString, shape
from shapely.ops import unary_union
from shapely.strtree import STRtree

from matched_path_evidence import digest
from .catalogue_extract import _source_index, _memberships_from_index
from .confirmed_paths import build_candidate
from .inventory_completion import inventory_ways
from .journey_replay import projector
from .network_geometry import metric_projector
from .numbered_reconciliation import uk_metadata_anchor, non_uk_namespace
from .partial_geometry_review import fingerprint, registry_checkpoint, existing_registry
from .source_capture import verify_snapshot
from .source_identity_review import read_catalogue


def sha(raw):return hashlib.sha256(raw).hexdigest()


def bounded_file(root, relative):
    path=(root/relative).resolve()
    if not path.is_relative_to(root.resolve()):raise ValueError('Evidence path escapes root')
    return path


def load_record(root, row):
    if row.get('error'):raise ValueError('Captured source unavailable: '+row['error'])
    raw=gzip.decompress(bounded_file(root,row['file']).read_bytes())
    if len(raw)>20_000_000 or sha(raw)!=row['sha256']:raise ValueError('Captured input changed')
    value=json.loads(raw)
    if value.get('remark') or value.get('error'):raise ValueError('Incomplete captured input')
    if row['kind']=='source':verify_snapshot(raw,row['relation_id'])
    if row['kind']=='official':
        count=json.loads(load_record(root,row['count_response'])[1])['count']
        pages=[f for page in row['pages'] for f in load_record(root,page)[0]['features']]
        ids=[f['properties']['FID'] for f in pages]
        if pages!=value['features'] or len(ids)!=count or len(set(ids))!=count:
            raise ValueError('Official page accounting differs')
    return value,raw


def propose_spans(ways, official, opened, conflicts):
    """Preclip scope-review proposals; ordinary admission gates run afterwards."""
    bounds=unary_union([shape(f['geometry'])for f in official]).bounds
    metric=metric_projector([(bounds[0]+bounds[2])/2,(bounds[1]+bounds[3])/2])
    def lines(feature):
        g=feature['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates']
        return [LineString([metric(c)for c in part])for part in parts]
    good=STRtree([line for f in opened for line in lines(f)])
    bad=STRtree([line for f in official if f not in opened for line in lines(f)])
    spans=[]
    for wid,way in sorted(ways.items()):
        if wid in conflicts:continue
        coordinates=[[n['lon'],n['lat']]for n in way['node_records']]
        start=None
        for i in range(len(coordinates)):
            ok=False
            if i<len(coordinates)-1:
                edge=LineString([metric(c)for c in coordinates[i:i+2]])
                hits=[good.geometries[int(j)]for j in good.query(edge.buffer(10))]
                excluded=[bad.geometries[int(j)]for j in bad.query(edge.buffer(10))]
                ok=(edge.length>1e-8 and edge.difference(unary_union([g.buffer(10)for g in hits])).length<=1e-5
                    and edge.intersection(unary_union([g.buffer(10)for g in excluded])).length<=1e-5)
            if ok and start is None:start=i
            if not ok and start is not None:
                spans.append({'way_id':wid,'from_vertex':start,'to_vertex':i});start=None
    return spans


def emit(folder, candidate, audit, review, raw_review, opened):
    bundle,payload,_=candidate
    project=projector(payload['metadata'])
    def geometry(feature):
        g=feature['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates']
        return unary_union([LineString([project(c)for c in part])for part in parts])
    official=unary_union([geometry(f)for f in opened])
    sections={s['key']:LineString([project(c)for c in s['coordinates']])for s in payload['sections']}
    support=unary_union(list(sections.values()));paths=json.loads(bundle)['paths']
    extent={'schema_version':1,'route_key':review['target_route_key'],'basis':'official_open_main_only',
        'projection_origin':payload['metadata']['projection_origin'],'features':opened,
        'canonical_open_union_m':official.length,'completion_binding_ready':False,
        'unmatched_official_open_extent_retained':True,'construction_closed_and_planned_connections_in_denominator':False}
    ref=json.dumps(payload,separators=(',',':')).encode();eraw=json.dumps(extent,indent=2).encode()+b'\n'
    files={'reference.json.gz':ref,'associations.json.gz':bundle,'identity-review.json':raw_review,
        'open-extent.json':eraw,'audit.json.gz':json.dumps(audit).encode()}
    folder.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():(folder/name).write_bytes(gzip.compress(data,mtime=0)if name.endswith('.gz')else data)
    manifest={'schema_version':1,'target_route_key':review['target_route_key'],
        'recorded_source_route_key':review['source_route_key'],'route_alias':False,'whole_published_route_ready':False,
        'reference_sha256':sha(ref),'association_sha256':sha(bundle),'source_identity_review_sha256':sha(raw_review),'open_extent_sha256':sha(eraw)}
    mraw=json.dumps(manifest,indent=2).encode()+b'\n';(folder/'manifest.json').write_bytes(mraw);read_catalogue(folder,sha(mraw))
    return {'manifest_sha256':sha(mraw),'official_open_union_m':official.length,
        'supported_source_union_m':support.length,'supported_source_union_m_by_mode':{
            mode:unary_union([sections[k]for p in paths if mode in p['modes']for k in p['section_keys']]).length
            for mode in ('on_foot','cycling')},
        'official_open_without_supported_source_at_10m_m':official.difference(support.buffer(10)).length,
        'supported_way_ids':[p['way_id']for p in paths]}


def assess(evidence, expected_pins_sha256, previous_folders, retry_keys=(), previous_summary_sha256=None):
    evidence=Path(evidence).resolve();pinraw=(evidence/'input-pins.json').read_bytes()
    if sha(pinraw)!=expected_pins_sha256:raise ValueError('Independent remainder pins changed')
    pins=json.loads(pinraw);queue_raw=(evidence/'queue.json').read_bytes();planraw=(evidence/'plans.json').read_bytes()
    capraw=(evidence/'capture/manifest.json').read_bytes();discoveryraw=(evidence/'discovery/manifest.json').read_bytes()
    if any(sha(raw)!=pins[name]for name,raw in [('queue',queue_raw),('plans',planraw),('capture',capraw),('discovery',discoveryraw)]):
        raise ValueError('Remainder input receipt changed')
    queue=json.loads(queue_raw);plans=json.loads(planraw)['routes'];manifest=json.loads(capraw)
    if len(queue['routes'])!=54 or {r['official_route_key']for r in queue['routes']}!={p['route_key']for p in plans}or len(plans)!=54:
        raise ValueError('Exactly the independently selected 54 route cases required')
    if manifest['queue_sha256']!=sha(queue_raw):raise ValueError('Capture queue differs')
    registry=registry_checkpoint(previous_folders,pins['existing_registry'],44622,194,[p['route_key']for p in plans])
    records={}
    for record in manifest['sources']:
        identity=record['kind'],record['relation_id']
        if identity in records:raise ValueError('Duplicate capture identity')
        records[identity]=(evidence/'capture',record)
    discovery=json.loads(discoveryraw)
    for record in discovery['sources']:
        identity=record['kind'],record['relation_id']
        if identity not in records:records[identity]=(evidence/'discovery',record)
    cache={}
    def get(kind,identity):
        key=kind,identity
        if key not in records:raise ValueError(f'Missing captured {key}')
        if key not in cache:cache[key]=load_record(*records[key])
        return cache[key]
    output=evidence/'output';output.mkdir(exist_ok=True)
    previous_results=[]
    if retry_keys:
        previous_raw=(output/'summary.json').read_bytes()
        if not previous_summary_sha256 or sha(previous_raw)!=previous_summary_sha256:
            raise ValueError('Independent preceding review summary checksum required')
        previous=json.loads(previous_raw)
        if (previous.get('input_pins_sha256')!=expected_pins_sha256 or len(previous['routes'])!=54
                or {r['route_key']for r in previous['routes']}!={p['route_key']for p in plans}
                or len(set(retry_keys))!=len(retry_keys)
                or not set(retry_keys)<={r['route_key']for r in previous['routes']if r['status']=='held'}):
            raise ValueError('Only explicitly held cases from the same pinned review may be retried')
        previous_results=[r for r in previous['routes']if r['route_key']not in retry_keys]
        for row in previous_results:
            if row['status']=='supported_open_spans':
                folder=output/row['route_key'].replace(':','-')
                read_catalogue(folder,row['manifest_sha256'])
                paths=json.loads(gzip.decompress((folder/'associations.json.gz').read_bytes()))['paths']
                if {p['way_id']for p in paths}!=set(row['supported_way_ids']):
                    raise ValueError('Preceding review route-way accounting differs')
    (output/'summary.json').unlink(missing_ok=True)
    results=previous_results
    new={wid for r in previous_results if r['status']=='supported_open_spans'for wid in r['supported_way_ids']}
    for plan in plans:
        key=plan['route_key'];folder=output/key.replace(':','-')
        if retry_keys and key not in retry_keys:continue
        if folder.exists():raise ValueError('Use a fresh output folder for each complete replay')
        row={'route_key':key,'original_state':plan['original_state'],'status':'held',
            'completion_binding_ready':False,'plan':plan}
        try:
            network,network_raw=get('official',key);official=network['features'];network_hash=sha(network_raw)
            opened=[f for f in official if f['properties'].get('RouteCat')=='Main Route'and f['properties'].get('OpenStatus')=='Open'and f['properties'].get('Desc_')in('OnRoad','TrafficFree')]
            row.update(official_feature_count=len(official),official_open_main_feature_count=len(opened),
                official_classifications=dict(Counter(str((f['properties'].get('OpenStatus'),f['properties'].get('RouteCat')))for f in official)))
            if not opened:
                row['reason']='no_explicit_open_main_extent';results.append(row);continue
            roots=plan['source_relation_ids']
            if not roots:
                row['reason']='current_numbered_source_membership_unresolved';results.append(row);continue
            raws=[get('source',i)[1]for i in plan['source_hierarchy_ids']]
            index,hashes=_source_index(raws);profiles={i:_memberships_from_index(index,hashes,i)for i in roots}
            permitted_empty=plan.get('approved_empty_relation_ids',[])
            if any((not p['ways']or p['missing_relations']or p['cyclic_relation_ids']or p['unsupported_relation_ids']
                    or set(p['empty_relation_ids'])-set(permitted_empty))for p in profiles.values()):
                raise ValueError('Current source hierarchy incomplete')
            source_key=plan.get('source_route_key',key);sk,sn=source_key.split(':')
            for i in roots:
                tags=index['relation',i]['tags']
                if tags.get('network')!=sk.lower()or tags.get('ref')!=sn or non_uk_namespace(tags):
                    raise ValueError('Reviewed source classification differs')
            providers={i:get('provider',i)[0]for i in roots}
            if permitted_empty:
                # Do not relax the historical inventory builder. This explicit
                # source review lists actual current ways; XML verification in
                # validate_review must confirm every zero-member placeholder.
                ways={}
                for wid in {w['way_id']for profile in profiles.values()for w in profile['ways']}:
                    w=index.get(('way',wid),{})
                    if len(w.get('nodes',[]))<2 or any(('node',n)not in index for n in w['nodes']):
                        raise ValueError('Complete current source nodes required')
                    ways[wid]={**w,'node_records':[index['node',n]for n in w['nodes']]}
            else:ways=inventory_ways(index,roots)
            conflicts={wid for wid,w in ways.items()if str(wid)in registry and registry[str(wid)]!={fingerprint(w['nodes'],[[n['lon'],n['lat']]for n in w['node_records']])}}
            scope=key.startswith('RCN:')or not all(uk_metadata_anchor(index['relation',i]['tags'])for i in roots)
            spans=(propose_spans(ways,official,opened,conflicts)if scope else
                [{'way_id':wid,'from_vertex':0,'to_vertex':len(w['nodes'])-1}for wid,w in sorted(ways.items())if wid not in conflicts])
            if not spans:
                row['reason']='no_approved_source_edges_on_open_main_extent';results.append(row);continue
            review={'schema_version':1,'source_route_key':source_key,'target_route_key':key,
                'policy':'official_uk_spans_scope_review'if scope else 'partial_provider_verified_spans_only',
                'route_alias':False,'old_journey_evidence_transferred':False,'whole_published_route_ready':False,
                'official_network_sha256':network_hash,'official_features_sha256':digest(official),
                'approved_open_features_sha256':digest(opened),'source_snapshot_sha256':digest(sorted(hashes)),
                'provider_sha256':digest(providers),'source_relations_sha256':digest([index['relation',i]for i in roots]),
                'source_relation_ids':roots,'approved_official_feature_ids':sorted(f['properties']['FID']for f in opened),
                'approved_source_spans':spans,'identity_evidence_urls':[records['official',key][1]['url']],
                'existing_registry_sha256':pins['existing_registry'],'original_triage_state':plan['original_state']}
            if scope:review['uk_scope_basis']='exact_source_spans_on_pinned_official_uk_open_extent'
            if permitted_empty:
                review['approved_empty_relation_ids']=permitted_empty
                review['empty_relation_evidence']=[]
                for rid in permitted_empty:
                    pin=next(x for x in discovery['empty_relation_evidence']if x['relation_id']==rid)
                    receipt_raw=bounded_file(evidence/'discovery',pin['receipt_file']).read_bytes()
                    if sha(receipt_raw)!=pin['receipt_sha256']:raise ValueError('Empty-relation receipt changed')
                    receipt=json.loads(receipt_raw)
                    xml=bounded_file(evidence/'discovery',receipt['file']).read_text()
                    review['empty_relation_evidence'].append({**receipt,'xml':xml})
            raw_review=json.dumps(review,indent=2).encode()+b'\n'
            candidate,audit=build_candidate(key,roots,profiles,index,hashes,network,network_hash,providers,
                source_identity_review=raw_review,expected_identity_review_sha256=sha(raw_review))
            row.update(catalogue_audit=audit,current_source_way_count=len(ways),existing_registry_identity_conflict_way_ids=sorted(conflicts))
            if candidate is None:
                row['reason']='no_source_span_passes_all_admission_gates';results.append(row);continue
            # Reduce the review to the final admitted spans, retaining every gate.
            review['approved_source_spans']=[{k:s[k]for k in ('way_id','from_vertex','to_vertex')}for s in candidate[1]['sections']]
            raw_review=json.dumps(review,indent=2).encode()+b'\n'
            candidate,audit=build_candidate(key,roots,profiles,index,hashes,network,network_hash,providers,
                source_identity_review=raw_review,expected_identity_review_sha256=sha(raw_review))
            metrics=emit(folder,candidate,audit,review,raw_review,opened);row.update(metrics,catalogue_audit=audit,status='supported_open_spans')
            row['source_roots']=[index['relation',i]for i in roots]
            row['shared_existing_way_ids']=sorted(set(metrics['supported_way_ids'])&registry.keys())
            new.update(metrics['supported_way_ids'])
        except (ValueError,KeyError,TypeError) as error:
            row['status']='held';row.pop('supported_way_ids',None)
            row['reason']='evidence_or_identity_hold';row['detail']=str(error)
            if folder.exists():
                for name in ('manifest.json','associations.json.gz','reference.json.gz'):
                    (folder/name).unlink(missing_ok=True)
        results.append(row)
        print(key,row['status'],row.get('reason',''),flush=True)
    order={p['route_key']:i for i,p in enumerate(plans)}
    results.sort(key=lambda r:order[r['route_key']])
    summary={'schema_version':1,'input_pins_sha256':expected_pins_sha256,'reviewed_route_cases':54,
        'supported_new_route_keys':sum(r['status']=='supported_open_spans'for r in results),
        'held_cases':sum(r['status']=='held'for r in results),'previous_supported_catalogues':194,
        'supported_catalogues':194+sum(r['status']=='supported_open_spans'for r in results),
        'previous_distinct_supported_ways':44622,'distinct_supported_ways':len(registry.keys()|new),
        'new_distinct_supported_ways':len(new-registry.keys()),'routes':results,
        'completion_binding_ready':False,'app_release':False,'legacy_holds_outside_this_queue':queue['legacy_holds_outside_this_queue']}
    (output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    return summary


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--expected-pins-sha256',required=True)
    parser.add_argument('--previous-folders',nargs='+',type=Path,required=True)
    parser.add_argument('--retry-held-route',action='append',default=[])
    parser.add_argument('--expected-previous-summary-sha256')
    args=parser.parse_args();assess(args.evidence,args.expected_pins_sha256,args.previous_folders,
        args.retry_held_route,args.expected_previous_summary_sha256)
