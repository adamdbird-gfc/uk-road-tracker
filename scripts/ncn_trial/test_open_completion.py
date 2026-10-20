import gzip
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from shapely.geometry import LineString

from .coverage import Evidence, Section, TrialReference
from .inventory_completion import InventoryLedger
from .open_completion import bind_edges, build_completion, edge_interval, read_completion, sha
from .saved_paths import KnownPath


def fixture(source, targets, modes=('on_foot','cycling')):
    reference=TrialReference('source',[Section('physical',LineString(source),('NCN:1:eligible-source-spans',))])
    paths=[KnownPath('1',tuple(source),('physical',),'source',modes,'exact_source_spans',tuple(range(1,len(source)+1)))]
    logical,bindings,audit=bind_edges(reference,paths,[(str(i),LineString(c))for i,c in enumerate(targets)],'NCN:1:captured-open-main')
    return InventoryLedger(reference,logical,bindings),audit


class OpenCompletionTests(unittest.TestCase):
    def test_offset_maps_logical_target_but_keeps_actual_physical_distance(self):
        ledger,_=fixture([(0,3),(50,3)], [[(0,0),(100,0)]])
        ledger.put('walk',Evidence('source','on_foot',(('physical',0,50),),()))
        summary=ledger.summary();route=summary['logical_routes']['NCN:1:captured-open-main']
        self.assertEqual(route['length_m'],100)
        self.assertAlmostEqual(route['completed_fraction'],.5)
        self.assertAlmostEqual(summary['physical_distance']['combined_m'],50)
        self.assertEqual(list(ledger.reference.sections['physical'].line.coords),[(0.,3.),(50.,3.)])

    def test_unsupported_middle_stays_unbound_and_cannot_be_bridged(self):
        ledger,_=fixture([(0,0),(30,0),(30,30),(70,30),(70,0),(100,0)], [[(0,0),(100,0)]])
        # Supply valid full physical interval, retaining the noncorresponding detour.
        ledger.put('path',Evidence('source','cycling',(('physical',0,ledger.reference.sections['physical'].line.length),),()))
        result=ledger.summary()['logical_routes']['NCN:1:captured-open-main']
        self.assertAlmostEqual(result['combined_m'],60)
        self.assertAlmostEqual(result['length_m'],100)

    def test_near_parallel_targets_are_ambiguous_not_nearest_wins(self):
        ledger,audit=fixture([(0,1),(100,1)], [[(0,0),(100,0)],[(0,4),(100,4)]])
        self.assertEqual(len(ledger.bindings),0)
        self.assertAlmostEqual(audit['ambiguous_source_occurrence_m'],100)
        self.assertEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['completed_fraction'],0)

    def test_crossing_and_outside_offset_do_not_bind(self):
        for source in [[(50,-20),(50,20)],[(0,10.01),(100,10.01)]]:
            ledger,_=fixture(source,[[(0,0),(100,0)]])
            self.assertFalse(ledger.bindings)

    def test_reversed_geometry_and_adjacent_target_edges(self):
        ledger,_=fixture([(100,2),(0,2)], [[(0,0),(40,0)],[(40,0),(100,0)]])
        ledger.put('cycle',Evidence('source','cycling',(('physical',0,100),),()))
        self.assertAlmostEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['combined_m'],100)
        self.assertTrue(all(b.logical_start>b.logical_end for b in ledger.bindings))

    def test_target_endpoint_is_not_extrapolated(self):
        ledger,_=fixture([(-20,0),(120,0)],[[(0,0),(100,0)]])
        self.assertEqual([(b.physical_start,b.physical_end)for b in ledger.bindings],[(20.,120.)])
        ledger.put('before',Evidence('source','cycling',(('physical',0,20),),()))
        self.assertEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['combined_m'],0)

    def test_modes_repeats_correction_and_deletion(self):
        ledger,_=fixture([(0,0),(100,0)],[[(0,0),(100,0)]],('cycling',))
        for key in ('a','b'):
            ledger.put(key,Evidence('source','cycling',(('physical',0,100),),()))
        ledger.remove('a')
        self.assertAlmostEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['combined_m'],100)
        ledger.put('b',Evidence('source','on_foot',(('physical',0,100),),()))
        self.assertEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['combined_m'],0)
        ledger.put('b',Evidence('source','driving',(('physical',0,100),),()))
        self.assertEqual(ledger.summary()['physical_distance']['combined_m'],0)

    def test_repeat_source_occurrences_do_not_double_logical_progress(self):
        ledger,_=fixture([(0,0),(100,0),(0,0)],[[(0,0),(100,0)]])
        ledger.put('return',Evidence('source','cycling',(('physical',100,200),),()))
        self.assertAlmostEqual(ledger.summary()['logical_routes']['NCN:1:captured-open-main']['combined_m'],100)
        self.assertAlmostEqual(ledger.summary()['physical_distance']['combined_m'],100)

    def test_unadmitted_or_unowned_sections_are_rejected(self):
        reference=TrialReference('v',[Section('x',LineString([(0,0),(10,0)]),('route',))])
        with self.assertRaisesRegex(ValueError,'admitted association'):
            bind_edges(reference,[],[('a',LineString([(0,0),(10,0)]))],'route')

    def test_small_edges_at_national_coordinates_keep_strict_span_bounds(self):
        import random
        randomizer=random.Random(112)
        for _ in range(100):
            x,y=randomizer.uniform(-500000,500000),randomizer.uniform(-500000,500000)
            dx,dy=randomizer.uniform(.0001,50),randomizer.uniform(.0001,50)
            ledger,_=fixture([(x+dx,y+dy),(x,y)], [[(x,y),(x+dx,y+dy)]])
            self.assertTrue(ledger.bindings)
            for b in ledger.bindings:
                self.assertGreaterEqual(min(b.logical_start,b.logical_end),0)
                self.assertLessEqual(max(b.logical_start,b.logical_end),ledger.logical[b.logical_key].length_m)

    def test_real_admission_fixture_roundtrip_retains_unmapped_target_and_pins(self):
        from .test_source_identity_review import setup,build
        from .remaining_routes_review import emit
        from copy import deepcopy
        from matched_path_evidence import digest
        values,review=setup();review['approved_source_spans'][0]['to_vertex']=1
        duplicate=deepcopy(values[5]['features'][0]);duplicate['properties']['FID']=2
        values[5]['features'].append(duplicate)
        review.update(official_features_sha256=digest(values[5]['features']),
            approved_open_features_sha256=digest(values[5]['features']),approved_official_feature_ids=[1,2])
        candidate,audit=build(values,review)
        with TemporaryDirectory()as temporary:
            root=Path(temporary)
            result=emit(root,candidate,audit,review,json.dumps(review).encode(),values[5]['features'])
            ledger,bundle,plan=build_completion(root,result['manifest_sha256'])
            raw=json.dumps(plan).encode()
            restored,paths,_=read_completion(raw,bundle,sha(raw),sha(bundle))
            self.assertEqual(restored.summary(),ledger.summary())
            self.assertEqual(len(paths),1)
            self.assertGreater(plan['unbound_open_length_m'],50)
            self.assertGreater(plan['canonical_open_length_m'],110)
            self.assertLess(plan['canonical_open_length_m'],112)  # One line, despite two official records.
            self.assertFalse(plan['android_activation_ready'])
            self.assertNotEqual(paths[0].reference_version,candidate[1]['reference_version'])
            for bad_raw,bad_bundle in [(raw+b' ',bundle),(raw,bundle+b' ')]:
                with self.assertRaisesRegex(ValueError,'checksum'):
                    read_completion(bad_raw,bad_bundle,sha(raw),sha(bundle))
            with self.assertRaises(ValueError):build_completion(root,'0'*64)

            # A real saved-path contract must still pass admission after the
            # logical reference is versioned; no fabricated GPS or new match.
            from matched_path_evidence import public_way_reference,build_matched_path_evidence
            from .open_completion import activation_trial
            from .activation import ActivationTrial,JourneyRecord
            from .saved_paths import assess_saved_paths
            coords=[[.00005,0],[.00025,0],[.00045,0]]
            source=public_way_reference('osm_geofabrik_test','latest','42','path',{'foot':'yes'},[[0,0],[.0005,0],[.001,0]])
            export={'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
                'journey':{'mode':'walking','processing_status':'complete','processing_result':{
                    'matcher':'preloaded_pedestrian_network_v4',
                    'geojson':{'features':[{'geometry':{'type':'LineString','coordinates':coords}}]},
                    'matched_path_evidence':build_matched_path_evidence(coords,['w','w'],{'w':{'matched_reference':source}})}}}
            worker,meta,known,_=activation_trial(raw,bundle,sha(raw),sha(bundle))
            worker.activate([JourneyRecord('walk','1','walking','timeline_import',1)],100,complete_snapshot=True)
            assessment,diagnostics=assess_saved_paths(export,worker.reference,meta,known)
            worker.run_batch(lambda _:assessment)
            value=worker.summary()['logical_routes'][plan['scope']]
            self.assertGreater(value['combined_m'],40,diagnostics)
            self.assertLess(value['completed_fraction'],.5)
            self.assertEqual(worker.review_candidates(),[])
            restored=ActivationTrial.restore(worker.reference,worker.export(),ledger_factory=worker.ledger_factory,
                accounting_key=worker.accounting_key)
            self.assertEqual(restored.summary(),worker.summary())
            restored.deactivate();restored.activate([],200,complete_snapshot=True)
            self.assertEqual(restored.summary()['logical_routes'][plan['scope']]['combined_m'],0)
            with self.assertRaisesRegex(ValueError,'accounting migration'):
                ActivationTrial.restore(worker.reference,worker.export())


if __name__=='__main__':
    unittest.main()
