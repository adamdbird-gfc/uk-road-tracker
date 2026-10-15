#!/usr/bin/env python3
"""Align NI settlements to the exact pinned County Collector asset, preserving GB."""
import argparse,gzip,hashlib,json
from pathlib import Path
from shapely.geometry import Polygon,MultiPolygon,shape
from shapely import make_valid
from shapely.strtree import STRtree

NI_COUNTIES={'ANM','ARH','DWN','FRM','LDR','TYN'}


def digest(raw):return hashlib.sha256(raw).hexdigest()


def decode_county(raw):
    data=gzip.decompress(raw);position=0
    def uint():
        nonlocal position
        value=0
        for shift in range(0,35,7):
            b=data[position];position+=1;value|=(b&127)<<shift
            if not b&128:return value
        raise ValueError('Invalid county integer')
    rings=[]
    for _ in range(uint()):
        count=uint();x=y=0;points=[]
        if count<3 or count>2_000_000:raise ValueError('Invalid county vertex count')
        for _ in range(count):
            a,b=uint(),uint();x+=(a>>1)^-(a&1);y+=(b>>1)^-(b&1);points.append((x/1e6,y/1e6))
        rings.append(points)
    if position!=len(data):raise ValueError('Trailing county data')
    shells,holes=[],[]
    for ring in rings:
        area=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(ring,ring[1:]+ring[:1]))/2
        (shells if area<0 else holes).append(ring)
    if not shells:raise ValueError('County exterior rings missing')
    outer=[Polygon(r) for r in shells];assigned=[[] for _ in outer]
    for ring in holes:
        hole=Polygon(ring)
        candidates=[i for i,g in enumerate(outer) if g.area>hole.area and g.covers(hole)]
        if not candidates:raise ValueError('County hole is outside an exterior')
        assigned[min(candidates,key=lambda i:outer[i].area)].append(ring)
    g=MultiPolygon([Polygon(r,h) for r,h in zip(shells,assigned)])
    if not g.is_valid:
        fixed=make_valid(g)
        if fixed.geom_type not in ('Polygon','MultiPolygon') or abs(fixed.area-g.area)/g.area>1e-8:
            raise ValueError('Packed county requires material repair')
        g=fixed
    return g


def prepare(boundary_dir,asset_dir,output):
    boundary_dir,asset_dir,output=map(Path,(boundary_dir,asset_dir,output))
    catalogue=json.loads((asset_dir/'catalogue.json').read_text());data=(asset_dir/'boundaries.bin').read_bytes()
    if digest(data)!=catalogue['data_sha256']:raise ValueError('Packed county asset changed')
    prior_raw=(asset_dir/'settlements.bin').read_bytes();prior=json.loads(gzip.decompress(prior_raw))
    if prior['county_source_sha256']!=catalogue['source_sha256'] or prior['county_version']!=catalogue['version']:
        raise ValueError('Historic reference versions differ')
    if len(prior['settlements'])!=8545 or any(c.startswith('N') for c in prior['settlements']):
        raise ValueError('Expected unchanged GB membership baseline')
    codes=[];geometries=[]
    for county in catalogue['counties']:
        if county['code'] in NI_COUNTIES:
            codes.append(county['code']);geometries.append(decode_county(data[county['offset']:county['offset']+county['length']]))
    if set(codes)!=NI_COUNTIES:raise ValueError('Incomplete NI county reference')
    tree=STRtree(geometries)
    raw=gzip.decompress((boundary_dir/'ni-boundary-candidates.geojson.gz').read_bytes())
    if digest(raw)!=json.loads((boundary_dir/'boundary-audit.json').read_text())['candidate_geojson_sha256']:
        raise ValueError('NI boundary snapshot changed')
    features=json.loads(raw)['features'];rows=[];mapping={}
    for f in features:
        c=f['properties']['code'];g=shape(f['geometry']);overlaps=[]
        for pos in tree.query(g,predicate='intersects'):
            area=g.intersection(geometries[int(pos)]).area
            if area>0:overlaps.append((area,codes[int(pos)]))
        overlaps.sort(key=lambda r:(-r[0],r[1]));members=[c for _,c in overlaps]
        if not members:raise ValueError('NI settlement has no positive historic county overlap: '+c)
        if c in mapping:raise ValueError('Duplicate NI settlement identity')
        mapping[c]=members;point=g.representative_point();name=f['properties']['name']
        rows.append({'code':c,'name':name,'nation':'Northern Ireland','region':None,'county':None,
                     'aliases':[name], 'centre':[point.y,point.x], 'historic_county_codes':members})
    if len(rows)!=527:raise ValueError('Incomplete NI settlement catalogue')
    merged=dict(prior);merged['settlements']=dict(prior['settlements'],**mapping)
    merged['scope']='Great Britain and Northern Ireland'
    merged['ni_settlement_source']='NISRA Settlement Development Limits 2015'
    merged['ni_boundary_sha256']=digest(raw);merged['ni_county_geometry_data_sha256']=catalogue['data_sha256']
    merged['ni_county_geometry_basis']='Exact County Collector packed WGS84 asset, coordinates rounded to 1e-6 degrees'
    assert all(merged['settlements'][c]==v for c,v in prior['settlements'].items())
    output.mkdir(parents=True,exist_ok=True)
    index=(json.dumps(merged,separators=(',',':'))+'\n').encode()
    (output/'settlements.bin').write_bytes(gzip.compress(index,mtime=0))
    metadata={'version':1,'source':'NISRA Settlement Development Limits 2015','settlements':rows}
    metadata_raw=(json.dumps(metadata,separators=(',',':'),ensure_ascii=False)+'\n').encode();(output/'ni-metadata.json').write_bytes(metadata_raw)
    report={'status':'prepared_only','gb_memberships_preserved':len(prior['settlements']),'ni_memberships':len(mapping),
      'total_memberships':len(merged['settlements']),'ni_spanning_counties':sum(len(v)>1 for v in mapping.values()),
      'primary_county_counts':{c:sum(v[0]==c for v in mapping.values()) for c in sorted(NI_COUNTIES)},
      'county_asset_sha256':catalogue['data_sha256'],'county_original_source_sha256':catalogue['source_sha256'],
      'prior_index_sha256':digest(prior_raw),'merged_index_sha256':digest((output/'settlements.bin').read_bytes()),
      'metadata_sha256':digest(metadata_raw),'merged_index_bytes':(output/'settlements.bin').stat().st_size,
      'coordinate_precision_degrees':1e-6,'notes':['Original GB memberships unchanged','NI uses exact already-pinned client boundary geometry; no upstream county update',
      'NI administrative county/region remain null; historic memberships are separate','Only official source names are added as aliases; no inferred or ambiguous place-name aliases']}
    (output/'integration-audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2));return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--boundaries',required=True);p.add_argument('--assets',required=True);p.add_argument('--output',required=True);a=p.parse_args();prepare(a.boundaries,a.assets,a.output)
