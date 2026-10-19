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

    def travel(self,label,mode):
        itinerary=next(i for i in self.manifest['itineraries'] if i['key']==label)
        for way in itinerary['way_ids']:self.add(label+':'+way,int(way),mode)

    def test_both_cycle_alignments_complete_same_section_once(self):
        for label in ['west-north-cycle','west-south']:
            self.travel(label,'cycling')
            section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if '7345' in k)
            self.assertAlmostEqual(section['completed_fraction'],1,places=6)
        s=self.ledger.summary()
        self.assertGreater(s['physical_distance']['cycling_m'],section['cycling_m'])

    def test_distinct_northern_footway_and_southern_cycleway_share_completion(self):
        self.travel('west-north-foot','walking');self.travel('west-south','cycling')
        section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if '7345' in k)
        self.assertAlmostEqual(section['on_foot_m'],section['length_m'],places=6)
        self.assertAlmostEqual(section['cycling_m'],section['length_m'],places=6)
        self.assertAlmostEqual(section['combined_m'],section['length_m'],places=6)

    def test_second_shared_section_qualifies_via_either_alignment(self):
        for label in ['east-north-shared','east-south-shared']:
            self.travel(label,'cycling')
            section=next(v for k,v in self.ledger.summary()['logical_sections'].items() if 'shared-reviewed' in k)
            self.assertAlmostEqual(section['completed_fraction'],1,places=6)

    def test_shorter_southern_path_does_not_complete_northern_extension(self):
        self.travel('west-south','cycling');self.travel('east-south-shared','cycling')
        summary=self.ledger.summary()
        tail=next(v for k,v in summary['logical_sections'].items() if 'north-extension' in k)
        self.assertEqual(tail['combined_m'],0)
        self.assertLess(summary['logical_routes']['NCN:177:reviewed-pilot']['completed_fraction'],.95)
        self.travel('east-north-extension','cycling')
        self.assertAlmostEqual(self.ledger.summary()['logical_routes']['NCN:177:reviewed-pilot']['completed_fraction'],1,places=6)

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

    def test_deleted_multiway_journeys_rebuild(self):
        self.travel('west-south','cycling')
        for identity in list(self.ledger.activities):self.ledger.remove(identity)
        self.assertEqual(sum(s['combined_m'] for s in self.ledger.summary()['logical_sections'].values()),0)
