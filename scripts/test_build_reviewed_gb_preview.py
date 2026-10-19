import tempfile
import unittest
from pathlib import Path
from build_reviewed_gb_preview import checked_hash, sha, validate_recovered


class ReviewedPreviewTests(unittest.TestCase):
    def test_recovered_inventory_rejects_wrong_identity_and_duplicate_names(self):
        good = {'code': 'E63000001', 'count': 2, 'roads': ['A Road', 'B Road']}
        validate_recovered(good, good['code'])
        for bad in [dict(good, code='E63000002'), dict(good, roads=['A Road', 'A Road']),
                    dict(good, count=3), dict(good, roads=['A Road', ''])]:
            with self.assertRaises(ValueError):
                validate_recovered(bad, good['code'])

    def test_reviewed_hash_rejects_changed_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'snapshot'
            path.write_bytes(b'reviewed')
            expected = sha(path)
            checked_hash(path, expected)
            path.write_bytes(b'changed')
            with self.assertRaises(ValueError):
                checked_hash(path, expected)


if __name__ == '__main__':
    unittest.main()
