"""Versioned source identity for exact portions of a stored matched LineString.

No extra coordinates or personal data are persisted on the server. Vertex spans
refer to the existing response geometry; reference metadata describes public ways.
This records routing provenance, not NCN membership or GPS certainty.
"""
import hashlib
import json
import math


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def geometry_digest(coordinates):
    """Exact returned geometry, including direction/order; detects edits."""
    normalized = []
    for point in coordinates:
        if not isinstance(point, (list, tuple)) or len(point) != 2:
            raise ValueError('Invalid matched coordinate')
        if not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in point):
            raise ValueError('Invalid matched coordinate')
        normalized.append([0.0 if value == 0 else float(value) for value in point])
    return digest(normalized)


def source_geometry_digest(coordinates):
    """OSM precision, independent of digit formatting and way orientation."""
    points = []
    for point in coordinates:
        if not isinstance(point, (list, tuple)) or len(point) < 2:
            raise ValueError('Invalid source coordinate')
        x, y = point[:2]
        if not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in (x, y)) \
                or not -180 <= x <= 180 or not -90 <= y <= 90:
            raise ValueError('Invalid WGS84 source coordinate')
        value = [round(float(x), 7) or 0.0, round(float(y), 7) or 0.0]
        if not points or value != points[-1]:
            points.append(value)
    if len(points) < 2:
        raise ValueError('Source way requires two distinct points')
    return digest(min(points, list(reversed(points))))


def build_matched_path_evidence(coordinates, used_feature_ids, features):
    if len(used_feature_ids) != len(coordinates) - 1 or len(coordinates) < 2:
        return {'schema_version': 1, 'status': 'unavailable', 'reason': 'unaligned_traversed_edges'}
    references, indexes, spans = [], {}, []
    for edge_index, feature_id in enumerate(used_feature_ids):
        feature = features.get(feature_id)
        if feature is None or 'matched_reference' not in feature:
            return {'schema_version': 1, 'status': 'unavailable', 'reason': 'missing_reference_identity'}
        if feature_id not in indexes:
            indexes[feature_id] = len(references)
            references.append(feature['matched_reference'])
        index = indexes[feature_id]
        if spans and spans[-1][2] == index and spans[-1][1] == edge_index:
            spans[-1][1] = edge_index + 1
        else:
            spans.append([edge_index, edge_index + 1, index])
    return {'schema_version': 1, 'status': 'available', 'geometry_feature_index': 0,
            'geometry_sha256': geometry_digest(coordinates), 'references': references,
            'spans': spans, 'span_format': ['from_vertex', 'to_vertex_inclusive', 'reference_index']}


def public_way_reference(source_key, source_version, feature_id, kind, tags, coordinates):
    geometry_sha = source_geometry_digest(coordinates)
    public = {'source_key': source_key, 'source_version': source_version,
              'source_feature_id': str(feature_id), 'reference_kind': kind,
              'source_geometry_sha256': geometry_sha,
              'tags': tags or {}}
    public['content_sha256'] = digest(public)
    return public
