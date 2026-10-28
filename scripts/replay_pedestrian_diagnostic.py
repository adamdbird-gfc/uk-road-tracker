"""Private offline regression; never uploads the diagnostic or invents missing ways."""
import sys,json,math,time,importlib.util,argparse,statistics
from unittest.mock import MagicMock,patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import database
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('export')
parser.add_argument('--baseline')
parser.add_argument('--baseline-database')
parser.add_argument('--network',help='Read-only source rows, including unselected alternatives')
parser.add_argument('--repeat',type=int,default=1)
args=parser.parse_args()
j=json.load(open(args.export))['journey']
original=json.dumps(j,sort_keys=True)
# Reproduce Android simplification from the aligned validated coordinates.
t=j['route_geometry']['coordinates'];keep={0,len(t)-1};ranges=[(0,len(t)-1)]
def metres(a,b):
 x,y=math.radians(a[1]),math.radians(b[1]);h=math.sin((y-x)/2)**2+math.cos(x)*math.cos(y)*math.sin(math.radians(b[0]-a[0])/2)**2
 return 12742000*math.asin(min(1,math.sqrt(h)))
def gap(p,a,b):
 scale=math.cos(math.radians(p[1]));dx=(b[0]-a[0])*scale;dy=b[1]-a[1];px=(p[0]-a[0])*scale;py=p[1]-a[1];norm=dx*dx+dy*dy;f=max(0,min(1,(px*dx+py*dy)/norm)) if norm else 0
 return math.hypot(px-f*dx,py-f*dy)*111195
while ranges:
 a,z=ranges.pop()
 if z-a<2:continue
 maximum=3;split=-1
 for i in range(a+1,z):
  d=gap(t[i],t[a],t[z])
  if d>maximum:maximum=d;split=i
 if split<0 and (z-a>8 or metres(t[a],t[z])>60):split=(a+z)//2
 if split>=0:keep.add(split);ranges.extend([(a,split),(split,z)])
trace=[{'lng':p[0],'lat':p[1]} for i,p in enumerate(t) if i in keep]
assert len(trace)==117,len(trace)
refs=j['processing_result']['matched_path_evidence']['references'];features=j['processing_result']['road_geojson']['features']
assert len(refs)==len(features)
rows=[(r['source_key'],r['source_version'],r['source_feature_id'],r['reference_kind'],r['tags'],json.dumps(f['geometry'])) for r,f in zip(refs,features)]
if args.network:
 snapshot=json.load(open(args.network))
 rows=[(r['source_key'],r['source_version'],r['source_feature_id'],r['segment_kind'],r['tags'],r['geometry']) for r in snapshot]
connection=MagicMock();connection.__enter__.return_value=connection;connection.cursor.return_value.__enter__.return_value.fetchall.return_value=rows
modules=[('current',str(Path(__file__).resolve().parents[1]/'pedestrian_matching.py'))]
if args.baseline: modules.insert(0,('baseline',args.baseline))
print('FULL CANDIDATE SNAPSHOT' if args.network else 'SUBSET: missing alternatives; no ground-truth claim')
current_database=database
if args.baseline_database:
 spec=importlib.util.spec_from_file_location('baseline_database',args.baseline_database)
 baseline_database=importlib.util.module_from_spec(spec);spec.loader.exec_module(baseline_database)
for name,path in modules:
 database=baseline_database if name=='baseline' and args.baseline_database else current_database
 spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 with patch.object(database,'DATABASE_URL','trial'),patch.dict(database.database_state,status='ready'),patch.object(database.psycopg,'connect',return_value=connection),patch.object(database,'match_reference_trajectory',module.match_reference_trajectory):
  durations=[];profiles=[]
  for repeat in range(args.repeat):
   started=time.perf_counter();result=database.match_pedestrian_reference(trace);durations.append(time.perf_counter()-started)
   if result: profiles.append(result.get('matcher_profile',{}))
  elapsed=statistics.median(durations)
 if result:
  c=result['geojson']['features'][0]['geometry']['coordinates'];rev=[i for i in range(1,len(c)-1) if c[i-1]==c[i+1]]
  print(name,'seconds',elapsed,'distance',result['matched_distance_m'],'coordinates',len(c),'exact immediate reversals',len(rev),'network ways',len(rows))
  assert all(math.isfinite(value) for point in c for value in point)
  assert result['matched_path_evidence']['status']=='available'
  assert result['matched_distance_m'] <= j['walking_validation']['validated_gps_distance_m']*1.6
  if name=='baseline' and args.network:
   assert c==j['processing_result']['geojson']['features'][0]['geometry']['coordinates'], 'Baseline snapshot does not reproduce original geometry'
   print('Original matched geometry reproduced exactly')
  if profiles and profiles[0]: print('Median profile ms/counters:',{key:statistics.median(p[key] for p in profiles) for key in profiles[0]})
 else:print(name,'no match')

assert json.dumps(j,sort_keys=True)==original,'Diagnostic evidence was mutated'
