import unittest
from unittest.mock import patch, AsyncMock
from fastapi import HTTPException
import api


class WalkingOutcomeTests(unittest.IsolatedAsyncioTestCase):
    async def call(self,n=2,recovery=True,error=None):
        points=[{"lat":51.44+i*.0001,"lng":.37} for i in range(n)]
        with patch.object(api,'match_pedestrian_reference',return_value=None), \
             patch.object(api,'match_payload',new=AsyncMock(side_effect=error,return_value={"status":"ok"})):
            return await api.match_walking_activity(api.MatchRequest(points=points,walking_recovery=recovery))
    async def test_sparse_no_geometry_is_recorded_without_inventing_route(self):
        result=await self.call(error=HTTPException(422,'The matcher returned no route geometry.'))
        self.assertEqual(result['walking_outcome'],'insufficient_detail')
        self.assertEqual(result['geojson']['features'],[])
        self.assertEqual(result['unmatched_point_indices'],[0,1])
    async def test_longer_unmatchable_walk_has_honest_no_route_outcome(self):
        result=await self.call(n=10,error=HTTPException(422,'Router NoMatch'))
        self.assertEqual(result['walking_outcome'],'no_route_found')
        self.assertEqual(result['matched_tracepoints'],0)
    async def test_rate_limit_and_server_errors_are_not_reported_as_off_path(self):
        for code in [429,502,503,504]:
            with self.assertRaises(HTTPException) as failure:await self.call(error=HTTPException(code,'temporary failure'))
            self.assertEqual(failure.exception.status_code,code)
    async def test_invalid_input_and_old_clients_keep_failure_semantics(self):
        for recovery,error in [(False,HTTPException(422,'The matcher returned no route geometry.')),
                               (True,HTTPException(422,'Invalid request'))]:
            with self.assertRaises(HTTPException):await self.call(recovery=recovery,error=error)

    async def test_upstream_500_is_not_downgraded_to_no_match(self):
        from types import SimpleNamespace
        with patch.object(api,'request_match',new=AsyncMock(return_value=(SimpleNamespace(status_code=500),{'code':'Error'}))):
            with self.assertRaises(HTTPException) as failure:
                await api.osrm_match_chunk(None,[],0,'unused')
            self.assertEqual(failure.exception.status_code,502)
