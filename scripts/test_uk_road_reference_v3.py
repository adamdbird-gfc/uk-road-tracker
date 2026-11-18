#!/usr/bin/env python3
"""Focused schema regression tests for the versioned national road reference."""
import unittest

from build_gravesend_coverage import ROAD_CLASSES
from build_uk_road_reference import CLASSES, FOOT_ONLY_CLASSES, LABEL_SEPARATOR, encoded_flags, encoded_label


class RoadReferenceV3Test(unittest.TestCase):
    def test_old_road_class_codes_are_stable(self):
        self.assertEqual(tuple(sorted(ROAD_CLASSES)), CLASSES[:len(ROAD_CLASSES)])
        self.assertEqual(len(CLASSES), len(set(CLASSES)))

    def test_named_a_road_retains_separate_official_reference(self):
        raw = encoded_label({"name": "High Street", "ref": "A2"})
        name, road_ref = raw.decode("utf8").split(LABEL_SEPARATOR)
        self.assertEqual("High Street", name)
        self.assertEqual("A2", road_ref)

    def test_footways_paths_cycleways_and_pedestrianised_streets_are_walk_only(self):
        for kind in FOOT_ONLY_CLASSES:
            with self.subTest(kind=kind):
                flags = encoded_flags(kind, {"highway": kind}, major=False)
                self.assertTrue(flags & 2, f"{kind} must be visible to pedestrians")
                self.assertFalse(flags & 1, f"{kind} must not be a drivable road")

    def test_inaccessible_footpath_is_not_awarded(self):
        for key in ("access", "foot"):
            flags = encoded_flags("footway", {"highway": "footway", key: "private"}, major=False)
            self.assertEqual(0, flags & 3)

    def test_drivable_road_preserves_legacy_local_flag(self):
        tags = {"highway": "residential", "access": "yes"}
        self.assertEqual(1, encoded_flags("residential", tags, major=False) & 3)
        self.assertEqual(0, encoded_flags("residential", tags, major=True) & 3)


if __name__ == "__main__":
    unittest.main()
