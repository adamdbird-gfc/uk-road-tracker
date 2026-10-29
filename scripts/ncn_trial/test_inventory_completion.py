import unittest
from shapely.geometry import LineString
from .inventory_completion import build_plan,InventoryLedger
from .saved_paths import KnownPath
from .coverage import Evidence


def fixture(include_connectors=False):
    coords={1:[0,0],2:[.001,0],3:[.001,.001],4:[0,.001],10:[-.0005,0],11:[.0015,0]}
    ways={}
    for wid,nodes,tag in [(1,[1,2,3,4,1],{'junction':'roundabout'}),(9,[10,1],{}),(10,[2,11],{})]:
        ways[wid]={'nodes':nodes,'tags':tag,'node_records':[{'lon':coords[n][0],'lat':coords[n][1]} for n in nodes]}
    selected=[];paths=[]
    for wid in ([1,9,10] if include_connectors else [1]):
        points=[coords[n] for n in ways[wid]['nodes']]
        selected.append({'key':str(wid),'way_id':wid,'coordinates':points,'from_vertex':0,'to_vertex':len(points)-1})
        paths.append(KnownPath(str(wid),tuple(map(tuple,points)),(str(wid),),'old',('cycling','on_foot'),'exact_source_spans',tuple(ways[wid]['nodes'])))
    return 'NCN:test',ways,selected,paths,{'projection_origin':[0,0]}


class InventoryCompletionTests(unittest.TestCase):
    def test_both_arcs_share_completion_without_double_distance(self):
        ledger,paths,plan=build_plan(*fixture())
        line=ledger.reference.sections['1'].line
        # Adjacent gate nodes define the short first edge and three-edge long arc.
        short=LineString(list(line.coords)[:2]).length
        ledger.put('short',Evidence(ledger.reference.version,'cycling',(('1',0,short),),()))
        route=ledger.summary()['logical_routes']['NCN:test:source-inventory']
        before=route['combined_m'];self.assertAlmostEqual(before,short)
        ledger.put('long',Evidence(ledger.reference.version,'on_foot',(('1',short,line.length),),()))
        self.assertAlmostEqual(ledger.summary()['logical_routes']['NCN:test:source-inventory']['combined_m'],before)
        self.assertAlmostEqual(ledger.summary()['physical_distance']['combined_m'],line.length)
        self.assertEqual(len(plan['roundabout_groups']),1)

    def test_unmapped_inventory_stays_in_denominator(self):
        ledger,_,plan=build_plan(*fixture())
        ledger.put('ring',Evidence(ledger.reference.version,'cycling',(('1',0,ledger.reference.sections['1'].line.length),),()))
        route=ledger.summary()['logical_routes']['NCN:test:source-inventory']
        self.assertAlmostEqual(route['completed_fraction'],.5,places=6)
        self.assertGreater(plan['unmapped_inventory_m'],100)
        self.assertFalse(plan['whole_published_route_ready'])

    def test_full_inventory_requires_connectors_and_either_arc(self):
        ledger,_,plan=build_plan(*fixture(True))
        for key in ['1','9','10']:
            end=ledger.reference.sections[key].line.length
            if key=='1':end=LineString(list(ledger.reference.sections[key].line.coords)[:2]).length
            ledger.put(key,Evidence(ledger.reference.version,'cycling',((key,0,end),),()))
        route=ledger.summary()['logical_routes']['NCN:test:source-inventory']
        self.assertAlmostEqual(route['completed_fraction'],1)
        self.assertLess(plan['unmapped_inventory_m'],1e-6)
        self.assertFalse(plan['whole_published_route_ready'])

    def test_deleting_and_mode_changing_restores_gaps(self):
        ledger,_,_=build_plan(*fixture(True))
        ledger.put('ring',Evidence(ledger.reference.version,'cycling',(('1',0,ledger.reference.sections['1'].line.length),),()))
        ledger.put('ring',Evidence(ledger.reference.version,'driving',(('1',0,ledger.reference.sections['1'].line.length),),()))
        self.assertEqual(ledger.summary()['logical_routes']['NCN:test:source-inventory']['combined_m'],0)
        ledger.remove('ring')
        self.assertEqual(ledger.summary()['physical_distance']['combined_m'],0)

    def test_no_eligible_paths_does_not_remove_any_inventory(self):
        args=list(fixture());args[2]=[];args[3]=[]
        ledger,paths,plan=build_plan(*args)
        self.assertEqual(paths,())
        self.assertEqual(plan['maximum_supported_coverage_m']['combined'],0)
        self.assertGreater(plan['canonical_inventory_length_m'],0)
        self.assertEqual(ledger.summary()['logical_routes']['NCN:test:source-inventory']['completed_fraction'],0)

    def test_portable_plan_roundtrip_requires_pinned_plan_and_bundle(self):
        import json,hashlib
        from .association_bundle import build_bundle
        from .inventory_completion import read_completion
        ledger,paths,plan=build_plan(*fixture(True))
        provenance={'attribution':'OpenStreetMap contributors','licence':'ODbL 1.0','source_snapshot_sha256':'a'*64,'membership_evidence_sha256':'b'*64}
        bundle=build_bundle(paths,ledger.reference,[0,0],provenance)
        raw=json.dumps(plan).encode();one=hashlib.sha256(raw).hexdigest();two=hashlib.sha256(bundle).hexdigest()
        restored,new_paths,_=read_completion(raw,bundle,one,two)
        self.assertEqual(len(new_paths),3)
        self.assertEqual(restored.summary(),ledger.summary())
        with self.assertRaises(ValueError):read_completion(raw+b' ',bundle,one,two)
        with self.assertRaises(ValueError):read_completion(raw,bundle+b' ',one,two)

    def test_activation_and_checkpoint_keep_fixed_inventory_denominator(self):
        from .activation import ActivationTrial,JourneyRecord,Assessment
        ledger,_,plan=build_plan(*fixture())
        factory=lambda reference:InventoryLedger(reference,tuple(ledger.logical.values()),ledger.bindings)
        worker=ActivationTrial(ledger.reference,ledger_factory=factory,accounting_key='source-inventory-v1')
        worker.activate([JourneyRecord('ride','1','cycling','timeline_import',1)],100,complete_snapshot=True)
        worker.run_batch(lambda _:Assessment(Evidence(ledger.reference.version,'cycling',(('1',0,ledger.reference.sections['1'].line.length),),())))
        restored=ActivationTrial.restore(ledger.reference,worker.export(),ledger_factory=factory,accounting_key='source-inventory-v1')
        value=restored.summary()['logical_routes']['NCN:test:source-inventory']
        self.assertAlmostEqual(value['completed_fraction'],.5,places=6)
        self.assertEqual(restored.review_candidates(),[])

    def test_repeated_geometry_has_both_source_occurrences_but_one_physical_distance(self):
        from .coverage import TrialReference,Section,TrialLedger
        ref=TrialReference('repeat',[Section('r',LineString([(0,0),(10,0),(0,0)]),('r',))])
        ledger=TrialLedger(ref);ledger.put('return',Evidence('repeat','on_foot',(('r',10,20),),()))
        self.assertAlmostEqual(ledger.summary()['combined_m'],10)
        ledger.put('out',Evidence('repeat','on_foot',(('r',0,10),),()))
        self.assertAlmostEqual(ledger.summary()['combined_m'],10)
