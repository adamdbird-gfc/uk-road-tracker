"""Bound SQLite rows and add display-only simplified overviews to compiler output."""
import gzip,json,sqlite3,hashlib
from pathlib import Path
from shapely.geometry import LineString
from shapely.ops import unary_union,linemerge

def finalize(folder):
    folder=Path(folder);path=folder/'ncn-national.db';db=sqlite3.connect(path);db.execute('PRAGMA journal_mode=OFF');db.execute('PRAGMA synchronous=OFF')
    db.executescript('CREATE TABLE targets(route TEXT,key TEXT,length REAL,coords TEXT,network TEXT,PRIMARY KEY(route,key)); CREATE TABLE overview(key TEXT PRIMARY KEY,data BLOB);')
    for key,raw in db.execute('SELECT key,data FROM route_detail').fetchall():
        if isinstance(raw,bytes):raw=gzip.decompress(raw)
        route=json.loads(raw);geometries=[]
        for target,coords in route['targets'].items():
            db.execute('INSERT INTO targets VALUES(?,?,?,?,?)',(key,target,route['logical'].get(target,0),json.dumps(coords,separators=(',',':')),json.dumps(route['network'].get(target,[]),separators=(',',':'))))
            geometries.append(LineString(coords))
        merged=unary_union(geometries)
        if merged.geom_type=='MultiLineString':merged=linemerge(merged)
        parts=[merged]if merged.geom_type=='LineString'else merged.geoms
        lines=[list(part.simplify(.00015,preserve_topology=True).coords)for part in parts if part.length>0]
        db.execute('INSERT INTO overview VALUES(?,?)',(key,gzip.compress(json.dumps(lines,separators=(',',':')).encode(),mtime=0)))
    for rowid,raw in db.execute('SELECT rowid,data FROM paths'):
        if isinstance(raw,str):db.execute('UPDATE paths SET data=? WHERE rowid=?',(gzip.compress(raw.encode(),compresslevel=6,mtime=0),rowid))
    db.execute('DROP TABLE route_detail');db.commit();db.execute('VACUUM');db.close()
    m=json.loads((folder/'manifest.json').read_text());m['sha256']=hashlib.sha256(path.read_bytes()).hexdigest();(folder/'manifest.json').write_text(json.dumps(m,separators=(',',':'))+'\n')
    print('Indexed',path.stat().st_size,'bytes',m['sha256'])
    pack(folder)
def pack(folder):
    folder=Path(folder);path=folder/'ncn-national.db';manifest=folder/'manifest.json';m=json.loads(manifest.read_text());m['database_bytes']=path.stat().st_size
    compressed=folder/'catalogue.db.gz'
    with path.open('rb')as source,gzip.GzipFile(filename=str(compressed),mode='wb',mtime=0)as target:
        import shutil;shutil.copyfileobj(source,target)
    from scripts.ncn_reference import sha_file,validate
    m.pop('asset_parts',None);m.update(sha256=sha_file(path),compressed_sha256=sha_file(compressed),compressed_bytes=compressed.stat().st_size,revision=m.get('revision',1))
    m['filename']='collections/published/ncn/'+m['sha256']+'/catalogue.db.gz';validate(m)
    manifest.write_text(json.dumps(m,separators=(',',':'))+'\n')
    print('External catalogue ready:',compressed,'; copy only manifest.json into Android assets')
if __name__=='__main__':
    import sys;finalize(sys.argv[1])
