"""Independent OSM member-way geometry, with synthetic observations only."""
import hashlib
import json
import math
from shapely.geometry import LineString
from shapely.ops import substring
from .fixtures import ROOT, fixes_along, load_reference


def samples():
    reference, metadata = load_reference()
    folder = ROOT / 'tests/fixtures/ncn-route1-context'
    source = json.loads((folder / 'source.json').read_text())
    raw = (folder / 'ways.geojson').read_bytes()
    if hashlib.sha256(raw).hexdigest() != source['fixture_sha256']:
        raise ValueError('OSM context fixture checksum changed')
    lng0, lat0 = metadata['projection_origin']
    def project(c):
        return (6371008.8 * math.radians(c[0] - lng0) * math.cos(math.radians(lat0)),
                6371008.8 * math.radians(c[1] - lat0))
    result = []
    for feature in json.loads(raw)['features']:
        props = feature['properties']
        way = props['osm_way']
        target = reference.sections['FID:16404:0' if way == 4973301 else 'FID:954:0']
        line = LineString([project(c) for c in feature['geometry']['coordinates']])
        clipped = line.intersection(target.line.buffer(3))
        parts = [clipped] if clipped.geom_type == 'LineString' else [p for p in clipped.geoms if p.geom_type == 'LineString']
        best = max(parts, key=lambda p: p.length)
        length = min(80, best.length * .8)
        sample = substring(best, (best.length - length) / 2, (best.length + length) / 2)
        result.append((props, length, fixes_along(sample, 0, 1)))
    return reference, source, result
