"""Extract provider geometry by explicit, current OSM membership identities.

Waymarked linework is metric Web Mercator rounded by the provider. It is useful
for nationwide correspondence diagnostics, not a substitute for OSM node chains
or the independently verified saved-journey matcher identity contract.
"""
import math


def wgs84(point):
    if not isinstance(point,(list,tuple)) or len(point)!=2 or any(
            isinstance(v,bool) or not isinstance(v,(int,float)) or not math.isfinite(v) for v in point):
        raise ValueError('Finite provider XY coordinates required')
    x,y=point
    if abs(x)>20037508.35 or abs(y)>20037508.35:raise ValueError('Provider coordinate outside Web Mercator')
    return [math.degrees(x/6378137),math.degrees(math.atan(math.sinh(y/6378137)))]


def extract_geometry(value, profile):
    identity=profile['relation_id'];tags=profile['tags']
    if value.get('id')!=identity or value.get('type')!='relation':raise ValueError('Source relation differs')
    if any(value.get('tags',{}).get(k)!=tags.get(k) for k in ('network','ref')):
        raise ValueError('Provider and current OSM route classification differ')
    allowed={w['way_id'] for w in profile['ways']};found={};variants={};conflicts=set();unlisted=set();invalid=set();seen=0
    def visit(part):
        nonlocal seen
        if isinstance(part,list):
            for child in part:visit(child)
        elif isinstance(part,dict):
            if part.get('route_type')=='base':
                way=part.get('id');seen+=1
                if way not in allowed:unlisted.add(way);return
                geom=part.get('geometry',{})
                try:
                    if geom.get('type')!='LineString':raise ValueError('LineString required')
                    points=geom['coordinates']
                    if len(points)<2:raise ValueError('At least two coordinates required')
                    coordinates=[wgs84(p) for p in points]
                    canonical=min(tuple(map(tuple,points)),tuple(map(tuple,reversed(points))))
                    if len(set(canonical))<2:raise ValueError('Nonzero provider path required')
                except (ValueError,KeyError,TypeError):invalid.add(way);return
                variants.setdefault(way,{})[canonical]=(coordinates,part.get('direction'),part.get('tags',{}).get('junction'))
                if way in found and found[way][0]!=canonical:conflicts.add(way)
                else:found[way]=(canonical,coordinates)
                return
            for key,child in part.items():
                if key not in ('geometry','tags'):visit(child)
    visit(value.get('route',{}))
    directional={}
    for way in list(conflicts):
        arms=variants[way];ends=[{part[0],part[-1]} for part in arms]
        details=list(arms.values())
        if len(arms)==2 and ends[0]==ends[1] and all(d[2] in ('roundabout','circular') for d in details) and {d[1] for d in details}=={-1,1}:
            directional[way]=[d[0] for d in details];conflicts.remove(way)
    rejected=conflicts|invalid
    ways=[{'way_id':identity,'coordinates':coords,**({'coordinate_parts':directional[identity]} if identity in directional else {})}
          for identity,(_,coords) in sorted(found.items()) if identity not in rejected]
    supported={w['way_id'] for w in ways}
    return {'relation_id':identity,'ways':ways,'expected_way_count':len(allowed),'provider_base_records':seen,
        'missing_way_ids':sorted(allowed-supported),'unlisted_provider_way_ids':sorted(unlisted,key=str),
        'conflicting_way_ids':sorted(conflicts),'invalid_way_ids':sorted(invalid),
        'directional_roundabout_way_ids':sorted(directional),
        'member_lists_complete':profile['member_lists_complete'],
        'provider_geometry_complete':bool(allowed) and allowed==supported and not unlisted,
        'osm_node_identity_verified':False,'association_approved':False,'completion_ready':False}


def metric_projector(origin):
    """Spherical azimuthal-equidistant diagnostics, centred on a route's bounds.

Avoids the latitude-dependent scale of Web Mercator on long national routes.
Lengths remain approximate spherical measurements, not published denominators.
"""
    lon0,lat0=map(math.radians,origin)
    def project(point):
        if len(point)!=2 or not all(math.isfinite(v) for v in point) or not -180<=point[0]<=180 or not -90<=point[1]<=90:
            raise ValueError('Finite WGS84 coordinates required')
        lon,lat=map(math.radians,point);delta=lon-lon0
        cosine=max(-1,min(1,math.sin(lat0)*math.sin(lat)+math.cos(lat0)*math.cos(lat)*math.cos(delta)))
        angle=math.acos(cosine)
        if angle<1e-12:return 0.,0.
        if abs(math.sin(angle))<1e-10:raise ValueError('Projection antipode unsupported')
        k=angle/math.sin(angle);radius=6371008.8
        return radius*k*math.cos(lat)*math.sin(delta),radius*k*(math.cos(lat0)*math.sin(lat)-math.sin(lat0)*math.cos(lat)*math.cos(delta))
    return project
