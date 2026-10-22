import unittest
from shapely.geometry import LineString
from scripts.ncn_trial.physical_crosswalk import compare_route


def way(i, coords):
    return {'way_id':i, 'line':LineString(coords)}


def section(key, coords, number=177):
    return {'key':key, 'route_type':'NCN', 'route_no':number, 'line':LineString(coords)}


class PhysicalCrosswalkTests(unittest.TestCase):
    def test_short_fragment_does_not_prove_whole_route(self):
        r=compare_route([way(1, [(0,0),(10,0)])], [section('a', [(0,0),(100,0)])], 'NCN',177)
        self.assertEqual(r['corridor_sensitivity']['3']['source_fraction'],1)
        self.assertLess(r['corridor_sensitivity']['3']['official_fraction'],.14)
        self.assertFalse(r['completion_ready'])

    def test_other_number_never_fills_matching_route_gap(self):
        r=compare_route([way(1, [(0,0),(100,0)])],
            [section('wrong',[(0,0),(100,0)],1), section('same',[(0,100),(100,100)])], 'NCN',177)
        self.assertEqual(r['corridor_sensitivity']['25']['source_fraction'],0)
        self.assertEqual(r['ways'][0]['other_route_section_keys'],['wrong'])

    def test_parallel_same_number_remains_unapproved(self):
        r=compare_route([way(1,[(0,5),(100,5)])], [section('a',[(0,0),(100,0)])], 'NCN',177)
        self.assertEqual(r['corridor_sensitivity']['3']['source_fraction'],0)
        self.assertEqual(r['corridor_sensitivity']['10']['source_fraction'],1)
        self.assertFalse(r['ways'][0]['association_approved'])

    def test_overlapping_ways_union_not_sum(self):
        r=compare_route([way(1,[(0,0),(100,0)]),way(2,[(100,0),(0,0)])],
            [section('a',[(0,0),(100,0)]),section('b',[(100,0),(0,0)])], 'NCN',177)
        self.assertEqual(r['source_unique_length_m'],100)
        self.assertEqual(r['official_unique_length_m'],100)

    def test_duplicate_identity_refused(self):
        with self.assertRaises(ValueError):
            compare_route([way(1,[(0,0),(10,0)]),way(1,[(10,0),(20,0)])],
                [section('a',[(0,0),(100,0)])], 'NCN',177)

    def test_missing_route_and_invalid_lines_refused(self):
        for sections in ([], [section('a',[(0,0),(0,0)])]):
            with self.assertRaises(ValueError):
                compare_route([way(1,[(0,0),(10,0)])], sections, 'NCN',177)


if __name__=='__main__':unittest.main()
