#!/usr/bin/env python3
"""Stage the exact prepared ZIP from an approved published GitHub release; no Firebase transport."""
import argparse
import hashlib
import json
import os
import re
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from urllib.parse import urlsplit

REPOSITORY = 'adamdbird-gfc/uk-road-tracker'
ASSET = 'roadprints-reference-pins-2026-10-27.zip'
BUNDLE_BYTES = 157393480
BUNDLE_SHA256 = 'd547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c'
MEMBERS = {
    'catalogue.db': (243724288, '973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093'),
    'boundaries.bin': (8992277, 'b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850'),
}
PAGE_SIZE = 100
MAX_PAGES = 20
METADATA_BYTES = 2 * 1024 * 1024


def allowed_url(url):
    parsed = urlsplit(url)
    return (parsed.scheme == 'https' and not parsed.username and not parsed.password
            and parsed.port is None and not parsed.fragment
            and ((parsed.hostname == 'api.github.com' and
                  (parsed.path == '/repos/' + REPOSITORY + '/releases' or
                   parsed.path.startswith('/repos/' + REPOSITORY + '/releases/')))
                 or parsed.hostname == 'release-assets.githubusercontent.com'))


class GitHubOnlyRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        if not allowed_url(newurl):
            raise ValueError('Non-GitHub release redirect rejected')
        redirected = super().redirect_request(request, fp, code, msg, headers, newurl)
        # A GitHub API token must never follow a signed redirect to asset storage.
        if redirected is not None and urlsplit(newurl).hostname != 'api.github.com':
            redirected.remove_header('Authorization')
        return redirected


def github_request(path, token, accept='application/vnd.github+json'):
    url = 'https://api.github.com/repos/' + REPOSITORY + '/releases' + path
    if not allowed_url(url):
        raise ValueError('Invalid GitHub release URL')
    return urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token,
        'Accept': accept, 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Roadprints-reference-bootstrap'})


def open_github(opener, request, operation, timeout=30):
    try:
        return opener.open(request, timeout=timeout)
    except urllib.error.HTTPError as error:
        code = error.code
        error.close()
        # Do not log response bodies, signed URLs or authentication headers.
        raise RuntimeError(
            f'GitHub {operation} failed (HTTP {code}). Published release visibility requires '
            'release access; listing and asset reads require contents: read. '
            '404 may hide inaccessible releases/assets. No retry, permission escalation, '
            'publication or public-download fallback was attempted.'
        ) from None


def list_github(opener, path, token, operation):
    # Construct pagination locally: never follow an API-provided Link/asset URL.
    for page in range(1, MAX_PAGES + 1):
        request = github_request(f'{path}?per_page={PAGE_SIZE}&page={page}', token)
        with open_github(opener, request, operation) as response:
            raw = response.read(METADATA_BYTES + 1)
        if len(raw) > METADATA_BYTES:
            raise ValueError('GitHub metadata exceeds limit')
        items = json.loads(raw)
        if (not isinstance(items, list) or len(items) > PAGE_SIZE
                or any(not isinstance(item, dict) for item in items)):
            raise ValueError('Invalid GitHub list response')
        yield from items
        if len(items) < PAGE_SIZE:
            return
    raise ValueError('GitHub pagination limit reached; cannot prove unique match')


def discover_asset(opener, tag, token):
    releases = [release for release in list_github(opener, '', token, 'release discovery')
                if release.get('tag_name') == tag]
    if len(releases) != 1:
        raise ValueError('Expected exactly one visible release matching the exact tag; '
                         'check draft visibility/push access if missing')
    release = releases[0]
    if release.get('draft') is not False or not release.get('published_at'):
        raise ValueError('Bootstrap source must be an explicitly published release')
    release_id = release.get('id')
    if type(release_id) is not int or release_id <= 0:
        raise ValueError('Invalid GitHub release identity')
    assets = [asset for asset in list_github(opener, f'/{release_id}/assets', token, 'asset discovery')
              if asset.get('name') == ASSET]
    if len(assets) != 1:
        raise ValueError('Expected exactly one approved asset filename')
    asset = assets[0]
    if (type(asset.get('size')) is not int or asset['size'] != BUNDLE_BYTES
            or asset.get('digest') != 'sha256:' + BUNDLE_SHA256
            or asset.get('state') != 'uploaded'):
        raise ValueError('Asset size, SHA-256 or upload state differs from approved input')
    asset_id = asset.get('id')
    if type(asset_id) is not int or asset_id <= 0:
        raise ValueError('Invalid GitHub asset identity')
    print(f'Exact published release {release_id}, asset {asset_id} and pinned metadata verified')
    return asset_id


def extract_verified(bundle, destination):
    if bundle.stat().st_size != BUNDLE_BYTES:
        raise ValueError('Bundle size differs from approved input')
    with bundle.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != BUNDLE_SHA256:
            raise ValueError('Bundle checksum differs from approved input')
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(bundle) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Duplicate ZIP member')
        # Only the two fixed root-level files are extracted; no ZIP paths/scripts execute.
        with tempfile.TemporaryDirectory(dir=destination) as work:
            for name, (size, expected) in MEMBERS.items():
                info = archive.getinfo(name)
                if info.file_size != size:
                    raise ValueError('Reference member size differs')
                target = Path(work) / name
                digest = hashlib.sha256()
                total = 0
                with archive.open(info) as source, target.open('wb') as out:
                    for chunk in iter(lambda: source.read(65536), b''):
                        total += len(chunk)
                        if total > size:
                            raise ValueError('Reference member exceeds pin')
                        digest.update(chunk)
                        out.write(chunk)
                if total != size or digest.hexdigest() != expected:
                    raise ValueError('Reference member checksum differs')
            for name in MEMBERS:
                (Path(work) / name).replace(destination / name)


def stage(tag, destination, token):
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,100}', tag):
        raise ValueError('Invalid release tag')
    if not token:
        raise ValueError('Read-only same-repository GitHub token required')
    opener = urllib.request.build_opener(GitHubOnlyRedirect())
    asset_id = discover_asset(opener, tag, token)
    with tempfile.TemporaryDirectory() as work:
        bundle = Path(work) / ASSET
        with open_github(opener, github_request('/assets/' + str(asset_id), token, 'application/octet-stream'), 'asset download', timeout=120) as source, bundle.open('wb') as out:
            total = 0
            for chunk in iter(lambda: source.read(65536), b''):
                total += len(chunk)
                if total > BUNDLE_BYTES:
                    raise ValueError('GitHub asset exceeds approved size')
                out.write(chunk)
        extract_verified(bundle, destination)
    print('Approved GitHub bundle and both pinned members verified; staging complete')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release-tag', required=True)
    parser.add_argument('--destination', type=Path, default=Path('reference-input'))
    args = parser.parse_args()
    stage(args.release_tag, args.destination, os.environ.get('GH_TOKEN', ''))
