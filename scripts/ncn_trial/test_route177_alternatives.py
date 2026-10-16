import unittest
from cycling_path_evidence import cycling_feature_evidence, cycling_evidence
from matched_path_evidence import public_way_reference,build_matched_path_evidence
from .route177_alternatives import load_trial
from .saved_paths import assess_saved_paths


class Route177AlternativesTests(unittest.TestCase):
    def setUp(self):self.ledger,self.data,self.paths,self.manifest=load_trial()

    def export(self,way,mode,span=None):
        path=next(p for p in self.paths if p.way_id==str(way))
        full=[list(c) for c in path.coordinates]
        coords=full[slice(*span)] if span else full
        nodes=list(path.osm_node_ids)[slice(*span)] if span else list(path.osm_node_ids)
        geometry={'type':'LineString','coordinates':coords}
        result={'geojson':{'features':[{'geometry':geometry}]},
            'road_geojson':{'features':[{'geometry':geometry}]}}
        if mode=='cycling':
            result.update(matcher='osrm_bicycle_v1',matching_mode='cycling')
            match={'geometry':geometry,'legs':[{'annotation':{'nodes':nodes},'steps':[{'geometry':geometry}]}]}
            result['cycling_path_evidence']=cycling_evidence([cycling_feature_evidence(match,0)])
        else:
            result['matcher']='preloaded_pedestrian_network_v4'
            ref=public_way_reference('osm_geofabrik_test','latest',path.way_id,'path',{'foot':'yes'},full)
            result['matched_path_evidence']=build_matched_path_evidence(coords,['way']*(len(coords)-1),{'way':{'matched_reference':ref}})
        return {'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
            'journey':{'mode':mode,'processing_status':'complete','processing_result':result}}

    def add(self,identity,way,mode):
        value=self.export(way,mode)
        assessment,_=assess_saved_paths(value,self.ledger.reference,self.data,self.paths)
        self.ledger.put(identity,assessment.evidence)
        return value

    def test_both_cycle_alignments_complete_same_bounded_section_once(self):
        for way in [44332298,94920359]:
            self.add(str(way),way,'cycling')
            section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if '7345' in k)
            self.assertAlmostEqual(section['completed_fraction'],1,places=6)
        s=self.ledger.summary()
        self.assertGreater(s['physical_distance']['cycling_m'],section['cycling_m'])

    def test_distinct_northern_footway_and_southern_cycleway_share_completion(self):
        self.add('foot',1460076188,'walking');self.add('cycle',94920359,'cycling')
        section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if '7345' in k)
        self.assertAlmostEqual(section['on_foot_m'],section['length_m'],places=6)
        self.assertAlmostEqual(section['cycling_m'],section['length_m'],places=6)
        self.assertAlmostEqual(section['combined_m'],section['length_m'],places=6)

    def test_second_section_qualifies_via_either_alignment(self):
        for way in [1302385780,62216492]:
            self.add('journey',way,'cycling')
            section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if '6680' in k)
            self.assertAlmostEqual(section['completed_fraction'],1,places=6)

    def test_footway_does_not_become_cycling_and_driving_cannot_qualify(self):
        self.add('foot-cycle',1460076188,'cycling')
        value=self.export(94920359,'cycling');value['journey']['mode']='driving'
        assessment,_=assess_saved_paths(value,self.ledger.reference,self.data,self.paths)
        self.ledger.put('drive',assessment.evidence)
        self.assertEqual(sum(s['combined_m'] for s in self.ledger.summary()['logical_sections'].values()),0)

    def test_correction_removes_logical_coverage(self):
        self.add('journey',94920359,'cycling');self.ledger.remove('journey')
        self.assertEqual(sum(s['combined_m'] for s in self.ledger.summary()['logical_sections'].values()),0)

    def test_partial_saved_travel_cannot_complete_entire_alternative(self):
        for mode in ['walking','cycling']:
            ledger,data,paths,_=load_trial()
            value=self.export(94920359,mode,span=(4,8))
            assessment,_=assess_saved_paths(value,ledger.reference,data,paths)
            ledger.put('partial',assessment.evidence)
            section=next(v for k,v in ledger.summary()['logical_sections'].items() if '7345' in k)
            self.assertGreater(section['combined_m'],0)
            self.assertLess(section['completed_fraction'],.1)
