"""Compile the eight pinned source inventories for an internal Android trial.

Requires the reviewed NCN trial checkout; never approves published-route scope.
"""
import argparse,gzip,hashlib,json,math,sys
from pathlib import Path

KEYS=['NCN:167','NCN:270','NCN:344','NCN:426','NCN:440','NCN:476','NCN:548','NCN:755']

def build(root,trial):
    sys.path.insert(0,str(trial))
    from scripts.ncn_trial.inventory_completion import read_completion
    routes=[];paths=[];inputs=[];inventories=[]
    from shapely.geometry import LineString
    from shapely.ops import unary_union
    for key in KEYS:
        folder=root/key.replace(':','-');m=json.loads((folder/'manifest.json').read_bytes())
        raw=gzip.decompress((folder/'plan.json.gz').read_bytes());a=gzip.decompress((folder/'associations.json.gz').read_bytes())
        ledger,known,plan=read_completion(raw,a,m['plan_sha256'],m['association_sha256'])
        if plan['roundabout_groups'] or plan['unmapped_inventory_m']>1e-5 or plan['whole_published_route_ready']:
            raise ValueError('This first Android adapter requires fully bound, non-branch trial inventories')
        inventory=unary_union([LineString(s['coordinates']) for s in plan['sections'] if s['key'].startswith('inventory:')])
        if any(inventory.intersection(previous).length>1e-10 for previous in inventories):raise ValueError('Overlapping inventories require a global physical union adapter')
        inventories.append(inventory)
        if any(abs((b['physical_end']-b['physical_start'])-abs(b['logical_end']-b['logical_start']))>1e-5 for b in plan['bindings']):raise ValueError('Alternative physical/logical distances need a separate adapter')
        origin=plan['metadata']['projection_origin'];sections={s['key']:s for s in plan['sections']}
        def project(p):return (6371008.8*math.radians(p[0]-origin[0])*math.cos(math.radians(origin[1])),6371008.8*math.radians(p[1]-origin[1]))
        for p in known:
            coords=[list(c) for c in p.coordinates];xy=[project(c) for c in coords];positions=[0.]
            for first,last in zip(xy,xy[1:]):positions.append(positions[-1]+math.dist(first,last))
            bindings=[]
            for section in p.section_keys:
                points=sections[section]['coordinates']
                starts=[i for i in range(len(coords)-len(points)+1) if coords[i:i+len(points)]==points]
                if len(starts)!=1:raise ValueError('Ambiguous source span')
                offset=positions[starts[0]]
                for b in plan['bindings']:
                    if b['physical_key']==section:bindings.append({'start':offset+b['physical_start'],'end':offset+b['physical_end'],
                        'target':b['logical_key'],'low':b['logical_start'],'high':b['logical_end'],'modes':b['modes']})
            paths.append({'route':key,'way':p.way_id,'coordinates':coords,'nodes':p.osm_node_ids,'modes':p.modes,
                'source_geometry_sha256':json.loads(a)['paths'][next(i for i,v in enumerate(json.loads(a)['paths']) if v['way_id']==p.way_id)]['source_geometry_sha256'],
                'origin':origin,'positions':positions,'bindings':bindings})
        routes.append({'key':key,'length_m':plan['canonical_inventory_length_m'],'reference_version':plan['reference_version'],
            'logical':{s['key']:s['length_m'] for s in plan['logical_sections']}})
        inputs.append({'route':key,**m,'source_snapshot_sha256':plan['metadata']['source_snapshot_sha256']})
    return {'schema_version':1,'scope':'internal-eight-source-inventory-trial','whole_published_route_ready':False,'physical_equivalence_verified':True,
        'attribution':'© OpenStreetMap contributors (ODbL 1.0); official scope: Walk Wheel Cycle Trust (OGL 3.0).',
        'routes':routes,'paths':paths,'inputs':inputs}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plans',type=Path,required=True)
    parser.add_argument('--trial-root',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);a=parser.parse_args()
    payload=build(a.plans,a.trial_root);raw=json.dumps(payload,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
    a.output.mkdir(parents=True,exist_ok=True);(a.output/'ncn-trial.bin').write_bytes(gzip.compress(raw,mtime=0))
    (a.output/'ncn-trial-manifest.json').write_text(json.dumps({'schema_version':1,'sha256':hashlib.sha256(raw).hexdigest(),
        'scope':payload['scope'],'route_keys':KEYS,'whole_published_route_ready':False},indent=2)+'\n')
    print('Compiled',len(payload['routes']),'inventories',len(payload['paths']),'source-way associations;',len(raw),'JSON bytes')
if __name__=='__main__':main()
