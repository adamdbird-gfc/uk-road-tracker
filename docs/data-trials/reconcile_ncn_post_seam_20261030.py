"""Read-only interval reconciliation; never generates a candidate catalogue.

Inputs: exact shipped SQLite; PR240 classified interval gzip; PR242 insertion
delta directory; PR242 validation receipt. No geometry matcher is imported.
"""
import argparse, base64, gzip, hashlib, io, itertools, json, math, sqlite3, struct
from collections import defaultdict, Counter
from pathlib import Path

CAUSES = ('deferred_binding_withheld', 'ambiguous_target_correspondence',
          'unadmitted_span_of_known_way', 'direction_gate',
          'finite_endpoint_or_alignment', 'no_admitted_source_correspondence')
DB_SHA = '973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093'
INV_SHA = '1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb'
CANDIDATE = 'cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0'
TOL = 0.0001  # metres; numerical checks only, never an admission tolerance

def sha(p):
    h=hashlib.sha256()
    with open(p,'rb') as f:
        for b in iter(lambda:f.read(1048576),b''): h.update(b)
    return h.hexdigest()

def union(spans):
    out=[]
    for a,z in sorted((min(a,z),max(a,z)) for a,z in spans):
        if z<=a: continue
        if out and a<=out[-1][1]:out[-1]=(out[-1][0],max(z,out[-1][1]))
        else:out.append((a,z))
    return out

def subtract(spans, covered):
    covered=union(covered);out=[]
    for a,z in spans:
        cursor=a
        for lo,hi in covered:
            if hi<=cursor:continue
            if lo>=z:break
            if lo>cursor:out.append((cursor,min(z,lo)))
            cursor=max(cursor,hi)
            if cursor>=z:break
        if cursor<z:out.append((cursor,z))
    return out

def length(spans):return math.fsum(z-a for a,z in spans)

def project(target, spans, dest):
    for edge,a,z,n in target['network']:
        for lo,hi in spans:
            lo,hi=max(lo,min(a,z)),min(hi,max(a,z))
            if hi>lo:dest[edge].append(tuple(sorted(((lo-a)/(z-a)*n,(hi-a)/(z-a)*n))))

def close(a,b,label):
    if abs(a-b)>TOL:raise ValueError((label,a,b,a-b))

def run(database, inventory, delta_dir, validation, output):
    assert sha(database)==DB_SHA and sha(inventory)==INV_SHA
    val=json.loads(Path(validation).read_text());idx=json.loads((Path(delta_dir)/'index.json').read_text())
    assert idx['candidate_content_sha256']==val['candidate_content_sha256']==CANDIDATE
    raw=[]
    for part in idx['parts']:
        p=Path(delta_dir)/part['file'];assert sha(p)==part['sha256'];raw.append(base64.b64decode(p.read_bytes(),validate=True))
    raw=b''.join(raw);assert hashlib.sha256(raw).hexdigest()==idx['artifact_sha256']
    f=io.BytesIO(gzip.decompress(raw));assert f.read(9)==b'NCNSEAM1\n'
    db=sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)
    additions=defaultdict(lambda:defaultdict(list));headers=records=0;target_cache={}
    record_struct=struct.Struct('<IIddddB')
    while b:=f.read(4):
        size=struct.unpack('<I',b)[0];route,way,previous,count=json.loads(f.read(size));headers+=1
        if route not in target_cache:target_cache[route]=[x[0] for x in db.execute('SELECT key FROM targets WHERE route=? ORDER BY key',(route,))]
        for _ in range(count):
            pos,target,start,end,low,high,mode=record_struct.unpack(f.read(record_struct.size));records+=1
            assert mode in (1,2,3,7) and all(math.isfinite(x) for x in (start,end,low,high))
            additions[route][target_cache[route][target]].append(tuple(sorted((low,high))))
    assert headers==idx['changed_source_rows'] and records==idx['added_bindings']
    del target_cache,raw
    for route in additions:
        for key in additions[route]:additions[route][key]=union(additions[route][key])
    expected_routes={r['route_key']:r for r in val['routes']}
    done=defaultdict(list);old_done=defaultdict(list)
    regions={c:defaultdict(list) for c in CAUSES}
    route_totals=Counter();recovered=Counter();counts=Counter();routes={c:set() for c in CAUSES}
    per_route=[];class_count=0;historical_sum=0.;residual_count=0
    with gzip.open(inventory,'rt') as inv:
        rows=(json.loads(line) for line in inv)
        for route, group in itertools.groupby(rows,key=lambda x:x['route_key']):
            targets={k:dict(length=n,coords=json.loads(c),network=json.loads(v)) for k,n,c,v in db.execute('SELECT key,length,coords,network FROM targets WHERE route=? ORDER BY key',(route,))}
            before=defaultdict(list)
            for rawpath, in db.execute('SELECT data FROM paths WHERE route=?',(route,)):
                p=json.loads(gzip.decompress(rawpath))
                for b in p['bindings']:
                    assert b['modes'] and set(b['modes'])<=set(p['modes'])&{'on_foot','cycling'}
                    before[b['target']].append(tuple(sorted((b['low'],b['high']))))
            after={}
            for key,t in targets.items():
                before[key]=union(before[key]);after[key]=union(before[key]+additions[route].get(key,[]))
                project(t,before[key],old_done);project(t,after[key],done)
            local=Counter();oldlocal=Counter()
            for r in group:
                class_count+=1;c=r['cause'];t=targets[r['target_key']]
                assert c in CAUSES and r['target_coordinates']==t['coords']
                close(r['target_length_m'],t['length'],'target length')
                original=[(r['low_m'],r['high_m'])];assert original[0][1]>original[0][0]
                spans=subtract(original,after[r['target_key']]);n=length(spans)
                oldlocal[c]+=length(original);local[c]+=n;counts[c]+=len(spans);residual_count+=len(spans)
                if n>1e-7:routes[c].add(route)
                # Preserve historical cause provenance, projected to unchanged network.
                project(t,original,regions[c])
            old_gap=math.fsum(t['length']-length(before[k]) for k,t in targets.items())
            new_gap=math.fsum(t['length']-length(after[k]) for k,t in targets.items())
            close(sum(oldlocal.values()),old_gap,'historical route partition')
            close(sum(local.values()),new_gap,'remaining route partition')
            expected=expected_routes[route]
            # Receipt uses integrated/candidate names; fail if its layout changes.
            before_key='before' if 'before' in expected else 'integrated'
            after_key='after' if 'after' in expected else 'candidate'
            close(new_gap,expected[after_key]['unbound_m'],'validated route deficit')
            route_totals.update(local);recovered.update({c:oldlocal[c]-local[c] for c in CAUSES})
            historical_sum+=old_gap
            per_route.append(dict(route_key=route,remaining_m=new_gap,historical_region_remaining_m=dict(local)))
    assert class_count==442910 and len(per_route)==218
    lengths=dict(db.execute('SELECT id,length FROM network'));db.close()
    sets=Counter();oldsets=Counter();oldgap=newgap=0.
    for edge,n in lengths.items():
        remaining=subtract([(0.,n)],done.get(edge,[]));prior=subtract([(0.,n)],old_done.get(edge,[]))
        oldgap+=length(prior);newgap+=length(remaining)
        layers={c:union(regions[c].get(edge,[])) for c in CAUSES}
        # Event sweep accounts for shared route causes without double-counting.
        events=[]
        for c,spans in layers.items():
            for a,z in spans:events.extend([(a,c,1),(z,c,-1)])
        for tag,spans in [('current',remaining),('historical',prior)]:
            ev=events[:]
            for a,z in spans:ev.extend([(a,'__gap',1),(z,'__gap',-1)])
            active=Counter();previous=0.;dest=sets if tag=='current' else oldsets
            for position,items in itertools.groupby(sorted(ev),key=lambda e:e[0]):
                if position>previous and active['__gap']>0:
                    cause=' + '.join(c for c in CAUSES if active[c]>0) or 'unclassified_numerical_projection_residual'
                    dest[cause]+=position-previous
                for _,c,change in items:active[c]+=change
                previous=position
    close(newgap,val['after_network']['unbound_m'],'validated unique deficit')
    close(oldgap,val['before_network']['unbound_m'],'validated old unique deficit')
    close(sum(route_totals.values()),val['route_local_remaining_deficit_m'],'validated route-local deficit')
    close(historical_sum-sum(route_totals.values()),val['route_local_recovery_m'],'route recovery')
    close(oldgap-newgap,val['unique_network_recovery_m'],'network recovery')
    close(sum(sets.values()),newgap,'unique cause-region partition')
    result=dict(schema_version=1,method='subtract validated inserted coverage; historical cause regions, not rerun current causes',
      input_sha256=dict(database=DB_SHA,classified_intervals=INV_SHA,delta_index=sha(Path(delta_dir)/'index.json'),delta_compressed=idx['artifact_sha256'],validation=sha(validation)),
      candidate_content_sha256=CANDIDATE,numerical_assertion_tolerance_m=TOL,
      counts=dict(historical_intervals=class_count,remaining_subintervals=residual_count,delta_rows=headers,added_bindings=records,measurable_routes=len(per_route)),
      network=dict(target_m=math.fsum(lengths.values()),historical_gap_m=oldgap,remaining_gap_m=newgap,recovered_m=oldgap-newgap,historical_cause_sets_m=dict(oldsets),remaining_historical_cause_sets_m=dict(sets)),
      route_local=dict(historical_gap_m=historical_sum,remaining_gap_m=sum(route_totals.values()),recovered_m=historical_sum-sum(route_totals.values()),historical_region_remaining_m=dict(route_totals),historical_region_recovered_m=dict(recovered),remaining_intervals=dict(counts),affected_routes={c:len(routes[c]) for c in CAUSES},validated_remaining_gap_m=val['route_local_remaining_deficit_m'],unclassified_numerical_residual_m=val['route_local_remaining_deficit_m']-sum(route_totals.values())),
      validated_network=val['after_network'],routes=per_route,
      limitations=['Current semantic causes not reclassified.','Physical absence and eligibility not inferred.','No candidate database generated.','Millimetre-scale network and micrometre-scale route-local numerical residues remain explicit.'])
    Path(output).write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='routes'},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('database','inventory','delta-dir','validation','output'):p.add_argument('--'+name,required=True)
    a=p.parse_args();run(a.database,a.inventory,a.delta_dir,a.validation,a.output)
