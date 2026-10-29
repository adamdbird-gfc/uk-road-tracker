"""Versioned NCN Hosting reference contract; no Android or research dependency."""
import hashlib,json,re,sqlite3
from pathlib import Path
BASE='https://roadprints-data-822cc.web.app/'
MANIFEST='collections/published/ncn/manifest-v2.json'
MAX_DATABASE=512*1024*1024
MAX_COMPRESSED=256*1024*1024

def sha_file(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda:source.read(65536),b''):h.update(chunk)
    return h.hexdigest()

def validate(m):
    if (m.get('schema_version')!=2 or m.get('scope')!='national-verified-open-checkpoint'
        or m.get('whole_published_route_ready') is not False
        or not re.fullmatch('[a-f0-9]{64}',m.get('sha256',''))
        or not re.fullmatch('[a-f0-9]{64}',m.get('compressed_sha256',''))
        or m.get('filename')!='collections/published/ncn/'+m['sha256']+'/catalogue.db.gz'
        or not 4096<=m.get('database_bytes',0)<=MAX_DATABASE
        or not 100<=m.get('compressed_bytes',0)<=MAX_COMPRESSED
        or not 1<=m.get('supported_identities',0)<=2000
        or type(m.get('revision')) is not int or m['revision']<1):
        raise ValueError('Unsupported NCN release manifest')
    return m

def verify_database(path,m):
    validate(m)
    if Path(path).stat().st_size!=m['database_bytes'] or sha_file(path)!=m['sha256']:
        raise ValueError('NCN database integrity failure')
    with sqlite3.connect(Path(path).resolve().as_uri()+'?mode=ro',uri=True) as db:
        if db.execute('PRAGMA quick_check').fetchone()[0]!='ok':raise ValueError('NCN SQLite integrity failure')
        if db.execute('SELECT COUNT(*) FROM routes').fetchone()[0]!=m['supported_identities']:raise ValueError('NCN route count differs')
