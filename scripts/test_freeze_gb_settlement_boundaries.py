import tempfile
import http.client
import json
import unittest
from unittest.mock import patch, MagicMock
from pathlib import Path

from freeze_gb_settlement_boundaries import freeze, validate_page, query
from normalise_gb_boundary_snapshot import normalise
from shapely.geometry import MultiPolygon, box, mapping, shape


def feature(identifier=1, code='S45000364'):
    return {'type': 'Feature', 'properties': {'OBJECTID': identifier, 'BUA22CD': code,
            'BUA22NM': 'Example'}, 'geometry': {'type': 'Polygon',
            'coordinates': [[[-4, 55], [-3, 55], [-3, 56], [-4, 56], [-4, 55]]]}}


class BoundarySnapshotTests(unittest.TestCase):
    def test_interrupted_response_retries_without_accepting_partial_bytes(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.read.side_effect = [http.client.IncompleteRead(b'{', 20), b'{"ok": true}']
        with patch('freeze_gb_settlement_boundaries.urllib.request.urlopen', return_value=response) as request, patch('freeze_gb_settlement_boundaries.time.sleep'):
            result, raw = query('https://example.test/query', {'f': 'json'})
        self.assertEqual(result, {'ok': True})
        self.assertEqual(json.loads(raw), result)
        self.assertEqual(request.call_count, 2)

    def test_nested_components_preserve_membership_without_creating_hole(self):
        outer = box(-4, 55, -3, 56)
        inner = box(-3.8, 55.2, -3.2, 55.8)
        geometry, repair = normalise(mapping(MultiPolygon([outer, inner])))
        self.assertTrue(shape(geometry).is_valid)
        self.assertTrue(shape(geometry).equals(outer))
        self.assertEqual(repair['method'], 'union_valid_polygon_components')

    def test_valid_geometry_unchanged(self):
        geometry = mapping(box(-4, 55, -3, 56))
        self.assertEqual(normalise(geometry), (geometry, None))

    def test_scottish_and_cross_border_codes_supported(self):
        for code in ['S45000364', 'K08000001']:
            self.assertEqual(len(validate_page({'features': [feature(code=code)]}, [1], 'OBJECTID')), 1)

    def test_partial_page_rejected(self):
        with self.assertRaises(ValueError):
            validate_page({'features': [feature()]}, [1, 2], 'OBJECTID')

    def test_truncation_flag_rejected(self):
        with self.assertRaises(ValueError):
            validate_page({'features': [feature()], 'exceededTransferLimit': True}, [1], 'OBJECTID')

    def test_duplicate_stable_codes_rejected(self):
        with self.assertRaises(ValueError):
            validate_page({'features': [feature(1), feature(2)]}, [1, 2], 'OBJECTID')

    def test_wrong_crs_rejected(self):
        row = feature()
        row['geometry']['coordinates'] = [[[100000, 100000], [100001, 100000],
            [100001, 100001], [100000, 100001], [100000, 100000]]]
        with self.assertRaises(ValueError):
            validate_page({'features': [row]}, [1], 'OBJECTID')

    def test_cannot_overwrite_published_data(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                freeze(root, Path(root) / 'settlement-inventories-v1')
