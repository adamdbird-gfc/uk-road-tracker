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
            with self.assertRaises(HTTPException) as failure:await self.call(n=4,error=HTTPException(code,'temporary failure'))
            self.assertEqual(failure.exception.status_code,code)
    async def test_invalid_input_and_old_clients_keep_failure_semantics(self):
        for recovery,error in [(False,HTTPException(422,'The matcher returned no route geometry.')),
                               (True,HTTPException(422,'Invalid request'))]:
            with self.assertRaises(HTTPException):await self.call(n=4,recovery=recovery,error=error)

    async def test_upstream_500_is_not_downgraded_to_no_match(self):
        from types import SimpleNamespace
        with patch.object(api,'request_match',new=AsyncMock(return_value=(SimpleNamespace(status_code=500),{'code':'Error'}))):
            with self.assertRaises(HTTPException) as failure:
                await api.osrm_match_chunk(None,[],0,'unused')
            self.assertEqual(failure.exception.status_code,502)

    async def test_two_fixes_do_not_invent_a_path_or_contact_router(self):
        with patch.object(api,'match_pedestrian_reference') as reference, patch.object(api,'match_payload',new=AsyncMock()) as router:
            result=await api.match_walking_activity(api.MatchRequest(points=[{'lat':51.44,'lng':.37}]*2,walking_recovery=True))
            self.assertEqual(result['walking_outcome'],'insufficient_detail')
            reference.assert_not_called();router.assert_not_awaited()

class WalkingWorkBudgetTests(unittest.IsolatedAsyncioTestCase):
    async def test_recursive_no_match_is_bounded_without_fabricated_geometry(self):
        points=[api.Point(lat=48.87+i*.00001,lng=2.77) for i in range(8)]
        budget=[3]
        with patch.object(api,'osrm_match_chunk',new=AsyncMock(side_effect=HTTPException(422,'NoMatch'))) as router:
            data,radius,failures=await api.match_chunk_resiliently(None,points,0,api.FOOT_OSRM_BASE_URL,subdivision_budget=budget)
        self.assertEqual(router.await_count,4)
        self.assertEqual(data['matchings'],[])
        self.assertTrue(any(f['detail']=='pedestrian_work_limit' for f in failures))
        self.assertEqual(budget,[0])

    async def test_exhausted_work_is_retryable_not_a_terminal_off_path_outcome(self):
        points=[{'lat':48.87+i*.00001,'lng':2.77} for i in range(40)]
        with patch.object(api,'match_pedestrian_reference',return_value=None), \
             patch.object(api,'osrm_match_chunk',new=AsyncMock(side_effect=HTTPException(422,'NoMatch'))) as router, \
             patch.object(api.asyncio,'sleep',new=AsyncMock()):
            with self.assertRaises(HTTPException) as failed:
                await api.match_walking_activity(api.MatchRequest(points=points,walking_recovery=True))
        self.assertEqual(failed.exception.status_code,503)
        self.assertLessEqual(router.await_count,22)

    async def test_verified_sibling_geometry_survives_work_exhaustion(self):
        from types import SimpleNamespace
        points=[{'lat':48.87+i*.00001,'lng':2.77} for i in range(40)]
        success={'matchings':[{'geometry':{'type':'LineString','coordinates':[[2.77,48.87],[2.77,48.87001]]},'distance':1.1,'legs':[]}],
                 'tracepoints':[{'waypoint_index':i,'matchings_index':0} for i in range(8)]}
        errors=[(success,20)]+[HTTPException(422,'NoMatch')]*40
        with patch.object(api,'osrm_match_chunk',new=AsyncMock(side_effect=errors)),patch.object(api.asyncio,'sleep',new=AsyncMock()):
            result=await api.match_payload(api.MatchRequest(points=points,walking_recovery=True),api.FOOT_OSRM_BASE_URL,False,disjoint_chunks=True)
        self.assertEqual(len(result['geojson']['features']),1)
        self.assertEqual(result['matched_tracepoints'],8)
        self.assertTrue(any(f['detail']=='pedestrian_work_limit' for f in result['failed_sections']))

    async def test_ordinary_long_successful_walk_is_not_truncated_by_retry_budget(self):
        points=[{'lat':51+i*.00001,'lng':.3} for i in range(200)]
        async def success(client,chunk,*args):
            return {'matchings':[{'geometry':{'type':'LineString','coordinates':[[p.lng,p.lat] for p in chunk]},'distance':10,'legs':[]}],
                    'tracepoints':[{'waypoint_index':i,'matchings_index':0} for i in range(len(chunk))]},20
        with patch.object(api,'osrm_match_chunk',new=AsyncMock(side_effect=success)) as router:
            result=await api.match_payload(api.MatchRequest(points=points,walking_recovery=True),api.FOOT_OSRM_BASE_URL,False,disjoint_chunks=True)
        self.assertEqual(result['matched_tracepoints'],200)
        self.assertEqual(result['failed_sections'],[])
        self.assertGreater(router.await_count,16)

class WalkingTemporalBudgetTests(unittest.IsolatedAsyncioTestCase):
    async def test_stalled_upstream_returns_recoverable_failure_with_no_geometry(self):
        import asyncio
        from walking_work import WalkingWorkBudget, walking_work
        work=WalkingWorkBudget(seconds=.03,call_seconds=.01)
        async def slow(*args): await asyncio.sleep(1)
        token=walking_work.set(work)
        try:
            with patch.object(api,'router_get',new=AsyncMock(side_effect=slow)):
                data,radius,failures=await api.match_chunk_resiliently(None,[api.Point(lat=51,lng=.3)]*4,0,api.FOOT_OSRM_BASE_URL)
            self.assertEqual(data['matchings'],[])
            self.assertEqual(failures[0]['detail'],'pedestrian_upstream_timeout')
            self.assertEqual(work.calls,1)
        finally: walking_work.reset(token)

    async def test_call_limit_avoids_duplicate_upstream_work(self):
        from walking_work import WalkingWorkBudget, WalkingWorkLimit
        work=WalkingWorkBudget(max_calls=1)
        operation=AsyncMock(return_value='ok')
        self.assertEqual(await work.call(operation),'ok')
        with self.assertRaises(WalkingWorkLimit):await work.call(operation)
        self.assertEqual(operation.await_count,1)

    async def test_request_local_state_is_cleared_after_error(self):
        from walking_work import walking_work
        with patch.object(api,'_match_walking_activity',new=AsyncMock(side_effect=HTTPException(504,'timeout'))):
            with self.assertRaises(HTTPException):await api.match_walking_activity(api.MatchRequest(points=[{'lat':51,'lng':.3}]*4,walking_recovery=True))
        self.assertIsNone(walking_work.get())
