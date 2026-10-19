"""Reassess explicit membership holds against refreshed provider/node evidence.

This produces a guarded overlay for selected inventories. It does not relax the
ordinary builder's membership gate, migrate old journey evidence, or certify the
full published route. Replay uses pinned inputs and makes no network requests.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import LineString
from shapely.ops import unary_union

from matched_path_evidence import digest
from .association_bundle import build_bundle
from .catalogue_extract import _source_index, _memberships_from_index
from .confirmed_paths import build_candidate
from .inventory_completion import inventory_ways, build_plan, read_completion
from .network_geometry import extract_geometry, metric_projector
from .network_reconciliation import way_geometry
from .numbered_reconciliation import non_uk_namespace, uk_metadata_anchor
from .scope_review import review_scope
from .source_capture import verified_sources

REPRESENTATION_TOLERANCE_M = .03


def read_pinned(root, record):
    path = (root / record['file']).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError('Pinned file escapes its evidence directory')
    raw = gzip.decompress(path.read_bytes())
    if hashlib.sha256(raw).hexdigest() != record['sha256']:
        raise ValueError('Pinned provider evidence changed')
    return json.loads(raw)


def review_relation(key, previous, current, old_provider, new_provider, index, origin):
    """Approve a new source baseline only after complete identity/geometry checks."""
    kind, number = key.split(':')
    identity = previous['relation_id']
    if current['relation_id'] != identity:
        raise ValueError('Review relation identity differs')
    for profile in (previous, current):
        tags = profile['tags']
        if (not profile['member_lists_complete'] or tags.get('network') != kind.lower()
                or str(tags.get('ref')) != number or non_uk_namespace(tags)):
            raise ValueError('Complete unchanged numbered-source classification required')
    # Validate the full current hierarchy and all source nodes, not just the
    # newly added IDs. Exact current membership is extracted from this index.
    ways = inventory_ways(index, [identity])
    if set(ways) != {w['way_id'] for w in current['ways']}:
        raise ValueError('Review profile differs from captured current hierarchy')
    old = extract_geometry(old_provider, previous)
    new = extract_geometry(new_provider, current)
    if not old['provider_geometry_complete'] or not new['provider_geometry_complete']:
        raise ValueError('Complete pinned previous and refreshed provider geometry required')
    project = metric_projector(origin)
    maximum_drift = 0.
    for represented in new['ways']:
        way = ways[represented['way_id']]
        line = LineString([project([n['lon'], n['lat']]) for n in way['node_records']])
        drift = line.hausdorff_distance(way_geometry(represented, project))
        maximum_drift = max(maximum_drift, drift)
        if drift > REPRESENTATION_TOLERANCE_M:
            raise ValueError('Refreshed provider differs from captured current source nodes')
    before = unary_union([way_geometry(w, project) for w in old['ways']])
    after = unary_union([way_geometry(w, project) for w in new['ways']])
    removed_geometry = before.difference(after.buffer(REPRESENTATION_TOLERANCE_M)).length
    added_geometry = after.difference(before.buffer(REPRESENTATION_TOLERANCE_M)).length
    old_ids = {w['way_id'] for w in previous['ways']}
    new_ids = set(ways)
    return {'relation_id': identity, 'added_way_ids': sorted(new_ids - old_ids),
            'removed_way_ids': sorted(old_ids - new_ids),
            'previous_profile_sha256': digest(previous), 'current_profile_sha256': digest(current),
            'previous_source_relations': previous.get('source_relations', []),
            'current_source_relations': current.get('source_relations', []),
            'previous_provider_sha256': digest(old_provider), 'refreshed_provider_sha256': digest(new_provider),
            'current_source_way_count': len(ways), 'provider_node_maximum_drift_m': maximum_drift,
            'representation_tolerance_m': REPRESENTATION_TOLERANCE_M,
            'previous_geometry_outside_refreshed_m': removed_geometry,
            'refreshed_geometry_outside_previous_m': added_geometry,
            'geometry_review': ('same_linework_at_representation_tolerance'
                                if max(removed_geometry, added_geometry) <= 1e-5
                                else 'geometry_changed_requires_current_extent_assessment'),
            'membership_baseline_reconciled': True, 'old_journey_evidence_transferred': False,
            'whole_published_route_ready': False}


def load_providers(root, required):
    raw = (root / 'fetch-manifest.json').read_bytes()
    manifest = json.loads(raw)
    records = manifest.get('sources', [])
    ids = [r['relation_id'] for r in records]
    if (not manifest.get('complete') or manifest.get('errors') or len(set(ids)) != len(ids)
            or not set(required) <= set(ids)):
        raise ValueError('Complete distinct pinned provider capture required')
    return {r['relation_id']: read_pinned(root, r) for r in records if r['relation_id'] in required}, hashlib.sha256(raw).hexdigest()


def read_reviewed_overlay(folder, previous_plan_raw, expected_manifest_sha256):
    """Read only against the exact preceding plan and a separately pinned overlay."""
    manifest_raw = (folder / 'manifest.json').read_bytes()
    if hashlib.sha256(manifest_raw).hexdigest() != expected_manifest_sha256:
        raise ValueError('Reviewed overlay manifest changed')
    manifest = json.loads(manifest_raw)
    if (manifest.get('schema_version') != 1
            or hashlib.sha256(previous_plan_raw).hexdigest() != manifest['replaces_plan_sha256']
            or manifest.get('whole_published_route_ready') is not False):
        raise ValueError('Reviewed overlay does not replace this exact inventory')
    raw = gzip.decompress((folder / 'plan.json.gz').read_bytes())
    associations = gzip.decompress((folder / 'associations.json.gz').read_bytes())
    ledger, paths, plan = read_completion(raw, associations, manifest['plan_sha256'], manifest['association_sha256'])
    previous = json.loads(previous_plan_raw)
    if (manifest.get('reference_version') != plan['reference_version']
            or plan['route_key'] != previous['route_key']
            or plan['metadata'].get('membership_review_sha256') != manifest['membership_review_sha256']
            or plan['metadata'].get('source_snapshot_sha256') != previous['metadata'].get('source_snapshot_sha256')
            or plan['metadata']['projection_origin'] != previous['metadata']['projection_origin']
            or any(abs(plan[k] - previous[k]) > 1e-5 for k in
                   ('source_inventory_length_m', 'canonical_inventory_length_m'))):
        raise ValueError('Reviewed overlay source identity or fixed denominator differs')
    return ledger, paths, plan


def review_batch(research, sources, prior_catalogues, prior_plans, refreshed, network_path,
                 network_sha256, keys, output):
    if not keys or len(set(keys)) != len(keys):
        raise ValueError('Explicit distinct review keys required')
    source_raw = (sources / 'fetch-manifest.json').read_bytes()
    capture = json.loads(source_raw)
    source_sha = hashlib.sha256(source_raw).hexdigest()
    research_raw = (research / 'physical-national/summary.json').read_bytes()
    profile_raw = gzip.decompress((research / 'numeric-route-membership-profiles.json.gz').read_bytes())
    if capture.get('inputs') != {'physical_report_sha256': hashlib.sha256(research_raw).hexdigest(),
                                'membership_profiles_sha256': hashlib.sha256(profile_raw).hexdigest()}:
        raise ValueError('Captured baseline triage inputs changed')
    baseline = json.loads((prior_catalogues / 'summary.json').read_bytes())
    if baseline['source_capture_sha256'] != source_sha:
        raise ValueError('Prior catalogue uses another source capture')
    previous_rows = {r['route_key']: r for r in baseline['routes']}
    leads = {r['official_route_key']: r for r in json.loads(research_raw)['routes']}
    for key in keys:
        if (key not in capture['routes'] or not leads[key]['uk_metadata_anchor']
                or previous_rows[key].get('route_hold') != 'Source membership changed; reassess extent'):
            raise ValueError('Review must target an explicit pinned membership hold')
    network_raw = gzip.decompress(network_path.read_bytes())
    if hashlib.sha256(network_raw).hexdigest() != network_sha256:
        raise ValueError('Pinned official network changed')
    network = json.loads(network_raw)
    index, hashes = _source_index(verified_sources(sources, capture))
    previous_profiles = {p['relation_id']: p for p in json.loads(profile_raw)['profiles']}
    roots = {r for key in keys for r in leads[key]['candidate_relation_ids']}
    old_providers, old_provider_sha = load_providers(research / 'national-geometry', roots)
    new_providers, new_provider_sha = load_providers(refreshed, roots)
    profiles = {identity: _memberships_from_index(index, hashes, identity) for identity in roots}
    inputs = {'source_capture_sha256': source_sha, 'official_sha256': network_sha256,
              'previous_provider_capture_sha256': old_provider_sha,
              'refreshed_provider_capture_sha256': new_provider_sha,
              'prior_catalogue_summary_sha256': hashlib.sha256((prior_catalogues / 'summary.json').read_bytes()).hexdigest()}
    output.mkdir(exist_ok=True, parents=True)
    (output / 'summary.json').unlink(missing_ok=True)
    rows = []
    for key in keys:
        identities = leads[key]['candidate_relation_ids']
        if not any(uk_metadata_anchor(profiles[i]['tags']) for i in identities):
            raise ValueError('Refreshed source loses its explicit UK metadata anchor')
        folder = prior_plans / key.replace(':', '-')
        manifest = json.loads((folder / 'manifest.json').read_bytes())
        raw = gzip.decompress((folder / 'plan.json.gz').read_bytes())
        assoc = gzip.decompress((folder / 'associations.json.gz').read_bytes())
        _, _, previous_plan = read_completion(raw, assoc, manifest['plan_sha256'], manifest['association_sha256'])
        if (previous_plan['route_key'] != key or manifest['reference_version'] != previous_plan['reference_version']
                or previous_plan['metadata']['source_snapshot_sha256'] != digest(sorted(hashes))
                or previous_plan['metadata']['national_reference_sha256'] != network_sha256):
            raise ValueError('Prior inventory source identity differs from review inputs')
        origin = previous_plan['metadata']['projection_origin']
        relations = [review_relation(key, previous_profiles[i], profiles[i], old_providers[i],
                                     new_providers[i], index, origin) for i in identities]
        result, audit = build_candidate(key, identities, profiles, index, hashes, network, network_sha256, new_providers)
        paths = ()
        sections = []
        if result:
            bundle, payload, _ = result
            sections = payload['sections']
            # Read the candidate through its public consumer before rebasing the
            # projection onto the fixed inventory's existing metric basis.
            from .association_bundle import read_bundle
            from .coverage import TrialReference, Section
            from .journey_replay import projector
            project = projector(payload['metadata'])
            reference = TrialReference(payload['reference_version'], [Section(s['key'],
                LineString([project(c) for c in s['coordinates']]), tuple(s['routes'])) for s in sections])
            paths = read_bundle(bundle, reference, payload['metadata']['projection_origin'], digest(json.loads(bundle)))
            metadata = payload['metadata']
            provenance = json.loads(bundle)['provenance']
        else:
            metadata = previous_plan['metadata']
            provenance = json.loads(assoc)['provenance']
        proof = {'route_key': key, 'inputs': inputs, 'relations': relations,
                 'prior_plan_sha256': manifest['plan_sha256']}
        metadata = {**metadata, 'projection_origin': origin, 'membership_review_sha256': digest(proof)}
        ledger, paths, plan = build_plan(key, inventory_ways(index, identities), sections, paths, metadata)
        for field in ('source_inventory_length_m', 'canonical_inventory_length_m'):
            if abs(plan[field] - previous_plan[field]) > 1e-5:
                raise ValueError('Membership review changed the fixed inventory denominator')
        bundle = build_bundle(paths, ledger.reference, origin, provenance)
        plan_raw = json.dumps(plan, separators=(',', ':')).encode()
        plan_sha = hashlib.sha256(plan_raw).hexdigest()
        bundle_sha = hashlib.sha256(bundle).hexdigest()
        read_completion(plan_raw, bundle, plan_sha, bundle_sha)
        scope, exceptions = review_scope(plan, json.loads(bundle), network)
        row = {**proof, 'membership_hold_resolved': True, 'catalogue_audit': audit,
               'source_inventory_length_m': plan['source_inventory_length_m'],
               'canonical_inventory_length_m': plan['canonical_inventory_length_m'],
               'previous_unmapped_inventory_m': previous_plan['unmapped_inventory_m'],
               'unmapped_inventory_m': plan['unmapped_inventory_m'],
               'maximum_supported_coverage_m': plan['maximum_supported_coverage_m'],
               'whole_published_route_ready': False}
        out = output / key.replace(':', '-')
        out.mkdir(exist_ok=True)
        for name, value in [('plan.json.gz', plan_raw), ('associations.json.gz', bundle),
                            ('review.json.gz', json.dumps(row, separators=(',', ':')).encode()),
                            ('scope.json.gz', json.dumps(scope, separators=(',', ':')).encode()),
                            ('exceptions.geojson.gz', json.dumps(exceptions, separators=(',', ':')).encode())]:
            (out / name).write_bytes(gzip.compress(value, mtime=0))
        (out / 'manifest.json').write_text(json.dumps({'schema_version': 1,
            'reference_version': plan['reference_version'], 'plan_sha256': plan_sha,
            'association_sha256': bundle_sha, 'replaces_plan_sha256': manifest['plan_sha256'],
            'membership_review_sha256': digest(proof), 'whole_published_route_ready': False}, indent=2) + '\n')
        read_reviewed_overlay(out, raw, hashlib.sha256((out / 'manifest.json').read_bytes()).hexdigest())
        rows.append(row)
        print(key, 'membership hold resolved;', audit['accepted_way_count'], 'supported ways;',
              round(plan['unmapped_inventory_m'], 2), 'unsupported metres retained', flush=True)
    summary = {'schema_version': 1, 'scope': 'Reviewed membership baseline overlay', 'inputs': inputs,
               'routes': rows, 'reconciled_keys': keys, 'whole_published_route_ready': False,
               'old_journey_evidence_transferred': False}
    temporary = output / 'summary.json.tmp'
    temporary.write_text(json.dumps(summary, indent=2) + '\n')
    temporary.replace(output / 'summary.json')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('research-root', 'sources', 'prior-catalogues', 'prior-plans',
                 'refreshed-provider', 'network', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--expected-network-sha256', required=True)
    parser.add_argument('--routes', nargs='+', required=True)
    args = parser.parse_args()
    review_batch(args.research_root, args.sources, args.prior_catalogues, args.prior_plans,
                 args.refreshed_provider, args.network, args.expected_network_sha256,
                 args.routes, args.output)


if __name__ == '__main__':
    main()
