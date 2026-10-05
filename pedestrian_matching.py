"""Pure helpers for choosing pedestrian-network paths that follow a trace."""

import math
import time
from heapq import nsmallest


class PedestrianVertexIndex:
    """Route-local grid for exact nearest-vertex queries on the UK network.

    The grid only narrows the search. Ranking and the 120 m acceptance limit
    still use the caller's original great-circle distance calculation.
    """

    CELL_DEGREES = 0.002

    def __init__(self, vertices):
        self.cells = {}
        for key, coordinate in vertices.items():
            cell = self._cell(coordinate)
            self.cells.setdefault(cell, []).append((key, coordinate))

    def _cell(self, coordinate):
        return tuple(math.floor(value / self.CELL_DEGREES) for value in coordinate)

    def nearest(self, point, length_metres, radius=120.0, count=6):
        longitude, latitude = point
        angular_radius = radius / 6371008.8
        latitude_radius = math.degrees(angular_radius)
        # Spherical-cap longitude bound; UK references are away from the poles
        # and date line. A tiny margin also includes floating-point boundaries.
        longitude_radius = math.degrees(math.asin(
            min(1.0, math.sin(angular_radius) / math.cos(math.radians(latitude)))
        ))
        margin = 1e-10
        west, south = self._cell((longitude - longitude_radius - margin,
                                  latitude - latitude_radius - margin))
        east, north = self._cell((longitude + longitude_radius + margin,
                                  latitude + latitude_radius + margin))
        nearby = []
        for x in range(west, east + 1):
            for y in range(south, north + 1):
                for key, coordinate in self.cells.get((x, y), ()):
                    distance = length_metres(point, coordinate)
                    if distance <= radius:
                        nearby.append((distance, key))
        return [(key, distance) for distance, key in nsmallest(count, nearby)]


def match_reference_trajectory(trace, vertex_index, vertices, length_metres,
                               paths_to_targets, deadline, diagnostics):
    """Try a small candidate set, then recover crowded network junctions.

    More candidates retain connected street alternatives when nearer steps
    or service ways crowd them out. Both passes share the original deadline,
    search radius and network graph; no disconnected edges are invented.
    """
    for count in (6, 24):
        layers = []
        for point in trace:
            if time.monotonic() >= deadline:
                diagnostics.update(candidate_count=count, elapsed_limit=True)
                return None
            candidates = vertex_index.nearest(point, length_metres, count=count)
            if not candidates:
                diagnostics.update(candidate_count=count, missing_candidates=True)
                return None
            layers.append(candidates)
        diagnostics.clear()
        diagnostics['candidate_count'] = count
        result = select_trajectory_paths(trace, layers, vertices, length_metres,
                                         paths_to_targets, diagnostics=diagnostics)
        if result:
            return result
        if time.monotonic() >= deadline:
            diagnostics['elapsed_limit'] = True
            return None
    return None


def select_trajectory_paths(
    trace: list[tuple[float, float]],
    candidate_layers: list[list[tuple[tuple[int, int], float]]],
    vertices: dict,
    length_metres,
    paths_to_targets,
    diagnostics: dict | None = None,
) -> tuple[list, list[str]] | None:
    """Select a connected network route that best follows ordered GPS fixes.

    ``paths_to_targets`` returns shortest connected paths from one graph
    vertex to candidate vertices in the next trace layer. Scoring each path
    against the GPS section prevents endpoint-only snaps from creating
    unsupported side-road excursions.
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
            )
            for previous_index, previous_cost in previous_costs.items()
            if math.isfinite(previous_cost)
        }

        current_costs = {}
        current_back = {}
        layer_paths = {}
        for current_index, (current_key, current_gap) in enumerate(candidate_layers[layer_index]):
            best_cost = float("inf")
            best_previous = -1
            for previous_index, previous_cost in previous_costs.items():
                if not math.isfinite(previous_cost):
                    continue
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
            if diagnostics is not None:
                reachable_sources = [index for index, cost in previous_costs.items()
                                     if math.isfinite(cost)]
                connected = [result
                             for index in reachable_sources
                             for result in paths_by_previous[index].values()]
                diagnostics.update(
                    failure_layer=layer_index,
                    observed_distance_m=round(observed_distance, 1),
                    source_candidates=len(reachable_sources),
                    target_candidates=len(target_keys),
                    connected_transitions=len(connected),
                    stationary_transitions=sum(len(result[0]) == 1 for result in connected),
                )
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
    # Dense fixes and short pauses can legitimately share a snapped vertex.
    # Score the zero-length network transition against the observed movement
    # and deviation below; rejecting it forces a detour or breaks the trace.
    if not route:
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
