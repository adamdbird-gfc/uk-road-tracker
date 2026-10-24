"""Shared public map areas; no journey data or user identifiers are retained.

Postgres provides durable cache/budgets and a cross-process upstream lock.
A configurable interpreter URL permits a dedicated provider without app changes.
"""
import json
import logging
import math
import os
import time

import httpx
import psycopg
from psycopg.types.json import Jsonb

VERSION = 1
FEATURE_REVISION = 3
TTL = 7 * 86400
MAX_BYTES = 4_000_000
MAX_AREAS = 256
MAX_CACHE_BYTES = 64_000_000
DAILY_LIMIT = int(os.getenv("CONTEXT_DAILY_UPSTREAM_LIMIT", "200"))
UPSTREAM = os.getenv("CONTEXT_INTERPRETER_URL", "https://overpass-api.de/api/interpreter")
PROVIDER_FORMAT = os.getenv("CONTEXT_PROVIDER_FORMAT", "overpass")
LOCK = 713940181
logger = logging.getLogger("roadprints.context")


class ContextUnavailable(Exception):
    def __init__(self, retry_after=600):
        self.retry_after = max(60, int(retry_after))


def tile_bounds(lat_cell, lon_cell):
    # UK service scope, integer area identifiers rather than exact phone fixes.
    if not 4900 <= lat_cell <= 6100 or not -550 <= lon_cell <= 100:
        raise ValueError("Map context is available for UK areas only")
    s, w = lat_cell / 100, lon_cell / 50
    return [s - .003, w - .006, s + .013, w + .026]


def clean_document(document):
    if document.get("remark") or not isinstance(document.get("elements"), list):
        raise ValueError("Incomplete context area")
    elements = []
    for item in document["elements"]:
        geometry = item.get("geometry")
        if not isinstance(geometry, list) or len(geometry) < 2:
            continue
        if any(not isinstance(p, dict) or not isinstance(p.get("lat"), (int, float))
               or not isinstance(p.get("lon"), (int, float))
               or not math.isfinite(p["lat"]) or not math.isfinite(p["lon"])
               or not -90 <= p["lat"] <= 90 or not -180 <= p["lon"] <= 180 for p in geometry):
            raise ValueError("Invalid context geometry")
        tags = item.get("tags", {})
        allowed = {"highway", "railway", "building", "amenity", "leisure", "natural", "access", "area", "shop", "office", "tourism", "aeroway"}
        public_venue = tags.get("amenity") in {"school", "college", "university", "hospital", "restaurant", "cafe", "pub", "bar", "fast_food"} or "shop" in tags or "office" in tags or tags.get("tourism") in {"theme_park", "zoo", "resort", "hotel"} or tags.get("aeroway") in {"aerodrome", "terminal"} or tags.get("leisure") in {"stadium", "sports_centre"}
        tags = {k: v for k, v in tags.items() if k in allowed or k == "name" and public_venue}
        if "name" in tags:
            tags["name"] = str(tags["name"])[:160]
        feature = {"type": "relation" if item.get("type")=="relation" else "way", "tags": tags, "geometry": geometry,
                         "bounds": {"minlat": min(p["lat"] for p in geometry),
                                    "maxlat": max(p["lat"] for p in geometry),
                                    "minlon": min(p["lon"] for p in geometry),
                                    "maxlon": max(p["lon"] for p in geometry)}}
        if isinstance(item.get("id"), int) and item["id"] > 0:
            feature["id"] = item["id"]
        if item.get("holes"):
            validated = clean_document({"elements": [{"geometry": ring} for ring in item["holes"]]})
            if len(validated) != len(item["holes"]):
                raise ValueError("Invalid context holes")
            feature["holes"] = [ring["geometry"] for ring in validated]
        elements.append(feature)
    return elements


def clean_overpass(document):
    """Join relation rings; disconnected or incomplete boundaries never imply a site."""
    if document.get("remark") or not isinstance(document.get("elements"), list):
        raise ValueError("Incomplete context area")
    elements = []
    def joined(parts):
        pending = [list(part) for part in parts if isinstance(part, list) and len(part) >= 2]
        rings = []
        while pending:
            ring = pending.pop()
            while ring[0] != ring[-1]:
                found = False
                for i, part in enumerate(pending):
                    if ring[-1] == part[0]: ring += part[1:]
                    elif ring[-1] == part[-1]: ring += list(reversed(part))[1:]
                    elif ring[0] == part[-1]: ring = part[:-1] + ring
                    elif ring[0] == part[0]: ring = list(reversed(part))[:-1] + ring
                    else: continue
                    pending.pop(i); found = True; break
                if not found: raise ValueError("Incomplete site ring")
            if len(ring) < 4: raise ValueError("Invalid site ring")
            rings.append(ring)
        return rings
    def inside(point, ring):
        x,y = point["lon"],point["lat"]
        result = False
        for a,b in zip(ring,ring[1:]):
            if (a["lat"] > y) != (b["lat"] > y) and x < (b["lon"]-a["lon"])*(y-a["lat"])/(b["lat"]-a["lat"])+a["lon"]:
                result = not result
        return result
    for item in document["elements"]:
        if item.get("type") != "relation":
            elements.append(item); continue
        try:
            members = item.get("members", [])
            outer = joined([m.get("geometry") for m in members if m.get("type")=="way" and m.get("role","") in ("", "outer")])
            holes = joined([m.get("geometry") for m in members if m.get("type")=="way" and m.get("role")=="inner"])
            for ring in outer:
                elements.append({"type":"relation", "id":item.get("id"), "tags":item.get("tags",{}),
                                 "geometry":ring, "holes":[hole for hole in holes if inside(hole[0],ring)]})
        except (KeyError, TypeError, ValueError):
            # Retain the other valid features; this relation contributes no boundary.
            continue
    return clean_document({"elements":elements})


def fetch_elements(bounds):
    if PROVIDER_FORMAT == "postpass":
        return fetch_postpass_elements(bounds)
    if PROVIDER_FORMAT != "overpass":
        raise ValueError("Unsupported context provider format")
    bbox = ",".join(f"{v:.3f}" for v in bounds)
    query = f"[out:json][timeout:12];(way[highway]({bbox});way[railway=rail]({bbox});" \
            f"way[building]({bbox});way[amenity=parking]({bbox});way[leisure]({bbox});way[natural]({bbox});" \
            f'way[amenity~"^(school|college|university|hospital)$"]({bbox});way[shop=mall]({bbox});way[office]({bbox});way[tourism~"^(theme_park|zoo|resort|hotel)$"]({bbox});way[aeroway~"^(aerodrome|terminal)$"]({bbox});' \
            f'relation[amenity~"^(school|college|university|hospital)$"]({bbox});relation[shop=mall]({bbox});relation[building]({bbox});relation[tourism~"^(theme_park|zoo|resort|hotel)$"]({bbox});relation[aeroway~"^(aerodrome|terminal)$"]({bbox});relation[leisure~"^(stadium|sports_centre)$"]({bbox}););out geom;'
    with httpx.Client(timeout=httpx.Timeout(18, connect=5), follow_redirects=False) as client:
        with client.stream("POST", UPSTREAM, data={"data": query},
                           headers={"User-Agent": "Roadprints-SharedContext/1"}) as response:
            response.raise_for_status()
            raw = bytearray()
            for chunk in response.iter_bytes():
                raw.extend(chunk)
                if len(raw) > MAX_BYTES:
                    raise ValueError("Context area too large")
    return clean_overpass(json.loads(raw))


def postpass_query(bounds):
    south, west, north, east = bounds
    # Only validated public area bounds enter this fixed read-only query.
    bbox = f"ST_MakeEnvelope({west:.3f},{south:.3f},{east:.3f},{north:.3f},4326)"
    return "SELECT osm_id,osm_type,(SELECT jsonb_object_agg(key,value) FROM jsonb_each(tags) " \
           "WHERE key IN ('highway','railway','building','amenity','leisure','natural','access','area','shop','office','tourism','aeroway') " \
           "OR key='name' AND (tags->>'amenity' IN ('school','college','university','hospital','restaurant','cafe','pub','bar','fast_food') OR tags ? 'shop' OR tags ? 'office' OR tags->>'tourism' IN ('theme_park','zoo','resort','hotel') OR tags->>'aeroway' IN ('aerodrome','terminal') OR tags->>'leisure' IN ('stadium','sports_centre'))) AS tags," \
           f"CASE WHEN GeometryType(geom) IN ('POLYGON','MULTIPOLYGON') THEN geom ELSE " \
           f"ST_Multi(ST_CollectionExtract(ST_Intersection(geom,{bbox}),2)) END AS geom " \
           f"FROM postpass_linepolygon WHERE geom && {bbox} AND " \
           "(tags ?| ARRAY['highway','building','leisure','natural'] OR tags->>'railway'='rail' " \
           "OR tags->>'amenity' IN ('parking','school','college','university','hospital') " \
           "OR tags->>'shop'='mall' OR tags ? 'office' OR tags->>'tourism' IN ('theme_park','zoo','resort','hotel') OR tags->>'aeroway' IN ('aerodrome','terminal')) LIMIT 10001"


def clean_postpass(document):
    features = document.get("features")
    if document.get("type") != "FeatureCollection" or not isinstance(features,list) or len(features)>=10001:
        raise ValueError("Incomplete Postpass context area")
    elements=[]
    for feature in features:
        geometry=feature.get("geometry") or {}
        kind=geometry.get("type")
        coordinates=geometry.get("coordinates")
        properties=feature.get("properties") or {}
        if kind=="LineString": parts=[(coordinates,[])]
        elif kind=="MultiLineString": parts=[(line,[]) for line in coordinates]
        elif kind=="Polygon": parts=[(coordinates[0],coordinates[1:])]
        elif kind=="MultiPolygon": parts=[(polygon[0],polygon[1:]) for polygon in coordinates]
        else: raise ValueError("Unsupported context geometry")
        for outer,holes in parts:
            def ring(points):
                if not isinstance(points,list) or any(not isinstance(p,list) or len(p)<2 for p in points):
                    raise ValueError("Invalid Postpass geometry")
                return [{"lon":p[0],"lat":p[1]} for p in points]
            elements.append({"type":"relation" if properties.get("osm_type")=="R" else "way",
                             "id":properties.get("osm_id"),"tags":properties.get("tags") or {},
                             "geometry":ring(outer),"holes":[ring(hole) for hole in holes]})
    return clean_document({"elements":elements})


def fetch_postpass_elements(bounds):
    with httpx.Client(timeout=httpx.Timeout(18,connect=5),follow_redirects=False) as client:
        with client.stream("GET",UPSTREAM,params={"data":postpass_query(bounds),"cache_for":"86400"},
                           headers={"User-Agent":"Roadprints-SharedContext/1"}) as response:
            response.raise_for_status()
            raw=bytearray()
            for chunk in response.iter_bytes():
                raw.extend(chunk)
                if len(raw)>MAX_BYTES:raise ValueError("Context area too large")
    return clean_postpass(json.loads(raw))


def connection_failure_category(exc):
    """A bounded diagnostic code, never exception text, query or coordinates."""
    seen = set()
    while exc is not None and id(exc) not in seen:
        seen.add(id(exc))
        message = str(exc).lower()
        for category, markers in (
            ("tls_verification", ("certificate verify failed", "certificate_verify_failed")),
            ("dns", ("name or service not known", "temporary failure in name resolution", "nodename nor servname")),
            ("connection_refused", ("connection refused",)),
            ("network_unreachable", ("network is unreachable", "no route to host")),
            ("timeout", ("timed out", "timeout")),
        ):
            if any(marker in message for marker in markers):
                return category
        exc = exc.__cause__ or exc.__context__
    return "unclassified"


def read_area(cursor, key, now):
    cursor.execute("SELECT document, refreshed_at FROM map_context_areas WHERE area_key=%s", (key,))
    row = cursor.fetchone()
    if row and now - row[1] < TTL and row[0].get("feature_revision",0) >= FEATURE_REVISION:
        return row[0]
    return None


def shared_area(database_url, lat_cell, lon_cell, fetch=fetch_elements, clock=time.time):
    bounds = tile_bounds(lat_cell, lon_cell)
    if not database_url:
        raise ContextUnavailable()
    key = f"v1:{lat_cell}:{lon_cell}"
    now = int(clock())
    try:
        with psycopg.connect(database_url, connect_timeout=5, autocommit=True) as connection:
            with connection.cursor() as cursor:
                cached = read_area(cursor, key, now)
                if cached is not None:
                    return cached
                # Session lock survives commits; release by closing connection.
                cursor.execute("SELECT pg_try_advisory_lock(%s)", (LOCK,))
                if not cursor.fetchone()[0]:
                    raise ContextUnavailable(60)
                cached = read_area(cursor, key, now)
                if cached is not None:
                    return cached
                cursor.execute("SELECT day, requests, next_allowed FROM map_context_budget WHERE singleton=true")
                day, requests, next_allowed = cursor.fetchone()
                cursor.execute("SELECT failures,next_allowed FROM map_context_provider_budget WHERE provider=%s",(UPSTREAM,))
                provider=cursor.fetchone()
                failures,provider_next=provider if provider else (0,0)
                next_allowed=max(next_allowed,provider_next)
                today = now // 86400
                if day != today:
                    requests = 0
                if now < next_allowed:
                    raise ContextUnavailable(next_allowed - now)
                if requests >= DAILY_LIMIT:
                    raise ContextUnavailable((today + 1) * 86400 - now)
                # Commit reservation before network I/O, including unsuccessful calls.
                cursor.execute("UPDATE map_context_budget SET day=%s,requests=%s,next_allowed=%s WHERE singleton=true",
                               (today, requests + 1, now + 60))
                cursor.execute("INSERT INTO map_context_provider_budget(provider) VALUES(%s) ON CONFLICT DO NOTHING",(UPSTREAM,))
                try:
                    elements = fetch(bounds)
                    document = {"schema_version": VERSION, "feature_revision": FEATURE_REVISION, "area_key": key, "bounds": bounds,
                                "elements": elements, "refreshed_at": now, "expires_at": now + TTL,
                                "attribution": "© OpenStreetMap contributors"}
                    if len(json.dumps(document).encode()) > MAX_BYTES:
                        raise ValueError("Context area too large")
                except Exception as exc:
                    causes = []
                    cause = exc
                    while cause is not None and len(causes) < 6:
                        errno = getattr(cause, "errno", None)
                        causes.append(cause.__class__.__name__ + (f"({errno})" if isinstance(errno, int) else ""))
                        cause = cause.__cause__
                    logger.warning("Shared context fill failed: %s; upstream_status=%s; causes=%s; category=%s",
                                   exc.__class__.__name__, getattr(getattr(exc,"response",None),"status_code",None),
                                   ",".join(causes), connection_failure_category(exc))
                    delay = min(14400, 600 * 2 ** min(failures, 5))
                    cursor.execute("UPDATE map_context_provider_budget SET failures=%s,next_allowed=%s WHERE provider=%s",
                                   (min(failures + 1, 6), int(clock()) + delay, UPSTREAM))
                    raise ContextUnavailable(delay) from exc
                cursor.execute("INSERT INTO map_context_areas(area_key,document,refreshed_at) VALUES(%s,%s,%s) "
                               "ON CONFLICT(area_key) DO UPDATE SET document=EXCLUDED.document,refreshed_at=EXCLUDED.refreshed_at",
                               (key, Jsonb(document), now))
                cursor.execute("UPDATE map_context_provider_budget SET failures=0 WHERE provider=%s",(UPSTREAM,))
                cursor.execute("DELETE FROM map_context_areas WHERE area_key IN (SELECT area_key FROM map_context_areas "
                               "ORDER BY refreshed_at DESC,area_key OFFSET %s)", (MAX_AREAS,))
                cursor.execute("DELETE FROM map_context_areas WHERE area_key IN (SELECT area_key FROM "
                               "(SELECT area_key,SUM(pg_column_size(document)) OVER "
                               "(ORDER BY refreshed_at DESC,area_key) AS bytes FROM map_context_areas) sizes "
                               "WHERE bytes>%s)", (MAX_CACHE_BYTES,))
                return document
    except ContextUnavailable:
        raise
    except psycopg.Error as exc:
        raise ContextUnavailable() from exc
