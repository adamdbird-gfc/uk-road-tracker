import copy
import json
import unittest

from .access_evidence import audit_extent, mode_evidence
from .extent_composition import compose_extent
from .test_extent_composition import source, selection


class AccessEvidenceTests(unittest.TestCase):
    def test_specific_mode_overrides_general_private_tag(self):
        tags = {'access': 'private', 'foot': 'permissive', 'bicycle': 'yes'}
        self.assertEqual('permissive', mode_evidence(tags, 'on_foot')['static_value'])
        self.assertEqual('positive_tag', mode_evidence(tags, 'cycling')['static_kind'])

    def test_motor_vehicle_restriction_does_not_apply_to_walk_or_cycle(self):
        for mode in ('on_foot', 'cycling'):
            self.assertEqual('not_explicitly_tagged',
                             mode_evidence({'motor_vehicle': 'no'}, mode)['static_kind'])

    def test_vehicle_restriction_applies_to_cycle_without_foot_inheritance(self):
        tags = {'vehicle': 'no'}
        self.assertEqual('negative_tag', mode_evidence(tags, 'cycling')['static_kind'])
        self.assertEqual('not_explicitly_tagged', mode_evidence(tags, 'on_foot')['static_kind'])

    def test_absent_cycleway_foot_tag_is_neither_permission_nor_prohibition(self):
        result = mode_evidence({'highway': 'cycleway', 'bicycle': 'designated'}, 'on_foot')
        self.assertEqual('not_explicitly_tagged', result['static_kind'])
        self.assertFalse(result['permission_verified'])

    def test_dismount_is_distinct_from_cycle_permission_and_walk_prohibition(self):
        tags = {'bicycle': 'dismount', 'foot': 'yes'}
        self.assertEqual('dismount_tag', mode_evidence(tags, 'cycling')['static_kind'])
        self.assertEqual('positive_tag', mode_evidence(tags, 'on_foot')['static_kind'])

    def test_seasonal_condition_preserved_without_affecting_walk_or_evaluation(self):
        tags = {'bicycle': 'yes', 'bicycle:conditional': 'no @ (May 1-Sep 30 10:00-18:00)'}
        self.assertEqual({'bicycle:conditional': tags['bicycle:conditional']},
                         mode_evidence(tags, 'cycling')['conditions'])
        self.assertEqual({}, mode_evidence(tags, 'on_foot')['conditions'])

    def test_audit_only_includes_nodes_in_selected_spans_and_keeps_barriers(self):
        value = json.loads(source(1,10,[1,2,3]))
        for element in value['elements']:
            if element['type']=='node' and element['id'] in (1,3):
                element['tags'] = {'barrier': 'gate'}
        raw = json.dumps(value).encode()
        extent = compose_extent({1:raw}, [selection(1,10,0,1)])
        audit = audit_extent(extent,{1:raw})
        self.assertEqual([1],[n['node_id'] for n in audit['nodes']])
        self.assertFalse(audit['journey_credit_changed'])
        changed = copy.deepcopy(extent)
        changed['physical_edges'][0]['nodes'] = [1,3]
        with self.assertRaises(ValueError):audit_extent(changed,{1:raw})

    def test_changed_source_tags_require_extent_refresh(self):
        raw = source(1,10,[1,2])
        extent = compose_extent({1:raw}, [selection(1,10,0,1)])
        value = json.loads(raw)
        next(e for e in value['elements'] if e['type']=='way')['tags'] = {'foot':'no'}
        with self.assertRaises(ValueError):audit_extent(extent,{1:json.dumps(value).encode()})

    def test_direction_tags_and_member_roles_are_retained_without_foot_prohibition(self):
        value = json.loads(source(1,10,[1,2]))
        next(e for e in value['elements'] if e['type']=='way')['tags'] = {'oneway':'yes'}
        next(e for e in value['elements'] if e['type']=='relation')['members'][0]['role'] = 'forward'
        raw = json.dumps(value).encode()
        extent = compose_extent({1:raw}, [selection(1,10,0,1)])
        way = audit_extent(extent,{1:raw})['ways'][0]
        self.assertEqual(['forward'], way['member_roles'])
        self.assertEqual('yes', way['tags']['oneway'])
        self.assertEqual('not_explicitly_tagged', way['modes']['on_foot']['static_kind'])
