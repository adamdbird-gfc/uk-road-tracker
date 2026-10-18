"""Bounded open-span assessment of explicitly selected partial-provider cases.

Replay uses independently pinned captures and existing registry fingerprints.
Unrepresented/conflicting provider ways and non-open source memberships remain
held. No ordinary queue is promoted and no historical inventory is replaced.
"""
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union
from matched_path_evidence import digest
from .association_bundle import build_bundle
from .catalogue_extract import _source_index, _memberships_from_index
from .confirmed_paths import build_candidate
from .inventory_completion import inventory_ways
from .journey_replay import projector
from .network_geometry import extract_geometry
from .numbered_reconciliation import uk_metadata_anchor, non_uk_namespace
from .source_capture import verify_snapshot
from .source_identity_review import read_catalogue


def sha(raw):return hashlib.sha256(raw).hexdigest()


def fingerprint(nodes,coordinates):
    pairs=tuple((n,*c)for n,c in zip(nodes,coordinates))
    if len(nodes)!=len(coordinates) or not pairs:
        raise ValueError('Complete source node identity required')
    return digest(min(pairs,tuple(reversed(pairs))))


def existing_registry(folders):
    registry={}
    for folder in folders:
        for p in sorted(Path(folder).glob('[NR]CN-*/associations.json.gz')):
            for row in json.loads(gzip.decompress(p.read_bytes()))['paths']:
                registry.setdefault(row['way_id'],set()).add(fingerprint(row['osm_node_ids'],row['coordinates']))
    if not registry:raise ValueError('Pinned existing registry required')
    return registry,digest({k:sorted(v)for k,v in registry.items()})


def select_cases(report,profiles,keys):
    if not keys or len(set(keys))!=len(keys):
        raise ValueError('Explicit distinct partial-review keys required')
    rows={r['official_route_key']:r for r in report['routes']}
    if len(rows)!=len(report['routes']) or not set(keys)<=rows.keys():
        raise ValueError('Selected partial-review route missing or duplicated')
    result=[rows[k]for k in sorted(keys)]
    for r in result:
        if (not r['official_route_key'].startswith('NCN:') or r['state']!='partial_source_geometry'
                or not r['uk_metadata_anchor'] or r.get('comparison_error')
                or not r['candidate_relation_ids']):
            raise ValueError('UK-anchored partial-source review cases required')
        if any(i not in profiles or not profiles[i]['member_lists_complete']for i in r['candidate_relation_ids']):
            raise ValueError('Complete original membership profiles required')
    return result


def load_capture(folder,expected_sha):
    folder=Path(folder);raw=(folder/'manifest.json').read_bytes()
    if sha(raw)!=expected_sha:raise ValueError('Partial-review capture manifest changed')
    manifest=json.loads(raw)
    if not manifest.get('complete'):raise ValueError('Required capture incomplete')
    values={}
    for record in manifest['sources']:
        path=(folder/record['file']).resolve()
        if not path.is_relative_to(folder.resolve()):raise ValueError('Capture file escapes evidence folder')
        data=gzip.decompress(path.read_bytes())
        if len(data)>20_000_000 or sha(data)!=record['sha256']:
            raise ValueError('Partial-review response changed')
        identity=(record['kind'],record['relation_id'])
        if identity in values:raise ValueError('Duplicate capture response')
        if record['kind']=='source':verify_snapshot(data,record['relation_id'])
        value=json.loads(data)
        if value.get('error') or value.get('remark') or value.get('exceededTransferLimit'):
            raise ValueError('Incomplete provider/official response')
        if record['kind']=='provider' and (value.get('type')!='relation' or value.get('id')!=record['relation_id']):
            raise ValueError('Provider response relation identity differs')
        if record['kind']=='official' and (value.get('type')!='FeatureCollection' or not isinstance(value.get('features'),list)):
            raise ValueError('Complete official feature collection required')
        if record['kind'] not in ('source','provider','official'):
            raise ValueError('Unknown captured source kind')
        values[identity]=(value,data)
    for record in manifest.get('supplementary_attempts',[]):
        path=(folder/record['file']).resolve()
        if (not path.is_relative_to(folder.resolve()) or record.get('required_for_admission') is not False
                or sha(path.read_bytes())!=record['sha256']):
            raise ValueError('Supplementary observation receipt changed')
    return manifest,values


def registry_checkpoint(folders, expected_sha, expected_ways, expected_catalogues, new_keys=()):
    if (type(expected_ways) is not int or expected_ways <= 0
            or type(expected_catalogues) is not int or expected_catalogues <= 0):
        raise ValueError('Explicit positive registry checkpoint counts required')
    registry, registry_sha = existing_registry(folders)
    keys = [p.parent.name for folder in folders
            for p in Path(folder).glob('[NR]CN-*/associations.json.gz')]
    if (registry_sha != expected_sha or len(registry) != expected_ways
            or len(keys) != expected_catalogues or len(set(keys)) != len(keys)):
        raise ValueError('Pinned existing catalogue registry changed or duplicated')
    if set(keys) & {key.replace(':','-') for key in new_keys}:
        raise ValueError('Selected route already exists in the registry checkpoint')
    return registry


def assess(research,capture,capture_sha,network_sha,report_sha,profiles_sha,existing,existing_sha,keys,output,
           *, expected_existing_way_count=43103, previous_supported_catalogues=183):
    research=Path(research);output=Path(output)
    rraw=(research/'physical-national/summary.json').read_bytes()
    praw=gzip.decompress((research/'numeric-route-membership-profiles.json.gz').read_bytes())
    if sha(rraw)!=report_sha or sha(praw)!=profiles_sha:
        raise ValueError('Pinned original triage inputs changed')
    previous={p['relation_id']:p for p in json.loads(praw)['profiles']}
    rows=select_cases(json.loads(rraw),previous,keys)
    registry=registry_checkpoint(existing,existing_sha,expected_existing_way_count,previous_supported_catalogues,keys)
    manifest,values=load_capture(capture,capture_sha)
    if set(manifest['route_keys'])!=set(keys) or len(manifest['route_keys'])!=len(keys):
        raise ValueError('Captured route scope differs')
    roots={i for r in rows for i in r['candidate_relation_ids']}
    required={v['id']for i in roots for v in previous[i]['source_relations']}
    if {i for kind,i in values if kind=='source'}!=required or not roots<={i for kind,i in values if kind=='provider'}:
        raise ValueError('Complete selected hierarchy and root providers required')
    if set(i for kind,i in values if kind=='provider')-required:
        raise ValueError('Provider outside the selected hierarchy')
    if sha(values['official',0][1])!=network_sha:
        raise ValueError('Pinned official geometry changed')
    network=values['official',0][0]
    index,hashes=_source_index([values['source',i][1]for i in sorted(required)])
    current={i:_memberships_from_index(index,hashes,i)for i in roots}
    output.mkdir(parents=True,exist_ok=True);(output/'summary.json').unlink(missing_ok=True)
    result_rows=[];admitted=set()
    for row in rows:
        key=row['official_route_key'];kind,number=key.split(':');ids=row['candidate_relation_ids']
        folder=output/key.replace(':','-');folder.mkdir(exist_ok=True)
        (folder/'manifest.json').unlink(missing_ok=True)
        ways=inventory_ways(index,ids)
        providers={i:values['provider',i][0]for i in ids}
        for i in ids:
            tags=current[i]['tags']
            if (not uk_metadata_anchor(tags) or non_uk_namespace(tags)
                    or tags.get('network')!='ncn' or tags.get('ref')!=number):
                raise ValueError('Current selected source classification differs')
        geometries={i:extract_geometry(providers[i],current[i])for i in ids}
        official=[f for f in network['features']if f['properties'].get('RouteType')==kind and f['properties'].get('RouteNo')==int(number)]
        opened=[f for f in official if f['properties'].get('RouteCat')=='Main Route'and f['properties'].get('OpenStatus')=='Open'and f['properties'].get('Desc_')in('TrafficFree','OnRoad')]
        if not opened:raise ValueError('No explicitly open main extent in selected case')
        conflicts=[]
        for wid,w in ways.items():
            coords=[[n['lon'],n['lat']]for n in w['node_records']]
            if str(wid)in registry and registry[str(wid)]!={fingerprint(w['nodes'],coords)}:conflicts.append(wid)
        review={'schema_version':1,'source_route_key':key,'target_route_key':key,
            'policy':'partial_provider_verified_spans_only','route_alias':False,
            'old_journey_evidence_transferred':False,'whole_published_route_ready':False,
            'official_network_sha256':network_sha,'official_features_sha256':digest(official),
            'approved_open_features_sha256':digest(opened),'source_snapshot_sha256':digest(sorted(hashes)),
            'provider_sha256':digest(providers),'source_relations_sha256':digest([index['relation',i]for i in ids]),
            'source_relation_ids':sorted(ids),'approved_official_feature_ids':sorted(f['properties']['FID']for f in opened),
            'approved_source_spans':[{'way_id':wid,'from_vertex':0,'to_vertex':len(w['nodes'])-1}for wid,w in sorted(ways.items())if wid not in conflicts],
            'identity_evidence_urls':[r['url']for r in manifest['sources']if r['kind']=='official'or(r['kind']=='source'and r['relation_id']in ids)],
            'existing_registry_sha256':existing_sha,'existing_registry_identity_conflict_way_ids':conflicts}
        def build(r):
            raw=json.dumps(r,indent=2).encode()+b'\n'
            return raw,build_candidate(key,ids,current,index,hashes,network,network_sha,providers,
                source_identity_review=raw,expected_identity_review_sha256=sha(raw))
        _,(candidate,audit)=build(review)
        if not candidate:
            result_rows.append({'route_key':key,'status':'held','audit':audit,'existing_registry_identity_conflict_way_ids':conflicts});continue
        review['approved_source_spans']=[{k:s[k]for k in('way_id','from_vertex','to_vertex')}for s in candidate[1]['sections']]
        raw_review,(candidate,audit)=build(review)
        bundle,payload,metrics=candidate
        project=projector(payload['metadata'])
        def geom(f):
            g=f['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates']
            return unary_union([LineString([project(c)for c in part])for part in parts])
        open_line=unary_union([geom(f)for f in opened])
        support=unary_union([LineString([project(c)for c in s['coordinates']])for s in payload['sections']])
        sections={s['key']:LineString([project(c)for c in s['coordinates']])for s in payload['sections']}
        paths=json.loads(bundle)['paths']
        mode_lengths={mode:unary_union([sections[k]for p in paths if mode in p['modes']for k in p['section_keys']]).length
                      for mode in ('on_foot','cycling')}
        extent={'schema_version':1,'route_key':key,'basis':'official_open_main_only',
            'projection_origin':payload['metadata']['projection_origin'],'features':opened,
            'canonical_open_union_m':open_line.length,'completion_binding_ready':False,
            'unmatched_official_open_extent_retained':True,
            'construction_closed_and_planned_connections_in_denominator':False}
        eraw=json.dumps(extent,indent=2).encode()+b'\n';ref_raw=json.dumps(payload,separators=(',',':')).encode()
        files={'reference.json.gz':ref_raw,'associations.json.gz':bundle,'identity-review.json':raw_review,
               'open-extent.json':eraw,'audit.json.gz':json.dumps(audit).encode()}
        for name,data in files.items():(folder/name).write_bytes(gzip.compress(data,mtime=0)if name.endswith('.gz')else data)
        m={'schema_version':1,'target_route_key':key,'recorded_source_route_key':key,'route_alias':False,
           'whole_published_route_ready':False,'reference_sha256':sha(ref_raw),'association_sha256':sha(bundle),
           'source_identity_review_sha256':sha(raw_review),'open_extent_sha256':sha(eraw)}
        mraw=json.dumps(m,indent=2).encode()+b'\n';(folder/'manifest.json').write_bytes(mraw)
        read_catalogue(folder,sha(mraw))
        new_ids={p['way_id']for p in json.loads(bundle)['paths']};admitted.update(new_ids)
        deltas={str(i):{'added_way_ids':sorted({w['way_id']for w in current[i]['ways']}-{w['way_id']for w in previous[i]['ways']}),
                       'removed_way_ids':sorted({w['way_id']for w in previous[i]['ways']}-{w['way_id']for w in current[i]['ways']})}for i in ids}
        result_rows.append({'route_key':key,'status':'supported_open_spans','current_source_way_count':len(ways),
            'membership_changes':deltas,'source_roots':[{'id':i,'version':index['relation',i].get('version'),'tags':index['relation',i]['tags']}for i in ids],
            'provider_geometry_reviews':{str(i):{k:v for k,v in g.items()if k!='ways'}for i,g in geometries.items()},
            'source_relations':[{'id':i,'state':index['relation',i].get('tags',{}).get('state')}for i in sorted(required)if i in {r['id']for rid in ids for r in current[rid]['source_relations']}],
            'official_open_feature_count':len(opened),'official_nonqualifying_feature_count':len(official)-len(opened),
            'official_open_union_m':open_line.length,'supported_source_union_m':support.length,
            'supported_source_union_m_by_mode':mode_lengths,
            'official_open_without_supported_source_at_10m_m':open_line.difference(support.buffer(10)).length,
            'diagnostic_corridors_grant_credit':False,'completion_binding_ready':False,
            'existing_registry_identity_conflict_way_ids':conflicts,'shared_existing_way_ids':sorted(new_ids&registry.keys()),
            'catalogue_audit':audit,'review':review,'manifest_sha256':sha(mraw)})
    summary={'schema_version':1,'route_keys':sorted(keys),'scope':'Explicit partial-provider open-span review; not full-route certification',
        'capture_sha256':capture_sha,'official_sha256':network_sha,'physical_report_sha256':report_sha,
        'membership_profiles_sha256':profiles_sha,'existing_registry_sha256':existing_sha,'routes':result_rows,
        'previous_supported_catalogues':previous_supported_catalogues,'supported_catalogues':previous_supported_catalogues+sum(r['status']=='supported_open_spans'for r in result_rows),
        'previous_distinct_supported_ways':len(registry),'distinct_supported_ways':len(registry.keys()|admitted),
        'new_distinct_supported_ways':len(admitted-registry.keys()),'shared_existing_way_ids':sorted(admitted&registry.keys()),
        'whole_published_route_ready':False,'app_release':False,'android_internal_trial_route_count':8}
    (output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    return summary


def main():
    import argparse
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('research','capture','output'):p.add_argument('--'+name,type=Path,required=True)
    for name in ('capture-sha','network-sha','report-sha','profiles-sha','existing-sha'):p.add_argument('--'+name,required=True)
    p.add_argument('--expected-existing-way-count',type=int,default=43103)
    p.add_argument('--previous-supported-catalogues',type=int,default=183)
    p.add_argument('--existing',nargs='+',type=Path,required=True);p.add_argument('--route-keys',nargs='+',required=True)
    a=p.parse_args();s=assess(a.research,a.capture,a.capture_sha,a.network_sha,a.report_sha,a.profiles_sha,a.existing,a.existing_sha,a.route_keys,a.output,
        expected_existing_way_count=a.expected_existing_way_count,previous_supported_catalogues=a.previous_supported_catalogues)
    print(json.dumps({k:v for k,v in s.items()if k!='routes'},indent=2))


if __name__=='__main__':main()
