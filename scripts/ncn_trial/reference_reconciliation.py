"""Read-only geometric triage of named-route candidates, never journey credit.

Lengths are approximate local metric measurements. A nearby NCN line is a review
candidate, not an approved identity association, access decision or denominator.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path

from shapely.geometry import LineString, box, shape
from shapely.ops import unary_union
from shapely.strtree import STRtree

from .journey_replay import projector


class ReferenceScreen:
    def __init__(self, sections):
        self.sections = tuple(sections)
        self.tree = STRtree([s['line'] for s in self.sections])

    def screen(self, line):
        if line.is_empty or line.length <= 0:
            raise ValueError('Non-empty candidate line required')
        hits = [self.sections[int(i)] for i in self.tree.query(line.buffer(25))]
        # Category lengths are exclusive at 10m; preserve a separate tolerance
        # sensitivity series. Neither series constitutes evidence for progress.
        remaining = line
        lengths = {}
        for category in ('open_ncn', 'nonqualifying_ncn', 'regional', 'link', 'unclassified'):
            buffers = [s['line'].buffer(10) for s in hits if s['category'] == category]
            corridor = unary_union(buffers)
            lengths[category] = remaining.intersection(corridor).length
            remaining = remaining.difference(corridor)
        lengths['outside_reference_corridors'] = remaining.length
        eligible = [s['line'] for s in hits if s['category'] == 'open_ncn']
        sensitivity = {str(radius): line.intersection(unary_union([s.buffer(radius) for s in eligible])).length
                       for radius in (3, 10, 25)}
        candidates = sorted({s['key'] for s in hits if s['line'].distance(line) <= 10})
        return {'mapped_length_m': line.length, 'exclusive_corridor_metres_10m': lengths,
                'open_ncn_corridor_metres_by_radius': sensitivity,
                'candidate_section_keys_10m': candidates, 'association_approved': False}


def category(properties):
    kind = properties.get('RouteType')
    if kind == 'NCN':
        return 'open_ncn' if properties.get('OpenStatus') == 'Open' and properties.get('Desc_') != 'Ferry' else 'nonqualifying_ncn'
    return {'RCN': 'regional', 'LINK': 'link'}.get(kind, 'unclassified')


def read_json(path):
    raw = path.read_bytes()
    if path.suffix == '.gz':raw = gzip.decompress(raw)
    return json.loads(raw), hashlib.sha256(raw).hexdigest()


def reconcile(network, ways, catalogue):
    reports = []
    for candidate in catalogue['candidates']:
        relation = candidate['relation_id']
        entries = [f for f in ways['features'] if f['properties']['osm_relation'] == relation]
        if not entries:raise ValueError('Candidate geometry missing')
        if len({f['properties']['osm_way'] for f in entries}) != len(entries):
            raise ValueError('Duplicate candidate way identity')
        if len(entries) != candidate['unique_way_count']:raise ValueError('Incomplete candidate geometry')
        bounds = unary_union([shape(f['geometry']) for f in entries]).bounds
        origin = [(bounds[0] + bounds[2])/2, (bounds[1] + bounds[3])/2]
        project = projector({'projection_origin': origin})
        region = box(bounds[0]-.01, bounds[1]-.01, bounds[2]+.01, bounds[3]+.01)
        sections = []
        for feature in network['features']:
            geometry = shape(feature['geometry'])
            if not geometry.intersects(region):continue
            parts = [geometry] if geometry.geom_type == 'LineString' else list(geometry.geoms)
            for index,part in enumerate(parts):
                sections.append({'key':f"FID:{feature['properties']['FID']}:{index}",
                                 'category':category(feature['properties']),
                                 'line':LineString([project(c) for c in part.coords])})
        screen = ReferenceScreen(sections)
        projected = [LineString([project(c) for c in f['geometry']['coordinates']]) for f in entries]
        aggregate = screen.screen(unary_union(projected))
        records = [{'osm_way':f['properties']['osm_way'], **screen.screen(line)} for f,line in zip(entries,projected)]
        reports.append({'relation_id':relation, 'name':candidate['name'], 'projection_origin':origin,
                        'aggregate':aggregate, 'ways':records,
                        'source_geometry_complete':candidate.get('member_geometry_complete'),
                        'unsupported_relation_ids':candidate.get('unsupported_relation_ids',[]),
                        'source_issues':candidate.get('source_issues',[]),
                        'completion_ready':False})
    return {'purpose':'geometric_triage_only_not_progress_or_verified_NCN_membership',
            'measurement':'local equirectangular metric projection; approximate, not published route distances',
            'corridor_precedence':['open_ncn','nonqualifying_ncn','regional','link','unclassified','outside_reference_corridors'],
            'candidates':reports}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--network',type=Path,required=True)
    parser.add_argument('--expected-network-sha256',required=True)
    parser.add_argument('--ways',type=Path,required=True)
    parser.add_argument('--catalogue',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    network,network_hash=read_json(args.network)
    if network_hash != args.expected_network_sha256:raise ValueError('Network snapshot checksum mismatch')
    ways,ways_hash=read_json(args.ways);catalogue,catalogue_hash=read_json(args.catalogue)
    report=reconcile(network,ways,catalogue)
    report['input_sha256']={'network':network_hash,'ways':ways_hash,'catalogue':catalogue_hash}
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    for candidate in report['candidates']:
        a=candidate['aggregate'];n=a['open_ncn_corridor_metres_by_radius']['10']
        print(candidate['name'],round(a['mapped_length_m']/1000,2),'mapped km;',round(100*n/a['mapped_length_m'],1),'% near open NCN at 10m (diagnostic only)')


if __name__ == '__main__':main()
