#!/usr/bin/env python3
"""Check whether the existing deployment identity can use a file shared on Drive."""
import json,os
from pathlib import Path
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials

def main():
    identity=json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if identity.get('project_id')!='roadprints-822cc':raise ValueError('Unexpected deployment project')
    session=AuthorizedSession(Credentials.from_service_account_info(identity,scopes=['https://www.googleapis.com/auth/drive.readonly']))
    response=session.get('https://www.googleapis.com/drive/v3/files',params={'pageSize':1,'fields':'kind'},timeout=30)
    result={'status':'drive_transport_available' if response.status_code==200 else 'drive_transport_blocked','http':response.status_code,'deployment_service_account':identity['client_email']}
    if response.status_code!=200:
        error=response.json().get('error',{});result['reason']=error.get('message','Drive API unavailable')
    out=Path('ncn-publication-evidence');out.mkdir(exist_ok=True);(out/'access.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
    if response.status_code!=200:raise SystemExit(1)
if __name__=='__main__':main()
