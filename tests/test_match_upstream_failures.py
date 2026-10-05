import unittest
from types import SimpleNamespace

from fastapi import HTTPException

import api


class FakeResponse:
    def __init__(self, status_code, headers=None):
        self.status_code = status_code
        self.headers = headers or {}
        self.text = "upstream temporarily unavailable"

    def json(self):
        return {"message": self.text}


class FakeClient:
    def __init__(self, response):
        self.response = response
        self.calls = 0

    async def get(self, url, params=None):
        self.calls += 1
        return self.response


class UpstreamFailureTests(unittest.IsolatedAsyncioTestCase):
    def make_points(self, count=8):
        return [SimpleNamespace(lat=51.5 + i * 0.0001, lng=-0.1) for i in range(count)]

    async def test_rate_limit_is_returned_without_recursive_route_splitting(self):
        client = FakeClient(FakeResponse(429, {"Retry-After": "45"}))

        with self.assertRaises(HTTPException) as raised:
            await api.match_chunk_resiliently(
                client,
                self.make_points(),
                0,
                api.FOOT_OSRM_BASE_URL,
                radius_attempts=api.FOOT_RADIUS_ATTEMPTS,
                retry_no_match=True,
            )

        self.assertEqual(503, raised.exception.status_code)
        self.assertEqual("45", raised.exception.headers["Retry-After"])
        self.assertEqual(1, client.calls)

    async def test_upstream_server_error_is_not_split_into_more_requests(self):
        client = FakeClient(FakeResponse(502))

        with self.assertRaises(HTTPException) as raised:
            await api.match_chunk_resiliently(
                client,
                self.make_points(),
                0,
                api.FOOT_OSRM_BASE_URL,
                radius_attempts=api.FOOT_RADIUS_ATTEMPTS,
                retry_no_match=True,
            )

        self.assertEqual(502, raised.exception.status_code)
        self.assertEqual(1, client.calls)


if __name__ == "__main__":
    unittest.main()
