"""Compile reviewed checkpoint into an indexed, read-only Android catalogue.

No source admissions. Physical network uses a noded union of verified open targets;
source travel remains separate from its route and network completion bindings.
"""
import argparse,gzip,json,math,hashlib,sqlite3
from pathlib import Path
from shapely.geometry import LineString
from shapely.ops import unary_union
from shapely.strtree import STRtree
from matched_path_evidence import digest
from scripts.ncn_trial.completion_checkpoint import read_checkpoint_route,load_source,open_extent

PINS='a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641'
def sha(b):return hashlib.sha256(b).hexdigest()
def dumps(v):return json.dumps(v,separators=(',',':'),allow_nan=False)
def positions(coords,origin):
    xy=[(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))for c in coords];out=[0.]
    for a,z in zip(xy,xy[1:]):out.append(out[-1]+math.dist(a,z))
    return out

def compile_path_bindings(path, plan, alternative, *, sections=None, bindex=None):
    """Translate section-local anchors to their evidenced full-way positions.

    Open-main plans can also contain proper sub-sections of a source way. They
    must use the same offset discipline as the preserved alternative inventory.
    A section key is only an occurrence hint: its coordinates must agree too.
    """
    origin=plan['metadata']['projection_origin'] if alternative else plan['projection_origin']
    coords=[list(c) for c in path.coordinates]; pos=positions(coords,origin)
    if sections is None:
        sections={s['key']:s for s in plan['sections' if alternative else 'physical_sections_metric']}
    if bindex is None:
        bindex={}
        for b in plan['bindings']:bindex.setdefault(b['physical_key'],[]).append(b)
    if alternative:
        points=coords
    else:
        project=lambda c:(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))
        points=[project(c) for c in coords]
    result=[]
    for key in path.section_keys:
        section=sections[key]['coordinates']
        def agrees(i):
            return (0<=i<=len(points)-len(section) and
                    (points[i:i+len(section)]==[list(c) for c in section] if alternative else
                     all(math.dist(a,z)<=1e-7 for a,z in zip(points[i:i+len(section)],section))))
        suffix=key.split(':')
        if key==str(path.way_id) and len(section)==len(points) and agrees(0):
            start=0
        elif (len(suffix)>=3 and suffix[-2].isdigit() and suffix[-1].isdigit()
              and int(suffix[-1])-int(suffix[-2])+1==len(section) and agrees(int(suffix[-2]))):
            start=int(suffix[-2])
        else:
            starts=[i for i in range(len(points)-len(section)+1) if agrees(i)]
            if len(starts)!=1:raise ValueError('Exact source section occurrence required: '+key)
            start=starts[0]
        offset=pos[start]; limit=pos[start+len(section)-1]
        for b in bindex.get(key,[]):
            if not b['modes'] or not set(b['modes'])<=set(path.modes)&{'on_foot','cycling'}:
                raise ValueError('Binding exceeds admitted source mode: '+key)
            a,z=offset+b['physical_start'],offset+b['physical_end']
            if not offset<=a<z or z>limit+1e-7:
                raise ValueError('Binding exceeds exact source section: '+key)
            result.append({'start':a,'end':z,'target':b['logical_key'],
                'low':b['logical_start'],'high':b['logical_end'],'modes':b['modes']})
    return result

def build(root,out):
    pinsraw=(root/'inputs/input-pins.json').read_bytes();assert sha(pinsraw)==PINS
    pins=json.loads(pinsraw);summaryraw=(root/'plans/summary.json').read_bytes()
    assert sha(summaryraw)=='e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b'
    summary=json.loads(summaryraw);assert len(summary['routes'])==219 and len(summary['held_route_keys'])==34
    # Reuse validated open extents, recovering their exact logical edge identities.
    rows={r['route_key']:r for r in pins['routes']};files={}
    from scripts.ncn_trial.completion_checkpoint import pinned,decode
    for r in pins['files']:files[r['file']]=pinned(root/'inputs',r)
    network=json.loads(decode(files[pins['official_network']],pins['official_network']))
    routes=[];physical=[];all_targets=[];registry={}
    for record in summary['routes']:
        key=record['route_key'];folder=root/'plans'/key.replace(':','-')
        manifest=json.loads((folder/'manifest.json').read_bytes());assert manifest==record
        planraw=gzip.decompress((folder/'plan.json.gz').read_bytes());associationraw=gzip.decompress((folder/'associations.json.gz').read_bytes())
        assert sha(planraw)==manifest['plan_sha256']and sha(associationraw)==manifest['association_sha256']
        plan=json.loads(planraw);assert plan['route_key']==key and plan['reference_version']==manifest['reference_version']and plan['whole_published_route_ready']is False
        from types import SimpleNamespace
        associations=json.loads(associationraw)
        known=[SimpleNamespace(way_id=p['way_id'],coordinates=p['coordinates'],osm_node_ids=p['osm_node_ids'],modes=p['modes'],section_keys=p['section_keys'])for p in associations['paths']]
        alternative=manifest['completion_kind']!='captured_open_main'
        origin=plan['metadata']['projection_origin']if alternative else plan['projection_origin']
        targets={}
        if not alternative:
            row=rows[key];extent_file=root/'inputs'/row['folder']/'open-extent.json'
            extent=json.loads(extent_file.read_bytes())if extent_file.exists()else open_extent(key,network['features'],{'national_reference_sha256':pins['official_network_sha256'],'projection_origin':origin},pins['official_network_sha256'])
            for f in extent['features']:
                props=f['properties'];assert props['OpenStatus']=='Open'and props['RouteCat']=='Main Route'and props['Desc_']in ('OnRoad','TrafficFree')and props['RouteType']+':'+str(props['RouteNo'])==key
            lines=[]
            for f in extent['features']:
                g=f['geometry'];parts=[g['coordinates']]if g['type']=='LineString'else g['coordinates']
                for part in parts:
                    lines.append(LineString([(6371008.8*math.radians(c[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(c[1]-origin[1]))for c in part]))
            union=unary_union(lines);parts=[union]if union.geom_type=='LineString'else union.geoms
            for part in parts:
                for a,z in zip(part.coords,list(part.coords)[1:]):
                    a,z=sorted((tuple(a),tuple(z)))
                    if math.dist(a,z)<=1e-7:continue
                    target='open-edge:'+digest((a,z))
                    coords=[[round(origin[0]+math.degrees(p[0]/(6371008.8*math.cos(math.radians(origin[1])))),10),round(origin[1]+math.degrees(p[1]/6371008.8),10)]for p in (a,z)]
                    targets[target]=coords
            assert set(targets)=={s['key']for s in plan['logical_sections']}
        else:
            # Preserved numbered alternative remains browseable, without an unsupported
            # open-main completion claim. It is excluded from aggregate denominator.
            for p in known:targets['source:'+p.way_id]=[list(c)for c in p.coordinates]
        route={'key':key,'name':key.replace(':',' '),'group':'regional'if key.startswith('RCN:')else 'national','measurable':not alternative,
            'length_m':plan.get('canonical_open_length_m',plan.get('canonical_inventory_length_m')),
            'logical':{s['key']:s['length_m']for s in plan['logical_sections']},'reference_version':plan['reference_version'],
            'scope':plan['scope'],'deferred_ways':plan.get('deferred_alternative_way_ids',[]),'targets':targets}
        routes.append(route)
        if not alternative:all_targets.extend(LineString(c)for c in targets.values())
        bindex={}
        for b in plan['bindings']:bindex.setdefault(b['physical_key'],[]).append(b)
        sections={s['key']:s for s in plan['sections' if alternative else 'physical_sections_metric']}
        hashes={a['way_id']:a['source_geometry_sha256']for a in associations['paths']}
        for p in known:
            coords=[list(c)for c in p.coordinates];pos=positions(coords,origin)
            bindings=compile_path_bindings(p,plan,alternative,sections=sections,bindex=bindex)
            value={'route':key,'way':p.way_id,'coordinates':coords,'nodes':p.osm_node_ids,'modes':p.modes,'source_geometry_sha256':hashes[p.way_id],'origin':origin,'positions':pos,'bindings':bindings}
            identity=digest([coords,p.osm_node_ids]);assert p.way_id not in registry or registry[p.way_id]==identity;registry[p.way_id]=identity
            physical.append(value)
        print(key,len(known),flush=True)
    assert len(registry)==62399
    # Node target intersections/overlaps once offline; never sum overlapping route totals.
    union=unary_union(all_targets);parts=[union]if union.geom_type=='LineString'else union.geoms
    edges=[LineString([a,z])for part in parts for a,z in zip(part.coords,list(part.coords)[1:])if a!=z];tree=STRtree(edges)
    lengths=[positions(list(e.coords),list(e.coords)[0])[1]for e in edges]
    out.mkdir(parents=True,exist_ok=True);dbfile=out/'ncn-national.db';dbfile.unlink(missing_ok=True);(out/'ncn-national.db-journal').unlink(missing_ok=True);db=sqlite3.connect(dbfile);db.execute('PRAGMA journal_mode=OFF');db.execute('PRAGMA synchronous=OFF')
    db.executescript('CREATE TABLE routes(key TEXT PRIMARY KEY,data TEXT); CREATE TABLE route_detail(key TEXT PRIMARY KEY,data TEXT); CREATE TABLE paths(route TEXT,way TEXT,hash TEXT,data TEXT,PRIMARY KEY(route,way)); CREATE INDEX source_way ON paths(way); CREATE INDEX source_hash ON paths(hash); CREATE TABLE nodes(node TEXT,way TEXT,PRIMARY KEY(node,way)); CREATE TABLE network(id INTEGER PRIMARY KEY,length REAL); CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT);')
    for i,length in enumerate(lengths):db.execute('INSERT INTO network VALUES(?,?)',(i,length))
    for route in routes:
        mappings={}
        if route['measurable']:
            for key,coords in route['targets'].items():
                line=LineString(coords);pieces=[]
                from scripts.repair_ncn_network_mapping import pieces_for
                pieces=pieces_for(line,tree,edges,lengths,route['logical'][key])
                assert abs(sum(abs(p[2]-p[1])for p in pieces)-route['logical'][key])<=max(1e-3,route['logical'][key]*1e-7)
                mappings[key]=pieces
        route['network']=mappings;db.execute('INSERT INTO route_detail VALUES(?,?)',(route['key'],gzip.compress(dumps(route).encode(),mtime=0)))
        db.execute('INSERT INTO routes VALUES(?,?)',(route['key'],dumps({k:v for k,v in route.items()if k not in ('logical','targets','network')})))
    for p in physical:
        db.execute('INSERT INTO paths VALUES(?,?,?,?)',(p['route'],p['way'],p['source_geometry_sha256'],gzip.compress(dumps(p).encode(),compresslevel=6,mtime=0)))
        db.executemany('INSERT OR IGNORE INTO nodes VALUES(?,?)',[(str(n),p['way'])for n in p['nodes']])
    meta={'schema_version':2,'scope':'national-verified-open-checkpoint','supported_identities':219,'source_ways':62399,'held_route_keys':summary['held_route_keys'],'network_length_m':sum(lengths),'whole_published_route_ready':False,'attribution':'© OpenStreetMap contributors (ODbL 1.0); official open scope: Walk Wheel Cycle Trust (OGL 3.0).','checkpoint_sha256':sha(summaryraw)}
    db.execute('INSERT INTO meta VALUES(?,?)',('manifest',dumps(meta)));db.commit();db.execute('VACUUM');db.close();meta['sha256']=sha(dbfile.read_bytes());(out/'manifest.json').write_text(dumps(meta)+'\n');print(meta)
    from scripts.finalize_ncn_android import finalize
    finalize(out)
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--checkpoint',type=Path,required=True);a.add_argument('--output',type=Path,required=True);args=a.parse_args();build(args.checkpoint,args.output)
