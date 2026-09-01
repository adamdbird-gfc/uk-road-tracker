import asyncio
import os
import re
from typing import List

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

OSRM_BASE_URL = os.getenv("OSRM_BASE_URL", "https://router.project-osrm.org")
FOOT_OSRM_BASE_URL = os.getenv("FOOT_OSRM_BASE_URL", "https://routing.openstreetmap.de/routed-foot")
OSRM_CHUNK_SIZE = int(os.getenv("OSRM_CHUNK_SIZE", "8"))
OSRM_CHUNK_OVERLAP = int(os.getenv("OSRM_CHUNK_OVERLAP", "2"))
RADIUS_ATTEMPTS = [20, 10, 5]

app = FastAPI(title="UK Road Tracker API", version="0.7.0")
PLACE_NAME_CACHE = {}

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "https://adamdbird-gfc.github.io",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
    ],
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

class Point(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)

class MatchRequest(BaseModel):
    points: List[Point]

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
        if re.fullmatch(r"M\d+[A-Z]?", cleaned) or re.fullmatch(r"A\d+\(M\)", cleaned):
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
    response = await client.get(url, params=params)
    try:
        data = response.json()
    except ValueError:
        data = {}
    return response, data

async def osrm_match_chunk(client, points, chunk_index, base_url, polite_delay_seconds=0.0):
    last_error = None
    for attempt_index, radius in enumerate(RADIUS_ATTEMPTS):
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
        break
    raise HTTPException(
        status_code=422,
        detail=f"Chunk {chunk_index + 1}: {last_error or 'No usable road match was found.'}",
    )

@app.get("/")
async def root():
    return {
        "service": "UK Road Tracker API",
        "status": "ok",
        "matcher": "OSRM public demo",
        "version": "0.7.0",
        "feature": "UK motorway matching plus isolated pedestrian matching",
        "chunk_size": OSRM_CHUNK_SIZE,
    }

@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.7.0"}

@app.get("/place-name")
async def place_name(lat: float, lng: float):
    key = f"{lat:.3f},{lng:.3f}"
    if key in PLACE_NAME_CACHE:
        return {"name": PLACE_NAME_CACHE[key]}
    try:
        async with httpx.AsyncClient(timeout=15.0, headers={"User-Agent": "Roadprints/0.7 (+https://adamdbird-gfc.github.io/uk-road-tracker/)"}) as client:
            response = await client.get(
                "https://nominatim.openstreetmap.org/reverse",
                params={"format": "jsonv2", "lat": lat, "lon": lng, "zoom": 10, "addressdetails": 1},
            )
        data = response.json() if response.status_code == 200 else {}
        address = data.get("address") or {}
        name = next((address.get(field) for field in ("city", "town", "village", "suburb", "county", "state") if address.get(field)), None)
    except (httpx.HTTPError, ValueError):
        name = None
    result = name or "Local area"
    PLACE_NAME_CACHE[key] = result
    return {"name": result}

@app.post("/match")
async def match_journey(payload: MatchRequest):
    return await match_payload(payload, OSRM_BASE_URL, include_motorways=True)

@app.post("/match-walking")
async def match_walking_activity(payload: MatchRequest):
    return await match_payload(
        payload,
        FOOT_OSRM_BASE_URL,
        include_motorways=False,
        polite_delay_seconds=1.05,
    )

async def match_payload(
    payload: MatchRequest,
    base_url: str,
    include_motorways: bool,
    polite_delay_seconds: float = 0.0,
):
    if len(payload.points) < 2:
        raise HTTPException(status_code=400, detail="At least two coordinates are required.")

    chunks = chunk_points(payload.points)
    features = []
    motorway_features = []
    a_road_features = []
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
                            if not include_motorways or not step_geometry:
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
                                    },
                                    "geometry": step_geometry,
                                })

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
    }
