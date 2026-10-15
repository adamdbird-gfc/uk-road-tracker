import copy
import gzip
import json
import os
from pathlib import Path
import tempfile
import unittest

from release_reviewed_gb import checked_channel, check_database, load_report, manifest_counts, register_counts, PREVIEW


class ReleaseTests(unittest.TestCase):
    def test_ready_total_drift_and_active_build_abort(self):
        counts = {'E63000001': 2}
        for rows in [[('E63000001', 'ready', 3)], [('E63000001', 'building', None)], []]:
            with self.assertRaises(ValueError):
                check_database(rows, counts)
        check_database([('E63000001', 'ready', 2)], counts)
        check_database([('E63000001', None, None)], counts)

    def test_duplicate_catalogue_codes_abort(self):
        with self.assertRaises(ValueError):
            check_database([('E63000001', None, None)] * 2, {'E63000001': 0})

    def test_zero_counts_and_scottish_codes_supported(self):
        self.assertEqual(manifest_counts({'inventories': [
            {'code': 'S45000001', 'status': 'available', 'count': 0}]}), {'S45000001': 0})

    def test_bad_manifest_aborts(self):
        row = {'code': 'E63000001', 'status': 'available', 'count': 0}
        for rows in [[row, row], [dict(row, count=-1)], [dict(row, count=True)], [dict(row, status='pending')]]:
            with self.assertRaises(ValueError):
                manifest_counts({'inventories': rows})

    def test_preview_changed_after_verification_aborts(self):
        channel = {'url': PREVIEW, 'release': {'releaseTime': '2026-10-14T23:00:00Z',
            'version': {'name': 'sites/roadprints-data-822cc/versions/example', 'status': 'FINALIZED'}}}
        self.assertTrue(checked_channel(channel).endswith('/example'))
        for edit in ('time', 'site', 'status', 'url'):
            changed = copy.deepcopy(channel)
            if edit == 'time':
                changed['release']['releaseTime'] = '2026-10-15T06:10:00Z'
            elif edit == 'site':
                changed['release']['version']['name'] = 'sites/other/versions/example'
            elif edit == 'status':
                changed['release']['version']['status'] = 'CREATED'
            else:
                changed['url'] = 'https://other.web.app'
            with self.assertRaises(ValueError):
                checked_channel(changed)

    def test_reviewed_report_integrity(self):
        report = load_report('.github/gb-live/hosting-stage-report.json.gz')
        self.assertEqual(len(report['files']), 17098)
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'changed.gz'
            p.write_bytes(gzip.compress(json.dumps(report).encode()))
            with self.assertRaises(ValueError):
                load_report(p)


@unittest.skipUnless(os.getenv('TEST_DATABASE_URL'), 'Postgres integration runs in CI')
class DatabaseIntegrationTests(unittest.TestCase):
    def setUp(self):
        import psycopg
        self.connection = psycopg.connect(os.environ['TEST_DATABASE_URL'])
        with self.connection.cursor() as cursor:
            cursor.execute('DROP TABLE IF EXISTS settlement_inventories,settlements CASCADE')
            cursor.execute('CREATE TABLE settlements (id integer PRIMARY KEY, external_code text UNIQUE, is_active boolean)')
            cursor.execute('''CREATE TABLE settlement_inventories (
                settlement_id integer PRIMARY KEY REFERENCES settlements(id), status text, road_count integer,
                source_key text,source_version text,inventory_version text,completed_at timestamptz,
                updated_at timestamptz,build_started_at timestamptz,failed_at timestamptz,failure_code text)''')
            cursor.execute("INSERT INTO settlements VALUES (1,'E63000001',true),(2,'S45000001',true)")
            cursor.execute("INSERT INTO settlement_inventories (settlement_id,status,road_count,source_version) VALUES (1,'ready',2,'preserved')")
        self.connection.commit()
        self.counts = {'E63000001': 2, 'S45000001': 0}

    def tearDown(self):
        self.connection.close()

    def test_atomic_registration_and_idempotence_preserve_ready_row(self):
        for _ in range(2):
            register_counts(self.connection, self.counts, write=True)
            self.connection.commit()
            with self.connection.cursor() as cursor:
                cursor.execute('SELECT status,road_count,source_version FROM settlement_inventories ORDER BY settlement_id')
                self.assertEqual(cursor.fetchall(), [('ready',2,'preserved'),('ready',0,'gb-reviewed-2026-10-14')])
            self.connection.commit()

    def test_mismatch_rolls_back_without_creating_missing_row(self):
        with self.assertRaises(ValueError):
            register_counts(self.connection, dict(self.counts, E63000001=3), write=True)
        self.connection.rollback()
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT count(*) FROM settlement_inventories')
            self.assertEqual(cursor.fetchone()[0], 1)

    def test_preflight_does_not_write(self):
        register_counts(self.connection, self.counts)
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT count(*) FROM settlement_inventories')
            self.assertEqual(cursor.fetchone()[0], 1)


if __name__ == '__main__':
    unittest.main()
