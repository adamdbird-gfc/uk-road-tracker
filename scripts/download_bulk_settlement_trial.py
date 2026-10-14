#!/usr/bin/env python3
"""Download the three trial extracts, verify supplier MD5 and record SHA-256."""
import argparse
import hashlib
import json
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REGIONS = {'kent': 'europe/united-kingdom/england/kent',
           'cheshire': 'europe/united-kingdom/england/cheshire',
           'wales': 'europe/united-kingdom/wales',
           'scotland': 'europe/united-kingdom/scotland',
           'great-britain': 'europe/great-britain'}


def download(root, item):
    name, path = item
    base = 'https://download.geofabrik.de/' + path
    start = time.monotonic()
    for suffix in ['.poly', '-latest.osm.pbf.md5', '-latest.osm.pbf']:
        target = root / (name + suffix)
        with urllib.request.urlopen(base + suffix, timeout=120) as response, target.open('wb') as out:
            while chunk := response.read(1024 * 1024):
                out.write(chunk)
    md5, sha = hashlib.md5(), hashlib.sha256()
    target = root / (name + '-latest.osm.pbf')
    with target.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            md5.update(chunk)
            sha.update(chunk)
    expected = (root / (name + '-latest.osm.pbf.md5')).read_text().split()[0]
    if md5.hexdigest() != expected:
        raise ValueError('Supplier checksum mismatch: ' + name)
    result = {'region': name, 'url': base + '-latest.osm.pbf',
              'bytes': target.stat().st_size, 'sha256': sha.hexdigest(), 'md5': expected,
              'download_seconds': round(time.monotonic() - start, 3)}
    print(json.dumps(result), flush=True)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--regions', nargs='+', choices=REGIONS,
                        default=['kent', 'cheshire', 'wales'])
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=3) as pool:
        selected = [(name, REGIONS[name]) for name in dict.fromkeys(args.regions)]
        results = list(pool.map(lambda item: download(args.output, item), selected))
    (args.output / 'downloads.json').write_text(json.dumps(results, indent=2) + '\n')
