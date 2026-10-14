import tempfile
import unittest
from pathlib import Path
from shapely.geometry import box
from bulk_settlement_inventory_trial import RoadAccumulator, read_poly, run


class BulkRuleTests(unittest.TestCase):
    def test_trial_cannot_overwrite_published_data(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                run(directory, directory, Path(directory) / 'settlement-inventories-v1', [])

    def test_distinct_names_and_accepted_highways(self):
        roads = RoadAccumulator([box(0, 0, 2, 2)])
        for highway in ['residential', 'tertiary', 'unclassified', 'living_street']:
            roads.accept(highway, ' Example Road ', [(0, 0), (1, 1)])
        roads.accept('motorway', 'Other Road', [(0, 0), (1, 1)])
        roads.accept('footway', 'Path', [(0, 0), (1, 1)])
        self.assertEqual(roads.names, [{'Example Road'}])

    def test_crossing_way_counts_even_with_nodes_outside(self):
        roads = RoadAccumulator([box(0, 0, 1, 1)])
        roads.accept('residential', 'Crossing', [(-1, .5), (2, .5)])
        self.assertEqual(roads.names, [{'Crossing'}])

    def test_outside_and_unnamed_ways_do_not_count(self):
        roads = RoadAccumulator([box(0, 0, 1, 1)])
        roads.accept('residential', 'Outside', [(2, 2), (3, 3)])
        roads.accept('residential', ' ', [(0, 0), (1, 1)])
        roads.accept('residential', 'One node', [(0, 0)])
        self.assertEqual(roads.names, [set()])

    def test_duplicate_extracts_and_shared_boundary_preserve_identity(self):
        roads = RoadAccumulator([box(0, 0, 1, 1), box(1, 0, 2, 1)])
        for _ in range(2):
            roads.accept('residential', 'Shared', [(.5, .5), (1.5, .5)])
        self.assertEqual(roads.names, [{'Shared'}, {'Shared'}])

    def test_poly_holes_are_excluded(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.poly'
            path.write_text('test\n1\n0 0\n2 0\n2 2\n0 2\n0 0\nEND\n!1\n.5 .5\n1.5 .5\n1.5 1.5\n.5 1.5\n.5 .5\nEND\nEND\n')
            self.assertEqual(read_poly(path).area, 3)
