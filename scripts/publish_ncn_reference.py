#!/usr/bin/env python3
"""Publish one verified NCN release by adding to a live Firebase Hosting clone.

Default is a verified preview. --promote releases exactly that verified version.
Rollback uses a higher revision manifest pointing at a retained older digest.
Never perform a full firebase deploy: all existing reference files are preserved.
"""
import argparse,copy,gzip,hashlib,json,os,tempfile
from pathlib import Path
from ncn_reference import MANIFEST,validate,verify_database,sha_file
from preview_ni_reference_data import SITE,version_name
CHANNEL='ncn-reference'
IMMUTABLE={'glob':'/collections/published/ncn/*/catalogue.db.gz','headers':{'Cache-Control':'public, max-age=31536000, immutable','Content-Type':'application/octet-stream'}}

def sha(raw):return hashlib.sha256(raw).hexdigest()

def preservation(before,after,added):
    if after!={**before,**added}:raise ValueError('Existing reference file coverage or hashes changed')

def next_manifest(old,new):
    validate(new)
    if old is not None:
        validate(old)
        if new['revision']<old['revision'] or (new['revision']==old['revision'] and new!=old):
            raise ValueError('Revision decrease or reuse; rollback must use a higher revision')

def configuration(source):
    config=copy.deepcopy(source);headers=config.setdefault('headers',[])
    if IMMUTABLE not in headers:headers.append(IMMUTABLE)
    return config

def local_check(asset,m):
    validate(m)
    if asset.stat().st_size!=m['compressed_bytes'] or sha_file(asset)!=m['compressed_sha256']:raise ValueError('Compressed NCN asset differs')
    # Bound expansion before trusting SQLite, including locally supplied publication inputs.
    with tempfile.TemporaryDirectory() as work:
        database=Path(work)/'catalogue.db'
        with gzip.open(asset,'rb') as source,database.open('wb') as target:
            total=0
            for chunk in iter(lambda:source.read(65536),b''):
                total+=len(chunk)
                if total>m['database_bytes']:raise ValueError('NCN expanded size exceeds manifest')
                target.write(chunk)
        verify_database(database,m)

def served(base,m):
    import requests
    # Hosting upload protocol gzips transport; the file itself is a .gz object.
    # Do not ask requests to auto-decode a Content-Encoding representation here.
    with requests.get(base+'/'+m['filename'],headers={'Accept-Encoding':'identity'},stream=True,timeout=(30,90),allow_redirects=False) as response:
        if response.status_code!=200:raise RuntimeError('NCN serving check HTTP '+str(response.status_code))
        h=hashlib.sha256();total=0
        for chunk in response.iter_content(65536):
            total+=len(chunk)
            if total>m['compressed_bytes']:raise ValueError('Hosted NCN exceeds size pin')
            h.update(chunk)
        if total!=m['compressed_bytes'] or h.hexdigest()!=m['compressed_sha256']:raise ValueError('Hosted NCN compressed bytes differ')
    response=requests.get(base+'/'+MANIFEST,timeout=60,allow_redirects=False)
    if response.status_code!=200 or len(response.content)>32768 or response.json()!=m:raise ValueError('Hosted NCN manifest differs')

def run(args):
    import requests
    from publish_county_build_reference import Publisher
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2.service_account import Credentials
    m=json.loads(args.manifest.read_text());local_check(args.asset,m)
    credentials=json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if credentials.get('project_id')!='roadprints-822cc' or credentials.get('type')!='service_account' or os.environ.get('ROADPRINTS_DATA_HOSTING_SITE')!=SITE:raise ValueError('Unexpected Firebase destination')
    session=AuthorizedSession(Credentials.from_service_account_info(credentials,scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    hosting=Publisher(session);live_path=f'sites/{SITE}/channels/live'
    source=version_name(hosting.request('GET',live_path)['release']['version']['name']);before=hosting.files(source)
    old=None
    if MANIFEST in before:
        response=requests.get('https://'+SITE+'.web.app/'+MANIFEST,timeout=60,allow_redirects=False)
        if response.status_code!=200 or len(response.content)>32768:raise ValueError('Cannot inspect existing NCN manifest')
        old=response.json()
    next_manifest(old,m)
    config=configuration(hosting.request('GET',source).get('config',{}))
    args.work.mkdir(parents=True,exist_ok=True)
    # Firebase Hosting content hashes are SHA256 of gzip-encoded upload bodies.
    # Stream the large already-compressed catalogue through one additional transport layer.
    transport=args.work/'hosting-upload.gz'
    with args.asset.open('rb') as source_file,transport.open('wb') as transport_file,gzip.GzipFile(filename='',fileobj=transport_file,mode='wb',mtime=0) as target_file:
        import shutil;shutil.copyfileobj(source_file,target_file)
    content_hash=sha_file(transport)
    manifest_body=gzip.compress(args.manifest.read_bytes(),mtime=0);manifest_hash=sha(manifest_body)
    added={m['filename']:content_hash,MANIFEST:manifest_hash}
    if m['filename'] in before and before[m['filename']]!=content_hash:raise ValueError('Immutable NCN path already exists with different content')
    target=version_name(hosting.wait(hosting.request('POST',f'sites/{SITE}/versions:clone',json={'sourceVersion':source,'finalize':False}))['name'])
    if hosting.files(target)!=before:raise ValueError('Clone did not preserve all live files')
    populate=hosting.request('POST',target+':populateFiles',json={'files':{'/'+p:h for p,h in added.items()}})
    required=set(populate.get('uploadRequiredHashes',[]))
    if not required<=set(added.values()):raise ValueError('Unexpected upload hashes requested')
    upload=populate.get('uploadUrl','')
    if required and upload!='https://upload-firebasehosting.googleapis.com/upload/'+target+'/files':raise ValueError('Unexpected upload destination')
    # No retry loop: an interrupted upload must be investigated; confirmed hashes are reused.
    for h in required:
        with transport.open('rb') as packed:
            data=packed if h==content_hash else manifest_body
            response=session.post(upload+'/'+h,data=data,headers={'Content-Type':'application/octet-stream'},timeout=(30,600),allow_redirects=False)
            if response.status_code not in (200,201):raise RuntimeError('NCN upload failed HTTP '+str(response.status_code))
    hosting.request('PATCH',target,params={'updateMask':'status,config'},json={'status':'FINALIZED','config':config})
    after=hosting.files(target);preservation(before,after,added)
    if hosting.request('GET',target).get('config',{})!=config:raise ValueError('Hosting configuration changed unexpectedly')
    channel=session.get('https://firebasehosting.googleapis.com/v1beta1/'+f'sites/{SITE}/channels/{CHANNEL}',timeout=60)
    if channel.status_code==404:hosting.request('POST',f'sites/{SITE}/channels',params={'channelId':CHANNEL},json={'ttl':'86400s','retainedReleaseCount':1})
    else:channel.raise_for_status()
    hosting.request('POST',f'sites/{SITE}/channels/{CHANNEL}/releases',params={'versionName':target},json={})
    url=hosting.request('GET',f'sites/{SITE}/channels/{CHANNEL}')['url']
    if not url.startswith('https://'+SITE+'--'+CHANNEL+'-') or not url.endswith('.web.app'):raise ValueError('Unexpected preview origin')
    served(url,m)
    if hosting.request('GET',live_path)['release']['version']['name']!=source:raise ValueError('Live changed during preparation')
    result={'status':'preview_verified','source_version':source,'version':target,'preview_url':url,'preserved_files':len(before)-(MANIFEST in before),'revision':m['revision'],'database_sha256':m['sha256'],'compressed_sha256':m['compressed_sha256']}
    (args.work/'publication.json').write_text(json.dumps(result,indent=2)+'\n')
    if args.promote:
        hosting.request('POST',f'sites/{SITE}/releases',params={'versionName':target},json={'message':'NCN reference revision '+str(m['revision'])+'; preserve all other reference assets'})
        served('https://'+SITE+'.web.app',m)
        if hosting.request('GET',live_path)['release']['version']['name']!=target:raise ValueError('Live changed after release')
        result.update(status='live_verified',url='https://'+SITE+'.web.app/'+m['filename'])
        (args.work/'publication.json').write_text(json.dumps(result,indent=2)+'\n')
    transport.unlink();print(json.dumps(result,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--asset',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--work',type=Path,default=Path('ncn-publication-evidence'));p.add_argument('--promote',action='store_true');run(p.parse_args())
