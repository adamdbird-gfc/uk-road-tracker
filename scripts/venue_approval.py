"""Common partial/complete approval checks for reviewed venue releases."""
import collections,re
from datetime import date,datetime,timezone

def instant(value):return datetime.fromisoformat(value.replace('Z','+00:00')).astimezone(timezone.utc)
def validate_approval(root):
    if root.get('visit_enabled') is not False:raise ValueError('Data cannot enable visits')
    if root.get('approval_scope')=='map_only_partial_beta':
        if root.get('coverage_complete') is not False or 'completion_target' not in root or root['completion_target'] is not None:raise ValueError('Partial approval cannot certify completion')
    elif root.get('approval_scope')=='reviewed_uk_completion_v1':
        rows=root['venues'];review=root['coverage_review'];counts=dict(collections.Counter(v['county']['code'] for v in rows))
        if root.get('coverage_complete') is not True or not rows or root['completion_target']!=len(rows) or review['active_members']!=len(rows) or review['current_members']!=len(rows)+review['temporarily_closed_members'] or review['source_listings']-review['outside_uk']-review['future_openings']-review['former_members']!=review['current_members'] or review['county_counts']!=counts or not re.fullmatch('[0-9a-f]{64}',review['membership_sha256']) or not review['source_url'].startswith('https://') or not review['review_id']:raise ValueError('Unreconciled complete target')
        if date.fromisoformat(review['reviewed_at'])>date.today() or date.fromisoformat(review['reference_snapshot_date'])>date.fromisoformat(review['reviewed_at']):raise ValueError('Future review')
        started=instant(root['lifecycle_started_at']);now=datetime.now(timezone.utc)
        if started>now:raise ValueError('Future lifecycle baseline')
        active={v['id'] for v in rows};closed=set()
        for v in root['lifecycle']:
            at=instant(v['permanently_closed_at']);known=instant(v['known_active_from'])
            if not 49<=v['lat']<=61 or not -9<=v['lon']<=2:raise ValueError('Missing last-known reviewed position')
            if v['id'] in active or v['id'] in closed or v['status']!='permanently_closed' or not started<at<=now or not known<at or not v['evidence_url'].startswith('https://') or not v['name'] or not v['county']['code'] or not at.date()<=date.fromisoformat(v['reviewed_at'])<=date.today():raise ValueError('Unverified future closure')
            closed.add(v['id'])
    else:raise ValueError('Unsupported approval scope')
