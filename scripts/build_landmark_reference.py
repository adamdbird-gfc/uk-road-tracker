#!/usr/bin/env python3
"""Build pinned offline landmark corridors from saved public OSM responses.
Usage: python build_landmark_reference.py SOURCE_DIRECTORY OUTPUT_JSON
Requires shapely. Source response SHA256s are embedded for reproducibility.
The input filenames and OSM IDs below deliberately exclude neighbouring bridges,
railway lines, the New Street bull, and motorway slip roads.
"""
import json, hashlib, math, sys
from pathlib import Path
from shapely.geometry import LineString, Point, Polygon, box, shape, mapping
from shapely.ops import unary_union, linemerge, transform, polygonize
root=Path(sys.argv[1]); sources={}
def load(name):
 p=root/name; raw=p.read_bytes(); sources[name]=hashlib.sha256(raw).hexdigest(); return json.loads(raw)
def elements(name): return load(name)['elements']
def ways(elements):
 nodes={e['id']:(e['lon'],e['lat']) for e in elements if e['type']=='node'}
 for e in elements:
  if e['type']!='way':continue
  coords=[(p['lon'],p['lat']) for p in e['geometry']] if 'geometry' in e else [nodes[n] for n in e['nodes'] if n in nodes]
  if len(coords)>1:yield e,LineString(coords)
def merge(lines):
 result=unary_union(lines)
 if result.geom_type!='LineString':result=linemerge(result)
 return result if result.geom_type=='LineString' else max(result.geoms,key=lambda p:p.length)
def metric(g,lat):
 scale=111320*math.cos(math.radians(lat))
 return transform(lambda x,y,z=None:(x*scale,y*111320),g),scale
def buffer(g,metres):
 m,scale=metric(g,g.centroid.y);return transform(lambda x,y,z=None:(x/scale,y/111320),m.buffer(metres))
def length(g):return metric(g,g.centroid.y)[0].length
def coords(g):return [[round(x,7),round(y,7)] for x,y in g.coords]
def lines(g):
 if g.is_empty:return []
 if g.geom_type=='LineString':return [g]
 return [part for part in g.geoms if part.geom_type=='LineString' and length(part)>1]
records=[]
def corridor(id,title,name,rule,lines,tolerance=25,modes=None,refs=None,minimum=0,source_ids=None):
 records.append(dict(id=id,title=title,name=name,rule=rule,modes=modes or ['walking','running','pedestrian','driving','bus','cycling','bicycle'],kind='distance' if minimum else 'crossing',tolerance=tolerance,minimum=minimum,roads=[dict(coordinates=coords(line),ref=(refs[i] if refs else '')) for i,line in enumerate(lines)],osm_ids=source_ids or []))
def area(id,title,name,rule,g,modes,ids):
 records.append(dict(id=id,title=title,name=name,rule=rule,modes=modes,kind='area',minimum=10,polygon=coords(g.exterior),osm_ids=ids))
west=list(ways(elements('westminster-osm.json')))
bridge=merge([g for e,g in west if e.get('tags',{}).get('highway')=='primary']).intersection(box(-.12365,51.5005,-.1199,51.5013))
corridor('big-ben','Tick Tock','Big Ben','Cross the full Westminster Bridge beside Big Ben on foot, by bicycle or by road.',[merge(lines(bridge))],25,source_ids=['way/'+str(e['id']) for e,g in west])
places=elements('landmark-places.json'); pw=list(ways(places)); byid={e['id']:e for e in places}
prom=merge([g for e,g in pw if e.get('tags',{}).get('ref')=='A584' and e.get('tags',{}).get('highway')=='primary']).intersection(box(-3.057,53.8147,-3.054,53.8170))
corridor('blackpool-tower','Paris is lovely this time of year','Blackpool Tower','Cover the full Tower-front Promenade stretch, between the mapped north and south endpoints (about 250 m), on foot, by bicycle or by road.',[merge(lines(prom))],55,source_ids=['way/'+str(e['id']) for e,g in pw if e.get('tags',{}).get('ref')=='A584'])
humber=list(ways(elements('landmark-humber-correct.json'))); hs=[(e,g) for e,g in humber if e['id'] in (3996808,4063883)]
corridor('humber-bridge-landmark','No longer the longest','Humber Bridge','Cross the full Humber Bridge on foot, by bicycle or by road.',[max((g for e,g in hs),key=length)],25,source_ids=['way/'+str(e['id']) for e,g in hs])
trent=list(ways(elements('landmark-trent-map.json'))); ts=[(e,g) for e,g in trent if e['id'] in (4440665,29102875)]
corridor('trent-bridge','Football or Cricket, Sir?','Trent Bridge','Cross the full A60 Trent Bridge on foot, by bicycle or by road. Nottingham’s football and cricket grounds are nearby.',[max((g for e,g in ts),key=length)],25,source_ids=['way/'+str(e['id']) for e,g in ts])
bull=byid[1101446898]
area('bullring-bull','Mooooo!','Bullring Bull','Walk through the immediate pedestrian area around the Bullring Bull; nearby streets and New Street station do not count.',buffer(Point(bull['lon'],bull['lat']),22),['walking','running','pedestrian'],['node/1101446898'])
ness=next(g for e,g in pw if e['id']==224261295)
area('ness-point','The Far East','Ness Point','Reach the Euroscope viewpoint at Ness Point on foot or by bicycle. Nearby Lowestoft routes do not count.',buffer(Polygon(ness.coords),15),['walking','running','pedestrian','cycling','bicycle'],['way/224261295'])
m4s=[(e,g) for e,g in pw if e.get('tags',{}).get('ref')=='M4' and e.get('tags',{}).get('highway')=='motorway']
m4=merge([g for e,g in m4s]).intersection(box(-.625,51.47,-.585,51.515))
corridor('windsor-castle','Royal drive-by','Windsor Castle','Drive or take a bus along the full defined M4 stretch north of Windsor, from the J6 area towards Datchet (about 3 km).', [merge(lines(m4))],35,['driving','bus'],['M4'],source_ids=['way/'+str(e['id']) for e,g in m4s])
lake=shape(load('landmark-lake-nominatim.json')[0]['geojson']); shore=buffer(lake.boundary,300)
lakeways={e['id']:(e,g) for e,g in ways(elements('landmark-a82.json'))}
for i in range(3):
 for e,g in ways(elements('landmark-lake-roads-'+str(i)+'.json')):lakeways[e['id']]=(e,g)
qualifying=[]; refs=[];ids=[]
# Merge by road reference before clipping so the same OSM way/lane isn't counted twice.
for ref in ['A82','B852','B862']:
 candidates=[(e,g) for e,g in lakeways.values() if e.get('tags',{}).get('ref')==ref and e.get('tags',{}).get('highway') in ('trunk','primary','secondary','tertiary')]
 merged=unary_union([g for e,g in candidates]).intersection(shore)
 for line in lines(merged):qualifying.append(line);refs.append(ref)
 ids += ['way/'+str(e['id']) for e,g in candidates]
 assert sum(length(g) for g,r in zip(qualifying,refs) if r==ref)>100,ref
corridor('loch-ness','Why hello Nessie','Loch Ness','Cover at least 5 km of distinct lakeside A82, B852 or B862 road in one journey, within 300 m of the mapped Loch Ness shore. Repeated stretches count once.',qualifying,25,refs=refs,minimum=5000,source_ids=['relation/4023212']+ids)
conwy=elements('landmark-conwy.json'); cs=[(e,g) for e,g in ways(conwy) if e['id'] in (151369882,151369883)]
corridor('conwy-castle','The Iron Ring','Conwy Castle','Walk through the immediate castle area, or cross the full adjacent A547 road bridge on foot, by bicycle or by road. The railway and A55 do not count.',[merge([g for e,g in cs])],25,source_ids=['way/52467063']+['way/'+str(e['id']) for e,g in cs])
castle=next(g for e,g in pw if e['id']==52467063);records[-1]['polygon']=coords(buffer(Polygon(castle.coords),25).exterior);records[-1]['area_modes']=['walking','running','pedestrian']
stormont=byid[6010674]; outers=[LineString([(p['lon'],p['lat']) for p in m['geometry']]) for m in stormont['members'] if m.get('role')=='outer' and 'geometry'in m]
parliament=unary_union(list(polygonize(unary_union(outers))))
assert not parliament.is_empty
area('stormont','Weather warning, Storms likely','Stormont','Reach the grounds immediately around the Parliament Buildings at Stormont. The estate entrance and distant approach do not count.',buffer(parliament.convex_hull,45),['walking','running','pedestrian','driving','bus','cycling','bicycle'],['relation/6010674'])
output={'version':1,'attribution':'© OpenStreetMap contributors, ODbL 1.0','source_date':'2026-10-07','source_sha256':sources,'landmarks':records}
Path(sys.argv[2]).parent.mkdir(parents=True,exist_ok=True);Path(sys.argv[2]).write_text(json.dumps(output,separators=(',',':'))+'\n')
for r in records:print(r['id'],round(sum(length(LineString(x['coordinates'])) for x in r.get('roads',[]))),len(r.get('roads',[])))
