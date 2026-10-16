from copy import deepcopy
import json
import unittest
from .catalogue_extract import extract_route


def encoded(elements):return json.dumps({'elements':elements}).encode()
def relation(identity,members,route='bicycle'):
    return {'type':'relation','id':identity,'version':1,'tags':{'type':'route','route':route,'name':'Trial'},'members':members}
def member(kind,identity,role=''):return {'type':kind,'ref':identity,'role':role}


class CatalogueExtractTests(unittest.TestCase):
    def setUp(self):
        self.nodes=[{'type':'node','id':i,'version':1,'lon':i*.001,'lat':0} for i in range(1,4)]
        self.way={'type':'way','id':10,'version':1,'nodes':[1,2,3],'tags':{'foot':'no'}}
        self.root=relation(100,[member('way',10)])

    def test_nested_membership_and_direction_are_preserved_without_access_inference(self):
        parent=relation(200,[member('relation',100,'alternative')]);parent['tags']['type']='superroute'
        self.root['members'][0]['role']='backward'
        summary,features=extract_route([encoded([parent,self.root,self.way]+self.nodes)],200)
        self.assertTrue(summary['member_geometry_complete'])
        self.assertEqual([100],summary['nested_relation_ids'])
        self.assertEqual([[[200,'alternative'],[100,'backward']]],json.loads(json.dumps(features[0]['properties']['membership_chains'])))
        self.assertEqual('no',features[0]['properties']['foot'])
        self.assertFalse(summary['activity_access_verified'])
        self.assertFalse(summary['completion_ready'])

    def test_duplicate_way_occurrences_do_not_duplicate_geometry(self):
        self.root['members'].append(member('way',10,'forward'))
        summary,features=extract_route([encoded([self.root,self.way]+self.nodes)],100)
        self.assertEqual(1,len(features));self.assertEqual(2,summary['unique_node_edges'])
        self.assertEqual(2,len(features[0]['properties']['membership_chains']))

    def test_missing_midpoint_never_creates_an_invented_edge(self):
        summary,features=extract_route([encoded([self.root,self.way,self.nodes[0],self.nodes[2]])],100)
        self.assertFalse(summary['member_geometry_complete']);self.assertEqual([],features)
        self.assertEqual([{'type':'node','id':2}],summary['missing_members'])

    def test_missing_nested_relation_and_cycle_are_explicit(self):
        self.root['members']=[member('relation',200)]
        summary,_=extract_route([encoded([self.root])],100)
        self.assertFalse(summary['member_geometry_complete'])
        child=relation(200,[member('relation',100)])
        summary,_=extract_route([encoded([self.root,child])],100)
        self.assertEqual([100],summary['cyclic_relation_ids'])

    def test_disconnected_paths_are_not_snapped_together(self):
        other={'type':'way','id':11,'nodes':[4,5]}
        extra=[{'type':'node','id':4,'lon':.00300001,'lat':0},{'type':'node','id':5,'lon':.004,'lat':0}]
        self.root['members'].append(member('way',11))
        summary,_=extract_route([encoded([self.root,self.way,other]+self.nodes+extra)],100)
        self.assertEqual(2,summary['connected_components'])
        self.assertEqual(4,summary['degree_one_endpoint_count'])

    def test_conflicting_source_versions_require_refresh(self):
        changed=deepcopy(self.way);changed['version']=2
        with self.assertRaises(ValueError):extract_route([encoded([self.root,self.way]+self.nodes),encoded([changed])],100)

    def test_marker_nodes_are_not_counted_as_path_edges(self):
        self.root['members'].append(member('node',3,'guidepost'))
        self.root['tags']['route']='hiking'
        summary,_=extract_route([encoded([self.root,self.way]+self.nodes)],100)
        self.assertEqual(2,summary['unique_node_edges'])
        self.assertEqual('hiking',summary['source_route_type'])

    def test_empty_or_invalid_geometry_cannot_be_treated_as_complete(self):
        self.root['members']=[]
        summary,_=extract_route([encoded([self.root])],100)
        self.assertFalse(summary['member_geometry_complete'])
        self.root['members']=[member('way',10)]
        self.nodes[1]['lon']=float('nan')
        with self.assertRaises(ValueError):extract_route([encoded([self.root,self.way]+self.nodes)],100)
