import math
import unittest

from pedestrian_matching import select_smoothed_pedestrian_snaps


def distance_metres(a, b):
    lat1, lat2 = math.radians(a[1]), math.radians(b[1])
    delta_lat = math.radians(b[1] - a[1])
    delta_lng = math.radians(b[0] - a[0])
    term = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lng / 2) ** 2
    return 6371008.8 * 2 * math.asin(min(1.0, math.sqrt(term)))


class PedestrianSnapTests(unittest.TestCase):
    def test_trace_direction_can_outweigh_a_nearer_crossing_snap(self):
        start = (0, 0)
        end = (2, 0)
        crossing = (1, 1)
        on_route = (1, 0)
        vertices = {
            start: [0.0005, -0.0001],
            end: [0.0015, -0.0001],
            crossing: [0.001, 0.0],
            on_route: [0.001, -0.0001],
        }
        trace = [[0.0005, 0.0], [0.001, 0.0], [0.0015, 0.0]]
        candidates = [
            [(start, 0.0)],
            [(crossing, 0.0), (on_route, distance_metres(vertices[crossing], vertices[on_route]))],
            [(end, 0.0)],
        ]

        selected = select_smoothed_pedestrian_snaps(trace, candidates, vertices, distance_metres)

        self.assertEqual([start, on_route, end], selected)

    def test_ordered_trace_preserves_a_genuine_out_and_back(self):
        route = [(0, 0), (1, 0), (2, 0), (1, 1), (1, 0), (0, 0)]
        vertices = {
            (0, 0): [0.000, 0.000],
            (1, 0): [0.001, 0.000],
            (2, 0): [0.002, 0.000],
            (1, 1): [0.001, 0.001],
        }
        trace = [vertices[key] for key in route]
        candidates = [[(key, 0.0)] for key in route]

        selected = select_smoothed_pedestrian_snaps(trace, candidates, vertices, distance_metres)

        self.assertEqual(route, selected)


if __name__ == "__main__":
    unittest.main()
