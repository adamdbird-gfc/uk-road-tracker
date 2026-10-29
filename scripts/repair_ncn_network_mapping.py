"""Resolve sub-micrometre GEOS noding arithmetic without admitting geometry."""
import json,sqlite3,math
from pathlib import Path
from shapely.geometry import LineString,Point
from shapely.ops import unary_union
from shapely.strtree import STRtree

def pieces_for(line,tree,edges,lengths,logical_length):
    pieces=[]
    for raw in tree.query(line.buffer(1e-11)):
        i=int(raw);edge=edges[i];overlap=line.intersection(edge)
        exact=overlap.geom_type=='LineString'and overlap.length>1e-12
        if not exact:
            # Only an existing noded union edge, collinear to numerical precision,
            # contained in this original bounded target at < 0.002 mm tolerance.
            a,z=list(line.coords);x,y=list(edge.coords);dx,dz=z[0]-a[0],z[1]-a[1];ex,ez=y[0]-x[0],y[1]-x[1]
            if abs(dx*ez-dz*ex)>line.length*edge.length*1e-10 or not line.buffer(1e-11).covers(edge):continue
        a,z=list(edge.coords);low=line.project(Point(a))/line.length;high=line.project(Point(z))/line.length
        if abs(high-low)>1e-12:pieces.append([i,low*logical_length,high*logical_length,lengths[i]])
    return pieces

def repair(folder):
    import shutil
    original=Path(folder)/'ncn-national.db';working=Path(folder)/'ncn-repair.db';shutil.copyfile(original,working)
    db=sqlite3.connect(working);db.execute('pragma journal_mode=OFF');db.execute('pragma synchronous=OFF')
    rows=db.execute('select route,key,length,coords,network from targets order by rowid').fetchall()
    measurable={k for k,d in db.execute('select key,data from routes')if json.loads(d)['measurable']}
    lines=[LineString(json.loads(r[3]))for r in rows if r[0]in measurable];union=unary_union(lines);parts=[union]if union.geom_type=='LineString'else union.geoms
    edges=[LineString([a,z])for part in parts for a,z in zip(part.coords,list(part.coords)[1:])if a!=z];tree=STRtree(edges)
    from scripts.build_ncn_national import positions
    lengths=[positions(list(e.coords),list(e.coords)[0])[1]for e in edges]
    saved=dict(db.execute('select id,length from network'));assert len(saved)==len(lengths)and all(abs(saved[i]-n)<1e-5 for i,n in enumerate(lengths)), 'Canonical physical edge order changed'
    repaired=0
    for route,key,length,coords,network in rows:
        if route not in measurable:continue
        old=json.loads(network)
        if abs(sum(abs(p[2]-p[1])for p in old)-length)<=max(1e-3,length*1e-7):continue
        new=pieces_for(LineString(json.loads(coords)),tree,edges,lengths,length)
        assert abs(sum(abs(p[2]-p[1])for p in new)-length)<=max(1e-3,length*1e-7),(route,key,length,new)
        db.execute('update targets set network=? where route=? and key=?',(json.dumps(new,separators=(',',':')),route,key));repaired+=1
    db.commit();assert db.execute('pragma quick_check').fetchone()[0]=='ok';db.close();working.replace(original);print('Repaired',repaired,'bounded target mappings',flush=True)
    import hashlib
    manifest=Path(folder)/'manifest.json';m=json.loads(manifest.read_text());m['sha256']=hashlib.sha256((Path(folder)/'ncn-national.db').read_bytes()).hexdigest();manifest.write_text(json.dumps(m,separators=(',',':'))+'\n')
    from scripts.finalize_ncn_android import pack
    pack(folder)
if __name__=='__main__':
    import sys;repair(sys.argv[1])
