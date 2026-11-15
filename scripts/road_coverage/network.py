"""Bounded driving evidence projection onto a versioned local road network."""

from collections import defaultdict
from dataclasses import dataclass
import gzip
import hashlib
import json
import math

POLICY = "road-coverage-driving-pair-v1"
METRES_PER_DEGREE = math.pi * 6_371_008.8 / 180
CELL_M = 50
RADIUS_M = 12
MAX_GAP_MS = 12_000
MAX_PAIR_M = 80
MAX_SPEED_MPS = 55
MIN_PAIR_M = 3
MIN_HEADING_COS = math.cos(math.radians(25))
CONNECT_EPSILON_M = 0.001


def point_at(a, b, fraction):
    return tuple(x + (y - x) * fraction for x, y in zip(a, b))


def cells(a, b, padding=0):
    x0, y0 = (math.floor((min(x, y) - padding) / CELL_M) for x, y in zip(a, b))
    x1, y1 = (math.floor((max(x, y) + padding) / CELL_M) for x, y in zip(a, b))
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            yield x, y


@dataclass(frozen=True)
class Span:
    edge: str
    start_mm: int
    end_mm: int


@dataclass(frozen=True)
class Fix:
    point: tuple[float, float]
    time_ms: int
    accuracy_m: float | None


@dataclass(frozen=True)
class Edge:
    id: str
    start_node: int
    end_node: int
    a: tuple[float, float]
    b: tuple[float, float]
    direction: int = 0
    label: str | None = None
    way: int | None = None
    eligible: bool = True

    @property
    def length_mm(self):
        return round(math.dist(self.a, self.b) * 1000)


@dataclass(frozen=True)
class Candidate:
    span: Span
    along_start: float
    along_end: float
    entry_node: int | None
    exit_node: int | None
    direction_allowed: bool
    separation_m: float


class Network:
    def __init__(self, version, edges, origin=(0.0, 0.0), bounds=None):
        self.version = version
        self.origin = origin
        self.x_scale = METRES_PER_DEGREE * math.cos(math.radians(origin[1]))
        self.bounds = bounds
        self.edges = {}
        self.grid = defaultdict(list)
        self.node_degree = defaultdict(int)
        for edge in edges:
            if edge.length_mm <= 0 or edge.id in self.edges:
                raise ValueError(f"Invalid or duplicate edge {edge.id}")
            self.edges[edge.id] = edge
            self.node_degree[edge.start_node] += 1
            self.node_degree[edge.end_node] += 1
            for cell in cells(edge.a, edge.b):
                self.grid[cell].append(edge)

    @classmethod
    def load(cls, path):
        raw = gzip.decompress(path.read_bytes())
        data = json.loads(raw)
        if data["format"] != 2:
            raise ValueError("Unknown road extract format")
        bounds = data["bounds"]
        origin = ((bounds["minlon"] + bounds["maxlon"]) / 2, (bounds["minlat"] + bounds["maxlat"]) / 2)
        projection = cls("projection", (), origin)
        nodes = {node: projection.project((lon, lat)) for node, lon, lat in data["nodes"]}
        edges = {}
        for way, _, name, ref, direction, refs in data["ways"]:
            for first, second in zip(refs, refs[1:]):
                edge_id = f"osm:{way}:{first}:{second}"
                edge = Edge(edge_id, first, second, nodes[first], nodes[second],
                            direction or 0, name or ref, way, eligible=direction is not None)
                if edge.length_mm > 0:
                    edges[edge_id] = edge
        version = "osm-sha256:" + hashlib.sha256(raw).hexdigest()
        extent = (
            projection.project((bounds["minlon"], bounds["minlat"])),
            projection.project((bounds["maxlon"], bounds["maxlat"])),
        )
        return cls(version, edges.values(), origin, extent)

    def project(self, point):
        return ((point[0] - self.origin[0]) * self.x_scale, (point[1] - self.origin[1]) * METRES_PER_DEGREE)

    def lonlat(self, point):
        return (point[0] / self.x_scale + self.origin[0], point[1] / METRES_PER_DEGREE + self.origin[1])

    def candidates(self, a, b):
        length = math.dist(a, b)
        if not 0 < length <= MAX_PAIR_M:
            return []
        if self.bounds is not None:
            lower, upper = self.bounds
            if any(not lo + RADIUS_M <= axis <= hi - RADIUS_M
                   for point in (a, b) for axis, lo, hi in zip(point, lower, upper)):
                return []  # Neighbouring candidates may be missing at an extract edge.
        x = (b[0] - a[0]) / length
        y = (b[1] - a[1]) / length
        eligible = {edge.id: edge for cell in cells(a, b, RADIUS_M) for edge in self.grid.get(cell, ())}
        found = []
        for edge in eligible.values():
            da = (edge.a[0] - a[0], edge.a[1] - a[1])
            db = (edge.b[0] - a[0], edge.b[1] - a[1])
            first = da[0] * x + da[1] * y
            second = db[0] * x + db[1] * y
            delta = second - first
            if abs(delta) < MIN_HEADING_COS * math.dist(edge.a, edge.b):
                continue
            start, end = max(0.0, min(first, second)), min(length, max(first, second))
            if end - start <= CONNECT_EPSILON_M:
                continue
            fractions = [(position - first) / delta for position in (start, end)]
            cross_start = da[1] * x - da[0] * y
            cross_end = db[1] * x - db[0] * y
            separation = max(abs(cross_start + (cross_end - cross_start) * fraction) for fraction in fractions)
            if separation > RADIUS_M:
                continue
            lo_mm, hi_mm = sorted(round(edge.length_mm * fraction) for fraction in fractions)
            if lo_mm == hi_mm:
                continue
            entry, exit = ((edge.start_node, edge.end_node) if delta > 0 else (edge.end_node, edge.start_node))
            found.append(Candidate(
                Span(edge.id, lo_mm, hi_mm), start, end,
                entry if start == min(first, second) else None,
                exit if end == max(first, second) else None,
                edge.direction == 0 or (edge.direction > 0) == (delta > 0),
                separation,
            ))
        return sorted(found, key=lambda item: (item.along_start, item.along_end, item.span.edge))


def continuous_chain(candidates, length):
    """Require one gap-free chain through actual shared OSM nodes."""
    if not candidates or candidates[0].along_start > CONNECT_EPSILON_M:
        return False
    if length - candidates[-1].along_end > CONNECT_EPSILON_M:
        return False
    return all(
        first.exit_node is not None
        and first.exit_node == second.entry_node
        and abs(first.along_end - second.along_start) <= CONNECT_EPSILON_M
        for first, second in zip(candidates, candidates[1:])
    )
