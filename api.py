import os
from typing import List

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

OSRM_BASE_URL = os.getenv("OSRM_BASE_URL", "https://router.project-osrm.org")
MAX_MATCH_POINTS = int(os.getenv("MAX_MATCH_POINTS", "100"))

app = FastAPI(title="UK Road Tracker API", version="0.1.0")

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

def evenly_sample(points: List[Point], maximum: int) -> List[Point]:
    if len(points) <= maximum:
        return points
    last = len(points) - 1
    indexes = {round(i * last / (maximum - 1)) for i in range(maximum)}
    return [points[i] for i in sorted(indexes)]

@app.get("/")
async def root():
    return {"service": "UK Road Tracker API", "status": "ok", "matcher": "OSRM"}

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.post("/match")
async def match_journey(payload: MatchRequest):
    if len(payload.points) < 2:
        raise HTTPException(status_code=400, detail="At least two coordinates are required.")

    points = evenly_sample(payload.points, MAX_MATCH_POINTS)
    coordinates = ";".join(f"{p.lng:.7f},{p.lat:.7f}" for p in points)
    url = f"{OSRM_BASE_URL.rstrip('/')}/match/v1/driving/{coordinates}"
    params = {
        "overview": "full",
        "geometries": "geojson",
        "steps": "false",
        "annotations": "false",
        "tidy": "true",
        "gaps": "split",
    }

    try:
        async with httpx.AsyncClient(timeout=45.0) as client:
            response = await client.get(url, params=params)
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Road matcher could not be reached: {exc}") from exc

    if response.status_code != 200:
        raise HTTPException(status_code=502, detail=f"Road matcher returned HTTP {response.status_code}.")

    data = response.json()
    if data.get("code") != "Ok":
        raise HTTPException(status_code=422, detail=data.get("message") or "No usable road match was found.")

    features = []
    matched_distance_m = 0.0
    for index, matching in enumerate(data.get("matchings") or []):
        geometry = matching.get("geometry")
        if not geometry:
            continue
        distance = float(matching.get("distance") or 0.0)
        matched_distance_m += distance
        features.append({
            "type": "Feature",
            "properties": {
                "matching_index": index,
                "confidence": matching.get("confidence"),
                "distance_m": distance,
            },
            "geometry": geometry,
        })

    if not features:
        raise HTTPException(status_code=422, detail="The matcher returned no route geometry.")

    tracepoints = data.get("tracepoints") or []
    return {
        "status": "ok",
        "input_points": len(payload.points),
        "points_sent_to_matcher": len(points),
        "matched_tracepoints": sum(tp is not None for tp in tracepoints),
        "matched_distance_m": round(matched_distance_m, 1),
        "geojson": {"type": "FeatureCollection", "features": features},
    }
