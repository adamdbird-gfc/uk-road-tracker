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
TTL = 7 * 86400
MAX_BYTES = 4_000_000
MAX_AREAS = 256
MAX_CACHE_BYTES = 64_000_000
DAILY_LIMIT = int(os.getenv("CONTEXT_DAILY_UPSTREAM_LIMIT", "200"))
UPSTREAM = os.getenv("CONTEXT_INTERPRETER_URL", "https://overpass-api.de/api/interpreter")
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
        tags = {k: v for k, v in tags.items() if k in
                {"highway", "railway", "building", "amenity", "leisure", "natural", "access", "area"}}
        elements.append({"type": "way", "tags": tags, "geometry": geometry,
                         "bounds": {"minlat": min(p["lat"] for p in geometry),
                                    "maxlat": max(p["lat"] for p in geometry),
                                    "minlon": min(p["lon"] for p in geometry),
                                    "maxlon": max(p["lon"] for p in geometry)}})
    return elements


def fetch_elements(bounds):
    bbox = ",".join(f"{v:.3f}" for v in bounds)
    query = f"[out:json][timeout:12];(way[highway]({bbox});way[railway=rail]({bbox});" \
            f"way[building]({bbox});way[amenity=parking]({bbox});way[leisure]({bbox});way[natural]({bbox}););out geom;"
    with httpx.Client(timeout=httpx.Timeout(18, connect=5), follow_redirects=False) as client:
        with client.stream("POST", UPSTREAM, data={"data": query},
                           headers={"User-Agent": "Roadprints-SharedContext/1"}) as response:
            response.raise_for_status()
            raw = bytearray()
            for chunk in response.iter_bytes():
                raw.extend(chunk)
                if len(raw) > MAX_BYTES:
                    raise ValueError("Context area too large")
    return clean_document(json.loads(raw))


def read_area(cursor, key, now):
    cursor.execute("SELECT document, refreshed_at FROM map_context_areas WHERE area_key=%s", (key,))
    row = cursor.fetchone()
    if row and now - row[1] < TTL:
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
                    document = {"schema_version": VERSION, "area_key": key, "bounds": bounds,
                                "elements": elements, "refreshed_at": now, "expires_at": now + TTL,
                                "attribution": "© OpenStreetMap contributors"}
                    if len(json.dumps(document).encode()) > MAX_BYTES:
                        raise ValueError("Context area too large")
                except Exception as exc:
                    causes = []
                    cause = exc
                    while cause is not None and len(causes) < 6:
                        causes.append(cause.__class__.__name__)
                        cause = cause.__cause__
                    logger.warning("Shared context fill failed: %s; upstream_status=%s; causes=%s",
                                   exc.__class__.__name__, getattr(getattr(exc,"response",None),"status_code",None),
                                   ",".join(causes))
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
