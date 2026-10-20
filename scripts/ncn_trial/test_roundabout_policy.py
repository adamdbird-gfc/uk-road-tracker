import unittest
from .roundabout_policy import review_roundabouts
from .coverage import Section,TrialReference,TrialLedger,Evidence
from shapely.geometry import LineString


def way(nodes,ring=False):return {'nodes':nodes,'tags':{'junction':'roundabout'} if ring else {}}


class RoundaboutPolicyTests(unittest.TestCase):
    def test_single_and_split_way_cycles_have_same_two_arcs(self):
        for members in [{1:way([1,2,3,4,1],True)},{1:way([1,2,3],True),2:way([3,4,1],True)}]:
            members.update({9:way([10,1]),10:way([3,11])})
            result=review_roundabouts(members)['reviewed'][0]
            self.assertEqual(result['policy'],'either_two_gate_arc')
            self.assertEqual([len(b) for b in result['branches']],[2,2])

    def test_linear_arc_is_not_invented_into_full_circle(self):
        result=review_roundabouts({1:way([1,2,3],True),9:way([10,1]),10:way([3,11])})
        self.assertEqual(result['reviewed'][0]['policy'],'linear_member_arc')

    def test_general_circuit_or_three_gates_does_not_shorten(self):
        self.assertEqual(review_roundabouts({1:way([1,2,3,1])})['reviewed'],[])
        result=review_roundabouts({1:way([1,2,3,4,1],True),9:way([10,1]),10:way([3,11]),11:way([2,12])})
        self.assertFalse(result['reviewed']);self.assertEqual(len(result['held']),1)

    def test_closed_reference_closing_edge_and_repeated_travel_are_preserved(self):
        line=LineString([(0,0),(10,0),(10,10),(0,10),(0,0)])
        ref=TrialReference('ring',[Section('r',line,('NCN:test',))]);ledger=TrialLedger(ref)
        ledger.put('closing',Evidence('ring','cycling',(('r',30,40),),()))
        self.assertAlmostEqual(ledger.summary()['cycling_m'],10)
        ledger.put('full',Evidence('ring','on_foot',(('r',0,40),),()))
        self.assertAlmostEqual(ledger.summary()['combined_m'],40)
