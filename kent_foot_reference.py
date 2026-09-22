"""Kent walking-reference prototype.

This is intentionally a read-through public-reference cache.  It receives a
journey only transiently, fetches no personal data, and retains only public OSM
path geometry in the running service.  The existing pedestrian router remains
the fallback outside Kent or whenever a reference tile is unavailable.
"""
import asyncio
import math
from collections import defaultdict

import httpx

KENT_BOUNDS = (50.80, 0.00, 51.55, 1.55)  # south, west, north, east
TILE_DEGREES = 0.04
MAX_SNAP_DISTANCE_M = 65.0
OVERPASS_URLS = (
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
)
_TILE_CACHE = {}
_TILE_LOCKS = defaultdict(asyncio.Lock)


class KentReferenceUnavailable(Exception):
    """The optional public Kent path reference could not be used."""


def supports_kent_reference(points):
    if len(points) < 2:
        return False
    south, west, north, east = KENT_BOUNDS
    return all(south <= point.lat <= north and west <= point.lng <= east for point in points)


def _tile_key(lat, lng):
    return (math.floor(lat / TILE_DEGREES), math.floor(lng / TILE_DEGREES))


def _tile_bounds(key):
    lat_index, lng_index = key
    south = lat_index * TILE_DEGREES
    west = lng_index * TILE_DEGREES
    return south, west, south + TILE_DEGREES, west + TILE_DEGREES


async def _load_tile(key):
    if key in _TILE_CACHE:
        return _TILE_CACHE[key]

    async with _TILE_LOCKS[key]:
        if key in _TILE_CACHE:
            return _TILE_CACHE[key]
        south, west, north, east = _tile_bounds(key)
        query = (
            "[out:json][timeout:35];"
            'way["highway"~"^(footway|path|pedestrian|steps|living_street|residential|service|unclassified|tertiary|secondary|primary|track)$"]'
            '["foot"!~"^(no|private)$"]'
            f"({south:.5f},{west:.5f},{north:.5f},{east:.5f});"
            "out tags geom;"
        )
        last_error = None
        for url in OVERPASS_URLS:
            try:
                async with httpx.AsyncClient(timeout=50.0, headers={"User-Agent": "Roadprints-Kent-Foot-Prototype/1.0"}) as client:
                    response = await client.post(url, data={"data": query})
                if response.status_code != 200:
                    last_error = f"HTTP {response.status_code}"
                    continue
                elements = response.json().get("elements") or []
                ways = []
                for element in elements:
                    coordinates = [
                        [float(point["lon"]), float(point["lat"])]
                        for point in (element.get("geometry") or [])
                        if "lon" in point and "lat" in point
                    ]
                    if len(coordinates) >= 2:
                        ways.append({
                            "id": element.get("id"),
                            "coordinates": coordinates,
                            "name": (element.get("tags") or {}).get("name", ""),
                            "ref": (element.get("tags") or {}).get("ref", ""),
                            "highway": (element.get("tags") or {}).get("highway", ""),
                        })
                if ways:
                    _TILE_CACHE[key] = ways
                    return ways
                last_error = "no pedestrian-permitted paths returned"
            except (httpx.HTTPError, ValueError) as error:
                last_error = str(error)
        raise KentReferenceUnavailable(last_error or "Kent reference unavailable")


def _metres(lng, lat, origin_lng, origin_lat):
    return (
        (lng - origin_lng) * 111320.0 * math.cos(math.radians(origin_lat)),
        (lat - origin_lat) * 110540.0,
    )


def _nearest_on_segment(point, start, end):
    origin_lng, origin_lat = point.lng, point.lat
    ax, ay = _metres(start[0], start[1], origin_lng, origin_lat)
    bx, by = _metres(end[0], end[1], origin_lng, origin_lat)
    dx, dy = bx - ax, by - ay
    length_squared = dx * dx + dy * dy
    fraction = 0.0 if length_squared == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / length_squared))
    px, py = ax + fraction * dx, ay + fraction * dy
    return math.hypot(px, py), [start[0] + (end[0] - start[0]) * fraction, start[1] + (end[1] - start[1]) * fraction]


def _nearest_way(point, ways):
    best = None
    for way in ways:
        coordinates = way["coordinates"]
        for start, end in zip(coordinates, coordinates[1:]):
            distance, snapped = _nearest_on_segment(point, start, end)
            if best is None or distance < best[0]:
                best = (distance, snapped, way)
    return best


def _trace_features(snapped):
    features, run = [], []
    for point in snapped:
        if point is None:
            if len(run) >= 2:
                features.append({"type": "Feature", "properties": {}, "geometry": {"type": "LineString", "coordinates": run}})
            run = []
            continue
        if run:
            previous = run[-1]
            # Avoid drawing an invented bridge across a large Timeline gap.
            gap = math.hypot((point[0] - previous[0]) * 111320, (point[1] - previous[1]) * 110540)
            if gap > 750:
                if len(run) >= 2:
                    features.append({"type": "Feature", "properties": {}, "geometry": {"type": "LineString", "coordinates": run}})
                run = []
        run.append(point)
    if len(run) >= 2:
        features.append({"type": "Feature", "properties": {}, "geometry": {"type": "LineString", "coordinates": run}})
    return features


async def match_kent_foot_reference(points):
    if not supports_kent_reference(points):
        raise KentReferenceUnavailable("route is outside the Kent prototype")
    keys = {_tile_key(point.lat, point.lng) for point in points}
    tiles = await asyncio.gather(*(_load_tile(key) for key in keys))
    ways = [way for tile in tiles for way in tile]
    snapped, selected = [], {}
    for point in points:
        nearest = _nearest_way(point, ways)
        if nearest is None or nearest[0] > MAX_SNAP_DISTANCE_M:
            snapped.append(None)
            continue
        _, coordinate, way = nearest
        snapped.append(coordinate)
        selected[way["id"]] = way
    trace_features = _trace_features(snapped)
    if not trace_features:
        raise KentReferenceUnavailable("no sufficiently close Kent pedestrian path was found")
    road_features = [
        {
            "type": "Feature",
            "properties": {
                "road_ref": way["ref"],
                "name": way["name"],
                "highway": way["highway"],
                "reference": "kent_public_path_cache",
            },
            "geometry": {"type": "LineString", "coordinates": way["coordinates"]},
        }
        for way in selected.values()
    ]
    matched = sum(point is not None for point in snapped)
    return {
        "geojson": {"type": "FeatureCollection", "features": trace_features},
        "road_geojson": {"type": "FeatureCollection", "features": road_features},
        "matched_distance_km": 0,
        "matched_tracepoints": matched,
        "points_sent": len(points),
        "matcher": "kent_public_path_reference",
        "reference_tiles": len(keys),
        "contains_personal_data": False,
    }
