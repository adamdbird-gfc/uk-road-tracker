"""Read existing Hosting access and project-wide quota before any payload transfer.

Uses the established deployment identity. Missing permissions, missing/stale
metrics or insufficient headroom block publication; never enables APIs or billing.
"""
import datetime as dt
import json
import os
from pathlib import Path

PROJECT = 'roadprints-822cc'
SITE = 'roadprints-data-822cc'
PREFIX = 'firebasehosting.googleapis.com/'
METRICS = ('network/monthly_sent', 'network/monthly_sent_limit',
           'storage/total_bytes', 'storage/limit')
RESERVE = 1024 ** 3
QUOTA_URL = f'https://monitoring.googleapis.com/v3/projects/{PROJECT}/timeSeries'
SCOPE = 'https://www.googleapis.com/auth/cloud-platform'


class PreflightBlocked(ValueError):
    pass


def quota_error(response):
    """Classify only known Google error fields; never persist raw response text.

    Error messages can contain principal identities or request data. Emit fixed
    labels only, including when a proxy returns HTML or malformed JSON.
    """
    result = {'http_status': response.status_code, 'cause': 'unclassified_api_error'}
    try:
        error = response.json()['error']
        status = error.get('status')
        if status in {'PERMISSION_DENIED', 'UNAUTHENTICATED', 'RESOURCE_EXHAUSTED',
                      'INVALID_ARGUMENT', 'NOT_FOUND', 'UNAVAILABLE'}:
            result['google_status'] = status
        for detail in error.get('details', []):
            if (detail.get('@type') == 'type.googleapis.com/google.rpc.ErrorInfo'
                    and detail.get('domain') == 'googleapis.com'):
                reason = detail.get('reason')
                causes = {'SERVICE_DISABLED': 'monitoring_api_disabled',
                          'IAM_PERMISSION_DENIED': 'monitoring_permission_denied',
                          'ACCESS_TOKEN_SCOPE_INSUFFICIENT': 'oauth_scope_insufficient',
                          'BILLING_DISABLED': 'api_billing_disabled',
                          'CONSUMER_INVALID': 'api_consumer_invalid'}
                if reason in causes:
                    result.update(reason=reason, cause=causes[reason])
        # Some Monitoring IAM failures supply only the canonical message.
        if (result['cause'] == 'unclassified_api_error'
                and status == 'PERMISSION_DENIED'
                and isinstance(error.get('message'), str)
                and 'monitoring.timeSeries.list' in error['message']):
            result['cause'] = 'monitoring_permission_denied'
    except (ValueError, KeyError, TypeError, AttributeError):
        pass
    return result


def authenticated_session(credentials):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2.service_account import Credentials
    return AuthorizedSession(Credentials.from_service_account_info(credentials, scopes=[SCOPE]))


def latest_values(series, now):
    if not series:
        raise PreflightBlocked('Quota metric unavailable; do not assume zero usage')
    values = []
    seen = set()
    for row in series:
        identity = json.dumps([row.get('resource'), row.get('metric')], sort_keys=True)
        if identity in seen:
            raise PreflightBlocked('Duplicate quota series')
        seen.add(identity)
        points = row.get('points', [])
        if not points:
            raise PreflightBlocked('Empty quota series')
        point = max(points, key=lambda p: p['interval']['endTime'])
        stamp = dt.datetime.fromisoformat(point['interval']['endTime'].replace('Z', '+00:00'))
        # Hosting monthly gauges sample every eight hours plus reporting delay.
        if not dt.timedelta(0) <= now - stamp <= dt.timedelta(hours=10):
            raise PreflightBlocked('Quota metric stale or future-dated')
        raw_value = point['value']['int64Value']
        if (not isinstance(raw_value, str) or not raw_value.isascii()
                or not raw_value.isdecimal()):
            raise PreflightBlocked('Invalid quota metric')
        value = int(raw_value)
        if value > 2**63 - 1:
            raise PreflightBlocked('Invalid quota metric')
        values.append(value)
    return values


def assess(metrics, now, transfer, storage):
    values = {k: latest_values(metrics[k], now) for k in METRICS}
    # Include every returned project domain/site, not just the NCN origin.
    # Conservative aggregation may block rather than undercount shared quotas.
    sent = sum(values['network/monthly_sent'])
    stored = sum(values['storage/total_bytes'])
    bandwidth_limit = min(values['network/monthly_sent_limit'])
    storage_limit = min(values['storage/limit'])
    if sent + transfer + RESERVE >= bandwidth_limit:
        raise PreflightBlocked('Insufficient measured Hosting transfer headroom')
    if stored + storage + RESERVE >= storage_limit:
        raise PreflightBlocked('Insufficient measured Hosting storage headroom')
    return dict(monthly_sent_bytes=sent, monthly_limit_bytes=bandwidth_limit,
                stored_bytes=stored, storage_limit_bytes=storage_limit,
                planned_verification_bytes=transfer, planned_storage_bytes=storage,
                reserve_bytes=RESERVE, metric_max_age_hours=10)


def run(work=Path('ncn-publication-evidence')):
    if os.environ.get('NCN_CONTROLLED_AUTHORISATION'):
        from controlled_ncn_publication import preflight
        return preflight(work)
    work.mkdir(parents=True, exist_ok=True)
    result = {'status': 'blocked', 'payload_bytes_downloaded': 0,
              'hosting_writes': False}
    try:
        credentials = json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
        if (credentials.get('project_id') != PROJECT or
                credentials.get('type') != 'service_account' or
                os.environ.get('ROADPRINTS_DATA_HOSTING_SITE') != SITE):
            raise PreflightBlocked('Existing Hosting credential/site configuration missing or mismatched')
        session = authenticated_session(credentials)
        live = session.get(f'https://firebasehosting.googleapis.com/v1beta1/sites/{SITE}/channels/live',
                           timeout=60, allow_redirects=False)
        if live.status_code != 200:
            raise PreflightBlocked('Existing Hosting access check HTTP ' + str(live.status_code))
        result['hosting_access'] = 'verified'
        result['live_version'] = live.json()['release']['version']['name']
        if not result['live_version'].startswith(f'sites/{SITE}/versions/'):
            raise PreflightBlocked('Existing live Hosting version has unexpected site')
        now = dt.datetime.now(dt.timezone.utc)
        metrics = {}
        for name in METRICS:
            rows = []
            params = {'filter': f'metric.type="{PREFIX}{name}"',
                      'interval.startTime': (now-dt.timedelta(hours=24)).isoformat(),
                      'interval.endTime': now.isoformat(), 'view': 'FULL', 'pageSize': 1000}
            seen = set()
            while True:
                result['quota_request'] = {'endpoint': QUOTA_URL, 'metric': PREFIX + name,
                                           'oauth_scope': SCOPE,
                                           'required_permission': 'monitoring.timeSeries.list'}
                response = session.get(QUOTA_URL,
                                       params=params, timeout=60, allow_redirects=False)
                if response.status_code != 200:
                    result['quota_error'] = quota_error(response)
                    raise PreflightBlocked('Current Hosting quota read HTTP ' + str(response.status_code) +
                                     '; quota unavailable; no payload transfer permitted')
                body = response.json()
                if (not isinstance(body, dict) or 'error' in body
                        or not isinstance(body.get('timeSeries', []), list)):
                    raise PreflightBlocked('Unexpected quota API response')
                rows.extend(body.get('timeSeries', []))
                token = body.get('nextPageToken')
                if token is not None and not isinstance(token, str):
                    raise PreflightBlocked('Unexpected quota page token')
                if not token:
                    break
                if token in seen:
                    raise PreflightBlocked('Repeated quota page')
                seen.add(token); params['pageToken'] = token
            metrics[name] = rows
        manifest = json.loads(Path('docs/data-trials/ncn-publication-readiness-20261104/candidate-manifest.json').read_text())
        result.update(assess(metrics, now, manifest['compressed_bytes'] + 65536,
                             manifest['compressed_bytes'] + 65536))
        result.update(status='quota_verified', checked_at=now.isoformat(), metrics=metrics)
    except PreflightBlocked as error:
        # Only our bounded messages are emitted, never HTTP response/credential bodies.
        result['blocker'] = str(error)
    except Exception:
        result['blocker'] = 'Existing authenticated quota preflight unavailable; inspect job status without logging credentials'
    (work/'quota.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k != 'metrics'}, indent=2))
    if result['status'] != 'quota_verified':
        raise SystemExit(1)


if __name__ == '__main__':
    run()

