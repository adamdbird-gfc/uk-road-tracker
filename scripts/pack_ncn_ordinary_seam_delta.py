"""Pack exact seam additions as bounded binary insertion records.

Inputs must be the integrated offset catalogue and independently audited seam
candidate. This utility never changes either input or any published reference.
"""
import argparse,gzip,struct,json,sqlite3
from pathlib import Path
from scripts.ncn_binding_attainability import encoded,digest


def pack(integrated,candidate,output):
    if Path(output).exists():raise ValueError('Fresh delta output required')
    old=sqlite3.connect(Path(integrated).resolve().as_uri()+'?mode=ro',uri=True)
    new=sqlite3.connect(Path(candidate).resolve().as_uri()+'?mode=ro',uri=True)
    path=Path(output);rows=0;added=0
    with gzip.GzipFile(filename=str(path),mode='wb',mtime=0) as out:
     out.write(b'NCNSEAM1\n')
     targets={}
     for route, in new.execute('SELECT key FROM routes ORDER BY key'):
      targets[route]={k:i for i,(k,) in enumerate(new.execute('SELECT key FROM targets WHERE route=? ORDER BY key',(route,)))}
     for route,way,raw in new.execute('SELECT route,way,data FROM paths ORDER BY route,way'):
      p=json.loads(gzip.decompress(raw));o=json.loads(gzip.decompress(old.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,way)).fetchone()[0]));prior=o['bindings'];seq=0;extra=[]
      for i,b in enumerate(p['bindings']):
       if seq<len(prior) and encoded(b)==encoded(prior[seq]):seq+=1;continue
       mask=(1 if 'on_foot' in b['modes'] else 0)+(2 if 'cycling' in b['modes'] else 0)+(4 if b['modes']==['cycling','on_foot'] else 0)
       extra.append(struct.pack('<IIddddB',i,targets[route][b['target']],b['start'],b['end'],b['low'],b['high'],mask))
      assert seq==len(prior),(route,way)
      if not extra:continue
      header=encoded([route,way,digest(prior),len(extra)])
      out.write(struct.pack('<I',len(header)));out.write(header);out.write(b''.join(extra));rows+=1;added+=len(extra)
    old.close();new.close()
    return {'changed_source_rows':rows,'added_bindings':added,'bytes':path.stat().st_size}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--integrated',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(pack(a.integrated,a.candidate,a.output))
