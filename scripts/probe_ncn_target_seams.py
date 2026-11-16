"""Read-only degree-two target seam opportunity probe, not a binding compiler.

Counts are upper bounds requiring a later chain-correspondence validation. Every
candidate still passes production distance/direction gates; no credit is created.
"""
import argparse,json,gzip,math,hashlib,sqlite3
from pathlib import Path
from collections import defaultdict,Counter
from scripts.classify_ncn_binding_gaps import metric_targets,geometry_spans
from scripts.ncn_binding_attainability import read_targets,target_intervals,missing,network_intervals,encoded,content_sha
from scripts.ncn_trial.coverage import merge_intervals
from scripts.ncn_trial.open_completion import edge_interval,POLICY
from scripts.ncn_trial.completion_checkpoint import open_extent
from shapely.geometry import LineString
from shapely.ops import unary_union
from shapely.strtree import STRtree

def bracketed_continuation(first,last,lines,degrees,window):
    """A degree-two vertex alone is insufficient: return legs are real ambiguity."""
    if len(lines)!=2:return False
    nodes=[set(map(tuple,l.coords))for l in lines];shared=nodes[0]&nodes[1]
    if len(shared)!=1:return False
    vertex=next(iter(shared))
    if degrees[vertex]!=2:return False
    ends=[next(iter(n-{vertex}))for n in nodes]
    vectors=[(p[0]-vertex[0],p[1]-vertex[1])for p in ends]
    norms=[math.hypot(*v)for v in vectors]
    if min(norms)<=1e-7:return False
    cosine=sum(a*b for a,b in zip(*vectors))/(norms[0]*norms[1])
    if cosine>-math.cos(math.radians(2*POLICY['maximum_angle_degrees']))+1e-12:return False
    dx,dy=last[0]-first[0],last[1]-first[1];length=math.hypot(dx,dy)
    if length<=1e-7:return False
    station=((vertex[0]-first[0])*dx+(vertex[1]-first[1])*dy)/length
    offset=abs((vertex[0]-first[0])*dy-(vertex[1]-first[1])*dx)/length
    return window[0]+1e-7<station<window[1]-1e-7 and offset<=POLICY['maximum_offset_m']


def main(args):
    if content_sha(args.database)!='936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a':
        raise ValueError('Exact integrated candidate reference required')
    root=args.checkpoint;pins=json.load(open(root/'inputs/input-pins.json'));summary=json.load(open(root/'plans/summary.json'));rows={r['route_key']:r for r in pins['routes']}
    official=json.loads(gzip.decompress((root/'inputs'/pins['official_network']).read_bytes()));db=sqlite3.connect(args.database.resolve().as_uri()+'?mode=ro',uri=True)
    network_covered=defaultdict(list);network_seams=defaultdict(list);route_results=[];examples=[];rejected_source_windows=0
    for record in summary['routes']:
     if record['completion_kind']!='captured_open_main':continue
     route=record['route_key'];folder=root/'plans'/route.replace(':','-');plan=json.loads(gzip.decompress((folder/'plan.json.gz').read_bytes()));ass=json.loads(gzip.decompress((folder/'associations.json.gz').read_bytes()));origin=plan['projection_origin']
     project=lambda c:(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))
     ef=root/'inputs'/rows[route]['folder']/'open-extent.json';extent=json.load(open(ef))if ef.exists()else open_extent(route,official['features'],{'national_reference_sha256':pins['official_network_sha256'],'projection_origin':origin},pins['official_network_sha256'])
     targets=metric_targets(extent,project);ordered=sorted(targets.items());tree=STRtree([l for k,l in ordered]);degrees=Counter(tuple(p) for k,l in ordered for p in l.coords)
     sections={s['key']:s for s in plan['physical_sections_metric']};deferred=set(plan.get('deferred_alternative_way_ids',[]));blocked={k for p in ass['paths']if p['way_id']in deferred for k in p['section_keys']};blocked_targets=set()
     if blocked:
      corridor=unary_union([LineString(sections[k]['coordinates'])for k in blocked]).buffer(10);blocked_targets={k for k,l in ordered if l.intersects(corridor)}
     seam=defaultdict(list);witness={}
     for section in sections.values():
      if section['key']in blocked:continue
      for first,last in zip(section['coordinates'],section['coordinates'][1:]):
       line=LineString([first,last]);candidates=[]
       for i in tree.query(line.buffer(10)):
        v=edge_interval(first,last,ordered[int(i)][1])
        if v:candidates.append((int(i),*v))
       if len(candidates)<2:continue
       cuts=sorted({0.,line.length,*[x for c in candidates for x in c[1:3]]})
       for a,z in zip(cuts,cuts[1:]):
        if z-a<=1e-7:continue
        mid=(a+z)/2;active=[c for c in candidates if c[1]<mid<c[2]]
        if len(active)<2 or any(ordered[c[0]][0]in blocked_targets for c in active):continue
        active_degrees=Counter(tuple(p)for c in active for p in ordered[c[0]][1].coords)
        if not bracketed_continuation(first,last,[ordered[c[0]][1]for c in active],degrees,(a,z)):continue
        rejected_source_windows+=1
        for c in active:
         i,lo,hi,ta,tz=c;key=ordered[i][0]
         x,y=[ta+(v-lo)/(hi-lo)*(tz-ta)for v in (a,z)];seam[key].append((x,y))
         item=dict(witness_projection_interval_m=sorted([x,y]),route_key=route,source_section_key=section['key'],source_edge_metric=[first,last],source_window_m=[a,z],target_keys=[ordered[c[0]][0]for c in active],target_edges_metric=[list(ordered[c[0]][1].coords)for c in active],internal_target_degree=2)
         if key not in witness or z-a>witness[key]['source_window_m'][1]-witness[key]['source_window_m'][0]:witness[key]=item
     pathrows=[json.loads(gzip.decompress(raw))for raw, in db.execute('select data from paths where route=?',(route,))];ts=read_targets(db,route);covered=target_intervals(pathrows,'combined');network_intervals(ts,covered,network_covered);seam_missing={};metres=0.;intervals=0
     for t in ts:
      spans=[]
      for a,z in missing(t['length'],covered.get(t['key'],[])):
       for x,y in merge_intervals(seam.get(t['key'],[])):
        lo,hi=max(a,x),min(z,y)
        if hi>lo:spans.append((lo,hi))
      spans=merge_intervals(spans)
      if spans:
       seam_missing[t['key']]=spans;metres+=sum(z-a for a,z in spans);intervals+=len(spans)
       examples.append(dict(witness[t['key']],target_key=t['key'],missing_intervals_m=spans,opportunity_m=sum(z-a for a,z in spans)))
       examples=sorted(examples,key=lambda v:-v['opportunity_m'])[:20]
     network_intervals(ts,seam_missing,network_seams)
     route_results.append(dict(route_key=route,intervals=intervals,target_metres=metres))
     print(route,round(metres,3),flush=True)
    unique=0.
    for edge,length in db.execute('select id,length from network'):
     for a,z in missing(length,network_covered.get(edge,[])):
      for x,y in merge_intervals(network_seams.get(edge,[])):
       unique+=max(0.,min(z,y)-max(a,x))
    result=dict(method='diagnostic upper bound: two degree-two continuing target edges; hairpins/branches/deferred excluded; source brackets the shared vertex; no bindings created',
     affected_routes=sum(r['target_metres']>1e-7 for r in route_results),route_local_target_m=sum(r['target_metres']for r in route_results),
     target_intervals=sum(r['intervals']for r in route_results),unique_network_upper_bound_m=unique,rejected_source_windows=rejected_source_windows,routes=route_results,examples=examples)
    args.output.write_text(json.dumps(result,indent=2)+'\n');print({k:v for k,v in result.items()if k not in ['routes','examples']})

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkpoint',type=Path,required=True)
    p.add_argument('--database',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    main(p.parse_args())
