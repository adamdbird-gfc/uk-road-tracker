import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import preserve_county_build_reference as reference


class PreserveBuildReferenceTest(unittest.TestCase):
    def test_valid_reference_is_carried_into_full_publication(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(reference, 'DIGEST', hashlib.sha256(b'pinned').hexdigest()):
            reference.preserve(directory, download=lambda url: b'pinned')
            self.assertEqual(b'pinned', (Path(directory) / reference.ASSET).read_bytes())

    def test_corrupt_download_fails_before_creating_public_file(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'integrity'):
                reference.preserve(directory, download=lambda url: b'changed')
            self.assertFalse((Path(directory) / reference.ASSET).exists())

    def test_serving_checks_use_the_same_immutable_path(self):
        urls = []
        def download(url):
            urls.append(url)
            return b'pinned'
        with patch.object(reference, 'DIGEST', hashlib.sha256(b'pinned').hexdigest()):
            reference.preserve(origin='https://preview.web.app/', download=download)
        self.assertEqual(['https://preview.web.app/' + reference.ASSET], urls)
