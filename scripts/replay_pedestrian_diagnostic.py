"""Private offline regression; never uploads the diagnostic or invents missing ways."""
import sys,json,math,time,importlib.util
from unittest.mock import MagicMock,patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import database
if len(sys.argv) < 2:
 raise SystemExit('Usage: replay_pedestrian_diagnostic.py PRIVATE_EXPORT [BASELINE_MATCHING_MODULE]')
j=json.load(open(sys.argv[1]))['journey']
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
connection=MagicMock();connection.__enter__.return_value=connection;connection.cursor.return_value.__enter__.return_value.fetchall.return_value=rows
modules=[('current',str(Path(__file__).resolve().parents[1]/'pedestrian_matching.py'))]
if len(sys.argv)>2: modules.insert(0,('baseline',sys.argv[2]))
print('SUBSET REPLAY: exported matched ways only; missing alternatives; no ground-truth claim')
for name,path in modules:
 spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 with patch.object(database,'DATABASE_URL','trial'),patch.dict(database.database_state,status='ready'),patch.object(database.psycopg,'connect',return_value=connection),patch.object(database,'match_reference_trajectory',module.match_reference_trajectory):
  started=time.perf_counter();result=database.match_pedestrian_reference(trace);elapsed=time.perf_counter()-started
 if result:
  c=result['geojson']['features'][0]['geometry']['coordinates'];rev=[i for i in range(1,len(c)-1) if c[i-1]==c[i+1]]
  print(name,'seconds',elapsed,'distance',result['matched_distance_m'],'coordinates',len(c),'exact immediate reversals',len(rev),'network ways',len(rows))
  assert all(math.isfinite(value) for point in c for value in point)
  assert result['matched_path_evidence']['status']=='available'
  assert result['matched_distance_m'] <= j['walking_validation']['validated_gps_distance_m']*1.6
 else:print(name,'no match')
