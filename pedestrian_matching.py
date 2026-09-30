"""Pure helpers for choosing pedestrian-network paths that follow a trace."""

import math


def shortest_trace_aligned_paths(
    start_key,
    target_keys,
    max_distance: float,
    trace_start: tuple[float, float],
    trace_end: tuple[float, float],
    vertices: dict,
    adjacency: dict,
    length_metres,
) -> dict:
    """Find graph paths that stay near the GPS section, not just shortest ones.

    The walking network can contain a short spur that is topologically valid
    but unsupported by the trace. A normal shortest-path search may prefer that
    spur. This search adds a cross-track cost to each edge while retaining a
    hard cap on physical route length.
    """
    from heapq import heappop, heappush

    target_set = set(target_keys)
    remaining = set(target_set)
    target_results = {}
    weighted_costs = {start_key: 0.0}
    physical_distances = {start_key: 0.0}
    previous = {}
    queue = [(0.0, start_key)]
    visited = 0

    while queue and remaining and visited < 150_000:
        weighted_cost, current = heappop(queue)
        if weighted_cost != weighted_costs.get(current):
            continue
        visited += 1
        remaining.discard(current)
        current_physical = physical_distances[current]
        for neighbour, edge_distance, feature_id in adjacency.get(current, []):
            physical_candidate = current_physical + edge_distance
            if physical_candidate > max_distance:
                continue
            a = vertices[current]
            b = vertices[neighbour]
            midpoint = ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)
            lateral_distance = distance_to_segment_metres(
                midpoint, trace_start, trace_end
            )
            outside_trace_corridor = max(0.0, lateral_distance - 8.0)
            edge_cost = edge_distance * (1.0 + (outside_trace_corridor / 10.0) ** 2)
            candidate_cost = weighted_cost + edge_cost
            if candidate_cost < weighted_costs.get(neighbour, float("inf")):
                weighted_costs[neighbour] = candidate_cost
                physical_distances[neighbour] = physical_candidate
                previous[neighbour] = (current, feature_id)
                heappush(queue, (candidate_cost, neighbour))

    for target in target_set - remaining:
        if target == start_key:
            target_results[target] = ([start_key], [], 0.0)
            continue
        path = [target]
        feature_ids = []
        current = target
        while current != start_key:
            prior, feature_id = previous[current]
            path.append(prior)
            feature_ids.append(feature_id)
            current = prior
        path.reverse()
        feature_ids.reverse()
        target_results[target] = (path, feature_ids, physical_distances[target])

    return target_results


def distance_to_segment_metres(
    point: tuple[float, float],
    segment_start: tuple[float, float],
    segment_end: tuple[float, float],
) -> float:
    """Return point-to-segment distance using a local metre projection."""
    mean_latitude = math.radians(
        (segment_start[1] + segment_end[1]) / 2.0
    )
    metres_per_degree_lat = 111_320.0
    metres_per_degree_lng = metres_per_degree_lat * math.cos(mean_latitude)
    start_x = segment_start[0] * metres_per_degree_lng
    start_y = segment_start[1] * metres_per_degree_lat
    end_x = segment_end[0] * metres_per_degree_lng
    end_y = segment_end[1] * metres_per_degree_lat
    point_x = point[0] * metres_per_degree_lng
    point_y = point[1] * metres_per_degree_lat
    segment_x, segment_y = end_x - start_x, end_y - start_y
    segment_length_squared = segment_x * segment_x + segment_y * segment_y

    if segment_length_squared:
        projection = (
            (point_x - start_x) * segment_x + (point_y - start_y) * segment_y
        ) / segment_length_squared
        projection = max(0.0, min(1.0, projection))
        nearest_x = start_x + projection * segment_x
        nearest_y = start_y + projection * segment_y
    else:
        nearest_x, nearest_y = start_x, start_y
    return math.hypot(point_x - nearest_x, point_y - nearest_y)


def select_trajectory_paths(
    trace: list[tuple[float, float]],
    candidate_layers: list[list[tuple[tuple[int, int], float]]],
    vertices: dict,
    length_metres,
    paths_to_targets,
) -> tuple[list, list[str]] | None:
    """Select a connected network route that best follows ordered GPS fixes.

    ``paths_to_targets`` returns connected paths from one graph vertex to
    candidates in the next trace layer, preferring paths aligned with the GPS
    section. Scoring each path also considers its shape and distance.
    """
    if not trace or len(trace) != len(candidate_layers) or any(not layer for layer in candidate_layers):
        return None
    if len(trace) == 1:
        return [candidate_layers[0][0][0]], []

    previous_costs = {
        index: (gap / 15.0) ** 2
        for index, (_, gap) in enumerate(candidate_layers[0])
    }
    back_pointers = []
    transition_paths = []

    for layer_index in range(1, len(candidate_layers)):
        trace_start, trace_end = trace[layer_index - 1], trace[layer_index]
        observed_distance = length_metres(trace_start, trace_end)
        max_path_distance = max(120.0, observed_distance * 4.0 + 60.0)
        target_keys = [key for key, _ in candidate_layers[layer_index]]
        paths_by_previous = {
            previous_index: paths_to_targets(
                candidate_layers[layer_index - 1][previous_index][0],
                target_keys,
                max_path_distance,
                trace_start,
                trace_end,
            )
            for previous_index in previous_costs
        }

        current_costs = {}
        current_back = {}
        layer_paths = {}
        for current_index, (current_key, current_gap) in enumerate(candidate_layers[layer_index]):
            best_cost = float("inf")
            best_previous = -1
            for previous_index, previous_cost in previous_costs.items():
                result = paths_by_previous[previous_index].get(current_key)
                if not result:
                    continue
                path, feature_ids, path_distance = result
                route = [vertices[key] for key in path]
                total = previous_cost + score_pedestrian_path(
                    trace_start, trace_end, route, path_distance, length_metres
                ) + (current_gap / 15.0) ** 2
                if total < best_cost:
                    best_cost, best_previous = total, previous_index
            current_costs[current_index] = best_cost
            current_back[current_index] = best_previous
            if best_previous >= 0:
                layer_paths[(best_previous, current_index)] = paths_by_previous[best_previous][current_key]

        if not current_costs or min(current_costs.values()) == float("inf"):
            return None
        previous_costs = current_costs
        back_pointers.append(current_back)
        transition_paths.append(layer_paths)

    state = min(previous_costs, key=previous_costs.get)
    selected_states = [state]
    for back in reversed(back_pointers):
        state = back[state]
        if state < 0:
            return None
        selected_states.append(state)
    selected_states.reverse()

    route_keys = [candidate_layers[0][selected_states[0]][0]]
    used_feature_ids = []
    for layer_index, (previous_index, current_index) in enumerate(
        zip(selected_states, selected_states[1:])
    ):
        path, feature_ids, _ = transition_paths[layer_index][(previous_index, current_index)]
        route_keys.extend(path[1:])
        used_feature_ids.extend(feature_ids)
    return route_keys, used_feature_ids


def score_pedestrian_path(
    trace_start: tuple[float, float],
    trace_end: tuple[float, float],
    route: list[tuple[float, float]],
    route_distance: float,
    length_metres,
) -> float:
    """Score a connected network path against one ordered GPS trace section.

    A path can have plausible endpoints yet take a needless side-road loop
    between them. Measuring the route's deviation from the GPS segment catches
    that case, especially for two-point traces where there are no intermediate
    fixes to steer a point-only matcher.
    """
    if len(route) < 2:
        return float("inf")

    observed_distance = length_metres(trace_start, trace_end)
    distance_scale = max(20.0, observed_distance * 0.4)
    distance_penalty = abs(route_distance - observed_distance) / distance_scale

    # Project the route and GPS section into a local metre-based plane. The
    # trace segment is the evidence available between these two observations;
    # a supported turn is retained because turn points are sampled separately.
    mean_latitude = math.radians((trace_start[1] + trace_end[1]) / 2.0)
    metres_per_degree_lat = 111_320.0
    metres_per_degree_lng = metres_per_degree_lat * math.cos(mean_latitude)
    start_x = trace_start[0] * metres_per_degree_lng
    start_y = trace_start[1] * metres_per_degree_lat
    end_x = trace_end[0] * metres_per_degree_lng
    end_y = trace_end[1] * metres_per_degree_lat
    segment_x, segment_y = end_x - start_x, end_y - start_y
    segment_length_squared = segment_x * segment_x + segment_y * segment_y

    deviations = []
    for longitude, latitude in route:
        point_x = longitude * metres_per_degree_lng
        point_y = latitude * metres_per_degree_lat
        if segment_length_squared:
            projection = (
                (point_x - start_x) * segment_x + (point_y - start_y) * segment_y
            ) / segment_length_squared
            projection = max(0.0, min(1.0, projection))
            nearest_x = start_x + projection * segment_x
            nearest_y = start_y + projection * segment_y
        else:
            nearest_x, nearest_y = start_x, start_y
        deviations.append(math.hypot(point_x - nearest_x, point_y - nearest_y))

    mean_deviation = sum(deviations) / len(deviations)
    max_deviation = max(deviations)
    shape_penalty = 2.0 * (mean_deviation / 12.0) ** 2
    shape_penalty += 1.5 * (max_deviation / 25.0) ** 2
    return distance_penalty + shape_penalty
