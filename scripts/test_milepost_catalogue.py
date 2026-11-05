"""Offline contract, hostile-input, deterministic-build and cache transition tests."""
import copy
from collections import Counter
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.milepost_catalogue import (ROOT, Catalogue, CatalogueCache, canonical,
    compile_inventory, digest, read_json, records, strict_json, validate_manifest,
    validate_record, verify, MAX_MANIFEST)


class MilepostCatalogueTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = ROOT / 'source/osm-mileposts.json'
        cls.pin = read_json(ROOT / 'source/provenance.json', MAX_MANIFEST)
        cls.manifest = read_json(ROOT / 'revision-1/manifest.json', MAX_MANIFEST)
        cls.path = ROOT / 'revision-1/catalogue.jsonl'
        cls.rows = list(records(cls.path, cls.manifest))

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.work = Path(self.tmp.name)

    def compiled(self, elements=None, revision=2, pin_change=None):
        source = self.source; pin = copy.deepcopy(self.pin)
        if elements is not None:
            root = read_json(source, 8*1024*1024); root['elements'] = elements
            source = self.work / 'source.json'; source.write_bytes(canonical(root))
            pin.update(sha256=digest(source), bytes=source.stat().st_size)
        if pin_change:
            pin.update(pin_change)
        output = self.work / ('revision-' + str(revision))
        m = compile_inventory(source, pin, revision, '2026-11-05T20:01:51Z', output)
        return output / 'catalogue.jsonl', m

    def source_rows(self):
        return read_json(self.source, 8*1024*1024)['elements']

    def test_original_889_ids_tags_positions_and_counts(self):
        source = {f"osm:{e['type']}:{e['id']}": e for e in self.source_rows()}
        self.assertEqual(889, len(source)); self.assertEqual(set(source), {r['osm_id'] for r in self.rows})
        self.assertEqual({'mills':446,'rowe':265,'mccoll':135,'dudgeon':43}, Counter(r['design'] for r in self.rows))
        for r in self.rows:
            e = source[r['osm_id']]
            self.assertEqual(e['tags'], r['source_tags'])
            self.assertEqual((e['lat'], e['lon']), (r['latitude'], r['longitude']))
            self.assertFalse(r['eligible']); self.assertIsNone(r['physical_marker_id'])
        self.assertEqual(0, self.manifest['eligible_count'])

    def test_archived_source_pin_and_machine_schema_fields(self):
        evidence=read_json(ROOT/'source/evidence-manifest.json',MAX_MANIFEST)
        entry=next(e for e in evidence if e['path']=='osm-mileposts.json')
        self.assertEqual(entry['sha256'],digest(self.source))
        self.assertEqual(entry['sha256'],self.pin['sha256'])
        self.assertEqual(entry['bytes'],self.source.stat().st_size)
        for name,example in [('record',self.rows[0]),('manifest',self.manifest)]:
            schema=read_json(ROOT/(name+'.schema.json'),MAX_MANIFEST)
            self.assertEqual(set(example),set(schema['required']))
            self.assertEqual(set(example),set(schema['properties']))
            for key,rule in schema['properties'].items():
                if 'const' in rule:self.assertEqual(rule['const'],example[key])

    def test_reader_memory_budget(self):
        import tracemalloc
        tracemalloc.start()
        try:
            for _ in range(3):
                self.assertEqual(889,sum(1 for _ in Catalogue(self.path,self.manifest)))
            self.assertLess(tracemalloc.get_traced_memory()[1],2*1024*1024)
        finally:tracemalloc.stop()

    def test_exact_rebuild_and_source_order_independence(self):
        p,m = self.compiled(revision=1)
        self.assertEqual(self.path.read_bytes(), p.read_bytes()); self.assertEqual(self.manifest,m)
        # Source ordering is not an identity/attribution source. Preserve the new
        # source pin while comparing row payloads after provenance normalization.
        p2,m2 = self.compiled(list(reversed(self.source_rows())), revision=2)
        other = list(records(p2,m2))
        self.assertEqual([r['osm_id'] for r in self.rows], [r['osm_id'] for r in other])
        for a,b in zip(self.rows,other):
            b.update(source_sha256=a['source_sha256'], revision=a['revision'])
            self.assertEqual(a,b)

    def test_missing_source(self):
        with self.assertRaises(OSError):
            compile_inventory(self.work/'absent',self.pin,1,'2026-11-05T20:01:51Z',self.work/'out')

    def test_source_hash_change_rejected(self):
        with self.assertRaises(ValueError):
            self.compiled(pin_change={'sha256':'0'*64})

    def test_source_timestamp_mismatch(self):
        with self.assertRaises(ValueError):
            self.compiled(pin_change={'snapshot_at':'2020-01-01T00:00:00Z'})

    def test_duplicate_source_id_rejected(self):
        e = self.source_rows()[:2]
        with self.assertRaises(ValueError):
            self.compiled(e + [e[0]])

    def test_bad_coordinates_rejected(self):
        for key,value in [('lat',91),('lon',181),('lat',True),('lon','0')]:
            with self.subTest(key=key,value=value):
                e = self.source_rows()[:1]; e[0][key]=value
                with self.assertRaises(ValueError):
                    self.compiled(e)

    def test_nonfinite_and_duplicate_json_keys(self):
        for raw in ['{"a":NaN}', '{"a":1,"a":2}', '{"a":Infinity}']:
            with self.assertRaises(ValueError):
                strict_json(raw)

    def test_malformed_source_no_output_activation(self):
        source = self.work/'malformed'; source.write_bytes(b'{broken')
        pin = dict(self.pin,sha256=digest(source),bytes=source.stat().st_size)
        with self.assertRaises(ValueError):
            compile_inventory(source,pin,1,'2026-11-05T20:01:51Z',self.work/'out')
        self.assertFalse((self.work/'out/manifest.json').exists())

    def test_missing_source_record_is_a_new_unapproved_revision(self):
        p,m = self.compiled(self.source_rows()[1:])
        self.assertEqual(888,m['record_count']); self.assertEqual(0,m['eligible_count'])
        self.assertNotEqual(self.manifest['sha256'],m['sha256'])
        self.assertEqual(888,len(list(records(p,m))))

    def test_unknown_design_is_explicit_and_never_approved(self):
        e=self.source_rows()[:1];e[0]['tags']['ncn_milepost']='unsupported-future-design'
        p,m=self.compiled(e);r=list(records(p,m))[0]
        self.assertEqual('unknown',r['design']);self.assertIsNone(r['designer'])
        self.assertEqual('unsupported',r['designer_status']);self.assertFalse(r['eligible'])
        self.assertEqual('unsupported-future-design',r['source_design_tag'])

    def test_geography_and_artist_name_never_override_design(self):
        e=self.source_rows()[:1];e[0].update(lat=59,lon=-5)
        e[0]['tags'].update(ncn_milepost='rowe',artist_name='Jon Mills')
        p,m=self.compiled(e);r=list(records(p,m))[0]
        self.assertEqual('rowe',r['designer']);self.assertEqual('unreviewed',r['boundary_status'])
        self.assertEqual('provisional_osm_design_tag',r['designer_status'])

    def test_changed_design_does_not_create_verified_assignment(self):
        e=self.source_rows()[:1];e[0]['tags']['ncn_milepost']='dudgeon'
        p,m=self.compiled(e);r=list(records(p,m))[0]
        self.assertEqual(f"osm:node:{e[0]['id']}",r['osm_id'])
        self.assertFalse(r['eligible']);self.assertEqual('unverified',r['identity_status'])

    def test_forged_approval_and_boundary_exception_rejected(self):
        for key,value in [('eligible',True),('physical_marker_id','physical:1'),
                          ('validation_status','validated_physical'),('designer_status','verified'),
                          ('boundary_exception',{'reason':'near coast'}),('identity_status','verified')]:
            with self.subTest(key=key):
                r=copy.deepcopy(self.rows[0]);r[key]=value
                with self.assertRaises(ValueError):validate_record(r,self.manifest)

    def test_unsupported_future_approved_schema_cannot_replace_assignments(self):
        cache=CatalogueCache(self.work/'cache');cache.install(self.path,self.manifest)
        before=(cache.directory/'state.json').read_bytes()
        m=copy.deepcopy(self.manifest);m.update(schema_version=2,eligible_count=7)
        with self.assertRaises(ValueError):cache.install(self.path,m)
        self.assertEqual(before,(cache.directory/'state.json').read_bytes())

    def test_missing_and_corrupt_catalogue_fail_closed(self):
        with self.assertRaises(OSError):Catalogue(self.work/'missing',self.manifest)
        p=self.work/'bad';p.write_bytes(self.path.read_bytes()[:-1])
        with self.assertRaises(ValueError):Catalogue(p,self.manifest)
        with self.assertRaises(ValueError):CatalogueCache(self.work/'empty').open()

    def test_manifest_schema_counts_path_and_size_limits(self):
        for key,value in [('schema_version',2),('eligible_count',1),('record_count',0),
                          ('bytes',17*1024*1024),('filename','../catalogue.jsonl'),('revision',True),
                          ('supported_reader_versions',[2])]:
            with self.subTest(key=key):
                m=copy.deepcopy(self.manifest);m[key]=value
                with self.assertRaises(ValueError):validate_manifest(m)

    def test_rehashed_duplicate_row_still_rejected(self):
        p=self.work/'duplicate';p.write_bytes(self.path.read_bytes()+canonical(self.rows[-1]))
        m=dict(self.manifest,sha256=digest(p),bytes=p.stat().st_size)
        m['filename']=f"collections/published/millennium-mileposts/{m['sha256']}/catalogue.jsonl"
        with self.assertRaises(ValueError):verify(p,m)

    def test_wrong_total_and_unsorted_records_rejected(self):
        for raw in [b''.join(canonical(r) for r in reversed(self.rows)),self.path.read_bytes()[:-len(canonical(self.rows[-1]))]]:
            p=self.work/'bad';p.write_bytes(raw);m=dict(self.manifest,sha256=digest(p),bytes=len(raw))
            m['filename']=f"collections/published/millennium-mileposts/{m['sha256']}/catalogue.jsonl"
            with self.assertRaises(ValueError):verify(p,m)

    def test_install_update_fallback_offline_and_monotonic_rollback(self):
        cache=CatalogueCache(self.work/'cache');cache.install(self.path,self.manifest)
        p,m=self.compiled();cache.install(p,m)
        self.assertEqual(2,cache.open().manifest['revision'])
        with self.assertRaises(ValueError):cache.install(self.path,self.manifest)
        active=cache.directory/m['sha256']/'catalogue.jsonl';active.write_bytes(b'corrupt')
        self.assertEqual(1,cache.open().manifest['revision'])
        with self.assertRaises(ValueError):cache.install(self.path,self.manifest)
        # Explicit rollback is a new increasing revision of retained source data.
        p3,m3=self.compiled(revision=3);cache.install(p3,m3)
        self.assertEqual(3,cache.open().manifest['revision'])

    def test_failed_install_preserves_state_and_cached_generation(self):
        cache=CatalogueCache(self.work/'cache');cache.install(self.path,self.manifest)
        state=(cache.directory/'state.json').read_bytes()
        p,m=self.compiled();p.write_bytes(b'broken')
        with self.assertRaises(ValueError):cache.install(p,m)
        self.assertEqual(state,(cache.directory/'state.json').read_bytes())
        self.assertEqual(1,cache.open().manifest['revision'])

    def test_interrupted_pointer_write_retains_last_good(self):
        cache=CatalogueCache(self.work/'cache');cache.install(self.path,self.manifest)
        p,m=self.compiled();before=(cache.directory/'state.json').read_bytes()
        with patch('scripts.milepost_catalogue.atomic_json',side_effect=OSError('disk full')):
            with self.assertRaises(OSError):cache.install(p,m)
        self.assertEqual(before,(cache.directory/'state.json').read_bytes())
        self.assertEqual(1,cache.open().manifest['revision'])

    def test_revision_reuse_and_immutable_build_rejected(self):
        cache=CatalogueCache(self.work/'cache');cache.install(self.path,self.manifest)
        m=copy.deepcopy(self.manifest);m['generated_at']='2026-11-05T21:00:00Z'
        with self.assertRaises(ValueError):cache.install(self.path,m)
        p,m=self.compiled(revision=1)
        with self.assertRaises(ValueError):
            compile_inventory(self.source,self.pin,1,'2026-11-05T21:00:00Z',p.parent)

    def test_bounded_lru_and_caller_mutation_isolation(self):
        c=Catalogue(self.path,self.manifest)
        for r in self.rows[:40]:self.assertEqual(r,c.get(r['osm_id']))
        self.assertEqual(32,len(c.cache))
        r=c.get(self.rows[39]['osm_id']);r['eligible']=True
        self.assertFalse(c.get(r['osm_id'])['eligible'])
        self.assertIsNone(c.get('osm:node:999999999999'))

    def test_cached_hit_checks_for_corruption(self):
        p=self.work/'copy';p.write_bytes(self.path.read_bytes())
        c=Catalogue(p,self.manifest);c.get(self.rows[0]['osm_id']);p.write_bytes(b'bad')
        with self.assertRaises(ValueError):c.get(self.rows[0]['osm_id'])


if __name__=='__main__':unittest.main()
