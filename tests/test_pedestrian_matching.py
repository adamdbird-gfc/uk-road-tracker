import math
import random
import time
import unittest
from heapq import nsmallest

from pedestrian_matching import PedestrianVertexIndex, PedestrianGraphPaths, compress_pedestrian_graph, select_trajectory_paths, match_reference_trajectory


def distance_metres(a, b):
    lat1, lat2 = math.radians(a[1]), math.radians(b[1])
    delta_lat = math.radians(b[1] - a[1])
    delta_lng = math.radians(b[0] - a[0])
    term = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lng / 2) ** 2
    return 6371008.8 * 2 * math.asin(min(1.0, math.sqrt(term)))


class PedestrianVertexIndexTests(unittest.TestCase):
    def test_candidates_equal_full_scan_across_uk_and_cell_boundaries(self):
        rng = random.Random(178)
        for longitude, latitude in [(-0.106, 51.514), (0.370, 51.440),
                                    (-6.0, 54.6), (-3.0, 60.8)]:
            vertices = {
                (i, 0): [longitude + rng.uniform(-0.01, 0.01),
                         latitude + rng.uniform(-0.01, 0.01)]
                for i in range(2500)
            }
            index = PedestrianVertexIndex(vertices)
            for point in [(longitude, latitude),
                          (longitude - 1e-10, latitude - 1e-10),
                          (longitude + 1e-10, latitude + 1e-10)]:
                expected = [(key, gap) for gap, key in nsmallest(
                    6, ((distance_metres(point, coord), key)
                        for key, coord in vertices.items())) if gap <= 120.0]
                self.assertEqual(expected, index.nearest(point, distance_metres))

    def test_radius_cutoff_and_missing_nearby_network(self):
        point = (-0.1, 51.5)
        delta = math.degrees(1.0 / 6371008.8)
        vertices = {(0, 0): [point[0], point[1] + 119.999 * delta],
                    (1, 0): [point[0], point[1] + 120.001 * delta]}
        index = PedestrianVertexIndex(vertices)
        self.assertEqual([(0, 0)], [key for key, _ in index.nearest(point, distance_metres)])
        self.assertEqual([], index.nearest((0.37, 51.44), distance_metres))
        self.assertEqual([], PedestrianVertexIndex({}).nearest(point, distance_metres))

    def test_ties_keep_original_key_order(self):
        vertices = {(i, 0): [-0.1, 51.5] for i in reversed(range(10))}
        result = PedestrianVertexIndex(vertices).nearest((-0.1, 51.5), distance_metres)
        self.assertEqual([(i, 0) for i in range(6)], [key for key, _ in result])

    def test_dense_graph_avoids_repeated_full_scan(self):
        vertices = {(i, j): [-0.15 + i * 0.0002, 51.49 + j * 0.0002]
                    for i in range(250) for j in range(200)}
        index = PedestrianVertexIndex(vertices)
        calls = 0

        def counted_distance(a, b):
            nonlocal calls
            calls += 1
            return distance_metres(a, b)

        for i in range(121):
            point = (-0.12 + i * 0.00005, 51.51)
            self.assertEqual(6, len(index.nearest(point, counted_distance)))
        # Work should depend on the nearby cells, not 50,000 vertices per fix.
        self.assertLess(calls, len(vertices) * 121 // 50)


class PedestrianPathTests(unittest.TestCase):
    def test_crowded_disconnected_candidates_recover_connected_street(self):
        vertices = {'start': [0.0, 51.5], 'street': [0.001, 51.5]}
        trace = [vertices['start'], vertices['street']]
        calls = []
        class Index:
            def nearest(self, point, length, count):
                calls.append(count)
                if point == trace[0]: return [('start', 0.0)]
                close = [('isolated'+str(i), float(i)) for i in range(6)]
                return close if count == 6 else close + [('street', 10.0)]
        vertices.update({'isolated'+str(i): [0.001,51.5001] for i in range(6)})
        def paths(start, targets, limit):
            if start == 'start' and 'street' in targets:
                return {'street': (['start','street'], ['connected-road'],
                                   distance_metres(vertices['start'],vertices['street']))}
            return {}
        diagnostics = {}
        result = match_reference_trajectory(trace, Index(), vertices, distance_metres,
                                            paths, time.monotonic()+10, diagnostics)
        self.assertEqual((['start','street'], ['connected-road']), result)
        self.assertEqual(24, diagnostics['candidate_count'])
        self.assertEqual([6,6,24,24], calls)

    def test_recovery_keeps_deadline_and_rejects_real_disconnection(self):
        vertices = {'start': [0.0,51.5], 'end': [0.001,51.5]}
        trace = list(vertices.values())
        class Index:
            def nearest(self, point, length, count):
                return [('start' if point == trace[0] else 'end', 0.0)]
        diagnostics = {}
        result = match_reference_trajectory(trace,Index(),vertices,distance_metres,
                                            lambda *_:{},time.monotonic()+10,diagnostics)
        self.assertIsNone(result)
        self.assertEqual(24,diagnostics['candidate_count'])
        diagnostics = {}
        result = match_reference_trajectory(trace,Index(),vertices,distance_metres,
                                            lambda *_:{},time.monotonic()-1,diagnostics)
        self.assertIsNone(result)
        self.assertTrue(diagnostics['elapsed_limit'])

    def test_dense_fixes_can_share_a_node_before_continuing(self):
        vertices = {'start': [-0.1, 51.5], 'end': [-0.099, 51.5]}
        trace = [vertices['start'], [-0.09998, 51.5], vertices['end']]
        candidates = [[('start', 0.0)], [('start', 1.4)], [('end', 0.0)]]

        def paths(source, targets, _limit):
            target = targets[0]
            if source == target:
                return {target: ([source], [], 0.0)}
            return {target: ([source, target], ['road'],
                             distance_metres(vertices[source], vertices[target]))}

        result = select_trajectory_paths(trace, candidates, vertices, distance_metres, paths)
        self.assertEqual((['start', 'end'], ['road']), result)

    def test_disconnected_network_is_rejected_and_diagnosed(self):
        vertices = {'start': [-0.1, 51.5], 'end': [-0.099, 51.5]}
        diagnostics = {}
        result = select_trajectory_paths(
            list(vertices.values()), [[('start', 0.0)], [('end', 0.0)]],
            vertices, distance_metres, lambda *_: {}, diagnostics=diagnostics)
        self.assertIsNone(result)
        self.assertEqual(1, diagnostics['failure_layer'])
        self.assertEqual(0, diagnostics['connected_transitions'])
        self.assertNotIn('trace', diagnostics)

    def test_two_point_trace_rejects_unobserved_side_road_detour(self):
        start, end = (0.0, 0.0), (0.0008, 0.0)
        nearer_snap, straight_snap, finish = "nearer", "straight", "finish"
        vertices = {
            nearer_snap: [0.00005, 0.00002],
            straight_snap: [0.00008, 0.0],
            finish: [0.00075, 0.0],
            "side_turn": [0.0004, 0.0003],
            "line_mid": [0.0004, 0.0],
        }
        trace = [start, end]
        candidates = [
            [(nearer_snap, 2.5), (straight_snap, 8.9)],
            [(finish, 5.6)],
        ]

        def paths_to_targets(source, targets, _limit):
            if source == nearer_snap:
                route = [nearer_snap, "side_turn", "line_mid", finish]
            else:
                route = [straight_snap, "line_mid", finish]
            distance = sum(
                distance_metres(vertices[a], vertices[b])
                for a, b in zip(route, route[1:])
            )
            return {finish: (route, [], distance)}

        result = select_trajectory_paths(trace, candidates, vertices, distance_metres, paths_to_targets)

        self.assertIsNotNone(result)
        route_keys, _ = result
        self.assertEqual([straight_snap, "line_mid", finish], route_keys)

    def test_observed_turn_is_kept(self):
        start, turn, end = "start", "turn", "end"
        vertices = {start: [0.0, 0.0], turn: [0.0004, 0.0], end: [0.0004, 0.0004]}
        trace = [vertices[start], vertices[turn], vertices[end]]
        candidates = [[(start, 0.0)], [(turn, 0.0)], [(end, 0.0)]]
        graph = {
            (start, turn): [start, turn],
            (turn, end): [turn, end],
        }

        def paths_to_targets(source, targets, _limit):
            route = graph.get((source, targets[0]))
            if not route:
                return {}
            distance = sum(distance_metres(vertices[a], vertices[b]) for a, b in zip(route, route[1:]))
            return {targets[0]: (route, [], distance)}

        result = select_trajectory_paths(trace, candidates, vertices, distance_metres, paths_to_targets)

        self.assertEqual([start, turn, end], result[0])

    def test_genuine_out_and_back_is_preserved(self):
        start, farthest = "start", "farthest"
        vertices = {start: [0.0, 0.0], farthest: [0.001, 0.0]}
        trace = [vertices[start], vertices[farthest], vertices[start]]
        candidates = [[(start, 0.0)], [(farthest, 0.0)], [(start, 0.0)]]

        def paths_to_targets(source, targets, _limit):
            target = targets[0]
            route = [source, target]
            return {target: (route, [], distance_metres(vertices[source], vertices[target]))}

        result = select_trajectory_paths(trace, candidates, vertices, distance_metres, paths_to_targets)

        self.assertEqual([start, farthest, start], result[0])


if __name__ == "__main__":
    unittest.main()


class PedestrianGraphSearchTests(unittest.TestCase):
    def chain(self, count):
        graph = {key: [] for key in range(count)}
        for key in range(count-1):
            graph[key].append((key+1, 1.0, 'road'+str(key)))
            graph[key+1].append((key, 1.0, 'road'+str(key)))
        return graph

    def test_contracted_chain_restores_geometry_and_feature_order_both_directions(self):
        graph = self.chain(101)
        compact = compress_pedestrian_graph(graph, {0,50,100}, time.monotonic()+10)
        self.assertEqual(3, len(compact))
        search = PedestrianGraphPaths(compact, time.monotonic()+10)
        forward = search(0,[100],100)[100]
        reverse = search(100,[0],100)[0]
        self.assertEqual(list(range(101)),forward[0])
        self.assertEqual(['road'+str(i) for i in range(100)],forward[1])
        self.assertEqual(forward[0][::-1],reverse[0])
        self.assertEqual(forward[1][::-1],reverse[1])
        self.assertLessEqual(search.settled_nodes,6)

    def test_cached_frontier_extends_and_still_obeys_each_layer_distance_bound(self):
        graph = self.chain(21)
        compact = compress_pedestrian_graph(graph, {0,10,20}, time.monotonic()+10)
        search = PedestrianGraphPaths(compact,time.monotonic()+10)
        self.assertEqual({},search(0,[20],5))
        self.assertEqual(20.0,search(0,[20],25)[20][2])
        self.assertEqual({},search(0,[20],5))
        self.assertEqual(1,search.searches)
        self.assertEqual(2,search.cache_hits)

    def test_disconnected_targets_do_not_force_search_of_entire_component(self):
        graph = self.chain(1000)
        graph[2000]=[(2001,1.0,'isolated')]
        graph[2001]=[(2000,1.0,'isolated')]
        search = PedestrianGraphPaths(graph,time.monotonic()+10)
        paths = search(0,[2,2000],900)
        self.assertEqual({2},set(paths))
        self.assertEqual(3,search.settled_nodes)

    def test_cache_is_bounded_and_expired_deadline_never_searches(self):
        graph = self.chain(20)
        search = PedestrianGraphPaths(graph,time.monotonic()+10,max_cache_nodes=5)
        self.assertIn(19,search(0,[19],30))
        self.assertLessEqual(sum(len(s['distances']) for s in search.cache.values()),5)
        expired = PedestrianGraphPaths(graph,time.monotonic()-1)
        self.assertEqual({},expired(0,[19],30))
        self.assertEqual(0,expired.settled_nodes)

    def test_cycles_parallel_edges_and_candidate_vertices_are_retained(self):
        graph={0:[(1,1,'short'),(1,2,'long'),(2,3,'direct')],
               1:[(0,1,'short'),(0,2,'long'),(2,1,'onward')],
               2:[(0,3,'direct'),(1,1,'onward')]}
        compact=compress_pedestrian_graph(graph,{0,2},time.monotonic()+10)
        search=PedestrianGraphPaths(compact,time.monotonic()+10)
        self.assertEqual(([0,1,2],['short','onward'],2),search(0,[2],10)[2])
