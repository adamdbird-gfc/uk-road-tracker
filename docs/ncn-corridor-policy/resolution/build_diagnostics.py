"""Exact finite geographical diagnostics, without external tiles or awards."""
import json
from pathlib import Path
from shapely.geometry import LineString,mapping
from shapely.ops import substring
from scripts.ncn_trial.journey_replay import projector
from pyproj import Geod

HERE=Path(__file__).resolve().parent

def build():
    cat=json.loads((HERE.parent/'delivery/catalogue.json').read_text());obs=json.loads((HERE/'sources/osm-local.json').read_text());review=json.loads((HERE/'review.json').read_text())
    features=[]
    def add(case,layer,coords,**props):
        features.append(dict(type='Feature',properties=dict(case=case,layer=layer,**props),geometry=dict(type='LineString',coordinates=coords)))
    for name,way in [('bradford','159311690'),('highland','463641622'),('derry','30268009'),('cumbria','23385711'),('wellington','77611523')]:
        for w in obs[name]['ways']:
            if str(w['id'])==way or str(w['id'])in ['4471747','1056889615','84079709','463641619','463641626','22747003','78390776','200365698','160017281','674543498','1157895911']:
                add(name,'source'if str(w['id'])==way else'neighbour',w['coordinates'],way=str(w['id']),tags=w['tags'],nodes=w['nodes'])
        routes={'bradford':['NCN:66'],'highland':['NCN:7'],'derry':['NCN:92'],'cumbria':['NCN:6','NCN:7'],'wellington':['NCN:1']}[name]
        bounds=LineString(next(w['coordinates']for w in obs[name]['ways']if str(w['id'])==way)).buffer(.003).bounds
        for t in cat['targets']:
            if t['route']in routes and LineString(t['coords']).intersects(LineString([[bounds[0],bounds[1]],[bounds[2],bounds[3]]]).envelope):
                add(name,'revision4',t['coords'],route=t['route'],target=t['key'],network=t['network'],existing_coverage=t['current_route_coverage'])
        if name in ('bradford','derry'):
            c=next(c for c in review['cases']if c['way']==way);p=c['path'];project=projector({'projection_origin':p['origin']})
            # Inverse of the exact existing operational projection, not a
            # different metric reprojected onto the source station system.
            import math
            def inverse(x,y):return [p['origin'][0]+math.degrees(x/(6371008.8*math.cos(math.radians(p['origin'][1])))),p['origin'][1]+math.degrees(y/6371008.8)]
            source=LineString([project(v)for v in p['coordinates']]);targets={t['key']:t for t in c['targets']}
            for b in c['bindings']:
                part=substring(source,b['start'],b['end']);add(name,'new_source', [inverse(*v)for v in part.coords],binding=b)
                t=targets[b['target']];line=LineString([project(v)for v in t['coords']]);part=substring(line,b['low'],b['high']);add(name,'new_target',[inverse(*v)for v in part.coords],binding=b)
    return dict(type='FeatureCollection',features=features)

if __name__=='__main__':(HERE/'diagnostics.geojson').write_text(json.dumps(build(),indent=2,sort_keys=True)+'\n')
