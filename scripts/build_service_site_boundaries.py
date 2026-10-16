#!/usr/bin/env python3
"""Rebuild service visit footprints from a pinned, minimal OSM snapshot.

Requires Shapely 2.x. No network access during generation or validation.
OSM site outlines retain their holes and disconnected carriageway sites;
main motorway/trunk carriageways are removed with conservative width margins.
"""
import argparse, gzip, json, math
from pathlib import Path
from shapely.geometry import Polygon, MultiPolygon, LineString, Point, mapping
from shapely.ops import unary_union, polygonize, transform

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'collections/service-site-boundary-source-v1.json.gz'
CATALOGUES=[ROOT/'collections/uk-motorway-services-v1.json',ROOT/'android-prototype/app/src/main/assets/uk-motorway-services-v1.json']
REPORT=ROOT/'docs/service-site-boundary-audit-v1.json'

def footprint(source):
    shapes=[]
    for site in source['sites']:
        if site['kind']=='way':
            ring=site['rings'][0]
            if len(ring)<4 or ring[0]!=ring[-1]:raise ValueError('Unclosed site '+site['source_id'])
            shape=Polygon(ring)
        else:
            outer=list(polygonize([LineString(r) for r in site['outer']]))
            inner=list(polygonize([LineString(r) for r in site['inner']]))
            if not outer:raise ValueError('Missing outer geometry '+site['source_id'])
            shape=unary_union(outer).difference(unary_union(inner))
        if shape.is_empty or not shape.is_valid:raise ValueError('Invalid site '+site['source_id'])
        shapes.append(shape)
    shape=unary_union(shapes)
    x,y=shape.centroid.x,shape.centroid.y;sx=111195*math.cos(math.radians(y));sy=111195
    forward=lambda a,b,z=None:((a-x)*sx,(b-y)*sy)
    inverse=lambda a,b,z=None:(a/sx+x,b/sy+y)
    site=transform(forward,shape)
    roads=[]
    for road in source['main_roads']:
        width=max(15 if road['highway']=='motorway' else 12,road.get('lanes',2)*1.8+6)
        roads.append(transform(forward,LineString(road['coordinates'])).buffer(width))
    safe=site.difference(unary_union(roads)) if roads else site
    if safe.is_empty or not safe.is_valid:raise ValueError('No valid visit grounds '+source['id'])
    if safe.geom_type=='Polygon':safe=MultiPolygon([safe])
    if safe.geom_type!='MultiPolygon':
        safe=MultiPolygon([p for p in safe.geoms if p.geom_type=='Polygon'])
    # Seven decimal places preserve centimetre precision without large payloads.
    geometry=mapping(transform(inverse,safe))
    def rounded(v):return [rounded(a) for a in v] if isinstance(v,(tuple,list)) else round(v,7)
    return {'type':'MultiPolygon','coordinates':rounded(geometry['coordinates'])},safe.area,len(roads)

def build(check=False):
    snapshot=json.loads(gzip.decompress(SOURCE.read_bytes()))
    sources={s['id']:s for s in snapshot['services']};report={'policy_version':1,'source_date':snapshot['source_date'],'attribution':'© OpenStreetMap contributors · ODbL 1.0','sites':[]}
    output=[]
    for path in CATALOGUES:
        root=json.loads(path.read_text())
        if set(sources)!={s['id'] for s in root['services']}:raise ValueError('Snapshot and catalogue membership differ')
        for row in root['services']:
            source=sources[row['id']];geometry,area,roads=footprint(source)
            row['site_boundary']=geometry
            row['site_boundary_source_ids']=[s['source_id'] for s in source['sites']]
            row['site_boundary_policy_version']=1
            if path==CATALOGUES[0]:report['sites'].append({'id':row['id'],'name':row['name'],'boundary_parts':len(geometry['coordinates']),'area_m2':round(area),'main_road_exclusions':roads,'source_ids':row['site_boundary_source_ids']})
        root['attribution']='© OpenStreetMap contributors · ODbL 1.0'
        root['site_boundary_source_date']=snapshot['source_date']
        text=json.dumps(root,separators=(',',':'),ensure_ascii=False)+'\n';output.append((path,text))
    report['catalogue_sites']=len(sources);report['sites_with_boundaries']=len(report['sites'])
    output.append((REPORT,json.dumps(report,indent=2,ensure_ascii=False)+'\n'))
    for path,text in output:
        if check:
            if path.read_text()!=text:raise ValueError('Stale generated file '+str(path))
        else:path.write_text(text)
    print(f"Verified {len(sources)} service footprints" if check else f"Wrote {len(sources)} service footprints")

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');build(parser.parse_args().check)
