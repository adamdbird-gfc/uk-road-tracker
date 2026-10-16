import json
import hashlib
from pathlib import Path
import tempfile
import unittest
from .extent_composition import compose_extent, compose_manifest


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

    def test_manifest_replays_pinned_source_and_refuses_changed_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = source(1,10,[1,2,3])
            (root/'source.json').write_bytes(raw)
            manifest = {'schema_version':1,
                        'sources':[{'relation_id':1,'file':'source.json',
                                    'sha256':hashlib.sha256(raw).hexdigest()}],
                        'selected_spans':[selection(1,10,0,1)]}
            path = root/'manifest.json'
            path.write_text(json.dumps(manifest))
            self.assertEqual([[1,2]],[e['nodes'] for e in compose_manifest(path)['physical_edges']])
            (root/'source.json').write_bytes(raw+b' ')
            with self.assertRaisesRegex(ValueError,'checksum mismatch'):
                compose_manifest(path)

    def test_manifest_rejects_duplicate_relation_binding(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = source(1,10,[1,2])
            (root/'source.json').write_bytes(raw)
            binding = {'relation_id':1,'file':'source.json','sha256':hashlib.sha256(raw).hexdigest()}
            path = root/'manifest.json'
            path.write_text(json.dumps({'schema_version':1,'sources':[binding,binding],
                                        'selected_spans':[selection(1,10,0,1)]}))
            with self.assertRaisesRegex(ValueError,'Distinct integer'):
                compose_manifest(path)

    def test_nested_area_member_does_not_add_edges_to_selected_lines(self):
        value = json.loads(source(1,10,[1,2]))
        value['elements'] += [
            {'type':'way','id':20,'nodes':[1,3,1]},
            {'type':'node','id':3,'lon':.003,'lat':0},
            {'type':'relation','id':2,'tags':{'type':'multipolygon'},
             'members':[{'type':'way','ref':20,'role':'outer'}]}]
        root = next(e for e in value['elements'] if e['type']=='relation' and e['id']==1)
        root['members'].append({'type':'relation','ref':2,'role':''})
        raw = json.dumps(value).encode()
        result = compose_extent({1:raw},[selection(1,10,0,1)])
        self.assertEqual([[1,2]],[e['nodes'] for e in result['physical_edges']])
        with self.assertRaises(ValueError):compose_extent({1:raw},[selection(1,20,0,1)])
