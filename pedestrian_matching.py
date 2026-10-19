"""Pure helpers for choosing pedestrian-network paths that follow a trace."""

import math
import time
from heapq import nsmallest, heappop, heappush
from collections import OrderedDict


class PedestrianEdgeCandidates:
    """Project fixes onto real edges, splitting only those edges in place.

    Synthetic vertices belong to a particular edge, so nearby, unconnected
    paths never acquire a fabricated junction. Original endpoints retain their
    identity. All candidate layers are prepared before graph contraction.
    """

    def __init__(self, trace, vertices, adjacency, length_metres, deadline):
        cells, edges, broad_edges = {}, [], []
        cell_size = PedestrianVertexIndex.CELL_DEGREES
        seen = set()
        for a, links in adjacency.items():
            for b, distance, feature in links:
                if time.monotonic() >= deadline:
                    raise TimeoutError("Pedestrian edge preparation exceeded deadline")
                identity = (frozenset((a, b)), feature)
                if identity in seen:
                    continue
                seen.add(identity)
                edge_id = len(edges)
                edges.append((a, b, distance, feature))
                start, end = vertices[a], vertices[b]
                west, east = sorted((math.floor(start[0]/cell_size), math.floor(end[0]/cell_size)))
                south, north = sorted((math.floor(start[1]/cell_size), math.floor(end[1]/cell_size)))
                if (east-west+1)*(north-south+1) > 4096:
                    broad_edges.append(edge_id)
                    continue
                for x in range(west, east + 1):
                    for y in range(south, north + 1):
                        cells.setdefault((x, y), []).append(edge_id)

        self.layers = {}
        splits = {}
        for point in trace:
            if time.monotonic() >= deadline:
                raise TimeoutError("Pedestrian projection exceeded deadline")
            if tuple(point) in self.layers:
                continue
            longitude, latitude = point
            scale = math.cos(math.radians(latitude))
            lat_radius = math.degrees(120.0/6371008.8) + 1e-10
            lng_radius = math.degrees(math.asin(math.sin(120.0/6371008.8)/scale)) + 1e-10
            nearby = set(broad_edges)
            for x in range(math.floor((longitude-lng_radius)/cell_size),
                           math.floor((longitude+lng_radius)/cell_size)+1):
                for y in range(math.floor((latitude-lat_radius)/cell_size),
                               math.floor((latitude+lat_radius)/cell_size)+1):
                    nearby.update(cells.get((x, y), ()))
            candidates = []
            for edge_id in nearby:
                if time.monotonic() >= deadline:
                    raise TimeoutError("Pedestrian projection exceeded deadline")
                a, b, _, _ = edges[edge_id]
                start, end = vertices[a], vertices[b]
                dx, dy = (end[0]-start[0])*scale, end[1]-start[1]
                norm = dx*dx + dy*dy
                if not norm:
                    continue
                fraction = max(0.0, min(1.0, ((longitude-start[0])*scale*dx
                                            + (latitude-start[1])*dy)/norm))
                projected = [start[0]+fraction*(end[0]-start[0]),
                             start[1]+fraction*(end[1]-start[1])]
                gap = length_metres(point, projected)
                if gap <= 120.0:
                    candidates.append((gap, edge_id, fraction, projected))
            layer, used = [], set()
            for gap, edge_id, fraction, projected in sorted(candidates):
                a, b, _, _ = edges[edge_id]
                # Edge-specific keys prevent joining paths that merely cross.
                key = a if fraction <= 1e-9 else b if fraction >= 1-1e-9 else (
                    "projection", edge_id, round(fraction, 12))
                if key in used:
                    continue
                used.add(key)
                layer.append((key, gap))
                if key != a and key != b:
                    vertices[key] = projected
                    splits.setdefault(edge_id, {})[key] = fraction
                if len(layer) >= 24:
                    break
            self.layers[tuple(point)] = layer

        for edge_id, projections in splits.items():
            if time.monotonic() >= deadline:
                raise TimeoutError("Pedestrian edge splitting exceeded deadline")
            a, b, _, feature = edges[edge_id]
            adjacency[a] = [link for link in adjacency[a] if not (link[0] == b and link[2] == feature)]
            adjacency[b] = [link for link in adjacency[b] if not (link[0] == a and link[2] == feature)]
            ordered = [a] + sorted(projections, key=projections.get) + [b]
            for start, end in zip(ordered, ordered[1:]):
                distance = length_metres(vertices[start], vertices[end])
                adjacency.setdefault(start, []).append((end, distance, feature))
                adjacency.setdefault(end, []).append((start, distance, feature))

    def nearest(self, point, length_metres, radius=120.0, count=6):
        return [(key, gap) for key, gap in self.layers.get(tuple(point), ())
                if gap <= radius][:count]


class PedestrianVertexIndex:
    """Route-local grid for exact nearest-vertex queries on the UK network.

    The grid only narrows the search. Ranking and the 120 m acceptance limit
    still use the caller's original great-circle distance calculation.
    """

    CELL_DEGREES = 0.002

    def __init__(self, vertices):
        self.cells = {}
        self.nearest_cache = OrderedDict()
        for key, coordinate in vertices.items():
            cell = self._cell(coordinate)
            self.cells.setdefault(cell, []).append((key, coordinate))

    def _cell(self, coordinate):
        return tuple(math.floor(value / self.CELL_DEGREES) for value in coordinate)

    def nearest(self, point, length_metres, radius=120.0, count=6):
        cache_key = (tuple(point), radius, length_metres)
        cached = self.nearest_cache.get(cache_key)
        if cached is not None and count <= cached[0]:
            self.nearest_cache.move_to_end(cache_key)
            return cached[1][:count]
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
        cached_count = max(24, count)
        result = [(key, distance) for distance, key in nsmallest(cached_count, nearby)]
        self.nearest_cache[cache_key] = (cached_count, result)
        while len(self.nearest_cache) > 256:
            self.nearest_cache.popitem(last=False)
        return result[:count]


def compress_pedestrian_graph(adjacency, retained_vertices, deadline):
    """Contract degree-two chains without removing any candidate or junction.

    Each link retains its original nodes and feature IDs in travel order, so
    path reconstruction and GPS shape scoring use the full original geometry.
    Parallel links and junctions remain explicit anchors.
    """
    anchors = set(retained_vertices)
    for key, edges in adjacency.items():
        if len(edges) != 2 or edges[0][0] == edges[1][0] or any(edge[0] == key for edge in edges):
            anchors.add(key)
    # Unanchored all-degree-two components have no route candidates. They do
    # not need searches; candidate-bearing cycles already contain an anchor.
    compressed = {}
    for anchor in anchors:
        if time.monotonic() >= deadline:
            return adjacency
        links = []
        for neighbour, cost, feature in adjacency.get(anchor, ()):
            nodes, features = [anchor, neighbour], [feature]
            prior, current, total = anchor, neighbour, cost
            while current not in anchors:
                if time.monotonic() >= deadline:
                    return adjacency
                edges = adjacency[current]
                onward = edges[0] if edges[0][0] != prior else edges[1]
                following, distance, feature = onward
                nodes.append(following)
                features.append(feature)
                total += distance
                prior, current = current, following
            links.append((current, total, (tuple(nodes), tuple(features))))
        compressed[anchor] = links
    return compressed


class PedestrianGraphPaths:
    """Exact bounded Dijkstra searches, reused across overlapping GPS layers.

    Frontiers remain resumable when a later layer needs a larger distance.
    Component membership excludes impossible targets without exploring their
    disconnected surroundings. Cache lifetime is one transient route graph.
    """
    def __init__(self, adjacency, deadline, max_cache_nodes=50000):
        self.adjacency = adjacency
        self.deadline = deadline
        self.max_cache_nodes = max_cache_nodes
        self.cache = OrderedDict()
        self.components = {}
        self.searches = self.cache_hits = self.settled_nodes = 0
        for start in adjacency:
            if start in self.components:
                continue
            stack = [start]
            self.components[start] = start
            while stack:
                if time.monotonic() >= deadline:
                    return
                current = stack.pop()
                for neighbour, _, _ in adjacency.get(current, ()):
                    if neighbour not in self.components:
                        self.components[neighbour] = start
                        stack.append(neighbour)

    def __call__(self, start, targets, max_distance):
        if time.monotonic() >= self.deadline or start not in self.components:
            return {}
        targets = {target for target in targets
                   if self.components.get(target) == self.components[start]}
        if not targets:
            return {}
        state = self.cache.pop(start, None)
        if state is None:
            self.searches += 1
            state = {'distances': {start: 0.0}, 'previous': {}, 'settled': set(),
                     'queue': [(0.0, 0, start)], 'serial': 0}
        else:
            self.cache_hits += 1
        distances, previous = state['distances'], state['previous']
        settled, queue = state['settled'], state['queue']
        remaining = targets - settled
        while queue and remaining and len(settled) < 150000:
            if time.monotonic() >= self.deadline:
                return {}
            # Preserve the frontier when this layer's bound is reached.
            if queue[0][0] > max_distance:
                break
            current_distance, _, current = heappop(queue)
            if current in settled or current_distance != distances.get(current):
                continue
            settled.add(current)
            self.settled_nodes += 1
            remaining.discard(current)
            for neighbour, cost, feature_id in self.adjacency.get(current, ()):
                candidate = current_distance + cost
                if candidate < distances.get(neighbour, float('inf')):
                    distances[neighbour] = candidate
                    previous[neighbour] = (current, feature_id)
                    # Include the next frontier beyond the current bound so
                    # future calls can extend this same exact shortest search.
                    state['serial'] += 1
                    heappush(queue, (candidate, state['serial'], neighbour))
        self.cache[start] = state
        cached_nodes = sum(len(item['distances']) for item in self.cache.values())
        while self.cache and (cached_nodes > self.max_cache_nodes or len(self.cache) > 96):
            _, removed = self.cache.popitem(last=False)
            cached_nodes -= len(removed['distances'])
        paths = {}
        for target in targets & settled:
            if distances[target] > max_distance:
                continue
            links = []
            current = target
            while current != start:
                prior, feature = previous[current]
                links.append((prior, current, feature))
                current = prior
            path, feature_ids = [start], []
            for prior, current, feature in reversed(links):
                if isinstance(feature, tuple):
                    original_nodes, original_features = feature
                    path.extend(original_nodes[1:])
                    feature_ids.extend(original_features)
                else:
                    path.append(current)
                    feature_ids.append(feature)
            paths[target] = (path, feature_ids, distances[target])
        return paths


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
        diagnostics['max_layer_candidates'] = max(len(layer) for layer in layers)
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
                if layer_index > 1 and len(path) > 1:
                    prior_state = back_pointers[-1].get(previous_index, -1)
                    prior_result = transition_paths[-1].get((prior_state, previous_index))
                    if prior_result:
                        total += unsupported_reversal_penalty(
                            trace[layer_index-2], trace_start, trace_end,
                            prior_result[0], path, vertices, length_metres)
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


def unsupported_reversal_penalty(before, point, after, incoming, outgoing,
                                 vertices, length_metres):
    """Discourage immediately retracing an edge without GPS turn evidence.

    This is a selection cost, never a geometry deletion: a genuine return is
    allowed, and clear observed reversals carry no additional cost.
    """
    if len(incoming) < 2 or len(outgoing) < 2:
        return 0.0
    if incoming[-2] != outgoing[1]:
        return 0.0
    scale = math.cos(math.radians(point[1]))
    dx, dy = (point[0]-before[0])*scale, point[1]-before[1]
    ex, ey = (after[0]-point[0])*scale, after[1]-point[1]
    norm = math.hypot(dx, dy)*math.hypot(ex, ey)
    if (norm and (dx*ex+dy*ey)/norm < -0.5
            and min(length_metres(before, point),length_metres(point, after)) >= 8.0):
        return 0.0
    retraced = length_metres(vertices[outgoing[0]], vertices[outgoing[1]])
    return 8.0 + min(8.0, retraced/10.0)


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
