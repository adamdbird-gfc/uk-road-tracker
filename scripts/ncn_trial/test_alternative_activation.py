from pathlib import Path
import json
import tempfile
import unittest
from .activation import ActivationTrial,JourneyRecord
from .route177_alternatives import activation_trial
from .saved_paths import assess_saved_paths
from . import test_route177_alternatives as export_fixture


class AlternativeActivationTests(unittest.TestCase):
    def setUp(self):
        self.worker,self.data,self.paths,self.manifest=activation_trial()
        helper=export_fixture.Route177AlternativesTests();helper.setUp();self.helper=helper
        self.values={};self.records=[]
        for label,mode in [('west-north-foot','walking'),('west-south','cycling'),('east-south-shared','cycling')]:
            itinerary=next(i for i in self.manifest['itineraries'] if i['key']==label)
            for way in itinerary['way_ids']:
                key=label+':'+way
                self.values[key]=helper.export(int(way),mode)
                self.records.append(JourneyRecord(key,'1',mode,'timeline_import',1))

    def assess(self,record):
        return assess_saved_paths(self.values[record.key],self.worker.reference,self.data,self.paths)[0]

    def complete(self):
        while self.worker.status()['queued']:self.worker.run_batch(self.assess,limit=3)

    def test_on_activation_bulk_backfill_uses_alternatives_without_prompts(self):
        self.assertFalse(self.worker.active)
        self.assertEqual(self.worker.run_batch(lambda _:self.fail('Inactive must not assess')),0)
        self.worker.activate(self.records,100,complete_snapshot=True);self.complete()
        summary=self.worker.summary();west=next(v for k,v in summary['logical_sections'].items() if '7345' in k)
        self.assertAlmostEqual(west['combined_m'],west['length_m'],places=6)
        self.assertAlmostEqual(west['on_foot_m'],west['cycling_m'],places=6)
        self.assertLess(summary['logical_routes']['NCN:177:reviewed-pilot']['completed_fraction'],1)
        self.assertEqual(self.worker.review_candidates(),[])
        self.assertEqual(self.worker.status()['failed'],0)

    def test_checkpoint_restore_keeps_deduplicated_progress_and_resume(self):
        self.worker.activate(self.records,100,complete_snapshot=True)
        self.worker.run_batch(self.assess,limit=3)
        self.worker=ActivationTrial.restore(self.worker.reference,self.worker.export(),ledger_factory=self.worker.ledger_factory,accounting_key=self.worker.accounting_key)
        self.complete();before=self.worker.summary()
        self.worker.deactivate();self.worker.activate(self.records,200,complete_snapshot=True)
        self.assertEqual(self.worker.run_batch(lambda _:self.fail('Unchanged journey reprocessed')),0)
        self.assertEqual(before,self.worker.summary())

    def test_policy_manifest_change_rejects_old_checkpoint(self):
        self.worker.activate(self.records,100,complete_snapshot=True);self.complete();raw=self.worker.export()
        folder=Path(__file__).resolve().parents[2]/'tests/fixtures/ncn-177-discrepancy'
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)
            (p/'public-snapshot.json').write_bytes((folder/'public-snapshot.json').read_bytes())
            manifest=json.loads((folder/'alternative-bindings.json').read_bytes())
            manifest['policy']+=' Revised policy.'
            (p/'alternative-bindings.json').write_text(json.dumps(manifest))
            updated,data,paths,_=activation_trial(p)
            with self.assertRaisesRegex(ValueError,'migration'):
                ActivationTrial.restore(updated.reference,raw,ledger_factory=updated.ledger_factory,accounting_key=updated.accounting_key)
            updated.activate(self.records,300,complete_snapshot=True)
            updated.run_batch(lambda record:assess_saved_paths(self.values[record.key],updated.reference,data,paths)[0],limit=100)
            self.assertEqual(updated.status()['assessed'],len(self.records))
            self.assertEqual(updated.review_candidates(),[])

    def test_archive_deletion_reconciles_after_reactivation(self):
        self.worker.activate(self.records,100,complete_snapshot=True);self.complete()
        self.worker.deactivate();self.worker.activate([],200,complete_snapshot=True)
        self.assertEqual(sum(s['combined_m'] for s in self.worker.summary()['logical_sections'].values()),0)
        self.assertEqual(self.worker.status()['total'],0)

    def test_mode_correction_replaces_existing_logical_contribution(self):
        record=self.records[0]
        self.worker.activate([record],100,complete_snapshot=True);self.complete()
        changed=JourneyRecord(record.key,'2','driving','timeline_import',1)
        self.worker.upsert(changed,origin='correction');self.complete()
        self.assertEqual(sum(s['combined_m'] for s in self.worker.summary()['logical_sections'].values()),0)

    def test_default_physical_accounting_cannot_restore_alternative_checkpoint(self):
        self.worker.activate(self.records,100,complete_snapshot=True)
        with self.assertRaisesRegex(ValueError,'accounting migration'):
            ActivationTrial.restore(self.worker.reference,self.worker.export())
