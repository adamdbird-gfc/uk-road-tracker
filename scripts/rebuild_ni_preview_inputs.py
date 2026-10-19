#!/usr/bin/env python3
"""Reproduce only the pinned NI additions, then compare their reviewed report."""
import argparse,hashlib,json,subprocess,sys,time
from pathlib import Path
from urllib.request import urlopen


def filehash(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        while data:=stream.read(1024*1024):h.update(data)
    return h.hexdigest()


def download(url,path,expected):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and filehash(path)==expected:return
    for attempt in range(3):
        partial=path.with_suffix(path.suffix+'.partial')
        try:
            with urlopen(url,timeout=120) as response,partial.open('wb') as stream:
                while chunk:=response.read(1024*1024):stream.write(chunk)
            if filehash(partial)!=expected:raise ValueError('Pinned download checksum changed')
            partial.replace(path);return
        except (OSError,ValueError):
            partial.unlink(missing_ok=True)
            if attempt==2:raise
            time.sleep(2**attempt)


def rebuild(work):
    work=Path(work);work.mkdir(parents=True,exist_ok=True);sources=work/'sources';sources.mkdir(exist_ok=True)
    download('https://www.nisra.gov.uk/files/nisra/publications/SDL15-ESRI_format.zip',work/'nisra.zip','fa880d3effb0e05923a7a27d2ae44191394737a926af0b61ed1e88c30f4b107c')
    pbf='ireland-and-northern-ireland-261014.osm.pbf';source_url='https://download.geofabrik.de/europe/'+pbf
    download(source_url,sources/pbf,'a41a12f0f8cfbb0f6457da662d51aaf91218ed69f1619b0b99c4dcf624925285')
    download('https://download.geofabrik.de/europe/ireland-and-northern-ireland.poly',sources/'ireland-and-northern-ireland.poly','fc56ea18191a8d272ae491fc14281ddc01ac9835d77e2cded56fb6d1829f0fe8')
    # The dated PBF SHA-256 pins the previously supplier-MD5-verified snapshot.
    (sources/'downloads.json').write_text(json.dumps({'file':pbf,'url':source_url,'sha256':filehash(sources/pbf),
      'poly_sha256':filehash(sources/'ireland-and-northern-ireland.poly'),'supplier_md5_verified_in_original_trial':True}))
    download('https://roadprints-data-822cc.web.app/settlement-inventory-manifest-v1.json',work/'gb-manifest.json','054382460295352450cfa61bb7d954ab0f9852c5319d127ec189a1702e506c39')
    def run(script,*args):subprocess.run([sys.executable,'scripts/'+script,*map(str,args)],check=True)
    run('prepare_ni_boundaries.py','--source-zip',work/'nisra.zip','--output',work/'boundaries')
    run('generate_ni_road_inventory_candidates.py','--boundary-dir',work/'boundaries','--source-dir',sources,'--output',work/'candidates')
    run('stage_ni_reference_additions.py','--gb-manifest',work/'gb-manifest.json','--boundaries',work/'boundaries',
        '--ni-dir',work/'candidates','--output',work/'public','--report',work/'additions-report.json')
    if filehash(work/'additions-report.json')!='8721b48b05b791bdca92b18a65b4da8acb5d72234b078a5e08207a8b6b78c6e0':
        raise ValueError('Rebuilt additions differ from reviewed report')
    print('NI additions match the reviewed file/hash report exactly.',flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',required=True);a=p.parse_args();rebuild(a.work)
