from copy import deepcopy
import unittest
from .network_geometry import extract_geometry,wgs84


def provider():
    return {'id':1,'type':'relation','tags':{'network':'ncn','ref':'177'},'route':{'main':[{'ways':[
        {'route_type':'base','id':42,'geometry':{'type':'LineString','coordinates':[[0,0],[100,0]]}}
    ]}]}}


def profile():
    return {'relation_id':1,'tags':{'network':'ncn','ref':'177'},'ways':[{'way_id':42}],'member_lists_complete':True}


class NetworkGeometryTests(unittest.TestCase):
    def test_membership_anchors_geometry_not_proximity(self):
        p=profile();p['ways']=[{'way_id':99}]
        r=extract_geometry(provider(),p)
        self.assertEqual(r['ways'],[]);self.assertEqual(r['unlisted_provider_way_ids'],[42])
        self.assertEqual(r['missing_way_ids'],[99])

    def test_nested_directional_repeats_deduplicate(self):
        value=provider();part=deepcopy(value['route']['main'][0]['ways'][0]);part['geometry']['coordinates'].reverse()
        value['route']['appendices']=[{'subroutes':[{'ways':[part]}]}]
        r=extract_geometry(value,profile());self.assertEqual(len(r['ways']),1)
        self.assertTrue(r['provider_geometry_complete']);self.assertFalse(r['osm_node_identity_verified'])
        self.assertFalse(r['completion_ready'])

    def test_conflicting_geometry_is_held(self):
        value=provider();part=deepcopy(value['route']['main'][0]['ways'][0]);part['geometry']['coordinates'][1][1]=10
        value['route']['appendices']=[part]
        r=extract_geometry(value,profile());self.assertEqual(r['ways'],[]);self.assertEqual(r['conflicting_way_ids'],[42])

    def test_classification_change_refused(self):
        for key,new in [('id',2),('tags',{'network':'rcn','ref':'177'})]:
            value=provider();value[key]=new
            with self.assertRaises(ValueError):extract_geometry(value,profile())

    def test_invalid_or_missing_geometry_never_bridges(self):
        value=provider();value['route']['main'][0]['ways'][0]['geometry']['coordinates']=[[0,0],[float('nan'),0]]
        r=extract_geometry(value,profile());self.assertEqual(r['invalid_way_ids'],[42]);self.assertFalse(r['provider_geometry_complete'])
        self.assertEqual(extract_geometry({'id':1,'type':'relation','tags':{'network':'ncn','ref':'177'}},profile())['missing_way_ids'],[42])

    def test_web_mercator_conversion_and_bounds(self):
        self.assertEqual(wgs84([0,0]),[0,0])
        self.assertAlmostEqual(wgs84([111319.49079327358,0])[0],1)
        for point in [[True,0],[0,float('inf')],[30000000,0]]:
            with self.assertRaises(ValueError):wgs84(point)

    def test_explicit_roundabout_arms_are_preserved_without_invented_chord(self):
        value=provider();one=value['route']['main'][0]['ways'][0]
        one.update(direction=1,tags={'junction':'roundabout'})
        one['geometry']['coordinates']=[[0,0],[50,30],[100,0]]
        two=deepcopy(one);two['direction']=-1;two['geometry']['coordinates']=[[0,0],[50,-30],[100,0]]
        value['route']['appendices']=[two]
        r=extract_geometry(value,profile())
        self.assertTrue(r['provider_geometry_complete'])
        self.assertEqual(r['directional_roundabout_way_ids'],[42])
        self.assertEqual(len(r['ways'][0]['coordinate_parts']),2)
        self.assertFalse(r['osm_node_identity_verified'])
        two['tags']['junction']='crossroads'
        self.assertEqual(extract_geometry(value,profile())['conflicting_way_ids'],[42])
