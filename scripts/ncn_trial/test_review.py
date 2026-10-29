from dataclasses import replace
import unittest

from shapely.geometry import LineString

from .coverage import Fix, Section, TrialLedger, TrialReference
from .review import Alternative, ConfirmationTrial, Window, prepare_review


class ReviewTrialTests(unittest.TestCase):
    def setUp(self):
        self.section = Section('ncn', LineString([(0, 0), (200, 0)]), ('TEST:NCN',))
        self.reference = TrialReference('review-v1', [self.section])
        self.alternatives = (Alternative.from_section('ncn-path', self.section),
                             Alternative('road', LineString([(0, 8), (200, 8)]), (), 'ineligible'))
        self.fixes = tuple(Fix(x, 4, i * 2, 5) for i, x in enumerate(range(20, 181, 8)))
        self.window = Window('ride1', 'window1', 'cycling', self.fixes, 'road')
        self.review = prepare_review(self.reference, self.window, self.alternatives)
        self.store = ConfirmationTrial()

    def test_only_collection_enables_the_prompt_not_core_mapping(self):
        self.assertEqual(('ncn-path', 'road'), self.review.options)
        self.assertFalse(self.store.state(self.review, False)['prompt'])
        state = self.store.state(self.review)
        self.assertTrue(state['prompt'])
        self.assertEqual('road', state['display_path'])
        self.assertFalse(state['display_path_certain'])
        self.assertEqual(self.fixes, state['original_fixes'])

    def test_confirm_ncn_adds_only_the_evidenced_window(self):
        state = self.store.choose(self.review, 'ncn-path')
        ledger = TrialLedger(self.reference)
        ledger.put('ride1-window1', state['evidence'])
        self.assertAlmostEqual(160, ledger.summary()['cycling_m'])
        self.assertEqual('ncn-path', state['display_path'])
        self.assertEqual(160, self.review.selected_geometry('ncn-path').length)
        self.assertEqual(self.fixes, state['original_fixes'])

    def test_confirm_parallel_road_preserves_ride_without_ncn_credit(self):
        state = self.store.choose(self.review, 'road')
        self.assertEqual('confirmed', state['status'])
        self.assertEqual('road', state['display_path'])
        self.assertEqual((), state['evidence'].intervals)
        self.assertEqual(self.fixes, state['original_fixes'])

    def test_not_sure_remains_pending_without_repeated_prompt(self):
        state = self.store.choose(self.review, None)
        self.assertEqual('pending', state['status'])
        self.assertFalse(state['prompt'])
        self.assertEqual((), state['evidence'].intervals)
        self.assertEqual('road', state['display_path'])

    def test_json_roundtrip_retains_confirmation_on_reprocessing(self):
        self.store.choose(self.review, 'ncn-path')
        restored = ConfirmationTrial.restore(self.store.export())
        state = restored.state(prepare_review(self.reference, self.window, self.alternatives))
        self.assertEqual('confirmed', state['status'])
        self.assertEqual('ncn-path', state['choice'])
        self.assertFalse(state['prompt'])

    def test_version_change_requires_reconciliation_without_old_credit(self):
        self.store.choose(self.review, 'ncn-path')
        changed = TrialReference('review-v2', [self.section])
        state = self.store.state(prepare_review(changed, self.window, self.alternatives))
        self.assertEqual('needs_reconciliation', state['status'])
        self.assertFalse(state['prompt'])
        self.assertEqual((), state['evidence'].intervals)

    def test_changed_raw_evidence_or_candidate_geometry_never_silently_reuses_choice(self):
        self.store.choose(self.review, 'ncn-path')
        changed = replace(self.window, fixes=tuple(replace(f, y=4.1) for f in self.fixes))
        self.assertEqual('needs_reconciliation', self.store.state(prepare_review(self.reference, changed, self.alternatives))['status'])
        alternatives = (self.alternatives[0], replace(self.alternatives[1], line=LineString([(0, 9), (200, 9)])))
        self.assertEqual('needs_reconciliation', self.store.state(prepare_review(self.reference, self.window, alternatives))['status'])

    def test_user_cannot_select_unoffered_or_distant_alternative(self):
        distant = Alternative('distant', LineString([(0, 100), (200, 100)]))
        review = prepare_review(self.reference, self.window, self.alternatives + (distant,))
        self.assertNotIn('distant', review.options)
        with self.assertRaises(ValueError):
            self.store.choose(review, 'distant')
        with self.assertRaises(ValueError):
            self.store.choose(review, 'arbitrary')

    def test_driving_cannot_be_rescued_by_confirmation(self):
        review = prepare_review(self.reference, replace(self.window, mode='driving'), self.alternatives)
        self.assertFalse(self.store.state(review)['prompt'])
        with self.assertRaises(ValueError):
            self.store.choose(review, 'ncn-path')

    def test_poor_accuracy_and_missing_time_evidence_offer_no_override(self):
        for fixes in [tuple(replace(f, accuracy=25) for f in self.fixes),
                      self.fixes[:5] + tuple(replace(f, seconds=f.seconds + 100) for f in self.fixes[5:])]:
            review = prepare_review(self.reference, replace(self.window, fixes=fixes), self.alternatives)
            self.assertEqual((), review.options)
            with self.assertRaises(ValueError):
                self.store.choose(review, 'ncn-path')

    def test_correction_replaces_old_window_and_preserves_other_credit(self):
        ledger = TrialLedger(self.reference)
        ledger.put('other-window', self.reference.observe('cycling', (Fix(1, 0, 0), Fix(8, 0, 2), Fix(15, 0, 4))))
        state = self.store.choose(self.review, 'ncn-path')
        ledger.put('ride1-window1', state['evidence'])
        self.assertAlmostEqual(174, ledger.summary()['cycling_m'])
        state = self.store.choose(self.review, 'road')
        ledger.put('ride1-window1', state['evidence'])
        self.assertAlmostEqual(14, ledger.summary()['cycling_m'])

    def test_qualifying_road_can_earn_ncn_credit(self):
        road_section = Section('qualifying-road', self.alternatives[1].line, ('TEST:road-route',))
        reference = TrialReference('road-v1', [self.section, road_section])
        alternatives = (self.alternatives[0], Alternative.from_section('road', road_section))
        review = prepare_review(reference, self.window, alternatives)
        ledger = TrialLedger(reference)
        ledger.put('window', self.store.choose(review, 'road')['evidence'])
        self.assertAlmostEqual(160, ledger.summary()['routes']['TEST:road-route']['cycling_m'])

    def test_unverified_nearby_path_cannot_claim_ncn_identity(self):
        false_link = replace(self.alternatives[1], target_keys=('ncn',), membership='eligible')
        with self.assertRaisesRegex(ValueError, 'Unverified'):
            prepare_review(self.reference, self.window, (self.alternatives[0], false_link))

    def test_single_plausible_path_does_not_ask_for_ambiguity_confirmation(self):
        window = replace(self.window, fixes=tuple(replace(f, y=0) for f in self.fixes))
        review = prepare_review(self.reference, window, self.alternatives)
        self.assertEqual(('ncn-path',), review.options)
        self.assertFalse(self.store.state(review)['prompt'])

    def test_direction_and_out_and_back_geometry_survive_correction(self):
        for xs in [list(range(180, 19, -8)), list(range(20, 101, 8)) + list(range(92, 19, -8))]:
            fixes = tuple(Fix(x, 4, i * 2, 5) for i, x in enumerate(xs))
            review = prepare_review(self.reference, replace(self.window, fixes=fixes), self.alternatives)
            geometry = review.selected_geometry('ncn-path')
            self.assertEqual(xs[0], geometry.coords[0][0])
            self.assertEqual(xs[-1], geometry.coords[-1][0])
            self.assertAlmostEqual(sum(abs(b - a) for a, b in zip(xs, xs[1:])), geometry.length)

    def test_duplicate_physical_paths_do_not_create_a_confirmation_prompt(self):
        alias = Alternative.from_section('alias', self.section)
        window = replace(self.window, fixes=tuple(replace(f, y=0) for f in self.fixes))
        review = prepare_review(self.reference, window, (self.alternatives[0], alias))
        self.assertEqual(1, len(review.options))
        self.assertFalse(self.store.state(review)['prompt'])

    def test_invalid_reprocessed_fix_holds_old_choice_for_reconciliation(self):
        self.store.choose(self.review, 'ncn-path')
        fixes = (replace(self.fixes[0], x=float('nan')),) + self.fixes[1:]
        changed = prepare_review(self.reference, replace(self.window, fixes=fixes), self.alternatives)
        self.assertEqual('needs_reconciliation', self.store.state(changed)['status'])

    def test_road_with_unknown_membership_keeps_credit_pending(self):
        alternatives = (self.alternatives[0], replace(self.alternatives[1], membership='unknown'))
        review = prepare_review(self.reference, self.window, alternatives)
        state = self.store.choose(review, 'road')
        self.assertEqual('confirmed', state['status'])
        self.assertEqual('pending_membership', state['ncn_status'])
        self.assertEqual((), state['evidence'].intervals)

    def test_no_ncn_candidate_means_no_collection_prompt(self):
        alternatives = (replace(self.alternatives[0], target_keys=(), membership='ineligible'), self.alternatives[1])
        review = prepare_review(self.reference, self.window, alternatives)
        self.assertEqual(2, len(review.options))
        self.assertFalse(self.store.state(review)['prompt'])
        with self.assertRaises(ValueError):
            self.store.choose(review, 'road')

    def test_disabled_collection_cannot_add_new_confirmation(self):
        with self.assertRaises(ValueError):
            self.store.choose(self.review, 'ncn-path', collection_enabled=False)
        self.assertEqual({}, self.store.rows)

    def test_walking_and_running_keep_the_correct_mode(self):
        for mode in ['walking', 'running']:
            review = prepare_review(self.reference, replace(self.window, mode=mode), self.alternatives)
            ledger = TrialLedger(self.reference)
            ledger.put('foot-window', ConfirmationTrial().choose(review, 'ncn-path')['evidence'])
            self.assertAlmostEqual(160, ledger.summary()['on_foot_m'])
            self.assertEqual(0, ledger.summary()['cycling_m'])
