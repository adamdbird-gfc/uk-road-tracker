from copy import deepcopy
import hashlib
import json
import gzip
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from matched_path_evidence import digest
from .confirmed_paths import build_candidate
from .test_confirmed_paths import inputs
from .numbered_alternative_review import read_reviewed_overlay


def reviewed_inputs():
    values = inputs()
    values[5]['features'][0]['properties']['RouteCat'] = 'Alternative Route'
    review = {'schema_version': 1, 'route_key': values[0],
              'policy': 'official_numbered_alternative_as_own_route',
              'cross_route_credit': False, 'whole_published_route_ready': False,
              'official_network_sha256': values[6],
              'official_features_sha256': digest(values[5]['features']),
              'source_relation_ids': [1], 'approved_feature_ids': [1],
              'identity_evidence_urls': ['https://example.test/pinned-review']}
    return values, review


def build(values, review):
    raw = json.dumps(review).encode()
    return build_candidate(*values, numbered_alternative_review=raw,
                           expected_review_sha256=hashlib.sha256(raw).hexdigest())


class NumberedAlternativeReviewTests(unittest.TestCase):
    def test_overlay_requires_exact_predecessor_review_and_fixed_denominator(self):
        from .test_inventory_completion import fixture
        from .inventory_completion import build_plan
        from .association_bundle import build_bundle
        args = list(fixture(True))
        args[4].update(source_snapshot_sha256='a'*64, national_reference_sha256='c'*64)
        before = list(args)
        before[2], before[3] = [], []
        _, _, previous = build_plan(*before)
        previous_raw = json.dumps(previous).encode()
        review_raw = json.dumps({'route_key': args[0],
            'policy': 'official_numbered_alternative_as_own_route',
            'cross_route_credit': False, 'whole_published_route_ready': False,
            'official_network_sha256': 'c'*64}).encode()
        review_sha = hashlib.sha256(review_raw).hexdigest()
        args[4]['numbered_alternative_review_sha256'] = review_sha
        ledger, paths, plan = build_plan(*args)
        plan_raw = json.dumps(plan).encode()
        bundle = build_bundle(paths, ledger.reference, [0, 0], {
            'attribution': 'OpenStreetMap contributors', 'licence': 'ODbL 1.0',
            'source_snapshot_sha256': 'a'*64, 'membership_evidence_sha256': 'b'*64})
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'extent-review.json').write_bytes(review_raw)
            (root/'plan.json.gz').write_bytes(gzip.compress(plan_raw))
            (root/'associations.json.gz').write_bytes(gzip.compress(bundle))
            manifest = {'schema_version': 1, 'reference_version': plan['reference_version'],
                'plan_sha256': hashlib.sha256(plan_raw).hexdigest(),
                'association_sha256': hashlib.sha256(bundle).hexdigest(),
                'replaces_plan_sha256': hashlib.sha256(previous_raw).hexdigest(),
                'numbered_alternative_review_sha256': review_sha,
                'whole_published_route_ready': False, 'cross_route_credit': False}
            manifest_raw = json.dumps(manifest).encode()
            (root/'manifest.json').write_bytes(manifest_raw)
            pin = hashlib.sha256(manifest_raw).hexdigest()
            self.assertEqual(len(read_reviewed_overlay(root, previous_raw, pin)[1]), 3)
            for old, sha in [(previous_raw+b' ', pin), (previous_raw, '0'*64)]:
                with self.assertRaises(ValueError):read_reviewed_overlay(root, old, sha)
            (root/'extent-review.json').write_bytes(review_raw+b' ')
            with self.assertRaises(ValueError):read_reviewed_overlay(root, previous_raw, pin)

    def test_default_still_holds_alternative_but_review_admits_own_route(self):
        values, review = reviewed_inputs()
        self.assertIsNone(build_candidate(*values)[0])
        result, audit = build(values, review)
        self.assertEqual(audit['accepted_way_count'], 1)
        self.assertFalse(audit['cross_route_credit'])
        self.assertEqual(result[1]['sections'][0]['routes'], ['NCN:137:eligible-source-spans'])
        self.assertIn('numbered_alternative_review_sha256', result[1]['metadata'])

    def test_independent_pin_and_both_arguments_are_required(self):
        values, review = reviewed_inputs()
        raw = json.dumps(review).encode()
        for kwargs in [dict(numbered_alternative_review=raw),
                       dict(expected_review_sha256='a' * 64),
                       dict(numbered_alternative_review=raw, expected_review_sha256='a' * 64)]:
            with self.assertRaises(ValueError):
                build_candidate(*values, **kwargs)

    def test_changed_route_scope_features_roots_and_network_are_rejected(self):
        for field, value in [('route_key', 'NCN:138'), ('source_relation_ids', [2]),
                             ('official_network_sha256', 'z' * 64),
                             ('official_features_sha256', 'z' * 64),
                             ('approved_feature_ids', [1, 1]), ('approved_feature_ids', [2]),
                             ('cross_route_credit', True), ('identity_evidence_urls', [])]:
            values, review = reviewed_inputs()
            review[field] = value
            with self.assertRaises(ValueError, msg=field):build(values, review)

    def test_closed_unknown_diversion_and_other_identity_cannot_be_promoted(self):
        for field, value in [('OpenStatus', 'Closed'), ('OpenStatus', None),
                             ('RouteCat', 'Temporary Diversion'), ('Desc_', 'Ferry'),
                             ('RouteType', 'RCN')]:
            values, review = reviewed_inputs()
            values[5]['features'][0]['properties'][field] = value
            review['official_features_sha256'] = digest(values[5]['features'])
            with self.assertRaises(ValueError):build(values, review)

    def test_source_membership_access_and_geometry_gates_still_apply(self):
        values, review = reviewed_inputs()
        values[3]['way', 42]['tags']['highway'] = 'construction'
        self.assertIsNone(build(values, review)[0])
        values, review = reviewed_inputs()
        values[3]['node', 2]['lat'] = .0001
        self.assertIsNone(build(values, review)[0])
        values, review = reviewed_inputs()
        values[2][1]['ways'] = []
        with self.assertRaises(ValueError):build(values, review)
        values, review = reviewed_inputs()
        del values[3]['relation', 1]['tags']['cycle_network']
        with self.assertRaises(ValueError):build(values, review)

    def test_unreviewed_feature_and_nearby_way_do_not_become_eligible(self):
        values, review = reviewed_inputs()
        values[3]['way', 99] = deepcopy(values[3]['way', 42])
        other = deepcopy(values[5]['features'][0])
        other['properties']['FID'] = 2
        values[5]['features'].append(other)
        review['official_features_sha256'] = digest(values[5]['features'])
        self.assertIsNone(build(values, review)[0])


if __name__ == '__main__':unittest.main()
