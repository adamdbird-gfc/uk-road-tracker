import asyncio
import types
import unittest
from unittest.mock import AsyncMock, patch
import httpx
import api

class CyclingTests(unittest.IsolatedAsyncioTestCase):
    async def test_endpoint_uses_bicycle_graph_and_holds_back_unverified_road_credit(self):
        payload=api.MatchRequest(points=[api.Point(lat=51.44,lng=.37),api.Point(lat=51.441,lng=.371)])
        result={"road_geojson":{"type":"FeatureCollection","features":[{"properties":{"road_ref":"A2"}}]},"motorway_geojson":{"features":[]},"a_road_geojson":{"features":[]}}
        mock=AsyncMock(return_value=result)
        with patch.object(api,'match_payload',mock):
            output=await api.match_cycling_activity(payload)
        self.assertEqual(api.CYCLE_OSRM_BASE_URL,mock.call_args.args[1])
        self.assertFalse(mock.call_args.kwargs['include_motorways'])
        self.assertTrue(mock.call_args.kwargs['deduplicate_distance'])
        self.assertFalse(mock.call_args.kwargs.get('road_recovery',False))
        self.assertEqual('cycling',output['matching_mode'])
        self.assertEqual([],output['road_geojson']['features'])
        self.assertEqual('A2',output['cycling_geojson']['features'][0]['properties']['road_ref'])
    async def test_timeout_is_actionable(self):
        payload=api.MatchRequest(points=[api.Point(lat=51,lng=0),api.Point(lat=51.001,lng=0)])
        with patch.object(api,'match_payload',AsyncMock(side_effect=asyncio.TimeoutError)):
            with self.assertRaises(api.HTTPException) as caught: await api.match_cycling_activity(payload)
        self.assertEqual(504,caught.exception.status_code)
        self.assertIn('saved GPS route',caught.exception.detail)
    async def test_overlap_distance_is_counted_once(self):
        points=[api.Point(lat=51+i*.00001,lng=0) for i in range(90)]
        async def response(client,chunk,radius,base):
            n=len(chunk)
            return types.SimpleNamespace(status_code=200,headers={},text=''),{
                'code':'Ok','tracepoints':[{'matchings_index':0,'waypoint_index':i,'location':[p.lng,p.lat]} for i,p in enumerate(chunk)],
                'matchings':[{'distance':(n-1)*10,'geometry':{'type':'LineString','coordinates':[[p.lng,p.lat] for p in chunk]},
                              'legs':[{'distance':10,'steps':[]} for _ in range(n-1)]}]}
        with patch.object(api,'request_match',response):
            output=await api.match_payload(api.MatchRequest(points=points),api.CYCLE_OSRM_BASE_URL,False,chunk_size=80,deduplicate_distance=True)
        self.assertEqual(90,output['matched_tracepoints'])
        self.assertEqual(890,output['matched_distance_m'])
        self.assertTrue(output['matched_distance_is_deduplicated'])
    async def test_bike_rate_limit_does_not_block_foot_router(self):
        client=types.SimpleNamespace(get=AsyncMock(return_value=types.SimpleNamespace(status_code=429,headers={'Retry-After':'120'})))
        with patch.object(api,'cycle_next_request_at',0),patch.object(api,'cycle_cooldown_until',0):
            await api.router_get(client,'unused',{},api.CYCLE_OSRM_BASE_URL)
            with self.assertRaises(api.HTTPException) as caught: await api.router_get(client,'unused',{},api.CYCLE_OSRM_BASE_URL)
            self.assertEqual(503,caught.exception.status_code)
            await api.router_get(client,'unused',{},api.OSRM_BASE_URL)
            self.assertEqual(2,client.get.await_count)
    def test_point_limits_and_rate_guard(self):
        self.assertIn('/match-cycling',api.RATE_LIMITED_PATHS)
        from pydantic import ValidationError
        with self.assertRaises(ValidationError): api.MatchRequest(points=[{'lat':51,'lng':0}]*501)
        with self.assertRaises(ValidationError): api.MatchRequest(points=[{'lat':91,'lng':0}]*2)

if __name__=='__main__': unittest.main()
