#!/usr/bin/env python3
"""One-off cleanup of superseded CI outputs, preserving releases and final evidence."""
import datetime as dt
import json
import os
from pathlib import Path
import re
import time
import requests

REPO = 'adamdbird-gfc/uk-road-tracker'
CUTOFF = '2026-10-15T13:00:00Z'
RELEASE_RUNS = {38727690316, 38603408167}
OBSOLETE = re.compile(r'(github-pages|roadprints-capture-prototype-debug|roadprints-verification-reports|android-check-reports-.*|reference-data-.*|settlement-inventory-.*|settlement-inventories-v1|roadprints-android-debug-.*|Roadprints-.*-preview)')


def select(rows):
    pages = [r for r in rows if r['name'] == 'github-pages']
    latest_page = max(pages, key=lambda r: (r['created_at'], r['id']))['id'] if pages else None
    return [r for r in rows if r['created_at'] < CUTOFF and not r['expired']
            and r['workflow_run']['id'] not in RELEASE_RUNS and r['id'] != latest_page
            and OBSOLETE.fullmatch(r['name'])]


def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO:
        raise ValueError('Unexpected repository')
    session = requests.Session()
    session.headers.update({'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json'})
    base = 'https://api.github.com/repos/' + REPO + '/actions/artifacts'
    def inventory():
        rows = []
        for page in range(1, 31):
            response = session.get(base, params={'per_page': 100, 'page': page}, timeout=60)
            response.raise_for_status()
            batch = response.json()['artifacts']
            rows.extend(batch)
            if len(batch) < 100:
                if len({r['id'] for r in rows}) != len(rows):
                    raise ValueError('Artifact list changed while paging')
                return rows
        raise ValueError('Artifact inventory exceeds safety limit')
    before = inventory()
    chosen = select(before)
    if not chosen or len(chosen) > 1500:
        raise ValueError('Unexpected cleanup size')
    # Snapshot the complete selection before any irreversible deletion.
    evidence = Path('maintenance-evidence')
    evidence.mkdir(exist_ok=True)
    (evidence / 'artifact-plan.json').write_text(json.dumps({'before': before, 'delete': chosen}, indent=2))
    deleted = []
    try:
        for row in chosen:
            response = session.delete(base + '/' + str(row['id']), timeout=60)
            if response.status_code not in (204, 404):
                response.raise_for_status()
            deleted.append(row['id'])
            time.sleep(0.3)
    finally:
        (evidence / 'artifact-deletions.json').write_text(json.dumps({'deleted': deleted}, indent=2))
    after = inventory()
    if set(deleted) & {r['id'] for r in after}:
        raise ValueError('Deleted artifacts still listed')
    protected = {r['id'] for r in before} - {r['id'] for r in chosen}
    if not protected <= {r['id'] for r in after}:
        raise ValueError('A protected artifact disappeared')
    (evidence / 'artifact-result.json').write_text(json.dumps({
        'status': 'verified', 'deleted_count': len(deleted),
        'deleted_bytes': sum(r['size_in_bytes'] for r in chosen),
        'remaining_count': len(after), 'remaining_bytes': sum(r['size_in_bytes'] for r in after),
        'completed_at': dt.datetime.now(dt.timezone.utc).isoformat()}, indent=2))
    print('Obsolete CI outputs removed; protected artifacts verified.')


if __name__ == '__main__':
    main()
