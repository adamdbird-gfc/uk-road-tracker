#!/usr/bin/env python3
"""Generate synthetic *accepted* Gravesend road journeys from the packaged v3 OSM edge reference.

No private location history is used. These are actual canonical edge endpoints
from the same immutable reference asset used by both candidate builds.
"""
import argparse, gzip, json, math, pathlib, struct
from collections import OrderedDict

def edges_from_reference(path):
    with open(path, "rb") as stream:
        header = stream.read(44)
        if header[:8] != b"RPRD0003":
            raise ValueError("Expected immutable v3 road reference")
        count = struct.unpack(">i", header[40:44])[0]
        index = [struct.unpack(">iiii", stream.read(16)) for _ in range(count)]
        selected = [(x,y,offset,size) for x,y,offset,size in index
                    if 17 <= x <= 25 and 2568 <= y <= 2575]
        if not selected:
            raise ValueError("No Gravesend reference tiles found")
        distinct = OrderedDict()
        for x,y,offset,size in selected:
            stream.seek(offset)
            payload = gzip.decompress(stream.read(size))
            cursor = 0
            while cursor < len(payload):
                # uint64 way, from, to; signed 1e-7 degree endpoint coordinates.
                way, node_a, node_b, ax, ay, bx, by, direction, flags, namelen = struct.unpack_from(
                    ">QQQiiiibBH", payload, cursor)
                cursor += 44
                cursor += namelen
                if cursor > len(payload): raise ValueError("Malformed road tile")
                if not (flags & 1): continue  # owner-eligible physical road
                road_class = flags >> 2
                if road_class > 5: continue
                lon_a, lat_a, lon_b, lat_b = ax/1e7, ay/1e7, bx/1e7, by/1e7
                length = 111195 * math.hypot((lon_b-lon_a)*math.cos(math.radians((lat_a+lat_b)/2)), lat_b-lat_a)
                if not (35 <= length <= 280): continue
                key=(way, min(node_a,node_b), max(node_a,node_b))
                distinct.setdefault(key, [[lon_a,lat_a],[lon_b,lat_b]])
        return distinct

def generate(reference, output, count, train_count):
    edges=list(edges_from_reference(reference).values())
    if len(edges)<count:
        raise ValueError(f"Only {len(edges)} eligible distinct local edges, wanted {count}")
    folder=pathlib.Path(output);folder.mkdir(parents=True,exist_ok=True)
    # deterministic spread across the tiles, to exercise more than one cached tile.
    step=max(1,len(edges)//count)
    picks=edges[::step][:count]
    if len(picks)!=count:raise ValueError("Unexpected sampling underflow")
    for i,points in enumerate(picks):
        geom={"type":"LineString","coordinates":points}
        journey={"journey_id":f"dense-osm-{i:04d}","mode":"driving",
                 "title":"Synthetic accepted road",
                 "distance_meters":120.0,"transport_confirmation":"confirmed",
                 "started_at":"2026-11-15T12:00:00Z","ended_at":"2026-11-15T12:10:00Z",
                 "processing_status":"complete","source":{"type":"android_activity_capture"},
                 "route_geometry":geom,"processing_result":{"status":"ok",
                 "geojson":{"type":"FeatureCollection","features":[{"type":"Feature","geometry":geom}]}}}
        (folder/f"journey_dense-osm-{i:04d}.json").write_text(json.dumps(journey))
    for i in range(train_count):
        row={"journey_id":f"railway-history-{i:04d}","mode":"train",
             "title":"Synthetic train","distance_meters":12000,
             "started_at":"2026-11-15T08:00:00Z","ended_at":"2026-11-15T08:30:00Z",
             "processing_status":"complete"}
        (folder/f"journey_railway-history-{i:04d}.json").write_text(json.dumps(row))
    (folder/"roadprints_onboarding.xml").write_text(
        '<map><boolean name="complete" value="true"/></map>')
    print(json.dumps({"reference_edges":len(edges),"accepted_journey_fixtures":len(picks),
                      "train_journey_fixtures":train_count,"total":count+train_count},sort_keys=True))

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--reference",required=True)
    parser.add_argument("--output",required=True)
    parser.add_argument("--accepted",type=int,default=750)
    parser.add_argument("--train",type=int,default=925)
    args=parser.parse_args()
    generate(args.reference,args.output,args.accepted,args.train)
