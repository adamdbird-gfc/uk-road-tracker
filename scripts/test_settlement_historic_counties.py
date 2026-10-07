"""Synthetic topology checks, using no private journeys or coordinates."""
import unittest
from shapely.geometry import box, Polygon
from shapely.strtree import STRtree
from build_settlement_historic_counties import assign


class CountyMembershipTests(unittest.TestCase):
    def test_shared_edge_and_point_do_not_create_membership(self):
        counties = [box(0, 0, 1, 1), box(1, 0, 2, 1), box(2, 1, 3, 2)]
        self.assertEqual(['A'], assign(box(0, 0, 1, 1), counties, ['A', 'B', 'C'], STRtree(counties)))

    def test_cross_border_settlement_has_one_primary_and_all_memberships(self):
        counties = [box(0, 0, 1, 1), box(1, 0, 3, 1)]
        self.assertEqual(['B', 'A'], assign(box(.5, .1, 2.5, .9), counties, ['A', 'B'], STRtree(counties)))

    def test_hole_does_not_assign_the_host_county(self):
        outer = Polygon([(0, 0), (3, 0), (3, 3), (0, 3)],
                        [[(1, 1), (1, 2), (2, 2), (2, 1)]])
        counties = [outer, box(1, 1, 2, 2)]
        self.assertEqual(['B'], assign(box(1.2, 1.2, 1.8, 1.8), counties, ['A', 'B'], STRtree(counties)))

    def test_equal_overlap_has_stable_primary(self):
        counties = [box(0, 0, 1, 1), box(1, 0, 2, 1)]
        self.assertEqual(['A', 'Z'], assign(box(.5, 0, 1.5, 1), counties, ['Z', 'A'], STRtree(counties)))

    def test_missing_reference_never_guesses_from_a_name(self):
        counties = [box(0, 0, 1, 1)]
        self.assertEqual([], assign(box(3, 3, 4, 4), counties, ['A'], STRtree(counties)))


if __name__ == '__main__':
    unittest.main()
