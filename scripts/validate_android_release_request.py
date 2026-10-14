#!/usr/bin/env python3
"""Accept one new immutable release request with a pinned ancestral source/version."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True, stderr=subprocess.DEVNULL).strip()


def validate(repo, request_path):
    request_path = str(request_path)
    if not re.fullmatch(r'\.github/release-requests/android/[0-9]+\.[0-9]+\.[0-9]+\.json', request_path):
        raise ValueError('Unsupported release request path')
    data = json.loads((Path(repo) / request_path).read_text())
    if (set(data) != {'schema_version', 'version_name', 'version_code', 'source_sha', 'firebase_upload'}
            or data['schema_version'] != 1 or not isinstance(data['version_name'], str)
            or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', data['version_name'])
            or type(data['version_code']) is not int or data['version_code'] <= 0
            or not isinstance(data['source_sha'], str) or not re.fullmatch(r'[0-9a-f]{40}', data['source_sha'])
            or type(data['firebase_upload']) is not bool
            or request_path != '.github/release-requests/android/' + data['version_name'] + '.json'):
        raise ValueError('Invalid release request')
    # Requests are added once per version. Retry the failed workflow/job rather
    # than editing a request and accidentally producing another release.
    if git(repo, 'log', '--format=%H', '-1', 'HEAD^', '--', request_path):
        raise ValueError('Release request already exists; rerun its original job')
    subprocess.run(['git', '-C', str(repo), 'merge-base', '--is-ancestor', data['source_sha'], 'HEAD^'], check=True, stderr=subprocess.DEVNULL)
    gradle = git(repo, 'show', data['source_sha'] + ':android-prototype/app/build.gradle.kts')
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    code = re.search(r'versionCode\s*=\s*([0-9]+)', gradle)
    if not name or not code or name.group(1) != data['version_name'] or int(code.group(1)) != data['version_code']:
        raise ValueError('Pinned source does not match requested app version')
    return data


def changed_request(repo):
    paths = git(repo, 'diff', '--name-only', 'HEAD^', 'HEAD', '--', '.github/release-requests/android').splitlines()
    if len(paths) != 1:
        raise ValueError('A release commit must add exactly one request')
    return paths[0]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path('.'))
    args = parser.parse_args()
    data = validate(args.repo, changed_request(args.repo))
    print('source_sha=' + data['source_sha'])
    print('firebase_upload=' + str(data['firebase_upload']).lower())
    print('version_name=' + data['version_name'])
