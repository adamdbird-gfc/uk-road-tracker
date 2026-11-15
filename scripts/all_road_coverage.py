"""Offline Stage 1.2C driving coverage model. Distances here are map offsets only."""

from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
import gzip
import hashlib
import json
from math import ceil, cos, floor, hypot, isfinite, radians
from pathlib import Path

ROAD_CLASSES = frozenset({
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
    "secondary_link", "tertiary_link",
})
DENSITY_LIMIT_M = 35
ALIGN_RADIUS_M = 7
GRID_M = 40
METRES_PER_LATITUDE_DEGREE = 111_195.08
SEAM_M = 0.5
POLICY = "gravesend-observed-pairs-v1"
COMPACT_FORMAT = "roadprints-gravesend-reference-v1"

Point = tuple[float, float]
Interval = tuple[int, int]


def union(intervals):
    """Union integer-millimetre ranges without bridging gaps."""
    result = []
    for start, end in sorted(intervals):
        if result and start <= result[-1][1]:
            result[-1] = result[-1][0], max(result[-1][1], end)
        else:
            result.append((start, end))
    return tuple(result)


@dataclass(frozen=True)
class Edge:
    id: str
    nodes: tuple[str, str]
    label: str | None
    a: Point
    b: Point

    @property
    def length(self):
        return hypot(self.b[0] - self.a[0], self.b[1] - self.a[1])

    @property
    def length_mm(self):
        return round(self.length * 1000)

    def at(self, fraction):
        return tuple(a + fraction * (b - a) for a, b in zip(self.a, self.b))


@dataclass(frozen=True)
class Span:
    edge_id: str
    start_mm: int
    end_mm: int


@dataclass(frozen=True)
class Candidate:
    edge: Edge
    start: float
    end: float
    entry: float
    exit: float

    def span(self):
        low, high = sorted((self.entry, self.exit))
        return Span(self.edge.id, round(low * self.edge.length_mm),
                    round(high * self.edge.length_mm))

    def joins(self, other):
        node = self.edge.nodes[round(self.exit)]
        return (node == other.edge.nodes[round(other.entry)]
                and abs(self.exit - round(self.exit)) * self.edge.length <= SEAM_M
                and abs(other.entry - round(other.entry)) * other.edge.length <= SEAM_M)


def dot(first, second):
    return first[0] * second[0] + first[1] * second[1]


def delta(first, second):
    return second[0] - first[0], second[1] - first[1]


def project(edge, a, b, length, radius):
    """Return only the observed overlap, with no longitudinal endpoint buffer."""
    axis = delta(edge.a, edge.b)
    step = delta(a, b)
    if abs(dot(axis, step)) < cos(radians(20)) * edge.length * length:
        return None
    denominator = dot(axis, axis)
    first = dot(delta(edge.a, a), axis) / denominator
    last = dot(delta(edge.a, b), axis) / denominator
    lower, upper = sorted((-first / (last - first), (1 - first) / (last - first)))
    start, end = max(0.0, lower), min(1.0, upper)
    if end <= start:
        return None

    # Clip at both the ends of the edge and the sides of its lateral corridor.
    def lateral(point):
        x, y = delta(edge.a, point)
        return (axis[0] * y - axis[1] * x) / edge.length

    offset = lateral(a)
    change = lateral(b) - offset
    if change:
        lower, upper = sorted(((-radius - offset) / change,
                               (radius - offset) / change))
        start, end = max(start, lower), min(end, upper)
    elif abs(offset) > radius:
        return None
    if end <= start:
        return None
    return Candidate(edge, start, end, max(0.0, min(1.0, first + start * (last - first))),
                     max(0.0, min(1.0, first + end * (last - first))))


def cells(a, b, margin=0):
    return ((x, y)
            for x in range(floor((min(a[0], b[0]) - margin) / GRID_M),
                           floor((max(a[0], b[0]) + margin) / GRID_M) + 1)
            for y in range(floor((min(a[1], b[1]) - margin) / GRID_M),
                           floor((max(a[1], b[1]) + margin) / GRID_M) + 1))


class Network:
    def __init__(self, revision, edges, bounds=None):
        self.revision = revision
        self.bounds = bounds
        self.edges = {}
        self.grid = defaultdict(list)
        self.degree = Counter()
        for edge in edges:
            if edge.id in self.edges or edge.length_mm <= 0:
                raise ValueError(f"Duplicate or empty edge: {edge.id}")
            self.edges[edge.id] = edge
            self.degree.update(edge.nodes)
            # Subdivide long straight edges before indexing their bounding boxes.
            pieces = ceil(edge.length / GRID_M)
            occupied = {cell for i in range(pieces)
                        for cell in cells(edge.at(i / pieces), edge.at((i + 1) / pieces))}
            for cell in occupied:
                self.grid[cell].append(edge.id)

    @classmethod
    def gravesend(cls, path: Path):
        raw = gzip.decompress(path.read_bytes())
        source = json.loads(raw)
        if source.get("format") == COMPACT_FORMAT:
            catalogue = []
            excluded = set()
            for edge_id, label, a, b, eligible in source["sections"]:
                nodes = tuple(edge_id.split(":")[-2:])
                first = local_point(source["bounds"], [coord / 1e7 for coord in a])
                last = local_point(source["bounds"], [coord / 1e7 for coord in b])
                catalogue.append(Edge(edge_id, nodes, label, first, last))
                if not eligible:
                    excluded.add(edge_id)
            return cls(source["revision"], catalogue, source["bounds"]), excluded

        bounds = source["metadata"]["bounds"]
        edges = []
        for feature in source["features"]:
            props = feature["properties"]
            # The old multimodal extract has limited access tags. Restrict the
            # prototype to these road classes but retain excluded driving
            # geometry as a competitor (held separately below).
            if "driving" not in props["modes"]:
                continue
            prefix, way, first, last = feature["id"].split(":")
            if prefix != "osm" or first == last or int(way) != props["osm_way_id"]:
                raise ValueError("Invalid OSM source identity")
            coordinates = [tuple(point) for point in feature["geometry"]["coordinates"]]
            edges.append((feature["id"], (first, last), props.get("name") or props.get("ref"),
                          coordinates, props.get("highway") in ROAD_CLASSES))

        # The source IDs sort their endpoint IDs, but the coordinates retain way
        # order. Recover the orientation of shared endpoints by intersecting
        # the coordinates of incident edges. Leave isolated edge orientation
        # in source order when neither endpoint can be independently resolved.
        possibilities = {}
        for _, nodes, _, coordinates, _ in edges:
            for node in nodes:
                possibilities.setdefault(node, set(coordinates)).intersection_update(coordinates)
        catalogue = []
        excluded = set()
        for edge_id, (first, last), label, coordinates, eligible in edges:
            a, b = coordinates
            forward = a in possibilities[first] and b in possibilities[last]
            reverse = b in possibilities[first] and a in possibilities[last]
            if not forward and not reverse:
                raise ValueError(f"Inconsistent OSM node geometry: {edge_id}")
            if reverse and not forward:
                a, b = b, a
            catalogue.append(Edge(edge_id, (first, last), to_label(label),
                                  local_point(bounds, a), local_point(bounds, b)))
            if not eligible:
                excluded.add(edge_id)
        return cls("osm-gravesend-v1:" + hashlib.sha256(raw).hexdigest(), catalogue, bounds), excluded

    def compact_gravesend(self, excluded):
        """Build a small deterministic trial asset from this resolved catalogue."""
        def e7(point):
            return [round(value * 1e7) for value in geographic_point(self.bounds, point)]

        sections = [[edge.id, edge.label, e7(edge.a), e7(edge.b), edge.id not in excluded]
                    for edge in sorted(self.edges.values(), key=lambda row: row.id)]
        document = {"format": COMPACT_FORMAT, "revision": self.revision,
                    "bounds": self.bounds, "sections": sections}
        return gzip.compress(json.dumps(document, separators=(",", ":")).encode(), mtime=0)

    def align(self, a, b, uncertainty_m=0):
        length = hypot(*delta(a, b))
        if not 0 < length <= DENSITY_LIMIT_M:
            return (), "distance_gap"
        nearby = {edge_id for cell in cells(a, b, ALIGN_RADIUS_M + uncertainty_m)
                  for edge_id in self.grid.get(cell, ())}
        candidates = []
        for edge_id in sorted(nearby):
            edge = self.edges[edge_id]
            candidate = project(edge, a, b, length, ALIGN_RADIUS_M)
            if candidate:
                candidates.append(candidate)
            elif uncertainty_m:
                competitor = project(edge, a, b, length, ALIGN_RADIUS_M + uncertainty_m)
                if competitor and (competitor.end - competitor.start) * length > SEAM_M:
                    return (), "competing_sections"
        if not candidates:
            return (), "no_alignment"
        candidates.sort(key=lambda candidate: (candidate.start, candidate.end))
        previous = candidates[0]
        for current in candidates[1:]:
            seam = (current.start - previous.end) * length
            if seam < -SEAM_M or (seam < 0 and not previous.joins(current)):
                return (), "competing_sections"
            if seam > SEAM_M or not previous.joins(current):
                return (), "disconnected_sections"
            previous = current
        if max(candidates[0].start, 1 - candidates[-1].end) * length > SEAM_M:
            return (), "incomplete_alignment"
        spans = tuple(span for candidate in candidates
                      if (span := candidate.span()).start_mm < span.end_mm)
        return spans, None if spans else "no_alignment"


def to_label(label):
    return label if isinstance(label, str) and label.strip() else None


def local_point(bounds, coordinates):
    lon, lat = coordinates
    offset_lon, offset_lat = bounds["west"], bounds["south"]
    return ((lon - offset_lon) * longitude_scale(bounds), (lat - offset_lat) * METRES_PER_LATITUDE_DEGREE)


def geographic_point(bounds, point):
    return (bounds["west"] + point[0] / longitude_scale(bounds),
            bounds["south"] + point[1] / METRES_PER_LATITUDE_DEGREE)


def longitude_scale(bounds):
    return METRES_PER_LATITUDE_DEGREE * cos(radians((bounds["south"] + bounds["north"]) / 2))


@dataclass(frozen=True)
class Fix:
    point: Point
    time_ms: int
    accuracy_m: float


def observed_coverage(network, fixes, excluded=()):
    """Returns prototype-supported intervals and hold reasons for original pairs."""
    covered = []
    holds = defaultdict(int)
    for first, last in zip(fixes, fixes[1:]):
        if not all(isfinite(value) for fix in (first, last)
                   for value in (*fix.point, fix.time_ms, fix.accuracy_m)):
            holds["invalid_fix"] += 1
            continue
        if not all(0 < fix.accuracy_m <= 5 for fix in (first, last)):
            holds["fix_accuracy"] += 1
            continue
        if not 0 < last.time_ms - first.time_ms <= 8_000:
            holds["time_gap"] += 1
            continue
        if hypot(*delta(first.point, last.point)) < 2:
            holds["insufficient_motion"] += 1
            continue
        spans, reason = network.align(first.point, last.point,
                                      max(first.accuracy_m, last.accuracy_m))
        if reason:
            holds[reason] += 1
        elif any(span.edge_id in excluded for span in spans):
            holds["out_of_scope_road"] += 1
        else:
            covered.extend(spans)
    if len(fixes) < 2:
        holds["missing_original_fixes"] += 1
    return covered, dict(holds)


class Ledger:
    """A per-revision, in-memory projection. Callers supply the source fingerprint."""

    def __init__(self, network, policy=POLICY):
        self.network = network
        self.cache_version = network.revision, policy
        self.journeys = {}
        self.by_edge = {}
        self.aggregate = {}
        self.interval_counts = {}
        self.unique_mm = 0

    def replace(self, journey_id, fingerprint, project_journey):
        old = self.journeys.get(journey_id)
        if old and old[0] == fingerprint:
            return False
        per_edge = defaultdict(list)
        for span in project_journey():
            edge = self.network.edges[span.edge_id]
            if not 0 <= span.start_mm < span.end_mm <= edge.length_mm:
                raise ValueError(f"Invalid coverage interval: {span}")
            per_edge[span.edge_id].append((span.start_mm, span.end_mm))
        self._update(journey_id, old, {key: union(value) for key, value in per_edge.items()})
        self.journeys[journey_id] = fingerprint, set(per_edge)
        return True

    def remove(self, journey_id):
        old = self.journeys.pop(journey_id, None)
        if old:
            self._update(journey_id, old, {})

    def _update(self, journey_id, old, per_edge):
        for edge_id in (old[1] if old else set()) | per_edge.keys():
            contributors = self.by_edge.setdefault(edge_id, {})
            old_ranges = set(contributors.pop(journey_id, ()))
            new_ranges = set(per_edge.get(edge_id, ()))
            if new_ranges:
                contributors[journey_id] = per_edge[edge_id]
            counts = self.interval_counts.setdefault(edge_id, Counter())
            changed = False
            for interval in old_ranges - new_ranges:
                counts[interval] -= 1
                if not counts[interval]:
                    del counts[interval]
                    changed = True
            for interval in new_ranges - old_ranges:
                changed |= interval not in counts
                counts[interval] += 1

            if not changed:
                continue
            previous = self.aggregate.pop(edge_id, ())
            if counts:
                self.aggregate[edge_id] = union(counts)
            else:
                del self.by_edge[edge_id]
                del self.interval_counts[edge_id]
            self.unique_mm += (sum(b - a for a, b in self.aggregate.get(edge_id, ()))
                               - sum(b - a for a, b in previous))

    def intervals(self, edge_id, journey_ids=None):
        if journey_ids is None:
            return self.aggregate.get(edge_id, ())
        return union(interval for journey, intervals in self.by_edge.get(edge_id, {}).items()
                     if journey in journey_ids for interval in intervals)

    def contributors_at(self, edge_id, offset_mm, journey_ids=None):
        edge = self.network.edges[edge_id]
        if not 0 <= offset_mm <= edge.length_mm:
            raise ValueError("Selection is outside the reference section")
        return sorted(journey for journey, intervals in self.by_edge.get(edge_id, {}).items()
                      if (journey_ids is None or journey in journey_ids)
                      and any(start <= offset_mm <= end for start, end in intervals))
