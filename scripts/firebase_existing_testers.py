#!/usr/bin/env python3
"""Read existing Roadprints testers; stdout is redirected to a private temporary file."""
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote


def existing_emails(session, project_number):
    parent=f'projects/{project_number}'
    emails=set();token='';seen=set()
    while True:
        response=session.get(f'https://firebaseappdistribution.googleapis.com/v1/{parent}/testers',
                             params={'pageSize':100,'pageToken':token},timeout=30)
        if response.status_code!=200:
            raise ValueError(f'Existing tester lookup failed (HTTP {response.status_code})')
        data=response.json()
        for tester in data.get('testers',[]):
            name=tester.get('name','');prefix=parent+'/testers/'
            email=unquote(name[len(prefix):]) if name.startswith(prefix) else ''
            if not re.fullmatch(r'[^\s,@/]+@[^\s,@/]+',email):
                raise ValueError('Invalid existing tester identity')
            emails.add(email)
        token=data.get('nextPageToken','')
        if not token:break
        if token in seen:raise ValueError('Repeated tester page token')
        seen.add(token)
    if not emails:raise ValueError('No existing testers found; no release was distributed')
    if len(emails)>999:raise ValueError('Existing testers exceed the single release distribution limit')
    return sorted(emails)


if __name__=='__main__':
    import google.auth
    from google.auth.transport.requests import AuthorizedSession
    config=json.loads(Path(sys.argv[1]).read_text())['project_info']
    if config['project_id']!='roadprints-822cc':raise SystemExit('Unexpected Firebase project')
    credentials,project=google.auth.default(scopes=['https://www.googleapis.com/auth/cloud-platform'])
    try:
        print('\n'.join(existing_emails(AuthorizedSession(credentials),config['project_number'])))
    except Exception as error:
        # Do not print response bodies, identities or credentials in CI logs.
        raise SystemExit(f'Existing tester lookup failed: {type(error).__name__}') from None
