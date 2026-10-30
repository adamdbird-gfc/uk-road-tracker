import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from scripts.review_capture_partition import partition


class CapturePartitionTest(unittest.TestCase):
    def run_case(self,count=12,change=None):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);original=root/'original.json';plan=root/'plan.json'
            raw=[[0,i*.000001,5,1000+i*1000,0] for i in range(count)]
            original.write_text(json.dumps({'journey':{'raw_capture_samples':raw,'route_geometry':{'coordinates':[[1,2],[3,4]]}}}))
            before=original.read_bytes()
            spec={'input_sha256':hashlib.sha256(before).hexdigest(),'partitions':[
                {'start':0,'end':count//2,'kind':'movement_candidate','reason':'synthetic reviewed boundary; transport uncertain'},
                {'start':count//2,'end':count,'kind':'stationary_or_unresolved','reason':'retain every observation'}]}
            if change:change(spec)
            plan.write_text(json.dumps(spec))
            m=partition(original,plan,root/'review');self.assertEqual(original.read_bytes(),before)
            self.assertEqual((root/'review/original-export.json').read_bytes(),before)
            restored=[]
            for item in m['partitions']:
                record=json.loads((root/'review'/item['file']).read_bytes())
                restored.extend(record['raw_samples']);self.assertFalse(record['grants_completion_credit'])
                self.assertFalse(record['transport_confirmed']);self.assertNotIn('route_geometry',record)
            self.assertEqual(restored,raw);self.assertFalse(m['applied_to_device'])
            with self.assertRaises(FileExistsError):partition(original,plan,root/'review')
    def test_complete_non_destructive_review(self):self.run_case()
    def test_integer_boundary_counts_are_not_truncated(self):
        for n in [32766,32767,32768,39120]:self.run_case(n)
    def test_gap_and_overlap_rejected_before_writing(self):
        for delta in [-1,1]:
            with self.assertRaises(ValueError):self.run_case(change=lambda p:p['partitions'][1].update(start=6+delta))
    def test_unpinned_original_rejected(self):
        with self.assertRaises(ValueError):self.run_case(change=lambda p:p.update(input_sha256='0'*64))
    def test_tail_loss_rejected(self):
        with self.assertRaises(ValueError):self.run_case(change=lambda p:p['partitions'][1].update(end=11))


if __name__=='__main__':unittest.main()
