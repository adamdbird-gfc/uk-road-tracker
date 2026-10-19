import json
from pathlib import Path
from contextlib import redirect_stdout
from io import StringIO
import tempfile
import unittest

from shapely.geometry import box
from bulk_settlement_inventory_trial import RoadAccumulator
from generate_ni_road_inventory_candidates import InventoryEvidence, build_all, validate_candidates
import test_ni_settlement_trial as trial_fixtures


class OverlapEvidenceTests(unittest.TestCase):
    def test_overlap_effect_measured_without_changing_candidate_membership(self):
        accumulator=RoadAccumulator([box(0,0,2,2),box(1,0,3,2)])
        codes=['N11000001','N11000002']
        evidence=InventoryEvidence(accumulator,codes,[{'codes':codes,'area_square_metres':2}])
        roads=[('residential','Overlap Only',[(1.2,1),(1.8,1)]),
               ('tertiary','Crossing Road',[(.5,1.8),(2.5,1.8)]),
               ('residential','Independent Evidence',[(1.2,.5),(1.8,.5)]),
               ('residential','Independent Evidence',[(.2,.5),(.8,.5)]),
               ('residential','Independent Evidence',[(2.2,.5),(2.8,.5)]),
               ('service','Excluded Depot',[(1.2,1.4),(1.8,1.4)])]
        for i,(highway,name,coordinates) in enumerate(roads):
            accumulator.accept(highway,name,coordinates)
            evidence.observe(highway,name,coordinates,i)
        report=evidence.report()
        for names,row in zip(accumulator.names,report['overlap_removal_sensitivity']):
            self.assertEqual(names,{'Overlap Only','Crossing Road','Independent Evidence'})
            self.assertEqual(row['names_relying_on_source_overlap'],['Overlap Only'])
            self.assertEqual(row['count_without_any_source_overlap'],2)
        self.assertEqual(len(report['overlaps'][0]['eligible_named_ways']),3)
        self.assertEqual(len(report['overlaps'][0]['excluded_named_ways']),1)

    def test_zero_count_evidence_retains_excluded_and_unnamed_classes(self):
        accumulator=RoadAccumulator([box(0,0,2,2)])
        evidence=InventoryEvidence(accumulator,['N11000001'],[])
        for highway,name in [('residential',None),('service','Depot Road'),('footway','53A & 53B Example Road')]:
            accumulator.accept(highway,name,[(.2,.2),(.8,.8)])
            evidence.observe(highway,name,[(.2,.2),(.8,.8)])
        row=evidence.report()['settlements'][0]
        self.assertEqual(accumulator.names[0],set())
        self.assertEqual(row['eligible_unnamed_ways'],1)
        self.assertEqual(row['excluded_named_ways'],2)
        self.assertEqual(row['excluded_named_samples']['service'],['Depot Road'])

    def test_point_touch_is_not_logged_as_way_inside_overlap(self):
        accumulator=RoadAccumulator([box(0,0,2,2),box(1,0,3,2)])
        evidence=InventoryEvidence(accumulator,['N11000001','N11000002'],[
            {'codes':['N11000001','N11000002'],'area_square_metres':2}])
        coordinates=[(.5,-.5),(1,0)]
        accumulator.accept('residential','Touch',coordinates)
        evidence.observe('residential','Touch',coordinates)
        self.assertEqual(evidence.report()['overlaps'][0]['eligible_named_ways'],[])
        self.assertIn('Touch',accumulator.names[0])


class AllCandidateTests(unittest.TestCase):
    def setup_fixture(self,root):
        bounds,sources=trial_fixtures.TrialTests().fixture(root)
        catalogue=[{'code':'N11000001','name':'EXAMPLE'}]
        (bounds/'ni-settlement-catalogue-candidate.json').write_text(json.dumps({'settlements':catalogue}))
        return bounds,sources,catalogue

    def test_full_scope_and_manifest_validated_with_real_pbf(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);bounds,sources,catalogue=self.setup_fixture(root);out=root/'all'
            with redirect_stdout(StringIO()):report,evidence=build_all(bounds,sources,out,expected_count=1)
            self.assertEqual(report['scope'],'all_source_settlements')
            self.assertEqual(report['settlement_count'],1)
            self.assertEqual(report['road_name_entries_across_settlements'],1)
            self.assertEqual(report['zero_count_settlements'],[])
            self.assertFalse((out/'trial-audit.json').exists())
            manifest=json.loads((out/'candidate-manifest.json').read_text())
            self.assertEqual(manifest['status'],'candidate_only')
            self.assertEqual(manifest['inventories'][0]['count'],1)
            self.assertEqual(evidence['settlements'][0]['excluded_named_samples']['footway'],['Excluded Path'])

    def test_incomplete_catalogue_aborts_before_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);bounds,sources,catalogue=self.setup_fixture(root)
            with self.assertRaises(ValueError):build_all(bounds,sources,root/'out')
            self.assertFalse((root/'out').exists())

    def test_changed_trial_file_and_corrupt_count_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);out=root/'out';out.mkdir();baseline=root/'baseline';baseline.mkdir()
            data={'version':1,'code':'N11000001','name':'EXAMPLE','count':1,'roads':['Local Road']}
            path=out/'N11000001.json';path.write_text(json.dumps(data))
            catalogue=[{'code':data['code'],'name':data['name']}]
            rows,zeros=validate_candidates(out,catalogue)
            self.assertEqual(len(rows),1);self.assertFalse(zeros)
            (baseline/path.name).write_text('different bytes')
            with self.assertRaises(ValueError):validate_candidates(out,catalogue,baseline)
            data['count']=2;path.write_text(json.dumps(data))
            with self.assertRaises(ValueError):validate_candidates(out,catalogue)


if __name__=='__main__':unittest.main()
