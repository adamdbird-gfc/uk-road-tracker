"""Publication artifact integrity, independent of NCN geometry generation."""
import copy
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
import struct
import tempfile
import unittest
from unittest.mock import patch

from scripts import prepare_ncn_publication as prep
from scripts.ncn_reference import sha_file


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        from scripts.ncn_binding_attainability import content_sha
        from scripts.finalize_ncn_android import pack
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.db = self.folder/'ncn-national.db'
        with sqlite3.connect(self.db) as db:
            db.executescript('''
              CREATE TABLE routes(key TEXT PRIMARY KEY);
              CREATE TABLE paths(route TEXT,way TEXT,data BLOB);
              CREATE TABLE nodes(node TEXT,way TEXT);
              CREATE TABLE network(id TEXT);
              CREATE TABLE meta(key TEXT,value TEXT);
              CREATE TABLE targets(route TEXT,key TEXT);
              CREATE TABLE overview(key TEXT,data BLOB);
              INSERT INTO routes VALUES('NCN:1');
              INSERT INTO meta VALUES('scope','synthetic integrity fixture');
            ''')
        self.canonical = content_sha(self.db)
        manifest = json.loads((prep.ROOT/'candidate-manifest.json').read_text())
        manifest['supported_identities'] = 1
        (self.folder/'manifest.json').write_text(json.dumps(manifest))
        pack(self.folder)
        self.manifest = json.loads((self.folder/'manifest.json').read_text())

    def verify(self):
        return prep.verify_archive(self.folder, self.manifest, self.canonical)

    def test_preserved_exact_archive_verifies_bytes_schema_counts_and_content(self):
        result = self.verify()
        self.assertEqual(self.canonical, result['canonical_content_sha256'])
        self.assertEqual(1, result['table_counts']['routes'])
        self.assertEqual(64, len(result['schema_sha256']))

    def test_wrong_compressed_checksum_and_size_rejected(self):
        asset = self.folder/'catalogue.db.gz'
        for raw in [b'corrupt', asset.read_bytes()+b'extra']:
            asset.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, 'Compressed NCN asset differs'):
                self.verify()

    def test_manifest_mismatch_rejected_even_when_archive_unchanged(self):
        changed = copy.deepcopy(self.manifest);changed['revision'] += 1
        (self.folder/'manifest.json').write_text(json.dumps(changed))
        with self.assertRaisesRegex(ValueError, 'Exact prepared publication pins differ'):
            self.verify()

    def test_same_database_different_gzip_identity_rejected(self):
        asset = self.folder/'catalogue.db.gz'
        asset.write_bytes(gzip.compress(self.db.read_bytes(), mtime=123))
        with self.assertRaisesRegex(ValueError, 'Compressed NCN asset differs'):
            self.verify()

    def test_equivalent_sqlite_layout_is_not_approved_database_bytes(self):
        from scripts.ncn_binding_attainability import content_sha
        with self.db.open('r+b') as out:
            out.seek(96);out.write(struct.pack('>I', 3045001))
        self.assertEqual(self.canonical, content_sha(self.db))
        raw = gzip.compress(self.db.read_bytes(), mtime=0)
        (self.folder/'catalogue.db.gz').write_bytes(raw)
        self.manifest.update(compressed_sha256=hashlib.sha256(raw).hexdigest(), compressed_bytes=len(raw))
        (self.folder/'manifest.json').write_text(json.dumps(self.manifest))
        with self.assertRaisesRegex(ValueError, 'NCN database integrity failure'):
            self.verify()

    def test_reference_change_rejected_even_if_new_byte_pins_are_supplied(self):
        from scripts.finalize_ncn_android import pack
        with sqlite3.connect(self.db) as db:
            db.execute("UPDATE meta SET value='unapproved reference change'")
        pack(self.folder)
        self.manifest = json.loads((self.folder/'manifest.json').read_text())
        with self.assertRaisesRegex(ValueError, 'Combined canonical candidate differs'):
            self.verify()

    def test_header_recovery_requires_complete_original_digest(self):
        original = sha_file(self.db)
        with self.db.open('r+b') as out:
            out.seek(96);old=out.read(4);out.seek(96);out.write(struct.pack('>I', 3045001))
        n = struct.unpack('>I', old)[0]
        writer = f'{n//1000000}.{n//1000%1000}.{n%1000}'
        result = prep.recover_writer_header(self.db, original, writer)
        self.assertEqual(original, sha_file(self.db))
        self.assertTrue(result['changed_offsets'])
        before = self.db.read_bytes()
        with self.assertRaisesRegex(ValueError, 'cannot be recovered'):
            prep.recover_writer_header(self.db, '0'*64, '3.1.2')
        self.assertEqual(before, self.db.read_bytes())

    def test_exact_archive_reuse_does_not_invoke_writer(self):
        # The production path requires the existing verified seed cache first.
        # With a preserved candidate it validates all properties and cannot call
        # either correction writer or the packer.
        root = self.folder/'receipts';root.mkdir()
        (root/'candidate-manifest.json').write_text(json.dumps(self.manifest))
        (root/'live-manifest.json').write_text(json.dumps(self.manifest))
        (root/'receipt.json').write_text(json.dumps({'canonical_content_sha256': self.canonical}))
        real_read = Path.read_text
        def read(path, *args, **kwargs):
            if str(path) == '.github/ncn-reference/request.json':
                return '{}'
            return real_read(path, *args, **kwargs)
        with patch.object(prep, 'ROOT', root), patch.object(Path, 'read_text', read), \
             patch.object(prep, 'verify_database') as seed, \
             patch.object(prep, 'verify_archive', return_value={}) as archive, \
             patch.object(Path, 'exists', return_value=True), \
             patch.object(Path, 'write_text'), \
             patch('scripts.reproduce_ncn_ordinary_seams.reproduce') as writer:
            prep.run()
            seed.assert_called_once()
            archive.assert_called_once()
            writer.assert_not_called()


if __name__ == '__main__': unittest.main()
