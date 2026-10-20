import json
import unittest
from .catalogue_extract import extract_memberships, extract_route, extract_membership_catalogue
from .test_extent_composition import source


def relation(id,members=(),**tags):
    return {'type':'relation','id':id,'version':1,'tags':{'type':'route','route':'bicycle',**tags},'members':list(members)}
def member(type,id,role=''):return {'type':type,'ref':id,'role':role}
def raw(*elements):return json.dumps({'elements':elements}).encode()


class RouteMembershipTests(unittest.TestCase):
    def test_member_id_lists_do_not_require_or_claim_way_geometry(self):
        result=extract_memberships([raw(relation(1,[member('way',42)]))],1)
        self.assertTrue(result['member_lists_complete']);self.assertFalse(result['way_geometry_verified'])
        self.assertEqual([42],[w['way_id'] for w in result['ways']])
        self.assertFalse(result['official_gis_association_approved'])
        self.assertEqual([result],extract_membership_catalogue([raw(relation(1,[member('way',42)]))],[1]))

    def test_repeated_way_is_deduplicated_without_losing_role_paths(self):
        result=extract_memberships([raw(relation(1,[member('way',42),member('relation',2,'alternative')]),
                                             relation(2,[member('way',42,'backward')]))],1)
        self.assertEqual(1,len(result['ways']));self.assertEqual(2,len(result['ways'][0]['membership_chains']))
        self.assertEqual('alternative',result['ways'][0]['membership_chains'][1][0][1])

    def test_real_api_empty_relation_without_members_is_reported_not_crashed(self):
        value=json.loads(source(1,42,[1,2]));root=next(e for e in value['elements'] if e['type']=='relation')
        root['members'].append(member('relation',2));empty=relation(2);empty.pop('members');value['elements'].append(empty)
        payload=json.dumps(value).encode()
        result=extract_memberships([payload],1)
        self.assertEqual([2],result['empty_relation_ids']);self.assertFalse(result['member_lists_complete'])
        summary,_=extract_route([payload],1);self.assertFalse(summary['member_geometry_complete'])

    def test_missing_cycles_and_areas_remain_explicit(self):
        result=extract_memberships([raw(relation(1,[member('way',42),member('relation',2),member('relation',3),member('relation',4)]),
                                             relation(2,[member('relation',1)]),relation(3,type='multipolygon'))],1)
        self.assertEqual([4],result['missing_relations']);self.assertEqual([1],result['cyclic_relation_ids'])
        self.assertEqual([3],result['unsupported_relation_ids']);self.assertFalse(result['member_lists_complete'])

    def test_source_version_conflicts_and_missing_roots_are_refused(self):
        first=relation(1,[member('way',42)]);second={**first,'version':2}
        with self.assertRaises(ValueError):extract_memberships([raw(first),raw(second)],1)
        with self.assertRaises(ValueError):extract_route([raw(first)],99)

    def test_guidepost_nodes_do_not_become_route_way_members(self):
        result=extract_memberships([raw(relation(1,[member('node',42),member('way',43)]))],1)
        self.assertEqual([43],[w['way_id'] for w in result['ways']])
