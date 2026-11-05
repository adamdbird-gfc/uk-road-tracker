"""Repeat bounded geometric findings from the committed fixture; never award credit."""
import argparse
import hashlib
import json
import math
from pathlib import Path

from pyproj import CRS, Geod, Transformer
from shapely.geometry import LineString, Point
from shapely.ops import substring, transform

HERE = Path(__file__).resolve().parent


def angle(a, z, q, r):
    dx, dy = z[0]-a[0], z[1]-a[1]
    ex, ey = r[0]-q[0], r[1]-q[1]
    return math.degrees(math.acos(min(1., abs(dx*ex+dy*ey)/(math.hypot(dx,dy)*math.hypot(ex,ey)))))


def admitted(source, edge):
    way = source['way_id']
    return any(k == way or (len(k.split(':')) == 3 and k.split(':')[0] == way
                           and int(k.split(':')[1]) <= edge < int(k.split(':')[2]))
               for k in source['section_keys'])


def findings(fixture):
    results = []
    for case in fixture['cases']:
        source = case['source']
        lon, lat = source['coordinates'][0]
        crs = CRS.from_proj4(f'+proj=aeqd +lat_0={lat} +lon_0={lon} +datum=WGS84 +units=m')
        fw = Transformer.from_crs(4326, crs, always_xy=True)
        line = transform(fw.transform, LineString(source['coordinates']))
        targets = {t['key']: t for t in case['targets']}
        pieces = []
        for r in case['candidates']:
            record = targets[r['target_key']]
            target = transform(fw.transform, LineString(record['coordinates']))
            gap = substring(target, r['low_m']/r['target_length_m'],
                            r['high_m']/r['target_length_m'], normalized=True)
            edge = transform(fw.transform, LineString(r['source_coordinates']))
            samples = [gap.interpolate(t, normalized=True).distance(edge) for t in (0,.25,.5,.75,1)]
            end = Point(target.coords[-1])
            a, z = list(edge.coords)
            dx, dy = z[0]-a[0], z[1]-a[1]
            end_station = ((end.x-a[0])*dx+(end.y-a[1])*dy)/edge.length
            pieces.append(dict(id=r['id'], target_key=r['target_key'],
                               route_local_screen_m=r['unassessable_m'],
                               target_interval_m=[r['low_m'],r['high_m']],
                               original_cause_region=r['historical_cause'],
                               source_edge=r['source_edge'], admitted=admitted(source,r['source_edge']),
                               local_angle_degrees=angle(a,z,*target.coords),
                               local_mean_gap_to_candidate_edge_m=sum(samples)/len(samples),
                               local_sampled_max_gap_to_candidate_edge_m=max(samples),
                               target_endpoint_distances_to_full_source_m=[line.distance(Point(c)) for c in target.coords],
                               target_endpoint_source_stations_m=[line.project(Point(c)) for c in target.coords],
                               selected_target_endpoint_degrees=record['endpoint_degrees'],
                               target_end_unclipped_station_on_candidate_edge_m=end_station,
                               candidate_edge_ground_length_m=edge.length,
                               new_binding_approved=False))
        total = math.fsum(p['route_local_screen_m'] for p in pieces)
        length = Geod(ellps='WGS84').line_length(*zip(*source['coordinates']))
        result = dict(route=case['route'], way=case['way'], source_length_ground_m=line.length,
                      source_length_geodesic_m=length, original_projection_origin=case['origin'],
                      source_section_keys=source['section_keys'], candidate_piece_count=len(pieces),
                      gross_route_local_screen_m=total,
                      admitted_piece_count=sum(p['admitted'] for p in pieces),
                      admitted_screen_route_local_m=math.fsum(p['route_local_screen_m'] for p in pieces if p['admitted']),
                      unadmitted_piece_count=sum(not p['admitted'] for p in pieces),
                      unadmitted_screen_route_local_m=math.fsum(p['route_local_screen_m'] for p in pieces if not p['admitted']),
                      original_source_binding_count=len(case['existing_source_bindings']), pieces=pieces,
                      confirmed_new_route_local_m=0., confirmed_new_unique_network_m=0.,
                      routes_improved=0, potential_unique_network_m=None,
                      historical_awards_changed=False, route_attainability_changed=False)
        if not pieces:
            target = next(t for t in case['targets'] if t['selected'])
            targetline = transform(fw.transform,LineString(target['coordinates']))
            result['nearest_original_target'] = dict(key=target['key'],
                local_distance_m=line.distance(targetline),
                angle_to_admitted_source_edge_degrees=angle(*list(line.coords)[:2],*targetline.coords),
                target_coordinates=target['coordinates'],
                note='Nearest is not corresponding: no target binding is inferred.')
        results.append(result)
    return dict(schema_version=1, evidence_sha256=hashlib.sha256((HERE/'evidence.json').read_bytes()).hexdigest(),
                diagnostic_only=True, metric='WGS84 ellipsoidal local azimuthal equidistant; metres',
                baseline_revision=4, assessability_before_percent=92.608243626372,
                assessability_after_percent=92.608243626372, assessability_delta_percentage_points=0.,
                confirmed_new_route_local_m=0.,confirmed_new_unique_network_m=0.,routes_improved=0,
                newly_fully_attainable_routes=0, potential_unique_network_recovery_m=None,
                already_published_deal_m=561.888736534, cases=results,
                note='Ground lengths are diagnostics. Original route-local interval units are retained, never replaced with ground lengths. No proposed correction passed every acceptance gate.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path)
    a = p.parse_args()
    text=json.dumps(findings(json.loads((HERE/'evidence.json').read_text())),indent=2,sort_keys=True,allow_nan=False)+'\n'
    if a.output:
        a.output.write_text(text)
    else:
        print(text,end='')
