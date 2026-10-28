"""Pure helpers for choosing pedestrian-network paths that follow a trace."""

import math
import time
from statistics import median
from heapq import nsmallest, heappop, heappush
from collections import OrderedDict


class PedestrianEdgeCandidates:
    """Project fixes onto real edges, splitting only those edges in place.

    Synthetic vertices belong to a particular edge, so nearby, unconnected
    paths never acquire a fabricated junction. Original endpoints retain their
    identity. All candidate layers are prepared before graph contraction.
    """

    def __init__(self, trace, vertices, adjacency, length_metres, deadline, accuracies=None):
        cells, edges, edge_bounds, broad_edges = {}, [], [], []
        # Finer edge cells keep crowded urban queries local. All intersecting
        # cells and broad edges are still queried; the acceptance radius and
        # exact great-circle ranking do not change.
        cell_size = PedestrianVertexIndex.CELL_DEGREES / 4
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
                edge_bounds.append((min(start[0], end[0]), max(start[0], end[0]),
                                    min(start[1], end[1]), max(start[1], end[1])))
                west, east = sorted((math.floor(start[0]/cell_size), math.floor(end[0]/cell_size)))
                south, north = sorted((math.floor(start[1]/cell_size), math.floor(end[1]/cell_size)))
                if (east-west+1)*(north-south+1) > 4096:
                    broad_edges.append(edge_id)
                    continue
                for x in range(west, east + 1):
                    for y in range(south, north + 1):
                        cells.setdefault((x, y), []).append(edge_id)

        junction_index = PedestrianVertexIndex({key:coordinate for key,coordinate in vertices.items()
            if len(adjacency.get(key,()))!=2}) if accuracies is not None else None
        self.accuracy_radii = {}
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
                west, east, south, north = edge_bounds[edge_id]
                # Grid cells are deliberately coarse. Reject edges whose
                # bounding boxes cannot intersect the exact 120 m spherical
                # cap before doing projection/trigonometry. This removes no
                # candidate that can pass the original great-circle cutoff.
                if (west > longitude+lng_radius or east < longitude-lng_radius
                        or south > latitude+lat_radius or north < latitude-lat_radius):
                    continue
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

        # Adjacent uncertain fixes may represent the same physical position.
        # Share existing edge projections, never join crossing/parallel edges.
        # This permits a stationary transition along an edge, not only at a
        # pre-existing network junction. Original candidates remain available.
        self.shared_layers = {}
        self.junction_layers = {}
        if accuracies is not None:
            for index, point in enumerate(trace):
                accuracy=accuracies[index] or 0.0
                if not math.isfinite(accuracy) or accuracy<=0:continue
                if time.monotonic()>=deadline:raise TimeoutError("Pedestrian shared candidates exceeded deadline")
                radius=min(50.0,max(3.0,accuracy))
                self.accuracy_radii[tuple(point)]=max(15.0,2.0*radius)
                own={key for key,_ in self.layers[tuple(point)]}
                shared={}
                for nearby in (index-1,index+1):
                    if nearby<0 or nearby>=len(trace):continue
                    for key,_ in self.layers[tuple(trace[nearby])]:
                        if key in own:continue
                        gap=length_metres(point,vertices[key])
                        if gap<=radius:shared[key]=gap
                self.junction_layers[tuple(point)]=[(key,gap) for key,gap in
                    junction_index.nearest(point,length_metres,radius=radius,count=4) if key not in own]
                self.shared_layers[tuple(point)]=sorted(shared.items(),key=lambda item:item[1])[:8]

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
        evidence_radius=min(radius,self.accuracy_radii.get(tuple(point),radius))
        own=[(key,gap) for key,gap in self.layers.get(tuple(point),()) if gap<=evidence_radius][:count]
        if not own:own=[(key,gap) for key,gap in self.layers.get(tuple(point),()) if gap<=radius][:1]
        shared=[(key,gap) for key,gap in self.shared_layers.get(tuple(point),()) if gap<=radius][:count//2]
        keys={key for key,_ in own+shared}
        junctions=[(key,gap) for key,gap in self.junction_layers.get(tuple(point),()) if key not in keys and gap<=radius]
        return own+shared+junctions


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
                               paths_to_targets, deadline, diagnostics, accuracies=None):
    """Try a small candidate set, then recover crowded network junctions.

    More candidates retain connected street alternatives when nearer steps
    or service ways crowd them out. Both passes share the original deadline,
    search radius and network graph; no disconnected edges are invented.
    """
    for count in (24,):
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
                                         paths_to_targets, diagnostics=diagnostics, accuracies=accuracies)
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
    accuracies: list[float] | None = None,
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

    def uncertainty(index):
        value=accuracies[index] if accuracies is not None else 0
        return min(50.0, max(3.0, value)) if value and math.isfinite(value) else 0.0
    def sigma(index):
        return uncertainty(index) or 15.0

    def observation_cost(gap, index):
        # Accuracy is an uncertainty region, not evidence for visiting its
        # centre. Rewarding every closer snap makes a run of drifting fixes
        # buy an unsupported side-street visit, even in a global Viterbi pass.
        return (max(0.0, gap - uncertainty(index)) / sigma(index)) ** 2

    known_uncertainties = [uncertainty(i) for i in range(len(trace)) if uncertainty(i) > 0]
    continuity_scale = max(20.0, median(known_uncertainties)) if known_uncertainties else None
    # One travel cost for the entire sequence: summing transitions now prices
    # cumulative network travel (including longer retraces) consistently.
    # Per-transition accuracy previously made travel artificially cheaper at
    # poor fixes. All observation/shape costs and real connected alternatives
    # remain in the optimisation; this is not endpoint shortest-path routing.

    # Reversal costs depend on arrival direction. Collapsing all arrivals at
    # a candidate into one cost loses the globally best continuation. Retain
    # the last moving edge and its GPS origin, including across stationary snaps.
    states = {(index, None, None): observation_cost(gap, 0)
              for index, (_, gap) in enumerate(candidate_layers[0])}
    history = []
    scored_transitions = pruned_transitions = shape_vertices = 0
    for layer_index in range(1, len(candidate_layers)):
        trace_start, trace_end = trace[layer_index - 1], trace[layer_index]
        observed_distance = length_metres(trace_start, trace_end)
        max_path_distance = max(120.0, observed_distance * 4.0 + 60.0)
        target_keys = [key for key, _ in candidate_layers[layer_index]]
        sources = {state[0] for state in states}
        # The full candidate lattice remains available. Reuse exact geometry
        # calculations within this pair instead of rescoring shared vertices.
        pair_uncertainty = max(uncertainty(layer_index-1), uncertainty(layer_index))
        known_pair = uncertainty(layer_index-1)>0 and uncertainty(layer_index)>0
        score_context = PedestrianPathScoreContext(
            trace_start, trace_end, length_metres, pair_uncertainty, continuity_scale)
        observation_costs = [observation_cost(gap, layer_index)
                             for _, gap in candidate_layers[layer_index]]
        source_costs = {}
        for state, cost in states.items():
            source_costs[state[0]] = min(source_costs.get(state[0], float('inf')), cost)
        # With known accuracies the policy merges arrivals at each candidate.
        # All remaining costs are nonnegative. A lower bound worse than an
        # already connected arrival can therefore never win this state or any
        # future continuation. Unknown-accuracy direction states stay exhaustive.
        ordered_sources = sorted(sources, key=source_costs.get) if known_pair else sources
        paths, scores, best_arrivals = {}, {}, {}
        for index in ordered_sources:
            source_key = candidate_layers[layer_index-1][index][0]
            needed = []
            for current, (key, _) in enumerate(candidate_layers[layer_index]):
                best = best_arrivals.get(current, float('inf'))
                if known_pair and math.isfinite(best):
                    # Network travel cannot be shorter than great-circle
                    # separation. The travel prior alone is a lower bound;
                    # omit shape/distance penalties until a path is known.
                    travel = max(0.0, length_metres(vertices[source_key], vertices[key])-1e-6)
                    lower = observation_costs[current] + travel/continuity_scale
                    if source_costs[index]+lower > best:
                        continue
                needed.append(key)
            pruned_transitions += len(target_keys)-len(needed)
            results = paths_to_targets(source_key, needed, max_path_distance) if needed else {}
            paths[index] = results
            for current, (key, gap) in enumerate(candidate_layers[layer_index]):
                result = results.get(key)
                if result:
                    path, _, distance = result
                    if (known_pair and source_costs[index] + (
                            score_context.lower_bound(distance)+observation_costs[current])
                            > best_arrivals.get(current, float('inf'))):
                        pruned_transitions += 1
                        continue
                    scores[index, current] = score_pedestrian_path(
                        trace_start, trace_end, [vertices[k] for k in path],
                        distance, length_metres,
                        uncertainty=pair_uncertainty, continuity_scale=continuity_scale,
                        prepared=score_context) + observation_costs[current]
                    scored_transitions += 1
                    best_arrivals[current] = min(best_arrivals.get(current, float('inf')),
                                                source_costs[index]+scores[index, current])
        shape_vertices += len(score_context.deviations)
        costs, back = {}, {}
        for prior, cost in states.items():
            previous, arrival, origin = prior
            for current, (key, _) in enumerate(candidate_layers[layer_index]):
                result = paths[previous].get(key)
                if not result or (previous, current) not in scores:
                    continue
                path, _, _ = result
                total = cost + scores[previous, current]
                if known_pair:
                    # The evidence-aware distance likelihood already prices
                    # repeated travel. A second binary turn cost would reward
                    # taking a loop merely to avoid a reversal.
                    state = (current, None, None)
                elif len(path) > 1:
                    if arrival is not None:
                        total += unsupported_reversal_penalty(
                            trace[origin], trace_start, trace_end,
                            [arrival, path[0]], path, vertices, length_metres,
                            uncertainty=max(uncertainty(origin),uncertainty(layer_index-1),uncertainty(layer_index)))
                    state = (current, path[-2], layer_index-1)
                else:
                    state = (current, arrival, origin)
                if total < costs.get(state, float("inf")):
                    costs[state] = total
                    back[state] = (prior, result)
        if not costs:
            if diagnostics is not None:
                connected = [result for results in paths.values() for result in results.values()]
                diagnostics.update(failure_layer=layer_index,
                    observed_distance_m=round(observed_distance, 1),
                    source_candidates=len(sources), target_candidates=len(target_keys),
                    connected_transitions=len(connected),
                    stationary_transitions=sum(len(result[0]) == 1 for result in connected))
            return None
        states = costs
        history.append(back)
    if diagnostics is not None:
        diagnostics.update(scored_transitions=scored_transitions,
                           pruned_transitions=pruned_transitions,
                           shape_vertices=shape_vertices)
    state = min(states, key=states.get)
    selected = []
    for back in reversed(history):
        state, result = back[state]
        selected.append(result)
    route_keys = [candidate_layers[0][state[0]][0]]
    used_feature_ids = []
    for path, feature_ids, _ in reversed(selected):
        route_keys.extend(path[1:])
        used_feature_ids.extend(feature_ids)
    return route_keys, used_feature_ids


def unsupported_reversal_penalty(before, point, after, incoming, outgoing,
                                 vertices, length_metres, uncertainty=0.0):
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
            and min(length_metres(before, point),length_metres(point, after)) >= max(8.0, 2.0*uncertainty)):
        return 0.0
    retraced = length_metres(vertices[outgoing[0]], vertices[outgoing[1]])
    return retraced/max(5.0, uncertainty) if uncertainty else 8.0 + min(8.0, retraced/10.0)


class PedestrianPathScoreContext:
    """Exact pair-local scoring, reusing deviation work across candidate paths.

    Candidate paths share many network vertices. A context belongs to one GPS
    transition only, so neither cache size nor lifetime grows with the journey.
    Coordinates and arithmetic are identical to the scalar scoring formula.
    """

    def __init__(self, trace_start, trace_end, length_metres, uncertainty=0.0,
                 continuity_scale=None):
        self.observed_distance = length_metres(trace_start, trace_end)
        self.uncertainty = uncertainty
        self.continuity_scale = continuity_scale
        self.distance_scale = max(20.0, self.observed_distance * 0.4, uncertainty*2.0)
        mean_latitude = math.radians((trace_start[1] + trace_end[1]) / 2.0)
        self.metres_per_degree_lat = 111_320.0
        self.metres_per_degree_lng = self.metres_per_degree_lat * math.cos(mean_latitude)
        self.start_x = trace_start[0] * self.metres_per_degree_lng
        self.start_y = trace_start[1] * self.metres_per_degree_lat
        end_x = trace_end[0] * self.metres_per_degree_lng
        end_y = trace_end[1] * self.metres_per_degree_lat
        self.segment_x, self.segment_y = end_x-self.start_x, end_y-self.start_y
        self.segment_length_squared = self.segment_x*self.segment_x + self.segment_y*self.segment_y
        self.deviations = {}

    def deviation(self, coordinate):
        key = tuple(coordinate)
        value = self.deviations.get(key)
        if value is not None:
            return value
        longitude, latitude = coordinate
        point_x = longitude * self.metres_per_degree_lng
        point_y = latitude * self.metres_per_degree_lat
        if self.segment_length_squared:
            projection = ((point_x-self.start_x)*self.segment_x
                          + (point_y-self.start_y)*self.segment_y) / self.segment_length_squared
            projection = max(0.0, min(1.0, projection))
            nearest_x = self.start_x + projection*self.segment_x
            nearest_y = self.start_y + projection*self.segment_y
        else:
            nearest_x, nearest_y = self.start_x, self.start_y
        value = math.hypot(point_x-nearest_x, point_y-nearest_y)
        self.deviations[key] = value
        return value

    def score(self, route, route_distance):
        if not route:
            return float("inf")
        uncertainty = self.uncertainty
        distance_penalty = max(0.0, abs(route_distance-self.observed_distance)
                               - 2.0*uncertainty) / self.distance_scale
        travel_prior = (route_distance/self.continuity_scale if self.continuity_scale else
                        route_distance/max(20.0, 2.0*uncertainty) if uncertainty else 0.0)
        deviations = [self.deviation(coordinate) for coordinate in route]
        mean_deviation = max(0.0, sum(deviations)/len(deviations)-uncertainty)
        max_deviation = max(0.0, max(deviations)-2.0*uncertainty)
        shape_penalty = 2.0 * (mean_deviation/max(12.0, uncertainty))**2
        shape_penalty += 1.5 * (max_deviation/max(25.0, uncertainty*2.0))**2
        return distance_penalty + shape_penalty + travel_prior

    def lower_bound(self, route_distance):
        """Original distance/travel terms with the nonnegative shape cost zero."""
        uncertainty = self.uncertainty
        distance_penalty = max(0.0, abs(route_distance-self.observed_distance)
                               - 2.0*uncertainty) / self.distance_scale
        travel_prior = (route_distance/self.continuity_scale if self.continuity_scale else
                        route_distance/max(20.0, 2.0*uncertainty) if uncertainty else 0.0)
        return distance_penalty + travel_prior


def score_pedestrian_path(trace_start, trace_end, route, route_distance,
                          length_metres, uncertainty=0.0, continuity_scale=None,
                          prepared=None):
    """Score the original connected path; optionally reuse its pair-local frame."""
    context = prepared if prepared is not None else PedestrianPathScoreContext(
        trace_start, trace_end, length_metres, uncertainty, continuity_scale)
    return context.score(route, route_distance)
