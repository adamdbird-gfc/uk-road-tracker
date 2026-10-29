import gzip
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from matched_path_evidence import digest
from .completion_checkpoint import pinned,open_extent,verified_plan,check_registry,read_checkpoint_route
from .open_completion import build_verified_completion,sha,read_completion
from .source_identity_review import read_catalogue


class CompletionCheckpointTests(unittest.TestCase):
    def test_pin_rejects_changed_bytes_and_path_escape(self):
        with TemporaryDirectory()as temporary:
            root=Path(temporary);(root/'a').write_bytes(b'original')
            record={'file':'a','sha256':sha(b'original')}
            self.assertEqual(pinned(root,record),b'original')
            (root/'a').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'changed'):pinned(root,record)
            with self.assertRaisesRegex(ValueError,'escapes'):pinned(root,{'file':'../a','sha256':sha(b'original')})

    def test_legacy_extent_keeps_only_exact_identity_open_main_geometry(self):
        def feature(number=1,status='Open',category='Main Route',description='OnRoad'):
            return {'properties':{'RouteType':'NCN','RouteNo':number,'OpenStatus':status,'RouteCat':category,'Desc_':description},
                'geometry':{'type':'LineString','coordinates':[[0,0],[.001,0]]}}
        features=[feature(),feature(2),feature(status='Closed'),feature(category='Alternative Route'),feature(description='Ferry')]
        extent=open_extent('NCN:1',features,{'projection_origin':[0,0],'national_reference_sha256':'a'*64},'a'*64)
        self.assertEqual(extent['features'],features[:1])
        with self.assertRaisesRegex(ValueError,'snapshot'):open_extent('NCN:1',features,{'national_reference_sha256':'b'*64},'a'*64)

    def test_exact_219_supported_and_34_held_partition_is_required(self):
        with TemporaryDirectory()as temporary:
            with self.assertRaisesRegex(ValueError,'identities'):
                check_registry(Path(temporary),{'routes':[],'held_route_keys':[]},{})

    def test_inventory_receipt_cannot_relabel_route_or_change_fixed_plan(self):
        from .inventory_completion import build_plan
        from .test_inventory_completion import fixture
        from .association_bundle import build_bundle
        ledger,paths,plan=build_plan(*fixture())
        bundle=build_bundle(paths,ledger.reference,[0,0],{'attribution':'OSM','licence':'ODbL',
            'source_snapshot_sha256':'a'*64,'membership_evidence_sha256':'b'*64})
        raw=json.dumps(plan).encode();manifest={'plan_sha256':sha(raw),'association_sha256':sha(bundle),
            'reference_version':plan['reference_version']}
        self.assertEqual(verified_plan(raw,bundle,manifest,plan['route_key']),json.loads(raw))
        with self.assertRaisesRegex(ValueError,'differs'):verified_plan(raw,bundle,manifest,'NCN:other')
        with self.assertRaisesRegex(ValueError,'differs'):verified_plan(raw+b' ',bundle,manifest,plan['route_key'])

        with TemporaryDirectory()as temporary:
            folder=Path(temporary)/'NCN-test';folder.mkdir()
            (folder/'plan.json.gz').write_bytes(gzip.compress(raw))
            (folder/'associations.json.gz').write_bytes(gzip.compress(bundle))
            manifest.update(route_key=plan['route_key'],completion_kind='preserved_numbered_alternative_inventory',
                input_kind='numbered_alternative_inventory')
            mraw=json.dumps(manifest).encode();(folder/'manifest.json').write_bytes(mraw)
            loaded=read_checkpoint_route(folder,sha(mraw))
            self.assertEqual(loaded[2]['scope'],'NCN:test:source-inventory')
            with self.assertRaisesRegex(ValueError,'manifest changed'):read_checkpoint_route(folder,'0'*64)
            manifest['completion_kind']='captured_open_main'
            mraw=json.dumps(manifest).encode();(folder/'manifest.json').write_bytes(mraw)
            with self.assertRaises(ValueError):read_checkpoint_route(folder,sha(mraw))

    def test_deferred_alternative_cannot_silently_become_ordinary_main_credit(self):
        from .test_source_identity_review import setup,build
        from .remaining_routes_review import emit
        values,review=setup();candidate,audit=build(values,review)
        with TemporaryDirectory()as temporary:
            root=Path(temporary)
            receipt=emit(root,candidate,audit,review,json.dumps(review).encode(),values[5]['features'])
            reference,metadata,paths,extent=read_catalogue(root,receipt['manifest_sha256'])
            provenance=json.loads(gzip.decompress((root/'associations.json.gz').read_bytes()))['provenance']
            ledger,bundle,plan=build_verified_completion(reference,metadata,paths,extent,'a'*64,provenance,[42])
            self.assertEqual(plan['deferred_alternative_way_ids'],['42'])
            self.assertEqual(ledger.bindings,())
            self.assertEqual(plan['maximum_bound_open_length_m']['combined'],0)
            self.assertAlmostEqual(plan['unbound_open_length_m'],extent['canonical_open_union_m'])
            self.assertEqual(json.loads(bundle)['paths'],json.loads(gzip.decompress((root/'associations.json.gz').read_bytes()))['paths'])
            raw=json.dumps(plan).encode();read_completion(raw,bundle,sha(raw),sha(bundle))


if __name__=='__main__':
    unittest.main()
