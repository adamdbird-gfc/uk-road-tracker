"""Rebuild reviewed Route177 branch anchors from explicit OSM node chains.

The enumerated northern and southern paths are a product-policy association, not
inferred OSM relation membership. No unknown connector or road is synthesized.
"""
import hashlib
import json
from pathlib import Path
from shapely.geometry import LineString
from matched_path_evidence import source_geometry_digest
from .journey_replay import projector

NORTH_WEST=(1460076195,1460076194,1460076193,1460076192,1460076196,
            1460076197,44332298,1460076199,1302385781)
NORTH_FOOT=(1460076195,-1460076187,1460076193,1460076192,-1460076189,
            1460076197,1460076188,1302385781)
SOUTH_WEST=(62216493,-1417748876,-1417748877,-1417748878,-94920359)
NORTH_EAST=(1302385780,-1251416153,-1547229428)
SOUTH_EAST=(-62216492,)


def chain(ways, identities, project):
    """Require real shared endpoint node IDs; identical coordinates are insufficient."""
    parts=[];coordinates=[];previous=None;offset=0
    for signed in identities:
        way=ways[str(abs(signed))]
        points=way['coordinates'][::1 if signed>0 else -1]
        nodes=way['node_ids'][::1 if signed>0 else -1]
        if previous is not None and previous != nodes[0]:
            raise ValueError('Disconnected source chain; no invented connector allowed')
        if coordinates and coordinates[-1] != points[0]:raise ValueError('Conflicting shared-node coordinates')
        line=LineString([project(c) for c in points])
        parts.append((way,signed<0,offset,line.length))
        coordinates.extend(points if not coordinates else points[1:]);offset+=line.length
        previous=nodes[-1]
    return LineString([project(c) for c in coordinates]),parts


def longitude_position(line, longitude, project):
    x=project([longitude,51.398])[0]
    hit=line.intersection(LineString([(x,-10000),(x,10000)]))
    if hit.geom_type!='Point':raise ValueError('Anchor needs one explicit source crossing')
    return line.project(hit)


def build(folder):
    raw=(folder/'public-snapshot.json').read_bytes();data=json.loads(raw);project=projector(data)
    ways={str(w['way_id']):w for w in data['source_member_ways']+data['nearby_nonmember_ways']}
    official={f['properties']['FID']:LineString([project(c) for c in f['geometry']['coordinates']]) for f in data['official_features']}
    west_lo=.455749252591623;west_hi=.469216759497909
    east_lo=.470175836238277;pivot=.4796504
    pivot_m=longitude_position(official[6680],pivot,project)
    sections=[];bindings=[];itineraries=[]

    def add(key,fid,length,identities,modes,lo,hi,label):
        if not any(s['key']==key for s in sections):
            sections.append({'key':key,'length_m':length,'routes':['NCN:177:reviewed-pilot'],'official_fid':fid})
        line,parts=chain(ways,identities,project)
        a=longitude_position(line,lo,project) if lo is not None else 0
        z=longitude_position(line,hi,project) if hi is not None else line.length
        if not a<z:raise ValueError('Reviewed chain direction must follow logical section')
        selected=[]
        for way,reverse,offset,physical_length in parts:
            left=max(a,offset);right=min(z,offset+physical_length)
            if right-left<1e-8:continue
            p,q=left-offset,right-offset
            x=(left-a)/(z-a)*length;y=(right-a)/(z-a)*length
            if reverse:p,q=physical_length-q,physical_length-p;x,y=y,x
            original_length=LineString([project(c) for c in way['coordinates']]).length
            p,q=(max(0,min(original_length,v)) for v in (p,q))
            x,y=(max(0,min(length,v)) for v in (x,y))
            b={'physical_key':str(way['way_id']),'physical_start':p,'physical_end':q,
                'logical_key':key,'logical_start':x,'logical_end':y,'modes':list(modes),
                'source_geometry_sha256':source_geometry_digest(way['coordinates'])}
            bindings.append(b);selected.append(str(way['way_id']))
        itineraries.append({'key':label,'logical_key':key,'way_ids':selected,'modes':list(modes),
            'signed_source_chain':list(identities),'source_path_length_m':z-a,
            'endpoint_policy':'Fixed source crossings; eastern terminal uses observed mapped node, not extrapolated geometry.'})

    west='NCN177:FID7345:full-reviewed';shared='NCN177:FID6680:shared-reviewed';tail='NCN177:FID6680:north-extension'
    add(west,7345,official[7345].length,NORTH_WEST,('cycling',),west_lo,west_hi,'west-north-cycle')
    add(west,7345,official[7345].length,NORTH_FOOT,('on_foot',),west_lo,west_hi,'west-north-foot')
    add(west,7345,official[7345].length,SOUTH_WEST,('on_foot','cycling'),west_lo,west_hi,'west-south')
    add(shared,6680,pivot_m,NORTH_EAST,('on_foot','cycling'),east_lo,pivot,'east-north-shared')
    add(shared,6680,pivot_m,SOUTH_EAST,('on_foot','cycling'),east_lo,None,'east-south-shared')
    add(tail,6680,official[6680].length-pivot_m,NORTH_EAST,('on_foot','cycling'),pivot,None,'east-north-extension')
    return {'schema_version':1,'fixture_sha256':hashlib.sha256(raw).hexdigest(),
        'policy':'Both reviewed alignments qualify over equivalent extents. Eastern northern continuation remains separately required.',
        'scope':'Two complete official features only; this is not the complete NCN177 denominator or the intervening official features.',
        'logical_sections':sections,'bindings':bindings,'itineraries':itineraries,
        'eastern_north_terminal_node':11632889922,'eastern_south_terminal_node':248536946,
        'eastern_terminal_offset_m':projector_offset(official[6680],data,project),
        'production_ready':False}


def projector_offset(official,data,project):
    from shapely.geometry import Point
    way=next(w for w in data['nearby_nonmember_ways'] if w['way_id']==1547229428)
    return Point(project(way['coordinates'][0])).distance(Point(official.coords[-1]))


if __name__=='__main__':
    folder=Path(__file__).resolve().parents[2]/'tests/fixtures/ncn-177-discrepancy'
    result=build(folder)
    (folder/'alternative-bindings.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Logical metres:',sum(s['length_m'] for s in result['logical_sections']))
    print('Reviewed bindings:',len(result['bindings']))
