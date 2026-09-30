import math
import unittest

from pedestrian_matching import select_trajectory_paths


def distance_metres(a, b):
    lat1, lat2 = math.radians(a[1]), math.radians(b[1])
    delta_lat = math.radians(b[1] - a[1])
    delta_lng = math.radians(b[0] - a[0])
    term = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lng / 2) ** 2
    return 6371008.8 * 2 * math.asin(min(1.0, math.sqrt(term)))


class PedestrianPathTests(unittest.TestCase):
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
