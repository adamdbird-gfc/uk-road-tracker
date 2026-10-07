"""Exercise production routing functions with deterministic, GPS-free responses.

AST loading avoids importing database startup dependencies for these unit tests.
No public router or personal journey is contacted by the tests.
"""
import ast
import math
import asyncio
import pathlib
import types
import unittest


class HTTPException(Exception):
    def __init__(self, status_code, detail, headers=None):
        super().__init__(detail)
        self.status_code, self.detail, self.headers = status_code, detail, headers


class NetworkError(Exception):
    pass


class Client:
    def __init__(self, **kwargs):
        pass
    async def __aenter__(self):
        return self
    async def __aexit__(self, *args):
        return False


def load_functions():
    path = pathlib.Path(__file__).resolve().parents[1] / "api.py"
    tree = ast.parse(path.read_text())
    names = {"osrm_match_chunk", "matched_road_legs", "supported_road_recovery_snap", "match_chunk_resiliently", "chunk_points", "match_payload"}
    nodes = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in names]
    namespace = dict(asyncio=asyncio, math=math, HTTPException=HTTPException, RADIUS_ATTEMPTS=[20, 10, 5],
                     OSRM_CHUNK_SIZE=8, OSRM_CHUNK_OVERLAP=2, CYCLE_OSRM_BASE_URL="bike", MatchRequest=object,
                     httpx=types.SimpleNamespace(AsyncClient=Client, HTTPError=NetworkError),
                     motorway_refs=lambda ref: [], a_road_refs=lambda ref: [])
    exec(compile(ast.Module(body=nodes, type_ignores=[]), str(path), "exec"), namespace)
    return namespace


class RecoveryTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.api = load_functions()
        self.calls = []
        self.respond = lambda points, radius: "NoMatch"
        async def request(client, points, radius, base_url):
            self.calls.append((list(points), radius))
            code = self.respond(points, radius)
            status = 429 if code == "RateLimit" else 200
            data = {"code": code}
            if code == "Ok":
                data.update(tracepoints=[{"location": [0, 0]} for _ in points],
                            matchings=[{"geometry": {"type": "LineString", "coordinates": [[0, 0], [0.001, 0]]},
                                        "distance": 100, "legs": []}])
            elif code == "Partial":
                data.update(code="Ok", tracepoints=[{"location": [0, 0]}, None], matchings=[])
            return types.SimpleNamespace(status_code=status, text=code, headers={}), data
        self.api["request_match"] = request

    async def match(self, points, **kwargs):
        points = [types.SimpleNamespace(lat=0, lng=0, tag=i) for i in points]
        return await self.api["match_chunk_resiliently"](None, points, 0, "unused", **kwargs)

    async def test_recovery_does_not_accept_a_distant_road_snap(self):
        check = self.api["supported_road_recovery_snap"]
        points = [types.SimpleNamespace(lat=0, lng=0)]
        self.assertFalse(check(points, [{"location": [0.001, 0]}]))
        self.assertTrue(check(points, [{"location": [0.0004, 0]}]))

    async def test_leg_indices_follow_waypoints_and_keep_chunk_offsets(self):
        data = {"matchings": [{"legs": [{"distance": 100}, {"distance": 200}]}],
                "tracepoints": [{"matchings_index": 0, "waypoint_index": 0}, None,
                                {"matchings_index": 0, "waypoint_index": 1},
                                {"matchings_index": 0, "waypoint_index": 2}]}
        legs, complete = self.api["matched_road_legs"](data, 6)
        self.assertTrue(complete)
        self.assertEqual([(6, 8, 100), (8, 9, 200)],
                         [(l["start_point_index"], l["end_point_index"], l["distance_m"]) for l in legs])
        data["tracepoints"].pop()
        self.assertFalse(self.api["matched_road_legs"](data, 6)[1])

    async def test_overlapping_chunk_leg_distance_is_counted_once(self):
        async def request(client, points, radius, base_url):
            data = {"code": "Ok", "tracepoints": [dict(matchings_index=0, waypoint_index=i, location=[0, 0]) for i in range(len(points))],
                    "matchings": [{"distance": 100 * (len(points) - 1), "geometry": {"type": "LineString", "coordinates": [[0, 0], [0.001, 0]]},
                                   "legs": [dict(distance=100, steps=[]) for _ in range(len(points) - 1)]}]}
            return types.SimpleNamespace(status_code=200), data
        self.api["request_match"] = request
        points = [types.SimpleNamespace(lat=0, lng=0) for i in range(10)]
        result = await self.api["match_payload"](types.SimpleNamespace(points=points), "unused", True, road_recovery=True)
        self.assertEqual(900, result["matched_distance_m"])
        self.assertTrue(result["matched_distance_is_deduplicated"])

    async def test_default_policy_does_not_widen_radius(self):
        _, _, failures = await self.match([0, 1])
        self.assertEqual([20], [r for _, r in self.calls])
        self.assertEqual(1, len(failures))

    async def test_android_recovers_failed_pair_at_40_metres(self):
        self.respond = lambda points, radius: "Ok" if radius >= 40 else "NoMatch"
        data, radius, failures = await self.match([0, 1], road_recovery=True, point_offset=8)
        self.assertEqual([20, 40], [r for _, r in self.calls])
        self.assertEqual([], failures)
        self.assertEqual([0, 1], data["matched_point_indices"])
        self.assertEqual({"start_point_index": 8, "end_point_index": 9, "radius_m": 40}, data["road_recovery"][0])

    async def test_retry_is_bounded_at_60_metres(self):
        _, _, failures = await self.match([0, 1], road_recovery=True)
        self.assertEqual([20, 40, 60], [r for _, r in self.calls])
        self.assertEqual(1, len(failures))

    async def test_second_radius_can_recover(self):
        self.respond = lambda points, radius: "Ok" if radius == 60 else "NoMatch"
        _, radius, failures = await self.match([0, 1], road_recovery=True)
        self.assertEqual(60, radius)
        self.assertEqual([], failures)

    async def test_partial_recovery_is_not_claimed_as_complete(self):
        self.respond = lambda points, radius: "Partial" if radius >= 40 else "NoMatch"
        _, _, failures = await self.match([0, 1], road_recovery=True)
        self.assertEqual(1, len(failures))

    async def test_rate_limit_is_not_split_or_retried_as_bad_gps(self):
        self.respond = lambda points, radius: "RateLimit"
        with self.assertRaises(HTTPException) as caught:
            await self.match([0, 1, 2, 3], road_recovery=True)
        self.assertEqual(503, caught.exception.status_code)
        self.assertEqual(1, len(self.calls))

    async def test_recursive_failure_indices_keep_original_offsets(self):
        _, _, failures = await self.match([0, 1, 2, 3, 4], point_offset=10)
        self.assertEqual([(10, 11), (11, 12), (12, 13), (13, 14)],
                         [(f["start_point_index"], f["end_point_index"]) for f in failures])

    async def test_chunk_overlap_does_not_double_count_matched_points(self):
        self.respond = lambda points, radius: "NoMatch" if any(p.lng == 3 for p in points) else "Ok"
        points = [types.SimpleNamespace(lat=0, lng=i) for i in range(10)]
        result = await self.api["match_payload"](types.SimpleNamespace(points=points), "unused", True)
        self.assertEqual(10, result["input_points"])
        self.assertEqual(9, result["matched_tracepoints"])
        self.assertEqual([3], result["unmatched_point_indices"])
        self.assertEqual([(2, 3), (3, 4)], [(f["start_point_index"], f["end_point_index"]) for f in result["failed_sections"]])


if __name__ == "__main__":
    unittest.main()
