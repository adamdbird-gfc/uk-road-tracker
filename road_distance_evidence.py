"""Native driving only: retain routed legs supported by their observation span.

The 1.4 / 300 m allowance is the existing captured-drive integrity policy.
Rejected intervals remain explicit gaps; no replacement links are invented.
"""
import copy
import math


def observation_distance(points):
    distance = 0.0
    for a, b in zip(points, points[1:]):
        lat1, lat2 = math.radians(a.lat), math.radians(b.lat)
        h = (math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)
             * math.sin(math.radians(b.lng-a.lng)/2)**2)
        distance += 12742017.6 * math.asin(min(1.0, math.sqrt(h)))
    return distance


def _coordinates(legs):
    """Use actual step geometry only; never bridge disconnected sections."""
    coordinates = []
    for leg in legs:
        steps = leg.get('steps') or []
        if not steps:
            return None
        for step in steps:
            geometry = step.get('geometry') or {}
            coords = geometry.get('coordinates')
            if geometry.get('type') != 'LineString' or not coords:
                return None
            if any(not isinstance(p, list) or len(p) != 2
                   or any(not isinstance(v, (int, float)) or not math.isfinite(v) for v in p)
                   for p in coords):
                return None
            if coordinates and coordinates[-1] != coords[0]:
                return None
            coordinates.extend(coords[1:] if coordinates else coords)
    return coordinates if len(coordinates) >= 2 else None


def supported_road_sections(data, points, point_offset=0):
    """Split excessive legs out of native-road responses with complete attribution.

    Work is linear in the returned observations/geometry, makes no router calls,
    and does not delete input fixes. Incomplete upstream attribution is left for
    existing integrity validation rather than guessing a leg's observations.
    """
    trace = data.get('tracepoints') or []
    if len(trace) != len(points):
        return data, []
    plans = []
    failures = []
    for matching_index, matching in enumerate(data.get('matchings') or []):
        legs = matching.get('legs') or []
        waypoints = sorted((p['waypoint_index'], i) for i, p in enumerate(trace)
                           if p and p.get('matchings_index') == matching_index
                           and isinstance(p.get('waypoint_index'), int))
        if not legs or [w for w, _ in waypoints] != list(range(len(legs)+1)):
            return data, []
        indices = [i for _, i in waypoints]
        if indices != sorted(indices) or len(set(indices)) != len(indices):
            return data, []
        allowed = []
        for leg_index, leg in enumerate(legs):
            start, end = indices[leg_index:leg_index+2]
            distance = leg.get('distance')
            if not isinstance(distance, (int, float)) or not math.isfinite(distance) or distance < 0:
                return data, []
            evidence = observation_distance(points[start:end+1])
            limit = max(evidence*1.4, evidence+300)
            accepted = distance <= limit
            allowed.append(accepted)
            if not accepted:
                failures.append(dict(points=end-start+1,
                    start_point_index=point_offset+start, end_point_index=point_offset+end,
                    detail='road_distance_exceeds_observations', response_received=True,
                    matched_distance_m=distance, observation_distance_m=round(evidence, 3),
                    maximum_supported_distance_m=round(limit, 3)))
        plans.append((matching, indices, allowed))
    if not failures:
        return data, []

    result = copy.deepcopy(data)
    result['matchings'] = []
    result['tracepoints'] = [None]*len(trace)
    for matching, indices, allowed in plans:
        start = 0
        while start < len(allowed):
            if not allowed[start]:
                start += 1
                continue
            end = start+1
            while end < len(allowed) and allowed[end]:
                end += 1
            legs = matching['legs'][start:end]
            coordinates = _coordinates(legs)
            if coordinates is None:
                return data, []
            section = copy.deepcopy(matching)
            section['legs'] = copy.deepcopy(legs)
            section['distance'] = sum(leg['distance'] for leg in legs)
            section['duration'] = sum(leg.get('duration', 0) for leg in legs)
            section['geometry'] = dict(type='LineString', coordinates=coordinates)
            section_index = len(result['matchings'])
            result['matchings'].append(section)
            for waypoint, original_index in enumerate(indices[start:end+1]):
                point = copy.deepcopy(trace[original_index])
                point['matchings_index'] = section_index
                point['waypoint_index'] = waypoint
                result['tracepoints'][original_index] = point
            start = end
    return result, failures
