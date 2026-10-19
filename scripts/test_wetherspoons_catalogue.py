import gzip
import unittest

from build_wetherspoons_catalogue import build, contains, decode_rings, query


def feature(number=1, name="The Example", kind="N", tags=None, geometry=None):
    return {"type": "Feature", "properties": {"osm_type": kind, "osm_id": number,
            "lon": 0.1, "lat": 51.5, "tags": {"amenity": "pub", "name": name, **(tags or {})}},
            "geometry": geometry or {"type": "Point", "coordinates": [0.1, 51.5]}}


def catalogue(*features, **kwargs):
    return build({"type": "FeatureCollection", "features": list(features)},
                 retrieved_at="2026-10-11T18:00:00Z", source_sha256="a" * 64, **kwargs)


class WetherspoonsCatalogueTest(unittest.TestCase):
    def test_rename_preserves_identity_and_missing_source_is_not_closure(self):
        old, _ = catalogue(feature())
        new, _ = catalogue(feature(name="Renamed"), previous=old)
        self.assertEqual(old["venues"][0]["id"], new["venues"][0]["id"])
        changed, audit = catalogue(feature(number=2), previous=old)
        self.assertEqual(1, len(audit["source_missing"]))
        missing = next(v for v in changed["venues"] if v["id"] == old["venues"][0]["id"])
        self.assertEqual("source_missing_review", missing["status"])

    def test_alias_merge_is_explicit_and_nearby_different_pubs_stay_separate(self):
        separate, audit = catalogue(feature(1), feature(2, "The Other"))
        self.assertEqual(2, len(separate["venues"]))
        self.assertEqual(1, len(audit["nearby_pairs"]))
        registry = {"venues": [{"id": "spoons-reviewed-example", "osm_aliases": ["node/1", "node/2"]}]}
        joined, _ = catalogue(feature(1), feature(2), registry=registry)
        self.assertEqual(1, len(joined["venues"]))
        self.assertEqual(2, len(joined["venues"][0]["sources"]))

    def test_registry_cannot_rekey_existing_identity_or_duplicate_alias(self):
        old, _ = catalogue(feature())
        registry = {"venues": [{"id": "spoons-new", "osm_aliases": ["node/1"]}]}
        with self.assertRaises(ValueError):
            catalogue(feature(), previous=old, registry=registry)
        registry["venues"].append({"id": "spoons-another", "osm_aliases": ["node/1"]})
        with self.assertRaises(ValueError):
            catalogue(feature(), registry=registry)

    def test_pub_with_rooms_qualifies_but_hotel_only_and_closed_do_not(self):
        hotel = feature(2, tags={"amenity": None, "tourism": "hotel"})
        closed = feature(3, tags={"disused:amenity": "pub"})
        result, audit = catalogue(feature(tags={"tourism": "hotel"}), hotel, closed)
        self.assertEqual(1, len(result["venues"]))
        self.assertEqual(2, len(audit["excluded_objects"]))
        self.assertNotIn("tourism", result["venues"][0])

    def test_footprint_holes_are_preserved_without_claiming_verified_boundary(self):
        geometry = {"type": "Polygon", "coordinates": [
            [[0, 51], [1, 51], [1, 52], [0, 52], [0, 51]],
            [[0.2, 51.2], [0.3, 51.2], [0.3, 51.3], [0.2, 51.2]]]}
        result, audit = catalogue(feature(kind="W", geometry=geometry))
        self.assertEqual(geometry, result["venues"][0]["sources"][0]["geometry"])
        self.assertEqual(1, audit["mapped_footprints"])
        self.assertFalse(result["ready_for_app"])
        self.assertIsNone(result["completion_target"])
        self.assertEqual("UK Wetherspoons Collection", result["display_name"])

    def test_invalid_or_truncated_extract_fails_closed(self):
        bad = feature()
        bad["geometry"]["coordinates"][0] = float("nan")
        with self.assertRaises(ValueError):
            catalogue(bad)
        with self.assertRaises(ValueError):
            catalogue(feature(), feature())
        with self.assertRaises(ValueError):
            catalogue(*[feature(i + 1) for i in range(2001)])

    def test_county_contains_handles_holes_islands_and_border(self):
        rings = [[(0, 0), (10, 0), (10, 10), (0, 10)],
                 [(2, 2), (4, 2), (4, 4), (2, 4)],
                 [(20, 20), (21, 20), (21, 21), (20, 21)]]
        self.assertTrue(contains(rings, 1, 1))
        self.assertFalse(contains(rings, 3, 3))
        self.assertTrue(contains(rings, 20.5, 20.5))
        self.assertFalse(contains(rings, 0, 1))
        with self.assertRaises(ValueError):
            decode_rings(gzip.compress(b"\x01\x03"))

    def test_query_is_pub_focused_and_exact_uk_boundary(self):
        sql = query()
        self.assertIn("'Q6109362'", sql.replace('"', "'"))
        self.assertIn("ST_Covers", sql)
        self.assertIn('"ISO3166-1":"GB"', sql)
        self.assertIn("LIMIT 2001", sql)
        self.assertIn("p.tags->>'operator'", sql)


if __name__ == "__main__":
    unittest.main()
