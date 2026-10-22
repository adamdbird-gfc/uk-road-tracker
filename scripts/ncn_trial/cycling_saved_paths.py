"""Bounded bicycle-graph node-chain replay; no geometric membership search."""
import math

from shapely.geometry import LineString
from shapely.ops import substring

from matched_path_evidence import digest, geometry_digest
from cycling_path_evidence import osm_node_id
from .activation import Assessment
from .coverage import Evidence
from .journey_replay import projector


def assess_cycling_paths(export, reference, metadata, known_paths):
    from .saved_paths import credit_source_line
    journey=export['journey'];result=journey.get('processing_result') or {}
    reasons=[];intervals=[]
    def finish():
        return Assessment(Evidence(reference.version,'cycling',tuple(intervals),tuple(reasons))), {
            'association_method':'stored_osrm_node_chain','reasons':reasons,
            'reference_scope':'bounded_snapshot_trial','historic_membership_verified':False,
            'production_progress_written':False}
    if result.get('matcher')!='osrm_bicycle_v1' or result.get('matching_mode')!='cycling':
        reasons.append('unsupported_mode_matcher_pair');return finish()
    evidence=result.get('cycling_path_evidence') or {}
    if type(evidence.get('schema_version')) is not int or evidence.get('schema_version')!=1 or evidence.get('provider')!='osrm' \
            or evidence.get('router_graph')!='bicycle' or evidence.get('status') not in ('available','partial'):
        reasons.append('unavailable_cycling_node_evidence');return finish()
    corrections=journey.get('journey_corrections') or {}
    if corrections.get('removed_road_ids') or corrections.get('removed_road_labels'):
        reasons.append('unresolved_road_identity_correction');return finish()
    removed=set(corrections.get('removed_matched_segments') or [])
    paths=[p for p in known_paths if p.reference_version==reference.version and 'cycling' in p.modes and p.osm_node_ids]
    project=projector(metadata)
    try:
        features=result['geojson']['features'];offsets=[];offset=0
        for feature in features:
            if feature['geometry']['type']!='LineString':raise ValueError('Unsupported geometry')
            offsets.append(offset);offset+=len(feature['geometry']['coordinates'])-1
        seen=set()
        for record in evidence['features']:
            public={k:v for k,v in record.items() if k!='content_sha256'}
            if digest(public)!=record['content_sha256']:raise ValueError('Changed node evidence')
            index=record['geometry_feature_index']
            if type(index) is not int or not 0<=index<len(features) or index in seen:
                raise ValueError('Invalid feature identity')
            seen.add(index)
            if not record['legs']:continue
            coordinates=features[index]['geometry']['coordinates']
            if geometry_digest(coordinates)!=record['geometry_sha256']:raise ValueError('Edited geometry')
            previous_end=-1
            for leg in record['legs']:
                start,end=leg['from_vertex'],leg['to_vertex']
                if type(start) is not int or type(end) is not int or not 0<=start<end<len(coordinates) or start<previous_end:
                    raise ValueError('Unaligned leg span')
                previous_end=end
                nodes=[]
                for value in leg['osm_nodes']:
                    node=osm_node_id(value)
                    if not nodes or nodes[-1]!=node:nodes.append(node)
                candidates=[]
                if len(nodes)>=2:
                    for path in paths:
                        if any(path.osm_node_ids.count(n)!=1 for n in nodes):continue
                        positions=[path.osm_node_ids.index(n) for n in nodes]
                        if not (all(a<b for a,b in zip(positions,positions[1:])) or all(a>b for a,b in zip(positions,positions[1:]))):continue
                        candidates.append((path,min(positions),max(positions)))
                if len(candidates)!=1:
                    reasons.append('unknown_or_ambiguous_node_chain');continue
                path,low,high=candidates[0]
                source=LineString([project(p) for p in path.coordinates[low:high+1]])
                for edge in range(start,end):
                    if offsets[index]+edge in removed:continue
                    line=LineString([project(coordinates[edge]),project(coordinates[edge+1])])
                    # OSRM GeoJSON uses six decimal places. This is a bounded
                    # quantisation check after unique node identity, not snapping.
                    if line.length<=.01 or not source.buffer(.12).covers(line):continue
                    count=max(2,math.ceil(line.length/2))
                    positions=[source.project(line.interpolate(i/count,normalized=True)) for i in range(count+1)]
                    changes=[b-a for a,b in zip(positions,positions[1:])]
                    if not (all(d>=0 for d in changes) or all(d<=0 for d in changes)):continue
                    covered=abs(positions[-1]-positions[0])
                    if not .9*line.length<=covered<=1.1*line.length+.15:continue
                    selected=substring(source,min(positions[0],positions[-1]),max(positions[0],positions[-1]))
                    if selected.geom_type=='LineString':intervals.extend(credit_source_line(path,selected,reference))
    except (KeyError,ValueError,TypeError,IndexError,AttributeError,OverflowError):
        intervals.clear();reasons.append('invalid_or_changed_cycling_evidence');return finish()
    reasons.append('snapshot_node_association' if intervals else 'no_supported_node_association')
    return finish()
