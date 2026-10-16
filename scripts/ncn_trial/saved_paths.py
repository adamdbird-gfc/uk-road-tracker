"""Bounded saved-path association trial; no proximity-only or whole-way credit.

Known links are explicit snapshot-scoped fixture associations. This module does
not infer a nationwide NCN-to-OSM catalogue or historic legal/access validity.
"""
from dataclasses import dataclass
import json
import math

from shapely.geometry import LineString, Point

from matched_path_evidence import digest, geometry_digest, source_geometry_digest
from .coverage import Evidence, MODES
from .fixtures import ROOT
from .journey_replay import projector
from .activation import Assessment


@dataclass(frozen=True)
class KnownPath:
    way_id: str
    coordinates: tuple
    section_keys: tuple
    reference_version: str
    modes: tuple = ('on_foot',)
    target_geometry: str = 'reviewed_reference'


def fixture_paths(reference):
    folder = ROOT / 'tests/fixtures/ncn-route1-context'
    source = json.loads((folder/'source.json').read_text())
    raw = (folder/'ways.geojson').read_bytes()
    import hashlib
    if hashlib.sha256(raw).hexdigest() != source['fixture_sha256']:
        raise ValueError('OSM fixture checksum changed')
    memberships = {str(r['osm_way']) for r in source['memberships'] if r['relation'] == 13512082}
    paths = []
    for feature in json.loads(raw)['features']:
        way = str(feature['properties']['osm_way'])
        if way not in memberships:
            raise ValueError('Missing independent route membership evidence')
        paths.append(KnownPath(way, tuple(tuple(p) for p in feature['geometry']['coordinates']),
                              ('FID:16404:0' if way == '4973301' else 'FID:954:0',), reference.version))
    return paths


def assess_saved_paths(export, reference, metadata, known_paths):
    if export.get('format') != 'roadprints_journey_diagnostics' or export.get('schema_version') != 1 \
            or export.get('export_complete') is not True:
        raise ValueError('Complete supported diagnostic export required')
    journey = export['journey']
    mode = str(journey.get('mode', 'unknown')).strip().lower()
    canonical = MODES.get(mode, mode)
    reasons = []
    def finish(intervals=(), method='none'):
        evidence = Evidence(reference.version, canonical, tuple(intervals), tuple(reasons))
        return Assessment(evidence), {'association_method': method, 'reasons': reasons,
                                     'reference_scope': 'bounded_snapshot_trial',
                                     'historic_membership_verified': False, 'production_progress_written': False}
    if mode not in MODES:
        reasons.append('ineligible_mode');return finish()
    result = journey.get('processing_result') or {}
    if journey.get('processing_status') != 'complete':
        reasons.append('match_not_complete');return finish()
    if canonical != 'on_foot':
        reasons.append('unsupported_mode_matcher_pair');return finish()
    if result.get('matcher') != 'preloaded_pedestrian_network_v4':
        reasons.append('unsupported_match_provenance');return finish()
    project = projector(metadata)
    paths = {p.way_id: p for p in known_paths if p.reference_version == reference.version and canonical in p.modes}
    geometries = {key: LineString([project(p) for p in path.coordinates]) for key, path in paths.items()}
    corrections = journey.get('journey_corrections') or {}
    removed = set(corrections.get('removed_matched_segments') or [])
    # Road-only edits cannot safely be mapped to this vertex-index contract yet.
    if corrections.get('removed_road_ids') or corrections.get('removed_road_labels'):
        reasons.append('unresolved_road_identity_correction');return finish()
    features = (result.get('geojson') or {}).get('features') or []
    evidence = result.get('matched_path_evidence')
    matched_edges = []
    if evidence is not None:
        if evidence.get('schema_version') != 1 or evidence.get('status') != 'available':
            reasons.append('unavailable_path_evidence');return finish()
        try:
            if type(evidence.get('geometry_feature_index')) is not int or evidence['geometry_feature_index'] != 0:
                raise ValueError('Unsupported geometry index')
            feature = features[0]
            if feature['geometry']['type'] != 'LineString':raise ValueError('Unsupported geometry')
            coordinates = feature['geometry']['coordinates']
            if geometry_digest(coordinates) != evidence['geometry_sha256']:raise ValueError('Edited geometry')
            refs = evidence['references']
            expected_edge = 0
            for start, end, index in evidence['spans']:
                if not all(isinstance(i,int) and not isinstance(i,bool) for i in (start,end,index)) \
                        or start != expected_edge or not start < end < len(coordinates) or not 0 <= index < len(refs):
                    raise ValueError('Unaligned spans')
                expected_edge = end
                item = refs[index]
                public = {k:v for k,v in item.items() if k != 'content_sha256'}
                if digest(public) != item['content_sha256']:raise ValueError('Changed reference metadata')
                tags = item.get('tags') or {}
                activity_tag = tags.get('foot' if canonical == 'on_foot' else 'bicycle')
                if activity_tag in {'no','private'} or (tags.get('access') in {'no','private'} and activity_tag not in {'yes','designated','permissive'}):
                    reasons.append('restricted_source_access');continue
                key = str(item['source_feature_id'])
                if not str(item['source_key']).startswith('osm_geofabrik_') or key not in paths:
                    continue
                if item['source_geometry_sha256'] != source_geometry_digest(paths[key].coordinates):
                    reasons.append('source_geometry_changed');continue
                for edge in range(start,end):
                    if edge not in removed:
                        matched_edges.append((key,coordinates[edge],coordinates[edge+1]))
            if expected_edge != len(coordinates)-1:raise ValueError('Incomplete span coverage')
        except (KeyError,ValueError,TypeError,IndexError,AttributeError):
            reasons.append('invalid_or_changed_path_evidence');return finish()
        method = 'stored_source_identity'
    else:
        # Compatibility for old saved pedestrian results: require an exact full
        # reference-way geometry identity, then use only travelled route edges.
        by_hash = {}
        for key,path in paths.items():by_hash.setdefault(source_geometry_digest(path.coordinates),[]).append(key)
        identities = set()
        for feature in (result.get('road_geojson') or {}).get('features') or []:
            geometry = feature.get('geometry') or {}
            if geometry.get('type') != 'LineString':continue
            try: candidates = by_hash.get(source_geometry_digest(geometry['coordinates']),[])
            except (KeyError,ValueError,TypeError):continue
            if len(candidates) == 1:identities.add(candidates[0])
        edge_index = 0
        for feature in features:
            geometry = feature.get('geometry') or {}
            if geometry.get('type') != 'LineString':
                reasons.append('unsupported_legacy_geometry');return finish()
            coordinates = geometry.get('coordinates') or []
            for a,b in zip(coordinates,coordinates[1:]):
                if edge_index not in removed:
                    matched_edges.extend((key,a,b) for key in identities)
                edge_index += 1
        method = 'exact_legacy_source_geometry'
    intervals = []
    for key,a,b in matched_edges:
        try:line=LineString([project(a),project(b)])
        except (ValueError,TypeError):continue
        # Strict identity of the traversed physical edge, not a 3/8/15m search.
        if line.length <= .01 or not geometries[key].buffer(.03).covers(line):continue
        for target in paths[key].section_keys:
            section=reference.sections.get(target)
            if section is None or not section.eligible:continue
            if paths[key].target_geometry == 'exact_source_spans':
                # Source identity is already established above. Clip only to
                # selected source geometry, with micron-scale numeric tolerance.
                # This is not the 3m GIS reconciliation corridor below.
                clipped=line.intersection(section.line.buffer(1e-6,cap_style=2))
                pieces=[clipped] if clipped.geom_type=='LineString' else list(getattr(clipped,'geoms',()))
            elif paths[key].target_geometry == 'reviewed_reference':
                pieces=[line]
            else:
                reasons.append('unsupported_target_geometry');continue
            for piece in pieces:
                if piece.geom_type!='LineString' or piece.length<=.01:continue
                count=max(2,math.ceil(piece.length/2))
                samples=[piece.interpolate(i/count,normalized=True) for i in range(count+1)]
                if any(p.distance(section.line)>3 for p in samples):continue
                positions=[section.line.project(p) for p in samples]
                deltas=[b-a for a,b in zip(positions,positions[1:])]
                if not (all(d>=0 for d in deltas) or all(d<=0 for d in deltas)):continue
                covered=abs(positions[-1]-positions[0])
                if not .9*piece.length <= covered <= 1.1*piece.length+.1:continue
                intervals.append((target,min(positions[0],positions[-1]),max(positions[0],positions[-1])))
    reasons.append('snapshot_path_association' if intervals else 'no_supported_path_association')
    return finish(intervals,method)
