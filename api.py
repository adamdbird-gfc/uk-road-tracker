import os
from typing import List

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

OSRM_BASE_URL = os.getenv("OSRM_BASE_URL", "https://router.project-osrm.org")
OSRM_CHUNK_SIZE = int(os.getenv("OSRM_CHUNK_SIZE", "8"))
OSRM_CHUNK_OVERLAP = int(os.getenv("OSRM_CHUNK_OVERLAP", "2"))
RADIUS_ATTEMPTS = [20, 10, 5]

app = FastAPI(title="UK Road Tracker API", version="0.4.0")

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

def chunk_points(points: List[Point]) -> List[List[Point]]:
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

async def request_match(client, points, radius):
    coordinates = ";".join(f"{p.lng:.7f},{p.lat:.7f}" for p in points)
    radiuses = ";".join(str(radius) for _ in points)

    url = f"{OSRM_BASE_URL.rstrip('/')}/match/v1/driving/{coordinates}"
    params = {
        "overview": "full",
        "geometries": "geojson",
        "steps": "false",
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

async def osrm_match_chunk(client, points: List[Point], chunk_index: int):
    last_error = None

    for radius in RADIUS_ATTEMPTS:
        response, data = await request_match(client, points, radius)

        if response.status_code == 200 and data.get("code") == "Ok":
            return data, radius

        code = data.get("code", f"HTTP {response.status_code}")
        message = data.get("message", response.text[:300] or "No details returned.")
        last_error = f"{code}: {message}"

        # If the public demo rejects the radius search size, retry smaller.
        if code == "TooBig" and "Radius search size" in message:
            continue

        # For NoSegment/NoMatch, a smaller radius won't help, so stop here.
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
        "version": "0.4.0",
        "chunk_size": OSRM_CHUNK_SIZE,
        "chunk_overlap": OSRM_CHUNK_OVERLAP,
        "radius_attempts_m": RADIUS_ATTEMPTS,
    }

@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.4.0"}

@app.post("/match")
async def match_journey(payload: MatchRequest):
    if len(payload.points) < 2:
        raise HTTPException(status_code=400, detail="At least two coordinates are required.")

    chunks = chunk_points(payload.points)

    features = []
    matched_distance_m = 0.0
    matched_tracepoints = 0
    tracepoints_seen = 0
    radiuses_used = []

    try:
        async with httpx.AsyncClient(timeout=45.0) as client:
            for chunk_index, chunk in enumerate(chunks):
                data, radius_used = await osrm_match_chunk(client, chunk, chunk_index)
                radiuses_used.append(radius_used)

                for matching_index, matching in enumerate(data.get("matchings") or []):
                    geometry = matching.get("geometry")
                    if not geometry:
                        continue

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

                tracepoints = data.get("tracepoints") or []
                matched_tracepoints += sum(tp is not None for tp in tracepoints)
                tracepoints_seen += len(tracepoints)

    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"Road matcher could not be reached: {exc}",
        ) from exc

    if not features:
        raise HTTPException(
            status_code=422,
            detail="The matcher returned no route geometry.",
        )

    return {
        "status": "ok",
        "input_points": len(payload.points),
        "chunks_used": len(chunks),
        "points_sent_to_matcher": tracepoints_seen,
        "matched_tracepoints": matched_tracepoints,
        "matched_distance_m": round(matched_distance_m, 1),
        "radiuses_used_m": radiuses_used,
        "geojson": {
            "type": "FeatureCollection",
            "features": features,
        },
    }
