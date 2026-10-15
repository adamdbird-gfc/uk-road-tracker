#!/usr/bin/env python3
"""Stage NI additions and a combined manifest without altering reviewed GB files."""
import argparse,gzip,hashlib,json,re
from pathlib import Path

GB_MANIFEST_SHA256='054382460295352450cfa61bb7d954ab0f9852c5319d127ec189a1702e506c39'
NI_MANIFEST_SHA256='8d288f135f11a469b23443349ee7b4e704a62d8a2ef5c2ee50c9b49a23a53a28'
NI_BOUNDARY_SHA256='2320848e0ccfe9f13e4c0c2b25ae047d5217ceff10d27ee3d74ac07badb344c0'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def stage(gb_manifest,boundary_dir,ni_dir,output):
    gb_manifest,boundary_dir,ni_dir,output=map(Path,(gb_manifest,boundary_dir,ni_dir,output))
    if output.exists() and any(output.iterdir()):raise ValueError('Additions output must be empty')
    raw=gb_manifest.read_bytes()
    if sha(raw)!=GB_MANIFEST_SHA256:raise ValueError('Reviewed GB manifest changed')
    manifest=json.loads(raw);gb={r['code']:r for r in manifest['inventories']}
    if len(gb)!=8545 or len(gb)!=len(manifest['inventories']) or any(c.startswith('N') for c in gb):raise ValueError('Invalid GB baseline')
    ni_raw=(ni_dir/'candidate-manifest.json').read_bytes()
    if sha(ni_raw)!=NI_MANIFEST_SHA256:raise ValueError('NI candidate inventories changed')
    ni=json.loads(ni_raw)['inventories'];bound_raw=gzip.decompress((boundary_dir/'ni-boundary-candidates.geojson.gz').read_bytes())
    if sha(bound_raw)!=NI_BOUNDARY_SHA256:raise ValueError('NI boundary snapshot changed')
    features=json.loads(bound_raw)['features'];by_code={f['properties']['code']:f for f in features}
    if len(ni)!=527 or len(by_code)!=527 or {r['code'] for r in ni}!=set(by_code):raise ValueError('NI catalogue and files differ')
    files={};rows=dict(gb)
    for r in ni:
        code=r['code'];f=by_code[code]
        if not re.fullmatch(r'N11\d{6}',code) or code in rows:raise ValueError('Duplicate NI identity')
        payload=(ni_dir/(code+'.json')).read_bytes();data=json.loads(payload)
        if sha(payload)!=r['sha256'] or len(payload)!=r['bytes'] or data['name']!=f['properties']['name'] or data['count']!=r['count'] or data['code']!=code:
            raise ValueError('NI inventory identity/count/hash changed')
        files['settlement-inventories-v1/'+code+'.json']=payload
        boundary={'type':'Feature','properties':{'code':code,'name':data['name']},'geometry':f['geometry']}
        files['settlement-inventories-v1/'+code+'-boundary.geojson']=(json.dumps(boundary,separators=(',',':'),ensure_ascii=False)+'\n').encode()
        rows[code]={'code':code,'status':'available','count':data['count']}
    combined=dict(manifest,inventories=[rows[c] for c in sorted(rows)])
    if any(rows[c]!=v for c,v in gb.items()):raise ValueError('GB manifest entry changed')
    files['settlement-inventory-manifest-v1.json']=(json.dumps(combined,indent=2)+'\n').encode()
    output.mkdir(parents=True,exist_ok=True);report={'schema_version':1,'files':[]}
    for name,payload in sorted(files.items()):
        path=output/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(payload)
        report['files'].append({'path':name,'bytes':len(payload),'sha256':sha(payload)})
    report['total_bytes']=sum(r['bytes'] for r in report['files'])
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--gb-manifest',required=True);p.add_argument('--boundaries',required=True);p.add_argument('--ni-dir',required=True);p.add_argument('--output',required=True);p.add_argument('--report',required=True);a=p.parse_args()
    r=stage(a.gb_manifest,a.boundaries,a.ni_dir,a.output);Path(a.report).write_text(json.dumps(r,indent=2)+'\n');print('Staged',len(r['files']),'new or replaced public files;',r['total_bytes'],'bytes')
