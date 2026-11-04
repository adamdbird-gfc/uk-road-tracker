"""Read-only bounded national geometry screen. Never compiles or writes a catalogue.

Run once with --inputs extracted checkpoint and --gaps classified-intervals.jsonl.gz.
Output is a proximity queue, not approved alternatives or recoverable kilometres.
"""
import argparse,base64,csv,gzip,hashlib,io,json,math,struct
from collections import defaultdict,Counter
from pathlib import Path
from shapely.geometry import LineString,Point,mapping
from shapely.ops import transform,substring,unary_union
from shapely.strtree import STRtree
from pyproj import CRS,Transformer,Geod
ROOT=Path(__file__).resolve().parents[3]
BASE='8c04b7eb1ac1bc8a6e8700ed19a14ba7ec34149c'
GAPS_SHA='1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def union(spans):
 out=[]
 for a,b in sorted((min(a,b),max(a,b)) for a,b in spans):
  if b<=a:continue
  if out and a<=out[-1][1]:out[-1]=(out[-1][0],max(b,out[-1][1]))
  else:out.append((a,b))
 return out
def subtract(span,covered):
 a,b=span;out=[]
 for l,h in union(covered):
  if h<=a:continue
  if l>=b:break
  if l>a:out.append((a,min(l,b)))
  a=max(a,h)
 if a<b:out.append((a,b))
 return out
def metric(coords):
 lon,lat=coords[0];crs=CRS.from_proj4(f'+proj=aeqd +lat_0={lat} +lon_0={lon} +datum=WGS84 +units=m')
 return Transformer.from_crs(4326,crs,always_xy=True),Transformer.from_crs(crs,4326,always_xy=True)
def angle(a,b):
 ax,ay=a;bx,by=b
 return math.degrees(math.acos(min(1,max(-1,abs(ax*bx+ay*by)/(math.hypot(ax,ay)*math.hypot(bx,by) or 1)))))
def run(inputs,gaps,out):
 inputs=Path(inputs);out=Path(out);out.mkdir(parents=True,exist_ok=True);assert sha(gaps)==GAPS_SHA
 pins={};plans={};sources={};keys={}
 # Frozen per-route plans are used only for sorted target identities; bindings are not regenerated.
 for p in sorted((inputs/'plans').glob('*/plan.json.gz')):
  d=json.load(gzip.open(p,'rt'));route=d['route_key'];keys[route]=sorted(s['key'] for s in d['logical_sections']);pins[str(p.relative_to(inputs))]=sha(p)
  a=p.parent/'associations.json.gz';assoc=json.load(gzip.open(a,'rt'));sources[route]=assoc['paths'];pins[str(a.relative_to(inputs))]=sha(a)
 idx=json.loads((ROOT/'.github/ncn-reference/ordinary-seams/index.json').read_text());raw=[]
 for part in idx['parts']:
  p=ROOT/'.github/ncn-reference/ordinary-seams'/part['file'];assert sha(p)==part['sha256'];raw.append(base64.b64decode(p.read_bytes(),validate=True))
 compressed=b''.join(raw);assert hashlib.sha256(compressed).hexdigest()==idx['artifact_sha256']
 f=io.BytesIO(gzip.decompress(compressed));assert f.read(9)==b'NCNSEAM1\n';added=defaultdict(list);rec=struct.Struct('<IIddddB')
 while b:=f.read(4):
  route,way,previous,count=json.loads(f.read(struct.unpack('<I',b)[0]))
  for _ in range(count):
   pos,target,start,end,lo,hi,mode=rec.unpack(f.read(rec.size));added[route,keys[route][target]].append((lo,hi))
 review=json.loads((ROOT/'tests/fixtures/ncn-deal-resolution/review.json').read_text())
 for b in review['bindings']:added['NCN:1',b['target']].append((b['low'],b['high']))
 grouped=defaultdict(list);causes=Counter();remaining=0.;count=0
 with gzip.open(gaps,'rt') as stream:
  for line in stream:
   r=json.loads(line);count+=1
   for lo,hi in subtract((r['low_m'],r['high_m']),added[r['route_key'],r['target_key']]):
    remaining+=hi-lo;causes[r['cause']]+=hi-lo
    # Exclude <5m residual fragments from candidate search only; full denominator above retained.
    if hi-lo>=5:grouped[r['route_key']].append(dict(r,low_m=lo,high_m=hi))
 rows=[];screened=0;not_found=0
 for route in sorted(grouped):
  paths=sources[route];edges=[];meta=[]
  for p in paths:
   for i,(a,b) in enumerate(zip(p['coordinates'],p['coordinates'][1:])):
    if a!=b:edges.append(LineString([a,b]));meta.append((p,i))
  if not edges:continue
  tree=STRtree(edges)
  for r in grouped[route]:
   screened+=1;coords=r['target_coordinates'];fw,bw=metric(coords);target=transform(fw.transform,LineString(coords));
   gap=substring(target,r['low_m']/r['target_length_m'],r['high_m']/r['target_length_m'],normalized=True)
   if gap.geom_type!='LineString':continue
   centre=gap.interpolate(.5,normalized=True);geo=transform(bw.transform,gap)
   # Bounding search <= 50m plus latitude-safe longitude expansion. Final gate is metric.
   lon,lat=geo.centroid.coords[0];dx=50/(111000*max(.1,math.cos(math.radians(lat))));dy=50/110000
   from shapely.geometry import box
   hits=tree.query(box(geo.bounds[0]-dx,geo.bounds[1]-dy,geo.bounds[2]+dx,geo.bounds[3]+dy))
   options=[]
   for n in hits:
    e=transform(fw.transform,edges[n]);a,b=list(e.coords);ga,gb=list(gap.coords)[0],list(gap.coords)[-1]
    deg=angle((b[0]-a[0],b[1]-a[1]),(gb[0]-ga[0],gb[1]-ga[1]));distances=[gap.interpolate(t,normalized=True).distance(e) for t in (0,.25,.5,.75,1)]
    if deg<=20 and max(distances)<=40:
     p,i=meta[n];options.append((sum(distances)/5,str(p['way_id']),i,n,deg,distances))
   if not options:not_found+=1;continue
   options.sort();mean,way,i,n,deg,ds=options[0];p,_=meta[n];e=transform(fw.transform,edges[n]);start=e.project(Point(gap.coords[0]));end=e.project(Point(gap.coords[-1]));alt=substring(e,min(start,end),max(start,end));altgeo=transform(bw.transform,alt) if alt.geom_type=='LineString' else edges[n]
   rid='PC-'+hashlib.sha256(json.dumps([route,r['target_key'],r['low_m'],r['high_m'],way,i],separators=(',',':')).encode()).hexdigest()[:12]
   rows.append(dict(id=rid,route=route,target_key=r['target_key'],historical_cause=r['cause'],low_m=r['low_m'],high_m=r['high_m'],target_length_m=r['target_length_m'],unassessable_m=r['high_m']-r['low_m'],location=[lon,lat],source_way=way,source_edge=i,source_coordinates=list(edges[n].coords),target_coordinates=coords,gap_coordinates=list(geo.coords),alternative_coordinates=list(altgeo.coords),candidate_distance_m=abs(end-start),separation_mean_m=mean,separation_sampled_max_m=max(ds),angle_deg=deg,alternative_count=len(options),modes=p['modes'],source_section_keys=p['section_keys'],source_geometry_sha256=p['source_geometry_sha256'],classification='Requires review',reason='Same-route admitted source nearby; may be representation mismatch, not a distinct parallel way. Access/topology and finite correspondence need review.',access_evidence='Pinned eligible source modes; original highway/access/bicycle tags and feature versions absent from association export.',route_membership_evidence='Pinned route-specific admitted sections; does not approve source beyond section bounds.',topology_evidence='Not independently verified; proximity and heading only.',new_credit_m=0))
 # Whole-way coordinates do not extend admitted finite sections.
 for r in rows:
  admitted=any(k==r['source_way'] or (k.startswith(r['source_way']+':') and len(k.split(':'))==3 and int(k.split(':')[1])<=r['source_edge']<int(k.split(':')[2])) for k in r['source_section_keys'])
  r['candidate_edge_within_admitted_section']=admitted
  if not admitted:
   r['classification']='Unresolved';r['reason']='Candidate edge is outside pinned admitted source sections; whole-way identity cannot extend route admission.'
   r['route_membership_evidence']='Known route-associated way, but this finite edge has no admitted section evidence.'
 # Shared targets have identical canonical coordinate chains in this export. Union normalized fractions across routes and alternatives.
 seen=defaultdict(list);lengths={};gross=sum(r['unassessable_m'] for r in rows)
 for r in rows:
  c=r['target_coordinates'];forward=tuple(map(tuple,c));reverse=forward[::-1];k=min(forward,reverse);lo=r['low_m']/r['target_length_m'];hi=r['high_m']/r['target_length_m']
  if forward!=k:lo,hi=1-hi,1-lo
  seen[k].append((lo,hi));lengths[k]=Geod(ellps='WGS84').line_length(*zip(*k))
 proxy=sum(sum(b-a for a,b in union(v))*lengths[k] for k,v in seen.items())
 receipt=dict(schema_version=1,baseline=BASE,revision=4,method='One national retained-source geometry screen after subtracting exact seam and Deal additions',classified_input_sha256=GAPS_SHA,input_files_sha256=pins,seam_delta_sha256=idx['artifact_sha256'],deal_review_sha256=sha(ROOT/'tests/fixtures/ncn-deal-resolution/review.json'),historical_rows=count,remaining_route_local_m=remaining,remaining_historical_cause_m=dict(causes),screen_minimum_gap_m=5,discovery_radius_m=40,discovery_angle_deg=20,screened_residual_pieces=screened,no_candidate_pieces=not_found,candidate_count=len(rows),gross_route_local_candidate_m=gross,exact_chain_deduplicated_geodesic_candidate_proxy_m=proxy,unique_network_recoverable_m=None,confidence_counts=dict(Counter(r['classification'] for r in rows)),validated_new_recovery_m=0,limitations=['Only already admitted same-route source ways searched; missing roads/paths outside these frozen associations are not covered.','One best finite source edge retained per gap; alternative_count records multiple options.','Five point sampled distance screen is discovery only; no continuous Hausdorff or equivalence claim.','20 degree / 40m gates select a review queue, not proposed production thresholds.','Exact-chain union removes same geometry/route duplicates; differing segmentation/partial overlaps are NOT canonically deduplicated. Proxy is not unique-network recovery.','No OSM public-access or current route status inferred from absent tags.','No positive national recovery scenario justified without canonical mappings and evidence-led approval.'])
 ca=Counter();bands=Counter();bandcounts=Counter()
 for r in rows:
  ca[r['historical_cause']]+=r['unassessable_m'];d=r['separation_mean_m'];band='0–2m' if d<=2 else '2–10m' if d<=10 else '10–25m' if d<=25 else '25–40m';bands[band]+=r['unassessable_m'];bandcounts[band]+=1
 receipt['candidate_route_local_m_by_historical_cause']=dict(ca);receipt['candidate_route_local_m_by_mean_separation']=dict(bands);receipt['candidate_count_by_mean_separation']=dict(bandcounts)
 confidence_metres=Counter()
 for r in rows:confidence_metres[r['classification']]+=r['unassessable_m']
 receipt['confidence_gross_route_local_m']=dict(confidence_metres);receipt['classification_refinement']='Finite admitted section containment checked by pinned section vertex ranges; no whole-way admission transfer.'
 (out/'national-candidates.json').write_text(json.dumps(rows,separators=(',',':'))+'\n');(out/'national-summary.json').write_text(json.dumps(receipt,indent=2)+'\n')
 fields=[k for k in rows[0] if not k.endswith('coordinates')]
 with (out/'national-candidates.csv').open('w') as f:
  w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore');w.writeheader();w.writerows({k:json.dumps(v) if isinstance(v,(list,dict)) else v for k,v in r.items()} for r in rows)
 for name in ('national-candidates.json','national-candidates.csv'):
  p=out/name;p.with_suffix(p.suffix+'.gz').write_bytes(gzip.compress(p.read_bytes(),mtime=0));p.unlink()
 print(json.dumps({k:v for k,v in receipt.items() if k!='input_files_sha256'},indent=2))
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--inputs',required=True);a.add_argument('--gaps',required=True);a.add_argument('--output',required=True);o=a.parse_args();run(o.inputs,o.gaps,o.output)
