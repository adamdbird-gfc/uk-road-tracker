import hashlib
import json
import unittest
from scripts.milepost_approval import build, verify, DATA, ASSET, ROOT

class ApprovalTest(unittest.TestCase):
    def test_reproducible_review_and_payload(self):
        verify()
        self.assertEqual(build(), build())

    def test_every_candidate_has_exactly_one_non_approval_decision(self):
        source=[json.loads(x)['osm_id'] for x in (DATA/'revision-1/catalogue.jsonl').read_text().splitlines()]
        decisions=[json.loads(x) for x in (DATA/'revision-2/decisions.jsonl').read_text().splitlines()]
        self.assertEqual(source,[r['candidate_id'] for r in decisions])
        self.assertEqual(889,len(set(source)))
        for row in decisions:
            self.assertEqual('held',row['status']);self.assertTrue(row['reasons'])
        self.assertEqual(4,sum(r['review_kind']=='targeted_source_review' for r in decisions))

    def test_runtime_contains_no_candidates_and_matches_immutable_source_pin(self):
        catalogue=json.loads(ASSET.read_text())
        self.assertEqual([],catalogue['records'])
        java=(ROOT/'android-prototype/app/src/main/java/com/roadprints/capture/MilepostCatalogue.java').read_text()
        self.assertIn(hashlib.sha256(ASSET.read_bytes()).hexdigest(),java)
        self.assertIn('APPROVED_COUNT=0',java)
        self.assertIn('READY=APPROVED_COUNT>0',java)
        self.assertNotIn('osm:node:',ASSET.read_text())
