import os
import pathlib
import threading
import unittest
from unittest.mock import patch
import psycopg
import map_context as context


class ContextValidationTests(unittest.TestCase):
    def test_scope_rejects_arbitrary_global_areas(self):
        self.assertEqual(context.tile_bounds(5144,18),[51.437, .354,51.452999999999996,.386])
        for args in [(9000,18),(5144,99999)]:
            with self.assertRaises(ValueError): context.tile_bounds(*args)

    def test_incomplete_or_invalid_provider_data_is_not_arrival_evidence(self):
        for document in [{"remark":"timeout","elements":[]},{"elements":[{"geometry":[{"lat":float('nan'),"lon":0},{"lat":51,"lon":0}]}]}]:
            with self.assertRaises(ValueError):context.clean_document(document)
        self.assertEqual(context.clean_document({"elements":[]}),[])

    def test_only_required_public_tags_are_retained(self):
        result=context.clean_document({"elements":[{"geometry":[{"lat":51,"lon":0},{"lat":51.1,"lon":.1}],
                             "tags":{"building":"yes","name":"Private name","addr:housenumber":"1"}}]})
        self.assertEqual(result[0]["tags"],{"building":"yes"})
        self.assertEqual(result[0]["bounds"]["maxlat"],51.1)


@unittest.skipUnless(os.getenv("TEST_CONTEXT_DATABASE_URL"),"Requires isolated PostgreSQL test service")
class SharedStorageTests(unittest.TestCase):
    def setUp(self):
        self.url=os.environ["TEST_CONTEXT_DATABASE_URL"]
        with psycopg.connect(self.url,autocommit=True) as db:
            db.execute("DROP TABLE IF EXISTS map_context_areas,map_context_budget,map_context_provider_budget")
            db.execute(pathlib.Path('migrations/008_shared_map_context.sql').read_text())
            db.execute(pathlib.Path('migrations/009_context_provider_cooldowns.sql').read_text())
        self.now=86400000
        self.calls=0
    def fetch(self,bounds):
        self.calls+=1
        return []
    def area(self,cell=5144):
        return context.shared_area(self.url,cell,18,fetch=self.fetch,clock=lambda:self.now)
    def test_shared_cache_hit_does_not_spend_budget(self):
        first=self.area()
        self.assertEqual(self.area(),first)
        self.assertEqual(self.calls,1)
        with psycopg.connect(self.url) as db:
            self.assertEqual(db.execute('SELECT requests FROM map_context_budget').fetchone()[0],1)
    def test_failure_backoff_persists_between_connections_and_escalates(self):
        def fail(bounds):raise RuntimeError('upstream unavailable')
        for delay in [600,1200]:
            with self.assertRaises(context.ContextUnavailable) as failed:
                context.shared_area(self.url,5144,18,fetch=fail,clock=lambda:self.now)
            self.assertEqual(failed.exception.retry_after,delay)
            with self.assertRaises(context.ContextUnavailable):self.area()
            self.now+=delay
        self.assertEqual(self.calls,0)
    def test_daily_budget_and_restart_do_not_reset_protection(self):
        with psycopg.connect(self.url,autocommit=True) as db:
            db.execute('UPDATE map_context_budget SET day=%s,requests=%s',(self.now//86400,context.DAILY_LIMIT))
        with self.assertRaises(context.ContextUnavailable):self.area()
        self.now+=86400
        self.area();self.assertEqual(self.calls,1)
    def test_overlapping_requests_make_one_upstream_call(self):
        entered,release=threading.Event(),threading.Event()
        results=[]
        def fetch(bounds):entered.set();release.wait(5);return []
        def first():results.append(context.shared_area(self.url,5144,18,fetch=fetch,clock=lambda:self.now))
        worker=threading.Thread(target=first);worker.start();self.assertTrue(entered.wait(5))
        try:
            with self.assertRaises(context.ContextUnavailable):self.area()
        finally:release.set();worker.join(5)
        self.assertEqual(len(results),1);self.assertEqual(self.calls,0)
        self.assertEqual(self.area(),results[0])

    def test_provider_change_keeps_global_limits_and_previous_provider_cooldown(self):
        old=context.UPSTREAM
        def fail(bounds):raise RuntimeError('unreachable provider')
        with self.assertRaises(context.ContextUnavailable):
            context.shared_area(self.url,5144,18,fetch=fail,clock=lambda:self.now)
        with patch.object(context,'UPSTREAM','https://dedicated-provider.example/interpreter'):
            with self.assertRaises(context.ContextUnavailable):self.area()
            self.now+=60;self.area()
        self.now+=60
        with self.assertRaises(context.ContextUnavailable):self.area(cell=5145)
        with psycopg.connect(self.url) as db:
            self.assertEqual(db.execute('SELECT requests FROM map_context_budget').fetchone()[0],2)
            self.assertEqual(db.execute('SELECT failures FROM map_context_provider_budget WHERE provider=%s',(old,)).fetchone()[0],1)
