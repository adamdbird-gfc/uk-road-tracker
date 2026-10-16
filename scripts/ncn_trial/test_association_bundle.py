from copy import deepcopy
from dataclasses import replace
import json
import unittest

from shapely.geometry import LineString
from matched_path_evidence import digest
from .association_bundle import build_bundle, read_bundle, bundle_metrics
from .coverage import Section, TrialReference
from .fixtures import load_reference
from .saved_paths import fixture_paths
from . import test_saved_paths


class AssociationBundleTests(unittest.TestCase):
    def setUp(self):
        self.ref, self.meta = load_reference()
        self.paths = fixture_paths(self.ref)
        self.origin = self.meta['projection_origin']
        self.provenance = {'attribution': 'OpenStreetMap contributors', 'licence': 'ODbL-1.0',
                           'source_snapshot_sha256': 'a' * 64, 'membership_evidence_sha256': 'b' * 64}
        self.raw = build_bundle(self.paths, self.ref, self.origin, self.provenance)
        self.pin = digest(json.loads(self.raw))

    def read(self, value=None, reference=None, origin=None, pin=None):
        raw = self.raw if value is None else json.dumps(value)
        return read_bundle(raw, reference or self.ref, self.origin if origin is None else origin, pin or self.pin)

    def test_verified_fixture_roundtrip_and_deterministic_sizes(self):
        self.assertEqual(tuple(sorted(self.paths, key=lambda p:p.way_id)), self.read())
        self.assertEqual(self.raw, build_bundle(reversed(self.paths), self.ref, self.origin, self.provenance))
        self.assertEqual(3, bundle_metrics(self.raw)['paths'])
        self.assertLess(bundle_metrics(self.raw)['gzip_bytes'], len(self.raw))

    def test_truncated_or_altered_download_is_rejected(self):
        with self.assertRaises(ValueError):read_bundle(self.raw[:-20], self.ref, self.origin, self.pin)
        value=json.loads(self.raw);value['paths'].pop()
        with self.assertRaises(ValueError):self.read(value)

    def test_version_geometry_and_projection_are_independently_bound(self):
        with self.assertRaises(ValueError):self.read(reference=TrialReference('changed', list(self.ref.sections.values())))
        sections=list(self.ref.sections.values())
        sections[0]=replace(sections[0],line=LineString([(0,0),(10,0)]))
        with self.assertRaises(ValueError):self.read(reference=TrialReference(self.ref.version, sections))
        with self.assertRaises(ValueError):self.read(origin=[0,0])

    def test_invalid_rows_cannot_be_blessed_by_recomputing_checksum(self):
        for mutate in (
            lambda p:p['paths'].append(deepcopy(p['paths'][0])),
            lambda p:p['paths'][0].update(section_keys=['missing']),
            lambda p:p['paths'][0].update(modes=['driving']),
            lambda p:p['paths'][0].update(way_id='0'),
            lambda p:p['paths'][0].update(source_geometry_sha256='0'*64),
            lambda p:p.update(schema_version=True),
            lambda p:p['provenance'].update(membership_evidence_sha256=''),
        ):
            with self.subTest(mutate=mutate):
                value=json.loads(self.raw);mutate(value)
                with self.assertRaises(ValueError):self.read(value,pin=digest(value))

    def test_empty_region_and_nonqualifying_section_are_valid(self):
        raw=build_bundle([],self.ref,self.origin,self.provenance)
        self.assertEqual((),read_bundle(raw,self.ref,self.origin,digest(json.loads(raw))))
        sections=[replace(s,eligible=False) for s in self.ref.sections.values()]
        closed=TrialReference(self.ref.version,sections)
        raw=build_bundle(self.paths,closed,self.origin,self.provenance)
        self.assertEqual(3,len(read_bundle(raw,closed,self.origin,digest(json.loads(raw)))))

    def test_downloaded_paths_feed_existing_activation_assessor(self):
        # Reuse the actual activation assertion, replacing its direct registry
        # with the serialized reader to cover download -> match -> unique ledger.
        case=test_saved_paths.SavedPathTests();case.setUp()
        raw=build_bundle([case.path],case.ref,case.meta['projection_origin'],self.provenance)
        paths=read_bundle(raw,case.ref,case.meta['projection_origin'],digest(json.loads(raw)))
        original=case.assess
        case.assess=lambda value=None,paths_override=None:original(value,paths)
        case.test_activation_worker_consumes_saved_path_assessment_without_import_review_queue()
