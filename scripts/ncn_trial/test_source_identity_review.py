from copy import deepcopy
import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from matched_path_evidence import digest
from .confirmed_paths import build_candidate
from .source_identity_review import read_catalogue
from .test_confirmed_paths import inputs


def setup():
    values = inputs()
    values[3]['relation',1]['tags']['ref'] = '626'
    values[2][1]['tags']['ref'] = '626'
    values[7][1]['tags']['ref'] = '626'
    review = {'schema_version':1,'source_route_key':'NCN:626','target_route_key':values[0],
        'policy':'reviewed_source_edges_to_official_open_target','route_alias':False,
        'old_journey_evidence_transferred':False,'whole_published_route_ready':False,
        'official_network_sha256':values[6],'official_features_sha256':digest(values[5]['features']),
        'approved_open_features_sha256':digest(values[5]['features']),
        'source_snapshot_sha256':digest(sorted(values[4])), 'provider_sha256':digest(values[7]),
        'source_relations_sha256':digest([values[3]['relation',1]]),
        'source_relation_ids':[1],'approved_official_feature_ids':[1],
        'approved_source_spans':[{'way_id':42,'from_vertex':0,'to_vertex':2}],
        'identity_evidence_urls':['https://example.test/pinned-identity-review']}
    return values, review


def build(values, review):
    raw = json.dumps(review).encode()
    return build_candidate(*values, source_identity_review=raw,
                          expected_identity_review_sha256=hashlib.sha256(raw).hexdigest())


class SourceIdentityReviewTests(unittest.TestCase):
    def test_default_rejects_mislabelling_and_review_preserves_original_source(self):
        values, review = setup()
        with self.assertRaises(ValueError):build_candidate(*values)
        result, audit = build(values, review)
        self.assertEqual(audit['recorded_source_route_key'],'NCN:626')
        self.assertEqual(result[1]['sections'][0]['routes'],['NCN:137:eligible-source-spans'])
        self.assertEqual(values[3]['relation',1]['tags']['ref'],'626')
        self.assertFalse(audit['route_alias'])
        self.assertFalse(audit['old_journey_evidence_transferred'])

    def test_only_listed_source_edges_are_admitted(self):
        values, review = setup()
        review['approved_source_spans'][0]['to_vertex']=1
        result, audit = build(values, review)
        self.assertEqual(result[1]['sections'][0]['to_vertex'],1)
        self.assertEqual(audit['partial_way_count'],1)
        values[3]['way',99]=deepcopy(values[3]['way',42])
        self.assertEqual([p['way_id']for p in json.loads(build(values,review)[0][0])['paths']],['42'])

    def test_source_snapshot_roots_provider_and_official_scope_are_pinned(self):
        for field, value in [('source_snapshot_sha256','0'*64),('source_relations_sha256','0'*64),
                             ('provider_sha256','0'*64),('official_features_sha256','0'*64),
                             ('official_network_sha256','0'*64),('source_relation_ids',[2]),
                             ('target_route_key','NCN:138'),('source_route_key','NCN:625'),
                             ('route_alias',True),('old_journey_evidence_transferred',True),
                             ('identity_evidence_urls',[])]:
            values, review = setup(); review[field]=value
            with self.assertRaises(ValueError,msg=field):build(values,review)

    def test_missing_wrong_pin_or_mixed_review_is_rejected(self):
        values, review = setup(); raw=json.dumps(review).encode()
        for kwargs in [dict(source_identity_review=raw),dict(expected_identity_review_sha256='0'*64),
                       dict(source_identity_review=raw,expected_identity_review_sha256='0'*64),
                       dict(source_identity_review=raw,expected_identity_review_sha256=hashlib.sha256(raw).hexdigest(),numbered_alternative_review=b'{}')]:
            with self.assertRaises(ValueError):build_candidate(*values,**kwargs)

    def test_proposed_closed_unknown_alternative_and_diversion_cannot_be_promoted(self):
        for field,value in [('OpenStatus','Temporary Closure'),('OpenStatus',None),
                            ('OpenStatus','Proposed'),('RouteCat','Alternative Route'),
                            ('RouteCat','Temporary Diversion'),('Desc_','Ferry')]:
            values,review=setup();values[5]['features'][0]['properties'][field]=value
            review['official_features_sha256']=digest(values[5]['features'])
            with self.assertRaises(ValueError):build(values,review)

    def test_adjacent_closed_extent_stays_excluded(self):
        values,review=setup();closed=deepcopy(values[5]['features'][0])
        closed['properties'].update(FID=2,OpenStatus='Temporary Closure')
        closed['geometry']['coordinates']=[[.00075,0],[.001,0]]
        values[5]['features'].append(closed)
        review['official_features_sha256']=digest(values[5]['features'])
        result,audit=build(values,review)
        self.assertEqual(result[1]['sections'][0]['to_vertex'],1)

    def test_access_geometry_missing_nodes_and_member_changes_remain_gated(self):
        for mutate in [lambda v:v[3]['way',42]['tags'].update(highway='construction'),
                       lambda v:v[3]['node',2].update(lat=.001),
                       lambda v:v[3].pop(('node',2))]:
            values,review=setup();mutate(values)
            self.assertIsNone(build(values,review)[0])
        values,review=setup();values[2][1]['ways']=[]
        with self.assertRaises(ValueError):build(values,review)

    def test_invalid_unlisted_duplicate_and_empty_spans_are_rejected(self):
        for spans in [[],[{'way_id':99,'from_vertex':0,'to_vertex':1}],
                       [{'way_id':42,'from_vertex':0,'to_vertex':3}],
                       [{'way_id':42,'from_vertex':0,'to_vertex':1}]*2]:
            values,review=setup();review['approved_source_spans']=spans
            with self.assertRaises(ValueError):build(values,review)

    def test_travel_on_unapproved_part_of_same_way_earns_no_target_credit(self):
        from shapely.geometry import LineString
        from .association_bundle import read_bundle
        from .coverage import TrialReference, Section, TrialLedger
        from .journey_replay import projector
        from .saved_paths import assess_saved_paths
        from matched_path_evidence import public_way_reference, build_matched_path_evidence
        values,review=setup();review['approved_source_spans'][0]['to_vertex']=1
        raw,payload,metrics=build(values,review)[0]
        meta=payload['metadata'];project=projector(meta)
        ref=TrialReference(payload['reference_version'],[Section(s['key'],
            LineString([project(c)for c in s['coordinates']]),tuple(s['routes']))for s in payload['sections']])
        paths=read_bundle(raw,ref,meta['projection_origin'],metrics['content_sha256'])
        coords=[[.00065,0],[.00095,0]]
        source=public_way_reference('osm_geofabrik_test','latest','42','path',{'foot':'yes'},[[0,0],[.0005,0],[.001,0]])
        export={'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
            'journey':{'mode':'walking','processing_status':'complete','processing_result':{
                'matcher':'preloaded_pedestrian_network_v4','geojson':{'features':[{'geometry':{'type':'LineString','coordinates':coords}}]},
                'matched_path_evidence':build_matched_path_evidence(coords,['w'],{'w':{'matched_reference':source}})}}}
        assessment,_=assess_saved_paths(export,ref,meta,paths)
        ledger=TrialLedger(ref);ledger.put('unapproved-half',assessment.evidence)
        self.assertEqual(ledger.summary()['combined_m'],0)

    def test_catalogue_roundtrip_keeps_open_extent_and_rejects_changed_bytes(self):
        from shapely.geometry import LineString
        from .journey_replay import projector
        values,review=setup();raw_review=json.dumps(review).encode()
        review['approved_source_spans'][0]['to_vertex']=1
        raw_review=json.dumps(review).encode()
        result,_=build(values,review);bundle,payload,_=result
        project=projector(payload['metadata'])
        length=LineString([project(c)for c in values[5]['features'][0]['geometry']['coordinates']]).length
        extent={'route_key':values[0],'basis':'official_open_main_only',
                'completion_binding_ready':False,'features':values[5]['features'],
                'projection_origin':payload['metadata']['projection_origin'],
                'canonical_open_union_m':length,'unmatched_official_open_extent_retained':True}
        files={'reference.json.gz':json.dumps(payload).encode(),'associations.json.gz':bundle,
               'identity-review.json':raw_review,'open-extent.json':json.dumps(extent).encode()}
        m={'schema_version':1,'target_route_key':values[0],'route_alias':False,'whole_published_route_ready':False}
        for name,field in [('reference.json.gz','reference_sha256'),('associations.json.gz','association_sha256'),
                           ('identity-review.json','source_identity_review_sha256'),('open-extent.json','open_extent_sha256')]:
            m[field]=hashlib.sha256(files[name]).hexdigest()
        mraw=json.dumps(m).encode();pin=hashlib.sha256(mraw).hexdigest()
        with TemporaryDirectory()as directory:
            root=Path(directory);(root/'manifest.json').write_bytes(mraw)
            for name,raw in files.items():(root/name).write_bytes(gzip.compress(raw)if name.endswith('.gz')else raw)
            loaded=read_catalogue(root,pin)
            self.assertEqual(len(loaded[2]),1)
            self.assertGreater(loaded[3]['canonical_open_union_m'],sum(s.line.length for s in loaded[0].sections.values())*1.99)
            with self.assertRaises(ValueError):read_catalogue(root,'0'*64)
            for change in [dict(canonical_open_union_m=length/2),
                           dict(unmatched_official_open_extent_retained=False)]:
                bad=json.dumps({**extent,**change}).encode()
                changed={**m,'open_extent_sha256':hashlib.sha256(bad).hexdigest()}
                changed_raw=json.dumps(changed).encode()
                (root/'manifest.json').write_bytes(changed_raw)
                (root/'open-extent.json').write_bytes(bad)
                with self.assertRaises(ValueError):read_catalogue(root,hashlib.sha256(changed_raw).hexdigest())
            shortened=deepcopy(extent)
            shortened['features'][0]['geometry']['coordinates'][-1][0]=.0005
            shortened['canonical_open_union_m']=length/2
            bad=json.dumps(shortened).encode()
            changed={**m,'open_extent_sha256':hashlib.sha256(bad).hexdigest()}
            changed_raw=json.dumps(changed).encode()
            (root/'manifest.json').write_bytes(changed_raw)
            (root/'open-extent.json').write_bytes(bad)
            with self.assertRaises(ValueError):read_catalogue(root,hashlib.sha256(changed_raw).hexdigest())
            (root/'manifest.json').write_bytes(mraw)
            (root/'open-extent.json').write_bytes(files['open-extent.json']+b' ')
            with self.assertRaises(ValueError):read_catalogue(root,pin)


if __name__=='__main__':unittest.main()
