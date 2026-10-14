import unittest
from protect_firebase_reference_data import protect


def manifest(*rows):
    return {'inventories': [{'code': c, 'status': s} for c, s in rows]}


class ProtectionTests(unittest.TestCase):
    def test_additive_publication_is_permitted(self):
        protect(manifest(('E63000001', 'available')),
                manifest(('E63000001', 'available'), ('S45000001', 'available')))

    def test_missing_and_unavailable_previously_served_inventory_are_blocked(self):
        live = manifest(('E63000001', 'available'), ('S45000001', 'available'))
        for stage in [manifest(('E63000001', 'available')),
                      manifest(('E63000001', 'available'), ('S45000001', 'pending'))]:
            with self.assertRaises(ValueError):
                protect(live, stage)

    def test_duplicate_identity_is_rejected(self):
        with self.assertRaises(ValueError):
            protect(manifest(('E63000001', 'available')),
                    manifest(('E63000001', 'available'), ('E63000001', 'pending')))


if __name__ == '__main__':
    unittest.main()
