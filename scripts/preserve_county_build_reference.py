#!/usr/bin/env python3
"""Carry the immutable Android build reference through every full Hosting deploy."""
import argparse
import hashlib
from pathlib import Path
from urllib.request import urlopen

DIGEST = 'b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850'
ASSET = 'build-references/historic-counties/' + DIGEST + '/boundaries.bin'
LIVE = 'https://roadprints-data-822cc.web.app'
LIMIT = 12_000_000


def fetch(url):
    with urlopen(url, timeout=90) as response:
        return response.read(LIMIT + 1)


def preserve(output=None, origin=LIVE, download=fetch):
    raw = download(origin.rstrip('/') + '/' + ASSET)
    if len(raw) > LIMIT or hashlib.sha256(raw).hexdigest() != DIGEST:
        raise ValueError('Pinned county build reference integrity failure')
    if output is not None:
        target = Path(output) / ASSET
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    return len(raw)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', help='Verify serving bytes instead of staging')
    args = parser.parse_args()
    preserve(origin=args.verify) if args.verify else preserve(Path('hosting-public'))
    print('Pinned county build reference verified and retained.')
