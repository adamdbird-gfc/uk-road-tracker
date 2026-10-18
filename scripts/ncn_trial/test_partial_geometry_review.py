from copy import deepcopy
import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from matched_path_evidence import digest
from .confirmed_paths import build_candidate, selected_routes
from .partial_geometry_review import select_cases, load_capture, fingerprint, existing_registry
from .source_identity_review import policy_matches_source
from .test_confirmed_paths import inputs


def selection():
    row={'official_route_key':'NCN:137','state':'partial_source_geometry',
         'source_geometry_complete':False,'uk_metadata_anchor':True,'candidate_relation_ids':[1]}
    return {'routes':[row]},{1:{'member_lists_complete':True}}


def review(values):
    key=values[0]
    return {'schema_version':1,'source_route_key':key,'target_route_key':key,
        'policy':'partial_provider_verified_spans_only','route_alias':False,
        'old_journey_evidence_transferred':False,'whole_published_route_ready':False,
        'official_network_sha256':values[6],'official_features_sha256':digest(values[5]['features']),
        'approved_open_features_sha256':digest(values[5]['features']),
        'source_snapshot_sha256':digest(sorted(values[4])),'provider_sha256':digest(values[7]),
        'source_relations_sha256':digest([values[3]['relation',1]]),'source_relation_ids':[1],
        'approved_official_feature_ids':[1],
        'approved_source_spans':[{'way_id':w['way_id'],'from_vertex':0,'to_vertex':2}for w in values[2][1]['ways']],
        'identity_evidence_urls':['https://example.test/pinned-partial-review']}


def build(values):
    raw=json.dumps(review(values)).encode()
    return build_candidate(*values,source_identity_review=raw,expected_identity_review_sha256=hashlib.sha256(raw).hexdigest())


class PartialGeometryReviewTests(unittest.TestCase):
    def test_selection_is_explicit_partial_anchored_and_does_not_relax_ordinary_gate(self):
        report,profiles=selection()
        self.assertEqual(len(select_cases(report,profiles,['NCN:137'])),1)
        with self.assertRaises(ValueError):selected_routes(report,{'complete':True,'routes':['NCN:137'],'seed_relation_ids':[1]})
        for keys in [[],['NCN:137','NCN:137'],['NCN:138']]:
            with self.assertRaises(ValueError):select_cases(report,profiles,keys)
        for field,value in [('uk_metadata_anchor',False),('state','source_scope_review_required')]:
            r,p=selection();r['routes'][0][field]=value
            with self.assertRaises(ValueError):select_cases(r,p,['NCN:137'])
        profiles[1]['member_lists_complete']=False
        with self.assertRaises(ValueError):select_cases(report,profiles,['NCN:137'])

    def test_conflicting_provider_way_is_held_while_another_verified_way_is_admitted(self):
        values=inputs()
        values[3]['way',43]={**deepcopy(values[3]['way',42]),'id':43}
        values[3]['relation',1]['members'].append({'type':'way','ref':43,'role':''})
        values[2][1]['ways'].append({'way_id':43})
        part=deepcopy(values[7][1]['route']['ways'][0]);part['id']=43
        values[7][1]['route']['ways'].append(part)
        bad=deepcopy(values[7][1]['route']['ways'][0]);bad['geometry']['coordinates'][-1][1]=20
        values[7][1]['route']['ways'].append(bad)
        result,audit=build(values)
        self.assertEqual([p['way_id']for p in json.loads(result[0])['paths']],['43'])
        self.assertEqual(audit['held'][0]['way_id'],42)

    def test_unlisted_provider_way_never_enters_registry(self):
        values=inputs();part=deepcopy(values[7][1]['route']['ways'][0]);part['id']=99
        values[7][1]['route']['ways'].append(part)
        result,_=build(values)
        self.assertEqual([p['way_id']for p in json.loads(result[0])['paths']],['42'])

    def test_nonopen_root_and_nested_membership_are_excluded(self):
        for field,state in [('state','proposed'),('state','construction'),('status','closed')]:
            values=inputs();values[3]['relation',1]['tags'][field]=state
            result,audit=build_candidate(*values)
            self.assertIsNone(result);self.assertEqual(audit['held'][0]['reason'],'proposed_or_closed_source_membership')
        values=inputs()
        values[3]['relation',2]={'type':'relation','id':2,'tags':{'type':'route','route':'bicycle','state':'proposed'},'members':[{'type':'way','ref':42,'role':''}]}
        values[3]['relation',1]['members']=[{'type':'relation','ref':2,'role':''}]
        self.assertIsNone(build_candidate(*values)[0])

    def test_independently_open_membership_chain_preserves_shared_way(self):
        values=inputs()
        values[3]['relation',2]={'type':'relation','id':2,'tags':{'type':'route','route':'bicycle','state':'proposed'},'members':[{'type':'way','ref':42,'role':''}]}
        values[3]['relation',1]['members'].append({'type':'relation','ref':2,'role':''})
        self.assertEqual(build_candidate(*values)[1]['accepted_way_count'],1)

    def test_partial_and_reclassification_policies_cannot_exchange_identity_rules(self):
        self.assertTrue(policy_matches_source({'source_route_key':'NCN:137','policy':'partial_provider_verified_spans_only'},'NCN:137'))
        self.assertFalse(policy_matches_source({'source_route_key':'NCN:138','policy':'partial_provider_verified_spans_only'},'NCN:137'))
        self.assertFalse(policy_matches_source({'source_route_key':'NCN:137','policy':'reviewed_source_edges_to_official_open_target'},'NCN:137'))

    def test_registry_fingerprint_preserves_shared_physical_identity_and_detects_edits(self):
        a=fingerprint([1,2],[[0,0],[.001,0]])
        self.assertEqual(a,fingerprint([2,1],[[.001,0],[0,0]]))
        self.assertNotEqual(a,fingerprint([1,3],[[0,0],[.001,0]]))
        self.assertNotEqual(a,fingerprint([1,2],[[0,0],[.0011,0]]))
        with TemporaryDirectory()as directory:
            p=Path(directory)/'NCN-137';p.mkdir()
            row={'way_id':'42','osm_node_ids':[1,2],'coordinates':[[0,0],[.001,0]]}
            (p/'associations.json.gz').write_bytes(gzip.compress(json.dumps({'paths':[row,row]}).encode()))
            registry,_=existing_registry([p.parent]);self.assertEqual(registry,{'42':{a}})

    def test_capture_pins_provider_identity_and_optional_failure_receipts(self):
        with TemporaryDirectory()as directory:
            root=Path(directory);raw=b'{"type":"relation","id":1}'
            (root/'one.gz').write_bytes(gzip.compress(raw));(root/'optional').write_bytes(b'404')
            m={'complete':True,'sources':[{'file':'one.gz','kind':'provider','relation_id':1,'sha256':hashlib.sha256(raw).hexdigest()}],
               'supplementary_attempts':[{'file':'optional','required_for_admission':False,'sha256':hashlib.sha256(b'404').hexdigest()}]}
            def write():
                data=json.dumps(m).encode();(root/'manifest.json').write_bytes(data);return hashlib.sha256(data).hexdigest()
            pin=write();self.assertIn(('provider',1),load_capture(root,pin)[1])
            with self.assertRaises(ValueError):load_capture(root,'0'*64)
            m['sources'][0]['relation_id']=2
            with self.assertRaises(ValueError):load_capture(root,write())
            m['sources'][0]['relation_id']=1;(root/'optional').write_bytes(b'changed')
            with self.assertRaises(ValueError):load_capture(root,write())


if __name__=='__main__':unittest.main()
