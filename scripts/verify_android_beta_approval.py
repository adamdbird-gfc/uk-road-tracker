#!/usr/bin/env python3
"""Fail closed before credentials: explicit owner approval of one verified artifact."""
import hashlib,io,json,os,re,urllib.request,zipfile
from pathlib import Path

def validate(event,actor,owner,approval,source,digest,run):
    if event!='workflow_dispatch' or actor!=owner:raise ValueError('Manual project owner approval required')
    if not re.fullmatch('[0-9a-f]{40}',source) or approval!='APPROVE_BETA '+source:raise ValueError('Approval must name the exact source commit')
    if not re.fullmatch('[0-9a-f]{64}',digest):raise ValueError('Exact APK checksum required')
    if run.get('head_sha')!=source or run.get('conclusion')!='success' or run.get('path')!='.github/workflows/android-development-request.yml':raise ValueError('Expected successful private development build at approved commit')

def main():
    repo=os.environ['GITHUB_REPOSITORY'];run_id=os.environ['BUILD_RUN_ID']
    if not re.fullmatch('[0-9]+',run_id):raise ValueError('Invalid run ID')
    class SafeRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,req,fp,code,msg,headers,newurl):
            redirected=super().redirect_request(req,fp,code,msg,headers,newurl)
            if redirected is not None:redirected.remove_header('Authorization')
            return redirected
    def get(path):
        req=urllib.request.Request('https://api.github.com/repos/'+repo+path,headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json'})
        with urllib.request.build_opener(SafeRedirect()).open(req,timeout=90)as response:return response.read()
    run=json.loads(get('/actions/runs/'+run_id));source=os.environ['SOURCE_SHA'];digest=os.environ['APK_SHA256']
    validate(os.environ['GITHUB_EVENT_NAME'],os.environ['GITHUB_ACTOR'],os.environ['GITHUB_REPOSITORY_OWNER'],os.environ['APPROVAL'],source,digest,run)
    artifacts=json.loads(get('/actions/runs/'+run_id+'/artifacts'))['artifacts'];matches=[a for a in artifacts if a['name']=='roadprints-capture-prototype-debug' and not a['expired']]
    if len(matches)!=1:raise ValueError('Expected one unexpired APK artifact')
    with zipfile.ZipFile(io.BytesIO(get('/actions/artifacts/'+str(matches[0]['id'])+'/zip')))as z:
        names=z.namelist()
        if names!=['app-debug.apk']:raise ValueError('Unexpected artifact contents')
        apk=z.read(names[0])
    if hashlib.sha256(apk).hexdigest()!=digest:raise ValueError('APK differs from explicitly approved checksum')
    Path('approved-apk').mkdir();Path('approved-apk/app-debug.apk').write_bytes(apk)
    with open(os.environ['GITHUB_STEP_SUMMARY'],'a')as out:out.write(f'Approved source: {source}\n\nRun: {run_id}; artifact: {matches[0]["id"]}\n\nAPK SHA-256: {digest}\n')
if __name__=='__main__':main()
