import asyncio
import json
import logging
import os
import re
import time
from collections import defaultdict, deque
from typing import Any, List

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from database import (
    database_state,
    find_settlements_for_geometry,
    initialise_database,
    match_pedestrian_reference,
    reference_catalogue_status,
    request_settlement_inventory,
    settlement_boundary_geojson,
    settlement_inventory_status,
    settlement_metadata,
)
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

OSRM_BASE_URL = os.getenv("OSRM_BASE_URL", "https://router.project-osrm.org")
FOOT_OSRM_BASE_URL = os.getenv("FOOT_OSRM_BASE_URL", "https://routing.openstreetmap.de/routed-foot")
# Public OSRM Match accepts substantially more than a handful of coordinates.
# Thirty-two points keeps the request URL comfortably small while avoiding the
# four-to-six tiny upstream calls that an eight-point chunk created for an
# ordinary Timeline journey.  The two-point overlap preserves continuity at
# each boundary; it does not alter the local source route or saved geometry.
OSRM_CHUNK_SIZE = int(os.getenv("OSRM_CHUNK_SIZE", "32"))
OSRM_CHUNK_OVERLAP = int(os.getenv("OSRM_CHUNK_OVERLAP", "2"))
RADIUS_ATTEMPTS = [20, 10, 5]
FOOT_RADIUS_ATTEMPTS = [20, 10, 5]
OVERPASS_INTERPRETER_URLS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.nchc.org.tw/api/interpreter",
]
CANONICAL_A_ROAD_CACHE = {}
logger = logging.getLogger("roadprints.api")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
MAX_MATCH_POINTS = int(os.getenv("MAX_MATCH_POINTS", "500"))
RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "60"))
RATE_LIMIT_WINDOW_SECONDS = int(os.getenv("RATE_LIMIT_WINDOW_SECONDS", "60"))
RATE_LIMITED_PATHS = {"/match", "/match-walking", "/settlements-for-geometry", "/import-coordinator"}
request_windows = defaultdict(deque)

app = FastAPI(title="UK Road Tracker API", version="0.11.0")
PLACE_NAME_CACHE = {}
ALLOWED_ORIGINS = [origin.strip() for origin in os.getenv("ALLOWED_ORIGINS", "https://adamdbird-gfc.github.io,http://localhost:8000,http://127.0.0.1:8000").split(",") if origin.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

class Point(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)

class MatchRequest(BaseModel):
    points: List[Point] = Field(min_length=2, max_length=MAX_MATCH_POINTS)

class SettlementGeometryRequest(BaseModel):
    geometry: dict[str, Any]

class ImportCoordinatorHandshake(BaseModel):
    """Compatibility handshake deliberately limited to non-personal data."""
    contract_version: int = Field(ge=1, le=1)
    contains_personal_data: bool = False

class SettlementInventoryRequest(BaseModel):
    settlement_code: str = Field(pattern=r"^[A-Z]\d{8}$")

class SettlementMetadataRequest(BaseModel):
    names: List[str] = Field(min_length=1, max_length=500)

def chunk_points(points):
    if len(points) <= OSRM_CHUNK_SIZE:
        return [points]
    chunks = []
    step = max(1, OSRM_CHUNK_SIZE - OSRM_CHUNK_OVERLAP)
    start = 0
    while start < len(points):
        end = min(len(points), start + OSRM_CHUNK_SIZE)
        chunk = points[start:end]
        if len(chunk) >= 2:
            chunks.append(chunk)
        if end >= len(points):
            break
        start += step
    return chunks

def motorway_refs(ref):
    if not ref:
        return []
    refs = []
    for part in re.split(r"[;,/]", ref.upper()):
        cleaned = re.sub(r"\s+", "", part.strip())
        # Include both conventional M-roads and A-road motorways such as A1(M).
        if cleaned in {"M6T", "M6TOLL"}:
            refs.append("M6 Toll")
        elif re.fullmatch(r"M\d+[A-Z]?", cleaned) or re.fullmatch(r"A\d+\(M\)", cleaned):
            refs.append(cleaned)
    return refs

def a_road_refs(ref):
    """Return ordinary numbered A-road refs, leaving A(M) with motorways."""
    if not ref:
        return []
    refs = []
    for part in re.split(r"[;,/]", ref.upper()):
        cleaned = re.sub(r"\s+", "", part.strip())
        if re.fullmatch(r"A\d+[A-Z]?", cleaned):
            refs.append(cleaned)
    return refs

def a_road_region(geometry):
    """Classify an A-road step for canonical reference matching.

    The separate island networks reuse A-road numbers, so the region is part
    of the identity. A matched step is wholly within one network.
    """
    coordinates = (geometry or {}).get("coordinates") or []
    while coordinates and isinstance(coordinates[0], list):
        coordinates = coordinates[0]
    if not isinstance(coordinates, list) or len(coordinates) < 2:
        return "GB"
    try:
        lng, lat = float(coordinates[0]), float(coordinates[1])
    except (TypeError, ValueError):
        return "GB"
    return "NI" if -8.5 <= lng <= -5.0 and 53.8 <= lat <= 55.5 else "GB"

async def request_match(client, points, radius, base_url):
    coordinates = ";".join(f"{p.lng:.7f},{p.lat:.7f}" for p in points)
    radiuses = ";".join(str(radius) for _ in points)
    # OSRM profile names in the URL are aliases chosen by the server. The
    # routed-foot instance is built from a pedestrian graph even though its
    # public API path, like the car instance, uses the conventional `driving`
    # alias.
    url = f"{base_url.rstrip('/')}/match/v1/driving/{coordinates}"
    params = {
        "overview": "full",
        "geometries": "geojson",
        "steps": "true",
        "annotations": "false",
        "tidy": "true",
        "gaps": "split",
        "radiuses": radiuses,
    }
    last_error = None
    for attempt in range(3):
        try:
            response = await client.get(url, params=params)
            try:
                data = response.json()
            except ValueError:
                data = {}
            return response, data
        except httpx.TransportError as exc:
            last_error = exc
            if attempt == 2:
                raise
            await asyncio.sleep(1.5 * (attempt + 1))
    raise last_error

async def osrm_match_chunk(client, points, chunk_index, base_url, polite_delay_seconds=0.0, radius_attempts=RADIUS_ATTEMPTS, retry_no_match=False):
    last_error = None
    for attempt_index, radius in enumerate(radius_attempts):
        if attempt_index and polite_delay_seconds:
            await asyncio.sleep(polite_delay_seconds)
        response, data = await request_match(client, points, radius, base_url)
        if response.status_code == 200 and data.get("code") == "Ok":
            return data, radius
        code = data.get("code", f"HTTP {response.status_code}")
        message = data.get("message", response.text[:300] or "No details returned.")
        last_error = f"{code}: {message}"
        if code == "TooBig" and "Radius search size" in message:
            continue
        if retry_no_match and code == "NoMatch":
            continue
        break
    raise HTTPException(
        status_code=422,
        detail=f"Chunk {chunk_index + 1}: {last_error or 'No usable road match was found.'}",
    )

@app.middleware("http")
async def request_guard(request: Request, call_next):
    if request.url.path in RATE_LIMITED_PATHS:
        client = request.client.host if request.client else "unknown"
        now = time.monotonic()
        window = request_windows[client]
        while window and now - window[0] >= RATE_LIMIT_WINDOW_SECONDS:
            window.popleft()
        if len(window) >= RATE_LIMIT_REQUESTS:
            return JSONResponse(status_code=429, content={"detail": "Too many requests. Please retry shortly."})
        window.append(now)
    started = time.monotonic()
    response = await call_next(request)
    logger.info("request method=%s path=%s status=%s duration_ms=%d", request.method, request.url.path, response.status_code, (time.monotonic() - started) * 1000)
    return response

@app.on_event("startup")
async def startup_database() -> None:
    initialise_database()

@app.get("/")
async def root():
    return {
        "service": "UK Road Tracker API",
        "status": "ok",
        "matcher": "OSRM public demo",
        "version": "0.9.0",
        "feature": "UK motorway and A-road matching plus isolated pedestrian matching",
        "chunk_size": OSRM_CHUNK_SIZE,
    }

@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.11.0", "database": database_state}

@app.get("/reference-catalogue/status")
async def reference_catalogue():
    """Expose readiness of shared public UK references only."""
    return {
        "catalogue": reference_catalogue_status(),
        "contains_personal_data": False,
    }

@app.get("/import-coordinator/capabilities")
async def import_coordinator_capabilities():
    """Describe the privacy-safe first import slice without receiving travel data."""
    return {
        "contract_version": 1,
        "mode": "client_persisted",
        "accepted_source": "google_timeline",
        "accepts_personal_data": False,
        "remote_retention": "none",
        "message": "Timeline files, journeys and import progress remain on this device.",
    }

@app.post("/import-coordinator")
async def import_coordinator_handshake(payload: ImportCoordinatorHandshake):
    """Reject any attempt to introduce hidden journey uploads in this prototype."""
    if payload.contains_personal_data:
        raise HTTPException(
            status_code=400,
            detail="This coordinator does not accept Timeline files, journeys, routes or personal data.",
        )
    return {
        "compatible": True,
        "contract_version": payload.contract_version,
        "storage": "on_device",
        "remote_retention": "none",
    }

@app.get("/canonical-a-road/{road_ref}")
async def canonical_a_road(road_ref: str):
    """Return compact, exact-reference A-road geometry for the browser cache."""
    ref = re.sub(r"\s+", "", road_ref.upper())
    if not re.fullmatch(r"A\d+[A-Z]?", ref):
        raise HTTPException(status_code=400, detail="A numbered A-road reference is required.")
    if ref in CANONICAL_A_ROAD_CACHE:
        return CANONICAL_A_ROAD_CACHE[ref]

    # The boundary-aware regex also catches ways carrying multiple route refs.
    ref_pattern = rf"(^|[;,/]){re.escape(ref)}($|[;,/])"
    query = (
        "[out:json][timeout:60];"
        'way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"]'
        f'["ref"~"{ref_pattern}"](49.8,-8.7,60.9,2.1);out geom;'
    )
    elements = None
    last_error = None
    for endpoint in OVERPASS_INTERPRETER_URLS:
        try:
            async with httpx.AsyncClient(timeout=75.0, headers={"User-Agent": "Roadprints/0.9 (+https://adamdbird-gfc.github.io/uk-road-tracker/)"}) as client:
                response = await client.post(endpoint, data={"data": query})
            if response.status_code != 200:
                last_error = f"HTTP {response.status_code}"
                continue
            elements = response.json().get("elements") or []
            break
        except (httpx.HTTPError, ValueError) as exc:
            last_error = str(exc)
    if elements is None:
        raise HTTPException(status_code=502, detail="A-road reference service could not be reached. Please retry shortly.")

    ways = []
    for element in elements:
        coords = [
            [float(point["lon"]), float(point["lat"])]
            for point in element.get("geometry") or []
            if "lon" in point and "lat" in point
        ]
        if len(coords) >= 2:
            ways.append({"id": element.get("id"), "coords": coords})
    if not ways:
        raise HTTPException(status_code=404, detail=f"No OpenStreetMap reference geometry found for {ref}.")
    result = {"ref": ref, "ways": ways}
    CANONICAL_A_ROAD_CACHE[ref] = result
    return result

@app.get("/place-name")
async def place_name(lat: float, lng: float):
    key = f"{lat:.3f},{lng:.3f}"
    if key in PLACE_NAME_CACHE:
        return {"name": PLACE_NAME_CACHE[key]}
    try:
        async with httpx.AsyncClient(timeout=15.0, headers={"User-Agent": "Roadprints/0.7 (+https://adamdbird-gfc.github.io/uk-road-tracker/)"}) as client:
            response = await client.get(
                "https://nominatim.openstreetmap.org/reverse",
                params={"format": "jsonv2", "lat": lat, "lon": lng, "zoom": 14, "addressdetails": 1},
            )
        data = response.json() if response.status_code == 200 else {}
        address = data.get("address") or {}
        name = next((address.get(field) for field in ("town", "village", "suburb", "city_district", "city", "county", "state") if address.get(field)), None)
    except (httpx.HTTPError, ValueError):
        name = None
    result = name or "Local area"
    PLACE_NAME_CACHE[key] = result
    return {"name": result}

@app.get("/settlement-inventories/{settlement_code}")
async def get_settlement_inventory(settlement_code: str):
    code = settlement_code.upper()
    if not re.fullmatch(r"[A-Z]\d{8}", code):
        raise HTTPException(status_code=400, detail="A valid settlement code is required.")
    inventory = settlement_inventory_status(code)
    if inventory is None:
        raise HTTPException(status_code=404, detail="Settlement inventory status is unavailable.")
    return {"inventory": inventory, "contains_personal_data": False}


@app.get("/settlement-boundaries/{settlement_code}")
async def get_settlement_boundary(settlement_code: str):
    code = settlement_code.upper()
    if not re.fullmatch(r"[A-Z]\d{8}", code):
        raise HTTPException(status_code=400, detail="A valid settlement code is required.")
    boundary = settlement_boundary_geojson(code)
    if boundary is None:
        raise HTTPException(status_code=404, detail="Settlement boundary is unavailable.")
    return {"boundary": boundary, "contains_personal_data": False}


@app.post("/settlement-inventories/request")
async def request_inventory(payload: SettlementInventoryRequest):
    inventory = request_settlement_inventory(payload.settlement_code.upper())
    if inventory is None:
        raise HTTPException(status_code=404, detail="Settlement inventory request could not be registered.")
    return {"inventory": inventory, "contains_personal_data": False}


@app.post("/settlement-metadata")
async def get_settlement_metadata(payload: SettlementMetadataRequest):
    metadata = settlement_metadata(payload.names)
    if metadata is None:
        raise HTTPException(status_code=503, detail="Settlement metadata is unavailable.")
    return {"settlements": metadata, "contains_personal_data": False}


@app.post("/settlements-for-geometry")
async def settlements_for_geometry(payload: SettlementGeometryRequest):
    geometry=payload.geometry or {}
    geometry_type=geometry.get("type")
    coordinates=geometry.get("coordinates")
    if geometry_type not in {"LineString","MultiLineString"} or not coordinates:
        raise HTTPException(status_code=400,detail="A matched line geometry is required.")
    # The shared PostGIS catalogue is preferred whenever it has the relevant
    # public boundary. It reads the transient geometry and returns facts only;
    # no Journey or route record is stored remotely.
    catalogue_matches=find_settlements_for_geometry(geometry)
    if catalogue_matches:
        return {"settlements":catalogue_matches}

    arc_type="esriGeometryPolyline"
    paths=[coordinates] if geometry_type=="LineString" else coordinates
    url="https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0/query"
    params={"geometry":json.dumps({"paths":paths}),"geometryType":arc_type,"inSR":"4326","spatialRel":"esriSpatialRelIntersects","outFields":"BUA22CD,BUA22NM","returnGeometry":"false","f":"json"}
    try:
        # A named road may have many matched step geometries.  Sending the
        # polyline in a GET query can exceed an intermediary URL limit and be
        # reported as a misleading 404. ArcGIS accepts the same parameters as
        # a form POST, keeping the geometry in the request body.
        async with httpx.AsyncClient(timeout=20.0) as client: response=await client.post(url,data=params)
        if response.status_code!=200: raise HTTPException(status_code=502,detail=f"ONS boundary query returned HTTP {response.status_code}.")
        return {"settlements":[{"code":f["attributes"].get("BUA22CD"),"name":f["attributes"].get("BUA22NM")} for f in response.json().get("features",[])]}
    except httpx.TimeoutException:
        raise HTTPException(status_code=504,detail="ONS boundary query timed out.")
    except httpx.HTTPError:
        raise HTTPException(status_code=502,detail="ONS boundary query failed.")

@app.post("/match")
async def match_journey(payload: MatchRequest):
    return await match_payload(payload, OSRM_BASE_URL, include_motorways=True)

@app.post("/match-walking")
async def match_walking_activity(payload: MatchRequest):
    # The preloaded shared public reference is used first. The request is
    # read-only and transient: no Timeline route data is retained by Postgres.
    reference_match = match_pedestrian_reference(
        [{"lat": point.lat, "lng": point.lng} for point in payload.points]
    )
    if reference_match:
        return reference_match
    # Other areas retain the established pedestrian-router path until their
    # equivalent public reference catalogue is loaded and verified.
    return await match_payload(
        payload,
        FOOT_OSRM_BASE_URL,
        include_motorways=False,
        polite_delay_seconds=1.05,
        radius_attempts=FOOT_RADIUS_ATTEMPTS,
        retry_no_match=True,
    )

@app.get("/diagnostics/foot-router")
async def foot_router_diagnostic():
    """Temporary connectivity check for the third-party pedestrian router."""
    url = f"{FOOT_OSRM_BASE_URL.rstrip('/')}/nearest/v1/driving/0.3700,51.4400"
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(url, params={"number": 1})
        return {"reachable": True, "status": response.status_code}
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Foot-router connection failed: {exc.__class__.__name__}: {exc}",
        ) from exc

@app.get("/diagnostics/foot-router-match")
async def foot_router_match_diagnostic():
    """Temporary end-to-end test of the same OSRM Match endpoint used by walks."""
    points = [
        Point(lat=51.4400, lng=0.3700),
        Point(lat=51.4405, lng=0.3710),
    ]
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            response, data = await request_match(client, points, 45, FOOT_OSRM_BASE_URL)
        return {
            "reachable": True,
            "status": response.status_code,
            "matcher_code": data.get("code"),
            "matcher_message": data.get("message"),
        }
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Foot-router match failed: {exc.__class__.__name__}: {exc}",
        ) from exc

async def match_payload(
    payload: MatchRequest,
    base_url: str,
    include_motorways: bool,
    polite_delay_seconds: float = 0.0,
    radius_attempts=RADIUS_ATTEMPTS,
    retry_no_match: bool = False,
):
    if len(payload.points) < 2:
        raise HTTPException(status_code=400, detail="At least two coordinates are required.")

    chunks = chunk_points(payload.points)
    features = []
    motorway_features = []
    a_road_features = []
    road_features = []
    other_road_distance_m = 0.0
    matched_distance_m = 0.0
    matched_tracepoints = 0
    tracepoints_seen = 0

    try:
        async with httpx.AsyncClient(
            timeout=45.0,
            headers={"User-Agent": "Roadprints-POC/0.7 (+https://adamdbird-gfc.github.io/uk-road-tracker/)"},
        ) as client:
            for chunk_index, chunk in enumerate(chunks):
                if chunk_index and polite_delay_seconds:
                    await asyncio.sleep(polite_delay_seconds)
                data, radius_used = await osrm_match_chunk(
                    client,
                    chunk,
                    chunk_index,
                    base_url,
                    polite_delay_seconds,
                    radius_attempts,
                    retry_no_match,
                )

                for matching_index, matching in enumerate(data.get("matchings") or []):
                    geometry = matching.get("geometry")
                    if geometry:
                        distance = float(matching.get("distance") or 0.0)
                        matched_distance_m += distance
                        features.append({
                            "type": "Feature",
                            "properties": {
                                "chunk_index": chunk_index,
                                "matching_index": matching_index,
                                "confidence": matching.get("confidence"),
                                "distance_m": distance,
                                "radius_m": radius_used,
                            },
                            "geometry": geometry,
                        })

                    for leg in matching.get("legs") or []:
                        for step in leg.get("steps") or []:
                            motorway_road_refs = motorway_refs(step.get("ref"))
                            a_road_refs_for_step = a_road_refs(step.get("ref"))
                            step_geometry = step.get("geometry")
                            if not step_geometry:
                                continue
                            # Retain the OSRM step attribution for Road discovery.
                            # Unlike the route overview, this identifies the actual
                            # named or numbered road beneath each part of the trip.
                            road_features.append({
                                "type": "Feature",
                                "properties": {
                                    "road_ref": step.get("ref") or "",
                                    "name": step.get("name") or "",
                                    "distance_m": float(step.get("distance") or 0.0),
                                    "chunk_index": chunk_index,
                                },
                                "geometry": step_geometry,
                            })
                            if not include_motorways:
                                continue
                            for road_ref in motorway_road_refs:
                                motorway_features.append({
                                    "type": "Feature",
                                    "properties": {
                                        "road_ref": road_ref,
                                        "name": step.get("name") or "",
                                        "distance_m": float(step.get("distance") or 0.0),
                                        "chunk_index": chunk_index,
                                    },
                                    "geometry": step_geometry,
                                })
                            for road_ref in a_road_refs_for_step:
                                a_road_features.append({
                                    "type": "Feature",
                                    "properties": {
                                        "road_ref": road_ref,
                                        "name": step.get("name") or "",
                                        "distance_m": float(step.get("distance") or 0.0),
                                        "chunk_index": chunk_index,
                                        "road_region": a_road_region(step_geometry),
                                    },
                                    "geometry": step_geometry,
                                })
                            if not motorway_road_refs and not a_road_refs_for_step:
                                # Other roads only feed the aggregate. Returning
                                # their step geometry duplicates almost every
                                # ordinary journey, which is prohibitively heavy
                                # on a phone during a large import.
                                other_road_distance_m += float(step.get("distance") or 0.0)

                tracepoints = data.get("tracepoints") or []
                matched_tracepoints += sum(tp is not None for tp in tracepoints)
                tracepoints_seen += len(tracepoints)

    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Road matcher could not be reached: {exc}") from exc

    if not features:
        raise HTTPException(status_code=422, detail="The matcher returned no route geometry.")

    return {
        "status": "ok",
        "input_points": len(payload.points),
        "chunks_used": len(chunks),
        "points_sent_to_matcher": tracepoints_seen,
        "matched_tracepoints": matched_tracepoints,
        "matched_distance_m": round(matched_distance_m, 1),
        "geojson": {"type": "FeatureCollection", "features": features},
        "motorway_geojson": {"type": "FeatureCollection", "features": motorway_features},
        "a_road_geojson": {"type": "FeatureCollection", "features": a_road_features},
        "road_geojson": {"type": "FeatureCollection", "features": road_features},
        "other_road_distance_m": round(other_road_distance_m, 1),
    }