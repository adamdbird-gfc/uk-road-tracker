import copy
import gzip
import json
import os
from pathlib import Path
import tempfile
import unittest

from release_reviewed_ni import load_inputs, load_public, register, preserved_state, REPORT, SOURCE


class InputTests(unittest.TestCase):
    def test_reviewed_inputs_have_complete_unique_coverage(self):
        report, rows = load_inputs()
        self.assertEqual(len({r['code'] for r in rows}), 527)
        paths = {r['path'] for r in report['files']}
        self.assertEqual(len(paths), 1055)
        for row in rows:
            self.assertIn('settlement-inventories-v1/'+row['code']+'.json', paths)
            self.assertIn('settlement-inventories-v1/'+row['code']+'-boundary.geojson', paths)

    def test_modified_report_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)
            (p/'additions-report.json.gz').write_bytes(gzip.compress(b'{}'))
            with self.assertRaises(ValueError):
                load_inputs(p)

    def test_changed_served_bytes_rejected(self):
        report, rows = load_inputs()
        with self.assertRaises(ValueError):
            load_public('https://example.invalid',report,rows,lambda *args:(200,{},b'{}'))


@unittest.skipUnless(os.getenv('TEST_DATABASE_URL'), 'PostGIS integration runs in CI')
class DatabaseTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        self.db = psycopg.connect(os.environ['TEST_DATABASE_URL'])
        with self.db.cursor() as c:
            c.execute('DROP SCHEMA public CASCADE; CREATE SCHEMA public')
            for name in ('001_shared_reference_schema.sql','003_shared_settlement_catalogue_support.sql',
                         '005_shared_settlement_inventory_status.sql','006_shared_settlement_inventory_roads.sql'):
                c.execute((Path('migrations')/name).read_text())
            c.execute("INSERT INTO reference_sources (source_key,display_name) VALUES ('ons_bua_2022_gb','ONS') RETURNING id")
            source = c.fetchone()[0]
            c.execute("INSERT INTO settlements (source_id,external_code,name) VALUES (%s,'E63000001','Preserved') RETURNING id",(source,))
            sid = c.fetchone()[0]
            c.execute("INSERT INTO settlement_inventories (settlement_id,status,road_count,source_version) VALUES (%s,'ready',2,'preserved')",(sid,))
            c.execute("INSERT INTO settlement_aliases (settlement_id,alias) VALUES (%s,'Existing alias')",(sid,))
            c.execute("INSERT INTO settlement_boundaries (settlement_id,source_id,geometry) VALUES (%s,%s,ST_Multi(ST_GeomFromText('POLYGON((0 0,0 1,1 1,1 0,0 0))',4326)))",(sid,source))
            c.execute("INSERT INTO settlement_inventory_roads (settlement_id,road_name,road_class) VALUES (%s,'Preserved Road','residential')",(sid,))
        self.db.commit()
        self.metadata = [{'code':'N11000151','name':'Belfast City','centre':[54.5,-6.5]},
                         {'code':'N11000243','name':'Derry City','centre':[54.5,-6.5]}]
        polygon = {'type':'Polygon','coordinates':[[[-7,54],[-7,55],[-6,55],[-6,54],[-7,54]]]}
        self.boundaries = {r['code']:copy.deepcopy(polygon) for r in self.metadata}
        self.counts = {'E63000001':2,'N11000151':10,'N11000243':0}

    def tearDown(self):
        self.db.close()

    def snapshot(self):
        with self.db.cursor() as c:
            state=preserved_state(c)
        self.db.commit()
        return state

    def test_atomic_idempotent_registration_and_overlap_matching(self):
        before = self.snapshot()
        for attempt in range(2):
            result=register(self.db,self.metadata,self.boundaries,self.counts,write=True)
            self.db.commit()
            self.assertEqual(result['ni_registered'],2)
            self.assertEqual(before,self.snapshot())
            with self.db.cursor() as c:
                c.execute('SELECT row_to_json(s)::text FROM settlements s ORDER BY external_code')
                current=c.fetchall()
                c.execute("SELECT s.external_code FROM settlements s JOIN settlement_boundaries b ON b.settlement_id=s.id WHERE ST_Intersects(b.geometry,ST_GeomFromText('LINESTRING(-6.8 54.5,-6.2 54.5)',4326)) ORDER BY 1")
                self.assertEqual(c.fetchall(),[('N11000151',),('N11000243',)])
                c.execute("SELECT road_count FROM settlement_inventories i JOIN settlements s ON s.id=i.settlement_id WHERE s.external_code='N11000243'")
                self.assertEqual(c.fetchone(),(0,))
            self.db.commit()
            if attempt:
                self.assertEqual(previous,current)
            previous=current

    def test_preflight_read_only(self):
        with self.db.cursor() as c:
            c.execute('SET TRANSACTION READ ONLY')
        self.assertEqual(register(self.db,self.metadata,self.boundaries,self.counts)['ni_already_present'],0)
        self.db.commit()
        with self.db.cursor() as c:
            c.execute('SELECT count(*) FROM settlements')
            self.assertEqual(c.fetchone(),(1,))

    def test_gb_total_drift_aborts(self):
        with self.assertRaises(ValueError):
            register(self.db,self.metadata,self.boundaries,dict(self.counts,E63000001=3),write=True)
        self.db.rollback()
        with self.db.cursor() as c:
            c.execute('SELECT count(*) FROM settlements')
            self.assertEqual(c.fetchone(),(1,))

    def test_partial_registration_failure_rolls_back_every_insert(self):
        before=self.snapshot()
        # A conflicting NI name passes identity preflight; postconditions must roll back all other additions.
        with self.db.cursor() as c:
            c.execute("INSERT INTO reference_sources (source_key,display_name,source_version) VALUES (%s,'NI','2015') RETURNING id",(SOURCE,))
            source=c.fetchone()[0]
            c.execute("INSERT INTO settlements (source_id,external_code,name) VALUES (%s,'N11000151','Conflict')",(source,))
        self.db.commit()
        with self.assertRaises(ValueError):
            register(self.db,self.metadata,self.boundaries,self.counts,write=True)
        self.db.rollback()
        self.assertEqual(before,self.snapshot())
        with self.db.cursor() as c:
            c.execute('SELECT count(*) FROM settlements')
            self.assertEqual(c.fetchone(),(2,))
            c.execute('SELECT count(*) FROM settlement_inventories')
            self.assertEqual(c.fetchone(),(1,))

    def test_wrong_source_identity_aborts(self):
        with self.db.cursor() as c:
            c.execute("INSERT INTO settlements (source_id,external_code,name) SELECT id,'N11000151','Wrong source' FROM reference_sources LIMIT 1")
        self.db.commit()
        with self.assertRaises(ValueError):
            register(self.db,self.metadata,self.boundaries,self.counts,write=True)
        self.db.rollback()

    def test_bad_boundary_aborts_without_new_source(self):
        self.boundaries['N11000151']={'type':'Polygon','coordinates':[[[0,0],[0,1],[1,1],[1,0],[0,0]]]}
        with self.assertRaises(ValueError):
            register(self.db,self.metadata,self.boundaries,self.counts,write=True)
        self.db.rollback()
        with self.db.cursor() as c:
            c.execute('SELECT count(*) FROM reference_sources')
            self.assertEqual(c.fetchone(),(1,))


if __name__=='__main__':
    unittest.main()
