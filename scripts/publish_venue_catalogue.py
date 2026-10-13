#!/usr/bin/env python3
"""Publish an approved catalogue using the shared registry; data cannot change app policy."""
import argparse,hashlib,json,math,re
from datetime import date
from pathlib import Path
from generate_venue_collections import ROOT,generate
MAX_BYTES=2*1024*1024

def validate(root,definition):
    if (root.get('schema_version')!=1 or root.get('collection_id')!=definition['id'] or root.get('scope')!='UK' or root.get('release_status')!='approved' or not root.get('catalogue_version') or not root.get('attribution')):raise ValueError('Unsupported approved catalogue')
    site=definition['catalogue_format']=='services_v1'
    if not site:
        required={'approval_scope':'map_only_partial_beta','ready_for_app':True,'visit_enabled':False,'coverage_complete':False,'completion_target':None}
        if any(k not in root or type(root[k]) is not type(v) or root[k]!=v for k,v in required.items()):raise ValueError('Unsupported map approval')
    rows=root['services' if site else 'venues'];ids=set();aliases=set()
    if len(rows)>5000 or (site and not rows):raise ValueError('Invalid catalogue size')
    if not site and root['review_summary']['map_venues']!=len(rows):raise ValueError('Review count mismatch')
    def position(row,longitude):
        lat,lng=row['lat'],row[longitude]
        if any(isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) for v in (lat,lng)) or not 49<=lat<=61 or not -9<=lng<=2:raise ValueError('Invalid UK position')
    for v in rows:
        ident=v['id']
        if not re.fullmatch(definition['identity_pattern'],ident) or ident in ids or len(ident)>200 or not v.get('name') or len(v['name'])>200:raise ValueError('Invalid venue identity')
        ids.add(ident);position(v,'lng' if site else 'lon')
        if site:
            if not v.get('road') or not v.get('country') or not 1<=len(v['points'])<=100:raise ValueError('Invalid service site')
            for p in v['points']:position(p,'lng')
        else:
            if v.get('status')!='active' or v.get('operating_status')!='reviewed_current' or v.get('visit_enabled') is not False or not isinstance(v.get('town'),str) or len(v['town'])>200 or date.fromisoformat(v['map_reviewed_at'])>date.today():raise ValueError('Unreviewed venue')
            prefix=definition.get('confirmation_id_prefix','')
            if prefix and ident.startswith(prefix) and (v.get('confirmation_required') is not True or not v.get('location_label') or not v.get('attribution')):raise ValueError('Unlabelled approximate venue')
            for alias in v['osm_aliases']:
                if not re.fullmatch('(node|way|relation)/[0-9]+',alias) or alias in aliases:raise ValueError('Duplicate source identity')
                aliases.add(alias)

def publish(source,definition,revision,directory):
    payload=Path(source).read_bytes();root=json.loads(payload);validate(root,definition)
    if not definition['bundled_revision']<=revision<=9999999999 or not 2<=len(payload)<=MAX_BYTES:raise ValueError('Invalid release bounds')
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True);filename=f'catalogue-{revision}.json';target=directory/filename;manifest_path=directory/'manifest-v1.json'
    manifest={'schema_version':1,'collection_id':definition['id'],'revision':revision,'catalogue_version':root['catalogue_version'],'filename':filename,'bytes':len(payload),'sha256':hashlib.sha256(payload).hexdigest()}
    if target.exists() and target.read_bytes()!=payload:raise ValueError('Published releases are immutable')
    if manifest_path.exists():
        old=json.loads(manifest_path.read_text())
        if old['collection_id']!=definition['id'] or old['revision']>revision or (old['revision']==revision and old!=manifest):raise ValueError('Downgrade or revision reuse')
    target.write_bytes(payload);manifest_path.write_text(json.dumps(manifest,indent=2)+'\n');return manifest
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--collection',required=True);p.add_argument('--catalogue',required=True);p.add_argument('--revision',required=True,type=int);p.add_argument('--directory',required=True);a=p.parse_args()
    config=json.loads((ROOT/'collections/venue-collections-v1.json').read_text());generate(config)
    definition=next(c for c in config['collections'] if c['id']==a.collection)
    print(json.dumps(publish(a.catalogue,definition,a.revision,a.directory)))
