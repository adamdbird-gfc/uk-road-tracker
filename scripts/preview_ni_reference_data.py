#!/usr/bin/env python3
"""Add reviewed NI files to a server-side clone; release only an expiring preview."""
import argparse,gzip,hashlib,json,os,re,time
from pathlib import Path
from urllib.parse import urlsplit
from verify_gb_preview import verify_batches

SITE='roadprints-data-822cc'
CHANNEL='uk-ni-reviewed-20261015'
API='https://firebasehosting.googleapis.com/v1beta1/'
EXPECTED_REPORT='8721b48b05b791bdca92b18a65b4da8acb5d72234b078a5e08207a8b6b78c6e0'
EXPECTED_GB_REPORT='33401ac0431acc84e79f35a95e70ce27cd8557c913dbe8d050a38fba6fe83015'
MANIFEST='settlement-inventory-manifest-v1.json'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def version_name(name):
    if not re.fullmatch(r'sites/'+SITE+r'/versions/[A-Za-z0-9_-]+',name):raise ValueError('Unexpected Hosting version')
    return name


def source_coverage_check(before,expected):
    missing=sorted(set(expected)-set(before));extra=sorted(set(before)-set(expected))
    if missing or not set(extra)<={'__/firebase/init.js','__/firebase/init.json'}:
        raise ValueError('Live dataset differs from reviewed GB file coverage: '+json.dumps({'actual':len(before),'expected':len(expected),'missing_sample':missing[:12],'extra_sample':extra[:12]}))


def preservation_check(before,after,added):
    if set(after)!=(set(before)|set(added)):raise ValueError('Preview file coverage changed unexpectedly')
    for path,h in before.items():
        if path!=MANIFEST and after.get(path)!=h:raise ValueError('Existing GB or collection file changed')
    if any(after.get(path)!=h for path,h in added.items()):raise ValueError('NI additions differ from reviewed upload hashes')


class Hosting:
    def __init__(self,session):self.session=session
    def request(self,method,path,allow404=False,**kwargs):
        # There is intentionally no operation that releases to the live channel.
        if method!='GET' and (path==f'sites/{SITE}/releases' or '/channels/live' in path):
            raise ValueError('Live publication is not permitted by this preview tool')
        response=self.session.request(method,API+path,timeout=60,allow_redirects=False,**kwargs)
        if allow404 and response.status_code==404:return None
        if response.status_code not in (200,201):raise RuntimeError('Hosting request failed: '+method+' '+path+' HTTP '+str(response.status_code))
        return response.json()
    def files(self,version):
        version_name(version);result={};token=None;seen=set()
        while True:
            params={'pageSize':1000,'status':'ACTIVE'}
            if token:params['pageToken']=token
            page=self.request('GET',version+'/files',params=params)
            for row in page.get('files',[]):
                path=row['path'].lstrip('/');h=row['hash']
                if path in result or not re.fullmatch('[a-f0-9]{64}',h):raise ValueError('Invalid version file list')
                result[path]=h
            token=page.get('nextPageToken')
            if not token:return result
            if token in seen:raise ValueError('Repeated Hosting page token')
            seen.add(token)
    def wait(self,operation):
        for _ in range(90):
            if operation.get('error'):raise RuntimeError('Hosting clone operation failed')
            if operation.get('done'):return operation['response']
            name=operation['name']
            if not re.fullmatch(r'(projects|sites)/[A-Za-z0-9_./-]+',name):raise ValueError('Unexpected operation name')
            time.sleep(2);operation=self.request('GET',name)
        raise TimeoutError('Hosting clone did not finish')


def run(args):
    import requests
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2.service_account import Credentials
    from verify_firebase_reference_data import verify
    from release_reviewed_gb import Fetcher
    raw=args.report.read_bytes()
    if sha(raw)!=EXPECTED_REPORT:raise ValueError('Reviewed NI additions changed')
    report=json.loads(raw)
    if len(report['files'])!=1055:raise ValueError('Incomplete NI additions')
    old_raw=gzip.decompress(Path('.github/gb-live/hosting-stage-report.json.gz').read_bytes())
    if sha(old_raw)!=EXPECTED_GB_REPORT:raise ValueError('Reviewed GB report changed')
    old_report=json.loads(old_raw);expected_old={r['path']:r for r in old_report['files']}
    packed={};added={}
    for row in report['files']:
        path=row['path']
        if path!=MANIFEST and not re.fullmatch(r'settlement-inventories-v1/N11\d{6}(?:-boundary\.geojson|\.json)',path):
            raise ValueError('Unexpected addition path')
        payload=(args.public/path).read_bytes()
        if len(payload)!=row['bytes'] or sha(payload)!=row['sha256']:raise ValueError('Staged addition changed')
        compressed=gzip.compress(payload,compresslevel=9,mtime=0);h=sha(compressed);packed[h]=compressed;added[path]=h
    credentials=json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if credentials.get('project_id')!='roadprints-822cc' or credentials.get('type')!='service_account' or os.environ['ROADPRINTS_DATA_HOSTING_SITE']!=SITE:
        raise ValueError('Unexpected Hosting credential or site')
    session=AuthorizedSession(Credentials.from_service_account_info(credentials,scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    hosting=Hosting(session);live=hosting.request('GET',f'sites/{SITE}/channels/live');source=version_name(live['release']['version']['name'])
    before=hosting.files(source)
    source_coverage_check(before,expected_old)
    source_config=hosting.request('GET',source).get('config',{})
    fetch=Fetcher();verify('https://'+SITE+'.web.app',{'schema_version':1,'files':[expected_old[MANIFEST]]},fetch=fetch)
    args.work.mkdir(parents=True,exist_ok=True)
    clone=hosting.wait(hosting.request('POST',f'sites/{SITE}/versions:clone',json={'sourceVersion':source,'finalize':False}))
    target=version_name(clone['name'])
    if hosting.files(target)!=before:raise ValueError('Server-side clone differs from its source')
    populate=hosting.request('POST',target+':populateFiles',json={'files':{'/'+p:h for p,h in added.items()}})
    required=set(populate.get('uploadRequiredHashes',[]))
    if not required<=set(packed):raise ValueError('Unexpected upload content requested')
    upload=populate.get('uploadUrl','')
    if required and upload!='https://upload-firebasehosting.googleapis.com/upload/'+target+'/files':
        raise ValueError('Unexpected upload destination')
    for i,h in enumerate(sorted(required),1):
        response=session.post(upload+'/'+h,data=packed[h],headers={'Content-Type':'application/octet-stream'},timeout=60,allow_redirects=False)
        if response.status_code not in (200,201):raise RuntimeError('NI content upload failed HTTP '+str(response.status_code))
        if i%100==0:print('Uploaded',i,'/',len(required),'new content objects',flush=True)
    finalized=hosting.request('PATCH',target,params={'updateMask':'status'},json={'status':'FINALIZED'})
    if finalized.get('status')!='FINALIZED' or finalized.get('config',{})!=source_config:raise ValueError('Preview configuration or finalization changed')
    after=hosting.files(target);preservation_check(before,after,added)
    if hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name']!=source:
        raise ValueError('Live version changed during preview preparation')
    channel=hosting.request('GET',f'sites/{SITE}/channels/{CHANNEL}',allow404=True)
    if channel is None:channel=hosting.request('POST',f'sites/{SITE}/channels',params={'channelId':CHANNEL},json={'ttl':'86400s','retainedReleaseCount':1})
    else:channel=hosting.request('PATCH',f'sites/{SITE}/channels/{CHANNEL}',params={'updateMask':'ttl'},json={'ttl':'86400s'})
    hosting.request('POST',f'sites/{SITE}/channels/{CHANNEL}/releases',params={'versionName':target},json={'message':'Reviewed NI additions; preserve the verified GB dataset'})
    channel=hosting.request('GET',f'sites/{SITE}/channels/{CHANNEL}');url=channel['url']
    if not url.startswith('https://'+SITE+'--'+CHANNEL+'-') or urlsplit(url).hostname is None or not url.endswith('.web.app'):
        raise ValueError('Unexpected preview origin')
    result={'status':'deployed_pending_serving_checks','url':url,'source_live_version':source,'preview_version':target,
        'gb_and_collection_files_preserved':len(expected_old)-1,'firebase_managed_files_preserved':len(before)-len(expected_old),'total_files':len(after),'total_settlements':9072,
        'new_files':1054,'replaced_manifests':1,'additions_report_sha256':EXPECTED_REPORT,
        'source_file_hashes_sha256':sha(json.dumps(before,sort_keys=True).encode()),
        'preview_file_hashes_sha256':sha(json.dumps(after,sort_keys=True).encode()),
        'inherited_config_sha256':sha(json.dumps(source_config,sort_keys=True).encode())}
    (args.work/'preview-result.json').write_text(json.dumps(result,indent=2)+'\n')
    # Verify all new/replaced assets. Old assets are inherited with exact Hosting hashes/configuration.
    checked=verify_batches(url,report,verify,fetch,args.work/'serving-progress.json')
    samples=[r for r in old_report['files'] if r['path'].startswith('collections/') or any(c in r['path'] for c in ['E63005058','S45000364','E63006323'])]
    verify(url,{'schema_version':1,'files':samples},fetch=fetch)
    if hosting.request('GET',f'sites/{SITE}/channels/{CHANNEL}')['release']['version']['name']!=target:
        raise ValueError('Preview changed during checks')
    if hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name']!=source:
        raise ValueError('Live version changed during checks')
    result.update(status='preview_verified',serving_checks=checked,unchanged_gb_sample_files=len(samples))
    (args.work/'preview-result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--report',type=Path,required=True);p.add_argument('--public',type=Path,required=True);p.add_argument('--work',type=Path,default=Path('ni-preview-evidence'));a=p.parse_args()
    try:run(a)
    except Exception as e:
        print('NI preview stopped:',type(e).__name__,str(e) if type(e) in (ValueError,RuntimeError) else '',flush=True);raise SystemExit(1)
