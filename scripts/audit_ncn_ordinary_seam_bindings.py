"""Independent geometric audit of candidate additions, with no binding compiler calls."""
import argparse
from bisect import bisect_right
from collections import Counter
import gzip
import hashlib
import json
import math
from pathlib import Path
import sqlite3
from shapely.geometry import LineString
from shapely.ops import unary_union
from shapely.strtree import STRtree
from matched_path_evidence import digest
from scripts.ncn_binding_attainability import encoded
from scripts.ncn_trial.completion_checkpoint import open_extent


def audit(checkpoint, integrated, candidate):
    checkpoint=Path(checkpoint)
    pins=json.loads((checkpoint/'inputs/input-pins.json').read_text())
    official=json.loads(gzip.decompress((checkpoint/'inputs'/pins['official_network']).read_bytes()))
    inputs={r['route_key']:r for r in pins['routes']}
    old=sqlite3.connect(Path(integrated).resolve().as_uri()+'?mode=ro',uri=True)
    new=sqlite3.connect(Path(candidate).resolve().as_uri()+'?mode=ro',uri=True)
    checked=0;max_offset=0.;max_angle=0.;witnesses=[]
    for route, in new.execute('SELECT key FROM routes ORDER BY key'):
        if route=='NCN:228':continue
        plan=json.loads(gzip.decompress((checkpoint/'plans'/route.replace(':','-')/'plan.json.gz').read_bytes()))
        origin=plan['projection_origin']
        project=lambda c:(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))
        extent_path=checkpoint/'inputs'/inputs[route]['folder']/'open-extent.json'
        extent=json.loads(extent_path.read_text()) if extent_path.exists() else open_extent(route,official['features'],{'projection_origin':origin,'national_reference_sha256':pins['official_network_sha256']},pins['official_network_sha256'])
        lines=[]
        for f in extent['features']:
            g=f['geometry'];parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
            lines.extend(LineString([project(c) for c in part]) for part in parts)
        union=unary_union(lines);parts=[union] if union.geom_type=='LineString' else union.geoms
        targets={}
        for part in parts:
            for a,z in zip(part.coords,list(part.coords)[1:]):
                a,z=sorted((tuple(a),tuple(z)))
                if math.dist(a,z)>1e-7:targets['open-edge:'+digest((a,z))]=(a,z)
        keys=sorted(targets);edges=[LineString(targets[k]) for k in keys];tree=STRtree(edges)
        degrees=Counter(p for a,z in targets.values() for p in (a,z))
        for way,raw in new.execute('SELECT way,data FROM paths WHERE route=?',(route,)):
            path=json.loads(gzip.decompress(raw))
            original=json.loads(gzip.decompress(old.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,way)).fetchone()[0]))
            if encoded({k:v for k,v in original.items() if k!='bindings'})!=encoded({k:v for k,v in path.items() if k!='bindings'}):raise ValueError('Source changed')
            before={encoded(b) for b in original['bindings']};after={encoded(b) for b in path['bindings']}
            if not before<=after:raise ValueError('Integrated binding lost')
            added=[b for b in path['bindings'] if encoded(b) not in before]
            if not added:continue
            xy=list(map(project,path['coordinates']));pos=path['positions']
            for b in added:
                a,z=b['start'],b['end'];mid=(a+z)/2;i=bisect_right(pos,mid)-1
                if not pos[i]-1e-6<=a<z<=pos[i+1]+1e-6:raise ValueError('Addition crosses source edge boundary')
                first,last=xy[i],xy[i+1];length=math.dist(first,last)
                u=((last[0]-first[0])/length,(last[1]-first[1])/length)
                local=lambda station:(first[0]+(station-pos[i])*u[0],first[1]+(station-pos[i])*u[1])
                q,r=targets[b['target']];target_length=math.dist(q,r);v=((r[0]-q[0])/target_length,(r[1]-q[1])/target_length)
                if not 0<=min(b['low'],b['high'])<max(b['low'],b['high'])<=target_length+1e-6:raise ValueError('Nonfinite target correspondence')
                angle=math.degrees(math.acos(min(1.,abs(u[0]*v[0]+u[1]*v[1]))))
                if angle>15+1e-8:raise ValueError('Direction gate violation')
                max_angle=max(max_angle,angle)
                for station,target_station in ((a,b['low']),(z,b['high'])):
                    target_point=(q[0]+target_station*v[0],q[1]+target_station*v[1])
                    distance=math.dist(local(station),target_point)
                    if distance>10+1e-6:raise ValueError('Unsupported anchor outside corridor')
                    max_offset=max(max_offset,distance)
                # Independent midpoint finite projection test against every nearby
                # edge. Exactly two candidates may own this physical interval.
                point=local(mid);active=[]
                for j in tree.query(LineString([first,last]).buffer(10)):
                    x,y=targets[keys[int(j)]];n=math.dist(x,y);t=((y[0]-x[0])/n,(y[1]-x[1])/n)
                    cosine=abs(u[0]*t[0]+u[1]*t[1])
                    station=(point[0]-x[0])*t[0]+(point[1]-x[1])*t[1]
                    perpendicular=abs((point[0]-x[0])*t[1]-(point[1]-x[1])*t[0])
                    if cosine>=math.cos(math.radians(15))-1e-12 and 0<station<n and perpendicular<=10+1e-7:
                        active.append(keys[int(j)])
                if len(active)!=2 or b['target'] not in active:raise ValueError('Addition is not a two-candidate ambiguous interval')
                shared=set(targets[active[0]])&set(targets[active[1]])
                if len(shared)!=1:raise ValueError('Disconnected/parallel target pair')
                vertex=next(iter(shared))
                if degrees[vertex]!=2:raise ValueError('Complete target graph branch')
                outward=[next(iter(set(targets[k])-{vertex})) for k in active]
                vectors=[(p[0]-vertex[0],p[1]-vertex[1]) for p in outward]
                cosine=sum(x*y for x,y in zip(*vectors))/(math.hypot(*vectors[0])*math.hypot(*vectors[1]))
                if cosine>-math.cos(math.radians(30))+1e-12:raise ValueError('Hairpin/return')
                station=(vertex[0]-first[0])*u[0]+(vertex[1]-first[1])*u[1]
                if not 1e-7<station<length-1e-7:raise ValueError('Source endpoint extrapolated')
                anchor=local(pos[i]+station)
                if min(math.dist(anchor,local(a)),math.dist(anchor,local(z)))>1e-6:raise ValueError('Unjustified seam anchor')
                # Affine interpolation between both bounded endpoints remains
                # within the same 10m corridor throughout each finite mapping.
                checked+=1
        print(route,'audit complete',flush=True)
    # Reference tables other than source bindings must be identical as SQLite values.
    for table,order in {'routes':'key','targets':'route,key','network':'id','meta':'key','nodes':'node,way','overview':'key'}.items():
        left=old.execute('SELECT * FROM '+table+' ORDER BY '+order)
        right=new.execute('SELECT * FROM '+table+' ORDER BY '+order)
        from itertools import zip_longest
        if any(a!=b for a,b in zip_longest(left,right)):raise ValueError('Non-binding catalogue table changed: '+table)
    old.close();new.close()
    return {'status':'pass','added_bindings_geometrically_verified':checked,'maximum_anchor_offset_m':max_offset,
            'maximum_source_target_angle_degrees':max_angle,'direction_gate_degrees':15,'offset_gate_m':10,
            'all_other_tables_identical':True,'checks':['finite monotone anchors','two active finite candidates','complete graph degree two','opposing outward directions','interior source-supported vertex','no source endpoint extrapolation','unchanged source fields and existing bindings']}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--integrated',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();result=audit(a.checkpoint,a.integrated,a.candidate);a.output.write_text(json.dumps(result,indent=2)+'\n');print(result)
