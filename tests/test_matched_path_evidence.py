import copy
import unittest
from matched_path_evidence import build_matched_path_evidence, public_way_reference, source_geometry_digest, geometry_digest


class MatchedPathEvidenceTests(unittest.TestCase):
    def feature(self, identity='42', coords=None, source='osm_region_a'):
        coords = coords or [[0,51],[.001,51],[.002,51]]
        return {'matched_reference':public_way_reference(source,'latest',identity,'path',{'foot':'yes'},coords)}

    def test_partial_travel_spans_reference_existing_geometry_not_whole_way(self):
        coords=[[.00025,51],[.0005,51],[.00075,51]]
        result=build_matched_path_evidence(coords,['a','a'],{'a':self.feature()})
        self.assertEqual([[0,2,0]],result['spans'])
        self.assertEqual(geometry_digest(coords),result['geometry_sha256'])
        self.assertNotIn('coordinates',str(result))
        self.assertEqual('42',result['references'][0]['source_feature_id'])

    def test_out_and_back_retains_edge_order_without_fabricated_gap(self):
        coords=[[0,51],[.001,51],[.002,51],[.001,51]]
        result=build_matched_path_evidence(coords,['a','b','a'],{'a':self.feature(),'b':self.feature('43')})
        self.assertEqual([[0,1,0],[1,2,1],[2,3,0]],result['spans'])

    def test_source_namespaces_are_distinct_even_for_same_way_id(self):
        features={'a':self.feature(source='osm_region_a'),'b':self.feature(source='osm_region_b')}
        result=build_matched_path_evidence([[0,51],[.001,51],[.002,51]],['a','b'],features)
        self.assertEqual(2,len(result['references']))
        self.assertNotEqual(result['references'][0]['content_sha256'],result['references'][1]['content_sha256'])

    def test_geometry_and_access_changes_invalidate_reference_content(self):
        first=self.feature()['matched_reference']
        changed=public_way_reference('osm_region_a','latest','42','path',{'foot':'no'},[[0,51],[.001,51],[.002,51]])
        self.assertNotEqual(first['content_sha256'],changed['content_sha256'])
        self.assertNotEqual(first['source_geometry_sha256'],self.feature(coords=[[0,51],[.001,51.001]])['matched_reference']['source_geometry_sha256'])

    def test_source_geometry_identity_is_orientation_and_format_independent(self):
        self.assertEqual(source_geometry_digest([[0,51],[.001,51]]),source_geometry_digest([[.001,51.0],[0.0,51.0]]))
        with self.assertRaises(ValueError):source_geometry_digest([[0,51],[float('nan'),51]])

    def test_unaligned_or_missing_source_identity_does_not_guess(self):
        self.assertEqual('unavailable',build_matched_path_evidence([[0,51],[.001,51]],[],{})['status'])
        self.assertEqual('unavailable',build_matched_path_evidence([[0,51],[.001,51]],['unknown'],{})['status'])

    def test_builder_does_not_mutate_input_reference(self):
        features={'a':self.feature()};original=copy.deepcopy(features)
        build_matched_path_evidence([[0,51],[.001,51]],['a'],features)
        self.assertEqual(original,features)

    def test_database_match_returns_source_identity_for_only_travelled_part(self):
        import json
        from unittest.mock import MagicMock, patch
        import database
        connection=MagicMock();connection.__enter__.return_value=connection
        cursor=connection.cursor.return_value.__enter__.return_value
        cursor.fetchall.return_value=[('osm_geofabrik_trial','latest','42','path',{'name':'Trial path','foot':'yes'},
                                      json.dumps({'type':'LineString','coordinates':[[0,51],[.001,51],[.002,51]]}))]
        with patch.object(database,'DATABASE_URL','trial'), patch.dict(database.database_state,status='ready'), \
                patch.object(database.psycopg,'connect',return_value=connection):
            result=database.match_pedestrian_reference([{'lng':.00025,'lat':51},{'lng':.00075,'lat':51}])
        self.assertIsNotNone(result)
        evidence=result['matched_path_evidence']
        self.assertEqual('available',evidence['status'])
        coords=result['geojson']['features'][0]['geometry']['coordinates']
        self.assertAlmostEqual(.00025,coords[0][0],places=7)
        self.assertAlmostEqual(.00075,coords[-1][0],places=7)
        self.assertEqual('42',evidence['references'][0]['source_feature_id'])
        self.assertEqual(geometry_digest(coords),evidence['geometry_sha256'])
        self.assertNotIn('matched_reference',result['road_geojson']['features'][0])
        self.assertEqual(0,result['road_geojson']['features'][0]['geometry']['coordinates'][0][0])

    def test_geometry_hash_survives_android_numeric_json_formatting(self):
        self.assertEqual(geometry_digest([[0.0,51.0],[.001,51.0]]),geometry_digest([[0,51],[.001,51]]))
        self.assertEqual(geometry_digest([[-0.0,51],[.001,51]]),geometry_digest([[0,51],[.001,51]]))
