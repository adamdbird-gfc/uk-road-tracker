from copy import deepcopy
import math
import unittest
from .network_geometry import metric_projector
from .network_reconciliation import reconcile,prepare_sources


def fixture():
    return ({'features':[{'properties':{'FID':1,'RouteType':'NCN','RouteNo':177,'OpenStatus':'Open','RouteCat':'Main Route','Desc_':'TrafficFree'},
        'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0]]}}]},
        {'routes':[{'id':'NCN:177'},{'id':'NCN:999'}]},
        [{'relation_id':1,'tags':{'network':'ncn','ref':'177'},'ways':[{'way_id':42}],'member_lists_complete':True}],
        {'routes':[{'official_route_key':'NCN:177','candidate_relation_ids':[1],'uk_metadata_anchor':True}]},
        {1:{'id':1,'type':'relation','tags':{'network':'ncn','ref':'177'},'route':{'ways':[
            {'route_type':'base','id':42,'geometry':{'type':'LineString','coordinates':[[0,0],[111.31949079327358,0]]}}]}}})


class NetworkReconciliationTests(unittest.TestCase):
    def test_close_alignment_is_candidate_not_approved_and_missing_routes_remain(self):
        r=reconcile(*fixture())
        self.assertEqual(r['official_numbered_route_count'],2)
        a,b=r['routes'];self.assertEqual(a['state'],'close_bidirectional_correspondence_candidate')
        self.assertEqual(b['state'],'missing_source_membership')
        self.assertFalse(a['association_approved']);self.assertFalse(r['completion_ready'])

    def test_missing_download_and_partial_members_cannot_become_complete(self):
        f=list(fixture());f[-1]={}
        self.assertEqual(reconcile(*f)['routes'][0]['state'],'source_geometry_unavailable')
        f=list(fixture());f[2][0]['ways'].append({'way_id':43})
        self.assertEqual(reconcile(*f)['routes'][0]['state'],'partial_source_geometry')

    def test_scope_and_classification_disagreement_are_preserved(self):
        f=list(fixture());f[3]['routes'][0]['uk_metadata_anchor']=False
        self.assertEqual(reconcile(*f)['routes'][0]['state'],'source_scope_review_required')
        f=list(fixture());f[-1][1]['tags']['network']='rcn'
        r=reconcile(*f);self.assertIn('1',r['source_parse_errors'])
        self.assertFalse(r['routes'][0]['source_geometry_complete'])

    def test_offset_sensitivity_does_not_approve_parallel_path(self):
        f=list(fixture())
        for p in f[-1][1]['route']['ways'][0]['geometry']['coordinates']:p[1]=20
        row=reconcile(*f)['routes'][0]
        self.assertEqual(row['state'],'offset_sensitive_correspondence_review')
        self.assertTrue(row['alternative_or_offset_review_needed']);self.assertFalse(row['association_approved'])

    def test_cross_profile_conflicts_hold_shared_way(self):
        _,_,profiles,_,providers=fixture();other=deepcopy(profiles[0]);other['relation_id']=2
        value=deepcopy(providers[1]);value['id']=2;value['route']['ways'][0]['geometry']['coordinates'][1][1]=10
        extracted,errors,conflicts=prepare_sources(profiles+[other],{**providers,2:value})
        self.assertEqual(conflicts,{42});self.assertFalse(errors)
        with self.assertRaises(ValueError):prepare_sources(profiles+profiles,providers)

    def test_long_route_projection_has_correct_radial_distance(self):
        project=metric_projector([0,0]);distance=900000
        x,y=project([math.degrees(distance/6371008.8),0])
        self.assertAlmostEqual(x,distance,places=6);self.assertAlmostEqual(y,0,places=6)
        with self.assertRaises(ValueError):project([180,0])


if __name__=='__main__':unittest.main()
