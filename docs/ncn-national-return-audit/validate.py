"""Independent register arithmetic and endpoint-precision sensitivity checks."""
import argparse,csv,gzip,hashlib,json,math,sqlite3
from collections import defaultdict
from pathlib import Path

def check(database,folder):
 folder=Path(folder);r=json.loads((folder/'results.json').read_text())
 with gzip.open(folder/'canonical-intervals.csv.gz','rt')as f:rows=list(csv.DictReader(f))
 total=math.fsum(float(x['length_m'])for x in rows)
 assert len(rows)==r['canonical_intervals']['count']
 assert abs(total+r['positive_fragment_numerical_omission_m']-r['candidate_gap_m'])<1e-8
 assert abs(sum(x['unique_m']for x in r['historical_regions'])-r['candidate_gap_m'])<1e-7
 with (folder/'routes.csv').open()as f:route_rows=list(csv.DictReader(f))
 assert abs(math.fsum(float(x['allocated_unique_m'])for x in route_rows)-total)<1e-7
 db=sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)
 lengths=dict(db.execute('select * from network'));ends={}
 for n,coords,net in db.execute('select length,coords,network from targets order by route,key'):
  c=json.loads(coords)
  for e,a,b,size in json.loads(net):
   if e not in ends:ends[e]=[tuple(c[0][i]+(c[-1][i]-c[0][i])*v/n for i in (0,1))for v in (a,b)]
 db.close();sensitivity={}
 for precision in (7,8,9):
  parent=list(range(len(rows)));seen={}
  def root(i):
   while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
   return i
  for i,row in enumerate(rows):
   e=int(row['edge'])
   if e not in ends:continue
   for side,hit in ((0,float(row['low_m'])<1e-7),(1,lengths[e]-float(row['high_m'])<1e-7)):
    if not hit:continue
    key=tuple(round(v,precision)for v in ends[e][side])
    if key in seen:parent[root(i)]=root(seen[key])
    else:seen[key]=i
  groups=defaultdict(float)
  for i,row in enumerate(rows):groups[root(i)]+=float(row['length_m'])
  v=sorted(groups.values(),reverse=True)
  sensitivity[precision]=dict(count=len(v),over_1km=sum(x>1000 for x in v),over_1km_m=sum(x for x in v if x>1000),largest_100_m=sum(v[:100]))
  assert abs(sum(v)-total)<1e-6
 assert sensitivity[8]['count']==r['connected_components']['count']
 assert abs(sensitivity[8]['largest_100_m']-r['connected_components']['largest']['100']['metres'])<1e-7
 # Independent interval controls: finite reversed target mapping and shared union.
 import importlib.util
 spec=importlib.util.spec_from_file_location('audit',Path(__file__).with_name('audit.py'));a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
 dest=defaultdict(list);a.project({'network':[[7,100.,0.,80.]]},[(25.,75.)],dest)
 assert a.union(dest[7])==[(20.,60.)]
 assert a.sub([(0,100)],[(30,40),(20,35),(80,120)])==[(0,20),(40,80)]
 assert a.length(a.union([(0,50),(25,75),(50,100)]))==100
 result=dict(register_arithmetic='passed',reversed_mapping_and_shared_union_controls='passed',endpoint_precision_sensitivity=sensitivity,
  interpretation='Precision sensitivity describes geometry grouping only; it does not prove common cause or eligible recovery.')
 (folder/'validation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--results',required=True);a=p.parse_args();check(a.database,a.results)
