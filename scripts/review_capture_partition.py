"""Non-destructive offline partitions of exported capture evidence; no importer or matcher.

A human-reviewed plan supplies contiguous raw sample index ranges. Output is review-only,
not Roadprints journeys: no geometry, inferred distance, matching or completion entitlement.
"""
import argparse
import hashlib
import json
from pathlib import Path


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def partition(export_path, plan_path, output):
    export_path, plan_path, output = map(Path, (export_path, plan_path, output))
    original = export_path.read_bytes()
    export = json.loads(original)
    journey = export.get('journey', export)
    raw = journey.get('raw_capture_samples')
    if not isinstance(raw, list) or not raw:
        raise ValueError('Original timestamped raw_capture_samples are required')
    plan = json.loads(plan_path.read_bytes())
    if plan.get('input_sha256') != digest(original):
        raise ValueError('Plan does not match immutable original export')
    ranges = plan.get('partitions', [])
    cursor = 0
    for item in ranges:
        start, end = item.get('start'), item.get('end')
        if type(start) is not int or type(end) is not int or start != cursor or not start < end <= len(raw):
            raise ValueError('Ranges must be contiguous, non-overlapping, non-empty and cover every raw sample')
        if item.get('kind') not in ('movement_candidate', 'stationary_or_unresolved', 'handover_uncertain'):
            raise ValueError('Unknown partition kind')
        if not item.get('reason'):
            raise ValueError('Each partition needs an evidence/review limitation')
        cursor = end
    if cursor != len(raw):
        raise ValueError('Plan leaves original samples unassigned')
    # New directory only: never overwrite the source or an earlier recovery review.
    output.mkdir(parents=True, exist_ok=False)
    (output/'original-export.json').write_bytes(original)
    manifest = {'format': 'roadprints_capture_review_partition', 'schema_version': 1,
                'input_sha256': digest(original), 'raw_sample_count': len(raw),
                'applied_to_device': False, 'grants_completion_credit': False,
                'review_required': True, 'partitions': []}
    for index, item in enumerate(ranges):
        points = raw[item['start']:item['end']]
        key = f'{index:02d}-{item["kind"]}.json'
        record = {'format': 'roadprints_capture_review_samples', 'schema_version': 1,
                  'input_sha256': digest(original), 'source_index_start_inclusive': item['start'],
                  'source_index_end_exclusive': item['end'], 'kind': item['kind'],
                  'label': item.get('label', ''), 'reason': item['reason'],
                  'suggested_mode': item.get('suggested_mode', 'unknown'),
                  'transport_confirmed': False, 'review_required': True,
                  'grants_completion_credit': False, 'raw_samples': points}
        (output/key).write_text(json.dumps(record, separators=(',', ':'))+'\n')
        manifest['partitions'].append({'file': key, 'start': item['start'], 'end': item['end'],
                                      'count': len(points), 'kind': item['kind'],
                                      'label': item.get('label', ''),
                                      'first_sample_time_ms': points[0][3], 'last_sample_time_ms': points[-1][3]})
    (output/'review-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    # Independently reassemble emitted sample arrays to prove none were lost or silently changed.
    recovered = []
    for item in manifest['partitions']:
        recovered.extend(json.loads((output/item['file']).read_bytes())['raw_samples'])
    if recovered != raw or export_path.read_bytes() != original:
        raise AssertionError('Original/reassembled evidence mismatch')
    return manifest


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input', required=True)
    p.add_argument('--plan', required=True)
    p.add_argument('--output', required=True)
    a = p.parse_args()
    m = partition(a.input, a.plan, a.output)
    print(json.dumps({'raw_samples': m['raw_sample_count'], 'partitions': len(m['partitions']),
                      'original_preserved': True, 'completion_credit': 0, 'applied_to_device': False}))
