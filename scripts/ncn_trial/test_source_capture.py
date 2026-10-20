import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from .source_capture import (select_sources, verify_snapshot, verified_sources,
                             capture, STATES)
from .confirmed_paths import selected_routes


def selection():
    rows = [{'official_route_key': f'NCN:{i}', 'state': state,
             'candidate_relation_ids': [i], 'source_geometry_complete': True,
             'uk_metadata_anchor': True} for i, state in enumerate(STATES, 1)]
    profiles = [{'relation_id': i, 'member_lists_complete': True,
                 'source_relations': [{'id': i}, {'id': 9}]} for i in range(1, 4)]
    return {'routes': rows}, profiles


class SourceCaptureTests(unittest.TestCase):
    def test_offset_selection_is_explicit_and_retains_nested_requests(self):
        report, profiles = selection()
        rows, seeds, ids = select_sources(report, profiles, [STATES[1]])
        self.assertEqual([r['official_route_key'] for r in rows], ['NCN:2'])
        self.assertEqual(seeds, [2])
        self.assertEqual(ids, [2, 9])

    def test_unanchored_incomplete_and_unsupported_queues_cannot_enter(self):
        for field in ('source_geometry_complete', 'uk_metadata_anchor'):
            report, profiles = selection()
            report['routes'][1][field] = False
            with self.assertRaises(ValueError):
                select_sources(report, profiles, [STATES[1]])
        report, profiles = selection()
        profiles[1]['member_lists_complete'] = False
        with self.assertRaises(ValueError):
            select_sources(report, profiles, [STATES[1]])
        with self.assertRaises(ValueError):
            select_sources(*selection(), ['source_scope_review_required'])

    def test_missing_profiles_and_duplicate_route_rows_fail(self):
        report, profiles = selection()
        with self.assertRaises(ValueError):
            select_sources(report, profiles[:1], [STATES[1]])
        report['routes'].append(report['routes'][1])
        with self.assertRaises(ValueError):
            select_sources(report, profiles, [STATES[1]])

    def test_source_pin_rejects_wrong_relation_changed_bytes_and_errors(self):
        raw = json.dumps({'elements': [{'type': 'relation', 'id': 2}]}).encode()
        self.assertEqual(verify_snapshot(raw, 2), hashlib.sha256(raw).hexdigest())
        for candidate, identity, sha in [(raw, 3, None), (raw, 2, '0'*64),
                                       (b'{"error":"partial","elements":[]}', 2, None)]:
            with self.assertRaises(ValueError):
                verify_snapshot(candidate, identity, sha)

    def test_manifest_cannot_claim_completeness_with_missing_or_duplicate_responses(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = {'complete': True, 'sources': [], 'requested_relation_ids': [2]}
            with self.assertRaises(ValueError):
                verified_sources(root, manifest)
            raw = b'{"elements":[{"type":"relation","id":2}]}'
            (root/'two.gz').write_bytes(gzip.compress(raw))
            record = {'relation_id': 2, 'file': 'two.gz', 'sha256': hashlib.sha256(raw).hexdigest()}
            manifest['sources'] = [record]
            self.assertEqual(verified_sources(root, manifest), [raw])
            manifest['sources'].append(record)
            with self.assertRaises(ValueError):
                verified_sources(root, manifest)

    def test_consumer_requires_exact_capture_scope_and_seeds(self):
        report, _ = selection()
        fetch = {'complete': True, 'routes': ['NCN:2'], 'seed_relation_ids': [2]}
        self.assertEqual(selected_routes(report, fetch)[0]['state'], STATES[1])
        for update in ({'complete': False}, {'routes': ['NCN:2', 'NCN:2']},
                       {'seed_relation_ids': [1]}, {'errors': ['failed']}):
            with self.assertRaises(ValueError):
                selected_routes(report, {**fetch, **update})

    def test_offset_review_does_not_widen_geometry_or_approve_alternatives(self):
        from .test_confirmed_paths import inputs
        from .confirmed_paths import build_candidate
        values = inputs()
        # Approximately 16.7m offset: still inside the diagnostic 25m corridor,
        # but every source edge remains outside the unchanged admission rule.
        values[5]['features'][0]['geometry']['coordinates'] = [[0, .00015], [.001, .00015]]
        result, audit = build_candidate(*values)
        self.assertIsNone(result)
        self.assertEqual(audit['held'][0]['reason'], 'unresolved_official_extent_edges')
        values = inputs()
        values[3]['relation', 1]['members'][0]['role'] = 'alternative'
        self.assertIsNone(build_candidate(*values)[0])

    def test_changed_source_membership_retains_exact_reconciliation_evidence(self):
        from .test_confirmed_paths import inputs
        from .confirmed_paths import build_candidate, SourceMembershipChanged
        values = inputs()
        values[2][1]['ways'] = [{'way_id': 99}]
        with self.assertRaises(SourceMembershipChanged) as error:
            build_candidate(*values)
        self.assertEqual(error.exception.details, {'relation_id': 1, 'current_version': 1,
            'current_timestamp': None, 'added_way_ids': [42], 'removed_way_ids': [99]})

    def test_inventory_cannot_hide_new_missing_children_or_nodes(self):
        from .test_confirmed_paths import inputs
        from .inventory_completion import inventory_ways
        index = inputs()[3]
        self.assertEqual(set(inventory_ways(index, [1])), {42})
        index['relation', 1]['members'].append({'type': 'relation', 'ref': 9})
        with self.assertRaises(ValueError):
            inventory_ways(index, [1])
        index = inputs()[3]
        del index['node', 2]
        with self.assertRaises(ValueError):
            inventory_ways(index, [1])

    def test_capture_reuses_pinned_sources_and_resumes_without_network(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'physical-national').mkdir()
            report, profiles = selection()
            (root/'physical-national/summary.json').write_text(json.dumps(report))
            (root/'numeric-route-membership-profiles.json.gz').write_bytes(
                gzip.compress(json.dumps({'profiles': profiles}).encode()))
            reuse = root/'reuse'
            reuse.mkdir()
            records = []
            for identity in (2, 9):
                raw = json.dumps({'elements': [{'type': 'relation', 'id': identity}]}).encode()
                (reuse/f'{identity}.gz').write_bytes(gzip.compress(raw))
                records.append({'relation_id': identity, 'file': f'{identity}.gz',
                                'sha256': hashlib.sha256(raw).hexdigest(), 'observed_at_utc': 'pinned'})
            (reuse/'fetch-manifest.json').write_text(json.dumps({'sources': records}))
            output = root/'capture'
            with patch('scripts.ncn_trial.source_capture.urlopen', side_effect=AssertionError('network')):
                first = capture(root, output, [STATES[1]], reuse)
                second = capture(root, output, [STATES[1]])
            self.assertTrue(first['complete'])
            self.assertEqual(first, second)
            self.assertEqual(len(verified_sources(output, second)), 2)


if __name__ == '__main__':
    unittest.main()
