import copy
import unittest

from cycling_path_evidence import cycling_feature_evidence, cycling_evidence


def matching():
    return {'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0],[.002,0]]},
            'legs':[{'annotation':{'nodes':[101,102.0,103]},
                     'steps':[{'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0],[.002,0]]}}]}]}


class CyclingPathEvidenceTests(unittest.TestCase):
    def test_preserves_node_ids_without_assuming_vertex_count(self):
        value=matching();value['legs'][0]['annotation']['nodes']=[101,103.0]
        original=copy.deepcopy(value);result=cycling_feature_evidence(value,2)
        self.assertEqual('available',result['status'])
        self.assertEqual([101,103],result['legs'][0]['osm_nodes'])
        self.assertEqual((0,2),(result['legs'][0]['from_vertex'],result['legs'][0]['to_vertex']))
        self.assertIsNone(result['data_version']);self.assertEqual(original,value)

    def test_unaligned_leg_is_held_without_nearest_vertex_fallback(self):
        value=matching();value['legs'][0]['steps'][0]['geometry']['coordinates'][1][1]=.000001
        result=cycling_feature_evidence(value,0)
        self.assertEqual('unavailable',result['status']);self.assertEqual([],result['legs'])

    def test_repeated_overview_occurrences_are_ambiguous(self):
        value=matching();value['geometry']['coordinates']*=2
        self.assertEqual('unavailable',cycling_feature_evidence(value,0)['status'])

    def test_missing_annotations_and_invalid_ids_fail_softly(self):
        for nodes in [[],[True,2],[1.2,2],[0,2],[float('inf'),2]]:
            value=matching();value['legs'][0]['annotation']['nodes']=nodes
            self.assertEqual('unavailable',cycling_feature_evidence(value,0)['status'])
        value=matching();del value['legs'][0]['annotation']
        self.assertEqual('unavailable',cycling_feature_evidence(value,0)['status'])

    def test_partial_response_keeps_good_leg_and_marks_gaps(self):
        value=matching();value['legs'].append({'steps':[]})
        result=cycling_feature_evidence(value,0,'2026-10-16')
        self.assertEqual('partial',cycling_evidence([result])['status'])
        self.assertEqual(1,len(result['legs']))
