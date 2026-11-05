"""Reproduce the two finite reviews from preserved revision-4 rows. No admission discovery."""
import json
import math
from pathlib import Path
from shapely.geometry import LineString, Point
from scripts.ncn_trial.journey_replay import projector

HERE=Path(__file__).resolve().parent

def build():
    cat=json.loads((HERE.parent/'delivery/catalogue.json').read_text())
    cases=[]
    for way in ('159311690','30268009'):
        path=cat['paths'][way][0]
        project=projector({'projection_origin':path['origin']})
        source=LineString([project(c) for c in path['coordinates']])
        targets={t['key']:t for t in cat['targets'] if t['route']==path['route']}
        def target(prefix):return next(t for k,t in targets.items() if k.startswith('open-edge:'+prefix))
        anchors=[]
        def add(a,z,prefix,low,high,reason):
            t=target(prefix)
            # Split at every source vertex: affine endpoint norms bound EVERY
            # point of each linear correspondence, not a sampled proximity test.
            stations=[a]+[s for s in path['positions'] if a<s<z]+[z]
            for s,e in zip(stations,stations[1:]):
                l=low+(high-low)*(s-a)/(z-a);h=low+(high-low)*(e-a)/(z-a)
                line=LineString([project(c) for c in t['coords']])
                distances=[source.interpolate(s).distance(line.interpolate(l)),source.interpolate(e).distance(line.interpolate(h))]
                anchors.append(dict(start=s,end=e,target=t['key'],low=l,high=h,modes=path['modes'],continuous_max_offset_m=max(distances),reason=reason))
        pos=path['positions']
        if way=='159311690':
            add(path['bindings'][0]['end'],path['bindings'][1]['start'],'217a',target('217a')['length'],0.,'Open south arm; retain both existing boundary bindings')
            add(path['bindings'][1]['end'],path['bindings'][2]['start'],'c728',path['bindings'][1]['high'],path['bindings'][2]['low'],'Open northwest arm only; northeast arm has separate physical ownership')
        else:
            b=path['bindings']
            add(0.,pos[1],'1a4b',0.,b[0]['low'],'Ballougry Road exit to first preserved binding')
            add(pos[6],next(x['start']for x in b if x['target']==target('0432')['key']),'bec7',target('bec7')['current_route_coverage'][-1][1],target('bec7')['length'],'Admitted source bend: finite unowned continuation')
            add(pos[7],next(x['start']for x in b if x['target']==target('7422')['key']),'0432',target('0432')['current_route_coverage'][-1][1],target('0432')['length'],'Admitted source bend: finite unowned continuation')
            add(pos[8],next(x['start']for x in b if x['target']==target('9b5b')['key']),'7422',target('7422')['current_route_coverage'][-1][1],target('7422')['length'],'Admitted source bend: finite unowned continuation')
            # Exact publisher vertices correspond to the same successive bends
            # on the uninterrupted designated former railway, not another road.
            evidence=json.loads((HERE.parent/'evidence.json').read_text())
            c=next(c for c in evidence['cases']if c['way']==way)
            for edge in (10,11,12,21,22,23,24):
                r=next(r for r in c['candidates']if r['source_edge']==edge)
                t=targets[r['target_key']]
                end=pos[edge+1];high=t['length']
                if edge==12:end=next(x['start']for x in b if x['target']==target('08e8')['key'])
                if edge==24:high=target('591a')['current_route_coverage'][0][0]
                add(pos[edge],end,t['key'].split(':')[1],0.,high,'Dated NCN92 railway corridor; exact successive finite bends')
        bindings=[{k:v for k,v in a.items()if k not in ('continuous_max_offset_m','reason')}for a in anchors]
        touched=sorted({b['target']for b in bindings})
        cases.append(dict(way=way,route=path['route'],path=path,targets=[targets[k]for k in touched],bindings=bindings,anchors=anchors))
    return dict(schema_version=1,base_sha256=cat['database_sha256'],policy='two_exact_reviewed_correspondences_only',cases=cases)

if __name__=='__main__':
    (HERE/'review.json').write_text(json.dumps(build(),indent=2,sort_keys=True)+'\n')
