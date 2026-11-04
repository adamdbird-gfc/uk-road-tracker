"""Owner-evidenced, single-attempt NCN publication. No generic quota bypass."""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request

ROOT = Path('docs/data-trials/ncn-publication-readiness-20261104')
FIELDS = ('revision', 'sha256', 'compressed_sha256', 'compressed_bytes', 'database_bytes')
PROJECT = 'roadprints-822cc'
SITE = 'roadprints-data-822cc'
REPOSITORY = 'adamdbird-gfc/uk-road-tracker'


def read(path):
    return json.loads(Path(path).read_text())


def identity(manifest):
    return {key: manifest[key] for key in FIELDS}


def assess(auth, manifest, now=None):
    from ncn_reference import validate
    validate(manifest)
    now = now or dt.datetime.now(dt.timezone.utc)
    evidence = auth['usage_evidence']
    day = dt.date.fromisoformat(evidence['observed_on'])
    if (auth.get('schema_version') != 1 or auth.get('owner_authorised') is not True
            or not re.fullmatch(r'[a-z0-9-]{1,80}', auth.get('authorisation_id', ''))
            or auth.get('candidate') != identity(manifest)
            or auth.get('project') != PROJECT or auth.get('site') != SITE
            or auth.get('max_attempts') != 1):
        raise ValueError('Missing authorisation or candidate identity mismatch')
    if (evidence.get('source') != 'owner_firebase_console_all_sites'
            or evidence.get('plan') != 'Spark'
            or evidence.get('period') != now.strftime('%Y-%m')
            or not 0 <= (now.date() - day).days <= 1):
        raise ValueError('Missing, stale or wrong-period usage evidence')
    if not re.fullmatch(r'sites/' + SITE + r'/versions/[A-Za-z0-9_-]+', auth['expected_live_version']):
        raise ValueError('Unexpected authorised Hosting baseline')
    integers = ('monthly_download_limit_bytes', 'reported_download_bytes',
                'storage_limit_bytes', 'reported_storage_bytes',
                'traffic_and_lag_reserve_bytes', 'storage_reserve_bytes')
    if any(type(evidence.get(k)) is not int or evidence[k] < 0 for k in integers):
        raise ValueError('Invalid usage evidence values')
    cap = auth['max_preview_transfer_bytes']
    planned = manifest['compressed_bytes'] + 4 * 32769 + 65536
    if (type(cap) is not int or not planned <= cap <= 200_000_000
            or evidence['traffic_and_lag_reserve_bytes'] < 2_000_000_000
            or evidence['storage_reserve_bytes'] < 1024**3
            or evidence['monthly_download_limit_bytes'] > 10_000_000_000
            or evidence['storage_limit_bytes'] > 10_000_000_000):
        raise ValueError('Excessive transfers or insufficient conservative reserve')
    if (evidence['reported_download_bytes'] + cap + evidence['traffic_and_lag_reserve_bytes']
            >= evidence['monthly_download_limit_bytes']
            or evidence['reported_storage_bytes'] + cap + evidence['storage_reserve_bytes']
            >= evidence['storage_limit_bytes']):
        raise ValueError('Insufficient evidenced transfer or storage budget')
    rollback = read(auth['rollback_manifest'])
    old = read(auth['baseline_manifest'])
    if (rollback['revision'] <= manifest['revision']
            or identity({**rollback, 'revision': old['revision']}) != identity(old)
            or rollback['filename'] != old['filename']):
        raise ValueError('Rollback reference changed')
    from publish_ncn_reference import next_manifest
    next_manifest(manifest, rollback)
    return {'status': 'controlled_budget_verified', 'authorisation_id': auth['authorisation_id'],
            'usage_evidence': evidence, 'candidate': identity(manifest),
            'expected_live_version': auth['expected_live_version'],
            'max_preview_transfer_bytes': cap, 'planned_max_consumed_body_bytes': planned,
            'reporting_timestamp': 'backend timestamp unknown; owner observed on supplied date',
            'estimated_reported_download_bytes_after_preview':
                evidence['reported_download_bytes'] + manifest['compressed_bytes'],
            'monitoring_unavailable': auth['monitoring_unavailable'],
            'rollback_manifest': auth['rollback_manifest'], 'checked_at': now.isoformat()}


def selected():
    path = os.environ['NCN_CONTROLLED_AUTHORISATION']
    request = read('.github/ncn-reference/request.json')
    if request.get('controlled_authorisation') != path:
        raise ValueError('Controlled policy is not selected by the publication request')
    return read(path)


def preflight(work):
    from check_ncn_publication_quota import authenticated_session
    work.mkdir(parents=True, exist_ok=True)
    result = {'status': 'blocked', 'payload_bytes_downloaded': 0, 'hosting_writes': False}
    try:
        auth = selected()
        decision = assess(auth, read(ROOT/'candidate-manifest.json'))
        credentials = json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
        if (credentials.get('type') != 'service_account' or credentials.get('project_id') != PROJECT
                or os.environ.get('ROADPRINTS_DATA_HOSTING_SITE') != SITE):
            raise ValueError('Unexpected existing Hosting identity or site')
        response = authenticated_session(credentials).get(
            f'https://firebasehosting.googleapis.com/v1beta1/sites/{SITE}/channels/live',
            timeout=60, allow_redirects=False)
        if response.status_code != 200:
            raise ValueError('Existing Hosting access unavailable')
        live = response.json()['release']['version']['name']
        if live != auth['expected_live_version']:
            raise ValueError('Unexpected live Hosting baseline')
        result.update(decision, hosting_access='verified', live_version=live)
    except (ValueError, KeyError, TypeError, OSError):
        result['blocker'] = 'Controlled evidence, candidate, rollback or Hosting baseline failed validation'
    except Exception:
        result['blocker'] = 'Existing Hosting preflight unavailable; no transfers permitted'
    (work/'quota.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
    if result['status'] != 'controlled_budget_verified':
        raise SystemExit(1)


def claim(work):
    """Create-only GitHub tag: durable across reruns/cache eviction, under writer lock."""
    auth = selected()
    assess(auth, read(ROOT/'candidate-manifest.json'))
    if (os.environ.get('GITHUB_REPOSITORY') != REPOSITORY
            or os.environ.get('GITHUB_REF') != 'refs/heads/main'
            or os.environ.get('GITHUB_EVENT_NAME') != 'push'
            or os.environ.get('GITHUB_RUN_ATTEMPT') != '1'):
        raise ValueError('Publication is restricted to first main-push attempt')
    receipt = read(work/'quota.json')
    if receipt.get('status') != 'controlled_budget_verified' or receipt.get('authorisation_id') != auth['authorisation_id']:
        raise ValueError('Verified controlled preflight missing')
    ref = 'refs/tags/ncn-publication-attempt-' + auth['authorisation_id']
    request = urllib.request.Request(
        'https://api.github.com/repos/' + REPOSITORY + '/git/refs',
        data=json.dumps({'ref': ref, 'sha': os.environ['GITHUB_SHA']}).encode(),
        headers={'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
                 'Accept': 'application/vnd.github+json', 'Content-Type': 'application/json'}, method='POST')
    # Exactly one create; an existing ref or an ambiguous failure consumes this request.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise ValueError('Unexpected attempt-claim redirect')
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=60) as response:
        if response.status != 201:
            raise ValueError('Publication attempt claim failed')
        created = json.loads(response.read(32768))
    if created.get('ref') != ref or created.get('object', {}).get('sha') != os.environ['GITHUB_SHA']:
        raise ValueError('Unexpected attempt claim identity')
    (work/'attempt.json').write_text(json.dumps({
        'ref': ref, 'run_id': os.environ['GITHUB_RUN_ID'], 'source_sha': os.environ['GITHUB_SHA'],
        'authorisation_sha256': hashlib.sha256(json.dumps(auth, sort_keys=True).encode()).hexdigest(),
        'max_attempts': 1, 'status': 'consumed_before_cache_or_transfers'}, indent=2)+'\n')


def publication_guard(manifest, work):
    auth = selected()
    assess(auth, manifest)
    attempt = read(work/'attempt.json')
    if (attempt['run_id'] != os.environ.get('GITHUB_RUN_ID')
            or attempt['source_sha'] != os.environ.get('GITHUB_SHA')
            or attempt['authorisation_sha256'] != hashlib.sha256(json.dumps(auth, sort_keys=True).encode()).hexdigest()
            or os.environ.get('GITHUB_RUN_ATTEMPT') != '1'):
        raise ValueError('Publication attempt receipt missing or mismatched')
    return auth


if __name__ == '__main__':
    claim(Path('ncn-publication-evidence'))
