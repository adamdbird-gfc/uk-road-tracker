#!/usr/bin/env python3
"""Extract public OSM geometry for the eight reviewed NI membership cases."""
import gzip,json,sys,hashlib
from pathlib import Path
import osmium
from shapely.geometry import shape,LineString


def extract(boundaries,evidence,pbf,output):
    features=json.loads(gzip.decompress(Path(boundaries).read_bytes()))['features']
    geometries={f['properties']['code']:shape(f['geometry']) for f in features}
    e=json.loads(Path(evidence).read_text());targets=[]
    ways=[w for o in e['overlaps'] for w in o['eligible_named_ways']]
    for row in e['overlap_removal_sensitivity']:
        for name in row['names_relying_on_source_overlap']:
            ids=sorted({w['way_id'] for w in ways if w['name']==name})
            if not ids:raise ValueError('Missing overlap source way')
            targets.append({'code':row['code'],'name':name,'way_ids':ids})
    selected={i for t in targets for i in t['way_ids']};found={}
    class Handler(osmium.SimpleHandler):
        def way(self,w):
            if w.id in selected:found[w.id]=[(n.lon,n.lat) for n in w.nodes]
    output=Path(output);output.mkdir(parents=True,exist_ok=True);index=output/'overlap-locations.index'
    try:Handler().apply_file(str(pbf),locations=True,idx='sparse_file_array,'+str(index.resolve()))
    finally:index.unlink(missing_ok=True)
    for target in targets:
        lines=[found[i] for i in target['way_ids']];matched=sorted({c for c,g in geometries.items() if any(g.intersects(LineString(line)) for line in lines)})
        if target['code'] not in matched:raise ValueError('Overlap credit not achievable from source geometry')
        target['geometry']={'type':'MultiLineString','coordinates':lines};target['intersected_settlement_codes']=matched
    if len(targets)!=8:raise ValueError('Expected eight reviewed memberships')
    result={'source':'Public OSM way geometry from the pinned NI inventory snapshot; no personal route data','cases':targets}
    (output/'overlap-credit-fixtures.json').write_text(json.dumps(result,separators=(',',':'))+'\n')
    print('Validated source geometry for',len(targets),'overlap-dependent memberships',flush=True)


if __name__=='__main__':extract(*sys.argv[1:])
