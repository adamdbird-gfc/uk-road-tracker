from copy import deepcopy
import json
import unittest
from matched_path_evidence import public_way_reference,build_matched_path_evidence
from cycling_path_evidence import cycling_feature_evidence,cycling_evidence
from .catalogue_extract import _source_index
from .confirmed_paths import build_candidate,eligible_modes
from .association_bundle import read_bundle
from .coverage import TrialLedger
from .saved_paths import assess_saved_paths


def inputs():
    elements=[{'type':'node','id':i+1,'lat':0,'lon':i*.0005,'version':1} for i in range(3)]
    elements += [{'type':'way','id':42,'nodes':[1,2,3],'version':1,'tags':{'highway':'cycleway','foot':'yes'}},
        {'type':'relation','id':1,'version':1,'members':[{'type':'way','ref':42,'role':''}],
         'tags':{'type':'route','route':'bicycle','network':'ncn','ref':'137','cycle_network':'UK:National Cycle Network'}}]
    raw=json.dumps({'elements':elements}).encode();index,hashes=_source_index([raw])
    network={'features':[{'properties':{'FID':1,'RouteType':'NCN','RouteNo':137,'RouteCat':'Main Route','OpenStatus':'Open','Desc_':'TrafficFree'},
        'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0]]}}]}
    profiles={1:{'relation_id':1,'tags':{'network':'ncn','ref':'137'},'ways':[{'way_id':42}],'member_lists_complete':True}}
    provider={1:{'type':'relation','id':1,'tags':{'network':'ncn','ref':'137'},'route':{'ways':[
        {'route_type':'base','id':42,'geometry':{'type':'LineString','coordinates':[[0,0],[111.31949079327358,0]]}}]}}}
    return ['NCN:137',[1],profiles,index,hashes,network,'a'*64,provider]


class ConfirmedPathsTests(unittest.TestCase):
    def test_identity_nodes_modes_and_roundtrip_are_explicit(self):
        result,audit=build_candidate(*inputs());raw,payload,metrics=result
        self.assertEqual(audit['accepted_way_count'],1);self.assertFalse(audit['whole_route_completion_ready'])
        self.assertEqual(json.loads(raw)['paths'][0]['osm_node_ids'],[1,2,3])
        self.assertEqual(json.loads(raw)['paths'][0]['modes'],['on_foot','cycling'])
        self.assertEqual(metrics['paths'],1)

    def test_unlisted_way_does_not_enter_registry(self):
        values=inputs();values[3]['way',99]={'type':'way','id':99,'nodes':[1,2,3],'tags':{'highway':'cycleway'}}
        self.assertEqual([p['way_id'] for p in json.loads(build_candidate(*values)[0][0])['paths']],['42'])

    def test_changed_membership_or_namespace_requires_reassessment(self):
        for field,value in [('network','rcn'),('cycle_network','US:US')]:
            values=inputs();values[3]['relation',1]['tags'][field]=value
            with self.assertRaises(ValueError):build_candidate(*values)
        values=inputs();values[2][1]['ways']=[]
        with self.assertRaises(ValueError):build_candidate(*values)

    def test_source_edits_provider_drift_and_missing_nodes_are_held(self):
        for mutate,reason in [(lambda v:v[3]['node',2].update(lat=.00001),'current_geometry_differs_from_assessed_provider'),
                              (lambda v:v[3].pop(('node',2)),'missing_current_source_nodes')]:
            values=inputs();mutate(values);result,audit=build_candidate(*values)
            self.assertIsNone(result);self.assertEqual(audit['held'][0]['reason'],reason)

    def test_roundabouts_and_declared_alternatives_need_logical_policy(self):
        values=inputs();values[3]['way',42]['tags']['junction']='roundabout'
        self.assertEqual(build_candidate(*values)[1]['held'][0]['reason'],'logical_branch_policy_required')
        values=inputs();values[3]['relation',1]['members'][0]['role']='alternative'
        self.assertIsNone(build_candidate(*values)[0])

    def test_nonqualifying_official_extent_does_not_earn_credit(self):
        for field,value in [('OpenStatus','Closed'),('Desc_','Ferry'),('RouteCat','Alternative Route')]:
            values=inputs();values[5]['features'][0]['properties'][field]=value
            self.assertIsNone(build_candidate(*values)[0])

    def test_modes_respect_overrides_conditions_and_separate_footways(self):
        self.assertEqual(eligible_modes({'highway':'cycleway'}),('cycling',))
        self.assertEqual(eligible_modes({'highway':'footway'}),('on_foot',))
        self.assertEqual(eligible_modes({'highway':'residential','access':'private','bicycle':'permissive'}),('cycling',))
        self.assertEqual(eligible_modes({'highway':'cycleway','bicycle:conditional':'no @ (winter)'}),())
        self.assertEqual(eligible_modes({'highway':'motorway','bicycle':'yes'}),())
        self.assertEqual(eligible_modes({'highway':'path','bicycle':'dismount'}),('on_foot',))

    def test_saved_matcher_uses_identity_and_credits_only_partial_travel(self):
        from .coverage import TrialReference,Section
        from .journey_replay import projector
        from shapely.geometry import LineString
        raw,payload,metrics=build_candidate(*inputs())[0];meta=payload['metadata'];project=projector(meta)
        ref=TrialReference(payload['reference_version'],[Section(s['key'],LineString([project(c) for c in s['coordinates']]),tuple(s['routes'])) for s in payload['sections']])
        paths=read_bundle(raw,ref,meta['projection_origin'],metrics['content_sha256'])
        coords=[[.0002,0],[.0004,0]];geometry={'type':'LineString','coordinates':coords}
        for mode in ['walking','cycling']:
            result={'geojson':{'features':[{'geometry':geometry}]}}
            if mode=='walking':
                source=public_way_reference('osm_geofabrik_test','latest','42','path',{'foot':'yes'},[[0,0],[.0005,0],[.001,0]])
                result.update(matcher='preloaded_pedestrian_network_v4',matched_path_evidence=build_matched_path_evidence(coords,['w'],{'w':{'matched_reference':source}}))
            else:
                matching={'geometry':geometry,'legs':[{'annotation':{'nodes':[1,3]},'steps':[{'geometry':geometry}]}]}
                result.update(matcher='osrm_bicycle_v1',matching_mode='cycling',cycling_path_evidence=cycling_evidence([cycling_feature_evidence(matching,0)]))
            value={'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
                'journey':{'mode':mode,'processing_status':'complete','processing_result':result}}
            assessment,_=assess_saved_paths(value,ref,meta,paths);ledger=TrialLedger(ref);ledger.put('one',assessment.evidence)
            self.assertAlmostEqual(ledger.summary()['combined_m'],22.239,places=2)

    def test_pinned_route476_builds_nine_current_identity_associations(self):
        from pathlib import Path
        data=json.loads((Path(__file__).resolve().parents[2]/'tests/fixtures/ncn-confirmed476/public-snapshot.json').read_bytes())
        raw=json.dumps(data['source']).encode();index,hashes=_source_index([raw])
        result,audit=build_candidate('NCN:476',[1597417],{1597417:data['profile']},index,hashes,data['network'],'b'*64,{1597417:data['provider']})
        self.assertEqual(audit['accepted_way_count'],9);self.assertEqual(audit['held_way_count'],0)
        self.assertFalse(audit['whole_route_completion_ready'])
        for p in json.loads(result[0])['paths']:
            self.assertEqual(len(p['osm_node_ids']),len(p['coordinates']))
            self.assertEqual(p['target_geometry'],'exact_source_spans')

    def test_partial_way_keeps_source_identity_and_clips_to_vertex_span(self):
        values=inputs();values[5]['features'][0]['geometry']['coordinates'][-1][0]=.00045
        result,audit=build_candidate(*values)
        self.assertIsNotNone(result);self.assertEqual(audit['partial_way_count'],1)
        self.assertEqual(result[1]['sections'][0]['from_vertex'],0)
        self.assertEqual(result[1]['sections'][0]['to_vertex'],1)
        self.assertEqual(len(json.loads(result[0])['paths'][0]['osm_node_ids']),3)
        self.assertTrue(audit['held'][0]['partly_accepted'])

    def test_unsupported_middle_edges_do_not_bridge_supported_runs(self):
        values=inputs()
        for i in range(5):values[3]['node',i+1]={'type':'node','id':i+1,'lon':i*.0005,'lat':0}
        values[3]['way',42]['nodes']=[1,2,3,4,5]
        one=values[5]['features'][0];one['geometry']['coordinates']=[[0,0],[.0005,0]]
        two=deepcopy(one);two['properties']['FID']=2;two['geometry']['coordinates']=[[.0015,0],[.002,0]]
        values[5]['features'].append(two)
        values[7][1]['route']['ways'][0]['geometry']['coordinates'][-1][0]=222.63898158654715
        result,audit=build_candidate(*values)
        self.assertEqual([(s['from_vertex'],s['to_vertex']) for s in result[1]['sections']],[(0,1),(3,4)])
