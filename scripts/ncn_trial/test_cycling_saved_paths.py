from copy import deepcopy
from dataclasses import replace
import unittest

from cycling_path_evidence import cycling_feature_evidence, cycling_evidence
from . import test_extent_reference
from .coverage import TrialLedger
from .saved_paths import assess_saved_paths
from .activation import ActivationTrial, JourneyRecord


class CyclingSavedPathTests(unittest.TestCase):
    def setUp(self):
        fixture=test_extent_reference.ExtentReferenceTests();fixture.setUp()
        self.reference,self.metadata,self.paths=fixture.ref,fixture.meta,fixture.paths
        self.value=deepcopy(fixture.value)
        result=self.value['journey']['processing_result']
        result.pop('matched_path_evidence')
        result['matcher']='osrm_bicycle_v1';result['matching_mode']='cycling'
        self.value['journey']['mode']='cycling'
        matching={'geometry':result['geojson']['features'][0]['geometry'],
                  'legs':[{'annotation':{'nodes':[1,3]},'steps':[{'geometry':result['geojson']['features'][0]['geometry']}]}]}
        result['cycling_path_evidence']=cycling_evidence([cycling_feature_evidence(matching,0)])

    def metres(self,value=None,paths=None):
        assessment,report=assess_saved_paths(value or self.value,self.reference,self.metadata,self.paths if paths is None else paths)
        ledger=TrialLedger(self.reference);ledger.put('one',assessment.evidence);ledger.put('two',assessment.evidence)
        return ledger.summary(),report

    def test_bicycle_node_identity_credits_only_selected_travel_once(self):
        original=deepcopy(self.value);totals,_=self.metres()
        self.assertAlmostEqual(55.598,totals['cycling_m'],places=2)
        self.assertEqual(0,totals['on_foot_m']);self.assertEqual(original,self.value)

    def test_changed_or_absent_node_evidence_cannot_use_geometry_fallback(self):
        for mutate in [lambda r:r.pop('cycling_path_evidence'),
                       lambda r:r['cycling_path_evidence']['features'][0]['legs'][0].update(osm_nodes=[9,10]),
                       lambda r:r['geojson']['features'][0]['geometry']['coordinates'][0].__setitem__(1,.00001)]:
            value=deepcopy(self.value);mutate(value['journey']['processing_result'])
            self.assertEqual(0,self.metres(value)[0]['cycling_m'])

    def test_source_registry_without_cycle_nodes_or_wrong_snapshot_does_not_qualify(self):
        for paths in [tuple(replace(p,osm_node_ids=()) for p in self.paths),
                      tuple(replace(p,reference_version='other') for p in self.paths)]:
            self.assertEqual(0,self.metres(paths=paths)[0]['cycling_m'])

    def test_same_node_chain_in_multiple_source_ways_is_held(self):
        paths=self.paths+(replace(self.paths[0],way_id='43'),)
        totals,report=self.metres(paths=paths)
        self.assertEqual(0,totals['cycling_m'])
        self.assertIn('unknown_or_ambiguous_node_chain',report['reasons'])

    def test_removed_edges_and_driving_relabelling_do_not_count(self):
        removed=deepcopy(self.value);removed['journey']['journey_corrections']={'removed_matched_segments':[0]}
        self.assertEqual(0,self.metres(removed)[0]['combined_m'])
        driving=deepcopy(self.value);driving['journey']['mode']='driving'
        self.assertEqual(0,self.metres(driving)[0]['combined_m'])

    def refresh(self,value,nodes):
        result=value['journey']['processing_result'];geometry=result['geojson']['features'][0]['geometry']
        matching={'geometry':geometry,'legs':[{'annotation':{'nodes':nodes},'steps':[{'geometry':geometry}]}]}
        result['cycling_path_evidence']=cycling_evidence([cycling_feature_evidence(matching,0)])

    def test_reverse_cycle_travel_matches_the_same_physical_interval(self):
        value=deepcopy(self.value)
        value['journey']['processing_result']['geojson']['features'][0]['geometry']['coordinates'].reverse()
        self.refresh(value,[3,1])
        self.assertAlmostEqual(55.598,self.metres(value)[0]['cycling_m'],places=2)

    def test_quantisation_check_is_bounded_after_identity_not_a_parallel_path_search(self):
        for offset,expected in [(.0000004,55.598),(.00001,0)]:
            value=deepcopy(self.value)
            for coordinate in value['journey']['processing_result']['geojson']['features'][0]['geometry']['coordinates']:
                coordinate[1]+=offset
            self.refresh(value,[1,3])
            self.assertAlmostEqual(expected,self.metres(value)[0]['cycling_m'],places=2)

    def test_walk_and_cycle_totals_remain_separate_with_one_combined_distance(self):
        fixture=test_extent_reference.ExtentReferenceTests();fixture.setUp()
        walking,_=assess_saved_paths(fixture.value,self.reference,self.metadata,self.paths)
        cycling,_=assess_saved_paths(self.value,self.reference,self.metadata,self.paths)
        ledger=TrialLedger(self.reference);ledger.put('walk',walking.evidence);ledger.put('cycle',cycling.evidence)
        result=ledger.summary()
        self.assertAlmostEqual(55.598,result['cycling_m'],places=2)
        self.assertAlmostEqual(55.598,result['on_foot_m'],places=2)
        self.assertAlmostEqual(55.598,result['combined_m'],places=2)

    def test_bulk_activation_uses_saved_cycle_nodes_without_a_review_queue(self):
        worker=ActivationTrial(self.reference)
        worker.activate([JourneyRecord('ride','v1','cycling','timeline_import',1)],100,complete_snapshot=True)
        worker.run_batch(lambda record:assess_saved_paths(self.value,self.reference,self.metadata,self.paths)[0])
        self.assertAlmostEqual(55.598,worker.summary()['cycling_m'],places=2)
        self.assertEqual([],worker.review_candidates())
