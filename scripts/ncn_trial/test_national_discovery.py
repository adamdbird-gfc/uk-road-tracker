import unittest
from .national_discovery import discover_routes


def row(id,ref='1'):return {'type':'relation','id':id,'ref':ref,'group':'NAT'}


class NationalDiscoveryTests(unittest.TestCase):
    def test_capped_response_is_subdivided_and_parent_results_are_preserved(self):
        def fetch(bounds,limit):
            return {'results':[row(1),row(2)] if bounds==(0,0,2,2) else [row(3)]}
        result=discover_routes(fetch,[0,0,2,2],limit=2)
        self.assertEqual([1,2,3],[r['id'] for r in result['routes']])
        self.assertEqual(5,result['requests']);self.assertTrue(result['query_extent_discovery_complete'])
        self.assertFalse(result['association_approved'])

    def test_terminal_cap_is_never_reported_as_complete(self):
        result=discover_routes(lambda b,l:{'results':[row(1)]},[0,0,1,1],limit=1,max_depth=0)
        self.assertFalse(result['query_extent_discovery_complete'])

    def test_budget_reports_unvisited_children(self):
        result=discover_routes(lambda b,l:{'results':[row(1)]},[0,0,1,1],limit=1,max_requests=1)
        self.assertEqual(4,len(result['unvisited_cells']));self.assertFalse(result['query_extent_discovery_complete'])

    def test_errors_are_explicit_and_do_not_look_like_empty_success(self):
        def fail(bounds,limit):raise ValueError('Incomplete response')
        result=discover_routes(fail,[0,0,1,1]);self.assertFalse(result['query_extent_discovery_complete'])
        self.assertIn('error',result['cells'][0])

    def test_non_relation_source_objects_are_held_separately(self):
        result=discover_routes(lambda b,l:{'results':[{'type':'way','id':42}]},[0,0,1,1])
        self.assertEqual([],result['routes']);self.assertEqual(1,len(result['unsupported_objects']))

    def test_source_conflicts_and_invalid_bounds_are_not_silenced(self):
        def fetch(bounds,limit):
            return {'results':[row(1,'1'),row(2)] if bounds==(0,0,2,2) else [row(1,'2')]}
        result=discover_routes(fetch,[0,0,2,2],limit=2)
        self.assertTrue(result['conflicts']);self.assertFalse(result['query_extent_discovery_complete'])
        with self.assertRaises(ValueError):discover_routes(fetch,[0,0,0,1])
