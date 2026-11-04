"""Persist consumed CDN bodies and operations, including failures; never retry."""
import json
from pathlib import Path


class Transfers:
    def __init__(self, work, cap):
        self.path = Path(work)/'transfers.json'
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.exists():
            raise ValueError('Transfer attempt already started; no retry authorised')
        self.cap = cap
        self.operations = []
        baseline = Path(work).parent/'baseline-transfer.json'
        if baseline.exists():
            self.operations.append(json.loads(baseline.read_text()))
        self.save()

    def save(self):
        total = sum(o.get('consumed_body_bytes', 0) for o in self.operations)
        self.path.write_text(json.dumps({'max_transfer_bytes': self.cap,
            'consumed_cdn_body_bytes': total, 'automatic_large_transfer_retries': 0,
            'billing_note': 'Consumed bodies; billing overhead and client traffic are not measured',
            'operations': self.operations}, indent=2)+'\n')
        if total > self.cap:
            raise ValueError('Publication transfer cap exceeded')

    def operation(self, kind, **details):
        op = dict(kind=kind, status='started', **details)
        self.operations.append(op)
        self.save()
        return op

    def fetch(self, url, limit, kind, consume=None):
        import requests
        count = sum(o['kind'] == kind for o in self.operations)
        if (kind == 'catalogue' and count >= 1) or (kind == 'manifest' and count >= 4):
            raise ValueError('Duplicate catalogue or excess manifest requests prohibited')
        total = sum(o.get('consumed_body_bytes', 0) for o in self.operations)
        if total + limit + 1 > self.cap:
            raise ValueError('Planned request exceeds remaining transfer cap')
        op = self.operation(kind, url=url, limit_bytes=limit, consumed_body_bytes=0)
        body = bytearray()
        try:
            with requests.get(url, headers={'Accept-Encoding': 'identity'}, stream=True,
                              timeout=(30, 90), allow_redirects=False) as response:
                op['http_status'] = response.status_code
                if response.status_code != 200 or response.headers.get('Content-Encoding', 'identity') != 'identity':
                    raise ValueError('Unexpected Hosting response status or encoding')
                while True:
                    chunk = response.raw.read(min(65536, limit-op['consumed_body_bytes']+1))
                    if not chunk:
                        break
                    op['consumed_body_bytes'] += len(chunk)
                    self.save()
                    if op['consumed_body_bytes'] > limit:
                        raise ValueError('Hosted body exceeds its size bound')
                    if consume:
                        consume(chunk)
                    else:
                        body.extend(chunk)
                op['status'] = 'body_received'
            return bytes(body), op
        except Exception:
            op['status'] = 'failed_no_retry'
            raise
        finally:
            self.save()
