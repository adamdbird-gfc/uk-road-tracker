import copy
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from road_distance_evidence import supported_road_sections


def points(xs):
    return [SimpleNamespace(lat=0, lng=x/111195) for x in xs]


def response(xs, distances, indices=None):
    indices = indices or list(range(len(xs)))
    trace = [None]*len(xs)
    for waypoint, index in enumerate(indices):
        trace[index] = dict(matchings_index=0, waypoint_index=waypoint,
                            location=[xs[index]/111195, 0])
    legs = []
    for i, distance in enumerate(distances):
        coords = [[xs[index]/111195, 0] for index in indices[i:i+2]]
        legs.append(dict(distance=distance, duration=10, steps=[dict(
            ref='A1' if distance <= 1000 else 'A999', name='Observed' if distance <= 1000 else 'Unsupported',
            distance=distance, geometry=dict(type='LineString', coordinates=coords))]))
    return dict(code='Ok', tracepoints=trace, matchings=[dict(
        distance=sum(distances), confidence=.9, legs=legs,
        geometry=dict(type='LineString', coordinates=[[x/111195, 0] for x in xs]))])


class RoadDistanceEvidenceTests(unittest.TestCase):
    def test_excessive_leg_is_an_explicit_gap_and_input_is_preserved(self):
        data=response([0,100,200,300], [100,3000,100]); original=copy.deepcopy(data)
        kept, failures=supported_road_sections(data, points([0,100,200,300]), 9)
        self.assertEqual(original,data)
        self.assertEqual([100,100], [m['distance'] for m in kept['matchings']])
        self.assertEqual([(9+1,9+2)], [(f['start_point_index'],f['end_point_index']) for f in failures])
        self.assertEqual('road_distance_exceeds_observations',failures[0]['detail'])
        self.assertEqual([0,0,1,1], [p['matchings_index'] for p in kept['tracepoints']])
        self.assertEqual([0,1,0,1], [p['waypoint_index'] for p in kept['tracepoints']])
        self.assertNotIn('Unsupported',str(kept['matchings']))

    def test_observed_circulation_uses_all_intermediate_gps_evidence(self):
        # Endpoints coincide, but intermediate fixes establish a genuine loop.
        data=response([0,200,400,200,0], [800], indices=[0,4])
        kept, failures=supported_road_sections(data, points([0,200,400,200,0]))
        self.assertIs(data,kept)
        self.assertEqual([],failures)

    def test_normal_turn_allowance_is_not_tightened(self):
        data=response([0,1000], [1399])
        self.assertEqual((data,[]),supported_road_sections(data,points([0,1000])))

    def test_multiple_bad_legs_do_not_retain_unconnected_geometry(self):
        data=response([0,10,20], [2000,3000])
        kept, failures=supported_road_sections(data,points([0,10,20]))
        self.assertEqual([],kept['matchings'])
        self.assertEqual([None,None,None],kept['tracepoints'])
        self.assertEqual(2,len(failures))

    def test_unverifiable_upstream_attribution_is_not_guessed(self):
        for mutation in ('trace_count','waypoint','nan','geometry_gap'):
            data=response([0,100,200,300],[100,3000,100])
            if mutation=='trace_count':data['tracepoints'].pop()
            if mutation=='waypoint':data['tracepoints'][2]['waypoint_index']=1
            if mutation=='nan':data['matchings'][0]['legs'][1]['distance']=float('nan')
            if mutation=='geometry_gap':
                leg=data['matchings'][0]['legs'][0]
                leg['steps'].append(dict(geometry=dict(type='LineString',coordinates=[[9,9],[10,10]])))
            kept,failures=supported_road_sections(data,points([0,100,200,300]))
            self.assertIs(data,kept)
            self.assertEqual([],failures)

    def test_repeated_application_is_stable(self):
        data=response([0,100,200,300],[100,3000,100])
        first,_=supported_road_sections(data,points([0,100,200,300]))
        second,failures=supported_road_sections(first,points([0,100,200,300]))
        self.assertEqual(first,second)
        self.assertEqual([],failures)


class RoadOnlyIntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def test_native_partial_excludes_bad_road_from_every_coverage_collection(self):
        import api
        coords=[0,100,200,300]
        payload=api.MatchRequest(points=[dict(lat=p.lat,lng=p.lng) for p in points(coords)],road_recovery=True)
        with patch.object(api,'osrm_match_chunk',new=AsyncMock(return_value=(response(coords,[100,3000,100]),20))):
            result=await api.match_journey(payload)
        self.assertEqual(200,result['matched_distance_m'])
        self.assertTrue(result['matched_distance_is_deduplicated'])
        self.assertEqual(4,result['points_sent_to_matcher'])
        self.assertEqual(1,len(result['failed_sections']))
        self.assertEqual(2,len(result['geojson']['features']))
        for field in ('geojson','road_geojson','a_road_geojson','motorway_geojson'):
            self.assertNotIn('Unsupported',str(result[field]))
            self.assertNotIn('A999',str(result[field]))

    async def test_walking_cycling_and_legacy_road_do_not_apply_native_guard(self):
        import api
        payload=api.MatchRequest(points=[dict(lat=p.lat,lng=p.lng) for p in points([0,10])])
        for base in (api.OSRM_BASE_URL,api.FOOT_OSRM_BASE_URL,api.CYCLE_OSRM_BASE_URL):
            with patch.object(api,'supported_road_sections',side_effect=AssertionError('road guard leaked')),\
                 patch.object(api,'osrm_match_chunk',new=AsyncMock(return_value=(response([0,10],[3000]),20))):
                result=await api.match_payload(payload,base,include_motorways=False)
            self.assertEqual(3000,result['matched_distance_m'])


if __name__=='__main__':
    unittest.main()
