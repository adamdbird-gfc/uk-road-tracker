import gzip,hashlib,importlib.util,json,math,unittest
from pathlib import Path
from collections import Counter,defaultdict
from shapely.geometry import LineString,Point
from shapely.ops import transform,substring
from pyproj import Geod
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('corridor_discovery',HERE/'discover.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class DiscoveryTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  p=HERE/'results/national-candidates.json';cls.rows=json.loads(p.read_text()) if p.exists() else json.loads(gzip.decompress(p.with_suffix('.json.gz').read_bytes()));cls.receipt=json.loads((HERE/'results/national-summary.json').read_text());cls.review=json.loads((HERE/'review-data.json').read_text())
 def test_union_partial_repeats_and_routes(self):
  self.assertEqual(mod.union([(0,10),(3,8),(8,12),(15,18),(18,20)]),[(0,12),(15,20)])
 def test_subtraction_preserves_holes(self):
  self.assertEqual(mod.subtract((0,20),[(2,4),(3,6),(15,30)]),[(0,2),(6,15)])
 def test_metric_against_geodesic(self):
  for coords in ([[1.4,51.2],[1.401,51.201]],[[-6.36,54.3],[-6.359,54.301]],[[-3.7,57.2],[-3.699,57.201]]):
   fw,_=mod.metric(coords);n=transform(fw.transform,LineString(coords)).length;expected=Geod(ellps='WGS84').line_length(*zip(*coords));self.assertAlmostEqual(n,expected,places=5)
 def test_stable_ids_and_no_duplicate_alternative_rows(self):
  ids=[]
  for r in self.rows:
   key=[r['route'],r['target_key'],r['low_m'],r['high_m'],r['source_way'],r['source_edge']];ids.append(r['id']);self.assertEqual(r['id'],'PC-'+hashlib.sha256(json.dumps(key,separators=(',',':')).encode()).hexdigest()[:12])
  self.assertEqual(len(ids),len(set(ids)))
 def test_confidence_and_zero_credit(self):
  self.assertEqual(dict(Counter(r['classification'] for r in self.rows)),self.receipt['confidence_counts']);self.assertTrue(all(r['new_credit_m']==0 for r in self.rows));self.assertIsNone(self.receipt['unique_network_recoverable_m'])
 def test_finite_section_no_whole_way_transfer(self):
  for r in self.rows:
   contained=any(k==r['source_way'] or (len(k.split(':'))==3 and k.split(':')[0]==r['source_way'] and int(k.split(':')[1])<=r['source_edge']<int(k.split(':')[2])) for k in r['source_section_keys']);self.assertEqual(contained,r['candidate_edge_within_admitted_section']);self.assertEqual(r['classification'],'Requires review' if contained else 'Unresolved')
 def test_evidence_retained(self):
  for r in self.rows:
   for k in ['reason','access_evidence','route_membership_evidence','topology_evidence','source_geometry_sha256','historical_cause']:self.assertTrue(r[k])
 def test_selected_measurements_reproduce(self):
  # Independent recomputation of every viewer-selected candidate; no repeated national scan.
  ids={i for g in self.review['selected_national_groups'] for i in g['candidate_ids']}
  for r in self.rows:
   if r['id'] not in ids:continue
   fw,_=mod.metric(r['target_coordinates']);t=transform(fw.transform,LineString(r['target_coordinates']));gap=substring(t,r['low_m']/r['target_length_m'],r['high_m']/r['target_length_m'],normalized=True);e=transform(fw.transform,LineString(r['source_coordinates']));ds=[gap.interpolate(v,normalized=True).distance(e) for v in (0,.25,.5,.75,1)];self.assertAlmostEqual(max(ds),r['separation_sampled_max_m'],places=7);self.assertAlmostEqual(sum(ds)/5,r['separation_mean_m'],places=7)
 def test_revision4_residual_arithmetic(self):
  post=json.loads((mod.ROOT/'docs/data-trials/ncn-post-seam-reconciliation-20261030.json').read_text());self.assertAlmostEqual(self.receipt['remaining_route_local_m'],1287660.4299504848,places=5);self.assertEqual(self.receipt['historical_rows'],442910);self.assertAlmostEqual(sum(self.receipt['remaining_historical_cause_m'].values()),self.receipt['remaining_route_local_m'],places=4)
 def test_register_total_independent_sum(self):
  self.assertAlmostEqual(math.fsum(r['unassessable_m'] for r in self.rows),self.receipt['gross_route_local_candidate_m'],places=5);self.assertEqual(len(self.rows),47869)
 def test_negative_controls(self):
  controls={e['id']:e for e in self.review['examples']};self.assertEqual(controls['NEG-DEAL-PRIVATE']['classification'],'Rejected');self.assertEqual(controls['REF-44807950']['classification'],'Rejected');self.assertEqual(self.review['private_measurements']['tags']['access'],'private');self.assertEqual(self.review['private_measurements']['new_credit_m'],0)
 def test_viewer_geometry_corresponds(self):
  byid={r['id']:r for r in self.rows};n=0
  for e in self.review['examples']:
   if not e['id'].startswith('NAT-'):continue
   for f in e['features']:
    p=f['properties']
    if p['layer']=='candidate':self.assertEqual(f['geometry']['coordinates'],byid[p['id']]['alternative_coordinates']);n+=1
  self.assertGreater(n,3)
 def test_twelve_geographical_examples(self):
  self.assertEqual(len(self.review['examples']),12);self.assertEqual(len({e['id'] for e in self.review['examples']}),12);self.assertTrue(self.review['diagnostic_only'])
if __name__=='__main__':unittest.main()
