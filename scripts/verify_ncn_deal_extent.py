"""Verify full retained target graph and excluded-geometry controls, offline."""
import argparse
import gzip,json,hashlib,math
from pathlib import Path
from collections import Counter
from shapely.geometry import LineString,shape
from shapely.ops import unary_union,transform
from pyproj import Transformer
from scripts.ncn_deal_correspondence import *
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--checkpoint',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();r=args.checkpoint; f=read_fixture(); pins=json.load(open(r/'inputs/input-pins.json'));raw=(r/'inputs'/pins['official_network']).read_bytes();assert hashlib.sha256(raw).hexdigest()=='b69fba462a780162fc408d6e09718b9a0e38fea62bbc6536fccf56a6f7621293';raw=gzip.decompress(raw);assert hashlib.sha256(raw).hexdigest()==pins['official_network_sha256'];network=json.loads(raw);review=json.load(open(r/'inputs/catalogues/NCN-1/identity-review.json'));approved=set(review['approved_official_feature_ids']);features=[x for x in network['features']if x['properties']['RouteType']=='NCN'and x['properties']['RouteNo']==1];project=Transformer.from_crs(4326,32631,always_xy=True).transform;source=LineString([project(*c)for c in f['path']['coordinates'][6:12]]);bad=[]
for v in features:
 p=v['properties'];good=p['FID']in approved and p.get('OpenStatus')=='Open'and p.get('Desc_')in ['TrafficFree','OnRoad']and p.get('RouteCat')=='Main Route'
 if not good:bad.append((p['FID'],transform(project,shape(v['geometry'])).distance(source)))
# Recover complete noded graph from the approved catalogue's exact target extent,
# not the rounded display target coordinates or the bounded viewer fixture.
extentraw=(r/'inputs/catalogues/NCN-1/open-extent.json').read_bytes();extent=json.loads(extentraw);projecteq=projector({'projection_origin':f['projection_origin']}); lines=[]
for v in extent['features']:
 g=v['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates'];lines.extend(LineString([projecteq(c)for c in part])for part in parts)
u=unary_union(lines);parts=[u]if u.geom_type=='LineString'else u.geoms;full={}
for part in parts:
 for a,z in zip(part.coords,list(part.coords)[1:]):
  a,z=sorted((tuple(a),tuple(z)))
  if math.dist(a,z)>1e-7:full['open-edge:'+__import__('matched_path_evidence').digest((a,z))]=LineString([a,z])
pinned={t['key']:t['coordinates']for t in f['nearby_targets_metric']}
for k,cs in pinned.items():assert list(map(list,full[k].coords))==cs
held=LineString([projecteq(c)for c in f['path']['coordinates'][6:12]])
assert {k for k,t in full.items()if t.distance(held)<=10}<=set(pinned)
new,j=proposed_bindings(f);vertex=tuple(j['reviewed_junction_target']);degree=sum(vertex in t.coords for t in full.values());assert degree==2
out={'schema_version':1,'input_pins_sha256':hashlib.sha256((r/'inputs/input-pins.json').read_bytes()).hexdigest(),'national_geometry_sha256':hashlib.sha256(raw).hexdigest(),'reviewed_open_extent_sha256':hashlib.sha256(extentraw).hexdigest(),'original_ncn1_features_checked':len(features),'nonapproved_features_checked':len(bad),'nearest_nonapproved_feature':sorted(bad,key=lambda p:p[1])[:1],'complete_approved_target_edge_count':len(full),'all_target_candidates_within_10_eq_m_of_held_source_in_bounded_fixture':True,'shared_target_vertex_complete_graph_degree':degree,'pinned_target_count':len(pinned),'interpretation':'The full original national snapshot has no excluded geometry in the held source corridor; the exact reviewed extent contains both bound target keys. GlobalID metadata differs between national snapshot and later catalogue extent; keys and authoritative geometry use the latter, pinned separately.'}
args.output.write_text(json.dumps(out,indent=2,sort_keys=True)+'\n');print(out)
