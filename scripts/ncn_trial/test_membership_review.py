from copy import deepcopy
import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from .catalogue_extract import _memberships_from_index
from .confirmed_paths import build_candidate, SourceMembershipChanged
from .membership_review import review_relation, load_providers, read_reviewed_overlay
from .test_confirmed_paths import inputs


def split_inputs():
    values = inputs()
    previous = deepcopy(values[2][1])
    old_provider = deepcopy(values[7][1])
    values[3]['way', 99] = {**deepcopy(values[3]['way', 42]), 'id': 99}
    values[3]['relation', 1]['members'] = [{'type': 'way', 'ref': 99, 'role': ''}]
    current = _memberships_from_index(values[3], values[4], 1)
    new_provider = deepcopy(old_provider)
    new_provider['route']['ways'][0]['id'] = 99
    return values, previous, current, old_provider, new_provider


class MembershipReviewTests(unittest.TestCase):
    def test_overlay_requires_exact_prior_inventory_and_pinned_new_bytes(self):
        from .test_inventory_completion import fixture
        from .inventory_completion import build_plan
        from .association_bundle import build_bundle
        args = list(fixture(True))
        args[4]['source_snapshot_sha256'] = 'a'*64
        before = list(args)
        before[2], before[3] = [], []
        _, _, old_plan = build_plan(*before)
        old_raw = json.dumps(old_plan).encode()
        args[4] = {**args[4], 'membership_review_sha256': 'd'*64}
        ledger, paths, new_plan = build_plan(*args)
        new_raw = json.dumps(new_plan).encode()
        bundle = build_bundle(paths, ledger.reference, [0, 0], {
            'attribution': 'OpenStreetMap contributors', 'licence': 'ODbL 1.0',
            'source_snapshot_sha256': 'a'*64, 'membership_evidence_sha256': 'b'*64})
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'plan.json.gz').write_bytes(gzip.compress(new_raw))
            (root/'associations.json.gz').write_bytes(gzip.compress(bundle))
            manifest = {'schema_version': 1, 'reference_version': new_plan['reference_version'],
                'plan_sha256': hashlib.sha256(new_raw).hexdigest(),
                'association_sha256': hashlib.sha256(bundle).hexdigest(),
                'replaces_plan_sha256': hashlib.sha256(old_raw).hexdigest(),
                'membership_review_sha256': 'd'*64, 'whole_published_route_ready': False}
            manifest_raw = json.dumps(manifest).encode()
            (root/'manifest.json').write_bytes(manifest_raw)
            pin = hashlib.sha256(manifest_raw).hexdigest()
            self.assertEqual(len(read_reviewed_overlay(root, old_raw, pin)[1]), 3)
            for old_bytes, sha in ((old_raw+b' ', pin), (old_raw, '0'*64)):
                with self.assertRaises(ValueError):
                    read_reviewed_overlay(root, old_bytes, sha)
            (root/'plan.json.gz').write_bytes(gzip.compress(new_raw+b' '))
            with self.assertRaises(ValueError):
                read_reviewed_overlay(root, old_raw, pin)

    def test_provider_capture_must_be_complete_distinct_and_checksum_pinned(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            raw = b'{"id":1,"type":"relation"}'
            (root / 'one.gz').write_bytes(gzip.compress(raw))
            record = {'relation_id': 1, 'file': 'one.gz', 'sha256': hashlib.sha256(raw).hexdigest()}
            manifest = {'complete': True, 'sources': [record]}
            def write(value):
                (root / 'fetch-manifest.json').write_text(json.dumps(value))
            write(manifest)
            self.assertEqual(load_providers(root, {1})[0][1]['id'], 1)
            for value in ({**manifest, 'complete': False}, {**manifest, 'errors': ['failed']},
                          {**manifest, 'sources': [record, record]},
                          {**manifest, 'sources': [{**record, 'sha256': '0'*64}]}):
                write(value)
                with self.assertRaises(ValueError):
                    load_providers(root, {1})
            write(manifest)
            with self.assertRaises(ValueError):
                load_providers(root, {2})

    def test_refreshed_identity_and_nodes_resolve_resegmentation_without_transferring_history(self):
        values, previous, current, old, new = split_inputs()
        proof = review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])
        self.assertEqual(proof['added_way_ids'], [99])
        self.assertEqual(proof['removed_way_ids'], [42])
        self.assertEqual(proof['geometry_review'], 'same_linework_at_representation_tolerance')
        self.assertTrue(proof['membership_baseline_reconciled'])
        self.assertFalse(proof['whole_published_route_ready'])
        self.assertFalse(proof['old_journey_evidence_transferred'])

    def test_ordinary_builder_still_rejects_unreviewed_membership_change(self):
        values, _, _, _, _ = split_inputs()
        with self.assertRaises(SourceMembershipChanged):
            build_candidate(*values)

    def test_removed_way_cannot_reappear_from_another_source_record(self):
        values, previous, current, old, new = split_inputs()
        review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])
        values[2] = {1: current}
        values[7] = {1: new}
        result, _ = build_candidate(*values)
        self.assertIn(('way', 42), values[3])  # Deliberately retain an unrelated old record.
        self.assertEqual([p['way_id'] for p in json.loads(result[0])['paths']], ['99'])

    def test_stale_incomplete_or_unlisted_refreshed_provider_stays_held(self):
        values, previous, current, old, new = split_inputs()
        with self.assertRaises(ValueError):
            review_relation('NCN:137', previous, current, old, old, values[3], [0, 0])
        extra = deepcopy(new['route']['ways'][0])
        extra['id'] = 100
        new['route']['ways'].append(extra)
        with self.assertRaises(ValueError):
            review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])

    def test_classification_namespace_and_incomplete_hierarchy_cannot_be_overridden(self):
        for mutate in (lambda p: p['tags'].update(network='rcn'),
                       lambda p: p['tags'].update(ref='138'),
                       lambda p: p['tags'].update(cycle_network='US:US'),
                       lambda p: p.update(member_lists_complete=False)):
            values, previous, current, old, new = split_inputs()
            mutate(current)
            with self.assertRaises(ValueError):
                review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])
        values, previous, current, old, new = split_inputs()
        values[3]['relation', 1]['members'].append({'type': 'relation', 'ref': 9})
        with self.assertRaises(ValueError):
            review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])

    def test_geometry_drift_or_missing_source_node_does_not_clear_membership_hold(self):
        values, previous, current, old, new = split_inputs()
        values[3]['node', 2]['lat'] = .00001
        with self.assertRaises(ValueError):
            review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])
        del values[3]['node', 2]
        with self.assertRaises(ValueError):
            review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])

    def test_new_official_extent_still_requires_current_span_admission(self):
        values, previous, current, old, new = split_inputs()
        # A genuinely changed source path has complete provider/node identity,
        # but is outside the pinned official extent. No old credit is carried.
        for i in (1, 2, 3):
            values[3]['node', i]['lat'] = .00015
        for point in new['route']['ways'][0]['geometry']['coordinates']:
            point[1] = 16.697923618991036
        proof = review_relation('NCN:137', previous, current, old, new, values[3], [0, 0])
        self.assertEqual(proof['geometry_review'], 'geometry_changed_requires_current_extent_assessment')
        values[2] = {1: current}
        values[7] = {1: new}
        result, audit = build_candidate(*values)
        self.assertIsNone(result)
        self.assertEqual(audit['held'][0]['reason'], 'unresolved_official_extent_edges')


if __name__ == '__main__':
    unittest.main()
