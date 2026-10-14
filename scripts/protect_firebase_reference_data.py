#!/usr/bin/env python3
"""Prevent routine publication from dropping already available inventories."""
import argparse
import json
from pathlib import Path
from urllib.request import urlopen


def protect(current, staged):
    def available(manifest):
        rows = manifest['inventories']
        if len({r['code'] for r in rows}) != len(rows):
            raise ValueError('Duplicate inventory identity')
        return {r['code'] for r in rows if r['status'] == 'available'}
    missing = available(current) - available(staged)
    if missing:
        raise ValueError('Publication would remove already available inventory codes; restore the complete reference source first.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', default='hosting-public/settlement-inventory-manifest-v1.json', type=Path)
    args = parser.parse_args()
    url = 'https://roadprints-data-822cc.web.app/settlement-inventory-manifest-v1.json'
    with urlopen(url, timeout=30) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024:
        raise ValueError('Live reference manifest exceeds safety limit')
    protect(json.loads(data), json.loads(args.staged.read_text()))
    print('Already available settlement inventory codes are retained.')
