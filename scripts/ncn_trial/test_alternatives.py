from dataclasses import replace
import unittest
from shapely.geometry import LineString
from .coverage import Evidence, Section, TrialReference
from .alternatives import LogicalSection, AlternativeSpan, AlternativeLedger


class AlternativesTests(unittest.TestCase):
    def setUp(self):
        self.ref=TrialReference('pinned',[Section('north',LineString([(0,0),(100,0)]),('NCN:177',)),
            Section('south',LineString([(0,20),(120,20)]),('NCN:177',)),
            Section('road',LineString([(0,10),(100,10)]),('NCN:177',))])
        self.sections=[LogicalSection('shared',100,('NCN:177',)),LogicalSection('remainder',900,('NCN:177',))]
        self.bindings=[AlternativeSpan('north',0,100,'shared',0,100),
            AlternativeSpan('south',0,120,'shared',100,0),
            AlternativeSpan('road',0,100,'remainder',0,100,('on_foot',))]
        self.ledger=AlternativeLedger(self.ref,self.sections,self.bindings)

    def put(self,name,mode,key,a,z):
        self.ledger.put(name,Evidence('pinned',mode,((key,a,z),),('verified_source_identity',)))

    def test_either_side_completes_same_section_not_whole_route(self):
        for key,length in [('north',100),('south',120)]:
            self.put('journey','cycling',key,0,length)
            s=self.ledger.summary()
            self.assertEqual(s['logical_sections']['shared']['completed_fraction'],1)
            self.assertEqual(s['logical_routes']['NCN:177']['completed_fraction'],.1)

    def test_both_sides_preserve_physical_distances_without_double_completion(self):
        self.put('north','cycling','north',0,100);self.put('south','on_foot','south',0,120)
        s=self.ledger.summary();logical=s['logical_sections']['shared']
        self.assertEqual(logical['combined_m'],100)
        self.assertEqual(logical['cycling_m'],100);self.assertEqual(logical['on_foot_m'],100)
        self.assertEqual(s['physical_distance']['cycling_m'],100)
        self.assertEqual(s['physical_distance']['on_foot_m'],120)
        self.assertEqual(s['physical_distance']['combined_m'],220)

    def test_reversed_partial_branches_union_correctly(self):
        self.put('a','on_foot','north',0,50)
        self.put('b','cycling','south',0,60)
        self.assertEqual(self.ledger.summary()['logical_sections']['shared']['combined_m'],100)
        self.put('b','cycling','south',60,120)
        self.assertEqual(self.ledger.summary()['logical_sections']['shared']['combined_m'],50)

    def test_reimport_removal_and_mode_correction_rebuild(self):
        self.put('a','on_foot','north',0,50);self.put('a','on_foot','north',0,50)
        self.assertEqual(self.ledger.summary()['logical_sections']['shared']['combined_m'],50)
        self.put('a','driving','north',0,100)
        self.assertEqual(self.ledger.summary()['logical_sections']['shared']['combined_m'],0)
        self.put('a','cycling','south',0,120);self.ledger.remove('a')
        self.assertEqual(self.ledger.summary()['logical_sections']['shared']['combined_m'],0)

    def test_explicit_mode_binding_excludes_cycle_on_foot_only_span(self):
        self.put('a','cycling','road',0,100)
        self.assertEqual(self.ledger.summary()['logical_sections']['remainder']['combined_m'],0)
        self.put('a','on_foot','road',0,100)
        self.assertEqual(self.ledger.summary()['logical_sections']['remainder']['combined_m'],100)

    def test_unbound_physical_path_cannot_gain_logical_credit(self):
        ledger=AlternativeLedger(self.ref,self.sections[:1],self.bindings[:2])
        ledger.put('road',Evidence('pinned','cycling',(('road',0,100),),()))
        self.assertEqual(ledger.summary()['logical_sections']['shared']['combined_m'],0)

    def test_ambiguous_bindings_bad_ranges_and_version_refused(self):
        for b in [replace(self.bindings[0],physical_end=101),replace(self.bindings[0],logical_end=101),
                  replace(self.bindings[0],modes=('driving',)), self.bindings[0]]:
            with self.assertRaises(ValueError):AlternativeLedger(self.ref,self.sections,self.bindings+[b])
        with self.assertRaises(ValueError):self.ledger.put('old',Evidence('old','cycling',(),()))
