from dataclasses import replace
import tempfile
from pathlib import Path
import unittest
from shapely.geometry import LineString
from .activation import ActivationTrial, Assessment, JourneyRecord
from .coverage import Evidence, Section, TrialReference


class ActivationTests(unittest.TestCase):
    def setUp(self):
        self.ref = TrialReference('source-v1', [Section('a',LineString([(0,0),(100,0)]),('NCN:1',))])
        self.trial = ActivationTrial(self.ref)

    def record(self, key='ride', **changes):
        return replace(JourneyRecord(key,'revision1','walking','android_activity_capture',1000), **changes)

    def supported(self, record):
        return Assessment(Evidence(self.ref.version,'on_foot',(('a',0,50),),('verified_trial_stub',)))

    def ambiguous(self, record):
        return Assessment(Evidence(self.ref.version,'on_foot',(),('ambiguous',)), True)

    def activate(self, records):
        self.trial.activate(records,2000,complete_snapshot=True)

    def test_no_assessment_or_storage_before_activation(self):
        self.assertFalse(self.trial.upsert(self.record(),origin='live_recording'))
        self.assertEqual(0,self.trial.run_batch(self.supported))
        self.assertEqual(0,self.trial.status()['total'])

    def test_hundreds_of_ambiguous_historical_captures_create_no_review_queue(self):
        self.activate([self.record(str(i)) for i in range(700)])
        while self.trial.status()['queued']: self.trial.run_batch(self.ambiguous,limit=100)
        self.assertEqual(700,self.trial.status()['assessed'])
        self.assertEqual([],self.trial.review_candidates())
        self.assertEqual(0,self.trial.summary()['combined_m'])

    def test_checkpoint_resumes_without_reassessing_finished_rows(self):
        self.activate([self.record(str(i)) for i in range(101)])
        self.trial.run_batch(self.supported,limit=25)
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'checkpoint.json';self.trial.save(path)
            resumed=ActivationTrial.restore(self.ref,path.read_text())
        calls=[]
        def assess(record): calls.append(record.key);return self.supported(record)
        while resumed.status()['queued']: resumed.run_batch(assess)
        self.assertEqual(76,len(calls));self.assertEqual('ready',resumed.status()['phase'])
        self.assertEqual(50,resumed.summary()['on_foot_m'])

    def test_bulk_import_after_activation_never_prompts_even_with_recent_native_source(self):
        self.activate([])
        self.trial.upsert(self.record(started_ms=3000),origin='bulk_import')
        self.trial.run_batch(self.ambiguous)
        self.assertEqual([],self.trial.review_candidates())

    def test_only_post_activation_native_recording_can_be_reviewed(self):
        self.activate([])
        for record in [self.record('new',started_ms=3000),self.record('old'),self.record('timeline',started_ms=3000,source='timeline_import')]:
            self.trial.upsert(record,origin='live_recording')
        self.trial.run_batch(self.ambiguous)
        self.assertEqual(['new'],self.trial.review_candidates())

    def test_corrected_evidence_replaces_old_credit_without_double_counting(self):
        record=self.record();self.activate([record]);self.trial.run_batch(self.supported)
        self.trial.upsert(replace(record,revision='corrected'),origin='correction')
        self.assertEqual(0,self.trial.summary()['combined_m'])
        self.trial.run_batch(lambda r:Assessment(Evidence(self.ref.version,'on_foot',(('a',20,40),),())))
        self.assertEqual(20,self.trial.summary()['combined_m'])

    def test_deactivation_pauses_and_reactivation_reconciles_deletions_and_corrections(self):
        self.activate([self.record('deleted'),self.record('changed')]);self.trial.run_batch(self.supported)
        self.trial.deactivate();self.assertEqual(0,self.trial.run_batch(self.supported))
        self.assertFalse(self.trial.upsert(self.record('ignored'),origin='bulk_import'))
        self.activate([self.record('changed',revision='r2')])
        self.assertEqual(1,self.trial.status()['total']);self.assertEqual(0,self.trial.summary()['combined_m'])

    def test_drive_is_excluded_without_calling_matcher(self):
        self.activate([self.record(mode='driving')])
        self.trial.run_batch(lambda r:self.fail('Driving should not be assessed'))
        self.assertEqual(0,self.trial.summary()['combined_m'])

    def test_failed_rows_do_not_hot_loop_or_report_ready(self):
        self.activate([self.record()])
        def failed(r):raise RuntimeError('Temporary unavailable reference')
        self.trial.run_batch(failed);self.assertEqual('needs_retry',self.trial.status()['phase'])
        self.assertEqual(0,self.trial.run_batch(self.supported))
        self.trial.retry_failed();self.trial.run_batch(self.supported)
        self.assertEqual('ready',self.trial.status()['phase'])

    def test_correction_during_assessment_prevents_stale_commit(self):
        self.activate([self.record()])
        def changed(record):
            self.trial.upsert(replace(record,revision='new'),origin='correction')
            return self.supported(record)
        self.trial.run_batch(changed)
        self.assertEqual(1,self.trial.status()['queued']);self.assertEqual(0,self.trial.summary()['combined_m'])

    def test_deactivation_during_assessment_prevents_commit(self):
        self.activate([self.record()])
        def stopped(record): self.trial.deactivate();return self.supported(record)
        self.trial.run_batch(stopped)
        self.assertEqual('paused',self.trial.status()['phase']);self.assertEqual(1,self.trial.status()['queued'])

    def test_incomplete_snapshot_and_duplicates_cannot_delete_existing_history(self):
        self.activate([self.record()])
        with self.assertRaises(ValueError):self.trial.activate([],2000,complete_snapshot=False)
        with self.assertRaises(ValueError):self.activate([self.record(),self.record()])
        self.assertEqual(1,self.trial.status()['total'])

    def test_new_reference_cannot_silently_reuse_checkpoint(self):
        self.activate([self.record()]);self.trial.run_batch(self.supported)
        new=TrialReference('source-v2',list(self.ref.sections.values()))
        with self.assertRaises(ValueError):ActivationTrial.restore(new,self.trial.export())

    def test_wrong_mode_or_source_evidence_does_not_enter_progress(self):
        self.activate([self.record()])
        self.trial.run_batch(lambda r:Assessment(Evidence('source-v2','cycling',(('a',0,50),),())))
        self.assertEqual(1,self.trial.status()['failed']);self.assertEqual(0,self.trial.summary()['combined_m'])

    def test_supported_timeline_import_can_qualify_without_raw_gps_samples(self):
        self.activate([self.record(source='timeline_import')])
        self.trial.run_batch(self.supported)
        self.assertEqual(50,self.trial.summary()['on_foot_m'])
        self.assertEqual([],self.trial.review_candidates())

    def test_ambiguous_import_keeps_supported_sections_without_creating_review(self):
        self.activate([self.record(source='timeline_import')])
        self.trial.run_batch(lambda r:replace(self.supported(r),ambiguous=True))
        self.assertEqual(50,self.trial.summary()['on_foot_m'])
        self.assertEqual([],self.trial.review_candidates())

    def test_reactivation_does_not_repeat_unchanged_assessments(self):
        records=[self.record()];self.activate(records);self.trial.run_batch(self.supported)
        self.trial.deactivate();self.activate(records)
        self.assertEqual(0,self.trial.run_batch(lambda r:self.fail('Unchanged history must be reused')))
        self.assertEqual(50,self.trial.summary()['combined_m'])

    def test_deleting_one_journey_preserves_overlap_supported_by_another(self):
        self.activate([self.record('one'),self.record('two')]);self.trial.run_batch(self.supported)
        self.trial.remove('one')
        self.assertEqual(50,self.trial.summary()['combined_m'])
        self.trial.remove('two');self.assertEqual(0,self.trial.summary()['combined_m'])
