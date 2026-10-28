"""Exact dominance and bounded geometry-work controls for normal walking."""
import time
import unittest

from pedestrian_matching import (PedestrianGraphPaths, PedestrianPathScoreContext,
                                 score_pedestrian_path, select_trajectory_paths)
from test_pedestrian_matching import distance_metres


class PedestrianWorkBudgetTests(unittest.TestCase):
    def select_branch(self, accuracies):
        vertices={'s':(0,51), 'a':(.001,51), 'b':(.001,51.0002), 'e':(.001,51.001)}
        for i in range(24):
            vertices['bad'+str(i)]=(0,51.001+i*.0001)
        edges=[('s','a'),('s','b'),('b','e')]+[('s','bad'+str(i)) for i in range(24)]
        graph={key:[] for key in vertices}
        for a,b in edges:
            distance=distance_metres(vertices[a],vertices[b])
            graph[a].append((b,distance,a+b));graph[b].append((a,distance,a+b))
        trace=[vertices['s'],vertices['a'],vertices['e']]
        layers=[[('s',0)]+[(key,distance_metres(vertices['s'],point))
                           for key,point in vertices.items() if key.startswith('bad')],
                [('a',0),('b',distance_metres(vertices['a'],vertices['b']))],[('e',0)]]
        search=PedestrianGraphPaths(graph,time.monotonic()+20)
        queried=[]
        def paths(start,targets,limit):
            queried.append(start)
            return search(start,targets,limit)
        diagnostics={}
        result=select_trajectory_paths(trace,layers,vertices,distance_metres,paths,
                                       diagnostics,accuracies)
        return result,queried,diagnostics

    def test_dominated_sources_do_not_expand_but_later_supported_turn_survives(self):
        result,queried,diagnostics=self.select_branch([3,25,3])
        # B is slightly more expensive at the middle fix, but is the globally
        # supported branch. Only arrivals to the SAME candidate may dominate.
        self.assertEqual(['s','b','e'],result[0])
        self.assertFalse(any(key.startswith('bad') for key in queried))
        self.assertGreaterEqual(diagnostics['pruned_transitions'],48)

    def test_unknown_accuracy_keeps_direction_dependent_arrivals_exhaustive(self):
        _,queried,_=self.select_branch(None)
        self.assertTrue(all('bad'+str(i) in queried for i in range(24)))

    def test_pair_cache_reuses_shared_vertices_and_isolated_frames(self):
        before,after=(0,51),(.001,51)
        route=[before,(.0005,51.0001),after]
        calls=[]
        def distance(a,b):
            calls.append((a,b));return distance_metres(a,b)
        context=PedestrianPathScoreContext(before,after,distance,12,20)
        expected=score_pedestrian_path(before,after,route,80,distance_metres,12,20)
        for _ in range(1000):
            self.assertEqual(expected,context.score(route,80))
        self.assertEqual(1,len(calls))
        self.assertEqual(3,len(context.deviations))
        self.assertLessEqual(context.lower_bound(80),expected)
        other=PedestrianPathScoreContext(before,(.001,51.001),distance_metres,12,20)
        self.assertNotEqual(context.score(route,80),other.score(route,80))
        self.assertEqual(3,len(context.deviations))

    def test_geometric_bound_cannot_exclude_stationary_or_real_retrace_costs(self):
        for before,after,route,distance in [
                ((0,51),(0,51),[(0,51)],0),
                ((0,51),(.001,51),[(0,51),(.002,51),(.001,51)],210),
                ((0,51),(.001,51),[(0,51),(.0005,51.001),(.001,51)],250)]:
            for accuracy in (0,3,25,50):
                context=PedestrianPathScoreContext(before,after,distance_metres,accuracy,20)
                self.assertLessEqual(context.lower_bound(distance),context.score(route,distance))


if __name__=='__main__':
    unittest.main()
