"""Compile a pinned multi-format catalogue checkpoint without new admissions.

Legacy source-inventory and numbered-alternative contracts remain distinct.
The input manifest is a trusted checkpoint, not a new route identity approval.
"""
import argparse
import gzip
import json
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union

from matched_path_evidence import digest
from .association_bundle import read_bundle
from .coverage import Section,TrialReference
from .inventory_completion import read_completion as read_inventory
from .journey_replay import projector
from .membership_review import read_reviewed_overlay
from .numbered_alternative_review import read_reviewed_overlay as read_alternative
from .open_completion import build_verified_completion,read_completion,sha
from .partial_geometry_review import fingerprint
from .source_identity_review import read_catalogue


def pinned(root,record):
    path=(root/record['file']).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Checkpoint input escapes root')
    raw=path.read_bytes()
    if sha(raw)!=record['sha256']:
        raise ValueError('Checkpoint file changed')
    return raw


def decode(raw,name):
    return gzip.decompress(raw)if name.endswith('.gz')else raw


def verified_plan(raw,bundle,manifest,key):
    """Verify a previously admitted inventory's immutable version and receipt."""
    value=json.loads(raw)
    if (sha(raw)!=manifest['plan_sha256'] or sha(bundle)!=manifest['association_sha256']
            or value.get('route_key')!=key or value.get('scope')!=key+':source-inventory'
            or value.get('whole_published_route_ready') is not False
            or value.get('reference_version')!='inventory-completion:'+digest({k:v for k,v in value.items()if k!='reference_version'})
            or value['reference_version']!=manifest['reference_version']):
        raise ValueError('Pinned prior inventory differs')
    return value


def load_source(root,row,files):
    """Use the format's existing consumer before compiling its matching spans."""
    key=row['route_key'];kind=row['kind'];folder=(root/row['folder']).resolve()
    if not folder.is_relative_to(root.resolve()):
        raise ValueError('Checkpoint folder escapes root')
    def raw(name):
        return decode(files[(row['folder']+'/'+name)],name)
    branch=[];preserved=None
    if kind=='reviewed_open_catalogue':
        reference,metadata,paths,extent=read_catalogue(folder,sha(raw('manifest.json')))
        audit=json.loads(raw('audit.json.gz'))
        branch=[g for g in audit.get('roundabout_review',{}).get('reviewed',[])if g.get('policy')=='either_two_gate_arc']
    elif kind=='legacy_source_catalogue':
        payload=json.loads(raw('reference.json.gz'));metadata=payload['metadata']
        if (metadata.get('route_key')!=key or metadata.get('scope')!='explicit_member_source_spans_only_not_full_official_route'
                or metadata.get('whole_route_completion_ready') is not False
                or payload.get('reference_version')!='numbered-source-spans:'+digest(metadata)):
            raise ValueError('Legacy source catalogue identity differs')
        project=projector(metadata)
        if any(s['routes']!=[key+':eligible-source-spans']for s in payload['sections']):
            raise ValueError('Legacy source catalogue target differs')
        reference=TrialReference(payload['reference_version'],[Section(s['key'],LineString([project(c)for c in s['coordinates']]),tuple(s['routes']))for s in payload['sections']])
        bundle=raw('associations.json.gz')
        paths=read_bundle(bundle,reference,metadata['projection_origin'],digest(json.loads(bundle)))
        planraw=decode(files[row['inventory_plan']],row['inventory_plan'])
        planbundle=decode(files[row['inventory_associations']],row['inventory_associations'])
        planmanifest=json.loads(files[row['inventory_manifest']])
        preserved=verified_plan(planraw,planbundle,planmanifest,key)
        if preserved['metadata']['national_reference_sha256']!=metadata['national_reference_sha256']:
            raise ValueError('Source inventory national snapshot differs')
        branch=preserved['roundabout_groups'];extent=None
    elif kind in ('reviewed_membership_inventory','numbered_alternative_inventory'):
        previous=decode(files[row['previous_plan']],row['previous_plan'])
        manifest=json.loads(raw('manifest.json'))
        consumer=read_reviewed_overlay if kind=='reviewed_membership_inventory'else read_alternative
        ledger,paths,preserved=consumer(folder,previous,sha(raw('manifest.json')))
        if preserved['route_key']!=key:
            raise ValueError('Reviewed inventory route differs')
        if kind=='numbered_alternative_inventory':
            return {'preserved_alternative':True,'paths':paths,'plan':preserved,
                'plan_raw':raw('plan.json.gz'),'bundle':raw('associations.json.gz')}
        metadata=preserved['metadata'];keys={s for p in paths for s in p.section_keys}
        reference=TrialReference(ledger.reference.version,[s for k,s in ledger.reference.sections.items()if k in keys])
        branch=preserved['roundabout_groups'];extent=None
    else:
        raise ValueError('Unsupported checkpoint input kind')
    if metadata.get('route_key')!=key or not paths or any(p.target_geometry!='exact_source_spans'for p in paths):
        raise ValueError('Admitted exact-source catalogue required')
    return {'reference':reference,'metadata':metadata,'paths':paths,'extent':extent,
        'provenance':json.loads(raw('associations.json.gz'))['provenance'],
        'deferred_way_ids':sorted({w for g in branch for w in g['way_ids']}),
        'prior_roundabout_group_count':len(branch),'preserved_inventory':preserved is not None}


def open_extent(key,features,metadata,network_sha256):
    if metadata.get('national_reference_sha256')!=network_sha256:
        raise ValueError('Legacy catalogue official snapshot differs')
    kind,number=key.split(':')
    opened=[f for f in features if f['properties'].get('RouteType')==kind
        and str(f['properties'].get('RouteNo'))==number
        and f['properties'].get('RouteCat')=='Main Route'
        and f['properties'].get('OpenStatus')=='Open'
        and f['properties'].get('Desc_')in('OnRoad','TrafficFree')]
    if not opened:
        raise ValueError('Legacy accepted catalogue has no explicit open main target')
    project=projector(metadata);lines=[]
    for f in opened:
        g=f['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates']
        lines.extend(LineString([project(c)for c in part])for part in parts)
    return {'route_key':key,'features':opened,'basis':'official_open_main_only',
        'projection_origin':metadata['projection_origin'],'canonical_open_union_m':unary_union(lines).length,
        'unmatched_official_open_extent_retained':True,'completion_binding_ready':False}


def check_registry(root,pins,files):
    rows=pins['routes'];keys=[r['route_key']for r in rows];held=pins['held_route_keys']
    if (len(keys)!=219 or len(set(keys))!=219 or len(held)!=34 or len(set(held))!=34
            or set(keys)&set(held) or len(set(keys)|set(held))!=253):
        raise ValueError('Exact supported and held checkpoint identities required')
    registry={}
    for row in rows:
        data=json.loads(gzip.decompress(files[row['folder']+'/associations.json.gz']))
        for path in data['paths']:
            registry.setdefault(path['way_id'],set()).add(fingerprint(path['osm_node_ids'],path['coordinates']))
    actual=digest({k:sorted(v)for k,v in registry.items()})
    if (len(registry)!=62399 or actual!=pins['registry_sha256']
            or any(len(variants)!=1 for variants in registry.values())):
        raise ValueError('Pinned supported physical registry differs')


def read_checkpoint_route(folder,expected_manifest_sha256):
    """Dispatch a trusted checkpoint record without mixing denominator scopes."""
    folder=Path(folder);raw=(folder/'manifest.json').read_bytes()
    if sha(raw)!=expected_manifest_sha256:
        raise ValueError('Completion checkpoint manifest changed')
    manifest=json.loads(raw);kind=manifest.get('completion_kind')
    if kind=='captured_open_main':
        consumer=read_completion
    elif kind=='preserved_numbered_alternative_inventory'and manifest.get('input_kind')=='numbered_alternative_inventory':
        consumer=read_inventory
    else:
        raise ValueError('Unknown or reclassified checkpoint scope')
    ledger,paths,plan=consumer(gzip.decompress((folder/'plan.json.gz').read_bytes()),
        gzip.decompress((folder/'associations.json.gz').read_bytes()),
        manifest['plan_sha256'],manifest['association_sha256'])
    if (plan['route_key']!=manifest['route_key'] or plan['reference_version']!=manifest['reference_version']
            or folder.name!=manifest['route_key'].replace(':','-')):
        raise ValueError('Completion checkpoint route identity differs')
    return ledger,paths,plan,manifest


def compile_checkpoint(root,expected_pins_sha256,output):
    root=Path(root).resolve();raw=(root/'input-pins.json').read_bytes()
    if sha(raw)!=expected_pins_sha256:
        raise ValueError('Independent checkpoint input hash required')
    pins=json.loads(raw);files={}
    for record in pins['files']:
        if record['file']in files:
            raise ValueError('Duplicate checkpoint input file')
        files[record['file']]=pinned(root,record)
    check_registry(root,pins,files)
    network_raw=decode(files[pins['official_network']],pins['official_network'])
    network_hash=sha(network_raw)
    if network_hash!=pins['official_network_sha256']:
        raise ValueError('Checkpoint national snapshot changed')
    network=json.loads(network_raw)
    output=Path(output)
    if output.exists():
        raise ValueError('Fresh checkpoint output required')
    output.mkdir(parents=True);results=[]
    for row in pins['routes']:
        key=row['route_key'];loaded=load_source(root,row,files);folder=output/key.replace(':','-');folder.mkdir()
        source_pin=digest(row|{'files':{p:sha(files[p])for p in row['pinned_files']}})
        if loaded.get('preserved_alternative'):
            # Preserve NCN228's independent numbered-alternative inventory
            # verbatim; never silently relabel it as an open-main denominator.
            planraw,bundle=loaded['plan_raw'],loaded['bundle'];plan=loaded['plan']
            read_inventory(planraw,bundle,sha(planraw),sha(bundle))
            extra={'completion_kind':'preserved_numbered_alternative_inventory',
                'canonical_inventory_length_m':plan['canonical_inventory_length_m'],
                'prior_roundabout_group_count':len(plan['roundabout_groups'])}
        else:
            extent=loaded['extent']or open_extent(key,network['features'],loaded['metadata'],network_hash)
            ledger,bundle,plan=build_verified_completion(loaded['reference'],loaded['metadata'],loaded['paths'],
                extent,source_pin,loaded['provenance'],loaded['deferred_way_ids'])
            planraw=json.dumps(plan,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
            _,paths,_=read_completion(planraw,bundle,sha(planraw),sha(bundle))
            if json.loads(bundle)['paths']!=json.loads(gzip.decompress(files[row['folder']+'/associations.json.gz']))['paths']:
                raise ValueError('Checkpoint compilation changed admitted path rows')
            extra={'completion_kind':'captured_open_main','canonical_open_length_m':plan['canonical_open_length_m'],
                'maximum_bound_open_length_m':plan['maximum_bound_open_length_m'],
                'unbound_open_length_m':plan['unbound_open_length_m'],
                'deferred_alternative_way_ids':plan.get('deferred_alternative_way_ids',[]),
                'prior_roundabout_group_count':loaded['prior_roundabout_group_count'],
                'source_inventory_preserved':loaded['preserved_inventory']}
        (folder/'plan.json.gz').write_bytes(gzip.compress(planraw,mtime=0))
        (folder/'associations.json.gz').write_bytes(gzip.compress(bundle,mtime=0))
        result={'route_key':key,'input_kind':row['kind'],'input_contract_sha256':source_pin,
            'plan_sha256':sha(planraw),'association_sha256':sha(bundle),'reference_version':plan['reference_version'],**extra}
        (folder/'manifest.json').write_text(json.dumps(result,indent=2)+'\n');results.append(result)
        print(key,extra['completion_kind'],'deferred ways',len(extra.get('deferred_alternative_way_ids',[])),flush=True)
    summary={'schema_version':1,'input_pins_sha256':expected_pins_sha256,'supported_identities':219,
        'distinct_supported_ways':62399,'routes':results,'held_route_keys':pins['held_route_keys'],
        'new_source_admissions':0,'whole_published_route_ready':False,'android_activation_ready':False,
        'source_inventory_and_reviewed_alternative_contracts_preserved':True}
    (output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    return summary


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs',type=Path,required=True)
    parser.add_argument('--expected-pins-sha256',required=True)
    parser.add_argument('--output',type=Path,required=True)
    a=parser.parse_args();compile_checkpoint(a.inputs,a.expected_pins_sha256,a.output)
