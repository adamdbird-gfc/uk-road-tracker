#!/usr/bin/env python3
"""Fetch one privately shared catalogue using the existing Hosting service account."""
import json,os,re
from pathlib import Path
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials
from ncn_reference import validate,sha_file

def request():
    if os.environ['GITHUB_EVENT_NAME']=='workflow_dispatch':return json.load(open(os.environ['GITHUB_EVENT_PATH']))['inputs']
    return json.loads(Path('.github/ncn-reference/request.json').read_text())

def main():
    file_id=request().get('drive_file_id','')
    if not re.fullmatch(r'[A-Za-z0-9_-]{10,200}',file_id):raise ValueError('Invalid prepared Drive file identifier')
    manifest=validate(json.loads(Path('android-prototype/app/src/main/assets/ncn-national/manifest.json').read_text()))
    identity=json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if identity.get('project_id')!='roadprints-822cc':raise ValueError('Unexpected deployment identity')
    session=AuthorizedSession(Credentials.from_service_account_info(identity,scopes=['https://www.googleapis.com/auth/drive.readonly']))
    path='https://www.googleapis.com/drive/v3/files/'+file_id
    metadata=session.get(path,params={'fields':'id,size,mimeType'},timeout=30)
    if metadata.status_code!=200:raise RuntimeError('Prepared private Drive file unavailable: HTTP '+str(metadata.status_code))
    if int(metadata.json().get('size',0))!=manifest['compressed_bytes']:raise ValueError('Prepared Drive file size differs from pinned catalogue')
    target=Path('catalogue.db.gz');temporary=target.with_suffix('.part')
    with session.get(path,params={'alt':'media'},stream=True,timeout=(30,90)) as source,temporary.open('wb') as output:
        if source.status_code!=200:raise RuntimeError('Prepared Drive download failed HTTP '+str(source.status_code))
        total=0
        for chunk in source.iter_content(65536):
            total+=len(chunk)
            if total>manifest['compressed_bytes']:raise ValueError('Prepared Drive download exceeds pin')
            output.write(chunk)
    if total!=manifest['compressed_bytes'] or sha_file(temporary)!=manifest['compressed_sha256']:raise ValueError('Prepared Drive catalogue checksum differs')
    temporary.replace(target);print('Private prepared catalogue downloaded and checksum verified; no public staging link needed')

if __name__=='__main__':main()
