"""Fixed full-source-inventory denominators with explicit roundabout alternatives.

Unmapped source mileage remains in the denominator. These totals describe the
captured source inventory, not a certification of the entire promoted route.
"""
from dataclasses import replace
import gzip
import hashlib
import json
import math
from pathlib import Path
from shapely.geometry import LineString
from matched_path_evidence import digest
from .alternatives import LogicalSection,AlternativeSpan,AlternativeLedger
from .association_bundle import build_bundle,read_bundle,bundle_metrics
from .catalogue_extract import _source_index,_route_members
from .coverage import Section,TrialReference,merge_intervals
from .journey_replay import projector
from .roundabout_policy import review_roundabouts


def build_plan(key,ways,eligible_sections,paths,metadata):
    scope=key+':source-inventory'
    metadata={**metadata,'scope':scope,'inventory_basis':'all_current_member_source_ways','whole_published_route_ready':False}
    project=projector(metadata)
    coordinates={wid:[[n['lon'],n['lat']] for n in w['node_records']] for wid,w in ways.items()}
    sections=[{'key':'inventory:'+str(wid),'coordinates':coords,'routes':[scope]} for wid,coords in sorted(coordinates.items())]
    sections.extend({**s,'routes':[scope]} for s in eligible_sections)
    if len({s['key'] for s in sections})!=len(sections):raise ValueError('Distinct inventory/eligible section keys required')
    ref=TrialReference('inventory-building',[Section(s['key'],LineString([project(c) for c in s['coordinates']]),(scope,)) for s in sections])
    review=review_roundabouts(ways);edge_keys={e:'edge:'+digest(e) for e in ref.edges}
    ring_edges=set();ring_groups=[];logical=[];bindings=[]
    cumulative={}
    for wid,coords in coordinates.items():
        values=[0.0]
        for a,b in zip(coords,coords[1:]):values.append(values[-1]+LineString([project(a),project(b)]).length)
        cumulative[wid]=values
    selected_by_way={}
    for path in paths:
        selected_by_way[int(path.way_id)]=path
    for group in review['reviewed']:
        if group['policy']!='either_two_gate_arc':continue
        identities={identity for wid in group['way_ids'] for identity,_,_ in ref.mapping['inventory:'+str(wid)]}
        outside={identity for wid in ways if wid not in group['way_ids'] for identity,_,_ in ref.mapping['inventory:'+str(wid)]}
        if identities & outside:continue  # Never replace another source road's length.
        lengths=[sum(cumulative[s['way_id']][s['edge_index']+1]-cumulative[s['way_id']][s['edge_index']] for s in branch) for branch in group['branches']]
        if min(lengths)<=1e-7:continue
        length=min(lengths);group_key='roundabout:'+digest(group['way_ids'])
        logical.append(LogicalSection(group_key,length,(scope,)));ring_edges.update(identities)
        ring_groups.append({**group,'logical_key':group_key,'canonical_length_m':length,'branch_lengths_m':lengths})
        for branch,total in zip(group['branches'],lengths):
            cursor=0.0
            for step in branch:
                wid=step['way_id'];i=step['edge_index'];amount=cumulative[wid][i+1]-cumulative[wid][i]
                a,z=cursor/total*length,(cursor+amount)/total*length;cursor+=amount
                if amount<=1e-7:continue
                path=selected_by_way.get(wid)
                if path is None:continue
                for selected in eligible_sections:
                    if selected['key'] not in path.section_keys:continue
                    lo=selected['from_vertex'];hi=selected['to_vertex']
                    if not lo<=i<hi:continue
                    first=cumulative[wid][i]-cumulative[wid][lo];last=cumulative[wid][i+1]-cumulative[wid][lo]
                    start,end=(a,z) if step['forward'] else (z,a)
                    # Floating-point normalization only; no extra travel extent.
                    segment_length=ref.sections[selected['key']].line.length
                    bindings.append(AlternativeSpan(selected['key'],max(0,min(segment_length,first)),
                        max(0,min(segment_length,last)),group_key,max(0,min(length,start)),max(0,min(length,end)),path.modes))
    logical.extend(LogicalSection(edge_keys[e],edge['line'].length,(scope,)) for e,edge in ref.edges.items() if e not in ring_edges)
    for path in paths:
        for selected in path.section_keys:
            for identity,first,last in ref.mapping[selected]:
                if identity in ring_edges:continue
                length=ref.edges[identity]['line'].length
                limit=ref.sections[selected].line.length
                first,last=(max(0,min(limit,v)) for v in (first,last))
                bindings.append(AlternativeSpan(selected,min(first,last),max(first,last),edge_keys[identity],
                    0 if first<last else length,length if first<last else 0,path.modes))
    # Logical sections with no eligible binding are intentionally retained.
    payload={'schema_version':1,'route_key':key,'scope':scope,'metadata':metadata,'sections':sections,
        'logical_sections':[{'key':s.key,'length_m':s.length_m,'routes':s.routes} for s in logical],
        'bindings':[dict(physical_key=b.physical_key,physical_start=b.physical_start,physical_end=b.physical_end,
            logical_key=b.logical_key,logical_start=b.logical_start,logical_end=b.logical_end,modes=b.modes) for b in bindings],
        'roundabout_groups':ring_groups,'source_inventory_length_m':sum(e['line'].length for e in ref.edges.values()),
        'canonical_inventory_length_m':sum(s.length_m for s in logical),
        'whole_published_route_ready':False,'unmapped_length_remains_in_denominator':True}
    capacity={}
    for mode in ('on_foot','cycling','combined'):
        intervals={}
        for b in bindings:
            if mode!='combined' and mode not in b.modes:continue
            intervals.setdefault(b.logical_key,[]).append((b.logical_start,b.logical_end))
        capacity[mode]=sum(z-a for spans in intervals.values() for a,z in merge_intervals(spans))
    payload['maximum_supported_coverage_m']=capacity
    payload['unmapped_inventory_m']=max(0,payload['canonical_inventory_length_m']-capacity['combined'])
    payload['source_inventory_complete']=True
    version='inventory-completion:'+digest(payload);payload['reference_version']=version
    ref=TrialReference(version,[Section(s['key'],LineString([project(c) for c in s['coordinates']]),(scope,)) for s in sections])
    # Inventory ledgers retain unmapped targets; ordinary AlternativeLedger
    # construction still requires a binding for every logical section.
    ledger=InventoryLedger(ref,logical,bindings)
    return ledger,tuple(replace(p,reference_version=version) for p in paths),payload


class InventoryLedger(AlternativeLedger):
    def __init__(self,reference,logical_sections,bindings):
        active={b.logical_key for b in bindings}
        used=[s for s in logical_sections if s.key in active]
        if used:super().__init__(reference,used,bindings)
        else:
            self.reference=reference;self.activities={};self.logical={};self.bindings=()
        # Missing bindings mean zero progress, never removal from the totals.
        if len({s.key for s in logical_sections})!=len(logical_sections):raise ValueError('Duplicate inventory section')
        if any(not math.isfinite(s.length_m) or s.length_m<=0 or not s.routes for s in logical_sections):raise ValueError('Positive inventory length required')
        self.logical={s.key:s for s in logical_sections}


def read_completion(raw,associations,expected_plan_sha256,expected_association_sha256):
    """Read a portable inventory plan against independently pinned byte hashes."""
    if hashlib.sha256(raw).hexdigest()!=expected_plan_sha256 or hashlib.sha256(associations).hexdigest()!=expected_association_sha256:
        raise ValueError('Completion download checksum differs')
    plan=json.loads(raw);unsigned={k:v for k,v in plan.items() if k!='reference_version'}
    if plan.get('schema_version')!=1 or plan['reference_version']!='inventory-completion:'+digest(unsigned):
        raise ValueError('Completion reference reconciliation required')
    if plan.get('whole_published_route_ready') is not False or not plan['scope'].endswith(':source-inventory'):
        raise ValueError('Source inventory cannot claim a complete published route')
    project=projector(plan['metadata'])
    reference=TrialReference(plan['reference_version'],[Section(s['key'],LineString([project(c) for c in s['coordinates']]),tuple(s['routes'])) for s in plan['sections']])
    logical=[LogicalSection(s['key'],s['length_m'],tuple(s['routes'])) for s in plan['logical_sections']]
    if abs(sum(s.length_m for s in logical)-plan['canonical_inventory_length_m'])>1e-5:
        raise ValueError('Completion denominator differs from plan')
    bindings=[AlternativeSpan(**b) for b in plan['bindings']]
    ledger=InventoryLedger(reference,logical,bindings)
    paths=read_bundle(associations,reference,plan['metadata']['projection_origin'],digest(json.loads(associations)))
    return ledger,paths,plan


def main():
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--research-root',type=Path,required=True);parser.add_argument('--catalogues',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);a=parser.parse_args();root=a.research_root
    fetch=json.loads((root/'confirmed-sources/fetch-manifest.json').read_bytes());raws=[]
    for record in fetch['sources']:
        raw=gzip.decompress((root/'confirmed-sources'/record['file']).read_bytes())
        if hashlib.sha256(raw).hexdigest()!=record['sha256']:raise ValueError('Source snapshot changed')
        raws.append(raw)
    index,hashes=_source_index(raws)
    leads={r['official_route_key']:r for r in json.loads((root/'physical-national/summary.json').read_bytes())['routes']}
    audits=json.loads((a.catalogues/'summary.json').read_bytes());rows=[];a.output.mkdir(exist_ok=True,parents=True)
    for audit in audits['routes']:
        key=audit['route_key'];source_ids=set().union(*[_route_members(index,rid)[-1] for rid in leads[key]['candidate_relation_ids']])
        ways={wid:{**index['way',wid],'node_records':[index['node',n] for n in index['way',wid]['nodes']]} for wid in source_ids}
        folder=a.catalogues/key.replace(':','-');paths=();eligible=[]
        if (folder/'reference.json.gz').exists():
            reference=json.loads(gzip.decompress((folder/'reference.json.gz').read_bytes()));meta=reference['metadata'];eligible=reference['sections']
            project=projector(meta);ref=TrialReference(reference['reference_version'],[Section(s['key'],LineString([project(c) for c in s['coordinates']]),tuple(s['routes'])) for s in eligible])
            raw=gzip.decompress((folder/'associations.json.gz').read_bytes());p=json.loads(raw)
            paths=read_bundle(raw,ref,meta['projection_origin'],digest(p));provenance=p['provenance']
        else:
            coords=[(n['lon'],n['lat']) for w in ways.values() for n in w['node_records']]
            meta={'projection_origin':[(min(x for x,y in coords)+max(x for x,y in coords))/2,(min(y for x,y in coords)+max(y for x,y in coords))/2]}
            provenance={'attribution':'OpenStreetMap contributors','licence':'ODbL 1.0','source_snapshot_sha256':digest(hashes),'membership_evidence_sha256':digest(sorted(source_ids))}
        ledger,paths,plan=build_plan(key,ways,eligible,paths,meta)
        raw=build_bundle(paths,ledger.reference,meta['projection_origin'],provenance)
        out=a.output/key.replace(':','-');out.mkdir(exist_ok=True)
        plan_raw=json.dumps(plan,separators=(',',':')).encode()
        read_completion(plan_raw,raw,hashlib.sha256(plan_raw).hexdigest(),hashlib.sha256(raw).hexdigest())
        (out/'plan.json.gz').write_bytes(gzip.compress(plan_raw,mtime=0))
        (out/'associations.json.gz').write_bytes(gzip.compress(raw,mtime=0))
        (out/'manifest.json').write_text(json.dumps({'reference_version':plan['reference_version'],
            'plan_sha256':hashlib.sha256(plan_raw).hexdigest(),'association_sha256':hashlib.sha256(raw).hexdigest(),
            'whole_published_route_ready':False},indent=2)+'\n')
        rows.append({'route_key':key,'source_way_count':len(ways),'bound_way_count':len(paths),
            'source_inventory_length_m':plan['source_inventory_length_m'],'canonical_inventory_length_m':plan['canonical_inventory_length_m'],
            'roundabout_group_count':len(plan['roundabout_groups']),'unmapped_inventory_m':plan['unmapped_inventory_m'],
            'maximum_supported_coverage_m':plan['maximum_supported_coverage_m'],'whole_published_route_ready':False})
    (a.output/'summary.json').write_text(json.dumps({'routes':rows,'whole_published_route_ready':False},indent=2)+'\n')
    print('plans',len(rows),'two-gate groups',sum(r['roundabout_group_count'] for r in rows),flush=True)


if __name__=='__main__':main()
