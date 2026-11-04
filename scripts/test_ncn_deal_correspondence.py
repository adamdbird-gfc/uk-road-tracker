"""Focused guards for a reviewed identity exception, not a proximity bypass."""
import copy
import gzip
import json
import math
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from scripts import ncn_deal_correspondence as deal
from scripts.inspect_ncn_deal_resolution import audit_geometry


class DealCorrespondenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.review,cls.fixture=deal.load_review()
        cls.obs=json.loads(deal.OBSERVATIONS.read_text())
        f=cls.fixture
        # Existing preserved real source row, including both ordinary seams.
        cls.path={'route':'NCN:1','way':'4471747','coordinates':f['path']['coordinates'],
                  'nodes':f['path']['osm_node_ids'],'modes':['on_foot','cycling'],
                  'origin':f['projection_origin'],'source_geometry_sha256':f['path']['source_geometry_sha256'],
                  'positions':json.loads((deal.ROOT/'docs/data-trials/ncn1-deal-results-20261030.json').read_text())['source_positions_m'],
                  'bindings':f['corrected_full_way_bindings']}
        # The source-offset fixture predates the two preserved seam insertions.
        for b in f['seam_source_insertions']:
            cls.path['bindings'].insert(b['insertion_position'],dict(start=b['start'],end=b['end'],target=b['target'],low=b['low'],high=b['high'],modes=['on_foot','cycling']))
        # Preserve the artifact's actual mode ordering (mask 7 differs from 3).
        if deal.digest(cls.path['bindings'])!=cls.review['previous_bindings_sha256']:
            raise AssertionError('Pinned real before-bindings reconstruction differs')

    def test_all_five_new_edges_are_reviewed_and_existing_seams_survive(self):
        updated=deal.corrected_path(self.path,self.review,self.fixture)
        self.assertEqual(updated['bindings'][:len(self.path['bindings'])],self.path['bindings'])
        self.assertEqual(len(updated['bindings'])-len(self.path['bindings']),6)
        self.assertEqual(self.review['approved_edges'],[6,7,8,9,10])

    def test_private_and_unrelated_route_never_qualify(self):
        for k,v in [('way','77611523'),('route','NCN:2'),('source_geometry_sha256','0'*64)]:
            p=copy.deepcopy(self.path);p[k]=v
            with self.assertRaises(ValueError):deal.corrected_path(p,self.review,self.fixture)

    def test_changed_source_nodes_geometry_modes_or_prior_bindings_fail(self):
        for kind in ['nodes','coordinates','modes','bindings','origin']:
            p=copy.deepcopy(self.path)
            if kind=='nodes':p[kind].reverse()
            elif kind=='coordinates':p[kind][7][0]+=.000001
            elif kind=='modes':p[kind]=['cycling']
            elif kind=='origin':p[kind]=[0.,0.]
            else:p[kind].pop()
            with self.assertRaises(ValueError):deal.corrected_path(p,self.review,self.fixture)

    def test_access_membership_and_connectivity_fail_closed(self):
        for kind in ['private','proposed','missing_member','node_order','disconnected']:
            obs=copy.deepcopy(self.obs);way=next(w for w in obs['osm']['ways']if w['id']=='4471747');r=next(r for r in obs['osm']['relations']if r['id']=='75880')
            if kind=='private':way['tags']['access']='private'
            elif kind=='proposed':r['tags']['route']='proposed'
            elif kind=='missing_member':r['relevant_members']=[]
            elif kind=='node_order':way['nodes'].reverse()
            else:next(w for w in obs['osm']['ways']if w['id']=='84079705')['nodes']=['bad','other']
            with self.assertRaises(ValueError):deal.validate_observations(self.fixture,obs)

    def test_reviewed_junction_owns_source_once_and_is_finite(self):
        rows=self.review['bindings'];j=self.review['junction']
        self.assertEqual(rows[-2]['end'],rows[-1]['start'])
        self.assertEqual(rows[-2]['high'],self.fixture['target_length_m'])
        self.assertEqual(rows[-1]['low'],0.)
        self.assertGreater(j['reviewed_junction_source_m'],rows[-2]['start'])
        self.assertLess(j['reviewed_junction_source_m'],rows[-1]['end'])
        self.assertLess(j['junction_eq_offset_m'],10)
        self.assertAlmostEqual(j['automatic_unbound_source_m'],.02838108716842669)

    def test_overlapping_evidence_is_rejected(self):
        r=copy.deepcopy(self.review);r['bindings'][-1]['start']=r['bindings'][-2]['start']
        with self.assertRaisesRegex(ValueError,'ownership overlap'):deal.corrected_path(self.path,r,self.fixture)

    def test_original_geometry_admission_still_fails_without_review(self):
        result=json.loads((deal.ROOT/'docs/data-trials/ncn1-deal-results-20261030.json').read_text())
        for i in self.review['approved_edges']:
            self.assertFalse(result['edge_results'][i]['necessary_whole_edge_approved_corridor_gate'])
        self.assertEqual(deal.POLICY['maximum_offset_m'],10)
        self.assertEqual(deal.POLICY['maximum_angle_degrees'],15)

    def test_local_ground_measurement_independently_matches_geodesics(self):
        r=audit_geometry()
        self.assertEqual([x['edge_index']for x in r['edge_verdicts']if x['prior_status']=='held'],[6,7,8,9,10])
        for x in r['geodesic_crosscheck']:self.assertLess(abs(x['difference_m']),.005)
        self.assertEqual(r['private_control']['verdict'],'Exclude')
        self.assertGreater(r['edge_verdicts'][7]['finite_focus_min_m'],10)

    def test_input_and_existing_output_are_never_overwritten(self):
        with tempfile.TemporaryDirectory()as tmp:
            p=Path(tmp)/'existing';p.write_text('untouched')
            with self.assertRaisesRegex(ValueError,'Fresh output'):deal.apply(p,p)
            self.assertEqual(p.read_text(),'untouched')


if __name__=='__main__':unittest.main()
