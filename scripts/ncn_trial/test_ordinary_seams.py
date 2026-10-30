"""Focused compiler regressions; public witnesses are not journey evidence."""
from collections import Counter
from copy import deepcopy
from dataclasses import replace
import gzip
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest

from shapely.geometry import LineString
from .coverage import Evidence, Section, TrialReference, merge_intervals
from .inventory_completion import InventoryLedger
from .open_completion import (bind_edges, build_completion, build_verified_completion,
                              ordinary_seam_partition, read_completion, sha, POLICY, SEAM_POLICY)
from .saved_paths import KnownPath

FIXTURES = Path(__file__).resolve().parents[2] / 'tests/fixtures/ncn-gap-seams'
WITNESSES_SHA = '9b741fa37ef7f74e29269ed77de2d9d0068ed54744b18775b1a994b49a06612b'


def compile_witness(witness, enabled=True, source=None, targets=None, modes=None):
    source = source or witness['source_edge_metric']
    targets = targets or list(zip(witness['target_keys'], witness['target_edges_metric']))
    key = witness['source_section_key']; route = witness['route_key']
    reference = TrialReference('source', [Section(key, LineString(source), (route,))])
    paths = [KnownPath(witness['source_way_id'], tuple(source), (key,), 'source',
                       tuple(modes or witness['admitted_modes']), 'exact_source_spans',
                       tuple(range(1, len(source)+1)))]
    logical, bindings, audit = bind_edges(reference, paths,
        [(key, LineString(coords)) for key, coords in targets], route, ordinary_seams=enabled)
    return InventoryLedger(reference, logical, bindings), audit


class OrdinarySeamTests(unittest.TestCase):
    def setUp(self):
        self.witnesses = json.loads((FIXTURES/'diagnostic-witnesses.json').read_bytes())
        self.w = self.witnesses['ordinary_candidates'][0]

    def test_research_fixture_bytes_are_pinned(self):
        self.assertEqual(sha((FIXTURES/'diagnostic-witnesses.json').read_bytes()), WITNESSES_SHA)

    def test_pinned_ordinary_witnesses_partition_without_losing_old_support(self):
        for w in self.witnesses['ordinary_candidates']:
            with self.subTest(route=w['route_key']):
                old, before = compile_witness(w, False)
                ledger, after = compile_witness(w)
                self.assertGreater(before['ambiguous_source_occurrence_m'], 0)
                self.assertEqual(after['ambiguous_source_occurrence_m'], 0)
                self.assertEqual(after['unbound_source_occurrence_m'], 0)
                self.assertTrue(set(old.bindings) <= set(ledger.bindings))
                spans = sorted((b.physical_start, b.physical_end) for b in ledger.bindings)
                self.assertEqual(merge_intervals(spans), [(0., ledger.reference.sections[w['source_section_key']].line.length)])
                self.assertTrue(all(a[1] <= b[0]+1e-7 for a, b in zip(spans, spans[1:])))
                self.assertTrue(all(b.modes == tuple(w['admitted_modes']) for b in ledger.bindings))
                self.assertEqual(old.logical, ledger.logical)
                self.assertEqual(old.reference.sections, ledger.reference.sections)

    def test_partial_journey_on_each_side_cannot_credit_the_other_side(self):
        for w in self.witnesses['ordinary_candidates']:
            ledger, _ = compile_witness(w); old, _ = compile_witness(w, False)
            added = sorted(set(ledger.bindings)-set(old.bindings), key=lambda b:b.physical_start)
            self.assertEqual(len(added), 2)
            self.assertEqual(added[0].physical_end, added[1].physical_start)
            for b in added:
                # Strictly inside one half of the source interval.
                a = b.physical_start+(b.physical_end-b.physical_start)*.25
                z = b.physical_start+(b.physical_end-b.physical_start)*.75
                ledger.put('partial', Evidence('source', 'cycling', ((b.physical_key,a,z),), ()))
                summary = ledger.summary()['logical_sections']
                self.assertAlmostEqual(summary[b.logical_key]['combined_m'], abs(b.logical_end-b.logical_start)*.5)
                for key in ledger.logical:
                    if key != b.logical_key:
                        self.assertEqual(summary[key]['combined_m'], 0)

    def test_reversing_source_or_target_and_reordering_targets_preserves_capacity(self):
        w = self.w; old, _ = compile_witness(w)
        targets = [(k,list(reversed(c))) for k,c in reversed(list(zip(w['target_keys'],w['target_edges_metric'])))]
        ledger, _ = compile_witness(w, source=list(reversed(w['source_edge_metric'])), targets=targets)
        for result in (old,ledger):
            result.put('full',Evidence('source','cycling',((w['source_section_key'],0,result.reference.sections[w['source_section_key']].line.length),),()))
        self.assertAlmostEqual(old.summary()['logical_routes'][w['route_key']]['combined_m'],ledger.summary()['logical_routes'][w['route_key']]['combined_m'])

    def test_genuine_hairpin_keeps_exact_original_ambiguity(self):
        w = self.witnesses['genuine_hairpin_negative']
        old, before = compile_witness(w, False); ledger, after = compile_witness(w)
        self.assertEqual(old.bindings, ledger.bindings)
        self.assertEqual(before, after)
        self.assertGreater(after['ambiguous_source_occurrence_m'], 40)

    def test_complete_graph_branch_even_outside_direction_gate_blocks_seam(self):
        w = self.w; vertex=w['target_edges_metric'][0][1]
        targets=list(zip(w['target_keys'],w['target_edges_metric']))+[('branch',[vertex,[vertex[0]+50,vertex[1]]])]
        old,before=compile_witness(w,False,targets=targets); ledger,after=compile_witness(w,targets=targets)
        self.assertEqual(old.bindings, ledger.bindings)
        self.assertEqual(before, after)

    def test_third_active_parallel_candidate_retains_ambiguity(self):
        w=self.w
        targets=list(zip(w['target_keys'],w['target_edges_metric']))+[('parallel',w['target_edges_metric'][0])]
        old,before=compile_witness(w,False,targets=targets); ledger,after=compile_witness(w,targets=targets)
        self.assertEqual(old.bindings,ledger.bindings)
        self.assertEqual(before,after)

    def test_parallel_disconnected_and_crossing_targets_do_not_gain_coverage(self):
        cases=[([(0,1),(100,1)], [[(0,0),(100,0)],[(0,4),(100,4)]]),
               ([(0,0),(100,0)], [[(50,-40),(50,40)],[(51,-40),(51,40)]]),
               ([(0,0),(100,0)], [[(0,2),(50,2)],[(50,3),(100,3)]])]
        for source,lines in cases:
            targets=[(str(i),c) for i,c in enumerate(lines)]
            old,before=compile_witness(self.w,False,source=source,targets=targets)
            ledger,after=compile_witness(self.w,source=source,targets=targets)
            self.assertEqual(old.bindings,ledger.bindings)
            self.assertEqual(before,after)

    def test_one_sided_source_or_endpoint_cannot_anchor_a_seam(self):
        w=self.w; a,z=w['source_edge_metric']; line=LineString([a,z]); station=4.45089718399978
        for source in ([a,list(line.interpolate(3).coords)[0]], [list(line.interpolate(5).coords)[0],z]):
            old,before=compile_witness(w,False,source=source); ledger,after=compile_witness(w,source=source)
            self.assertEqual(old.bindings,ledger.bindings)
            self.assertEqual(before,after)
        # Explicit endpoint equality is rejected even with both candidates.
        from .open_completion import edge_interval
        edges=list(zip(w['target_keys'],map(LineString,w['target_edges_metric'])))
        active=[(i,*edge_interval(a,z,line)) for i,(_,line) in enumerate(edges)]
        degrees=Counter(tuple(p) for _,line in edges for p in line.coords)
        self.assertIsNone(ordinary_seam_partition(a,z,active,edges,degrees,station,6.08240710865381))

    def test_modes_repeated_journeys_edit_deletion_and_return_occurrences(self):
        w=self.w; source=w['source_edge_metric']+[w['source_edge_metric'][0]]
        ledger,_=compile_witness(w,source=source,modes=['cycling']); length=ledger.reference.sections[w['source_section_key']].line.length
        for key in ('a','b'):
            ledger.put(key,Evidence('source','cycling',((w['source_section_key'],0,length),),()))
        total=ledger.summary()['logical_routes'][w['route_key']]['combined_m']
        ledger.remove('a')
        self.assertEqual(ledger.summary()['logical_routes'][w['route_key']]['combined_m'],total)
        ledger.put('b',Evidence('source','cycling',((w['source_section_key'],length/2,length),),()))
        self.assertAlmostEqual(ledger.summary()['logical_routes'][w['route_key']]['combined_m'],total)
        for mode in ('on_foot','driving'):
            ledger.put('b',Evidence('source',mode,((w['source_section_key'],0,length),),()))
            self.assertEqual(ledger.summary()['logical_routes'][w['route_key']]['combined_m'],0)
        ledger.remove('b')
        self.assertEqual(ledger.summary()['logical_routes'][w['route_key']]['combined_m'],0)

    def test_versioned_policy_roundtrip_and_deferred_mask(self):
        from .test_source_identity_review import setup,build
        from .remaining_routes_review import emit
        from .source_identity_review import read_catalogue
        values,review=setup(); candidate,audit=build(values,review)
        with TemporaryDirectory() as tmp:
            root=Path(tmp);receipt=emit(root,candidate,audit,review,json.dumps(review).encode(),values[5]['features'])
            args=read_catalogue(root,receipt['manifest_sha256'])
            provenance=json.loads(gzip.decompress((root/'associations.json.gz').read_bytes()))['provenance']
            old,old_bundle,old_plan=build_completion(root,receipt['manifest_sha256'])
            ledger,bundle,plan=build_completion(root,receipt['manifest_sha256'],ordinary_seams=True)
            self.assertEqual(old_plan['policy'],POLICY); self.assertEqual(plan['policy'],SEAM_POLICY)
            self.assertNotEqual(old_plan['reference_version'],plan['reference_version'])
            self.assertEqual(json.loads(old_bundle)['paths'],json.loads(bundle)['paths'])
            for field in ('physical_sections_metric','logical_sections','catalogue_manifest_sha256','canonical_open_length_m'):
                self.assertEqual(old_plan[field],plan[field])
            raw=json.dumps(plan).encode()
            restored,_,_=read_completion(raw,bundle,sha(raw),sha(bundle),ordinary_seams=True)
            self.assertEqual(restored.summary(),ledger.summary())
            with self.assertRaisesRegex(ValueError,'policy'):
                read_completion(raw,bundle,sha(raw),sha(bundle))
            for bad_raw,bad_bundle in ((raw+b' ',bundle),(raw,bundle+b' ')):
                with self.assertRaisesRegex(ValueError,'checksum'):
                    read_completion(bad_raw,bad_bundle,sha(raw),sha(bundle),ordinary_seams=True)
            blocked,_,blocked_plan=build_verified_completion(*args,'a'*64,provenance,[42],ordinary_seams=True)
            self.assertEqual(blocked.bindings,())
            self.assertEqual(blocked_plan['maximum_bound_open_length_m']['combined'],0)


if __name__=='__main__':
    unittest.main()
