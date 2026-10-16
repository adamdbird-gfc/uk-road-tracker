from copy import deepcopy
from dataclasses import replace
import hashlib
import json
import unittest
from shapely.geometry import LineString

from matched_path_evidence import digest, public_way_reference, build_matched_path_evidence
from .association_bundle import build_bundle, read_bundle
from .coverage import TrialLedger, TrialReference
from .extent_composition import compose_extent
from .extent_reference import build_extent_reference
from .saved_paths import assess_saved_paths
from .test_extent_composition import source, selection


class ExtentReferenceTests(unittest.TestCase):
    def setUp(self):
        self.raw = source(1,42,[0,1,2])
        self.coordinates = [[0,0],[.001,0],[.002,0]]
        self.extent = compose_extent({1:self.raw}, [selection(1,42,1,2)])
        self.network = b'pinned public reference test fixture'
        self.pin = hashlib.sha256(self.network).hexdigest()
        self.ref,self.meta,self.paths = self.build([('named:short', self.extent)])
        coords = [[.0005,0],[.0015,0]]
        item = public_way_reference('osm_geofabrik_test','snapshot','42','path',{'foot':'yes'},self.coordinates)
        evidence = build_matched_path_evidence(coords,['way'],{'way':{'matched_reference':item}})
        self.value = {'format':'roadprints_journey_diagnostics','schema_version':1,'export_complete':True,
                      'journey':{'mode':'walking','processing_status':'complete','processing_result':{
                          'matcher':'preloaded_pedestrian_network_v4', 'matched_path_evidence':evidence,
                          'geojson':{'features':[{'geometry':{'type':'LineString','coordinates':coords}}]},
                          'road_geojson':{'features':[{'geometry':{'type':'LineString','coordinates':self.coordinates}}]}}}}

    def build(self,candidates):
        return build_extent_reference(candidates,{1:self.raw},self.network,self.pin)

    def totals(self,value=None,paths=None):
        assessment,report = assess_saved_paths(value or self.value,self.ref,self.meta,self.paths if paths is None else paths)
        ledger=TrialLedger(self.ref);ledger.put('first',assessment.evidence);ledger.put('repeat',assessment.evidence)
        return ledger.summary(),report

    def test_boundary_crossing_credits_only_travel_inside_selected_span(self):
        totals,report=self.totals()
        self.assertAlmostEqual(55.598,totals['combined_m'],places=2)
        self.assertEqual('stored_source_identity',report['association_method'])
        self.assertEqual({'named:short'},set(totals['routes']))

    def test_legacy_exact_way_identity_uses_same_selected_span_boundary(self):
        value=deepcopy(self.value)
        del value['journey']['processing_result']['matched_path_evidence']
        self.assertAlmostEqual(self.totals()[0]['combined_m'],self.totals(value)[0]['combined_m'])

    def test_parallel_geometry_does_not_gain_named_extent_credit(self):
        value=deepcopy(self.value)
        result=value['journey']['processing_result']
        del result['matched_path_evidence']
        result['geojson']['features'][0]['geometry']['coordinates']=[[.0005,.00001],[.0015,.00001]]
        self.assertEqual(0,self.totals(value)[0]['combined_m'])

    def test_removed_edge_is_not_restored_by_boundary_clipping(self):
        value=deepcopy(self.value);value['journey']['journey_corrections']={'removed_matched_segments':[0]}
        self.assertEqual(0,self.totals(value)[0]['combined_m'])

    def test_overlapping_named_scopes_share_one_physical_distance(self):
        wide=compose_extent({1:self.raw},[selection(1,42,0,2)])
        self.ref,self.meta,self.paths=self.build([('named:short',self.extent),('named:wide',wide)])
        totals,_=self.totals()
        self.assertAlmostEqual(111.195,totals['combined_m'],places=2)
        self.assertAlmostEqual(55.598,totals['routes']['named:short']['combined_m'],places=2)
        self.assertAlmostEqual(111.195,totals['routes']['named:wide']['combined_m'],places=2)

    def test_download_roundtrip_preserves_exact_source_span_contract(self):
        provenance={'attribution':'OpenStreetMap contributors','licence':'ODbL-1.0',
                    'source_snapshot_sha256':hashlib.sha256(self.raw).hexdigest(),
                    'membership_evidence_sha256':digest(self.extent)}
        raw=build_bundle(self.paths,self.ref,self.meta['projection_origin'],provenance)
        paths=read_bundle(raw,self.ref,self.meta['projection_origin'],digest(json.loads(raw)))
        self.assertEqual('exact_source_spans',paths[0].target_geometry)
        self.assertAlmostEqual(55.598,self.totals(paths=paths)[0]['combined_m'],places=2)
        section=next(iter(self.ref.sections.values()))
        wrong=replace(section,line=LineString([(x,y+.5) for x,y in section.line.coords]))
        wrong_reference=TrialReference(self.ref.version,[wrong])
        with self.assertRaisesRegex(ValueError,'outside source way'):
            build_bundle(self.paths,wrong_reference,self.meta['projection_origin'],provenance)

    def test_network_or_extent_change_requires_reconciliation(self):
        with self.assertRaises(ValueError):
            build_extent_reference([('named:short',self.extent)],{1:self.raw},self.network+b'changed',self.pin)
        changed=deepcopy(self.extent);changed['selected_spans'][0]['from_vertex']=0
        with self.assertRaises(ValueError):self.build([('named:short',changed)])

    def test_driving_and_relabelled_pedestrian_match_do_not_gain_credit(self):
        for mode in ('driving','cycling'):
            value=deepcopy(self.value);value['journey']['mode']=mode
            self.assertEqual(0,self.totals(value)[0]['combined_m'])
