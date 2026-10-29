"""Experimental completion bindings for pinned, explicitly open route extents.

Correspondence is considered only AFTER exact-source catalogue admission. It
does not match GPS, create route membership, or colour the publisher's line as
travelled. Ambiguous correspondence stays unbound in a fixed denominator.
"""
from dataclasses import asdict, replace
import gzip
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union
from shapely.strtree import STRtree

from matched_path_evidence import digest
from .alternatives import AlternativeSpan, LogicalSection
from .association_bundle import build_bundle, read_bundle
from .coverage import Section, TrialReference, merge_intervals
from .inventory_completion import InventoryLedger
from .journey_replay import projector
from .source_identity_review import read_catalogue


POLICY = {'name': 'unique_monotone_open_edge_correspondence_v1',
          'maximum_offset_m': 10.0, 'maximum_angle_degrees': 15.0,
          'ambiguous_intervals': 'unbound', 'endpoint_extrapolation': False,
          'grants_source_membership': False, 'changes_travelled_geometry': False}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def edge_interval(first, last, target):
    """Physical and target distances on a single pair of straight edges.

    Clip to both finite edges and the perpendicular offset bound, without
    snapping endpoints past a target's end. The result is monotone in either
    direction. An isolated crossing cannot satisfy the direction gate.
    """
    px, py = first; dx, dy = last[0]-px, last[1]-py
    length = math.hypot(dx, dy)
    q, z = list(target.coords)
    ex, ey = z[0]-q[0], z[1]-q[1]; total = math.hypot(ex, ey)
    if min(length, total) <= 1e-7:
        return None
    dx, dy, ex, ey = dx/length, dy/length, ex/total, ey/total
    slope = dx*ex+dy*ey
    if abs(slope) < math.cos(math.radians(POLICY['maximum_angle_degrees'])):
        return None
    origin = (px-q[0])*ex+(py-q[1])*ey
    lo, hi = max(0., min(-origin/slope, (total-origin)/slope)), min(length, max(-origin/slope, (total-origin)/slope))
    cross = (px-q[0])*ey-(py-q[1])*ex
    cross_slope = dx*ey-dy*ex
    limit = POLICY['maximum_offset_m']
    if abs(cross_slope) <= 1e-12:
        if abs(cross) > limit:
            return None
    else:
        a, b = sorted(((-limit-cross)/cross_slope, (limit-cross)/cross_slope))
        lo, hi = max(lo, a), min(hi, b)
    if hi-lo <= 1e-7:
        return None
    return lo, hi, max(0., min(total, origin+lo*slope)), max(0., min(total, origin+hi*slope))


def bind_edges(reference, paths, target_edges, scope):
    """Keep only uniquely corresponding physical intervals, per source section."""
    logical = [LogicalSection(key, line.length, (scope,)) for key, line in target_edges]
    tree = STRtree([line for _, line in target_edges])
    modes = {}
    for path in paths:
        if path.target_geometry != 'exact_source_spans':
            raise ValueError('Exact admitted source spans required')
        for key in path.section_keys:
            if key in modes:
                raise ValueError('A source section must have one association owner')
            modes[key] = path.modes
    if set(modes) != set(reference.sections):
        raise ValueError('Every physical section must have an admitted association')
    bindings = []; ambiguous = 0.; unbound = 0.
    for key, section in sorted(reference.sections.items()):
        offset = 0.
        for first, last in zip(section.line.coords, list(section.line.coords)[1:]):
            segment = LineString([first, last]); length = segment.length
            if length <= 1e-7:
                continue
            candidates = []
            for i in sorted(int(i) for i in tree.query(segment.buffer(POLICY['maximum_offset_m']))):
                interval = edge_interval(first, last, target_edges[i][1])
                if interval:
                    candidates.append((i, *interval))
            cuts = sorted({0., length, *(x for c in candidates for x in c[1:3])})
            for a, z in zip(cuts, cuts[1:]):
                if z-a <= 1e-7:
                    continue
                mid = (a+z)/2
                active = [c for c in candidates if c[1] < mid < c[2]]
                if len(active) != 1:
                    unbound += z-a
                    if len(active) > 1:
                        ambiguous += z-a
                    continue
                i, lo, hi, ta, tz = active[0]
                def at(value):
                    return max(0.,min(target_edges[i][1].length,ta+(value-lo)/(hi-lo)*(tz-ta)))
                first=max(0.,min(section.line.length,offset+a))
                last=max(0.,min(section.line.length,offset+z))
                start,end=at(a),at(z)
                if last-first<=1e-7 or abs(end-start)<=1e-7:
                    unbound+=z-a
                    continue
                bindings.append(AlternativeSpan(key, first, last,
                    target_edges[i][0], start, end, modes[key]))
            offset += length
    return logical, bindings, {'ambiguous_source_occurrence_m': ambiguous,
                                'unbound_source_occurrence_m': unbound}


def build_completion(folder, expected_manifest_sha256):
    """Consume a trusted reviewed catalogue; preserve every open target edge."""
    folder = Path(folder)
    source, metadata, paths, extent = read_catalogue(folder, expected_manifest_sha256)
    provenance = json.loads(gzip.decompress((folder/'associations.json.gz').read_bytes()))['provenance']
    return build_verified_completion(source,metadata,paths,extent,expected_manifest_sha256,provenance)


def build_verified_completion(source,metadata,paths,extent,input_sha256,provenance,deferred_way_ids=()):
    """Internal compiler boundary: callers must validate admitted inputs first.

    Existing either-arc source contracts are not reinterpreted as ordinary
    publisher edges. Their source spans and nearby target edges stay unbound
    until an explicit open-target alternative correspondence is established.
    """
    key = extent['route_key']; scope = key+':captured-open-main'
    project = projector(metadata); lines = []
    for feature in extent['features']:
        geometry = feature['geometry']
        parts = [geometry['coordinates']] if geometry['type']=='LineString' else geometry['coordinates']
        lines.extend(LineString([project(c) for c in part]) for part in parts)
    union = unary_union(lines)
    parts = [union] if union.geom_type=='LineString' else list(union.geoms)
    edges = {}
    for part in parts:
        for a, z in zip(part.coords, list(part.coords)[1:]):
            a, z = sorted((tuple(a), tuple(z))); line = LineString([a, z])
            if line.length > 1e-7:
                # Full precision; never coalesce distinct nearby paths.
                edges['open-edge:'+digest((a, z))] = line
    logical, bindings, diagnostics = bind_edges(source, paths, sorted(edges.items()), scope)
    deferred=sorted(set(str(w)for w in deferred_way_ids)&{p.way_id for p in paths})
    if deferred:
        blocked={key for p in paths if p.way_id in deferred for key in p.section_keys}
        corridor=unary_union([source.sections[k].line for k in blocked]).buffer(POLICY['maximum_offset_m'])
        blocked_targets={k for k,line in edges.items()if line.intersects(corridor)}
        removed=[b for b in bindings if b.physical_key in blocked or b.logical_key in blocked_targets]
        bindings=[b for b in bindings if b.physical_key not in blocked and b.logical_key not in blocked_targets]
        diagnostics.update(deferred_alternative_source_section_count=len(blocked),
            deferred_alternative_target_edge_count=len(blocked_targets),
            deferred_alternative_binding_count=len(removed))
        diagnostics['unbound_source_occurrence_m']+=sum(b.physical_end-b.physical_start for b in removed)
    target_length = sum(s.length_m for s in logical)
    if abs(target_length-extent['canonical_open_union_m']) > 1e-5:
        raise ValueError('Complete open target denominator changed')
    physical = [{'key': s.key, 'coordinates': list(s.line.coords), 'routes': list(s.routes)}
                for s in sorted(source.sections.values(), key=lambda s:s.key)]
    payload = {'schema_version': 1, 'route_key': key, 'scope': scope, 'policy': POLICY,
        'catalogue_manifest_sha256': input_sha256,
        'source_reference_version': source.version, 'projection_origin': metadata['projection_origin'],
        'physical_sections_metric': physical,
        'logical_sections': [{'key':s.key,'length_m':s.length_m,'routes':list(s.routes)} for s in logical],
        'bindings': [asdict(b) for b in bindings],
        'canonical_open_length_m': target_length,
        'unbound_open_target_retained': True, 'whole_published_route_ready': False,
        'android_activation_ready': False, 'diagnostics': diagnostics}
    if deferred:
        payload['deferred_alternative_way_ids']=deferred
        payload['source_alternative_policy_preserved_separately']=True
    capacity = {}
    for mode in ('on_foot', 'cycling', 'combined'):
        intervals = {}
        for b in bindings:
            if mode=='combined' or mode in b.modes:
                intervals.setdefault(b.logical_key, []).append((b.logical_start,b.logical_end))
        capacity[mode] = sum(z-a for spans in intervals.values() for a,z in merge_intervals(spans))
    payload['maximum_bound_open_length_m'] = capacity
    payload['unbound_open_length_m'] = max(0., target_length-capacity['combined'])
    payload['reference_version'] = 'open-completion:'+digest(payload)
    reference = TrialReference(payload['reference_version'], list(source.sections.values()))
    ledger = InventoryLedger(reference, logical, bindings)
    new_paths = tuple(replace(p, reference_version=reference.version) for p in paths)
    bundle = build_bundle(new_paths, reference, metadata['projection_origin'], provenance)
    return ledger, bundle, payload


def read_completion(raw, bundle, expected_plan_sha256, expected_bundle_sha256):
    """Portable consumer; the two hashes must come from a trusted checkpoint."""
    if sha(raw)!=expected_plan_sha256 or sha(bundle)!=expected_bundle_sha256:
        raise ValueError('Open completion checksum differs')
    plan = json.loads(raw); unsigned = {k:v for k,v in plan.items() if k!='reference_version'}
    if (plan.get('schema_version')!=1 or plan.get('policy')!=POLICY
            or plan.get('reference_version')!='open-completion:'+digest(unsigned)
            or plan.get('scope')!=plan.get('route_key','')+':captured-open-main'
            or plan.get('whole_published_route_ready') is not False
            or plan.get('android_activation_ready') is not False
            or plan.get('unbound_open_target_retained') is not True):
        raise ValueError('Open completion scope or policy differs')
    reference = TrialReference(plan['reference_version'], [Section(s['key'],LineString(s['coordinates']),tuple(s['routes']))
        for s in plan['physical_sections_metric']])
    logical = [LogicalSection(s['key'],s['length_m'],tuple(s['routes'])) for s in plan['logical_sections']]
    if (any(s.routes!=(plan['scope'],) for s in logical)
            or abs(sum(s.length_m for s in logical)-plan['canonical_open_length_m'])>1e-5):
        raise ValueError('Open completion denominator differs')
    ledger = InventoryLedger(reference,logical,[AlternativeSpan(**b) for b in plan['bindings']])
    paths = read_bundle(bundle,reference,plan['projection_origin'],digest(json.loads(bundle)))
    allowed = {s:m for p in paths for s in p.section_keys for m in [set(p.modes)]}
    if any(not set(b.modes)<=allowed.get(b.physical_key,set()) for b in ledger.bindings):
        raise ValueError('Completion binding exceeds admitted mode policy')
    return ledger,paths,plan


def activation_trial(raw,bundle,expected_plan_sha256,expected_bundle_sha256):
    """Wire the portable plan into resumable retrospective saved-path assessment."""
    from .activation import ActivationTrial
    ledger,paths,plan=read_completion(raw,bundle,expected_plan_sha256,expected_bundle_sha256)
    def factory(reference):
        return InventoryLedger(reference,tuple(ledger.logical.values()),ledger.bindings)
    worker=ActivationTrial(ledger.reference,ledger_factory=factory,
        accounting_key='captured-open-main:'+expected_plan_sha256)
    return worker,{'projection_origin':plan['projection_origin']},paths,plan


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalogues',type=Path,required=True)
    parser.add_argument('--expected-summary-sha256',required=True)
    parser.add_argument('--output',type=Path,required=True)
    a=parser.parse_args(); raw=(a.catalogues/'summary.json').read_bytes()
    if sha(raw)!=a.expected_summary_sha256:
        raise ValueError('Independently pinned catalogue checkpoint required')
    summary=json.loads(raw); rows=summary['routes']
    if len({r['route_key'] for r in rows})!=len(rows):
        raise ValueError('Duplicate checkpoint route')
    if a.output.exists():
        raise ValueError('Fresh completion output required')
    a.output.mkdir(parents=True); results=[]
    for row in rows:
        if row['status']!='supported_open_spans':
            continue
        key=row['route_key']; folder=a.catalogues/key.replace(':','-')
        ledger,bundle,plan=build_completion(folder,row['manifest_sha256'])
        plan_raw=json.dumps(plan,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
        read_completion(plan_raw,bundle,sha(plan_raw),sha(bundle))
        out=a.output/key.replace(':','-');out.mkdir()
        (out/'plan.json.gz').write_bytes(gzip.compress(plan_raw,mtime=0))
        (out/'associations.json.gz').write_bytes(gzip.compress(bundle,mtime=0))
        manifest={'route_key':key,'plan_sha256':sha(plan_raw),'association_sha256':sha(bundle),
            'reference_version':plan['reference_version'],'catalogue_manifest_sha256':row['manifest_sha256']}
        (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        results.append({**manifest,'canonical_open_length_m':plan['canonical_open_length_m'],
            'maximum_bound_open_length_m':plan['maximum_bound_open_length_m'],
            'unbound_open_length_m':plan['unbound_open_length_m'],**plan['diagnostics']})
        print(key,'bindings',len(ledger.bindings),'unbound target m',round(plan['unbound_open_length_m'],3),flush=True)
    (a.output/'summary.json').write_text(json.dumps({'schema_version':1,
        'catalogue_summary_sha256':a.expected_summary_sha256,'routes':results,
        'held_route_keys':[r['route_key']for r in rows if r['status']=='held'],
        'whole_published_route_ready':False,'android_activation_ready':False},indent=2)+'\n')


if __name__=='__main__':
    main()
