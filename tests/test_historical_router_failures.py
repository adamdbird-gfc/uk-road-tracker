"""PR #8 regressions against today's real parsing, pacing and split paths.

No public router is contacted. Unlike the higher-level outcome tests these
start below request_match, so an accidental 422 conversion or retry fan-out
cannot be hidden by a mocked osrm_match_chunk/request_match.
"""
import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import httpx
from fastapi import HTTPException

import api
from walking_work import walking_work


class HistoricalRouterFailureTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.patches = [
            patch.object(api, "FOOT_REQUEST_LOCK", asyncio.Lock()),
            patch.object(api, "REFERENCE_MATCH_LOCK", asyncio.Lock()),
            patch.object(api, "foot_next_request_at", 0.0),
            patch.object(api, "foot_cooldown_until", 0.0),
        ]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)
        self.points = [SimpleNamespace(lat=51.5 + i * .0001, lng=-.1) for i in range(8)]

    async def fail_with(self, status, headers=None):
        client = SimpleNamespace(get=AsyncMock(return_value=httpx.Response(
            status, headers=headers, text="upstream temporarily unavailable")))
        with self.assertRaises(HTTPException) as raised:
            await api.match_chunk_resiliently(
                client, self.points, 0, api.FOOT_OSRM_BASE_URL,
                radius_attempts=api.FOOT_RADIUS_ATTEMPTS, retry_no_match=True)
        self.assertEqual(1, client.get.await_count)
        return raised.exception, client

    async def test_429_keeps_retry_after_without_radius_retry_or_recursive_split(self):
        failure, _ = await self.fail_with(429, {"Retry-After": "45"})
        self.assertEqual(503, failure.status_code)
        self.assertEqual("45", failure.headers["Retry-After"])

    async def test_5xx_never_splits_or_returns_empty_successful_geometry(self):
        for status in (500, 502, 503, 504):
            with self.subTest(status=status):
                api.foot_next_request_at = 0.0
                failure, _ = await self.fail_with(status)
                self.assertEqual(502, failure.status_code)

    async def test_rate_limited_followup_is_blocked_before_another_router_request(self):
        _, client = await self.fail_with(429, {"Retry-After": "45"})
        with self.assertRaises(HTTPException) as raised:
            await api.match_chunk_resiliently(
                client, self.points, 0, api.FOOT_OSRM_BASE_URL,
                radius_attempts=api.FOOT_RADIUS_ATTEMPTS, retry_no_match=True)
        self.assertEqual(503, raised.exception.status_code)
        self.assertGreater(int(raised.exception.headers["Retry-After"]), 0)
        self.assertEqual(1, client.get.await_count)

    async def test_legacy_walking_client_still_has_a_total_fallback_deadline(self):
        # Keep the production 55s contract while making the stall deterministic
        # and fast. The real wait_for cancellation executes, with no real I/O.
        wait_for = asyncio.wait_for
        observed = []

        async def short_deadline(operation, timeout):
            observed.append(timeout)
            return await wait_for(operation, .01 if timeout == 55.0 else timeout)

        async def stalled(*args, **kwargs):
            await asyncio.sleep(1)

        payload = api.MatchRequest(points=[vars(p) for p in self.points], walking_recovery=False)
        with patch.object(api, "match_pedestrian_reference", return_value=None), \
                patch.object(api, "match_payload", new=AsyncMock(side_effect=stalled)), \
                patch.object(api.asyncio, "wait_for", side_effect=short_deadline):
            with self.assertRaises(HTTPException) as raised:
                await api.match_walking_activity(payload)
        self.assertEqual(504, raised.exception.status_code)
        self.assertIn(55.0, observed)
        self.assertIsNone(walking_work.get())
