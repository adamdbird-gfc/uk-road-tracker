"""Read-only mechanism classification of retained NCN target gaps.

Never creates bindings, writes the input DB, changes POLICY, or publishes data.
An unadmitted known-way span is a review lead, not approved source evidence.
"""
import argparse
from collections import defaultdict, Counter
import gzip
import hashlib
import json
import math
from pathlib import Path
from types import SimpleNamespace
import sqlite3

from shapely.geometry import LineString, Point
from shapely.ops import unary_union
from shapely.strtree import STRtree
from matched_path_evidence import digest
from scripts.ncn_trial.coverage import Section, TrialReference, merge_intervals
from scripts.ncn_trial.open_completion import bind_edges, edge_interval, POLICY
from scripts.ncn_trial.completion_checkpoint import open_extent
from scripts.ncn_binding_attainability import (PINS, CHECKPOINT_SHA, content_sha,
    read_targets, target_intervals, route_capacity, missing, network_intervals, encoded)

CAUSES = ('deferred_binding_withheld','ambiguous_target_correspondence',
    'unadmitted_span_of_known_way','direction_gate','finite_endpoint_or_alignment',
    'no_admitted_source_correspondence')
EXPECTED_CONTENT='936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a'


def partition(gaps, layers):
    """Partition each exact gap, preserving every positive length and priority."""
    out=[]
    for low,high in gaps:
        cuts=sorted({low,high,*[max(low,min(high,x)) for spans in layers.values() for span in spans for x in span]})
        for a,z in zip(cuts,cuts[1:]):
            if z<=a:continue
            mid=(a+z)/2
            cause=next((c for c in CAUSES[:-1] if any(x<=mid<=y for x,y in layers.get(c,[]))),CAUSES[-1])
            if out and out[-1][2]==cause and out[-1][1]==a:out[-1]=(out[-1][0],z,cause)
            else:out.append((a,z,cause))
    return out


def interval_without_direction(first,last,target):
    """Diagnostic finite correspondence with ONLY the angle test omitted.

    The production 15-degree gate is unchanged. Crossing matches here are
    rejection explanations, never proposed evidence or completion credit.
    """
    px,py=first;dx,dy=last[0]-px,last[1]-py;length=math.hypot(dx,dy)
    q,z=list(target.coords);ex,ey=z[0]-q[0],z[1]-q[1];total=math.hypot(ex,ey)
    if min(length,total)<=1e-7:return None
    dx,dy,ex,ey=dx/length,dy/length,ex/total,ey/total;slope=dx*ex+dy*ey
    if abs(slope)<=1e-12:return None
    origin=(px-q[0])*ex+(py-q[1])*ey
    lo,hi=max(0.,min(-origin/slope,(total-origin)/slope)),min(length,max(-origin/slope,(total-origin)/slope))
    cross=(px-q[0])*ey-(py-q[1])*ex;cross_slope=dx*ey-dy*ex;limit=POLICY['maximum_offset_m']
    if abs(cross_slope)<=1e-12:
        if abs(cross)>limit:return None
    else:
        a,b=sorted(((-limit-cross)/cross_slope,(limit-cross)/cross_slope));lo,hi=max(lo,a),min(hi,b)
    if hi-lo<=1e-7:return None
    return lo,hi,max(0.,min(total,origin+lo*slope)),max(0.,min(total,origin+hi*slope))


def geometry_spans(line, geometry):
    if geometry.is_empty:return []
    if geometry.geom_type=='LineString':
        coords=list(geometry.coords)
        return [(min(line.project(Point(c)) for c in coords),
                 max(line.project(Point(c)) for c in coords))]
    if hasattr(geometry,'geoms'):
        return [v for g in geometry.geoms for v in geometry_spans(line,g)]
    return []  # An isolated point has no interval length.


def metric_targets(extent,project):
    lines=[]
    for f in extent['features']:
        g=f['geometry'];parts=[g['coordinates']] if g['type']=='LineString' else g['coordinates']
        lines.extend(LineString([project(c)for c in p]) for p in parts)
    union=unary_union(lines);parts=[union] if union.geom_type=='LineString' else union.geoms
    result={}
    for part in parts:
        for a,z in zip(part.coords,list(part.coords)[1:]):
            a,z=sorted((tuple(a),tuple(z)))
            if math.dist(a,z)>1e-7:result['open-edge:'+digest((a,z))]=LineString([a,z])
    return result


def add_candidates(lines,targets,tree,directionless=False):
    normal=defaultdict(list);without=defaultdict(list)
    for line in lines:
        for a,z in zip(line.coords,list(line.coords)[1:]):
            segment=LineString([a,z])
            if segment.length<=1e-7:continue
            for i in tree.query(segment.buffer(POLICY['maximum_offset_m'])):
                key,target=targets[int(i)];v=edge_interval(a,z,target)
                if v:normal[key].append((v[2],v[3]))
                elif directionless:
                    v=interval_without_direction(a,z,target)
                    if v:without[key].append((v[2],v[3]))
    return ({k:merge_intervals(v) for k,v in normal.items()},
            {k:merge_intervals(v) for k,v in without.items()})


def run(checkpoint,database,output,integration):
    checkpoint,output=Path(checkpoint),Path(output)
    if output.exists():raise ValueError('Fresh output required')
    actual=content_sha(database)
    if actual!=EXPECTED_CONTENT:raise ValueError('Integrated candidate reference values differ')
    pinraw=(checkpoint/'inputs/input-pins.json').read_bytes();summaryraw=(checkpoint/'plans/summary.json').read_bytes()
    if hashlib.sha256(pinraw).hexdigest()!=PINS or hashlib.sha256(summaryraw).hexdigest()!=CHECKPOINT_SHA:raise ValueError('Checkpoint pins differ')
    pins=json.loads(pinraw);summary=json.loads(summaryraw);pinfiles={r['file']:r for r in pins['files']};rows={r['route_key']:r for r in pins['routes']}
    def pinned_file(name):
        raw=(checkpoint/'inputs'/name).read_bytes()
        if hashlib.sha256(raw).hexdigest()!=pinfiles[name]['sha256']:raise ValueError('Input pin differs: '+name)
        return raw
    network_raw=gzip.decompress(pinned_file(pins['official_network']))
    if hashlib.sha256(network_raw).hexdigest()!=pins['official_network_sha256']:raise ValueError('National snapshot differs')
    official=json.loads(network_raw);output.mkdir(parents=True)
    totals={c:dict(intervals=0,metres=0.,routes=set()) for c in CAUSES};examples={c:[] for c in CAUSES}
    route_results=[];network_done=defaultdict(list);network_causes={c:defaultdict(list) for c in CAUSES};interval_count=0;structural_errors=0
    db=sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True)
    with gzip.GzipFile(filename=str(output/'classified-intervals.jsonl.gz'),mode='wb',mtime=0) as inventory:
        for record in summary['routes']:
            if record['completion_kind']!='captured_open_main':continue
            route=record['route_key'];folder=checkpoint/'plans'/route.replace(':','-')
            planraw=gzip.decompress((folder/'plan.json.gz').read_bytes());assocraw=gzip.decompress((folder/'associations.json.gz').read_bytes())
            if hashlib.sha256(planraw).hexdigest()!=record['plan_sha256'] or hashlib.sha256(assocraw).hexdigest()!=record['association_sha256']:raise ValueError('Plan pin differs')
            plan=json.loads(planraw);association=json.loads(assocraw);origin=plan['projection_origin']
            project=lambda c:(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))
            extent_name=rows[route]['folder']+'/open-extent.json'
            extent=json.loads(pinned_file(extent_name)) if extent_name in pinfiles else open_extent(route,official['features'],{'national_reference_sha256':pins['official_network_sha256'],'projection_origin':origin},pins['official_network_sha256'])
            target_lines=metric_targets(extent,project);targets=read_targets(db,route);bytarget={t['key']:t for t in targets}
            if set(bytarget)!=set(target_lines):raise ValueError('Target identities differ')
            if any(abs(t['length']-target_lines[t['key']].length)>1e-7 for t in targets):raise ValueError('Target length differs')
            paths=[json.loads(gzip.decompress(raw)) for raw, in db.execute('SELECT data FROM paths WHERE route=? ORDER BY way',(route,))]
            cap=route_capacity(paths,targets);covered=target_intervals(paths,'combined');network_intervals(targets,covered,network_done)
            if abs(cap['combined_m']-plan['maximum_bound_open_length_m']['combined'])>1e-5:raise ValueError('Recalculated capacity differs')
            sections={s['key']:s for s in plan['physical_sections_metric']}
            ref=TrialReference(plan['reference_version'],[Section(k,LineString(s['coordinates']),tuple(s['routes'])) for k,s in sections.items()])
            ordered=sorted(target_lines.items());tree=STRtree([line for _,line in ordered])
            _,raw_bindings,_=bind_edges(ref,[SimpleNamespace(**p) for p in association['paths']],ordered,plan['scope'])
            potential,angle=add_candidates([s.line for s in ref.sections.values()],ordered,tree,True)
            deferred=set(plan.get('deferred_alternative_way_ids',[]));blocked={k for p in association['paths'] if p['way_id'] in deferred for k in p['section_keys']}
            blocked_targets=set()
            if blocked:
                corridor=unary_union([ref.sections[k].line for k in blocked]).buffer(POLICY['maximum_offset_m'])
                blocked_targets={k for k,l in ordered if l.intersects(corridor)}
            withheld=defaultdict(list);replayed=defaultdict(list)
            for b in raw_bindings:
                dest=withheld if b.physical_key in blocked or b.logical_key in blocked_targets else replayed
                dest[b.logical_key].append((b.logical_start,b.logical_end))
            # Prove this classifier replays the actual policy, not an alternate matcher.
            for t in targets:
                first=merge_intervals(replayed.get(t['key'],[]));second=covered.get(t['key'],[])
                sym=abs(sum(z-a for a,z in first)-sum(z-a for a,z in second))
                if len(first)!=len(second) or sym>1e-5 or any(abs(x-y)>1e-7 for a,b in zip(first,second) for x,y in zip(a,b)):
                    raise ValueError('Replayed production bindings differ: '+route+'/'+t['key'])
            withheld={k:merge_intervals(v) for k,v in withheld.items()}
            # Unadmitted portions of already captured full ways are only leads.
            unadmitted=[]
            for p in association['paths']:
                admitted={tuple(sorted((tuple(a),tuple(z)))) for k in p['section_keys'] for a,z in zip(sections[k]['coordinates'],sections[k]['coordinates'][1:])}
                coords=[project(c) for c in p['coordinates']]
                unadmitted.extend(LineString([a,z]) for a,z in zip(coords,coords[1:]) if tuple(sorted((tuple(a),tuple(z)))) not in admitted and a!=z)
            known,_=add_candidates(unadmitted,ordered,tree)
            source_lines=[s.line for s in ref.sections.values()];source_tree=STRtree(source_lines);buffers={}
            counts={c:dict(intervals=0,metres=0.) for c in CAUSES};original_gaps=0;reassembled=0.
            for t in targets:
                key=t['key'];gaps=missing(t['length'],covered.get(key,[]))
                if not gaps:continue
                original_gaps+=len(gaps);line=target_lines[key];near=[]
                for i in source_tree.query(line.buffer(POLICY['maximum_offset_m'])):
                    i=int(i)
                    if i not in buffers:buffers[i]=source_lines[i].buffer(POLICY['maximum_offset_m'])
                    near.extend(geometry_spans(line,line.intersection(buffers[i])))
                layers=dict(deferred_binding_withheld=withheld.get(key,[]),ambiguous_target_correspondence=potential.get(key,[]),
                    unadmitted_span_of_known_way=known.get(key,[]),direction_gate=angle.get(key,[]),finite_endpoint_or_alignment=merge_intervals(near))
                pieces=partition(gaps,layers)
                reassembled+=sum(z-a for a,z,c in pieces)
                for a,z,cause in pieces:
                    counts[cause]['intervals']+=1;counts[cause]['metres']+=z-a
                    totals[cause]['intervals']+=1;totals[cause]['metres']+=z-a;totals[cause]['routes'].add(route);interval_count+=1
                    item=dict(route_key=route,target_key=key,low_m=a,high_m=z,cause=cause,target_coordinates=t['coords'],target_length_m=t['length'])
                    inventory.write(encoded(item)+b'\n')
                    if z-a>.001:
                        examples[cause].append(dict(item,metres=z-a));examples[cause]=sorted(examples[cause],key=lambda x:-x['metres'])[:5]
                    for edge,x,y,length in t['network']:
                        low,high=max(a,min(x,y)),min(z,max(x,y))
                        if high>low:network_causes[cause][edge].append(((low-x)/(y-x)*length,(high-x)/(y-x)*length))
            if abs(reassembled-cap['unbound_m'])>1e-4:raise ValueError('Gap partition lost distance: '+route)
            route_results.append(dict(route_key=route,capacity=cap,original_gap_intervals=original_gaps,causes=counts))
            print(route,round(cap['unbound_m'],3),{c:round(v['metres'],2) for c,v in counts.items() if v['metres']>.01},flush=True)
    lengths=dict(db.execute('SELECT id,length FROM network'));network_total=sum(lengths.values());network_unbound=0.;network_reason_sets=defaultdict(float)
    for edge,length in lengths.items():
        gaps=missing(length,network_done.get(edge,[]));network_unbound+=sum(z-a for a,z in gaps)
        layers={c:merge_intervals(network_causes[c].get(edge,[])) for c in CAUSES}
        for a,z in gaps:
            cuts=sorted({a,z,*[max(a,min(z,x)) for spans in layers.values() for s in spans for x in s]})
            for low,high in zip(cuts,cuts[1:]):
                if high<=low:continue
                mid=(low+high)/2;reasons=tuple(c for c in CAUSES if any(x<=mid<=y for x,y in layers[c]))
                network_reason_sets[' + '.join(reasons) or 'numerical_projection_residual']+=high-low
    db.close()
    result=dict(schema_version=1,integration_commit=integration,method='exact production-policy replay; exclusive target-interval partition; read-only input',
        candidate_content_sha256=actual,checkpoint_sha256=CHECKPOINT_SHA,input_pins_sha256=PINS,
        measurable_routes=len(route_results),original_gap_intervals=sum(r['original_gap_intervals']for r in route_results),classified_subintervals=interval_count,
        route_local_unbound_m=sum(r['capacity']['unbound_m']for r in route_results),
        network=dict(target_m=network_total,unbound_m=network_unbound,combined_percent=100*(1-network_unbound/network_total),cause_sets_m=dict(network_reason_sets)),
        causes={c:dict(intervals=v['intervals'],metres=v['metres'],affected_routes=len(v['routes'])) for c,v in totals.items()},
        inspected_status=dict(confirmed_mode_conflicts_in_admitted_bindings=0,production_binding_replay_mismatches=structural_errors,
            confirmed_structural_catalogue_errors=0,unadmitted_source_eligibility='unresolved; never inferred from proximity'),
        examples=examples,routes=route_results,inventory_sha256=hashlib.sha256((output/'classified-intervals.jsonl.gz').read_bytes()).hexdigest())
    (output/'summary.json').write_text(json.dumps(result,indent=2)+'\n');return result


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--checkpoint',type=Path,required=True);p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--integration-commit',required=True)
    a=p.parse_args();r=run(a.checkpoint,a.database,a.output,a.integration_commit)
    print(json.dumps({k:v for k,v in r.items() if k not in ('routes','examples')},indent=2))
