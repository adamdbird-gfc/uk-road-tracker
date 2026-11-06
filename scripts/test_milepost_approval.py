import copy
import hashlib
import json
import unittest
from scripts.milepost_approval import build,verify,DATA,ASSET,ROOT,classify

class ApprovalTest(unittest.TestCase):
    def rows(self):return [json.loads(x) for x in (DATA/'revision-1/catalogue.jsonl').read_text().splitlines()]
    def test_reproducible_review_and_payload(self):
        verify();self.assertEqual(build(),build())
    def test_complete_decisions_and_specific_holds(self):
        rows=self.rows();ds=classify(rows)
        self.assertEqual([r['osm_id'] for r in rows],[d['candidate_id'] for d in ds])
        self.assertEqual(887,sum(d['status']=='approved' for d in ds))
        self.assertEqual({'osm:node:416710572','osm:node:9825206150'},{d['candidate_id'] for d in ds if d['status']=='held'})
        for d in ds:
            if d['status']!='approved':self.assertTrue(d['reasons'])
    def test_runtime_pin_approved_only_and_attributed(self):
        r=json.loads(ASSET.read_text())['records'];self.assertEqual(887,len(r))
        self.assertEqual({'mills','rowe','mccoll','dudgeon'},{x['designer'] for x in r})
        self.assertTrue(all(x['status']=='approved' for x in r))
        self.assertEqual(len(r),len({x['physical_marker_id'] for x in r}))
    def test_hostile_coordinates_identity_classification_and_duplicates(self):
        for key,value,reason in [('latitude',100,'invalid_coordinates'),('osm_id','test:1','invalid_osm_point_identity')]:
            r=copy.deepcopy(self.rows()[:1]);r[0][key]=value;self.assertIn(reason,classify(r)[0]['reasons'])
        r=self.rows()[:2];r[1]['osm_id']=r[0]['osm_id'];self.assertTrue(all(d['status']=='held' for d in classify(r)))
        r=self.rows()[:1];r[0]['source_tags']['ncn_milepost']='no';self.assertEqual('rejected',classify(r)[0]['status'])
        r=self.rows()[:2];r[1]['latitude']=r[0]['latitude'];r[1]['longitude']=r[0]['longitude'];self.assertTrue(all(d['status']=='held' for d in classify(r)))
    def test_unknown_or_conflicting_design_keeps_location_without_award(self):
        for value in ['yes','unknown']:
            r=self.rows()[:1];r[0]['source_tags']['ncn_milepost']=value;d=classify(r)[0]
            self.assertEqual('approved',d['status']);self.assertFalse(d['designer_verified'])
        r=self.rows()[:1];r[0]['source_tags']['artist_name']='Iain McColl';d=classify(r)[0]
        self.assertEqual('approved',d['status']);self.assertEqual('unknown',d['designer'])
    def test_movement_limits_history_without_blocking_current_location(self):
        r=self.rows()[:1];d=classify(r)[0];self.assertEqual('approved',d['status']);self.assertEqual('snapshot_forward_known_movement',d['temporal_policy'])
    def test_nearby_distinct_posts_are_not_merged(self):
        rows=[r for r in self.rows() if r['osm_id'] in ('osm:node:11954626856','osm:node:11962688546','osm:node:11962688547','osm:node:11962688548')]
        self.assertEqual(4,len(rows));self.assertTrue(all(d['status']=='approved' for d in classify(rows)))
