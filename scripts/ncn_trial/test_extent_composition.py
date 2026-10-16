import json
import unittest
from .extent_composition import compose_extent


def source(relation,way,nodes,offset=0):
    elements=[{'type':'node','id':n,'lon':n*.001+offset,'lat':0} for n in nodes]
    elements += [{'type':'way','id':way,'nodes':nodes},
                 {'type':'relation','id':relation,'version':1,'tags':{'type':'route','route':'bicycle'},
                  'members':[{'type':'way','ref':way,'role':''}]}]
    return json.dumps({'elements':elements}).encode()
def selection(rel,way,start,end):return dict(relation_id=rel,way_id=way,from_vertex=start,to_vertex=end)


class ExtentCompositionTests(unittest.TestCase):
    def test_only_selected_partial_way_edges_are_included(self):
        result=compose_extent({1:source(1,10,[1,2,3,4])},[selection(1,10,1,2)])
        self.assertEqual([[2,3]],[e['nodes'] for e in result['physical_edges']])
        self.assertFalse(result['completion_ready']);self.assertFalse(result['routing_instruction'])

    def test_shared_edges_and_reversed_spans_are_deduplicated(self):
        result=compose_extent({1:source(1,10,[1,2,3]),2:source(2,20,[3,2,1])},
                              [selection(1,10,0,2),selection(2,20,2,0)])
        self.assertEqual(2,result['unique_edge_count'])
        self.assertEqual(1,result['connected_components'])

    def test_close_disconnected_nodes_do_not_create_a_connector(self):
        result=compose_extent({1:source(1,10,[1,2]),2:source(2,20,[3,4],-.00099999)},
                              [selection(1,10,0,1),selection(2,20,0,1)])
        self.assertEqual(2,result['connected_components'])
        self.assertEqual(2,result['unique_edge_count'])

    def test_unrelated_way_cannot_be_selected_under_another_route(self):
        with self.assertRaises(ValueError):compose_extent({1:source(1,10,[1,2])},[selection(1,20,0,1)])

    def test_changed_common_node_requires_reference_refresh(self):
        with self.assertRaises(ValueError):compose_extent({1:source(1,10,[1,2]),2:source(2,20,[2,3],.001)},
                                                          [selection(1,10,0,1),selection(2,20,0,1)])

    def test_invalid_span_and_missing_source_geometry_are_refused(self):
        for start,end in [(0,3),(0,0),(True,1)]:
            with self.subTest(start=start,end=end):
                with self.assertRaises(ValueError):compose_extent({1:source(1,10,[1,2])},[selection(1,10,start,end)])
        value=json.loads(source(1,10,[1,2]));value['elements'].pop(0)
        with self.assertRaises(ValueError):compose_extent({1:json.dumps(value).encode()},[selection(1,10,0,1)])
