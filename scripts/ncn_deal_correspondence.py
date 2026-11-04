"""Opt-in, exact-evidence Deal NCN1 candidate. Never downloads or publishes data.

The reviewed exception is a source/target identity correspondence, not a new
proximity rule. No unchanged or future geometry can qualify by being nearby.
Existing admission and ordinary-seam compilers keep their original policy.
"""
import argparse
from collections import defaultdict
from dataclasses import asdict
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import sqlite3
from types import SimpleNamespace

from shapely.geometry import LineString, Point
from scripts.ncn_binding_attainability import content_sha, encoded, digest, network_intervals
from scripts.ncn_trial.coverage import Section, TrialReference, merge_intervals
from scripts.ncn_trial.journey_replay import projector
from scripts.ncn_trial.open_completion import bind_edges, POLICY

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT/'docs/data-trials/ncn1-deal-fixture-20261030.json'
OBSERVATIONS = ROOT/'tests/fixtures/ncn-deal-resolution/observations.json'
REVIEW = ROOT/'tests/fixtures/ncn-deal-resolution/review.json'
FIXTURE_SHA = 'bb7fa4420151218c053cda1e93b6a592323e2c85a724965985b211d9386c779c'
SEAM_CONTENT = 'cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0'
WAY = '4471747'
ROUTE = 'NCN:1'


def read_fixture():
    raw = FIXTURE.read_bytes()
    if hashlib.sha256(raw).hexdigest() != FIXTURE_SHA:
        raise ValueError('Deal evidence fixture changed')
    return json.loads(raw)


def validate_observations(f, observations):
    way = next(w for w in observations['osm']['ways'] if w['id'] == WAY)
    if (way['coordinates'] != f['path']['coordinates'] or
            list(map(int, way['nodes'])) != f['path']['osm_node_ids'] or
            way['version'] != 11 or way['tags'] != {
                'bicycle':'designated','foot':'designated','highway':'cycleway',
                'lit':'no','segregated':'no','smoothness':'good','surface':'asphalt'}):
        raise ValueError('Exact designated public cycleway evidence required')
    relation = next(r for r in observations['osm']['relations'] if r['id'] == '75880')
    if (relation['version'] != 747 or
            any(relation['tags'].get(k) != v for k,v in
                {'network':'ncn','ref':'1','route':'bicycle'}.items()) or
            {'type':'way','ref':WAY,'role':''} not in relation['relevant_members']):
        raise ValueError('Exact open NCN1 membership required')
    for other, node in [('84079387',way['nodes'][0]),('84079705',way['nodes'][-1])]:
        neighbour = next(w for w in observations['osm']['ways'] if w['id'] == other)
        if node not in (neighbour['nodes'][0],neighbour['nodes'][-1]):
            raise ValueError('Connected endpoint neighbour required')
    private = next(w for w in observations['osm']['ways'] if w['id'] == '77611523')
    if private['tags'].get('access') != 'private':
        raise ValueError('Private control observation changed; review again')


def proposed_bindings(f):
    """Compile only the independently reviewed held source span, then bind its
    one evidenced degree-two target junction to the closest source station.
    That explicit finite anchor resolves the 28 mm automatic ambiguity.
    """
    project = projector({'projection_origin': f['projection_origin']})
    xy = [project(c) for c in f['path']['coordinates']]
    section_key = WAY+':6:11'
    section = Section(section_key, LineString(xy[6:12]), (ROUTE,))
    reference = TrialReference('reviewed-deal-only', [section])
    path = SimpleNamespace(section_keys=[section_key], modes=['on_foot','cycling'],
                           target_geometry='exact_source_spans')
    targets = sorted((t['key'],LineString(t['coordinates'])) for t in f['nearby_targets_metric'])
    _, spans, diagnostics = bind_edges(reference,[path],targets,ROUTE+':captured-open-main',ordinary_seams=True)
    offset = sum(math.dist(a,z) for a,z in zip(xy[:6],xy[1:7]))
    rows = [dict(start=offset+b.physical_start,end=offset+b.physical_end,
                 target=b.logical_key,low=b.logical_start,high=b.logical_end,modes=b.modes) for b in spans]
    incoming = next(t for key,t in targets if key==f['target_key'])
    vertex = tuple(incoming.coords[-1])
    outgoing = [t for key,t in targets if key!=f['target_key'] and vertex in t.coords]
    if len(outgoing)!=1 or sum(vertex in t.coords for key,t in targets)!=2:
        raise ValueError('Exact degree-two ordinary target junction required')
    a,z = xy[10:12]
    edge=LineString([a,z]); station=edge.project(Point(vertex))
    if not 0 < station < edge.length or edge.distance(Point(vertex)) > POLICY['maximum_offset_m']:
        raise ValueError('Finite source-supported junction anchor required')
    source_start = offset+sum(math.dist(a,z) for a,z in zip(xy[6:10],xy[7:11]))
    cut=source_start+station
    if len(rows)!=6 or not rows[-2]['end'] < cut < rows[-1]['end']:
        raise ValueError('Reviewed two-target junction shape changed')
    rows[-2]['end']=cut; rows[-2]['high']=incoming.length
    rows[-1]['start']=cut; rows[-1]['low']=0.
    return rows, {'automatic_unbound_source_m':diagnostics['unbound_source_occurrence_m'],
                  'reviewed_junction_source_m':cut,'junction_eq_offset_m':edge.distance(Point(vertex)),
                  'reviewed_junction_target':list(vertex)}


def load_review():
    review=json.loads(REVIEW.read_text()); f=read_fixture()
    observations=json.loads(OBSERVATIONS.read_text())
    if hashlib.sha256(OBSERVATIONS.read_bytes()).hexdigest()!=review['observations_sha256']:
        raise ValueError('Observed identity/access evidence changed')
    validate_observations(f,observations)
    if hashlib.sha256((REVIEW.parent/'full-extent-check.json').read_bytes()).hexdigest()!=review['full_extent_check_sha256']:
        raise ValueError('Full target graph/exclusion evidence changed')
    bindings,junction=proposed_bindings(f)
    if (review['schema_version']!=1 or review['policy']!='exact_reviewed_route_correspondence' or
            review['route']!=ROUTE or review['way']!=WAY or
            review['fixture_sha256']!=FIXTURE_SHA or review['base_content_sha256']!=SEAM_CONTENT or
            review['source_geometry_sha256']!=f['path']['source_geometry_sha256'] or
            review['source_nodes']!=f['path']['osm_node_ids'] or
            review['bindings']!=bindings or review['junction']!=junction or
            review['approved_edges']!=[6,7,8,9,10] or
            review['private_control_eligible'] is not False):
        raise ValueError('Exact reviewed Deal correspondence required')
    return review,f


def corrected_path(path,review,f):
    """Fail closed on route, identity, node order, geometry, modes and old bindings."""
    if (path['route']!=ROUTE or path['way']!=WAY or
            path['source_geometry_sha256']!=review['source_geometry_sha256'] or
            path['coordinates']!=f['path']['coordinates'] or path['nodes']!=review['source_nodes'] or
            path['modes']!=['on_foot','cycling'] or path['origin']!=f['projection_origin'] or
            digest(path['bindings'])!=review['previous_bindings_sha256']):
        raise ValueError('Exact prior source identity, modes and bindings required')
    updated=dict(path); updated['bindings']=path['bindings']+review['bindings']
    ordered=sorted(updated['bindings'],key=lambda b:b['start'])
    for b in ordered:
        if (not set(b['modes'])<=set(path['modes']) or
                not all(math.isfinite(b[k]) for k in ('start','end','low','high')) or
                not 0<=b['start']<b['end']<=path['positions'][-1]+1e-7):
            raise ValueError('Unsupported interval or mode')
    if any(a['end']>b['start']+1e-7 for a,b in zip(ordered,ordered[1:])):
        raise ValueError('Source evidence ownership overlap')
    return updated


def apply(database, output):
    review,f=load_review(); database=Path(database); output=Path(output)
    if output.resolve()==database.resolve() or output.exists():
        raise ValueError('Fresh output required; input is read-only')
    if content_sha(database)!=review['base_content_sha256']:
        raise ValueError('Exact validated seam candidate required')
    with sqlite3.connect(database.resolve().as_uri()+'?mode=ro',uri=True) as db:
        row=db.execute('SELECT data FROM paths WHERE route=? AND way=?',(ROUTE,WAY)).fetchone()
        path=json.loads(gzip.decompress(row[0])); updated=corrected_path(path,review,f)
        for key,pinned in review['targets'].items():
            target=db.execute('SELECT length,coords,network FROM targets WHERE route=? AND key=?',(ROUTE,key)).fetchone()
            if [target[0],json.loads(target[1]),json.loads(target[2])]!=pinned:
                raise ValueError('Exact target length, coordinates and canonical mapping required')
    output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(database,output)
    try:
        with sqlite3.connect(output) as db:
            db.execute('UPDATE paths SET data=? WHERE route=? AND way=?',
                       (gzip.compress(encoded(updated),compresslevel=6,mtime=0),ROUTE,WAY)); db.commit()
    except Exception:
        output.unlink(missing_ok=True); raise
    return output


def measure_delta(database,review):
    """Read all existing bindings for the affected canonical edges, including
    other routes, before computing the union delta. Never sum route lengths.
    """
    keys=set(review['targets']); before=defaultdict(list); after=defaultdict(list)
    with sqlite3.connect(Path(database).resolve().as_uri()+'?mode=ro',uri=True) as db:
        rows=[dict(route=r,key=k,length=l,network=json.loads(n)) for r,k,l,n in db.execute('SELECT route,key,length,network FROM targets')]
        affected={p[0] for t in rows if t['route']==ROUTE and t['key']in keys for p in t['network']}
        wanted={(t['route'],t['key']) for t in rows if any(p[0]in affected for p in t['network'])}
        for route,raw in db.execute('SELECT route,data FROM paths'):
            p=json.loads(gzip.decompress(raw))
            for b in p['bindings']:
                if (route,b['target'])in wanted:
                    before[route,b['target']].append((b['low'],b['high']))
        after={k:list(v)for k,v in before.items()}
        for b in review['bindings']:
            after.setdefault((ROUTE,b['target']),[]).append((b['low'],b['high']))
        totals=[]; local=[]
        for coverage in [before,after]:
            mapped=defaultdict(list)
            for t in rows:
                if (t['route'],t['key'])in wanted:
                    network_intervals([t],{t['key']:merge_intervals(coverage.get((t['route'],t['key']),[]))},mapped)
            totals.append(sum(z-a for edge,spans in mapped.items() if edge in affected for a,z in merge_intervals(spans)))
            local.append(sum(z-a for key in keys for a,z in merge_intervals(coverage.get((ROUTE,key),[]))))
    return {'affected_network_edges':sorted(affected),'affected_target_route_identities':len(wanted),
            'route_local_before_m':local[0],'route_local_after_m':local[1],
            'route_local_added_m':local[1]-local[0],
            'unique_network_before_m':totals[0],'unique_network_after_m':totals[1],
            'unique_network_added_m':totals[1]-totals[0]}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); print(apply(a.database,a.output))


if __name__=='__main__':main()
