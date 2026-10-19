"""Preserve OSRM bicycle node annotations against exact returned geometry spans.

Annotations describe graph nodes, not a one-to-one list of overview vertices.
Only uniquely aligned step geometry is retained; gaps never gain an identity.
"""
from matched_path_evidence import geometry_digest, digest


def osm_node_id(value):
    if type(value) not in (int, float) or not 0 < value < 2**53 or int(value) != value:
        raise ValueError('Invalid OSM node identity')
    return int(value)


def cycling_feature_evidence(matching, feature_index, data_version=None):
    output = {'geometry_feature_index': feature_index, 'legs': [], 'held_legs': [],
              'data_version': data_version if isinstance(data_version, str) else None}
    try:
        geometry = matching['geometry']
        if geometry['type'] != 'LineString': raise ValueError('Unsupported geometry')
        coordinates = geometry['coordinates']
        output['geometry_sha256'] = geometry_digest(coordinates)
        overview = []
        for index, point in enumerate(coordinates):
            if not overview or point != overview[-1][1]: overview.append((index, point))
        previous_end = -1
        for leg_index, leg in enumerate(matching.get('legs') or []):
            try:
                nodes = [osm_node_id(n) for n in leg['annotation']['nodes']]
                if len(nodes) < 2: raise ValueError('Missing graph node chain')
                points = []
                for step in leg['steps']:
                    if step['geometry']['type'] != 'LineString': raise ValueError('Unsupported step')
                    step_points = step['geometry']['coordinates']
                    geometry_digest(step_points)
                    if points and step_points[0] != points[-1]: raise ValueError('Discontinuous steps')
                    for point in step_points:
                        if not points or point != points[-1]: points.append(point)
                if len(points) < 2: raise ValueError('Zero-length leg')
                hits = [i for i in range(len(overview)-len(points)+1)
                        if [p for _, p in overview[i:i+len(points)]] == points]
                if len(hits) != 1: raise ValueError('Leg not uniquely aligned to overview')
                start, end = overview[hits[0]][0], overview[hits[0]+len(points)-1][0]
                if start < previous_end: raise ValueError('Overlapping leg spans')
                previous_end = end
                output['legs'].append({'leg_index': leg_index, 'from_vertex': start,
                                       'to_vertex': end, 'osm_nodes': nodes})
            except (KeyError, TypeError, ValueError, IndexError, OverflowError) as error:
                output['held_legs'].append({'leg_index': leg_index, 'reason': str(error)})
    except (KeyError, TypeError, ValueError, IndexError, OverflowError) as error:
        output['reason'] = str(error)
    output['status'] = ('partial' if output['held_legs'] else 'available') if output['legs'] else 'unavailable'
    output['content_sha256'] = digest(output)
    return output


def cycling_evidence(features):
    return {'schema_version': 1, 'provider': 'osrm', 'router_graph': 'bicycle',
            'status': ('partial' if any(f['status']!='available' for f in features) else 'available')
                      if any(f['legs'] for f in features) else 'unavailable',
            'features': features}
