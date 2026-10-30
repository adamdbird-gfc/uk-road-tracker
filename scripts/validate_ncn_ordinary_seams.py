"""Offline independent seam candidate assessment from immutable admitted inputs.

No journey matching, source admission, deployment or publication. Outputs are
synthetic capacity receipts and a reproducible patch against the shipped DB.
"""
import argparse
from collections import defaultdict
from dataclasses import asdict
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
from types import SimpleNamespace

from shapely.geometry import LineString
from shapely.ops import unary_union
from scripts.build_ncn_national import compile_path_bindings, PINS
from scripts.ncn_binding_attainability import (BASELINE_SHA, CHECKPOINT_SHA, encoded, digest,
    content_sha, apply_patch, read_targets, route_capacity, target_intervals, network_intervals, missing)
from scripts.ncn_reference import sha_file, verify_database
from scripts.ncn_trial.completion_checkpoint import pinned, decode, check_registry, load_source, open_extent
from scripts.ncn_trial.open_completion import build_verified_completion, POLICY, SEAM_POLICY
from scripts.ncn_trial.coverage import merge_intervals

IMPLEMENTATION = 'c0566c6421a2d2b155f738e0411a526143bc385c'
INTEGRATED_CONTENT = '936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a'


def measure_network(db, coverage):
    lengths=dict(db.execute('SELECT id,length FROM network'))
    out={'target_m':sum(lengths.values())}
    for mode, values in coverage.items():
        if set(values)-set(lengths):raise ValueError('Unknown physical network edge')
        for edge,spans in values.items():
            if any(min(a,z)<-1e-6 or max(a,z)>lengths[edge]+1e-6 for a,z in spans):
                raise ValueError('Out-of-bounds physical network mapping')
        out[mode+'_m']=sum(z-a for spans in values.values() for a,z in merge_intervals(spans))
    out['unbound_m']=out['target_m']-out['combined_m']
    out['attainable']=out['unbound_m']<=max(1e-7,out['target_m']*1e-12)
    out['combined_percent']=100*out['combined_m']/out['target_m']
    return out


def require_preserved(old, new):
    for key,spans in old.items():
        for a,z in spans:
            if sum(max(0,min(z,y)-max(a,x)) for x,y in new.get(key,[])) < z-a-1e-6:
                raise ValueError('Previously supported target interval lost: '+key)


def assess(checkpoint, shipped, output):
    checkpoint,shipped,output=map(Path,(checkpoint,shipped,output))
    if output.exists() and {p.name for p in output.iterdir() if not p.name.startswith('.integrated.db.')}!={'integrated.db'}:raise ValueError('Fresh output directory required')
    manifest=json.loads(Path('android-prototype/app/src/main/assets/ncn-national/manifest.json').read_text())
    verify_database(shipped,manifest)
    raw=(checkpoint/'inputs/input-pins.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=PINS:raise ValueError('Input pins changed')
    pins=json.loads(raw); files={r['file']:pinned(checkpoint/'inputs',r) for r in pins['files']}
    if len(files)!=len(pins['files']):raise ValueError('Duplicate input file')
    check_registry(checkpoint/'inputs',pins,files)
    summary_raw=(checkpoint/'plans/summary.json').read_bytes()
    if hashlib.sha256(summary_raw).hexdigest()!=CHECKPOINT_SHA:raise ValueError('Checkpoint changed')
    summary=json.loads(summary_raw); rows={r['route_key']:r for r in pins['routes']}
    network_raw=decode(files[pins['official_network']],pins['official_network'])
    if hashlib.sha256(network_raw).hexdigest()!=pins['official_network_sha256']:raise ValueError('Official target pin changed')
    official=json.loads(network_raw)
    output.mkdir(parents=True,exist_ok=True)
    prior=json.loads(gzip.decompress(Path('.github/ncn-reference/stage1-binding-correction.json.gz').read_bytes()))
    integrated=output/'integrated.db'
    if not integrated.exists():apply_patch(shipped,prior,integrated)
    if content_sha(integrated)!=INTEGRATED_CONTENT:raise ValueError('Integrated reference differs')
    db=sqlite3.connect(integrated.resolve().as_uri()+'?mode=ro',uri=True)
    base=sqlite3.connect(shipped.resolve().as_uri()+'?mode=ro',uri=True)
    patch={'schema_version':1,'kind':'exact-source-offsets-and-ordinary-seams','baseline_sha256':BASELINE_SHA,
           'checkpoint_sha256':CHECKPOINT_SHA,'input_pins_sha256':PINS,'implementation_commit':IMPLEMENTATION,'updates':[]}
    networks=[{m:defaultdict(list) for m in ('on_foot','cycling','combined')} for _ in range(2)]
    results=[]; added_count=0; changed_rows=0; replayed=0; deferred_routes=0
    witnesses=json.loads(Path('tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json').read_text())
    witness_checks=[]
    for record in summary['routes']:
        route=record['route_key']; folder=checkpoint/'plans'/route.replace(':','-')
        plan_raw=gzip.decompress((folder/'plan.json.gz').read_bytes())
        ass_raw=gzip.decompress((folder/'associations.json.gz').read_bytes())
        if hashlib.sha256(plan_raw).hexdigest()!=record['plan_sha256'] or hashlib.sha256(ass_raw).hexdigest()!=record['association_sha256']:
            raise ValueError('Route receipt changed')
        old_plan=json.loads(plan_raw); associations=json.loads(ass_raw)
        if record['completion_kind']!='captured_open_main':
            continue  # Preserved NCN228 alternative is not changed or counted.
        loaded=load_source(checkpoint/'inputs',rows[route],files)
        extent=loaded['extent'] or open_extent(route,official['features'],loaded['metadata'],pins['official_network_sha256'])
        args=(loaded['reference'],loaded['metadata'],loaded['paths'],extent,
              old_plan['catalogue_manifest_sha256'],loaded['provenance'],loaded['deferred_way_ids'])
        legacy,legacy_bundle,legacy_plan=build_verified_completion(*args)
        if encoded(legacy_plan)!=encoded(old_plan):raise ValueError('Disabled policy no longer reproduces retained plan: '+route)
        replayed+=1
        ledger,bundle,new_plan=build_verified_completion(*args,ordinary_seams=True)
        if new_plan['policy']!=SEAM_POLICY or POLICY['maximum_offset_m']!=10 or POLICY['maximum_angle_degrees']!=15:
            raise ValueError('Distance/direction policy changed')
        for field in ('physical_sections_metric','logical_sections','canonical_open_length_m','projection_origin','catalogue_manifest_sha256'):
            if encoded(new_plan[field])!=encoded(old_plan[field]):raise ValueError('Identity/geometry/denominator changed')
        if json.loads(bundle)['paths']!=associations['paths']:raise ValueError('Source/provenance/modes changed')
        oldset={encoded(b) for b in old_plan['bindings']}; newset={encoded(b) for b in new_plan['bindings']}
        if not oldset<=newset:raise ValueError('Old binding modified or lost')
        additions=[b for b in ledger.bindings if encoded(asdict(b)) not in oldset]
        # InventoryLedger has independently enforced finite bounds, valid modes
        # and non-overlapping physical ownership at construction.
        added_count+=len(additions)
        blocked={k for p in loaded['paths'] if str(p.way_id) in set(map(str,loaded['deferred_way_ids'])) for k in p.section_keys}
        if blocked:
            deferred_routes+=1
            corridor=unary_union([loaded['reference'].sections[k].line for k in blocked]).buffer(10)
            project=lambda c:(6371008.8*__import__('math').radians(c[0]-new_plan['projection_origin'][0])*__import__('math').cos(__import__('math').radians(new_plan['projection_origin'][1])),6371008.8*__import__('math').radians(c[1]-new_plan['projection_origin'][1]))
            # Logical target endpoints are independently recovered from immutable extent.
            lines=[]
            for f in extent['features']:
                g=f['geometry'];parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
                lines.extend(LineString([project(c) for c in part]) for part in parts)
            union=unary_union(lines);parts=[union] if union.geom_type=='LineString' else union.geoms
            blocked_targets=set()
            from matched_path_evidence import digest as identity_digest
            for part in parts:
                for a,z in zip(part.coords,list(part.coords)[1:]):
                    a,z=sorted((tuple(a),tuple(z)))
                    if LineString([a,z]).length>1e-7 and LineString([a,z]).intersects(corridor):
                        blocked_targets.add('open-edge:'+identity_digest((a,z)))
            if any(b.physical_key in blocked or b.logical_key in blocked_targets for b in ledger.bindings):
                raise ValueError('Deferred mask violated')
        for witness in witnesses['ordinary_candidates']+[witnesses['genuine_hairpin_negative']]:
            if witness['route_key']!=route:continue
            path=next(p for p in associations['paths'] if str(p['way_id'])==witness['source_way_id'])
            if path['source_geometry_sha256']!=witness['source_geometry_sha256']:raise ValueError('Full source hash differs')
            section=loaded['reference'].sections[witness['source_section_key']]
            pairs=list(zip(section.line.coords,list(section.line.coords)[1:]))
            edge=witness['source_edge_metric']
            if not any(list(a)==edge[0] and list(z)==edge[1] for a,z in pairs):raise ValueError('Witness absent from complete source section')
            witness_checks.append({'route':route,'section':section.key,'complete_source_hash_verified':True,'source_edge_verified':True})
        sections={s['key']:s for s in new_plan['physical_sections_metric']}
        indexes=[]
        for plan in (old_plan,new_plan):
            idx=defaultdict(list)
            for b in plan['bindings']:idx[b['physical_key']].append(b)
            indexes.append(idx)
        before=[];after=[]; corrected=0
        for path in associations['paths']:
            way=str(path['way_id'])
            old=json.loads(gzip.decompress(db.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,way)).fetchone()[0]))
            if old['coordinates']!=path['coordinates'] or old['nodes']!=path['osm_node_ids'] or set(old['modes'])!=set(path['modes']) or old['source_geometry_sha256']!=path['source_geometry_sha256']:
                raise ValueError('Complete source identity differs from integrated catalogue')
            expected=compile_path_bindings(SimpleNamespace(**path),old_plan,False,sections=sections,bindex=indexes[0])
            if encoded(expected)!=encoded(old['bindings']):raise ValueError('Integrated full-way offset baseline differs')
            compiled=compile_path_bindings(SimpleNamespace(**path),new_plan,False,sections=sections,bindex=indexes[1])
            new=dict(old,bindings=compiled);before.append(old);after.append(new)
            if encoded(compiled)!=encoded(old['bindings']):corrected+=1;changed_rows+=1
            shipped_path=json.loads(gzip.decompress(base.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,way)).fetchone()[0]))
            if encoded(compiled)!=encoded(shipped_path['bindings']):
                patch['updates'].append({'route':route,'way':way,'previous_bindings_sha256':digest(shipped_path['bindings']),'bindings':compiled})
        targets=read_targets(db,route); capacities=[route_capacity(paths,targets) for paths in (before,after)]
        for mode in networks[0]:
            prior_spans=target_intervals(before,mode);new_spans=target_intervals(after,mode)
            require_preserved(prior_spans,new_spans)
            network_intervals(targets,prior_spans,networks[0][mode]);network_intervals(targets,new_spans,networks[1][mode])
        results.append({'route_key':route,'before':capacities[0],'after':capacities[1],
                        'recovery_m':capacities[1]['combined_m']-capacities[0]['combined_m'],'changed_source_rows':corrected,'added_bindings':len(additions)})
        print(route,'gain',round(results[-1]['recovery_m'],6),'m',flush=True)
    net_before,net_after=[measure_network(db,n) for n in networks]
    db.close();base.close()
    candidate=apply_patch(shipped,patch,output/'ncn-national.db')
    patch.update(candidate_sha256=sha_file(candidate),candidate_content_sha256=content_sha(candidate),sqlite_writer_version=sqlite3.sqlite_version)
    (output/'binding-correction.json.gz').write_bytes(gzip.compress(encoded(patch),mtime=0))
    (output/'manifest.json').write_bytes(encoded(dict(manifest,sha256=patch['candidate_sha256'],revision=manifest['revision']+2))+b'\n')
    from scripts.finalize_ncn_android import pack
    pack(output)
    candidate_manifest=json.loads((output/'manifest.json').read_text());verify_database(candidate,candidate_manifest)
    report={'schema_version':1,'status':'candidate_assessed_not_published','implementation_commit':IMPLEMENTATION,
            'baseline_sha256':BASELINE_SHA,'integrated_content_sha256':INTEGRATED_CONTENT,
            'candidate_sha256':patch['candidate_sha256'],'candidate_content_sha256':patch['candidate_content_sha256'],
            'candidate_manifest':candidate_manifest,'checkpoint_sha256':CHECKPOINT_SHA,'input_pins_sha256':PINS,
            'policy':SEAM_POLICY,'measurable_routes':len(results),'affected_routes':sum(r['recovery_m']>1e-7 for r in results),
            'attainable_routes':sum(r['after']['attainable'] for r in results),
            'newly_attainable_routes':[r['route_key'] for r in results if r['after']['attainable'] and not r['before']['attainable']],
            'before_network':net_before,'after_network':net_after,
            'route_local_recovery_m':sum(r['recovery_m'] for r in results),'unique_network_recovery_m':net_after['combined_m']-net_before['combined_m'],
            'route_local_remaining_deficit_m':sum(r['after']['unbound_m'] for r in results),
            'legacy_plans_reproduced':replayed,'deferred_routes_checked':deferred_routes,
            'added_bindings':added_count,'changed_source_rows':changed_rows,'patch_source_rows':len(patch['updates']),
            'complete_witness_checks':witness_checks,'geometry_identity_and_mode_changes':0,
            'previous_supported_intervals_preserved':True,'source_interval_nonoverlap_checked':True,
            'deferred_mask_violations':0,'capacity_is_synthetic_not_personal_travel':True,
            'correction_artifact_sha256':sha_file(output/'binding-correction.json.gz'),'routes':results}
    (output/'assessment.json').write_text(json.dumps(report,indent=2)+'\n')
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=assess(a.checkpoint,a.database,a.output)
    print(json.dumps({k:v for k,v in r.items() if k not in ('routes','candidate_manifest')},indent=2))
