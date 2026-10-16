from copy import deepcopy
from dataclasses import replace
import unittest
from shapely.geometry import LineString
from matched_path_evidence import public_way_reference,build_matched_path_evidence
from .coverage import TrialReference,Section,TrialLedger
from .saved_paths import KnownPath,assess_saved_paths
from .activation import ActivationTrial,JourneyRecord


class SavedPathTests(unittest.TestCase):
    def setUp(self):
        self.ref=TrialReference('snapshot', [Section('a',LineString([(0,0),(223,0)]),('NCN:1',))])
        self.meta={'projection_origin':[0,0]}
        self.source=[[0,0],[.001,0],[.002,0]]
        self.path=KnownPath('42',tuple(tuple(p) for p in self.source),('a',),self.ref.version)
        self.value={'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
                    'journey':{'mode':'walking','processing_status':'complete','processing_result':{
                        'matcher':'preloaded_pedestrian_network_v4',
                        'geojson':{'type':'FeatureCollection','features':[{'geometry':{'type':'LineString','coordinates':[[.0002,0],[.0004,0]]}}]},
                        'road_geojson':{'features':[{'geometry':{'type':'LineString','coordinates':self.source}}]}}}}

    def assess(self,value=None,paths=None):
        return assess_saved_paths(value or self.value,self.ref,self.meta,[self.path] if paths is None else paths)

    def metres(self,value=None,paths=None):
        result,_=self.assess(value,paths);ledger=TrialLedger(self.ref);ledger.put('walk',result.evidence)
        return ledger.summary()['combined_m']

    def add_identity(self,tags=None,source_key='osm_geofabrik_test'):
        r=self.value['journey']['processing_result'];coords=r['geojson']['features'][0]['geometry']['coordinates']
        item=public_way_reference(source_key,'latest','42','path',tags or {'foot':'yes'},self.source)
        r['matched_path_evidence']=build_matched_path_evidence(coords,['key']*(len(coords)-1),{'key':{'matched_reference':item}})

    def test_legacy_exact_reference_geometry_supports_only_partial_travel_without_gps(self):
        self.assertAlmostEqual(22.239,self.metres(),places=2)
        self.assertLess(self.metres(),30)
        self.assertEqual('exact_legacy_source_geometry',self.assess()[1]['association_method'])

    def test_new_identity_and_legacy_path_association_agree(self):
        old=self.metres();self.add_identity()
        self.assertEqual('stored_source_identity',self.assess()[1]['association_method'])
        self.assertAlmostEqual(old,self.metres())

    def test_parallel_path_is_not_credited_even_when_full_way_is_in_saved_result(self):
        self.value['journey']['processing_result']['geojson']['features'][0]['geometry']['coordinates']=[[.0002,.00001],[.0004,.00001]]
        self.assertEqual(0,self.metres())

    def test_crossing_the_way_does_not_credit_a_section(self):
        self.value['journey']['processing_result']['geojson']['features'][0]['geometry']['coordinates']=[[.0002,-.0001],[.0002,.0001]]
        self.assertEqual(0,self.metres())

    def test_no_full_way_identity_means_no_legacy_proximity_fallback(self):
        self.value['journey']['processing_result']['road_geojson']['features'][0]['geometry']['coordinates']=[[0,.00001],[.001,.00001],[.002,.00001]]
        self.assertEqual(0,self.metres())

    def test_edits_to_geometry_invalidate_identity_without_falling_back(self):
        self.add_identity();self.value['journey']['processing_result']['geojson']['features'][0]['geometry']['coordinates'][1][0]=.0005
        self.assertEqual(0,self.metres())
        self.assertIn('invalid_or_changed_path_evidence',self.assess()[1]['reasons'])

    def test_removed_travel_edge_is_not_reintroduced(self):
        self.add_identity();self.value['journey']['journey_corrections']={'removed_matched_segments':[0]}
        self.assertEqual(0,self.metres())

    def test_overlapping_or_out_of_range_spans_are_refused(self):
        self.add_identity();self.value['journey']['processing_result']['matched_path_evidence']['spans']=[[0,1,0],[0,1,0]]
        self.assertEqual(0,self.metres())

    def test_reference_snapshot_change_requires_reassociation(self):
        self.assertEqual(0,self.metres(paths=[replace(self.path,reference_version='new')]))

    def test_restricted_or_unknown_reference_sources_do_not_qualify(self):
        self.add_identity(tags={'foot':'no'});self.assertEqual(0,self.metres())
        self.add_identity(source_key='unverified_provider');self.assertEqual(0,self.metres())

    def test_driving_or_unsupported_matcher_does_not_qualify(self):
        self.value['journey']['mode']='driving';self.assertEqual(0,self.metres())
        self.value['journey']['mode']='walking';self.value['journey']['processing_result']['matcher']='other_router'
        self.assertEqual(0,self.metres())

    def test_activation_worker_consumes_saved_path_assessment_without_import_review_queue(self):
        worker=ActivationTrial(self.ref)
        worker.activate([JourneyRecord('walk','v1','walking','timeline_import',1)],100,complete_snapshot=True)
        worker.run_batch(lambda record:self.assess()[0])
        self.assertAlmostEqual(22.239,worker.summary()['on_foot_m'],places=2)
        self.assertEqual([],worker.review_candidates())
        worker.deactivate();worker.activate([JourneyRecord('walk','v1','walking','timeline_import',1)],200,complete_snapshot=True)
        self.assertEqual(0,worker.run_batch(lambda record:self.fail('Should not repeat unchanged match')))

    def test_input_is_preserved_and_duplicate_paths_do_not_inflate_coverage(self):
        original=deepcopy(self.value)
        result,_=self.assess();ledger=TrialLedger(self.ref);ledger.put('one',result.evidence);ledger.put('two',result.evidence)
        self.assertAlmostEqual(self.metres(),ledger.summary()['combined_m'])
        self.assertEqual(original,self.value)

    def test_unresolved_road_only_edits_are_held_without_resurrecting_travel(self):
        self.value['journey']['journey_corrections']={'removed_road_ids':['name:trial path']}
        self.assertEqual(0,self.metres())
        self.assertIn('unresolved_road_identity_correction',self.assess()[1]['reasons'])

    def test_reference_metadata_tampering_is_detected(self):
        self.add_identity()
        self.value['journey']['processing_result']['matched_path_evidence']['references'][0]['source_feature_id']='43'
        self.assertEqual(0,self.metres())
        self.assertIn('invalid_or_changed_path_evidence',self.assess()[1]['reasons'])

    def test_relabelling_a_pedestrian_match_as_cycling_does_not_supply_cycle_provenance(self):
        self.value['journey']['mode']='cycling'
        self.assertEqual(0,self.metres())
        self.assertIn('unsupported_mode_matcher_pair',self.assess()[1]['reasons'])
