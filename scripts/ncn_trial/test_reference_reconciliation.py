import unittest
from shapely.geometry import LineString
from .reference_reconciliation import ReferenceScreen, category


class ReferenceScreenTests(unittest.TestCase):
    def screen(self,sections,line=None):
        return ReferenceScreen([{'key':key,'category':kind,'line':LineString(coords)} for key,kind,coords in sections]).screen(line if line is not None else LineString([(0,0),(100,0)]))

    def test_overlap_is_not_double_counted_or_approved(self):
        r=self.screen([('a','open_ncn',[(0,0),(100,0)]),('b','open_ncn',[(0,0),(100,0)])])
        self.assertAlmostEqual(100,r['exclusive_corridor_metres_10m']['open_ncn'])
        self.assertFalse(r['association_approved'])
        self.assertEqual(['a','b'],r['candidate_section_keys_10m'])

    def test_parallel_path_remains_only_a_tolerance_sensitive_candidate(self):
        r=self.screen([('a','open_ncn',[(0,5),(100,5)])])
        self.assertEqual(0,r['open_ncn_corridor_metres_by_radius']['3'])
        self.assertGreater(r['open_ncn_corridor_metres_by_radius']['10'],0)
        self.assertFalse(r['association_approved'])

    def test_empty_region_has_explicit_unassociated_length(self):
        r=self.screen([])
        self.assertEqual(100,r['exclusive_corridor_metres_10m']['outside_reference_corridors'])
        self.assertEqual([],r['candidate_section_keys_10m'])

    def test_closed_regional_and_link_are_not_open_ncn(self):
        for kind in ('nonqualifying_ncn','regional','link','unclassified'):
            with self.subTest(kind=kind):
                r=self.screen([('a',kind,[(0,0),(100,0)])])
                self.assertEqual(0,r['open_ncn_corridor_metres_by_radius']['25'])
                self.assertAlmostEqual(100,r['exclusive_corridor_metres_10m'][kind])

    def test_exclusive_categories_conserve_total_length(self):
        r=self.screen([('a','open_ncn',[(0,0),(20,0)]),('b','regional',[(10,0),(60,0)])])
        self.assertAlmostEqual(r['mapped_length_m'],sum(r['exclusive_corridor_metres_10m'].values()))

    def test_source_classification_excludes_ferry_and_unknown_status(self):
        for value in ({'RouteType':'NCN','OpenStatus':'Open','Desc_':'Ferry'}, {'RouteType':'NCN'}):
            self.assertEqual('nonqualifying_ncn',category(value))
        self.assertEqual('regional',category({'RouteType':'RCN','RouteNo':1}))

    def test_catalogue_pipeline_returns_diagnostics_without_manufacturing_membership(self):
        from .reference_reconciliation import reconcile
        network={'features':[{'properties':{'FID':42,'RouteType':'NCN','OpenStatus':'Open'},
                              'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0]]}}]}
        ways={'features':[{'properties':{'osm_relation':1,'osm_way':2},
                           'geometry':{'type':'LineString','coordinates':[[0,.00001],[.001,.00001]]}}]}
        catalogue={'candidates':[{'relation_id':1,'name':'Parallel candidate','unique_way_count':1}]}
        r=reconcile(network,ways,catalogue)['candidates'][0]
        self.assertEqual(['FID:42:0'],r['ways'][0]['candidate_section_keys_10m'])
        self.assertFalse(r['completion_ready'])
        self.assertFalse(r['ways'][0]['association_approved'])

    def test_incomplete_or_duplicate_way_extract_is_rejected(self):
        from .reference_reconciliation import reconcile
        feature={'properties':{'osm_relation':1,'osm_way':2},
                 'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0]]}}
        catalogue={'candidates':[{'relation_id':1,'name':'Candidate','unique_way_count':2}]}
        for features in ([],[feature],[feature,feature]):
            with self.subTest(size=len(features)):
                with self.assertRaises(ValueError):reconcile({'features':[]},{'features':features},catalogue)
