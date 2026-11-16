"""Build the Stage 4.5 compact comparison paths from the verified Stage 4.4 catalogue."""
import argparse
import gzip
import hashlib
import io
import sys
import json
from pathlib import Path
import sqlite3
import struct


# Exact OSM ways reviewed for the westbound passenger comparison. This does
# not change the underlying regional catalogue or its GPS witness eligibility.
TUNNELS = {
    1362391258, 1362706029, 166353872, 1362391259, 1362706031, 4208555,
    4415277, 492574850, 492574848,
}
CONNECTORS = {
    163742792: 'NCS', 382531263: 'NCS', 382531270: 'NCS',
    623073078: 'LCH', 382531266: 'LCH', 622907369: 'LCH',
    4346192: 'LLL', 256367760: 'XTD', 41117629: 'GRV0',
}
STATIONS = {
    'GRV': (0.366990, 51.441029),
    'CHX': (-0.123689, 51.507497),
}

def approved(way, eligible, tunnel, tags):
    if tags.get('railway') != 'rail' or tags.get('operator') != 'Network Rail':
        return False
    if tunnel:
        return eligible and way in TUNNELS and tags.get('tunnel') == 'yes'
    if eligible:
        return True
    ref = CONNECTORS.get(way)
    if ref == 'GRV0':
        return tags.get('service') == 'siding' and tags.get('name') == 'North Kent Line'
    if ref == 'XTD':
        return tags.get('name') == 'South Eastern Main Line' and tags.get('bridge') == 'viaduct'
    return ref is not None and tags.get('ref') == ref and tags.get('usage') == 'main'

def build(source, destination, prototype):
    sys.path.insert(0, str(prototype.resolve()))
    from stage44_network_first import Catalogue, CATALOGUE_SHA, GRAVESEND, CHX_TEST_PLATFORMS

    with source.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == CATALOGUE_SHA
    with sqlite3.connect(source.resolve().as_uri() + '?mode=ro', uri=True) as db:
        catalogue = Catalogue(db)
        _, paths = catalogue.candidates()
        assert not catalogue.limit_hits
        metadata = {i:(way, bool(e), bool(t), json.loads(tags), a, b, ax, ay, bx, by)
                    for i,way,e,t,tags,a,b,ax,ay,bx,by in db.execute('''
                        SELECT rail.id,way,eligible,tunnel,tags,a,b,ax,ay,bx,by
                        FROM rail JOIN way ON way.id=rail.way''')}
        stop_ids = GRAVESEND + CHX_TEST_PLATFORMS
        stop_positions = {n: (x,y) for n,x,y in db.execute('SELECT id,x,y FROM stop') if n in stop_ids}
    directions = {}
    routes = []
    for index, branch in enumerate(('Sidcup', 'Bexleyheath', 'Woolwich')):
        for path in paths[branch]:
            edges = []
            eligible = True
            previous_end = None
            for id_, start, vector, length in zip(path.edge_ids,path.starts,path.vectors,path.lengths):
                way, base, tunnel, tags, a,b,ax,ay,bx,by = metadata[id_]
                reverse = tuple(start) == (bx,by)
                assert reverse or tuple(start) == (ax,ay)
                origin,destination_node = (b,a) if reverse else (a,b)
                assert previous_end is None or previous_end == origin
                previous_end = destination_node
                if not edges:
                    origin_stop = stop_ids.index(origin)
                    assert origin in stop_positions
                key = id_, reverse
                if key not in directions:
                    directions[key] = (len(directions), tuple(start), tuple(vector), length, tunnel)
                edges.append(directions[key][0])
                eligible &= approved(way,base,tunnel,tags)
            assert previous_end in CHX_TEST_PLATFORMS
            routes.append((index, eligible, origin_stop, stop_ids.index(previous_end), edges))
    assert len(routes) == 72 and any(branch == 0 and eligible for branch, eligible, *_ in routes)
    out = io.BytesIO()
    out.write(b'RPN1')
    out.write(struct.pack('>HHB',len(directions),len(routes),len(stop_ids)))
    for stop in stop_ids:
        out.write(struct.pack('>qdd',stop,*stop_positions[stop]))
    for (id_, reverse), (_, (x,y), (dx,dy), length, tunnel) in directions.items():
        # Store 1 cm coordinates relative to a regional origin and millimetre lengths.
        out.write(struct.pack('>iBiiiii',id_,int(reverse) | (2 if tunnel else 0),
                              round((x-700000)*100),round((y-5700000)*100),
                              round(dx*100),round(dy*100),round(length*1000)))
    for branch, eligible, first, last, edges in routes:
        out.write(struct.pack('>BBBBH',branch,eligible,first,last,len(edges)))
        out.write(struct.pack(f'>{len(edges)}H',*edges))
    payload = out.getvalue()
    destination.write_bytes(gzip.compress(payload,compresslevel=9,mtime=0))
    print(f'{len(directions)} directed edges, {len(routes)} paths, '
          f'{sum(route[1] for route in routes)} qualified, {len(payload)} bytes inflated, '
          f'{destination.stat().st_size} bytes compressed')
    print(hashlib.sha256(destination.read_bytes()).hexdigest())

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path)
    parser.add_argument('destination',type=Path)
    parser.add_argument('--stage44-prototype', type=Path, required=True,
                        help='Directory containing the preserved Stage 4.4 Python module')
    args = parser.parse_args()
    build(args.source, args.destination, args.stage44_prototype)
