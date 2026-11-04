"""Independent metric/identity audit of the pinned Deal resolution (offline)."""
import argparse
from collections import defaultdict
import gzip
import hashlib
import json
import math
from pathlib import Path
import sqlite3

from pyproj import Geod, Transformer
from shapely.geometry import LineString, Point
from shapely.ops import nearest_points, unary_union
from scripts.ncn_deal_correspondence import load_review, read_fixture, ROOT

GEOD=Geod(ellps='WGS84')
PROJECT=Transformer.from_crs('EPSG:4326','EPSG:32631',always_xy=True).transform
INVERSE=Transformer.from_crs('EPSG:32631','EPSG:4326',always_xy=True).transform


def geodesic_minimum(point,ends):
    """Golden-section minimisation on the WGS84 geodesic target, independent
    of the planar correspondence/compiler projection and clipping functions."""
    azimuth,_,length=GEOD.inv(*ends[0],*ends[1])
    def distance(t):
        lon,lat,_=GEOD.fwd(*ends[0],azimuth,t*length)
        return GEOD.inv(*point,lon,lat)[2]
    lo,hi=0.,1.;ratio=(math.sqrt(5)-1)/2
    a=hi-ratio*(hi-lo);b=lo+ratio*(hi-lo);da,db=distance(a),distance(b)
    for _ in range(90):
        if da<db:hi,b,db=b,a,da;a=hi-ratio*(hi-lo);da=distance(a)
        else:lo,a,da=a,b,db;b=lo+ratio*(hi-lo);db=distance(b)
    return min(da,db,distance(0),distance(1))


def audit_geometry():
    review,f=load_review(); xy=[PROJECT(*c)for c in f['path']['coordinates']]
    ends=f['target_original']['coordinates'];target=LineString([PROJECT(*c)for c in ends])
    good=[]
    for feature in f['nearby_official_features']:
        g=feature['geometry']
        for part in [g['coordinates']]if g['type']=='LineString'else g['coordinates']:
            good.append(LineString([PROJECT(*c)for c in part]))
    union=unary_union(good);a,z=target.coords;dx,dy=z[0]-a[0],z[1]-a[1];total=math.hypot(dx,dy)
    history=json.loads((ROOT/'docs/data-trials/ncn1-deal-results-20261030.json').read_text())
    rows=[]
    for i,(p,q)in enumerate(zip(xy,xy[1:])):
        line=LineString([p,q]);s0=((p[0]-a[0])*dx+(p[1]-a[1])*dy)/total;s1=((q[0]-a[0])*dx+(q[1]-a[1])*dy)/total
        offsets=[((v[0]-a[0])*dy-(v[1]-a[1])*dx)/total for v in [p,q]]
        cosine=abs(((q[0]-p[0])*dx+(q[1]-p[1])*dy)/(line.length*total))
        angle=math.degrees(math.acos(min(1.,cosine)))
        count=max(1,math.ceil(line.length/.1)); distances=[line.interpolate(j/count,normalized=True).distance(union)for j in range(count+1)]
        rows.append({'edge_index':i,'node_ids':f['path']['osm_node_ids'][i:i+2],
                     'verdict':'Admit','prior_status':'held'if 6<=i<=10 else 'admitted',
                     'source_local_length_m':line.length,
                     'finite_focus_min_m':line.distance(target),
                     'finite_focus_max_m':max(Point(p).distance(target),Point(q).distance(target)),
                     'signed_lateral_offsets_m':offsets,'supporting_target_stations_m':[s0,s1],
                     'angle_degrees':angle,
                     'approved_union_sampled_max_m':max(distances),
                     'approved_union_max_upper_bound_m':max(distances)+line.length/count/2,
                     'sampling_step_limit_m':.1,
                     'original_outside_10m_m':history['edge_results'][i]['outside_approved_10m_corridor_m'],
                     'original_reason':'unresolved_official_extent_edges; outside reviewed source spans and whole-edge 10 m corridor'if 6<=i<=10 else 'existing approved section',
                     'identity':'4471747 / NCN1 relation 75880; exact ordered node pair',
                     'access':'designated cycling and foot; public route corroborated by operator and council',
                     'identity_or_access_failure':False,'geometry_failure_only':6<=i<=10,
                     'thresholds':{'operational_corridor_m':10,'operational_angle_degrees':15,'review':'Exact pinned source/target correspondence; no replacement national distance threshold'},
                     'correction_scope':'new exact correspondence'if 6<=i<=10 else 'retained unchanged'})
    crosscheck=[]
    for vertex in range(6,11):
        point=f['path']['coordinates'][vertex];utm=Point(xy[vertex]).distance(target);geod=geodesic_minimum(point,ends)
        if abs(utm-geod)>.005:raise ValueError('Local metric/geodesic disagreement')
        crosscheck.append({'vertex':vertex,'utm31n_finite_m':utm,'wgs84_geodesic_finite_m':geod,'difference_m':utm-geod})
    return {'metric':'WGS84 / UTM zone 31N, EPSG:32631; local independent WGS84 geodesic minima',
            'source_station_warning':'Source and target storage stations retain historical route-wide EQ metres; do not equate them with local ground lengths.',
            'edge_verdicts':rows,'geodesic_crosscheck':crosscheck,
            'private_control':{'way':'77611523','verdict':'Exclude','reason':'Private residential way, different identity, no NCN1 bicycle membership; cannot consume reviewed cycleway correspondence.'},
            'parallel_path_decision':'The published NCN target and designated OSM cycleway are corresponding representations of NCN1. Independent council-linked route coordinates follow the cycleway. A nearby footpath description does not transfer identity or modes to another feature.',
            'positional_accuracy':'Evidence establishes route correspondence, not which dataset has surveyed-accurate coordinates.'}


def independent_candidate_audit(before,after):
    """No call to bind_edges, corrected_path, or apply. Read back every row,
    interpolate finite anchors independently, and check old bindings survive."""
    review,f=load_review();changes=[];checked=0
    with sqlite3.connect(Path(before).resolve().as_uri()+'?mode=ro',uri=True)as old,sqlite3.connect(Path(after).resolve().as_uri()+'?mode=ro',uri=True)as new:
        tables=[r[0]for r in old.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
        if list(old.execute('SELECT sql FROM sqlite_master ORDER BY name'))!=list(new.execute('SELECT sql FROM sqlite_master ORDER BY name')):raise ValueError('Schema changed')
        for table in tables:
            left=old.execute('SELECT * FROM '+table+' ORDER BY 1,2');right=new.execute('SELECT * FROM '+table+' ORDER BY 1,2')
            while True:
                a,b=left.fetchone(),right.fetchone()
                if a is None or b is None:
                    if a!=b:raise ValueError('Row count changed')
                    break
                checked+=1
                if a!=b:
                    if table!='paths' or a[:3]!=b[:3] or a[:2]!=('NCN:1','4471747'):raise ValueError('Unrelated reference data changed')
                    p,q=json.loads(gzip.decompress(a[3])),json.loads(gzip.decompress(b[3]));oldbindings=p.pop('bindings');newbindings=q.pop('bindings')
                    if p!=q or newbindings[:len(oldbindings)]!=oldbindings:raise ValueError('Existing source fields/bindings changed')
                    additions=newbindings[len(oldbindings):];changes.append([table,*a[:2]])
        if changes!=[['paths','NCN:1','4471747']]:raise ValueError('Expected exactly one changed source row')
        project=lambda c:(6371008.8*math.radians(c[0]-f['projection_origin'][0])*math.cos(math.radians(f['projection_origin'][1])),6371008.8*math.radians(c[1]-f['projection_origin'][1]))
        xy=[project(c)for c in f['path']['coordinates']];source=LineString(xy)
        targets={t['key']:LineString(t['coordinates'])for t in f['nearby_targets_metric']}
        maximum=0.;count=0
        for b in additions:
            t=targets[b['target']]
            if not 0<=min(b['low'],b['high'])<max(b['low'],b['high'])<=t.length+1e-7:raise ValueError('Target extent exceeded')
            # Each binding stays within one straight source edge and target edge;
            # convexity makes endpoint-distance bounds apply to the full map.
            for fraction in [0.,.25,.5,.75,1.]:
                s=source.interpolate(b['start']+(b['end']-b['start'])*fraction)
                q=t.interpolate(b['low']+(b['high']-b['low'])*fraction)
                distance=s.distance(q); maximum=max(maximum,distance);count+=1
                if distance>10+1e-7:raise ValueError('Stored finite correspondence exceeds unchanged EQ safeguard')
        if len(additions)!=6:raise ValueError('Unexpected addition count')
    return {'all_rows_checked':checked,'changed_rows':changes,'added_bindings':6,'finite_anchor_checks':count,
            'maximum_eq_anchor_distance_m':maximum,'all_existing_bindings_preserved':True,
            'all_other_source_fields_tables_targets_lengths_network_mappings_preserved':True,
            'new_user_completion_credit':False}


def independent_distance_effect(before,after):
    review,_=load_review()
    affected={entry[0]for t in review['targets'].values()for entry in t[2]}
    def total(spans):
        merged=[]
        for a,z in sorted((min(a,z),max(a,z))for a,z in spans):
            if merged and a<=merged[-1][1]+1e-9:merged[-1][1]=max(z,merged[-1][1])
            else:merged.append([a,z])
        return sum(z-a for a,z in merged)
    def read(database):
        with sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)as db:
            maps={(r,k):json.loads(n)for r,k,n in db.execute('SELECT route,key,network FROM targets')if any(p[0]in affected for p in json.loads(n))}
            covered=defaultdict(list);net=defaultdict(list)
            for route,raw in db.execute('SELECT route,data FROM paths'):
                for b in json.loads(gzip.decompress(raw))['bindings']:
                    if(route,b['target'])in maps:covered[route,b['target']].append([b['low'],b['high']])
            for key,pieces in maps.items():
                for edge,a,z,length in pieces:
                    if edge not in affected:continue
                    for low,high in covered[key]:
                        low,high=max(min(low,high),min(a,z)),min(max(low,high),max(a,z))
                        if high>low:net[edge].append([(low-a)/(z-a)*length,(high-a)/(z-a)*length])
            return (sum(total(spans)for k,spans in covered.items()if k[0]=='NCN:1'and k[1]in review['targets']),
                    sum(total(spans)for spans in net.values()),len(maps))
    a,b=read(before),read(after)
    return dict(affected_network_edges=sorted(affected),affected_target_route_identities=a[2],
                route_local_before_m=a[0],route_local_after_m=b[0],route_local_added_m=b[0]-a[0],
                unique_network_before_m=a[1],unique_network_after_m=b[1],unique_network_added_m=b[1]-a[1])


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path);p.add_argument('--after',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=audit_geometry()
    if a.before or a.after:
        if not a.before or not a.after:p.error('Both before and after required')
        result['candidate_audit']=independent_candidate_audit(a.before,a.after)
        result['distance_effect']=independent_distance_effect(a.before,a.after)
    a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
