import json,hashlib,collections,copy,unittest
from pathlib import Path
from venue_approval import validate_approval
ROOT=Path(__file__).resolve().parents[1]
class CompletionCatalogueTest(unittest.TestCase):
    def setUp(self):
        self.root=json.loads((ROOT/'android-prototype/app/src/main/assets/collections/uk-wetherspoons-map-v1.json').read_text())
        self.review=json.loads((ROOT/'collections/wetherspoons-coverage-review-v1.json').read_text())
    def test_every_dated_current_member_resolves_once_to_active_or_temporary_identity(self):
        members=self.review['members'];self.assertEqual(814,len(members));self.assertEqual(814,len({v['source_url'] for v in members}));self.assertEqual(814,len({v['venue_id'] for v in members}))
        active={v['venue_id'] for v in members if v['status']=='active'};self.assertEqual(active,{v['id'] for v in self.root['venues']});self.assertEqual(813,len(active))
        self.assertEqual({'spoons-osm-w507289751'},{v['venue_id'] for v in members if v['status']=='temporarily_closed'})
        digest=hashlib.sha256(json.dumps(members,sort_keys=True,separators=(',',':')).encode()).hexdigest();self.assertEqual(digest,self.root['coverage_review']['membership_sha256'])
        validate_approval(self.root)
    def test_county_counts_use_the_existing_92_county_reference(self):
        counties={c['code']:c for c in json.loads((ROOT/'android-prototype/app/src/main/assets/historic-counties/catalogue.json').read_text())['counties']}
        for v in self.root['venues']:
            self.assertIn(v['county']['code'],counties);self.assertEqual({k:counties[v['county']['code']][k] for k in ['code','name','nation']},v['county'])
        counts=dict(collections.Counter(v['county']['code'] for v in self.root['venues']));self.assertEqual(67,len(counts));self.assertEqual(counts,self.root['coverage_review']['county_counts'])
    def test_incomplete_or_wrong_target_never_passes_certification(self):
        for mutate in [lambda r:r.update(completion_target=814),lambda r:r.update(coverage_complete=False),lambda r:r['coverage_review']['county_counts'].update(KNT=99)]:
            r=copy.deepcopy(self.root);mutate(r)
            with self.assertRaises(ValueError):validate_approval(r)
    def test_baseline_does_not_backfill_prior_closed_pubs(self):
        self.assertEqual([],self.root['lifecycle']);self.assertNotIn('spoons-osm-w38325241',{v['id'] for v in self.root['venues']});self.assertTrue(self.root['lifecycle_started_at'])
if __name__=='__main__':unittest.main()
