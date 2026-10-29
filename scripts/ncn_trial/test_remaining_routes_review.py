import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from .remaining_routes_review import load_record, propose_spans, assess


class RemainingRoutesReviewTests(unittest.TestCase):
    def test_retry_requires_pinned_complete_previous_summary_and_a_held_key(self):
        with TemporaryDirectory() as directory:
            root=Path(directory);(root/'capture').mkdir();(root/'discovery').mkdir();(root/'output').mkdir()
            routes=[{'official_route_key':f'NCN:{1000+i}'}for i in range(54)]
            def save(name,value):
                raw=json.dumps(value).encode();(root/name).write_bytes(raw)
                return hashlib.sha256(raw).hexdigest()
            queue=save('queue.json',{'routes':routes})
            plans=save('plans.json',{'routes':[{'route_key':r['official_route_key']}for r in routes]})
            capture=save('capture/manifest.json',{'queue_sha256':queue,'sources':[]})
            discovery=save('discovery/manifest.json',{'sources':[]})
            pins=save('input-pins.json',dict(queue=queue,plans=plans,capture=capture,discovery=discovery,existing_registry='0'*64))
            previous={'input_pins_sha256':pins,'routes':[{'route_key':r['official_route_key'],'status':'held'}for r in routes]}
            prior=save('output/summary.json',previous)
            with patch('scripts.ncn_trial.remaining_routes_review.registry_checkpoint',return_value={}):
                with self.assertRaisesRegex(ValueError,'summary checksum'):
                    assess(root,pins,[],['NCN:1000'],'0'*64)
                self.assertEqual(hashlib.sha256((root/'output/summary.json').read_bytes()).hexdigest(),prior)
                previous['routes'][0]['status']='supported_open_spans';prior=save('output/summary.json',previous)
                with self.assertRaisesRegex(ValueError,'Only explicitly held'):
                    assess(root,pins,[],['NCN:1000'],prior)
                self.assertEqual(hashlib.sha256((root/'output/summary.json').read_bytes()).hexdigest(),prior)

    def test_scope_proposal_cannot_cross_a_closed_feature_or_registry_conflict(self):
        feature={'type':'Feature','properties':{'FID':1},'geometry':{'type':'LineString','coordinates':[[0,0],[.002,0]]}}
        closed={'type':'Feature','properties':{'FID':2},'geometry':{'type':'LineString','coordinates':[[.001,0],[.002,0]]}}
        ways={42:{'node_records':[{'lon':0,'lat':0},{'lon':.0005,'lat':0},{'lon':.0015,'lat':0}]}}
        self.assertEqual(propose_spans(ways,[feature,closed],[feature],set()),[{'way_id':42,'from_vertex':0,'to_vertex':1}])
        self.assertEqual(propose_spans(ways,[feature],[feature],{42}),[])

    def test_official_page_completeness_and_input_hashes_are_independently_verified(self):
        with TemporaryDirectory() as directory:
            root=Path(directory)
            def save(name,value,kind):
                raw=json.dumps(value).encode();(root/name).write_bytes(gzip.compress(raw))
                return {'file':name,'kind':kind,'sha256':hashlib.sha256(raw).hexdigest()}
            feature={'properties':{'FID':1}}
            count=save('count.gz',{'count':1},'official_count')
            page=save('page.gz',{'features':[feature]},'official_page')
            record=save('official.gz',{'features':[feature]},'official')
            record.update(count_response=count,pages=[page])
            self.assertEqual(len(load_record(root,record)[0]['features']),1)
            record['pages']=[page,page]
            with self.assertRaisesRegex(ValueError,'accounting'):load_record(root,record)
            record['pages']=[page];record['sha256']='0'*64
            with self.assertRaisesRegex(ValueError,'changed'):load_record(root,record)
            record['file']='../escape.gz'
            with self.assertRaisesRegex(ValueError,'escapes'):load_record(root,record)


if __name__=='__main__':unittest.main()
