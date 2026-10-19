#!/usr/bin/env python3
"""Verify the established Actions Hosting deployment identity without exporting it."""
import hashlib,json,os
from pathlib import Path
from google.auth.transport.requests import AuthorizedSession
from google.oauth2.service_account import Credentials
from preview_ni_reference_data import SITE,version_name
from publish_county_build_reference import Publisher

def main():
    raw=os.environ.get('FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON','')
    if not raw:raise RuntimeError('Existing FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON is unavailable to this Actions job')
    identity=json.loads(raw)
    if identity.get('type')!='service_account' or identity.get('project_id')!='roadprints-822cc' or os.environ.get('ROADPRINTS_DATA_HOSTING_SITE')!=SITE:raise ValueError('Existing Hosting identity/site configuration differs')
    session=AuthorizedSession(Credentials.from_service_account_info(identity,scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    hosting=Publisher(session);source=version_name(hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name'])
    files=hosting.files(source)
    county='build-references/historic-counties/b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850/boundaries.bin'
    if county not in files or 'settlement-inventory-manifest-v1.json' not in files:raise ValueError('Existing public reference coverage differs')
    # The same secret/identity that published these assets can be used directly;
    # no local key download, account replacement or new Firebase project.
    out=Path('ncn-publication-evidence');out.mkdir(exist_ok=True)
    result={'status':'existing_hosting_authentication_verified','project':'roadprints-822cc','site':SITE,'source_version':source,'files':len(files),'original_county_asset_present':True,'identity_sha256':hashlib.sha256(identity['client_email'].encode()).hexdigest(),'credential_source':'GitHub Actions secret FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'}
    (out/'access.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))

if __name__=='__main__':main()
