"""Read-only revision-4 canonical gap audit. Standard library, no matcher import.

Candidate bindings are unioned in memory; neither input DB nor bindings are written.
Historical mechanism labels are retained as provenance, never promoted to causes.
"""
import argparse,csv,gzip,hashlib,itertools,json,math,sqlite3,statistics
from collections import defaultdict,Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
TOL=1e-4
CAUSES=('deferred_binding_withheld','ambiguous_target_correspondence',
        'unadmitted_span_of_known_way','direction_gate',
        'finite_endpoint_or_alignment','no_admitted_source_correspondence')
def sha(p):
 h=hashlib.sha256()
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def union(spans):
 out=[]
 for a,b in sorted((min(a,b),max(a,b))for a,b in spans):
  if b<=a:continue
  if out and a<=out[-1][1]:out[-1]=(out[-1][0],max(b,out[-1][1]))
  else:out.append((a,b))
 return out
def sub(spans,covered):
 out=[];covered=union(covered)
 for a,b in spans:
  for l,h in covered:
   if h<=a:continue
   if l>=b:break
   if l>a:out.append((a,min(l,b)))
   a=max(a,h)
  if a<b:out.append((a,b))
 return out
def length(spans):return math.fsum(b-a for a,b in spans)
def project(t,spans,dest):
 for edge,a,b,n in t['network']:
  for lo,hi in spans:
   lo,hi=max(lo,min(a,b)),min(hi,max(a,b))
   if hi>lo:dest[edge].append(tuple(sorted(((lo-a)/(b-a)*n,(hi-a)/(b-a)*n))))
def stats(vals):
 vals=sorted(vals);total=math.fsum(vals)
 def quantile(q):
  x=(len(vals)-1)*q;i=int(x);return vals[i]+(vals[min(i+1,len(vals)-1)]-vals[i])*(x-i)
 return dict(count=len(vals),total_m=total,median_m=statistics.median(vals),mean_m=total/len(vals),p90_m=quantile(.9),p99_m=quantile(.99),
  below={str(n):dict(count=sum(v<n for v in vals),metres=math.fsum(v for v in vals if v<n))for n in (10,50,100,500,1000)},
  above={str(n):dict(count=sum(v>n for v in vals),metres=math.fsum(v for v in vals if v>n))for n in (1000,5000)},
  largest={str(n):dict(metres=math.fsum(vals[-n:]),percent=100*math.fsum(vals[-n:])/total)for n in (10,20,50,100)})
def dump_csv(path,rows):
 with open(path,'w')as f:
  w=csv.DictWriter(f,fieldnames=list(rows[0]),lineterminator='\n');w.writeheader();w.writerows(rows)
def run(database,inventory,screen,out):
 out=Path(out);out.mkdir(parents=True,exist_ok=True)
 assert sha(database)=='be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f'
 assert sha(inventory)=='1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb'
 assert sha(screen)=='9be024c03383cfea419a5017257a5ff2c32d02d0fc4d941ec9b1d0fdb27aa947'
 db=sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)
 assert db.execute('pragma quick_check').fetchone()==('ok',)
 lengths=dict(db.execute('select id,length from network'));den=math.fsum(lengths.values())
 review=json.loads((ROOT/'docs/ncn-corridor-policy/resolution/review.json').read_text())
 chapel=json.loads((ROOT/'docs/ncn-corridor-policy/delivery/chapel-review.json').read_text())
 assert sha(ROOT/'docs/ncn-corridor-policy/resolution/review.json')=='e76571514408eaa7b20a7e9cd88acce42cdcd188822cde09948161faee18c9ab'
 assert sha(ROOT/'docs/ncn-corridor-policy/delivery/chapel-review.json')=='f356c3d4b25410d3e913e07446362e380f76bcd7f5f8205d39f3f652b34c7359'
 added=defaultdict(list)
 for c in review['cases']:
  raw=db.execute('select data from paths where route=? and way=?',(c['route'],c['way'])).fetchone()[0]
  assert json.loads(gzip.decompress(raw))==c['path']
  for b in c['bindings']:added[c['route'],b['target']].append((b['low'],b['high']))
 assert json.loads(gzip.decompress(db.execute('select data from paths where route=? and way=?',('NCN:1','4471919')).fetchone()[0]))==chapel['path']
 b=chapel['binding'];added['NCN:1',b['target']].append((b['low'],b['high']))
 targets={};edge_routes=defaultdict(set);edge_ends={};live=defaultdict(list);candidate=defaultdict(list)
 route_gaps={};route_layers=defaultdict(lambda:defaultdict(list))
 for route, in db.execute('select key from routes order by key'):
  cov=defaultdict(list)
  for raw, in db.execute('select data from paths where route=?',(route,)):
   p=json.loads(gzip.decompress(raw))
   for b in p['bindings']:
    assert b['modes'] and set(b['modes'])<=set(p['modes'])&{'on_foot','cycling'}
    cov[b['target']].append((b['low'],b['high']))
  gap_total=0
  for key,n,coords,net in db.execute('select key,length,coords,network from targets where route=?',(route,)):
   t=dict(length=n,coords=json.loads(coords),network=json.loads(net));targets[route,key]=t
   before=union(cov[key]);after=union(before+added[route,key]);project(t,before,live);project(t,after,candidate)
   gap=sub([(0,n)],after);gap_total+=length(gap);project(t,gap,route_layers[route])
   for edge,a,z,size in t['network']:
    assert len(t['coords'])==2
    assert abs(size-lengths[edge])<TOL
    edge_routes[edge].add(route)
    c,d=t['coords'];ends=tuple(tuple(round(c[i]+(d[i]-c[i])*x/n,8)for i in (0,1))for x in (a,z))
    if edge not in edge_ends:edge_ends[edge]=ends
  route_gaps[route]=gap_total
 db.close();live_gaps={e:sub([(0,n)],live[e])for e,n in lengths.items()}
 gaps={e:sub([(0,n)],candidate[e])for e,n in lengths.items()}
 live_total=math.fsum(length(x)for x in live_gaps.values());total=math.fsum(length(x)for x in gaps.values())
 assert abs(live_total-1288762.517376247)<TOL
 assert abs((live_total-total)-2128.6161505962186)<TOL
 regions={c:defaultdict(list)for c in CAUSES};historical_count=0
 with gzip.open(inventory,'rt')as f:
  for line in f:
   r=json.loads(line);historical_count+=1;t=targets[r['route_key'],r['target_key']]
   assert r['target_coordinates']==t['coords'] and abs(r['target_length_m']-t['length'])<TOL
   project(t,[(r['low_m'],r['high_m'])],regions[r['cause']])
 assert historical_count==442910
 # Event partition keeps mixed historical labels explicit and exclusive.
 categories=Counter();catvals=defaultdict(list);catroutes=defaultdict(set);rows=[]
 for edge,n in lengths.items():
  events=[]
  for c in CAUSES:
   for a,b in union(regions[c][edge]):events.extend([(a,c,1),(b,c,-1)])
  for a,b in gaps[edge]:events.extend([(a,'gap',1),(b,'gap',-1)])
  active=Counter();previous=0
  for position,group in itertools.groupby(sorted(events),key=lambda e:e[0]):
   if position>previous and active['gap']:
    labels=tuple(c for c in CAUSES if active[c]);label=' + '.join(labels)or'unclassified_projection_residue'
    v=position-previous;categories[label]+=v;catvals[label].append(v);catroutes[label].update(edge_routes[edge])
   for _,c,change in group:active[c]+=change
   previous=position
  for a,b in gaps[edge]:
   if b-a>1e-7:rows.append(dict(edge=edge,low_m=a,high_m=b,length_m=b-a,routes='|'.join(sorted(edge_routes[edge]))))
 assert abs(sum(categories.values())-total)<TOL
 positive_total=math.fsum(r['length_m']for r in rows)
 # Physical components join only coincident pinned endpoints (8 decimals), not nearby lines.
 parent=list(range(len(rows)));sizes=[r['length_m']for r in rows]
 def find(i):
  while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
  return i
 ends=defaultdict(list)
 for i,r in enumerate(rows):
  if r['edge']not in edge_ends:continue
  if r['low_m']<1e-7:ends[edge_ends[r['edge']][0]].append(i)
  if lengths[r['edge']]-r['high_m']<1e-7:ends[edge_ends[r['edge']][1]].append(i)
 for hits in ends.values():
  for i in hits[1:]:parent[find(i)]=find(hits[0])
 components=defaultdict(list)
 for i in range(len(rows)):components[find(i)].append(i)
 clusters=[]
 for ids in components.values():
  cluster=dict(length_m=math.fsum(sizes[i]for i in ids),intervals=len(ids),routes='|'.join(sorted(set(r for i in ids for r in rows[i]['routes'].split('|')))),edges='|'.join(str(rows[i]['edge'])for i in ids))
  clusters.append(cluster)
 clusters.sort(key=lambda x:-x['length_m'])
 # Route-exclusive allocation splits shared canonical gap equally among its owning routes.
 allocated=Counter();associated=Counter()
 for r in rows:
  routes=r['routes'].split('|')if r['routes']else['unmapped_numerical_residue']
  for route in routes:allocated[route]+=r['length_m']/len(routes);associated[route]+=r['length_m']
 assert abs(sum(allocated.values())-positive_total)<TOL
 ranked=[dict(route=r,allocated_unique_m=n,associated_unique_m=associated[r],route_local_gap_m=route_gaps.get(r,0))for r,n in allocated.most_common()]
 # Reuse prior screen; map its finite intervals through exact canonical mappings.
 screen_rows=json.loads(Path(screen).read_text());assert len(screen_rows)==47869
 screens=defaultdict(lambda:defaultdict(list));sourcegroups=defaultdict(lambda:defaultdict(list))
 for r in screen_rows:
  t=targets[r['route'],r['target_key']];span=[(r['low_m'],r['high_m'])]
  labels=['all_screened', 'admitted_edge'if r['candidate_edge_within_admitted_section']else'unadmitted_edge', 'historical_'+r['historical_cause']]
  if r['candidate_edge_within_admitted_section'] and r['separation_sampled_max_m']<=2 and r['angle_deg']<=15 and r['alternative_count']==1:labels.append('strict_geometry_single_option_admitted')
  if r['candidate_edge_within_admitted_section'] and r['separation_sampled_max_m']<=2:labels.append('admitted_max_separation_2m')
  for label in labels:project(t,span,screens[label])
  project(t,span,sourcegroups[r['route'],r['source_way']])
 def residual(layers):return math.fsum(length(sub(union(spans),candidate[e]))for e,spans in layers.items())
 screen_totals={k:residual(v)for k,v in screens.items()}
 groups=sorted([dict(route=k[0],source_way=k[1],screen_residual_unique_m=residual(v))for k,v in sourcegroups.items()],key=lambda r:-r['screen_residual_unique_m'])
 # Membership flags overlap; exact all_screened union is the only additive national total.
 def summary_categories():
  return [dict(historical_labels=k,unique_m=v,deficit_percent=100*v/total,partition_pieces=len(catvals[k]),routes=len(catroutes[k]),median_piece_m=statistics.median(catvals[k]))for k,v in categories.most_common()]
 result=dict(schema_version=1,baseline='24278f5256bf367039b9a3adf986d619754ccb15',numerical_tolerance_m=TOL,
  input_sha256={'database':sha(database),'historical_inventory':sha(inventory),'screen':sha(screen),'review':sha(ROOT/'docs/ncn-corridor-policy/resolution/review.json'),'chapel':sha(ROOT/'docs/ncn-corridor-policy/delivery/chapel-review.json')},
  denominator_m=den,published_gap_m=live_total,candidate_gap_m=total,candidate_added_m=live_total-total,
  published_assessability_percent=100*(den-live_total)/den,candidate_assessability_percent=100*(den-total)/den,
  historical_regions=summary_categories(),semantic_current_root_cause_unknown_m=total,
  canonical_intervals=stats([r['length_m']for r in rows]),positive_fragment_numerical_omission_m=total-positive_total,
  connected_components=stats([r['length_m']for r in clusters]),screen_residual_unique_m=screen_totals,
  route_allocation='Equal allocation across all route owners of each canonical edge; associated column overlaps, allocated column additive.',
  component_method='Coincident projected edge endpoints rounded to 8 WGS84 decimals, 1e-7m endpoint numerical tolerance; connected geometry is not common cause.',
  limitations=['Historical mechanism envelopes are not newly established underlying causes.','Screen labels overlap; proximity/heading are not entitlement.','Fragments <=1e-7m excluded from size/graph statistics, retained in exact reconciliation.','Eight-decimal endpoint joins may underjoin displaced junctions; branches may connect unrelated problems.','No active source graph, current access inventory or original import metadata inferred.'])
 (out/'results.json').write_text(json.dumps(result,indent=2)+'\n')
 dump_csv(out/'historical-regions.csv',summary_categories());dump_csv(out/'routes.csv',ranked)
 dump_csv(out/'largest-clusters.csv',clusters[:100]);dump_csv(out/'screen-source-groups.csv',groups[:100])
 with gzip.GzipFile(filename=str(out/'canonical-intervals.csv.gz'),mode='wb',mtime=0)as f:
  import io
  s=io.StringIO();w=csv.DictWriter(s,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows);f.write(s.getvalue().encode())
 print(json.dumps({k:v for k,v in result.items()if k not in ('input_sha256','historical_regions')},indent=2))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('database','inventory','screen','output'):p.add_argument('--'+name,required=True)
 a=p.parse_args();run(a.database,a.inventory,a.screen,a.output)
