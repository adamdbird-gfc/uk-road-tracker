#!/usr/bin/env python3
"""Pack HCT Definition A full-resolution WGS84 shapes; pip install pyshp shapely.
Usage: python package_historic_counties.py SOURCE_ZIP ASSET_DIRECTORY
No topology simplification: retain all vertices rounded to 1e-6 degrees (~0.11 m).
County gzip chunks allow bounded lazy reads rather than a UK-sized JSON allocation.
"""
import gzip, hashlib, io, json, pathlib, struct, sys, zipfile
import shapefile
from shapely.geometry import shape

def uint(value):
    out=bytearray()
    while value>=128: out.append((value&127)|128); value>>=7
    out.append(value); return out

def signed(value): return uint(value*2 if value>=0 else -value*2-1)

def main():
    source,out=pathlib.Path(sys.argv[1]),pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
    expected=json.loads((out/'catalogue.json').read_text()) if (out/'catalogue.json').exists() else None
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    if expected and digest!=expected['source_sha256']: raise ValueError('County source changed; review and version the reference before building')
    z=zipfile.ZipFile(source)
    def part(ext): return io.BytesIO(z.read(next(n for n in z.namelist() if n.lower().endswith(ext))))
    r=shapefile.Reader(shp=part('.shp'),shx=part('.shx'),dbf=part('.dbf'))
    assert len(r)==92
    northern={'ANM','ARH','DWN','FRM','LDR','TYN'}
    welsh={'AGL','BRN','CRN','CRD','CRM','DBH','FLT','GLM','MRN','MNM','MTG','PMB','RDN'}
    scottish={'ABN','ANG','ARG','AYS','BNF','BRW','BTE','CTN','CLM','CRT','DMF','DUN','ELT','FFE','INS','KNC','KNR','KCB','LNK','MLT','MOY','NRN','ORN','PBS','PRT','RNF','RSS','RXB','SKK','SHT','STL','SRL','WLT','WGT'}
    metadata=[];offset=0;vertices=0
    with (out/'boundaries.bin').open('wb') as binary:
        for row in r.iterShapeRecords():
            rec=row.record.as_dict();s=row.shape;code=rec['HCS_CODE']
            geom=shape(s.__geo_interface__);assert geom.is_valid,code
            rings=[]
            ends=list(s.parts)+[len(s.points)]
            for a,b in zip(ends,ends[1:]):
                ring=[]
                for x,y in s.points[a:b]:
                    p=(round(x*1e6),round(y*1e6))
                    if not ring or p!=ring[-1]:ring.append(p)
                if len(ring)>1 and ring[0]==ring[-1]:ring.pop()
                if len(ring)>=3:rings.append(ring)
            raw=bytearray(uint(len(rings)))
            for ring in rings:
                raw+=uint(len(ring));prev=(0,0)
                for p in ring:
                    raw+=signed(p[0]-prev[0])+signed(p[1]-prev[1]);prev=p
                vertices+=len(ring)
            data=gzip.compress(raw,compresslevel=9,mtime=0);binary.write(data)
            nation='Northern Ireland' if code in northern else 'Wales' if code in welsh else 'Scotland' if code in scottish else 'England'
            q=geom.representative_point()
            metadata.append(dict(code=code,name=rec['NAME'],nation=nation,bbox=[min(p[0] for ring in rings for p in ring)/1e6,min(p[1] for ring in rings for p in ring)/1e6,max(p[0] for ring in rings for p in ring)/1e6,max(p[1] for ring in rings for p in ring)/1e6],offset=offset,length=len(data),interior=[q.x,q.y]))
            offset+=len(data)
    assert len({c['code'] for c in metadata})==92
    counts={n:sum(c['nation']==n for c in metadata) for n in ['England','Scotland','Wales','Northern Ireland']}
    assert list(counts.values())==[39,34,13,6],counts
    manifest=dict(format=1,dataset='HCT Definition A WGS84 full resolution',version='2026-10-06',source='https://county-borders.co.uk/UKDefinitionA_WG84_Full_Resolution.zip',source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),data_sha256=hashlib.sha256((out/'boundaries.bin').read_bytes()).hexdigest(),coordinate_scale=1000000,counties=metadata)
    (out/'catalogue.json').write_text(json.dumps(manifest,separators=(',',':'))+'\n')
    print(dict(counts=counts,vertices=vertices,compressed_bytes=offset))
if __name__=='__main__':main()
