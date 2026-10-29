"""Offline Stage 1.1 correction/attainability receipt; never publishes a catalogue.

Prepare from the immutable shipped DB and checkpoint. Apply the small correction
artifact to those exact shipped bytes to reproduce the candidate without a
checkpoint download. Synthetic capacity is not a real user's journey evidence.
"""
import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import sqlite3
from types import SimpleNamespace

from shapely.geometry import LineString
from shapely.strtree import STRtree
from scripts.build_ncn_national import compile_path_bindings, PINS
from scripts.ncn_reference import sha_file, verify_database
from scripts.ncn_trial.coverage import merge_intervals
from scripts.ncn_trial.open_completion import POLICY

BASELINE_SHA = '973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093'
CHECKPOINT_SHA = 'e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b'
ROOT = Path(__file__).resolve().parents[1]


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(value):
    return hashlib.sha256(encoded(value)).hexdigest()


def content_sha(database):
    """SQLite writer versions may change file layout, never reference values."""
    h=hashlib.sha256()
    orders={'routes':'key','paths':'route,way','nodes':'node,way','network':'id',
            'meta':'key','targets':'route,key','overview':'key'}
    with sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True) as db:
        names={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if names!=set(orders):raise ValueError('Unexpected catalogue tables')
        for table,order in sorted(orders.items()):
            h.update(encoded(table)+b'\n')
            for row in db.execute('SELECT * FROM '+table+' ORDER BY '+order):
                values=[json.loads(gzip.decompress(v)) if isinstance(v,bytes) else v for v in row]
                h.update(encoded(values)+b'\n')
    return h.hexdigest()


def missing(length, spans):
    out=[];start=0.
    for a,z in merge_intervals(spans):
        if a < -1e-7 or z > length+1e-7:raise ValueError('Target interval out of bounds')
        a,z=max(0.,a),min(length,z)
        if a>start:out.append((start,a))
        start=max(start,z)
    if start<length:out.append((start,length))
    return [(a,z) for a,z in out if z-a>1e-7]


def target_intervals(paths, mode):
    covered=defaultdict(list)
    for path in paths:
        for b in path['bindings']:
            if not b['modes'] or not set(b['modes'])<=set(path['modes'])&{'on_foot','cycling'}:
                raise ValueError('Unsupported binding mode')
            if mode=='combined' or mode in b['modes']:
                covered[b['target']].append((b['low'],b['high']))
    return {k:merge_intervals(v) for k,v in covered.items()}


def route_capacity(paths, targets):
    result={'target_m':sum(t['length'] for t in targets)}
    for mode in ('on_foot','cycling','combined'):
        covered=target_intervals(paths,mode)
        result[mode+'_m']=sum(z-a for t in targets for a,z in covered.get(t['key'],[]))
    result['unbound_m']=max(0.,result['target_m']-result['combined_m'])
    result['attainable']=result['unbound_m']<=max(1e-7,result['target_m']*1e-12)
    result['combined_percent']=100*result['combined_m']/result['target_m']
    return result


def network_intervals(targets, covered, network):
    # Same piecewise projection and union as Android NcnLedger.prepare().
    for t in targets:
        for edge,a,z,length in t['network']:
            for low,high in covered.get(t['key'],[]):
                low,high=max(low,min(a,z)),min(high,max(a,z))
                if high>low:network[edge].append(((low-a)/(z-a)*length,(high-a)/(z-a)*length))


def apply_patch(database, patch, output):
    if sha_file(database)!=BASELINE_SHA or patch['baseline_sha256']!=BASELINE_SHA:
        raise ValueError('Exact shipped baseline required')
    output=Path(output)
    if output.exists():raise ValueError('Fresh candidate path required')
    output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(database,output)
    with sqlite3.connect(output) as db:
        for row in patch['updates']:
            raw=db.execute('SELECT data FROM paths WHERE route=? AND way=?',(row['route'],row['way'])).fetchone()
            if not raw:raise ValueError('Unknown source identity')
            path=json.loads(gzip.decompress(raw[0]))
            if digest(path['bindings'])!=row['previous_bindings_sha256']:
                raise ValueError('Source bindings changed')
            path['bindings']=row['bindings']
            db.execute('UPDATE paths SET data=? WHERE route=? AND way=?',
                (gzip.compress(encoded(path),compresslevel=6,mtime=0),row['route'],row['way']))
        db.commit();db.execute('VACUUM')
    if patch.get('candidate_content_sha256') and content_sha(output)!=patch['candidate_content_sha256']:
        raise ValueError('Candidate reference values differ')
    if patch.get('candidate_sha256') and sha_file(output)!=patch['candidate_sha256']:
        if patch.get('sqlite_writer_version')==sqlite3.sqlite_version or not patch.get('candidate_content_sha256'):
            raise ValueError('Candidate reproduction differs')
        print('SQLite writer layout differs; all reference values match. Candidate SHA256:',sha_file(output))
    return output


def read_targets(db, route):
    return [dict(key=k,length=n,coords=json.loads(c),network=json.loads(v))
        for k,n,c,v in db.execute('SELECT key,length,coords,network FROM targets WHERE route=? ORDER BY key',(route,))]


def require_attainable(report):
    blocked=[r['route_key'] for r in report['routes'] if not r['after']['attainable']]
    if blocked or not report['after_network']['attainable']:
        raise ValueError('Completion gate blocked: '+str(len(blocked))+' routes; network attainable='+
                         str(report['after_network']['attainable']))


def prepare(checkpoint, database, manifest, output):
    checkpoint,output=Path(checkpoint),Path(output)
    if output.exists():raise ValueError('Fresh output directory required')
    verify_database(database,manifest)
    if manifest['sha256']!=BASELINE_SHA:raise ValueError('Exact shipped manifest required')
    pins_raw=(checkpoint/'inputs/input-pins.json').read_bytes()
    summary_raw=(checkpoint/'plans/summary.json').read_bytes()
    if hashlib.sha256(pins_raw).hexdigest()!=PINS or hashlib.sha256(summary_raw).hexdigest()!=CHECKPOINT_SHA:
        raise ValueError('Pinned completion plans required')
    summary=json.loads(summary_raw);output.mkdir(parents=True)
    patch={'schema_version':1,'kind':'exact-source-section-offsets','baseline_sha256':BASELINE_SHA,
        'checkpoint_sha256':CHECKPOINT_SHA,'input_pins_sha256':PINS,'updates':[]}
    network={m:defaultdict(list) for m in ('on_foot','cycling','combined')}
    result=[];causes=Counter();counts=Counter();route_updates=Counter()
    db=sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)
    with gzip.GzipFile(filename=str(output/'unbound-intervals.jsonl.gz'),mode='wb',mtime=0) as blockers:
        for record in summary['routes']:
            route=record['route_key'];folder=checkpoint/'plans'/route.replace(':','-')
            plan_raw=gzip.decompress((folder/'plan.json.gz').read_bytes());association_raw=gzip.decompress((folder/'associations.json.gz').read_bytes())
            if (hashlib.sha256(plan_raw).hexdigest()!=record['plan_sha256'] or
                hashlib.sha256(association_raw).hexdigest()!=record['association_sha256']):raise ValueError('Plan pin differs')
            plan=json.loads(plan_raw);associations=json.loads(association_raw)
            alternative=record['completion_kind']!='captured_open_main'
            sections={s['key']:s for s in plan['sections' if alternative else 'physical_sections_metric']}
            bindex=defaultdict(list)
            for b in plan['bindings']:bindex[b['physical_key']].append(b)
            before=[];after=[]
            for path in associations['paths']:
                data=db.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,str(path['way_id']))).fetchone()
                if not data:raise ValueError('Missing admitted source')
                old=json.loads(gzip.decompress(data[0]));counts['source_rows_checked']+=1
                if old['coordinates']!=path['coordinates'] or old['nodes']!=path['osm_node_ids'] or set(old['modes'])!=set(path['modes']):
                    raise ValueError('Source coordinates/nodes/access differ')
                compiled=compile_path_bindings(SimpleNamespace(**path),plan,alternative,sections=sections,bindex=bindex)
                new=dict(old,bindings=compiled);before.append(old);after.append(new)
                if compiled!=old['bindings']:
                    patch['updates'].append(dict(route=route,way=str(path['way_id']),previous_bindings_sha256=digest(old['bindings']),bindings=compiled))
                    route_updates[route]+=1;counts['corrected_binding_rows']+=len(compiled)
                    # Target anchors and mode permissions must remain byte-equivalent as values.
                    stripped=lambda bs:[{k:v for k,v in b.items() if k not in ('start','end')} for b in bs]
                    if stripped(old['bindings'])!=stripped(compiled):raise ValueError('Correction changed target or access')
            if alternative:
                if before!=after:raise ValueError('Preserved alternative unexpectedly changed')
                continue
            targets=read_targets(db,route);oldcap=route_capacity(before,targets);cap=route_capacity(after,targets)
            if oldcap!=cap:raise ValueError('Offset correction changed target denominator/capacity')
            if abs(cap['combined_m']-plan['maximum_bound_open_length_m']['combined'])>1e-5:
                raise ValueError('Published capacity differs from pinned plan')
            result.append(dict(route_key=route,before=oldcap,after=cap,corrected_source_rows=route_updates[route]))
            covered=target_intervals(after,'combined')
            for mode in network:network_intervals(targets,target_intervals(after,mode),network[mode])
            # Diagnostic categories never grant correspondence. Exact low/high and
            # route-local target IDs locate blockers regardless of category.
            lines=[LineString(s['coordinates']) for s in sections.values()]
            tree=STRtree(lines);origin=plan['projection_origin']
            project=lambda c:(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))
            deferred=set(plan.get('deferred_alternative_way_ids',[]))
            owners={k:str(p['way_id']) for p in associations['paths'] for k in p['section_keys']}
            section_keys=list(sections)
            for t in targets:
                for low,high in missing(t['length'],covered.get(t['key'],[])):
                    line=LineString([project(c) for c in t['coords']])
                    segment=LineString([line.interpolate(low/t['length'],normalized=True),line.interpolate(high/t['length'],normalized=True)])
                    nearby=[int(i) for i in tree.query(segment.buffer(POLICY['maximum_offset_m']))
                        if segment.distance(lines[int(i)])<=POLICY['maximum_offset_m']]
                    if any(owners[section_keys[i]] in deferred for i in nearby):cause='deferred_alternative_neighbourhood'
                    elif not nearby:cause='no_admitted_source_within_existing_offset'
                    else:cause='nearby_source_without_unique_finite_correspondence'
                    causes[cause]+=high-low;counts['unbound_intervals']+=1
                    value=dict(route_key=route,target_key=t['key'],low_m=low,high_m=high,
                        target_length_m=t['length'],target_coordinates=t['coords'],diagnostic=cause,
                        nearby_admitted_section_keys=[section_keys[i] for i in nearby])
                    blockers.write(encoded(value)+b'\n')
            print(route,'corrected sources',route_updates[route],'capacity',round(cap['combined_percent'],6),flush=True)
    lengths=dict(db.execute('SELECT id,length FROM network'));network_result={'target_m':sum(lengths.values())}
    for mode,values in network.items():
        network_result[mode+'_m']=sum(z-a for spans in values.values() for a,z in merge_intervals(spans))
    network_result['unbound_m']=max(0.,network_result['target_m']-network_result['combined_m'])
    network_result['attainable']=network_result['unbound_m']<=max(1e-7,network_result['target_m']*1e-12)
    network_result['combined_percent']=100*network_result['combined_m']/network_result['target_m']
    db.close()
    candidate=apply_patch(database,patch,output/'ncn-national.db');patch['candidate_sha256']=sha_file(candidate)
    patch['sqlite_writer_version']=sqlite3.sqlite_version
    patch['candidate_content_sha256']=content_sha(candidate)
    (output/'binding-correction.json.gz').write_bytes(gzip.compress(encoded(patch),mtime=0))
    m=dict(manifest,sha256=patch['candidate_sha256'],revision=manifest['revision']+1)
    (output/'manifest.json').write_bytes(encoded(m)+b'\n')
    from scripts.finalize_ncn_android import pack
    pack(output)
    m=json.loads((output/'manifest.json').read_text());verify_database(candidate,m)
    report={'schema_version':1,'status':'partial_correction_full_attainability_blocked',
        'baseline_sha256':BASELINE_SHA,'candidate_sha256':m['sha256'],'candidate_manifest':m,
        'sqlite_writer_version':patch['sqlite_writer_version'],'candidate_content_sha256':patch['candidate_content_sha256'],
        'checkpoint_sha256':CHECKPOINT_SHA,'input_pins_sha256':PINS,
        'geometry_identity_and_mode_changes':0,'candidate_only_not_published':True,
        'capacity_is_synthetic_not_personal_travel':True,'counts':dict(counts),
        'corrected_source_rows':len(patch['updates']),'routes_with_corrections':len(route_updates),
        'measurable_routes':len(result),'attainable_routes':sum(r['after']['attainable'] for r in result),
        'before_network':network_result,'after_network':network_result,
        'diagnostic_unbound_route_local_metres':dict(causes),
        'diagnostic_caveat':'Neighbourhood classification is a lead, not proof of access or a proposed binding. Exact intervals retained in unbound-intervals.jsonl.gz.',
        'routes':result,'blocker_artifact_sha256':sha_file(output/'unbound-intervals.jsonl.gz'),
        'correction_artifact_sha256':sha_file(output/'binding-correction.json.gz')}
    (output/'assessability.json').write_text(json.dumps(report,indent=2)+'\n')
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('prepare');p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--database',type=Path,required=True);p.add_argument('--manifest',type=Path,default=ROOT/'android-prototype/app/src/main/assets/ncn-national/manifest.json');p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('apply');p.add_argument('--database',type=Path,required=True);p.add_argument('--patch',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('gate');p.add_argument('report',type=Path)
    a=parser.parse_args()
    if a.command=='prepare':
        report=prepare(a.checkpoint,a.database,json.loads(a.manifest.read_text()),a.output)
        print(json.dumps({k:v for k,v in report.items() if k not in ('routes','candidate_manifest')},indent=2))
    elif a.command=='apply':apply_patch(a.database,json.loads(gzip.decompress(a.patch.read_bytes())),a.output)
    else:require_attainable(json.loads(a.report.read_text()))


if __name__=='__main__':main()
