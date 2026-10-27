#!/usr/bin/env python3
"""Verify the APK's local NCN pin; remote retrieval requires explicit opt-in."""
import argparse,gzip,json,os,urllib.request
from pathlib import Path
from ncn_reference import BASE,validate,verify_database,sha_file

def prepare(manifest,directory,allow_firebase_download=False):
    m=validate(json.loads(manifest.read_text()));directory.mkdir(parents=True,exist_ok=True)
    database=directory/'catalogue.db'
    if database.exists():
        try:verify_database(database,m);return database
        except ValueError:pass
    if not allow_firebase_download:
        raise RuntimeError('Pinned NCN reference missing or invalid; offline mode forbids Firebase access. Provision a verified local reference or explicitly authorise --allow-firebase-download.')
    packed=directory/'catalogue.db.gz';temporary=directory/'catalogue.tmp'
    request=urllib.request.Request(BASE+m['filename'],headers={'Accept-Encoding':'identity'})
    with urllib.request.urlopen(request,timeout=60) as source,packed.open('wb') as target:
        if source.url!=BASE+m['filename']:raise ValueError('Unexpected NCN redirect')
        total=0
        for chunk in iter(lambda:source.read(65536),b''):
            total+=len(chunk)
            if total>m['compressed_bytes']:raise ValueError('NCN compressed limit exceeded')
            target.write(chunk)
    if total!=m['compressed_bytes'] or sha_file(packed)!=m['compressed_sha256']:raise ValueError('NCN compressed integrity failure')
    with gzip.open(packed,'rb') as source,temporary.open('wb') as target:
        total=0
        for chunk in iter(lambda:source.read(65536),b''):
            total+=len(chunk)
            if total>m['database_bytes']:raise ValueError('NCN decoded limit exceeded')
            target.write(chunk)
    verify_database(temporary,m);temporary.replace(database);packed.unlink();return database

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--directory',type=Path,default=Path('.cache/ncn'));p.add_argument('--allow-firebase-download',action='store_true');a=p.parse_args()
    path=prepare(Path('android-prototype/app/src/main/assets/ncn-national/manifest.json'),a.directory,a.allow_firebase_download).resolve()
    if os.environ.get('GITHUB_ENV'):
        with open(os.environ['GITHUB_ENV'],'a') as target:target.write('NCN_TEST_DATABASE='+str(path)+'\n')
    print('Verified external NCN test reference:',path)
