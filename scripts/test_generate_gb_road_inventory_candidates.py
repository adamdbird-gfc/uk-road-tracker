import gzip
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

import osmium
from generate_gb_road_inventory_candidates import comparison, digest, generate


class NationalGenerationTests(unittest.TestCase):
    def test_comparison_preserves_names_and_reports_additions_removals(self):
        result = comparison('E00000001', 'Example', ['A', 'B'], ['B', 'C'])
        self.assertEqual((result['unchanged'], result['added'], result['removed']), (1, ['C'], ['A']))

    def test_published_directory_is_protected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                generate(directory, 'unused', 'unused', Path(directory) / 'settlement-inventories-v1')

    def test_real_pbf_disk_index_generates_candidate_and_cleans_index(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = root / 'sources'
            sources.mkdir()
            (root / 'settlement-inventories-v1').mkdir()
            (root / 'settlement-catalogue-v1.json').write_text(json.dumps({'settlements': [{'code': 'E00000001', 'name': 'Example'}]}))
            boundary = root / 'boundaries.geojson.gz'
            data = {'type': 'FeatureCollection', 'features': [{'type': 'Feature',
                    'properties': {'code': 'E00000001', 'name': 'Example'}, 'geometry': {
                    'type': 'Polygon', 'coordinates': [[[0, 0], [2, 0], [2, 2], [0, 2], [0, 0]]]}}]}
            with gzip.open(boundary, 'wt') as stream:
                json.dump(data, stream)
            (sources / 'great-britain.poly').write_text('test\n1\n0 0\n2 0\n2 2\n0 2\n0 0\nEND\nEND\n')
            pbf = sources / 'great-britain-latest.osm.pbf'
            with osmium.SimpleWriter(str(pbf)) as writer:
                writer.add_node(osmium.osm.mutable.Node(id=1, location=(.5, .5)))
                writer.add_node(osmium.osm.mutable.Node(id=2, location=(1.5, 1.5)))
                writer.add_way(osmium.osm.mutable.Way(id=1, nodes=[1, 2],
                    tags={'name': 'Example Road', 'highway': 'residential'}))
                writer.add_way(osmium.osm.mutable.Way(id=2, nodes=[1, 2],
                    tags={'name': 'Excluded Path', 'highway': 'footway'}))
            (sources / 'downloads.json').write_text(json.dumps([{'sha256': digest(pbf)}]))
            output = root / 'candidates'
            with redirect_stdout(StringIO()):
                generate(root, boundary, sources, output)
            result = json.loads((output / 'inventories/E00000001.json').read_text())
            self.assertEqual(result['roads'], ['Example Road'])
            self.assertFalse((output / 'node-locations.index').exists())
            report = json.loads((output / 'generation-audit.json').read_text())
            self.assertEqual(report['settlement_count'], 1)
            self.assertEqual(report['status'], 'candidates_only_not_published')
