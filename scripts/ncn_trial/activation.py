"""Local activation/backfill contract; no Android scheduling or production writes.

The assessor must supply independently supported matched-path evidence. This
queue does not establish eligibility from proximity or reconstructed vertices.
"""
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path

from .coverage import Evidence, MODES, TrialLedger


@dataclass(frozen=True)
class JourneyRecord:
    key: str
    revision: str
    mode: str
    source: str
    started_ms: int

    def fingerprint(self):
        return hashlib.sha256(json.dumps(asdict(self), sort_keys=True).encode()).hexdigest()


@dataclass(frozen=True)
class Assessment:
    evidence: Evidence
    ambiguous: bool = False


class ActivationTrial:
    def __init__(self, reference):
        self.reference = reference
        self.active = False
        self.activated_at_ms = None
        self.rows = {}

    @staticmethod
    def validate(record):
        if not isinstance(record, JourneyRecord) or not record.key or not record.revision \
                or not isinstance(record.started_ms, int) or isinstance(record.started_ms, bool):
            raise ValueError('Stable journey identity, revision and start time required')

    def activate(self, records, now_ms, *, complete_snapshot):
        if complete_snapshot is not True:
            raise ValueError('Activation requires a complete archive snapshot')
        records = list(records)
        for record in records:
            self.validate(record)
        if len({r.key for r in records}) != len(records):
            raise ValueError('Duplicate archive identities')
        if not isinstance(now_ms, int) or isinstance(now_ms, bool):
            raise ValueError('Activation time required')
        # Reconcile additions, corrections and deletions while disabled. Every
        # member of this activation snapshot is historical, even native captures.
        retained = {}
        for record in records:
            old = self.rows.get(record.key)
            if old and old['fingerprint'] == record.fingerprint():
                retained[record.key] = {**old, 'review_allowed': False}
            else:
                retained[record.key] = self.new_row(record, False)
        self.rows = retained
        self.active = True
        self.activated_at_ms = now_ms

    @staticmethod
    def new_row(record, review_allowed):
        return {'record': asdict(record), 'fingerprint': record.fingerprint(), 'state': 'queued',
                'review_allowed': review_allowed, 'ambiguous': False, 'evidence': None}

    def deactivate(self):
        self.active = False

    def upsert(self, record, *, origin):
        if origin not in {'bulk_import', 'live_recording', 'correction'}:
            raise ValueError('Explicit ingestion origin required')
        self.validate(record)
        if not self.active:
            return False
        old = self.rows.get(record.key)
        if old and old['fingerprint'] == record.fingerprint():
            return False
        live = origin == 'live_recording' and record.source == 'android_activity_capture' \
            and record.started_ms >= self.activated_at_ms
        # A correction of a post-activation recording may retain its eligibility
        # for targeted review, but importing an archive never creates prompts.
        review = live or bool(origin == 'correction' and old and old['review_allowed'])
        self.rows[record.key] = self.new_row(record, review)
        return True

    def remove(self, key):
        if self.active:
            self.rows.pop(key, None)

    def run_batch(self, assessor, *, limit=25):
        if not 1 <= limit <= 100:
            raise ValueError('Batch limit must be between 1 and 100')
        if not self.active:
            return 0
        work = [(key, row['fingerprint']) for key, row in self.rows.items() if row['state'] == 'queued'][:limit]
        processed = 0
        for key, fingerprint in work:
            if not self.active:
                break
            row = self.rows.get(key)
            if row is None or row['fingerprint'] != fingerprint:
                continue
            record = JourneyRecord(**row['record'])
            try:
                if record.mode not in MODES:
                    result = Assessment(Evidence(self.reference.version, record.mode, (), ('ineligible_mode',)))
                else:
                    result = assessor(record)
                if not isinstance(result, Assessment) or result.evidence.version != self.reference.version \
                        or result.evidence.mode != MODES.get(record.mode, record.mode):
                    raise ValueError('Assessor returned mismatched reference or mode evidence')
                # Validate bounds/identities before committing any result.
                validation = TrialLedger(self.reference)
                validation.put(key, result.evidence)
                validation.summary()
            except Exception:
                current = self.rows.get(key)
                if self.active and current and current['fingerprint'] == fingerprint:
                    self.rows[key] = {**current, 'state': 'failed', 'evidence': None, 'ambiguous': False}
                processed += 1
                continue
            # A callback may race a correction, deletion or deactivation.
            current = self.rows.get(key)
            if self.active and current and current['fingerprint'] == fingerprint:
                self.rows[key] = {**current, 'state': 'assessed', 'evidence': asdict(result.evidence),
                                  'ambiguous': result.ambiguous}
                processed += 1
        return processed

    def retry_failed(self):
        if self.active:
            for row in self.rows.values():
                if row['state'] == 'failed':
                    row['state'] = 'queued'

    def summary(self):
        ledger = TrialLedger(self.reference)
        for key, row in self.rows.items():
            if row['state'] == 'assessed' and row['evidence']:
                ledger.put(key, Evidence(**row['evidence']))
        return ledger.summary()

    def review_candidates(self):
        if not self.active:
            return []
        return [key for key, row in self.rows.items() if row['state'] == 'assessed'
                and row['review_allowed'] and row['ambiguous'] and row['record']['mode'] in MODES]

    def status(self):
        counts = {state: sum(r['state'] == state for r in self.rows.values()) for state in ('queued', 'assessed', 'failed')}
        return {'active': self.active, 'total': len(self.rows), **counts,
                'phase': 'paused' if not self.active else 'assessing' if counts['queued'] else
                         'needs_retry' if counts['failed'] else 'ready'}

    def export(self):
        return json.dumps({'schema_version': 1, 'reference_version': self.reference.version,
                           'active': self.active, 'activated_at_ms': self.activated_at_ms, 'rows': self.rows},
                          sort_keys=True, allow_nan=False)

    @classmethod
    def restore(cls, reference, raw):
        value = json.loads(raw)
        if value.get('schema_version') != 1 or value.get('reference_version') != reference.version:
            raise ValueError('Checkpoint/reference migration requires reconciliation')
        trial = cls(reference)
        trial.active = value['active']
        trial.activated_at_ms = value['activated_at_ms']
        trial.rows = value['rows']
        for key, row in trial.rows.items():
            record = JourneyRecord(**row['record'])
            trial.validate(record)
            if key != record.key or row['fingerprint'] != record.fingerprint() \
                    or row['state'] not in {'queued', 'assessed', 'failed'}:
                raise ValueError('Invalid checkpoint row')
        trial.summary()
        return trial

    def save(self, path):
        path = Path(path)
        temporary = path.with_name(path.name + '.tmp')
        temporary.write_text(self.export())
        temporary.replace(path)
