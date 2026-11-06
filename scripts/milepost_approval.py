"""Revision 3: reproducible OSM source acceptance, independent location/design decisions."""
import hashlib
import json
import math
from decimal import Decimal
from datetime import datetime
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'reference-data/millennium-mileposts'
ASSET = ROOT / 'android-prototype/app/src/main/assets/millennium-mileposts/catalogue.json'
POLICY = 'osm-source-acceptance-1'
DESIGNERS = {'mills':'Jon Mills','rowe':'Andrew Rowe','mccoll':'Iain McColl','dudgeon':'David Dudgeon'}
ATTRIBUTION = '© OpenStreetMap contributors; https://www.openstreetmap.org/copyright'


def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False) + '\n').encode()


def mp_ref(row):
    value=re.search(r"(?i)MP\s*([0-9]+)",row.get("sustrans_ref") or "")
    return value.group(1) if value else None


def classify(rows):
    """Fail closed on actual anomalies; nearby distinct references never merge by radius."""
    decisions=[]; ids=Counter(r['osm_id'] for r in rows)
    for r in rows:
        reasons=[]; rejected=[]; tags=r['source_tags']; oid=r['osm_id']
        if not re.fullmatch(r'osm:node:[1-9][0-9]*',oid): rejected.append('invalid_osm_point_identity')
        if ids[oid]>1: reasons.append('duplicate_source_identity')
        lat,lon=r['latitude'],r['longitude']
        if (type(lat) not in (int,float) or type(lon) not in (int,float)
                or not math.isfinite(lat) or not math.isfinite(lon) or not -90<=lat<=90 or not -180<=lon<=180):
            rejected.append('invalid_coordinates')
        elif not (49.8<=lat<=61 and -8.7<=lon<=2): reasons.append('outside_uk_source_envelope')
        # Original GB administrative-area query supplies scope; envelope is an anomaly check,
        # not a substitute for that query. Do not silently admit arbitrary bounding-box points.
        if tags.get('addr:country','GB') not in ('GB','UK'): reasons.append('conflicting_country_tag')
        if not tags.get('ncn_milepost') or tags.get('ncn_milepost') in ('no','false'): rejected.append('not_millennium_milepost')
        if any(tags.get(k) in ('yes','true') for k in ('removed','demolished','razed')): rejected.append('post_removed')
        issue=' '.join(tags.get(k,'') for k in ('fixme','note')).lower()
        if 'exact location uncertain' in issue or 'current status' in issue and 'unclear' in issue:
            reasons.append('explicit_unresolved_location')
        for other in rows:
            if other is r: continue
            # Same MP reference plus a nearby same-design node is an identity conflict to
            # review, not proof that either can be merged or counted twice.
            if mp_ref(r) is not None and mp_ref(r)==mp_ref(other) and r['design']==other['design']:
                separation=math.hypot((lon-other['longitude'])*math.cos(math.radians(lat))*111320,(lat-other['latitude'])*111320)
                if separation<100: reasons.append('nearby_reference_identity_conflict')
            # Exact overlapping nodes cannot prove two separate posts. No distance-only merging.
            if (lat,lon)==(other['latitude'],other['longitude']):
                reasons.append('coincident_source_records'); break
        design=tags.get('ncn_milepost','unknown')
        design=design if design in DESIGNERS else 'unknown'
        artist=tags.get('artist_name')
        attribution=design!='unknown' and (artist is None or artist in (DESIGNERS[design], 'John Mills' if design=='mills' else 'Rowe' if design=='rowe' else ''))
        decisions.append(dict(candidate_id=oid,status='rejected' if rejected else 'held' if reasons else 'approved',
            reasons=sorted(set(rejected or reasons)),designer=design if attribution else 'unknown',
            designer_verified=attribution,attribution_reason='explicit_osm_design_tag' if attribution else 'unsupported_or_conflicting_attribution',
            temporal_policy='snapshot_forward_known_movement' if re.search(r'\bmov(?:ed|e)\b|appeared after',issue) else 'source_assumed_stable',
            reviewed_at='2026-11-06T00:00:00Z',policy=POLICY))
    return decisions


def build():
    source=json.loads((DATA/'revision-1/manifest.json').read_text())
    raw=(DATA/'revision-1/catalogue.jsonl').read_bytes()
    assert hashlib.sha256(raw).hexdigest()==source['sha256']
    rows=[json.loads(line) for line in raw.splitlines()]
    decisions=classify(rows); records=[]
    for r,d in zip(rows,decisions):
        if d['status']!='approved': continue
        snapshot=source['source']['snapshot_at']; oid=r['osm_id']
        records.append(dict(status='approved',identity_status='verified',location_status='verified',scope='UK',
            physical_marker_id='roadprints:millennium:'+oid.replace(':','-'),aliases=[oid],
            longitude=r['longitude'],latitude=r['latitude'],designer=d['designer'],designer_verified=d['designer_verified'],
            designer_evidence=d['attribution_reason'],source_tags=r['source_tags'],
            coordinate_evidence='OSM point in pinned GB administrative-area snapshot',
            precision_resolution='Source coordinate precision accepted; no Roadprints survey or accuracy-radius enlargement',
            validity_evidence=d['temporal_policy'],temporal_policy=d['temporal_policy'],
            # Policy bounds, explicitly NOT claimed survey/installation dates.
            valid_from=snapshot if d['temporal_policy']=='snapshot_forward_known_movement' else '2000-01-01T00:00:00Z',
            valid_until='9999-12-31T23:59:59Z',reviewed_at=d['reviewed_at'],
            evidence=[dict(url='https://www.openstreetmap.org/node/'+oid.split(':')[-1],licence='ODbL-1.0',
                attribution=ATTRIBUTION,observed_at=snapshot,receipt_sha256=source['source']['sha256'])]))
    catalogue=dict(schema_version=1,revision=3,scope='approved-physical-mileposts',policy=POLICY,
        evaluator='android-archive-milepost-1/milepost-proximity-1',records=records,licence='ODbL-1.0',
        attribution=ATTRIBUTION,source=source['source'],source_url='https://github.com/adamdbird-gfc/uk-road-tracker/tree/main/reference-data/millennium-mileposts')
    return canonical(catalogue),b''.join(canonical(d) for d in decisions)


def receipt(payload,decisions):
    rows=[json.loads(x) for x in decisions.splitlines()]; records=json.loads(payload)['records']
    return dict(revision=3,policy=POLICY,sha256=hashlib.sha256(payload).hexdigest(),bytes=len(payload),
        decisions_sha256=hashlib.sha256(decisions).hexdigest(),input_count=len(rows),marker_fingerprint=marker_fingerprint(payload),
        counts={s:sum(r['status']==s for r in rows) for s in ('approved','held','rejected')},
        reason_counts=dict(Counter(reason for r in rows for reason in r['reasons'])),
        approved_attributed=sum(r['designer_verified'] for r in records),
        approved_unknown=sum(not r['designer_verified'] for r in records),
        production_ready=bool(records),source=json.loads(payload)['source'])


def marker_fingerprint(payload):
    """Existing Android typed-value digest; includes Java's scientific double spelling."""
    rows=[]
    for r in json.loads(payload)['records']:
        time=lambda x:int(datetime.fromisoformat(x.replace('Z','+00:00')).timestamp()*1000)
        rows.append([r['physical_marker_id'],r['longitude'],r['latitude'],True,time(r['valid_from']),time(r['valid_until']),sorted(r['aliases']),r['designer'],r['designer_verified']])
    rows.sort(key=lambda r:r[0]);digest=hashlib.sha256()
    def hash_value(value):
        if isinstance(value,dict):
            digest.update(b'{')
            for key in sorted(value):hash_value(key);hash_value(value[key])
            digest.update(b'}')
        elif isinstance(value,list):
            digest.update(b'[')
            for item in value:hash_value(item)
            digest.update(b']')
        else:
            scalar=json.dumps(value,ensure_ascii=False,separators=(',',':'))
            if isinstance(value,float):
                if 0<abs(value)<.001:
                    mantissa,exponent=format(Decimal(str(value)).normalize(),'E').split('E')
                    scalar=mantissa+('.0' if '.' not in mantissa else '')+'E'+str(int(exponent))
                elif scalar.endswith('.0'):scalar=scalar[:-2]
            digest.update(scalar.encode());digest.update(b'\0')
    hash_value({'markers':rows})
    return digest.hexdigest()


def verify():
    payload,decisions=build(); expected=receipt(payload,decisions)
    assert ASSET.read_bytes()==payload
    assert (DATA/'revision-3/catalogue.json').read_bytes()==payload
    assert (DATA/'revision-3/decisions.jsonl').read_bytes()==decisions
    assert json.loads((DATA/'revision-3/manifest.json').read_text())==expected
    java=(ROOT/'android-prototype/app/src/main/java/com/roadprints/capture/MilepostCatalogue.java').read_text()
    assert expected['sha256'] in java
    assert marker_fingerprint(payload) in java
    assert 'APPROVED_COUNT='+str(expected['counts']['approved']) in java
    print('PASS: revision 3',expected['counts'],expected['sha256'])


if __name__=='__main__':
    import sys
    if '--write' in sys.argv:
        payload,decisions=build(); out=DATA/'revision-3';out.mkdir(exist_ok=True)
        ASSET.write_bytes(payload);(out/'catalogue.json').write_bytes(payload);(out/'decisions.jsonl').write_bytes(decisions)
        (out/'manifest.json').write_bytes(canonical(receipt(payload,decisions)))
        print(json.dumps(receipt(payload,decisions),indent=2))
    else: verify()
